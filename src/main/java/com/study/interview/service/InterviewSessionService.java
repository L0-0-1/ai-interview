package com.study.interview.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.study.interview.exception.BizException;
import com.study.interview.mapper.InterviewMessageMapper;
import com.study.interview.mapper.InterviewSessionMapper;
import com.study.interview.model.InterviewMessage;
import com.study.interview.model.InterviewSession;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 面试会话的数据库读写层：只管会话表 + 消息表这两张相关表的读写操作。
 *
 * 为什么要把「会话 + 消息」的操作单独做成一个 Spring Bean，
 * 而不是写在 InterviewService 里当私有方法：
 *   Spring 的事务是「代理事务」——@Transactional 生效的前提是调用
 *   「穿过代理」。同类内部互调（this.xxx()）不过代理，注解被忽略、
 *   事务不生效；跨 Bean 调用（InterviewService 注入本类）才走代理。
 *   所以短事务方法必须住在另一个 Bean 里，这是 Spring 事务的经典坑。
 */
@Service
public class InterviewSessionService {

    private final InterviewSessionMapper sessionMapper;
    private final InterviewMessageMapper messageMapper;

    // 构造器注入：本类同包，两个 Mapper 都由 @MapperScan 注册、Spring 自动装配
    public InterviewSessionService(InterviewSessionMapper sessionMapper, InterviewMessageMapper messageMapper) {
        this.sessionMapper = sessionMapper;
        this.messageMapper = messageMapper;
    }

    /**
     * 短事务：一次保存「会话 + 首条消息」两条写。
     *
     * 为什么这两条 INSERT 必须同一个事务（本交付最要紧的边界）：
     *   它们是一条业务数据的两个半件——会话没有消息 = 一场「哑巴面试」，
     *   消息没有会话 = 没人认领的孤儿。分开提交，任何一条中途失败
     *   （比如消息表 insert 时数据库抖动）都会留下半截脏数据。
     *   包进一个事务 = 「要么两条都进库，要么都不进库」。
     *
     * 为什么事务边界恰好只包这两条写、绝不上移到包 AI 调用：
     *   @Transactional 的语义是「方法一进来就借连接、方法结束才归还」。
     *   AI 出题要等几秒到几十秒，把它包进事务 = 连接被白白占用，
     *   连接池（HikariCP 默认 10 格）会被慢调用拖垮，无辜的快查询全部排队。
     *   （与 ResumeReviewService「AI 慢调用放事务外」同款边界。）
     *
     * 事务生效的触发条件：任何 RuntimeException（含 BizException、insert 抛的库异常）
     * 冒泡出本方法，Spring 就回滚两条写——本方法只负责抛，不负责 catch。
     */
    @Transactional
    public Long createSessionAndFirstMessage(String resumeText, String targetPosition, String question) {
        // 第一条写：会话表。currentRound=1、maxRound=8、status=ONGOING
        // 都是「开始一场面试」的固有初始值，由代码写死，不信任调用方传入
        InterviewSession session = new InterviewSession();
        session.setResumeText(resumeText);
        session.setTargetPosition(targetPosition);
        session.setCurrentRound(1);
        session.setMaxRound(InterviewSession.DEFAULT_MAX_ROUND);
        session.setStatus(InterviewSession.STATUS_ONGOING);
        int sessionRow = sessionMapper.insert(session);
        if (sessionRow == 0) {
            // insert 要么成功返回 1、要么抛异常（抛异常走回滚），返回 0 只可能是库层故障
            throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "面试会话保存失败");
        }

        // 第二条写：首条消息。sessionId 用会话表 insert 后回写的主键
        // （getGeneratedKeys 机制，与 Resume 落库同款）；role=INTERVIEWER、roundNo=1
        InterviewMessage firstMessage = new InterviewMessage();
        firstMessage.setSessionId(session.getId());
        firstMessage.setRole(InterviewMessage.ROLE_INTERVIEWER);
        firstMessage.setContent(question);
        firstMessage.setRoundNo(1);
        int messageRow = messageMapper.insert(firstMessage);
        if (messageRow == 0) {
            // 抛运行时异常 → 连同上面的会话 insert 一起回滚，不会留下孤儿会话
            throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "面试消息保存失败");
        }

        return session.getId();
    }

    // ---------- 读：提交回答前的存在性 / 状态 / 题目检查 ----------

    /**
     * 按 id 查会话；不存在 → 1002 + HTTP 404（与 ResumeService.getById 同款约定）。
     *
     * 为什么读操作不放 @Transactional：selectById 是单条 SQL，自身原子，
     * 无需借用事务；会话随后还要被检查状态/轮次，真正的并发防线在下面的
     * 条件更新里（见 advanceToNextRound），不靠这里的读。
     */
    public InterviewSession getSessionOrThrow(Long sessionId) {
        InterviewSession session = sessionMapper.selectById(sessionId);
        if (session == null) {
            // 资源不存在 → 1002 + 404，与「参数填错」分开（1001 只留给参数问题）
            throw new BizException(1002, HttpStatus.NOT_FOUND, "面试会话不存在");
        }
        return session;
    }

    /**
     * 按 session_id + round_no + INTERVIEWER 读当前轮次的题目。
     *
     * 为什么查不到要按 500 处理而不是 404：能走到这里，说明会话存在、
     * 状态 ONGOING、轮次对得上——题目却在库里缺失，这是数据完整性被
     * 破坏（如人为删行），属于服务端数据问题，不是请求方的问题。
     */
    public String getCurrentQuestionOrThrow(Long sessionId, int roundNo) {
        InterviewMessage questionMessage = messageMapper.selectOne(
                new LambdaQueryWrapper<InterviewMessage>()
                        .eq(InterviewMessage::getSessionId, sessionId)
                        .eq(InterviewMessage::getRoundNo, roundNo)
                        .eq(InterviewMessage::getRole, InterviewMessage.ROLE_INTERVIEWER));
        if (questionMessage == null
                || questionMessage.getContent() == null
                || questionMessage.getContent().isBlank()) {
            throw new BizException(2004, HttpStatus.INTERNAL_SERVER_ERROR, "当前轮次的面试题缺失，无法推进面试");
        }
        return questionMessage.getContent();
    }

    // ---------- 写：短事务推进轮次 / 结束会话 ----------

    /**
     * 短事务：普通轮次提交 = 条件更新轮次 + 写候选人回答 + 写下一轮题目。
     *
     * 为什么更新必须带「会话 ID + ONGOING + 旧轮次」三个条件：
     *   它们是「推进前那一刻的状态」——两个请求同时提交同一轮时，
     *   只有先拿到锁的那个把 current_round 改掉；后一个再来，条件里
     *   current_round=旧轮次 已经不成立，影响行数为 0，直接判冲突，
     *   不会把两条回答都写进同一轮。这就是并发场景下的乐观锁。
     *
     * 为什么冲突（影响行数 0）在事务里直接抛：此时没有任何写发生，
     *   回滚是空转，异常冒泡到全局处理器翻成 1003 + 409。
     *
     * 为什么三条写必须同一个事务：轮次 +1 和两条消息是一个业务整体的
     *   三个半件——更新成功但消息插入失败，会出现「会话说在第 2 轮，
     *   消息表却没有第 2 题的题干」的断档。任一写失败，运行时异常冒泡，
     *   全部回滚，会话原样等待第 N 轮，请求可原样重试。
     */
    @Transactional
    public int advanceToNextRound(Long sessionId, int submittedRound, String answerText, String nextQuestion) {
        // 第一条写：条件更新轮次。影响行数 0 = 条件没匹配上：
        // 会话已结束 / 轮次已被并发请求推进（或会话被删），一律按状态冲突回 409
        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getStatus, InterviewSession.STATUS_ONGOING)
                .eq(InterviewSession::getCurrentRound, submittedRound)
                .set(InterviewSession::getCurrentRound, submittedRound + 1));
        if (updated == 0) {
            throw new BizException(1003, HttpStatus.CONFLICT,
                    "提交冲突：第 " + submittedRound + " 轮已提交或会话状态已变化");
        }

        // 第二条写：当前轮次的候选人回答
        InterviewMessage answerMessage = new InterviewMessage();
        answerMessage.setSessionId(sessionId);
        answerMessage.setRole(InterviewMessage.ROLE_CANDIDATE);
        answerMessage.setContent(answerText);
        answerMessage.setRoundNo(submittedRound);
        if (messageMapper.insert(answerMessage) == 0) {
            // 抛运行时异常 → 连同上面的轮次更新一起回滚
            throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "候选人回答保存失败");
        }

        // 第三条写：下一轮的面试官题目（模型已在事务外生成好，这里只落库）
        InterviewMessage nextMessage = new InterviewMessage();
        nextMessage.setSessionId(sessionId);
        nextMessage.setRole(InterviewMessage.ROLE_INTERVIEWER);
        nextMessage.setContent(nextQuestion);
        nextMessage.setRoundNo(submittedRound + 1);
        if (messageMapper.insert(nextMessage) == 0) {
            // 抛运行时异常 → 三条写全部回滚，会话仍等待原轮次，可原样重试
            throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "面试题保存失败");
        }

        return submittedRound + 1;
    }

    /**
     * 短事务：最后一轮提交 = 条件更新状态为 ENDED + 写候选人回答。
     *
     * 与普通轮次的差异（本分支由系统轮数上限决定，与 advanceToNextRound 拆开）：
     *   1. 不推进轮次：current_round 留在最后一轮（已到 max_round，无处可去）；
     *   2. 不写下一题：面试结束，没有 INTERVIEWER 消息；
     *   3. 更新条件里仍带 current_round=提交轮次：同样防并发重复提交，
     *      重复提交影响行数为 0 → 1003 + 409。
     */
    @Transactional
    public void endSessionWithLastAnswer(Long sessionId, int submittedRound, String answerText) {
        // 第一条写：条件更新状态为 ENDED（轮次不动）
        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<InterviewSession>()
                .eq(InterviewSession::getId, sessionId)
                .eq(InterviewSession::getStatus, InterviewSession.STATUS_ONGOING)
                .eq(InterviewSession::getCurrentRound, submittedRound)
                .set(InterviewSession::getStatus, InterviewSession.STATUS_ENDED));
        if (updated == 0) {
            throw new BizException(1003, HttpStatus.CONFLICT,
                    "提交冲突：第 " + submittedRound + " 轮已提交或会话状态已变化");
        }

        // 第二条写：最后一轮的候选人回答。失败抛异常 → 连同上一条更新一起回滚，
        // 会话仍是 ONGOING 等待最后一轮，可原样重试
        InterviewMessage answerMessage = new InterviewMessage();
        answerMessage.setSessionId(sessionId);
        answerMessage.setRole(InterviewMessage.ROLE_CANDIDATE);
        answerMessage.setContent(answerText);
        answerMessage.setRoundNo(submittedRound);
        if (messageMapper.insert(answerMessage) == 0) {
            throw new BizException(2001, HttpStatus.INTERNAL_SERVER_ERROR, "候选人回答保存失败");
        }
    }
}
