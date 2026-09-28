package com.study.interview.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.ai.chat.memory.MessageWindowChatMemory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

// 把 ChatClient 做成 Bean 交给 Spring 管理,Controller 直接注入即可,
// 避免每个接口都 new 一个 ChatClient、重复配置 system prompt
@Configuration
public class ChatClientConfig {
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder.defaultSystem("你是一个Java老师,说话简短")
                .build();
    }
    @Bean//用于记录对话历史
    public ChatMemory chatMemory() {
        return MessageWindowChatMemory
                .builder()
                .maxMessages(20)
                .build();
    }
}
