package com.study.interview.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.study.interview.exception.BizException;
import com.study.interview.model.Resume;
import com.study.interview.model.ResumeReview;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;

/**
 * 简历评审业务层：AI 结构化输出的编排逻辑。
 *
 * 「调 review() 拿到结果再落库」这种跨 Service 的编排放在本类：
 * reviewAndSave() 内部组合 ResumeService 完成落库——编排属于业务，绝不下沉回 Controller。
 */
@Service
public class ResumeReviewService {

    private final ChatClient chatClient;
    private final ObjectMapper objectMapper;
    private final ResumeService resumeService;

    // 构造器注入三个依赖：
    //   ChatClient    —— ChatClientConfig 里配好 defaultSystem（资深面试官人设）的全局 Bean
    //   ObjectMapper  —— Spring Boot 自动配置好的 Jackson 序列化器（web starter 自带）。
    //                    不要自己 new：new 出来的拿不到 Boot 的全局配置（日期格式、null 策略等）
    //   ResumeService —— 落库的执行者。和本类同包，所以不需要 import
    public ResumeReviewService(ChatClient chatClient, ObjectMapper objectMapper, ResumeService resumeService) {
        this.chatClient = chatClient;
        this.objectMapper = objectMapper;
        this.resumeService = resumeService;
    }

    /**
     * 简历评审（结构化输出）。
     *
     * 和同步对话相比，唯一实质变化是最后一行：content() 换成 entity(ResumeReview.class)。
     * entity() 内部做两件事（DefaultChatClient 源码实测）：
     *   1. new BeanOutputConverter<>(ResumeReview.class).getFormat()：
     *      把类变成 JSON Schema，再生成一段「你必须按 JSON 格式输出，Schema 如下……」的文字；
     *   2. 这段文字由 advisor 链追加到最后一个 user 消息的末尾一起发给模型，
     *      拿到回答后交给 Jackson 反序列化成 ResumeReview 对象。
     *
     * 提示词故意只写「形态」（必须分维度打分、每维度配理由），不写具体打分标准：
     * 规则（各维度 0-100 怎么算、理由要写什么、列表最多几条）全部写在
     * ResumeReview 的各 @JsonPropertyDescription 里，不在这里堆。
     * 好处是「说明跟着字段走」：以后改评分规则只动模型类，别处不用碰。
     *
     * 这里故意不挂 MessageChatMemoryAdvisor：
     * 简历评审是「一次性任务」，每次都是独立的输入输出，不需要多轮上下文；
     * 挂上记忆反而会把历史对话混进评审依据里，污染结果。
     * 记忆是给「聊天」用的，任务型接口按需挂 —— 这是接口设计里的取舍。
     */
    public ResumeReview review(String resumeText) {
        // 第一道闸：空内容直接拒绝，绝不放行去调 AI。
        // 为什么这么严：调一次模型是真金白银，拿空串/纯空格问它等于白烧钱，它还只会胡编。
        // isBlank() = 只有空格、制表符这类空白也算「空」（JDK 11 起自带）。
        // 为什么放 Service 不放 Controller：校验规则属于业务（与 ResumeController 注释同款边界）；
        // 且 review() 是 review 和 reviewAndSave 两个接口的共同入口——这里拦一道，两处全防住。
        if (resumeText == null || resumeText.isBlank()) {
            // 业务码 1001 = 参数问题（约定表），经全局异常处理器翻成 HTTP 400 + 统一信封
            throw new BizException(1001, "简历内容不能为空");
        }
        return chatClient.prompt()
                // .system() 是请求级人设，会【覆盖】builder 里的 defaultSystem
                // （Spring AI 官方文档：不带 default 前缀的运行时方法覆盖对应默认值）。
                // 为什么必须覆盖：全局人设「回答简洁、直接给结论」是给聊天接口定的岗位说明；
                // 本任务是结构化评审，要求每个字段完整充分——「简洁」和「输出完整」两条指令
                // 同时在场，模型会自行折中，常见翻车是 summary 只给半句、
                // strengths/weaknesses 缩成几个词。任务型接口换任务型人设，不和聊天共用。
                // 提示词只规定「分维度 + 每维度配理由」这个形态；
                // 每个维度具体怎么算分，看 ResumeReview 里各字段的 @JsonPropertyDescription。
                .system("你是一位严格的技术简历评审官。本次评审必须从三个维度分别打分，"
                        + "每个维度都要同时给出分数和理由：技术能力（techScore/techReason）、"
                        + "项目经验（projectScore/projectReason）、表达与逻辑（expressionScore/expressionReason）；"
                        + "打分标准以各字段说明为准，分数高低必须能从对应理由中看出依据，"
                        + "禁止只给分数不给理由，禁止理由与分数互相矛盾；"
                        + "strengths 与 weaknesses 每条都给出结论和理由，summary 写 1-2 句完整概括；"
                        + "宁可详细，不要为省字数而缩略或省略内容。")
                // resumeText 直接拼进用户消息。提示词里没有「JSON」字样也没关系：
                // getFormat() 追加的那段文本自带 "JSON format" 字样，天然满足
                // DeepSeek JSON 模式「提示词必须含 json」的前置条件
                .user("请评审这份简历：\n\n" + resumeText)
                .call()
                .entity(ResumeReview.class);
    }

    /**
     * 评审 + 落库的编排方法（阶段 1 闭环收尾）。
     *
     * 为什么这个方法上【没有】@Transactional —— 这是本步最要紧的边界：
     *   @Transactional 的语义是「方法一进来就借一个数据库连接，直到方法结束才归还」。
     *   若标在这里，review() 等 DeepSeek 的几秒~几十秒里，连接会被全程占着不放。
     *   连接池默认只有 10 个格子（本机未配置，就是 HikariCP 默认 10）：
     *   10 个并发评审就能占满，第 11 个请求（哪怕只是一条普通查询）借不到连接，
     *   只能排队干等甚至超时报错——一个慢操作把无关的快操作全部堵死。
     *
     * 正确边界（三句话）：
     *   1) AI 调用慢、且根本不碰数据库 —— 留在事务外（不加注解，天然如此）；
     *   2) 落库只有一条 INSERT —— 单条 SQL 自身就原子，不需要显式事务；
     *   3) 将来一次落库要写多条 SQL（比如评审详情拆去子表）时，才把 @Transactional
     *      加到「恰好包住那几条写」的最内层方法上 —— 永远不包 AI 调用。
     */
    public Resume reviewAndSave(String resumeText) {
        // 第一步：先调 AI 拿评审结果。此刻无事务、不占任何数据库连接，
        // 等多久都只消耗本线程的时间，不消耗连接池的格子
        ResumeReview aiReview = review(resumeText);

        // 第二步：组装数据库行（Resume 实体 = Resume 表的一行）
        Resume resume = new Resume();
        // 姓名兜底：AI 输出天生不可靠（Schema 只是"说明书"，
        // 不保证模型真按"没提及就填未知"执行）——防它返回 null 落库成 NULL 姓名
        // （schema.sql 里 name 列允许 NULL，兜底是数据质量约定，NULL 参与不了后续按姓名筛选）
        String name = aiReview.name();
        if (name == null || name.trim().isEmpty()) {
            name = "未知";
        }
        resume.setName(name);
        resume.setResumeText(resumeText);      // 原文用入参，不依赖 AI 复述
        resume.setSummary(aiReview.summary()); // 独立列：给数据库排序/筛选用
        // 综合分由代码聚合（overallScore 简单平均），不让模型输出总分——
        // 大模型算术不可靠，让它输出总分，总分大概率对不上分项。
        resume.setScore(aiReview.overallScore());
        // 三个维度分拆独立列落库：分数将来要进 WHERE / ORDER BY（按技术分排序、筛低于 60 分的），
        // 必须是列；理由不拆列，留在 review_json 快照里（展示文本不参与查询，拆列就是列膨胀）。
        // null 直接存 NULL：NULL = 模型漏输，暴露问题（和 record 用 Integer 的选型同一条逻辑）。
        resume.setTechScore(aiReview.techScore());
        resume.setProjectScore(aiReview.projectScore());
        resume.setExpressionScore(aiReview.expressionScore());

        // 第三步：把整份评审序列化成 JSON 文本，装进 review_json 快照列。
        // writeValueAsString 会逐个调 record 的组件访问器（name()/techScore()/techReason()...）拼 JSON；
        // 维度分和理由改的是 record 组件，所以快照会自动带上新字段，这一步一行都不用改；
        // 与 Controller 返回对象时 Spring MVC 自动转 JSON 是同一件事，只是目的地不同（存库）
        try {
            resume.setReviewJson(objectMapper.writeValueAsString(aiReview));
        } catch (JsonProcessingException e) {
            // 刚从 review() 拿到的对象理论上不可能序列化失败；真失败说明是程序级 bug，
            // 包成运行时异常快速失败，不让受检异常的技术细节污染调用方的方法签名
            throw new IllegalStateException("评审详情序列化失败", e);
        }

        // 第四步：落库。单条 INSERT 自身原子。
        // myai 的 ResumeService.save() 返回 void：失败时它自己会抛 BizException(2001)，这里不用再判返回值——
        // 「void = 要么正常结束、要么抛异常」的签名，本来就不该给调用方留"失败怎么办"的假分支。
        resumeService.save(resume);
        // 返回落库后的实体：id 已被主键回写（getGeneratedKeys 机制）；
        // create_time 不会回写（它是库端默认值、不是主键）——想要真实时间得回库里查
        return resume;
    }
}
