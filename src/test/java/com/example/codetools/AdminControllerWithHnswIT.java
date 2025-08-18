package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Import(AdminControllerWithHnswIT.HnswTestConfig.class)
@Tag("integration")
public class AdminControllerWithHnswIT {

    @TestConfiguration
    static class HnswTestConfig {
        @Bean
        public AnnIndex hnswAnnService() {
            // use package-private constructor to set small test params
            return new HnswAnnService(8, 50, 200, 100);
        }
    }

    @Autowired
    org.springframework.test.web.servlet.MockMvc mvc;

    @Autowired
    AnnIndex annIndex;

    @TempDir
    Path tmp;

    @Test
    public void hnswIntegrationFlow() throws Exception {
        // add a couple of small vectors via the AnnIndex directly
        float[] a = new float[] {1f, 0f, 0f, 0f};
        float[] b = new float[] {0f, 1f, 0f, 0f};
        annIndex.add("a", a);
        annIndex.add("b", b);

        // query should return nearest neighbour
        java.util.List<String> res = annIndex.query(new float[] {1f, 0f, 0f, 0f}, 1);
        assertThat(res).isNotEmpty();
        assertThat(res.get(0)).isEqualTo("a");

        // persist to a temp file
        Path idx = tmp.resolve("test.idx");
        annIndex.persistTo(idx);
        assertThat(java.nio.file.Files.exists(idx)).isTrue();

        // load into a fresh instance via the same bean's loadFrom (roundtrip)
        annIndex.loadFrom(idx);
        assertThat(annIndex.size()).isGreaterThanOrEqualTo(2);

        // persist/load stability: reload several times and ensure size and query results remain stable
    long initialSize = annIndex.size();
        for (int i = 0; i < 3; i++) {
            annIndex.loadFrom(idx);
            assertThat(annIndex.size()).isEqualTo(initialSize);
            java.util.List<String> res2 = annIndex.query(new float[] {1f, 0f, 0f, 0f}, 1);
            assertThat(res2).isNotEmpty();
            assertThat(res2.get(0)).isEqualTo("a");
        }

        // persist again to a second file and ensure roundtrip still works
        Path idx2 = tmp.resolve("test2.idx");
        annIndex.persistTo(idx2);
        assertThat(java.nio.file.Files.exists(idx2)).isTrue();
        annIndex.loadFrom(idx2);
    assertThat(annIndex.size()).isEqualTo(initialSize);

        // call admin endpoints to ensure they work with real HNSW
        String health = mvc.perform(get("/api/admin/ann/health")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(health).contains("size");

        String params = mvc.perform(get("/api/admin/ann/params")).andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        assertThat(params).contains("m");

        // reconfigure (small values)
        mvc.perform(post("/api/admin/ann/params").param("m", "6").param("efConstruction", "30").param("maxItems", "100")).andExpect(status().isOk());
    }
}
