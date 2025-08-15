package com.example.codetools;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

@Service
public class FileScanner {

    private static final Logger log = LoggerFactory.getLogger(FileScanner.class);

    private final Map<String, String> index = new HashMap<>();
    private final String rootPath;
    private final String applicationId;

    private final VectorService vectorService;

    public FileScanner(org.springframework.core.env.Environment env, VectorService vectorService) {
    this.rootPath = env.getProperty("scanner.root.path", "");
        this.applicationId = env.getProperty("application.id", "default-app");
        this.vectorService = vectorService;
    }

    @PostConstruct
    public void scanRoot() throws IOException {
        if (rootPath == null || rootPath.isBlank()) {
            log.info("scanner.root.path not set; skipping file scan");
            return;
        }
        Path root = Paths.get(rootPath);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".java") || p.toString().endsWith(".js"))
                    .forEach(this::indexFile);
        }
        log.info("Indexed {} files", index.size());
    }

    private void indexFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file));
            Path root = Paths.get(rootPath);
            String rel = root.relativize(file).toString();
            index.put(rel, content);

            // chunking parameters (characters)
            int chunkSize = 800; // default
            int overlap = 200; // default
            try {
                String cs = System.getProperty("scanner.chunk.size");
                String ov = System.getProperty("scanner.chunk.overlap");
                if (cs != null) chunkSize = Integer.parseInt(cs);
                if (ov != null) overlap = Integer.parseInt(ov);
            } catch (Exception ignored) {}

            // create overlapping chunks
            int len = content.length();
            int idx = 0;
            int chunkIndex = 0;
            while (idx < len) {
                int end = Math.min(idx + chunkSize, len);
                String chunk = content.substring(idx, end);
                try {
                    vectorService.upsert(applicationId, rel, chunk, chunkIndex, idx, end);
                } catch (Exception ex) {
                    log.warn("Vector upsert failed for {} chunk {}: {}", rel, chunkIndex, ex.getMessage());
                }
                chunkIndex++;
                if (end == len) break;
                idx = Math.max(0, end - overlap);
            }

        } catch (IOException e) {
            log.warn("Failed to read {}", file, e);
        }
    }

    public List<QueryModels.CodeSnippet> fetchSnippets(List<String> paths, int max) {
        return index.entrySet()
                .stream()
                .filter(e -> paths == null || paths.isEmpty() || paths.contains(e.getKey()))
                .limit(max)
                .map(e -> {
                    QueryModels.CodeSnippet s = new QueryModels.CodeSnippet();
                    s.setPath(e.getKey());
                    s.setContent(e.getValue());
                    return s;
                })
                .collect(Collectors.toList());
    }
}