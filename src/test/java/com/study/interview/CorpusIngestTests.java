package com.study.interview;

import com.study.interview.config.CorpusIngest;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.VectorStore;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;

class CorpusIngestTests {
    @Test
    void importsOnlyTheSelectedResourcesWithStableChunkIds() {
        VectorStore store = mock(VectorStore.class);
        List<List<Document>> writes = new ArrayList<>();
        doAnswer(call -> {
            writes.add(List.copyOf(call.getArgument(0)));
            return null;
        }).when(store).add(anyList());
        var ingest = new CorpusIngest(store);

        ingest.run();
        ingest.run();

        assertThat(writes).hasSize(4).allSatisfy(batch -> assertThat(batch).isNotEmpty());
        assertThat(writes.get(0)).allSatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry("source", "corpus/javaguide/redis-questions-01.md"));
        assertThat(writes.get(1)).allSatisfy(chunk -> assertThat(chunk.getMetadata())
                .containsEntry("source", "corpus/javaguide/redis-questions-02.md"));
        assertThat(writes.get(0)).extracting(Document::getId)
                .containsExactlyElementsOf(writes.get(2).stream().map(Document::getId).toList());
        assertThat(writes.get(1)).extracting(Document::getId)
                .containsExactlyElementsOf(writes.get(3).stream().map(Document::getId).toList());
    }
}
