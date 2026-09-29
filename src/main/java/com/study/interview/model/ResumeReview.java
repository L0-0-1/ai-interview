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
 */
public record ResumeReview(

        @JsonPropertyDescription("候选人姓名；如果简历里没有提及，就填「未知」")
        String name,

//用包装类防止返回int为空时会直接显示0，造成歧义，用Integer则直接为null
        @JsonPropertyDescription("综合评分，0-100 之间的整数，60 分以下表示不建议通过初筛")
        Integer score,

        @JsonPropertyDescription("简历的优点列表，每条一句话，最多 3 条")
        List<String> strengths,

        @JsonPropertyDescription("简历的不足或可疑点列表，每条一句话，最多 3 条")
        List<String> weaknesses,

        @JsonPropertyDescription("一句话总结对这份简历的整体评价，不超过 50 字")
        String summary

) {
}
