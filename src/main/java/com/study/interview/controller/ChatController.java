package com.study.interview.controller;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

@RestController
@RequestMapping("/api/chat")
public class ChatController {

    private final ChatClient chatClient;

    // 「对话记录本」，就是 ChatClientConfig 里 @Bean 声明的那个。
    // Spring 启动时看到构造器需要它，会自动把保险柜里同类型的 Bean 塞进来（依赖注入，和 chatClient 同一个机制）
    private final ChatMemory chatMemory;

    // 构造器注入。注意拿的是配置好的 ChatClient，不是 Builder
    public ChatController(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }
    @GetMapping("/simple")
    public String simple(@RequestParam(defaultValue = "用一句话介绍你自己") String message,
                         @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                // 挂拦截者，顺便把「这次用哪页记录」直接告诉它（builder 自带 conversationId 方法，
                // javap 实测存在）——比 Consumer 面板写法短一半，效果相同（mvn compile 验证）
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(conversationId)
                        .build())
                .user(message)
                .call()
                .content();
    }
    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE + ";charset=UTF-8")
    public Flux<String> stream(@RequestParam(defaultValue = "讲讲 Spring Boot 自动配置原理") String message,
                               @RequestParam(defaultValue = "default") String conversationId) {
        return chatClient.prompt()
                // 接记忆：与 /simple 完全相同的三行（本子递给拦截者 + 报上页码）
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(conversationId)
                        .build())
                .user(message)
                .stream()
                .content()
                // 给每个词元垫一个「牺牲空格」，吸收 SSE 协议的开头空格剥离规则
                .map(chunk -> " " + chunk);
    }
}
