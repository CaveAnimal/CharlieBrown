package com.example.codetools;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * EmbeddingService that shells out to a configured command (default: `ollama`) and parses
 * the output as either a JSON array of floats or whitespace/comma separated floats.
 * This uses the existing ProcessRunner abstraction for testability.
 */
@Service
@ConditionalOnProperty(prefix = "embedding", name = "cli.enabled", havingValue = "true", matchIfMissing = false)
public class CommandLineEmbeddingService implements EmbeddingService {

    private final String command;
    private final String model;
    private final ProcessRunner runner;

    @Autowired
    public CommandLineEmbeddingService(Environment env, ProcessRunner runner) {
        this.command = env.getProperty("embedding.command", "ollama");
        this.model = env.getProperty("embedding.model", "codellama:13b-instruct");
        this.runner = runner;
    }

    @Override
    public float[] embed(String text) {
        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(command);
            // try common ollama embedding command form; callers can configure a different command if needed
            cmd.add("embed");
            cmd.add(model);

            Process p = runner.start(cmd);

            try (java.io.OutputStream os = p.getOutputStream()) {
                os.write(text.getBytes(StandardCharsets.UTF_8));
                os.flush();
            }

            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String l;
                while ((l = r.readLine()) != null) { out.append(l).append('\n'); }
            }

            p.waitFor();

            String resp = out.toString().trim();
            if (resp.isEmpty()) return null;

            // try parsing as JSON array first
            try {
                com.fasterxml.jackson.databind.ObjectMapper m = new com.fasterxml.jackson.databind.ObjectMapper();
                float[] arr = m.readValue(resp, float[].class);
                return arr;
            } catch (Exception ignored) {}

            // fallback: parse whitespace/comma-separated floats
            String cleaned = resp.replaceAll("[,\\s]+", " ").trim();
            String[] parts = cleaned.split(" ");
            float[] outv = new float[parts.length];
            for (int i = 0; i < parts.length; i++) outv[i] = Float.parseFloat(parts[i]);
            return outv;
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(CommandLineEmbeddingService.class).warn("Embedding failed: {}", e.getMessage());
            return null;
        }
    }
}
