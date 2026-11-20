package com.study.interview.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;

/** 按需将 JavaGuide 面试选节追加到现有向量库。 */
@Component
@ConditionalOnProperty(name = "interview.rag.import-javaguide", havingValue = "true")
public class CorpusIngest implements CommandLineRunner {
    private static final Logger log = LoggerFactory.getLogger(CorpusIngest.class);
    private static final List<String> SOURCES = List.of(
            "corpus/javaguide/redis-questions-01.md",
            "corpus/javaguide/redis-questions-02.md");

    private final VectorStore vectorStore;

    public CorpusIngest(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {
        TokenTextSplitter splitter = new TokenTextSplitter();
        for (String source : SOURCES) {
            List<Document> chunks = splitter.split(new TextReader(new ClassPathResource(source)).get())
                    .stream().map(chunk -> withSourceId(source, chunk)).toList();
            vectorStore.add(chunks);
            log.info("JavaGuide 语料导入完成，文件 {}，{} 个分块", source, chunks.size());
        }
    }

    private Document withSourceId(String source, Document chunk) {
        // 相同来源和正文得到同一个 ID，重复导入会覆盖同一条向量记录。
        String id = UUID.nameUUIDFromBytes((source + "\n" + chunk.getText())
                .getBytes(StandardCharsets.UTF_8)).toString();
        var metadata = new HashMap<>(chunk.getMetadata());
        metadata.put("source", source);
        return new Document(id, chunk.getText(), metadata);
    }
}
