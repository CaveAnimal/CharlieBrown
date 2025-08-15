package com.example.codetools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ann")
public class AdminController {

    @Autowired
    private AnnIndex annService;

    @org.springframework.beans.factory.annotation.Autowired
    private VectorService vectorService;

    @org.springframework.beans.factory.annotation.Autowired
    private VectorRepository vectorRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    @PostMapping("/rebuild")
    public String rebuild() {
        try {
            annService.rebuildFromDatabase();
            return "rebuild finished";
        } catch (Exception e) {
            return "rebuild failed: " + e.getMessage();
        }
    }

    @GetMapping("/status")
    public String status() {
        try {
            return "size=" + annService.size();
        } catch (Exception e) {
            return "status error: " + e.getMessage();
        }
    }

    /**
     * Insert a small sample vector (via VectorService) so you can verify H2 persistence and
     * that the indexing pipeline works without a real ANN implementation.
     */
    @PostMapping("/sample")
    public String insertSample() {
        try {
            String appId = env.getProperty("application.id", "default-app");
            String path = "sample/auto.txt";
            String content = "// sample code snippet created at " + System.currentTimeMillis();
            vectorService.upsert(appId, path, content);
            long count = vectorRepository.count();
            return "inserted sample, total vectors=" + count;
        } catch (Exception e) {
            return "insert-sample failed: " + e.getMessage();
        }
    }
}
