package com.example.codetools;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.util.Arrays;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

public class ScannerJobServiceUnitTest {

    @Test
    public void testStartPauseResumeCancelWithMockScanner() throws Exception {
        FileScanner mockScanner = Mockito.mock(FileScanner.class);
        Mockito.when(mockScanner.listAllFiles()).thenReturn(Arrays.asList("a.txt", "b.txt", "c.txt"));

        // make processFile a short sleep to simulate work
        Mockito.doAnswer(invocation -> {
            Thread.sleep(100);
            return null;
        }).when(mockScanner).processFile(Mockito.anyString());

        ScannerJobService svc = new ScannerJobService(mockScanner);

        String jobId = svc.startJob();
        assertThat(jobId).isNotNull();

        // wait a bit for progress
        TimeUnit.MILLISECONDS.sleep(150);
    java.util.Map<String,Object> st = svc.status();
    assertThat((Integer) st.get("processedFiles")).isGreaterThanOrEqualTo(1);

        // pause
        boolean paused = svc.pause();
        assertThat(paused).isTrue();
        TimeUnit.MILLISECONDS.sleep(200);
        int processedWhenPaused = (Integer) svc.status().get("processedFiles");

        // resume
        boolean resumed = svc.resume();
        assertThat(resumed).isTrue();
        TimeUnit.MILLISECONDS.sleep(400);

        // cancel (may be already finished)
        svc.cancel();

    java.util.Map<String,Object> finalStatus = svc.status();
    int processed = (Integer) finalStatus.get("processedFiles");
        assertThat(processed).isGreaterThanOrEqualTo(processedWhenPaused);
        assertThat((Integer) finalStatus.get("totalFiles")).isEqualTo(3);
    }
}
