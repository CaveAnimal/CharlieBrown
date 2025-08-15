package com.example.codetools;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.transaction.annotation.Transactional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;
import java.security.MessageDigest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.*;

@Service
public class VectorService {

    private static final Logger log = LoggerFactory.getLogger(VectorService.class);

    @Autowired
    private VectorRepository repo;

    @Autowired
    private EmbeddingService embeddingService;

    @Autowired
    private ObjectMapper mapper;
    @Autowired
    private AnnIndex annService;
    @Transactional
    public void upsert(String applicationId, String path, String content) {
        upsert(applicationId, path, content, 0, 0, 0);
    }

    @Transactional
    public void upsert(String applicationId, String path, String content, int chunkIndex, int startOffset, int endOffset) {
        try {
            String id = applicationId + ":" + path + ":" + chunkIndex;
            String checksum = checksumFor(content);
            VectorRecord existing = repo.findById(id).orElse(null);
            if (existing != null && checksum.equals(existing.getChecksum())) {
                // unchanged
                return;
            }
            float[] vec = embeddingService.embed(content);
            byte[] blob = floatArrayToGzipBytes(vec);
            String vecJson = mapper.writeValueAsString(vec);
            VectorRecord r = existing == null ? new VectorRecord() : existing;
            r.setId(id);
            r.setApplicationId(applicationId);
            r.setPath(path);
            r.setChunkIndex(chunkIndex);
            r.setStartOffset(startOffset);
            r.setEndOffset(endOffset);
            r.setContent(content);
            r.setVectorBlob(blob);
            r.setVectorJson(vecJson);
            r.setChecksum(checksum);
            r.setMetadata(null);
            r.setCreatedAt(System.currentTimeMillis());
            repo.save(r);
            // push into ANN index (best-effort)
            try {
                annService.add(r.getId(), vec);
            } catch (Exception ex) {
                log.warn("failed to add to ANN: {}", ex.getMessage());
            }
        } catch (Exception e) {
            // don't let indexing fail the whole scan
            log.debug("VectorService.upsert error: {}", e.getMessage(), e);
        }
    }

    public List<QueryModels.CodeSnippet> queryTopK(String applicationId, String question, int k) {
        try {
            float[] qv = embeddingService.embed(question);
            List<VectorRecord> candidates = repo.findByApplicationId(applicationId);
            PriorityQueue<Map.Entry<VectorRecord, Double>> pq = new PriorityQueue<>(Comparator.comparingDouble(Map.Entry::getValue));
            for (VectorRecord r : candidates) {
                float[] v = null;
                try {
                    byte[] blob = r.getVectorBlob();
                    if (blob != null && blob.length > 0) v = gzipBytesToFloatArray(blob);
                    else v = mapper.readValue(r.getVectorJson(), float[].class);
                } catch (Exception ex2) {
                    log.warn("failed to decode vector for {}: {}", r.getId(), ex2.getMessage());
                    continue;
                }
                double score = cosine(qv, v);
                Map.Entry<VectorRecord, Double> entry = new AbstractMap.SimpleEntry<>(r, score);
                pq.offer(entry);
                if (pq.size() > k) pq.poll();
            }
            List<QueryModels.CodeSnippet> out = new ArrayList<>();
            List<Map.Entry<VectorRecord, Double>> winners = new ArrayList<>();
            while (!pq.isEmpty()) winners.add(pq.poll());
            Collections.reverse(winners);
            for (Map.Entry<VectorRecord, Double> e : winners) {
                QueryModels.CodeSnippet s = new QueryModels.CodeSnippet();
                s.setPath(e.getKey().getPath());
                s.setContent(e.getKey().getContent());
                out.add(s);
            }
            return out;
        } catch (Exception ex) {
            log.error("VectorService.queryTopK error: {}", ex.getMessage());
            return Collections.emptyList();
        }
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null) return -1.0;
        int n = Math.min(a.length, b.length);
        double dot = 0.0, na = 0.0, nb = 0.0;
        for (int i = 0; i < n; i++) {
            dot += a[i] * b[i];
            na += a[i] * a[i];
            nb += b[i] * b[i];
        }
        if (na == 0 || nb == 0) return -1.0;
        return dot / (Math.sqrt(na) * Math.sqrt(nb));
    }

    private byte[] floatArrayToGzipBytes(float[] arr) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (GZIPOutputStream gz = new GZIPOutputStream(baos);
             DataOutputStream dos = new DataOutputStream(gz)) {
            dos.writeInt(arr.length);
            for (float f : arr) dos.writeFloat(f);
        }
        return baos.toByteArray();
    }

    private float[] gzipBytesToFloatArray(byte[] blob) throws Exception {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(blob));
             DataInputStream dis = new DataInputStream(gzis)) {
            int len = dis.readInt();
            float[] arr = new float[len];
            for (int i = 0; i < len; i++) arr[i] = dis.readFloat();
            return arr;
        }
    }

    private String checksumFor(String s) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-1");
        byte[] b = s.getBytes("UTF-8");
        byte[] d = md.digest(b);
        StringBuilder sb = new StringBuilder();
        for (byte bb : d) sb.append(String.format("%02x", bb));
        return sb.toString();
    }
}
