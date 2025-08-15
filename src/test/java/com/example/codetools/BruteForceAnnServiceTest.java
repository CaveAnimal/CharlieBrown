package com.example.codetools;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class BruteForceAnnServiceTest {

    @Test
    public void addAndQueryReturnsNearest() throws Exception {
        BruteForceAnnService ann = new BruteForceAnnService();
        float[] v1 = new float[] {1.0f, 0.0f};
        float[] v2 = new float[] {0.0f, 1.0f};
        float[] q = new float[] {0.9f, 0.1f};

        ann.add("id1", v1);
        ann.add("id2", v2);

        List<String> res = ann.query(q, 2);
        assertThat(res).isNotEmpty();
        assertThat(res.get(0)).isEqualTo("id1");
        assertThat(res).containsExactly("id1", "id2");

        ann.remove("id1");
        List<String> res2 = ann.query(q, 2);
        assertThat(res2).containsExactly("id2");
    }
}
