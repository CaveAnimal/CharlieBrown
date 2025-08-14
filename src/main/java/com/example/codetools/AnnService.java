package com.example.codetools;

import org.springframework.stereotype.Service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Minimal NoOp AnnService to allow the application to run without an external ANN index.
 * Methods are intentionally lightweight: they keep a simple count and provide a rebuild stub.
 */
@Service
public class AnnService {

    private final AtomicLong counter = new AtomicLong(0);

    public void add(String id, float[] vector) {
        // No-op: record increment so status looks reasonable
        counter.incrementAndGet();
    }

    public void rebuildFromDatabase() {
        // No-op: in a real implementation, this would stream vectors from DB and add them to the ANN index
        // Keep it synchronous and quick to avoid blocking callers in this stub
        counter.set(0);
    }

    public long size() {
        return counter.get();
    }
}
