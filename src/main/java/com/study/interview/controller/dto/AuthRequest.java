package com.study.interview.controller.dto;

public record AuthRequest(String username, String password) {

    @Override
    public String toString() {
        return "AuthRequest[username=" + username + "]";
    }
}
