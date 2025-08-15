package com.example.codetools;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Autowired;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;
import java.util.zip.GZIPInputStream;

@Service
@Profile("ann-dev")
public class BruteForceAnnService implements AnnIndex {

    private final Map<String, float[]> store = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    @Override
    public void add(String id, float[] vector) {
        lock.writeLock().lock();
        try {
            store.put(id, Arrays.copyOf(vector, vector.length));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void remove(String id) {
        lock.writeLock().lock();
        try {
            store.remove(id);
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<String> query(float[] q, int topK) {
        lock.readLock().lock();
        try {
            return store.entrySet().stream()
                    .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), cosine(q, e.getValue())))
                    .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                    .limit(topK)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void rebuildFromDatabase() {
        // reload all persisted vectors into the in-memory brute-force store
        lock.writeLock().lock();
        try {
            store.clear();
            // repository and mapper might be injected by Spring if available
            if (vectorRepository == null) return;
            List<VectorRecord> all = vectorRepository.findAll();
            for (VectorRecord r : all) {
                try {
                    float[] v = null;
                    byte[] blob = r.getVectorBlob();
                    if (blob != null && blob.length > 0) v = gzipBytesToFloatArray(blob);
                    else if (r.getVectorJson() != null && !r.getVectorJson().isBlank()) v = mapper.readValue(r.getVectorJson(), float[].class);
                    if (v != null) store.put(r.getId(), v);
                } catch (Exception ex) {
                    // skip malformed vector
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long size() {
        lock.readLock().lock();
        try { return store.size(); } finally { lock.readLock().unlock(); }
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null) return -1.0;
        int n = Math.min(a.length, b.length);
        double dot=0, na=0, nb=0;
        for (int i=0;i<n;i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        if (na==0 || nb==0) return -1.0;
        return dot / (Math.sqrt(na)*Math.sqrt(nb));
    }

    // --- helpers & DI ---
    @Autowired(required = false)
    private VectorRepository vectorRepository;

    @Autowired(required = false)
    private ObjectMapper mapper;

    private float[] gzipBytesToFloatArray(byte[] blob) throws Exception {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(blob));
             DataInputStream dis = new DataInputStream(gzis)) {
            int len = dis.readInt();
            float[] arr = new float[len];
            for (int i = 0; i < len; i++) arr[i] = dis.readFloat();
            return arr;
        }
    }
}
