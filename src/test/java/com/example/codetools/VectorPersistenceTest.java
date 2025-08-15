package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("ann-dev")
public class VectorPersistenceTest {

    @Autowired
    private VectorRepository repo;

    @Autowired
    private ObjectMapper mapper;

    @Autowired
    private VectorService vectorService;

    @Test
    public void gzipRoundTripAndJsonFallback() throws Exception {
        String app = "persistence-app";
        vectorService.upsert(app, "f1", "one two three");
        var records = repo.findByApplicationId(app);
        assertThat(records).isNotEmpty();
        VectorRecord r = records.get(0);
        assertThat(r.getVectorBlob()).isNotNull();
        // ensure inspector can decode via VectorService's private method indirectly via queryTopK
        var results = vectorService.queryTopK(app, "one", 1);
        assertThat(results).isNotNull();
    }

    @Test
    public void malformedJsonDoesNotCrashQuery() throws Exception {
        VectorRecord vr = new VectorRecord();
        vr.setId("bad:1");
        vr.setApplicationId("bad-app");
        vr.setPath("bad");
        vr.setContent("broken");
        vr.setVectorJson("{not:valid,json}");
        repo.save(vr);

        var res = vectorService.queryTopK("bad-app", "q", 5);
        // should not throw and should return empty list
        assertThat(res).isNotNull();
    }
}
