package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.TestPropertySource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@SpringBootTest
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:h2:mem:testdb;DB_CLOSE_DELAY=-1",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "scanner.root.path=.",
})
public class VectorServiceTest {

    @Autowired
    private VectorService vectorService;

    @Autowired
    private VectorRepository repo;

    @MockBean
    private EmbeddingService embeddingService;

    @MockBean
    private AnnService annService;

    @Test
    public void testUpsertPersistsVector() throws Exception {
        when(embeddingService.embed("hello")).thenReturn(new float[]{0.1f, 0.2f, 0.3f});

        vectorService.upsert("app1", "file.js", "hello", 0, 0, 5);

        String id = "app1:file.js:0";
        VectorRecord r = repo.findById(id).orElse(null);
        assertThat(r).isNotNull();
        assertThat(r.getVectorJson()).contains("0.1");
        assertThat(r.getVectorBlob()).isNotNull();

        // verify gzip roundtrip of stored blob to floats
        byte[] blob = r.getVectorBlob();
        // decompress using same logic as VectorService
        try (java.util.zip.GZIPInputStream gzis = new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(blob));
             java.io.DataInputStream dis = new java.io.DataInputStream(gzis)) {
            int len = dis.readInt();
            float[] arr = new float[len];
            for (int i = 0; i < len; i++) arr[i] = dis.readFloat();
            assertThat(arr).hasSize(3);
            assertThat(arr[0]).isEqualTo(0.1f);
            assertThat(arr[1]).isEqualTo(0.2f);
            assertThat(arr[2]).isEqualTo(0.3f);
        }
    }
}
