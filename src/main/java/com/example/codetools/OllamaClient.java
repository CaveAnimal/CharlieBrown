package com.example.codetools;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

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

    // Run Ollama with a raw prompt and return trimmed stdout
    private String runWithPrompt(String promptStr) throws Exception {
        List<String> cmd = new ArrayList<>();
        cmd.add(command);
        cmd.add("run");
        cmd.add(model);

        log.info("OllamaClient: Running command: {} [prompt length={}]", String.join(" ", cmd), promptStr.length());
        log.debug("OllamaClient: Prompt (truncated 1000 chars):\n{}", promptStr.length() > 1000 ? promptStr.substring(0, 1000) + "..." : promptStr);

        try {
            Process proc = processRunner.start(cmd);

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

    public String queryModel(String question, List<QueryModels.CodeSnippet> snippets) throws Exception {
        String promptStr = promptBuilder.build(question, snippets);
        return runWithPrompt(promptStr);
    }

    /**
     * Ask the model to classify whether a question requires consulting source code.
     * Returns true if it should consult code (CODE), false if it's general knowledge (GENERAL).
     */
    public boolean classifyQuestion(String question) throws Exception {
    String classifier = "You are a classifier. Decide whether the user question requires consulting the project's source code files to answer. "
        + "Respond with exactly ONE token on a single line: either GENERAL or CODE and NOTHING ELSE. "
        + "GENERAL means the question is general-knowledge, factual, or conceptual and does NOT require reading repository files. "
        + "CODE means the question asks about file contents, exact implementation, debugging, code examples from this repo, or explicitly references files, paths, or repository structure.\n\n"
        + "EXAMPLES (input => expected single-token output):\n"
        + "What is the value of pi? => GENERAL\n"
        + "How many planets are in the solar system? => GENERAL\n"
        + "How to sort an array in Java? => GENERAL\n"
        + "Show me the contents of src/main/java/com/example/MyClass.java => CODE\n"
        + "Why does my NullPointerException occur at com/example/Foo.java:42? => CODE\n"
        + "What does the function computeChecksum in file util/Checksum.java do? => CODE\n\n"
        + "QUESTION:\n" + question + "\n\nREPLY WITH ONE TOKEN (GENERAL or CODE):\n";

    String resp = runWithPrompt(classifier);
    if (resp == null) return false;
    // Take only the first non-empty token from the response
        String[] toks = resp.trim().split("\\s+");
    if (toks.length == 0) return false;
    String first = toks[0].trim().toUpperCase();
    log.info("OllamaClient: classification response='{}' (raw='{}')", first, resp.trim());
        // Heuristic overrides for common general-knowledge questions that the model may misclassify.
        String q = question == null ? "" : question.trim().toLowerCase();
        // If user asks directly about pi or other simple factual tokens, treat as GENERAL
        if (q.matches(".*\\b(pi|pi\\s+value|value of pi|what is pi|what's pi|define pi|approximate value of pi)\\b.*")) {
            log.info("OllamaClient: heuristic override -> GENERAL for question='{}'", question);
            return false;
        }

        if ("CODE".equals(first)) return true;
        return false;
    }
}
