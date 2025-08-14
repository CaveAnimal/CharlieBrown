package com.example.codetools;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.*;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@Service
public class OllamaClient {

    private final String command;
    private final String model;

    public OllamaClient(Environment env) {
        this.command = env.getProperty("ollama.command");
        this.model = env.getProperty("ollama.model");
    }

    public String queryModel(String question, List<QueryModels.CodeSnippet> snippets) throws Exception {
        log.info("OllamaClient: Received question: {}", question);
        log.info("OllamaClient: Number of code snippets: {}", snippets != null ? snippets.size() : 0);
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.add("run");
        cmd.add(model);
        StringBuilder prompt = new StringBuilder(question.replaceAll("\n", " ") + " ");
        if (snippets != null) {
            snippets.forEach(s -> prompt.append("File: ").append(s.getPath()).append(" ")
                    .append(s.getContent().replaceAll("\n", " ")).append(" "));
        }
        log.info("OllamaClient: Original prompt (pre-formatting): {}", prompt);
        String promptStr = prompt.toString().replaceAll(",", " ").trim();
        cmd.add(promptStr);
        log.info("OllamaClient: Running command: {}", String.join(" ", cmd));
        log.info("OllamaClient: Everything sent to Ollama:\n{}", promptStr);

        try {
            ProcessBuilder pb = new ProcessBuilder(cmd);
            Process proc = pb.start();
            BufferedReader out = new BufferedReader(new InputStreamReader(proc.getInputStream()));
            StringBuilder resp = new StringBuilder();
            String line;
            boolean gotResponse = false;
            while ((line = out.readLine()) != null) {
                gotResponse = true;
                resp.append(line).append("\n");
            }
            int exitCode = proc.waitFor();
            log.info("OllamaClient: Process exited with code {}", exitCode);
            if (!gotResponse) {
                log.warn("OllamaClient: No response received from Ollama. Check if Ollama is running and the model is available. Command: {}", String.join(" ", cmd));
            } else {
                log.info("OllamaClient: Raw response from Ollama:\n{}", resp);
            }
            return resp.toString().trim();
        } catch (Exception e) {
            log.error("OllamaClient: Exception while running Ollama command: {}", String.join(" ", cmd), e);
            throw e;
        }
    }
}
