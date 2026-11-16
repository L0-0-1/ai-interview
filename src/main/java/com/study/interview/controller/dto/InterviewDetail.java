package com.study.interview.controller.dto;

import java.time.LocalDateTime;
import java.util.List;

public record InterviewDetail(InterviewSessionSummary session, List<Message> messages) {

    public record Message(Long id, String role, Integer roundNo, String content, LocalDateTime createTime) {}
}
