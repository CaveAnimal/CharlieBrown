package com.example.codetools;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import javax.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;

@Slf4j
@Service
public class FileScanner {

    private final Map<String, String> index = new HashMap<>();
    private final String rootPath;

    public FileScanner(org.springframework.core.env.Environment env) {
        this.rootPath = env.getProperty("scanner.root.path");
    }

    @PostConstruct
    public void scanRoot() throws IOException {
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
            index.put(root.relativize(file).toString(), content);
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