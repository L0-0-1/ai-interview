package com.study.interview.controller;

import com.study.interview.service.ChatService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {
    public final ChatService chatService;

    public ChatController(ChatService chatService) {
        this.chatService = chatService;
    }
    @GetMapping("/simple")
    public String simple(@RequestParam(defaultValue = "用一句话介绍你自己") String message,
                         @RequestParam(defaultValue = "default") String conversationId) {
        return chatService.simple(message, conversationId);
    }
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> stream(@RequestParam(defaultValue = "讲讲 Spring Boot 自动配置原理") String message,
                               @RequestParam(defaultValue = "default") String conversationId) {
        return chatService.stream(message, conversationId);
    }
}
