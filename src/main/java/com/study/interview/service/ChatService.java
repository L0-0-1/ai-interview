package com.study.interview.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.advisor.MessageChatMemoryAdvisor;
import org.springframework.ai.chat.memory.ChatMemory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
@Service
public class ChatService {

    private final ChatClient chatClient;

    // 「对话记录本」，就是 ChatClientConfig 里 @Bean 声明的那个。
    // Spring 启动时看到构造器需要它，会自动把保险柜里同类型的 Bean 塞进来（依赖注入，和 chatClient 同一个机制）
    private final ChatMemory chatMemory;

    // 构造器注入。注意拿的是配置好的 ChatClient，不是 Builder
    public ChatService(ChatClient chatClient, ChatMemory chatMemory) {
        this.chatClient = chatClient;
        this.chatMemory = chatMemory;
    }
    public String simple(String message, String conversationId) {
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
    public Flux<String> stream(String message, String conversationId) {
        return chatClient.prompt()
                .advisors(MessageChatMemoryAdvisor.builder(chatMemory)
                        .conversationId(conversationId)
                        .build())
                .user(message)
                .stream()
                .content()
                .map(chunk -> " " + chunk);
    }
}
