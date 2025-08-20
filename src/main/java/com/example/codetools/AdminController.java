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

    @org.springframework.beans.factory.annotation.Autowired
    private FileScanner fileScanner;

    @org.springframework.beans.factory.annotation.Autowired
    private ScannerJobService scannerJobService;

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

    @GetMapping("/scanner-info")
    public Object scannerInfo() {
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        String root = env.getProperty("scanner.root.path", "");
        out.put("root", root);
        String lines = env.getProperty("scanner.root.lines", "0");
        try { out.put("lines", Integer.parseInt(lines)); } catch (Exception e) { out.put("lines", 0); }
        return out;
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

    @PostMapping("/scan")
    public String scanNow() {
        try {
            fileScanner.scanRoot();
            return "scan triggered";
        } catch (Exception e) {
            return "scan failed: " + e.getMessage();
        }
    }

    @GetMapping("/persist-info")
    public Object persistInfo() {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann instanceof HnswAnnService) {
            return ((HnswAnnService) ann).getLastPersistInfo();
        }
        return java.util.Collections.singletonMap("message", "persist info not available for this ann implementation");
    }

    @GetMapping("/auto-load")
    public Object getAutoLoad() {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann instanceof HnswAnnService) {
            return java.util.Collections.singletonMap("autoLoadEnabled", ((HnswAnnService) ann).isAutoLoadEnabled());
        }
        return java.util.Collections.singletonMap("message", "auto-load not applicable for this ann implementation");
    }

    @PostMapping("/auto-load")
    public Object setAutoLoad(@RequestParam(name = "enabled") boolean enabled) {
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (!(ann instanceof HnswAnnService)) return "not hnsw implementation";
        HnswAnnService h = (HnswAnnService) ann;
        h.setAutoLoadEnabled(enabled);
        return java.util.Collections.singletonMap("autoLoadEnabled", h.isAutoLoadEnabled());
    }

    @PostMapping("/scan/start")
    public Object startScanAsync() {
        try {
            String jobId = scannerJobService.startJob();
            return java.util.Collections.singletonMap("jobId", jobId);
        } catch (Exception e) {
            return "start failed: " + e.getMessage();
        }
    }

    @PostMapping("/scan/pause")
    public String pauseScan() {
        try {
            boolean ok = scannerJobService.pause();
            return ok ? "paused" : "no-job";
        } catch (Exception e) {
            return "pause failed: " + e.getMessage();
        }
    }

    @PostMapping("/scan/resume")
    public String resumeScan() {
        try {
            boolean ok = scannerJobService.resume();
            return ok ? "resumed" : "no-job";
        } catch (Exception e) {
            return "resume failed: " + e.getMessage();
        }
    }

    @PostMapping("/scan/cancel")
    public String cancelScan() {
        try {
            boolean ok = scannerJobService.cancel();
            return ok ? "cancelled" : "no-job";
        } catch (Exception e) {
            return "cancel failed: " + e.getMessage();
        }
    }

    @GetMapping("/scan/status")
    public Object scanStatus() {
        try {
            return scannerJobService.status();
        } catch (Exception e) {
            return java.util.Collections.singletonMap("error", e.getMessage());
        }
    }

    /**
     * Migrate any VectorRecord entries stored under "default-app" to the configured
     * application id derived by the FileScanner. This avoids re-embedding if data already exists.
     */
    @PostMapping("/migrate-default")
    public String migrateDefault() {
        try {
            String targetApp = fileScanner.getApplicationId();
            if (targetApp == null || targetApp.isBlank()) return "no target app id";
            java.util.List<VectorRecord> defaults = vectorRepository.findByApplicationId("default-app");
            int migrated = 0;
            for (VectorRecord r : defaults) {
                // compute new id with target app
                String[] parts = r.getId().split(":", 3);
                if (parts.length < 3) continue;
                String newId = targetApp + ":" + parts[1] + ":" + parts[2];
                if (!vectorRepository.existsById(newId)) {
                    VectorRecord copy = new VectorRecord();
                    copy.setId(newId);
                    copy.setApplicationId(targetApp);
                    copy.setPath(r.getPath());
                    copy.setChunkIndex(r.getChunkIndex());
                    copy.setStartOffset(r.getStartOffset());
                    copy.setEndOffset(r.getEndOffset());
                    copy.setContent(r.getContent());
                    copy.setVectorBlob(r.getVectorBlob());
                    copy.setVectorJson(r.getVectorJson());
                    copy.setChecksum(r.getChecksum());
                    copy.setMetadata(r.getMetadata());
                    copy.setCreatedAt(System.currentTimeMillis());
                    vectorRepository.save(copy);
                    migrated++;
                }
            }
            return "migrated=" + migrated;
        } catch (Exception e) {
            return "migrate failed: " + e.getMessage();
        }
    }
}
