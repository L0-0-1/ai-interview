package com.study.interview.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AskService {
    private final VectorStore vectorStore;
    private final ChatClient chatClient;
    public AskService(VectorStore vectorStore, ChatClient chatClient) {
        this.vectorStore = vectorStore;
        this.chatClient = chatClient;
    }

    public String ask(String question) {
        // builder 是静态方法，必须 SearchRequest.builder() 起步，且 build() 的成品要接住；
        // 原来的写法 build() 返回值被丢弃，0.5 门槛和 topK=3 根本没生效
        SearchRequest searchRequest = SearchRequest.builder()
                .query(question)
                .topK(3)
                .similarityThreshold(0.5)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        // 把命中的资料块拼成文本返回；下一步在此拼接 Prompt 交给 ChatClient，对应 RagProbe 的块 C
        StringBuilder corpus = new StringBuilder();
        for (int i = 0; i < documents.size(); i++) {
            corpus.append("【资料").append(i + 1).append("】\n")
                    .append(documents.get(i).getText())
                    .append("\n\n");
        }
        return chatClient.prompt()
                .system("你是一个助手，根据给定的资料回答问题,资料没有的就说不知道，不要编造。")
                .user(corpus.toString()+"\n"+question)
                .call()
                .content();
    }
}
