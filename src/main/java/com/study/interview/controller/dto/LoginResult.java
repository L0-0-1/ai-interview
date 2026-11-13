package com.study.interview.controller.dto;

public record LoginResult(Long userId, String token) {

    @Override
    public String toString() {
        return "LoginResult[userId=" + userId + "]";
    }
}
