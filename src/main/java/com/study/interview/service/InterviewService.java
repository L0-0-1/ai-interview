package com.study.interview.service;

import com.study.interview.exception.BizException;
import com.study.interview.model.InterviewAnswerResponse;
import com.study.interview.model.InterviewSession;
import com.study.interview.model.InterviewStartResponse;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

/**
 * 模拟面试业务编排层：开始面试 = 校验 → 事务外生成第一题 → 短事务落库 → 组装返回；
 * 提交回答 = 校验 → 查会话/状态/轮次 → 事务外生成下一题 → 短事务推进落库 → 组装返回。
 *
 * 处理顺序为什么严格按这个链条（与主线规划的交付约定一致）：
 *   1. 参数非法必须先拒绝：调一次模型是真金白银，拿空简历/空岗位问它等于白烧钱；
 *   2. AI 调用在数据库事务外：出题要等几秒到几十秒，不占连接池的格子；
 *   3. AI 失败（抛异常）时还没建会话——数据库里不会留下任何残留；
 *   4. 两条写放短事务（见 InterviewSessionService），要么都进库、要么都不进。
 */
@Service
public class InterviewService {

    private final ChatClient chatClient;
    private final InterviewSessionService interviewSessionService;

    // 构造器注入两个依赖：
    //   ChatClient              —— ChatClientConfig 里配好 defaultSystem 的全局 Bean
    //   InterviewSessionService —— 短事务落库的执行者。跨 Bean 注入才走 Spring 代理，
    //                              @Transactional 才真正生效（同类内部互调不生效的坑）
    public InterviewService(ChatClient chatClient, InterviewSessionService interviewSessionService) {
        this.chatClient = chatClient;
        this.interviewSessionService = interviewSessionService;
    }

    /**
     * 开始一场模拟面试并生成第一题。
     *
     * 为什么本方法【不加】@Transactional：
     *   事务边界必须恰好包住 InterviewSessionService 里的两条写，
     *   绝不能上移到这里把 AI 调用也包进去——AI 慢且不碰库，
     *   包进去 = 连接被慢调用长时间占用，连接池被拖垮。
     *   （与 ResumeReviewService.reviewAndSave「AI 慢调用放事务外」同款边界。）
     *
     * 幂等性声明：本版不实现幂等——每次调用 start 都创建一场新面试。
     * 重复点击「开始面试」产生多个会话是当前版本的既定行为，不算 bug；
     * 幂等（同一请求只建一场）留给后续小交付再考虑。
     */
    public InterviewStartResponse start(String resumeText, String targetPosition) {
        // ---- 第 1 步：校验入参（在调模型之前，省真金白银） ----
        // isBlank() = null、纯空格、制表符这类「没内容」都算空，直接拒绝
        // 校验属于业务，放 Service 不放 Controller（与 ResumeReviewService 同款边界）
        if (resumeText == null || resumeText.isBlank()) {
            throw new BizException(1001, "简历内容不能为空");
        }
        if (targetPosition == null || targetPosition.isBlank()) {
            throw new BizException(1001, "目标岗位不能为空");
        }

        // ---- 第 2 步：事务外生成第一题 ----
        // 此刻无事务、不占任何数据库连接；失败（供应商超时/限流等）直接冒泡，
        // 全局异常处理器兜底，且数据库里没有任何残留
        String question = generateFirstQuestion(resumeText, targetPosition);

        // ---- 第 3 步：检查题目非空（模型输出不可靠，提示词不是强制的数据约束） ----
        // 模型真返回空串/纯空白：这是服务端（模型侧）的问题，不是请求方的错
        // → 2004 + HTTP 500，不落库、不建会话
        if (question == null || question.isBlank()) {
            throw new BizException(2004, HttpStatus.INTERNAL_SERVER_ERROR, "模型未生成有效面试题，请重试");
        }

        // ---- 第 4 步：短事务保存会话和首条消息（跨 Bean 调用，事务生效） ----
        Long sessionId = interviewSessionService.createSessionAndFirstMessage(resumeText, targetPosition, question);

        // ---- 第 5 步：组装返回。currentRound 固定 1（开始面试的固有值，代码写死） ----
        return new InterviewStartResponse(sessionId, question, 1);
    }

    /**
     * 提交当前轮次的回答，推进到下一题（或轮数上限时结束会话）。
     *
     * 为什么本方法【不加】@Transactional（与 start 同款边界）：
     *   本方法里既有数据库读、又有 AI 慢调用、又有数据库写。
     *   事务边界必须恰好包住 InterviewSessionService 里的几条写，
     *   绝不上移到这里把 AI 调用包进去——AI 慢且不碰库，
     *   包进去 = 连接被慢调用长时间占用，连接池被拖垮。
     *
     * 为什么先查状态/轮次、再在写事务里「条件更新」复查一次：
     *   第 2~4 步的检查是给请求方快速失败的人话版；真正的并发防线
     *   是短事务里的 UPDATE ... WHERE status=ONGOING AND current_round=N，
     *   两个请求同时提交同一轮时，只有先到的能改动行（见 advanceToNextRound）。
     *
     * 为什么模型失败后原轮次可原样重试：AI 调用在事务外，失败时
     *   一条消息、一次轮次更新都没发生——会话仍 ONGOING 等待第 N 轮，
     *   请求方用同一请求体重发即可（模型侧已扣的 token 不退回）。
     */
    public InterviewAnswerResponse answer(Long sessionId, Integer roundNo, String answerText) {
        // ---- 第 1 步：校验入参（在查库和调模型之前，先拦明显不合法） ----
        if (sessionId == null || sessionId <= 0) {
            throw new BizException(1001, "会话 id 不合法");
        }
        if (roundNo == null || roundNo < 1) {
            throw new BizException(1001, "轮次号必须为正整数");
        }
        if (answerText == null || answerText.isBlank()) {
            throw new BizException(1001, "回答内容不能为空");
        }
        if (answerText.length() > 2000) {
            // length() 按字符数算（不是字节），上限 2000 是业务暂定值，够写一段完整回答
            throw new BizException(1001, "回答内容过长：最多 2000 字");
        }

        // ---- 第 2 步：查会话存在性（不存在 → 1002 + 404，同款约定见 getSessionOrThrow） ----
        InterviewSession session = interviewSessionService.getSessionOrThrow(sessionId);

        // ---- 第 3 步：状态闸 —— 已结束的会话不接受任何提交，先于轮次判断给出清楚的冲突 ----
        if (!InterviewSession.STATUS_ONGOING.equals(session.getStatus())) {
            throw new BizException(1003, HttpStatus.CONFLICT, "面试已结束，无法继续提交回答");
        }

        // ---- 第 4 步：轮次闸 —— 客户端轮次必须等于会话当前等待的轮次 ----
        // 为什么过期（roundNo < currentRound）返回 409 而不是 400：不是格式错，
        // 是客户端状态落后于服务端状态，用冲突状态码 + 提示当前轮次，前端可自行对齐
        int currentRound = session.getCurrentRound();
        if (roundNo < currentRound) {
            throw new BizException(1003, HttpStatus.CONFLICT,
                    "第 " + roundNo + " 轮已提交，当前等待第 " + currentRound + " 轮的回答");
        }
        if (roundNo > currentRound) {
            throw new BizException(1003, HttpStatus.CONFLICT,
                    "轮次超前：当前等待第 " + currentRound + " 轮的回答");
        }

        // ---- 第 5 步：轮数上限兜底 —— 当前轮就是最后一轮时，不再调模型 ----
        // 该分支由系统轮数上限（max_round）决定：只落候选人回答、状态改 ENDED、
        // 轮次留在最后一轮、不生成下一题
        if (roundNo.equals(session.getMaxRound())) {
            interviewSessionService.endSessionWithLastAnswer(sessionId, roundNo, answerText);
            return new InterviewAnswerResponse(sessionId, roundNo, InterviewSession.STATUS_ENDED, "");
        }

        // ---- 第 6 步：读当前题目（session_id + round_no + INTERVIEWER），供模型追问 ----
        String currentQuestion = interviewSessionService.getCurrentQuestionOrThrow(sessionId, roundNo);

        // ---- 第 7 步：事务外生成下一题 ----
        // 此刻无事务、不占数据库连接；失败直接冒泡，会话仍等待第 N 轮，可原样重试
        String nextQuestion = generateFollowUpQuestion(session.getTargetPosition(), currentQuestion, answerText);

        // ---- 第 8 步：检查题目非空（模型输出不可靠，提示词不是强制的数据约束） ----
        // 模型真返回空串/纯空白：服务端（模型侧）的问题 → 2004 + HTTP 500，不落库、不推进轮次
        if (nextQuestion == null || nextQuestion.isBlank()) {
            throw new BizException(2004, HttpStatus.INTERNAL_SERVER_ERROR, "模型未生成有效的下一题，请重试");
        }

        // ---- 第 9 步：短事务推进轮次并落两条消息（跨 Bean 调用，事务生效） ----
        int newRound = interviewSessionService.advanceToNextRound(sessionId, roundNo, answerText, nextQuestion);

        // ---- 第 10 步：组装返回 ----
        return new InterviewAnswerResponse(sessionId, newRound, InterviewSession.STATUS_ONGOING, nextQuestion);
    }

    /**
     * 用简历 + 岗位让模型生成第一道面试题。
     *
     * 为什么覆盖全局 defaultSystem：
     *   ChatClientConfig 里的 defaultSystem「资深 Java 后端面试官，回答简洁」是给聊天接口
     *   定的「答题人设」；本任务是「出题」，要求完全不同——出题官需要从简历里挖可问点。
     *   不带 default 前缀的 .system() 是请求级覆盖（Spring AI 官方文档），任务型接口换任务型人设。
     *
     * 为什么故意不挂 MessageChatMemoryAdvisor：
     *   第一题是「一次性出题任务」，输入只有简历 + 岗位，不需要多轮上下文；
     *   挂上记忆反而会把无关的历史对话混进出题依据（与简历评审不挂记忆同款取舍）。
     *   后续追问同样不挂记忆（见 generateFollowUpQuestion）——每轮追问的上下文
     *   由接口入参（当前题 + 回答）显式拼装，不依赖框架的会话记忆。
     *
     * 为什么提示词要「只输出题目本身、不给答案」：
     *   第一题会作为 INTERVIEWER 消息落库并直接返回给用户，
     *   混进开场白/解释会让消息表里全是垃圾文本，还会让「当前在第几轮」的语义变模糊。
     */
    private String generateFirstQuestion(String resumeText, String targetPosition) {
        return chatClient.prompt()
                .system("你是一位资深的技术面试官，负责为候选人设计模拟面试题。"
                        + "现在开始一场面试，请你出第 1 道题："
                        + "结合候选人简历中的项目经历和技术栈，围绕「" + targetPosition + "」岗位的要求出题；"
                        + "题目要具体、可回答、能考察真实水平（优先从简历里的项目细节切入追问），"
                        + "不要泛泛而谈。"
                        + "只输出题目本身，不要任何开场白、解释、题号或答案。")
                .user("候选人的简历如下：\n\n" + resumeText)
                .call()
                .content();
    }

    /**
     * 用岗位 + 当前题目 + 候选人回答让模型生成一道追问（下一题）。
     *
     * 为什么不把整份简历一起塞进去：会话里存了简历快照，但追问的锚点是
     * 「刚回答了什么」——回答里的漏洞、含糊点、关键结论才是追问素材，
     * 简历在开始面试时已经消费过一轮；控制上下文规模也省 token。
     *
     * 与 generateFirstQuestion 一样：请求级 system 覆盖默认人设，不挂记忆，
     * 只输出题目本身——下一题同样要落库并直接返回给用户，不能混入解释。
     */
    private String generateFollowUpQuestion(String targetPosition, String currentQuestion, String answerText) {
        return chatClient.prompt()
                .system("你是一位资深的技术面试官，正在主持一场围绕「" + targetPosition + "」岗位的模拟面试。"
                        + "请根据候选人刚回答的内容，追问一道能继续考察真实水平的后续题目："
                        + "追问要点（按优先级）：候选人的回答是否回避了关键细节、是否有说不清或含糊的地方、"
                        + "是否有关键结论可以要求展开或验证；"
                        + "题目要承接回答逐步深入，不要重复上一道题的题干，也不要提问已答清楚的内容。"
                        + "只输出题目本身，不要任何开场白、解释、题号或答案。")
                .user("上一道面试题：\n" + currentQuestion
                        + "\n\n候选人的回答：\n" + answerText)
                .call()
                .content();
    }
}
