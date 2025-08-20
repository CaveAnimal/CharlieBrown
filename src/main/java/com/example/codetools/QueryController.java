package com.example.codetools;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/query")
public class QueryController {

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(QueryController.class);

    @Autowired
    private FileScanner scanner;

    @Autowired
    private OllamaClient client;

    @Autowired
    private VectorService vectorService;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    @PostMapping
    public QueryModels.QueryResponse handleQuery(@RequestBody QueryModels.QueryRequest req) throws Exception {
        log.info("Received POST query: {}", req.getQuestion());
        log.info("POST request paths: {}", req.getPaths());
    int max = Integer.parseInt(env.getProperty("scanner.max.snippets", "3"));
    List<QueryModels.CodeSnippet> snippets = null;

        // Classify the question first. If the model says it's CODE-related, fetch snippets.
        boolean needsCode = false;
        try {
            needsCode = client.classifyQuestion(req.getQuestion());
        } catch (Exception e) {
            log.warn("Question classification failed, falling back to fetching snippets if paths provided", e);
            needsCode = req.getPaths() != null && !req.getPaths().isEmpty();
        }

        if (needsCode) {
            snippets = scanner.fetchSnippets(req.getPaths(), max);
        }
        log.info("Fetched {} code snippets for POST", snippets == null ? 0 : snippets.size());
        String answer = client.queryModel(req.getQuestion(), snippets);
        log.info("Ollama answer (POST): {}", answer);
        QueryModels.QueryResponse resp = new QueryModels.QueryResponse();
        resp.setAnswer(answer);
        log.info("Returning response for POST: {}", resp.getAnswer());
        return resp;
    }

    @GetMapping
    public QueryModels.QueryResponse handleQueryGet(@RequestParam(name = "question") String question,
                                                    @RequestParam(name = "paths", required = false) List<String> paths) throws Exception {
        log.info("Received GET query: {}", question);
    int max = Integer.parseInt(env.getProperty("scanner.max.snippets", "3"));
    List<QueryModels.CodeSnippet> snippets = null;

        // Only fetch raw file snippets when explicit paths are provided by the caller.
        // For general queries (no paths) prefer semantic retrieval from the vector DB.
        if (paths != null && !paths.isEmpty()) {
            snippets = scanner.fetchSnippets(paths, max);
        } else if (vectorService != null) {
            snippets = vectorService.queryTopK(env.getProperty("application.id", "default-app"), question, max);
        }

        log.info("Fetched {} code snippets for GET", snippets == null ? 0 : snippets.size());
        String answer = client.queryModel(question, snippets);
        log.info("Ollama answer (GET): {}", answer);
        QueryModels.QueryResponse resp = new QueryModels.QueryResponse();
        resp.setAnswer(answer);
        log.info("Returning response for GET: {}", resp.getAnswer());
        return resp;
    }
}