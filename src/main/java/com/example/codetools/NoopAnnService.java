package com.example.codetools;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Collections;
import java.util.concurrent.atomic.AtomicLong;

@Service
@ConditionalOnMissingBean(AnnIndex.class)
public class NoopAnnService implements AnnIndex {

    private final AtomicLong counter = new AtomicLong(0);

    @Override
    public void add(String id, float[] vector) {
        counter.incrementAndGet();
    }

    @Override
    public void remove(String id) {
        // no-op
    }

    @Override
    public void rebuildFromDatabase() {
        counter.set(0);
    }

    @Override
    public List<String> query(float[] q, int topK) {
        return Collections.emptyList();
    }

    @Override
    public long size() { return counter.get(); }
}
