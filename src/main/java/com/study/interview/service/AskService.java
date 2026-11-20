package com.study.interview.service;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class AskService {

    private static final Logger log = LoggerFactory.getLogger(AskService.class);

    // 检索参数提取成常量：排障时要反复调这两个值，散落在方法里不好对照
    private static final int TOP_K = 4;

    // 相似度门槛。实测命中块的 score 都在 0.8 以上，0.5 离得很远，拦不到真命中的块，
    // 所以「资料里有却答不知道」不是它造成的（见命中日志）。这里仍然是 0.5，
    // 但降下去也没用——真正的诱因是入库重复，让 topK=3 全被同一块的副本吃掉。
    private static final double SIMILARITY_THRESHOLD = 0.5;

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
                .topK(TOP_K)
                .similarityThreshold(SIMILARITY_THRESHOLD)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);

        // 检索这一段以前是完全「黑盒」：命中 0 条和命中 3 条，日志里一模一样，
        // 只能靠模型的回答反推。打出来才知道是「没检索到」还是「检索到了但模型不用」。
        log.info("RAG 检索：question={}，命中 {} 条（topK={}，threshold={}）",
                question, documents.size(), TOP_K, SIMILARITY_THRESHOLD);
        for (int i = 0; i < documents.size(); i++) {
            String text = documents.get(i).getText();
            String brief = text == null ? "" : text.replaceAll("\\s+", " ");
            log.info("  命中[{}] score={} 摘要={}", i + 1, documents.get(i).getScore(),
                    brief.substring(0, Math.min(60, brief.length())));
        }

        // 一条都没命中时直接说清楚，不要再花一次模型调用的钱去换一句「不知道」
        if (documents.isEmpty()) {
            return "向量库没有检索到相关资料（请确认 corpus/bagu.md 已入库、或相似度门槛过高）。";
        }

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
