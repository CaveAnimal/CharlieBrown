package com.example.codetools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequestMapping("/api/query")
public class QueryController {

    @Autowired
    private FileScanner scanner;

    @Autowired
    private OllamaClient client;

    @PostMapping
    public QueryModels.QueryResponse handleQuery(@RequestBody QueryModels.QueryRequest req) throws Exception {
        log.info("Received POST query: {}", req.getQuestion());
        log.info("POST request paths: {}", req.getPaths());
        List<QueryModels.CodeSnippet> snippets = scanner.fetchSnippets(req.getPaths(), 3);
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
        List<QueryModels.CodeSnippet> snippets = scanner.fetchSnippets(null, 3);
        log.info("Fetched {} code snippets for GET", snippets.size());
        String answer = client.queryModel(question, snippets);
        log.info("Ollama answer (GET): {}", answer);
        QueryModels.QueryResponse resp = new QueryModels.QueryResponse();
        resp.setAnswer(answer);
        log.info("Returning response for GET: {}", resp.getAnswer());
        return resp;
    }
}