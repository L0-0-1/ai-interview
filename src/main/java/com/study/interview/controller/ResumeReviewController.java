package com.study.interview.controller;

import com.study.interview.model.ResumeReview;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.awt.*;

@RestController
public class ResumeReviewController {
    ChatClient chatClient;
    public ResumeReviewController(ChatClient chatClient) {
        this.chatClient = chatClient;
    }
    @GetMapping(value = "/api/chat/resume",produces = MediaType.APPLICATION_JSON_VALUE+";charset=UTF-8")
    public ResumeReview getResumeReview(String resumeText) {
        return chatClient.prompt()
                .user("请评审这份简历：\n\n" + resumeText)
                .call()
                .entity(ResumeReview.class);
    }
}
