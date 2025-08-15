package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;
import org.springframework.core.env.Environment;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class FileScannerTest {

    @TempDir
    Path tempDir;

    @Test
    public void testScanRootAndFetchSnippets() throws Exception {
        // create a sample file
        Path js = tempDir.resolve("webpack.config.js");
        String content = "console.log('hello');\nfunction test() { return 1; }";
        Files.write(js, content.getBytes());

        // mock env and vectorService
        Environment env = Mockito.mock(Environment.class);
        Mockito.when(env.getProperty("scanner.root.path")).thenReturn(tempDir.toString());
        Mockito.when(env.getProperty("application.id", "default-app")).thenReturn("app1");

        VectorService vs = Mockito.mock(VectorService.class);

        FileScanner scanner = new FileScanner(env, vs);
        scanner.scanRoot();

        List<QueryModels.CodeSnippet> got = scanner.fetchSnippets(null, 10);
        assertThat(got).isNotEmpty();
        assertThat(got.get(0).getPath()).isEqualTo("webpack.config.js");
        assertThat(got.get(0).getContent()).contains("console.log('hello')");

        // ensure upsert was called at least once (chunking)
        Mockito.verify(vs, Mockito.atLeastOnce()).upsert(Mockito.eq("app1"), Mockito.eq("webpack.config.js"), Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt(), Mockito.anyInt());
    }
}
