package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@SpringBootTest
@ActiveProfiles("ann-hnsw")
public class HnswAnnServiceTest {

    @Autowired
    private AnnIndex annIndex;

    @Test
    public void persistAndLoad() throws Exception {
        if (!(annIndex instanceof HnswAnnService)) return;
        HnswAnnService h = (HnswAnnService) annIndex;
        h.add("x:1", new float[]{1f,0f,0f});
        h.add("x:2", new float[]{0f,1f,0f});
        assertThat(h.size()).isEqualTo(2);

        Path tmp = Files.createTempFile("hnsw", ".dat");
        h.persistTo(tmp);

        // create a fresh instance by clearing store via remove
        h.remove("x:1");
        h.remove("x:2");
        assertThat(h.size()).isEqualTo(0);

        h.loadFrom(tmp);
        assertThat(h.size()).isEqualTo(2);

        List<String> res = h.query(new float[]{1f,0f,0f}, 1);
        assertThat(res).isNotEmpty();
        assertThat(res.get(0)).isEqualTo("x:1");

        Files.deleteIfExists(tmp);
    }
}
