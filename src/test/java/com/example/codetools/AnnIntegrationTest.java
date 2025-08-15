package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("ann-dev")
public class AnnIntegrationTest {

    @Autowired
    private VectorService vectorService;

    @Autowired
    private AnnIndex annIndex;

    @Autowired
    private EmbeddingService embeddingService;

    @Test
    public void upsertAddsToAnnIndex() throws Exception {
        // simple deterministic embedding service is already the SimpleEmbeddingService in tests
        String app = "integration-app";
        vectorService.upsert(app, "p1", "content one");
        vectorService.upsert(app, "p2", "content two");

        // query via annIndex directly
        float[] q = embeddingService.embed("content one");
        List<String> res = annIndex.query(q, 2);
        assertThat(res).isNotEmpty();
        assertThat(res.get(0)).contains("integration-app:p1");
    }
}
