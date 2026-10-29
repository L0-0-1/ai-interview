package com.study.interview.model;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

import java.util.List;

/**
 * 简历评审结果 —— 结构化输出的「目标类型」。
 *
 * 它在「让模型返回对象」这件事里扮演什么角色：
 *   把类交给 .entity(ResumeReview.class) 后，Spring AI 底层会做三件事：
 *   1. 用反射把类变成一份 JSON Schema（相当于给模型看的「填表说明」）；
 *   2. 把说明书拼进提示词发给模型，要求模型按 Schema 输出 JSON 文本；
 *   3. 模型返回文本后，用 Jackson 把它反序列化成 ResumeReview 对象。
 *   所以「结构化输出」= 提示词引导（前）+ 本地反序列化（后），
 *   不是模型真的返回了对象 —— 模型从头到尾只会写字。
 *
 * 为什么用 record 而不是普通 class：
 *   1. record 是 JDK 16+ 的语法，一行字段声明自动获得构造器、访问器、equals/toString，
 *      不用手写样板代码（本项目没有 Lombok，也不打算引入）；
 *   2. 字段天然不可变：评审结果是「一次性快照」，创建后不应该再被修改；
 *   3. Spring Boot 自带的 Jackson（2.12+）原生支持 record 反序列化，无需额外配置。
 *
 * 为什么从「单一总分」改成「三个维度分 + 每维度理由」：
 *   （真实招聘产品的评估也是这个形态：Moka EVA 的简历初筛就是多维度的量化评分，
 *   且各维度的权重可以按岗位配置。）
 *   1. 总分信息密度太低：两个候选人都 75 分——一个技术过硬但简历写得烂，
 *      一个技术平庸但包装漂亮，总分完全一样，招聘决策却截然不同。
 *      单一总分把「哪里强、哪里弱」糊成一团，筛选的人拿它做不了决定。
 *   2. 分数必须配理由：分数回答「多少分」，理由回答「凭什么」。
 *      有理由的分数才能被复核、被追溯、被挑战（比如面试时针对性追问）。
 *   3. 总分不让模型输出：大模型擅长写字、不擅长算术，让它同时输出
 *      三个分项和一个总分，总分大概率对不上分项（像 bug）。
 *      总分是分项的派生数据，派生计算属于代码（见 overallScore()）。
 */
public record ResumeReview(

        // @JsonPropertyDescription = 给模型看的「字段说明书」。
        // 它会被写进生成的 JSON Schema 的 description 里，模型能在提示词中读到。
        // 注意：它只影响「给模型的说明书」，不参与反序列化（反序列化只认字段名对应）。
        //
        // 三个评分字段用包装类型 Integer 而不是 int：
        // 模型万一漏返回，int 会静默变成 0（看起来像「打了 0 分」），
        // Integer 则是 null（验收时一眼就能看出「模型没返回」）。
        // AI 输出天生不可靠，类型选择偏向「暴露问题」而不是「掩盖问题」。
        @JsonPropertyDescription("候选人姓名；如果简历里没有提及，就填「未知」")
        String name,

        // ---- 维度一：技术能力 ----
        // 打分标准写在 @JsonPropertyDescription 里的理由：
        // 「说明跟着字段走」——将来调某个维度的打分标准，只动这一处，提示词不用碰。
        @JsonPropertyDescription("技术能力评分，0-100 之间的整数。打分标准：考察掌握的技术栈广度与深度、"
                + "对所用技术的原理理解（是否说清为什么用、怎么用），以及技术选型的合理性；"
                + "只罗列技术名词、没有落地细节的给低分。")
        Integer techScore,

        @JsonPropertyDescription("技术能力维度的给分理由，一句话点明依据（这份简历技术上好在哪/差在哪），"
                + "分数高低必须能从理由中看出依据")
        String techReason,

        // ---- 维度二：项目经验 ----
        @JsonPropertyDescription("项目经验评分，0-100 之间的整数。打分标准：考察项目复杂度与业务价值、"
                + "个人在项目中的角色与贡献是否清晰（是主导核心模块还是边缘打杂），"
                + "以及成果是否量化（性能提升、用户规模等具体数字）；没有量化结果或职责含糊的给低分。")
        Integer projectScore,

        @JsonPropertyDescription("项目经验维度的给分理由，一句话点明依据（项目规模/个人角色/量化成果等），"
                + "分数高低必须能从理由中看出依据")
        String projectReason,

        // ---- 维度三：表达与逻辑 ----
        @JsonPropertyDescription("表达与逻辑评分，0-100 之间的整数。打分标准：考察简历的条理性"
                + "（结构清晰、重点突出）、用词的专业与准确，以及描述是否有数据支撑；"
                + "通篇堆砌术语或记流水账的给低分。")
        Integer expressionScore,

        @JsonPropertyDescription("表达与逻辑维度的给分理由，一句话点明依据，分数高低必须能从理由中看出依据")
        String expressionReason,

        // ---- 保留的原有字段：优缺点与一句话总结 ----
        @JsonPropertyDescription("简历的优点列表，每条一句话，最多 3 条")
        List<String> strengths,

        @JsonPropertyDescription("简历的不足或可疑点列表，每条一句话，最多 3 条")
        List<String> weaknesses,

        @JsonPropertyDescription("一句话总结对这份简历的整体评价，不超过 50 字")
        String summary

) {

    /**
     * 综合分 = 三个维度分的简单平均，由代码算，绝不问模型要。
     *
     * 为什么不让模型直接输出总分（旧版 score 字段就是模型输出总分）：
     *   1. 大模型算术不可靠：让它同时算分项和总分，总分经常对不上分项
     *      （分项 85/90/60，总分却给 82），评审单上「总分≠分项」看着像 bug；
     *   2. 派生数据要可复现：同一份评审结果，代码聚合的总分永远一样；
     *   3. 权重演进留口子：现在先简单平均占位，将来「技术 40%、项目 40%、
     *      表达 20%」这类加权（真实招聘系统的维度权重跟着岗位走）只改这一处。
     *
     * 为什么返回 Integer 而不是 int：三个维度全是 null（模型漏输）时无法平均，
     * 返回 null 暴露问题，而不是静默给 0 分。
     *
     * 注意：本方法不是 record 的组件，Jackson 序列化/反序列化都看不见它——
     * 不会出现在 JSON Schema 里（模型不会被要求输出总分），
     * 响应 JSON 和 review_json 快照里也不会多出这个字段。
     */
    public Integer overallScore() {
        // 只对真实给了分的维度求平均：漏输的 null 不参与计算，也不计数
        int total = 0;
        int count = 0;
        if (techScore != null) { total += techScore; count++; }
        if (projectScore != null) { total += projectScore; count++; }
        if (expressionScore != null) { total += expressionScore; count++; }
        if (count == 0) {
            return null; // 一个维度都没给分：宁可返回 null 暴露问题，不编造 0 分
        }
        return total / count;
    }
}
