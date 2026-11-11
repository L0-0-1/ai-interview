package com.study.interview.model;

/**
 * 提交回答接口的入参（与 InterviewStartRequest 同款的普通请求体类）。
 *
 * 两个字段都由 Service 层校验（与项目「校验属于业务，放 Service」同款边界）：
 *   roundNo     —— 必须为正整数（与 status 一起决定请求是否过期/超前）；
 *   answerText  —— 非空且不超过 2000 字（调模型前拦截，省真金白银）。
 */
public class InterviewAnswerRequest {

    /** 提交的是第几轮的回答，从 1 开始 */
    private Integer roundNo;

    /** 候选人的回答文本 */
    private String answerText;

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getAnswerText() {
        return answerText;
    }

    public void setAnswerText(String answerText) {
        this.answerText = answerText;
    }
}
