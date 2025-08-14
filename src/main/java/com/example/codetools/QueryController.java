package com.example.codetools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;

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
    List<QueryModels.CodeSnippet> snippets = scanner.fetchSnippets(req.getPaths(), max);
        log.info("Fetched {} code snippets for POST", snippets.size());
        String answer = client.queryModel(req.getQuestion(), snippets);
        log.info("Ollama answer (POST): {}", answer);
        QueryModels.QueryResponse resp = new QueryModels.QueryResponse();
        resp.setAnswer(answer);
        log.info("Returning response for POST: {}", resp.getAnswer());
        return resp;
    }

    @GetMapping
    public QueryModels.QueryResponse handleQueryGet(@RequestParam(name = "question") String question) throws Exception {
        log.info("Received GET query: {}", question);
    int max = Integer.parseInt(env.getProperty("scanner.max.snippets", "3"));
    List<QueryModels.CodeSnippet> snippets = scanner.fetchSnippets(null, max);
        if ((snippets == null || snippets.isEmpty()) && vectorService != null) {
            // try semantic retrieval
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