package com.study.interview.controller.dto;

import java.time.LocalDateTime;

public record InterviewHistoryItem(Long id, String targetPosition, LocalDateTime createTime,
                                   String status, Integer currentRound) {}
