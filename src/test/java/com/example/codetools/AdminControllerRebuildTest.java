package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(properties = {"spring.jpa.hibernate.ddl-auto=create-drop", "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("ann-dev")
@AutoConfigureMockMvc
public class AdminControllerRebuildTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private VectorService vectorService;

    @Autowired
    private AnnIndex annIndex;

    @Autowired
    private VectorRepository repo;

    @Test
    public void rebuildEndpointRepopulatesIndex() throws Exception {
        String app = "admin-rebuild";
        vectorService.upsert(app, "a", "one two three");
        vectorService.upsert(app, "b", "four five six");

        // ensure repo has entries
        long cnt = repo.count();
        assertThat(cnt).isGreaterThanOrEqualTo(2);

        // clear in-memory index
        if (annIndex instanceof BruteForceAnnService) {
            BruteForceAnnService b = (BruteForceAnnService) annIndex;
            for (String id : repo.findAll().stream().map(r -> r.getId()).toList()) b.remove(id);
            assertThat(b.size()).isEqualTo(0);
        }

        mvc.perform(post("/api/admin/ann/rebuild")).andExpect(status().isOk());

        // after rebuild, size should be >= repo.count()
        long size = annIndex.size();
        assertThat(size).isGreaterThanOrEqualTo(cnt);
    }
}
