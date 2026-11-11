package com.study.interview.model;

/**
 * 开始面试接口的入参（与 ResumeText 同款的普通请求体类）。
 *
 * 两个字段都由 Service 层校验非空且非全空白：
 * 校验属于业务，放 Service 不放 Controller（与 ResumeReviewService 的 isBlank 校验同款边界）。
 */
public class InterviewStartRequest {

    /** 简历文本，必填且不能全空白 */
    private String resumeText;

    /** 目标岗位，必填且不能全空白 */
    private String targetPosition;

    public String getResumeText() {
        return resumeText;
    }

    public void setResumeText(String resumeText) {
        this.resumeText = resumeText;
    }

    public String getTargetPosition() {
        return targetPosition;
    }

    public void setTargetPosition(String targetPosition) {
        this.targetPosition = targetPosition;
    }
}
