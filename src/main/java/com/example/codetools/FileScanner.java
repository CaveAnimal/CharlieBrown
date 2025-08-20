package com.example.codetools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

@Slf4j
@Service
public class FileScanner {

    // keep a lightweight index of path -> lastModified to avoid OOM on very large repos
    private final Map<String, Long> index = new HashMap<>();
    private final String rootPath;
    private final String applicationId;
    private final Set<String> extensions;

    private final VectorService vectorService;

    public FileScanner(org.springframework.core.env.Environment env, VectorService vectorService) {
        String rp = env.getProperty("scanner.root.path");
        this.rootPath = rp == null ? "" : rp;
        String appId = env.getProperty("application.id", "default-app");
        if ((appId == null || appId.isBlank() || "default-app".equals(appId)) && this.rootPath != null && !this.rootPath.isBlank()) {
            try {
                java.nio.file.Path p = java.nio.file.Paths.get(this.rootPath);
                java.nio.file.Path name = p.getFileName();
                if (name != null) appId = name.toString();
            } catch (Exception ignored) {}
        }
        this.applicationId = appId == null ? "default-app" : appId;
        this.vectorService = vectorService;
        // supported extensions (configurable)
        String ext = env.getProperty("scanner.extensions");
        if (ext != null && !ext.isBlank()) {
            this.extensions = Arrays.stream(ext.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(s -> s.startsWith(".") ? s.toLowerCase() : ("." + s.toLowerCase()))
                    .collect(Collectors.toSet());
        } else {
            this.extensions = new HashSet<>(Arrays.asList(
                    ".java", ".js", ".ts", ".jsx", ".tsx", ".py", ".go", ".rb", ".php",
                    ".html", ".htm", ".css", ".scss", ".json", ".xml", ".md", ".properties"
            ));
        }
    }

    @PostConstruct
    public void scanRoot() throws IOException {
        if (rootPath == null || rootPath.isBlank()) {
            log.info("scanner.root.path not set; skipping file scan");
            return;
        }
        // reuse the new list/process API so async job and legacy scan use the same logic
        List<String> files = listAllFiles();
        for (String rel : files) {
            processFile(rel);
        }
        log.info("Indexed {} files", index.size());
    }

    private void indexFile(Path file) {
        try {
            String content = new String(Files.readAllBytes(file));
            Path root = Paths.get(rootPath);
            String rel = root.relativize(file).toString();
            // store last-modified time only to avoid keeping large file contents in memory
            try { index.put(rel, Files.getLastModifiedTime(file).toMillis()); } catch (Exception ignored) { index.put(rel, 0L); }

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
        return index.keySet().stream()
                .filter(k -> paths == null || paths.isEmpty() || paths.contains(k))
                .limit(max)
                .map(k -> {
                    QueryModels.CodeSnippet s = new QueryModels.CodeSnippet();
                    s.setPath(k);
                    // read file content on demand
                    try {
                        java.nio.file.Path p = java.nio.file.Paths.get(rootPath).resolve(k);
                        String content = java.nio.file.Files.readString(p);
                        s.setContent(content);
                    } catch (Exception ex) {
                        s.setContent("");
                    }
                    return s;
                })
                .collect(Collectors.toList());
    }

    // expose computed applicationId for admin operations
    public String getApplicationId() {
        return this.applicationId;
    }

    /**
     * Return a list of relative file paths (relative to configured root) that match configured extensions.
     * This is used by the async scanner job to iterate files and report progress.
     */
    public List<String> listAllFiles() throws IOException {
        List<String> out = new ArrayList<>();
        if (rootPath == null || rootPath.isBlank()) return out;
        Path root = Paths.get(rootPath);
        try (Stream<Path> paths = Files.walk(root)) {
            paths.filter(Files::isRegularFile)
                    .forEach(p -> {
                        String s = p.toString().toLowerCase();
                        boolean ok = false;
                        for (String e : extensions) if (s.endsWith(e)) { ok = true; break; }
                        if (!ok) return;
                        try {
                            String rel = root.relativize(p).toString();
                            out.add(rel);
                        } catch (Exception ignored) {}
                    });
        }
        return out;
    }

    /**
     * Process a single file (relative path) by reading content and upserting vector chunks.
     * This reuses existing indexing/chunking logic.
     */
    public void processFile(String relativePath) {
        try {
            Path p = java.nio.file.Paths.get(rootPath).resolve(relativePath);
            indexFile(p);
        } catch (Exception e) {
            log.warn("Failed to process {}: {}", relativePath, e.getMessage());
        }
    }
}