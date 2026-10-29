package com.study.interview.config;

import org.springframework.ai.document.Document;
import org.springframework.ai.reader.TextReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 语料入库：应用启动时把 corpus/bagu.md 读进来 → 切块 → embedding → 写进 Redis 向量库。
 *
 * 它是 RAG 的「前半段」（语料 → 切分 → 嵌入 → 存储），
 * 配合 AskService 的「后半段」（检索 → 拼 Prompt → 问模型）组成完整闭环。
 *
 * 为什么是 CommandLineRunner：
 *   应用启动、所有 Bean 装配完成后自动执行 run()，正好用来做「启动时灌数据」。
 *   和 @PostConstruct 的区别：CommandLineRunner 在所有 Bean 就绪后跑，
 *   此时 VectorStore（向量库）已经装配好了，可以安全地往里写。
 *
 * 为什么注入 VectorStore 接口而不是 RedisVectorStore 具体类：
 *   这段入库逻辑不关心向量库具体是 Redis 还是别的（pgvector/内存），
 *   面向接口写，将来换存储实现只改配置、不改这段代码。
 *
 * ⚠️ 每次启动都会重新入库一遍，重复数据会污染检索结果。
 *    所以再跑之前先在 redis-cli 里执行 FLUSHDB 清空本地向量库（学习库随便清）。
 *    生产环境不会用 CommandLineRunner 干这事，而是单独的入库任务 + 幂等去重。
 */
@Component
public class CorpusIngest implements CommandLineRunner {

    private final VectorStore vectorStore;

    public CorpusIngest(VectorStore vectorStore) {
        this.vectorStore = vectorStore;
    }

    @Override
    public void run(String... args) {

        List<Document> wholeFile = new TextReader(new ClassPathResource("corpus/bagu.md")).get();

        // 第 2 段（切分）：一篇知识太长，模型塞不下、检索也定位不准，所以切成小块。
        // 用无参构造 = 全用默认值（1.1.0 默认：一块最多 800 token，尾块不足 350 字符并进上一块，
        // 短于 5 字符不嵌入，最多 10000 块，保留换行）。
        TokenTextSplitter splitter = new TokenTextSplitter();
        List<Document> chunks = splitter.split(wholeFile);

        // 第 3+4 段（嵌入 + 存储）：add() 内部一次做完——向量库自带嵌入模型和分批策略，
        // 入库时自动「批量转向量 + 写进 Redis」。
        vectorStore.add(chunks);
    }
}
