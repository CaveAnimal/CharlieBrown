package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("ann-dev")
public class AnnRebuildIntegrationTest {

    @Autowired
    private VectorService vectorService;

    @Autowired
    private AnnIndex annIndex;

    @Autowired
    private VectorRepository repo;

    @Test
    public void rebuildLoadsPersistedVectors() throws Exception {
        String app = "rebuild-app";
        // upsert two vectors
        vectorService.upsert(app, "p1", "alpha bravo charlie");
        vectorService.upsert(app, "p2", "delta echo foxtrot");

        // ensure ann index size reflects adds
        long before = annIndex.size();
        assertThat(before).isGreaterThanOrEqualTo(2);

        // clear the in-memory store by removing entries (simulate restart)
        // if the implementation provides no clear, we remove by id from repo and call rebuild
        if (annIndex instanceof BruteForceAnnService) {
            BruteForceAnnService b = (BruteForceAnnService) annIndex;
            // remove all entries via brute force API
            for (String id : repo.findAll().stream().map(r -> r.getId()).toList()) b.remove(id);
            assertThat(b.size()).isEqualTo(0);
            // call rebuild
            annIndex.rebuildFromDatabase();
            // after rebuild, the store should be repopulated
            assertThat(b.size()).isGreaterThanOrEqualTo(2);
            // query for one of the items
            float[] q = new float[]{0f};
            List<String> res = annIndex.query(q, 5);
            assertThat(res).isNotNull();
        }
    }
}
