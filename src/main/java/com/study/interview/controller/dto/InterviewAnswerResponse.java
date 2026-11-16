package com.study.interview.controller.dto;

/** 会话结束时 status 为 ENDED，question 为空字符串。 */
public record InterviewAnswerResponse(Long sessionId, Integer currentRound, String status, String question) {}
