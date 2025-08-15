package com.example.codetools;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.concurrent.TimeUnit;
import java.util.ArrayList;
import java.util.List;

@Service
public class OllamaClient {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(OllamaClient.class);

    private final String command;
    private final String model;

    private final OllamaPromptBuilder promptBuilder;

    private final ProcessRunner processRunner;

    public OllamaClient(Environment env, OllamaPromptBuilder promptBuilder, ProcessRunner processRunner) {
        this.command = env.getProperty("ollama.command", "ollama");
        this.model = env.getProperty("ollama.model", "codellama:13b-instruct");
        this.promptBuilder = promptBuilder;
        this.processRunner = processRunner;
    }

    public String queryModel(String question, List<QueryModels.CodeSnippet> snippets) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.add("run");
        cmd.add(model);

    String promptStr = promptBuilder.build(question, snippets);
        log.info("OllamaClient: Running command: {} [prompt length={}]", String.join(" ", cmd), promptStr.length());
        log.debug("OllamaClient: Everything that will be sent to Ollama (truncated 1000 chars):\n{}", promptStr.length() > 1000 ? promptStr.substring(0, 1000) + "..." : promptStr);

        try {
            Process proc = processRunner.start(cmd);

            // write prompt to stdin of the process to avoid CLI quoting/length issues
            try (java.io.OutputStream os = proc.getOutputStream()) {
                os.write(promptStr.getBytes(java.nio.charset.StandardCharsets.UTF_8));
                os.flush();
            } catch (IOException io) {
                log.error("OllamaClient: Error writing prompt to Ollama stdin", io);
            }

            StringBuilder resp = new StringBuilder();
            StringBuilder err = new StringBuilder();

            Thread outReader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getInputStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) {
                        resp.append(l).append("\n");
                    }
                } catch (IOException io) {
                    log.error("OllamaClient: Error reading stdout", io);
                }
            }, "ollama-stdout-reader");

            Thread errReader = new Thread(() -> {
                try (BufferedReader r = new BufferedReader(new InputStreamReader(proc.getErrorStream()))) {
                    String l;
                    while ((l = r.readLine()) != null) {
                        err.append(l).append("\n");
                    }
                } catch (IOException io) {
                    log.error("OllamaClient: Error reading stderr", io);
                }
            }, "ollama-stderr-reader");

            outReader.start();
            errReader.start();

            boolean finished = proc.waitFor(180, TimeUnit.SECONDS);
            // Give readers a moment to drain
            outReader.join(2000);
            errReader.join(2000);

            int exitCode = finished ? proc.exitValue() : -1;
            if (!finished) {
                log.warn("OllamaClient: Process did not finish within timeout, destroying...");
                proc.destroyForcibly();
            }
            log.info("OllamaClient: Process exited with code {} (finished={} )", exitCode, finished);

            if (err.length() > 0) {
                log.warn("OllamaClient: Stderr from Ollama:\n{}", err.toString().trim());
            }

            if (resp.length() == 0) {
                log.warn("OllamaClient: No stdout response received from Ollama. Check if Ollama is running and the model is available. Command: {}", String.join(" ", cmd));
            } else {
                log.info("OllamaClient: Raw response from Ollama:\n{}", resp.toString().trim());
            }

            return resp.toString().trim();
        } catch (Exception e) {
            log.error("OllamaClient: Exception while running Ollama command: {}", String.join(" ", cmd), e);
            throw e;
        }
    }
}
