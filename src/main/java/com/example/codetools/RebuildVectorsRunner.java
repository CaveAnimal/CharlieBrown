package com.example.codetools;

import java.nio.file.Path;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Small helper that can be enabled with --rebuild.vectors=true to perform a full
 * re-scan of the configured root, repopulate the vector table, rebuild the ANN index
 * from the DB and persist the ANN index to disk.
 */
@Component
@ConditionalOnProperty(name = "rebuild.vectors", havingValue = "true")
public class RebuildVectorsRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(RebuildVectorsRunner.class);

    @Autowired
    private FileScanner fileScanner;

    @Autowired
    private java.util.Optional<VectorService> vectorServiceOpt;

    @Autowired
    private org.springframework.beans.factory.ObjectProvider<AnnIndex> annServiceProvider;

    @Autowired
    private org.springframework.core.env.Environment env;

    @Value("${hnsw.persist.path:./data/ann-index.idx}")
    private String defaultPersistPath;

    @Override
    public void run(String... args) throws Exception {
        log.info("RebuildVectorsRunner: starting full rebuild (scan -> rebuild -> persist)");

        // 1) scan files and upsert vectors into the JPA store
        try {
            fileScanner.scanRoot();
            log.info("RebuildVectorsRunner: scanRoot completed");
        } catch (Exception e) {
            log.error("RebuildVectorsRunner: scanRoot failed: {}", e.getMessage(), e);
        }

        // 2) rebuild ANN from the persisted vectors
        AnnIndex ann = annServiceProvider.getIfAvailable();
        if (ann == null) {
            log.warn("RebuildVectorsRunner: no ANN implementation available; skipping rebuild");
            return;
        }

        try {
            log.info("RebuildVectorsRunner: rebuilding ANN from database...");
            ann.rebuildFromDatabase();
            log.info("RebuildVectorsRunner: rebuildFromDatabase() finished; ann size={}", ann.size());
        } catch (Exception e) {
            log.error("RebuildVectorsRunner: rebuildFromDatabase failed: {}", e.getMessage(), e);
        }

        // 3) persist ANN index to the configured path (if supported)
        try {
            String persist = env.getProperty("hnsw.persist.path", defaultPersistPath);
            if (persist == null || persist.isBlank()) persist = defaultPersistPath;
            Path p = Path.of(persist);
            // attempt to persist
            try {
                if (ann instanceof HnswAnnService) {
                    ((HnswAnnService) ann).persistTo(p);
                    log.info("RebuildVectorsRunner: persisted ANN to {}", p.toAbsolutePath());
                } else {
                    // try generic persistTo if AnnIndex provides it via reflection (best-effort)
                    try {
                        ann.getClass().getMethod("persistTo", java.nio.file.Path.class).invoke(ann, p);
                        log.info("RebuildVectorsRunner: persisted ANN (via reflection) to {}", p.toAbsolutePath());
                    } catch (NoSuchMethodException ex) {
                        log.warn("RebuildVectorsRunner: ANN implementation does not support persistTo(Path) method");
                    }
                }
            } catch (Exception ex) {
                log.error("RebuildVectorsRunner: failed to persist ANN index: {}", ex.getMessage(), ex);
            }
        } catch (Exception e) {
            log.error("RebuildVectorsRunner: error while determining persist path: {}", e.getMessage(), e);
        }

        log.info("RebuildVectorsRunner: finished");
    }
}
