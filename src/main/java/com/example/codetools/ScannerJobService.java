package com.example.codetools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
@Service
public class ScannerJobService {

    private final FileScanner fileScanner;

    // single-threaded executor for scanning to avoid overwhelming the embedder
    private final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "scanner-job-thread");
        t.setDaemon(true);
        return t;
    });

    // current job state (only one job at a time for simplicity)
    private volatile String currentJobId = null;
    private volatile Instant startedAt = null;
    private volatile Instant finishedAt = null;
    private volatile AtomicInteger totalFiles = new AtomicInteger(0);
    private volatile AtomicInteger processedFiles = new AtomicInteger(0);
    private final AtomicBoolean paused = new AtomicBoolean(false);
    private final AtomicBoolean cancelled = new AtomicBoolean(false);

    private volatile Future<?> currentFuture = null;

    public ScannerJobService(FileScanner fileScanner) {
        this.fileScanner = fileScanner;
    }

    public synchronized String startJob() throws IOException {
        if (currentJobId != null && currentFuture != null && !currentFuture.isDone()) {
            return currentJobId; // job already running
        }

        // reset state
        this.currentJobId = UUID.randomUUID().toString();
        this.startedAt = Instant.now();
        this.finishedAt = null;
        this.totalFiles.set(0);
        this.processedFiles.set(0);
        this.paused.set(false);
        this.cancelled.set(false);

        // collect file list
        java.util.List<String> files = fileScanner.listAllFiles();
        this.totalFiles.set(files.size());

        this.currentFuture = executor.submit(() -> {
            log.info("Scanner job {} started, files={}.", currentJobId, files.size());
            try {
                for (String rel : files) {
                    if (cancelled.get()) {
                        log.info("Scanner job {} cancelled.", currentJobId);
                        break;
                    }
                    // pause support
                    while (paused.get()) {
                        try { Thread.sleep(200); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                    }
                    try {
                        fileScanner.processFile(rel);
                    } catch (Exception e) {
                        log.warn("Failed processing {}: {}", rel, e.getMessage());
                    }
                    processedFiles.incrementAndGet();
                }
            } finally {
                finishedAt = Instant.now();
                log.info("Scanner job {} finished. processed={}/{}", currentJobId, processedFiles.get(), totalFiles.get());
            }
        });

        return currentJobId;
    }

    public synchronized boolean pause() {
        if (currentJobId == null) return false;
        paused.set(true);
        return true;
    }

    public synchronized boolean resume() {
        if (currentJobId == null) return false;
        paused.set(false);
        return true;
    }

    public synchronized boolean cancel() {
        if (currentJobId == null) return false;
        cancelled.set(true);
        if (currentFuture != null) currentFuture.cancel(true);
        return true;
    }

    public Map<String, Object> status() {
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        out.put("jobId", currentJobId);
        out.put("startedAt", startedAt == null ? null : startedAt.toString());
        out.put("finishedAt", finishedAt == null ? null : finishedAt.toString());
        out.put("totalFiles", totalFiles.get());
        out.put("processedFiles", processedFiles.get());
        out.put("paused", paused.get());
        out.put("cancelled", cancelled.get());
        out.put("running", currentFuture != null && !currentFuture.isDone());
        return out;
    }

}
