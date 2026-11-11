package com.study.interview.model;

/**
 * 开始面试接口的出参。
 *
 * 为什么用 record 而不是普通 class：
 *   1. 出参是「一次性快照」：会话建完、第一题生成后不再修改，不可变正合适；
 *   2. record 自动获得构造器、访问器和 toString，本项目无 Lombok，不用手写样板；
 *   3. Boot 自带 Jackson 原生支持 record 序列化（与 ResumeReview 同款选型）。
 *
 * 与 InterviewSession（普通 class 实体）的分工别混：
 * 实体是数据库行的形状（要 setter 灌值）；出参是接口响应形状（只读快照）。
 */
public record InterviewStartResponse(
        /** 面试会话 id，后续回答/追问流程凭它推进同一场面试 */
        Long sessionId,
        /** 第一道面试题（模型生成，已校验非空才返回） */
        String question,
        /** 当前轮次：开始面试固定为 1 */
        Integer currentRound
) {
}
