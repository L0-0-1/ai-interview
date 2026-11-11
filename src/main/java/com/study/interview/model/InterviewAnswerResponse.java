package com.study.interview.model;

/**
 * 提交回答接口的出参（与 InterviewStartResponse 同款的 record 快照）。
 *
 * 为什么 question 用空字符串而不是 null 表达「没有下一题」：
 *   只有最后一轮（轮数上限兜底）才没有下一题——该场景同时带 status=ENDED；
 *   question="" 保持字段类型语义稳定，前端判断面试结束以 status 为准。
 */
public record InterviewAnswerResponse(
        /** 面试会话 id */
        Long sessionId,
        /** 提交并推进后的当前轮次：普通轮次 = 提交轮次 +1；最后一轮 = 留在最后一轮 */
        Integer currentRound,
        /** 会话状态：ONGOING 继续等待下一轮 / ENDED 已达轮数上限结束 */
        String status,
        /** 下一道面试题：普通轮次为模型生成的追问；最后一轮为空字符串 */
        String question
) {
}
