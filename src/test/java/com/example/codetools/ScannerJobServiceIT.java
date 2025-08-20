package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
public class ScannerJobServiceIT {

    @TempDir
    static Path tmp;

    @Autowired
    private ScannerJobService scannerJobService;

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("scanner.root.path", () -> tmp.toString());
        r.add("application.id", () -> "it-app");
    }

    @Test
    public void testIntegrationJobRuns() throws Exception {
        // create a few small files in tmp
        Path a = tmp.resolve("one.txt");
        Path b = tmp.resolve("two.js");
        Files.writeString(a, "hello world");
        Files.writeString(b, "console.log('x')");

        String jobId = scannerJobService.startJob();
        assertThat(jobId).isNotNull();

        // wait for some progress
        TimeUnit.MILLISECONDS.sleep(300);
        Map<String,Object> s = scannerJobService.status();
        assertThat((Integer) s.get("totalFiles")).isGreaterThanOrEqualTo(2);

        // cancel to end quickly
        scannerJobService.cancel();
    }
}
