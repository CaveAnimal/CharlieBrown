package com.example.codetools;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/ann")
public class AdminController {

    @Autowired
    private ObjectProvider<AnnIndex> annServiceProvider;

    @org.springframework.beans.factory.annotation.Autowired
    private VectorService vectorService;

    @org.springframework.beans.factory.annotation.Autowired
    private VectorRepository vectorRepository;

    @org.springframework.beans.factory.annotation.Autowired
    private org.springframework.core.env.Environment env;

    @PostMapping("/rebuild")
    public String rebuild() {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann == null) return "no ann service configured";
        try {
            ann.rebuildFromDatabase();
            return "rebuild finished";
        } catch (Exception e) {
            return "rebuild failed: " + e.getMessage();
        }
    }

    @PostMapping("/persist")
    public String persist(@RequestParam(name = "path", required = false) String path) {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann == null) return "no ann service configured";
        try {
            java.nio.file.Path p = path != null ? java.nio.file.Path.of(path) : java.nio.file.Files.createTempFile("ann", ".idx");
            ann.persistTo(p);
            return "persisted to " + p.toAbsolutePath().toString();
        } catch (Exception e) {
            return "persist failed: " + e.getMessage();
        }
    }

    @PostMapping("/load")
    public String load(@RequestParam(name = "path") String path) {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann == null) return "no ann service configured";
        try {
            java.nio.file.Path p = java.nio.file.Path.of(path);
            ann.loadFrom(p);
            return "loaded from " + p.toAbsolutePath().toString();
        } catch (Exception e) {
            return "load failed: " + e.getMessage();
        }
    }

    @GetMapping("/params")
    public Object getParams() {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann instanceof HnswAnnService) {
            return ((HnswAnnService) ann).getHnswParams();
        }
        return java.util.Collections.emptyMap();
    }

    @PostMapping("/params")
    public String setParams(@RequestParam int m, @RequestParam int efConstruction, @RequestParam int maxItems) {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (!(ann instanceof HnswAnnService)) return "not hnsw implementation";
        try {
            ((HnswAnnService) ann).reconfigure(m, efConstruction, maxItems);
            return "reconfigured";
        } catch (Exception e) {
            return "reconfigure failed: " + e.getMessage();
        }
    }

    @GetMapping("/status")
    public String status() {
        try {
            AnnIndex ann = annServiceProvider.getIfAvailable();
            return "size=" + (ann == null ? 0 : ann.size());
        } catch (Exception e) {
            return "status error: " + e.getMessage();
        }
    }

    @GetMapping("/health")
    public Object health() {
        AnnIndex annCheck = annServiceProvider.getIfAvailable();
        if (annCheck instanceof HnswAnnService) {
            HnswAnnService h = (HnswAnnService) annCheck;
            java.util.Map<String, Object> out = new java.util.HashMap<>();
            out.put("size", h.size());
            out.put("dimensions", h.getDimensions());
            out.put("params", h.getHnswParams());
            return out;
        }
        AnnIndex ann = annServiceProvider.getIfAvailable();
        return java.util.Collections.singletonMap("size", ann == null ? 0 : ann.size());
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
