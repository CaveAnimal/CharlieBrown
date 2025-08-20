package com.example.codetools;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Safe test: persist to a temporary directory instead of touching the project's ./data directory.
 */
public class HnswAutoPersistTest {

    @TempDir
    Path tmp;

    @Test
    public void createAndPersistIndex() throws Exception {
        HnswAnnService h = new HnswAnnService(4, 16, 100, 10);
        float[] v = new float[] {1.0f, 0.0f, 0.0f};
        h.add("app:one:0", v);
        java.nio.file.Path out = tmp.resolve("ann-index.idx");
        java.nio.file.Files.createDirectories(out.getParent());
        h.persistTo(out);

        assertThat(java.nio.file.Files.exists(out)).isTrue();
        assertThat(java.nio.file.Files.size(out)).isGreaterThan(0);
    }
}
