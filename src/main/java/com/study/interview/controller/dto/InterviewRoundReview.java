package com.study.interview.controller.dto;

public record InterviewRoundReview(int roundNo, Status status, Long answerMessageId, String quote,
                                   String feedback, String missingPoint, Long practiceQuestionId,
                                   String practiceQuestion) {
    public enum Status {
        REVIEWED,
        INSUFFICIENT_EVIDENCE
    }
}
