package com.example.codetools;

import com.github.jelmerk.hnswlib.core.hnsw.HnswIndex;
import com.github.jelmerk.hnswlib.core.DistanceFunctions;
import com.github.jelmerk.hnswlib.core.SearchResult;
import com.github.jelmerk.hnswlib.core.Item;
import org.springframework.context.annotation.Profile;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.file.Path;
import java.util.*;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Collectors;

@Service
@Profile("!ann-dev")
public class HnswAnnService implements AnnIndex, Serializable {
    // Explicit SLF4J logger used to avoid Lombok annotation processor issues in some environments
    private static final Logger log = LoggerFactory.getLogger(HnswAnnService.class);

    // Fallback store used only when HNSW index is not yet initialized or for persistence compatibility
    private final Map<String, float[]> store = new HashMap<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock();

    // jelmerk HNSW index. Will be lazily initialized when we know the vector dimension.
    private transient HnswIndex<String, float[], Item<String, float[]>, Float> index;
    private volatile int dimension = -1;

    // configurable HNSW parameters (can be overridden in application.properties)
    @Value("${hnsw.m:16}")
    private int m;

    @Value("${hnsw.efConstruction:200}")
    private int efConstruction;

    @Value("${hnsw.max.items:1000}")
    private int maxItems;

    @Value("${hnsw.rebuild.page.size:1000}")
    private int rebuildPageSize;

    @Value("${hnsw.persist.path:./data/ann-index.idx}")
    private String persistPath;

    @Value("${hnsw.auto.persist.enabled:true}")
    private boolean autoPersistEnabled;

    @Value("${hnsw.auto.persist.min-size:1}")
    private int autoPersistMinSize;

    @Value("${hnsw.auto.load.enabled:false}")
    private boolean autoLoadEnabled;

    // attempt to auto-load persisted index at startup
    @jakarta.annotation.PostConstruct
    public void tryAutoLoad() {
    if (!autoLoadEnabled) return;
    if (persistPath == null || persistPath.isBlank()) return;
        java.nio.file.Path p = java.nio.file.Path.of(persistPath);
        try {
            java.nio.file.Files.createDirectories(p.getParent());
        } catch (Exception ignored) {
            // ignore
        }
        try {
            if (java.nio.file.Files.exists(p)) {
                loadFrom(p);
                lastPersistedPath = p.toAbsolutePath().toString();
                lastPersistedAt = java.time.Instant.now();
                log.info("Loaded HNSW index from {}", lastPersistedPath);
            }
        } catch (Exception e) {
            log.warn("Failed to load persisted HNSW index from {}: {}", persistPath, e.getMessage());
        }
    }

    // last persisted metadata
    private volatile String lastPersistedPath = null;
    private volatile java.time.Instant lastPersistedAt = null;

    @Autowired(required = false)
    private VectorRepository vectorRepository; // used for full rebuild

    // explicit no-arg constructor (preserve Spring instantiation)
    public HnswAnnService() {
    }

    // package-private constructor for tests to configure small HNSW params without reflection
    HnswAnnService(int m, int efConstruction, int maxItems, int rebuildPageSize) {
        this.m = m;
        this.efConstruction = efConstruction;
        this.maxItems = maxItems;
        this.rebuildPageSize = rebuildPageSize;
    }

    private void ensureIndex(int dim) {
        if (index != null && dimension == dim) return;
        lock.writeLock().lock();
        try {
            if (index != null && dimension == dim) return;
            // create new HNSW index with cosine distance and configured defaults
            this.dimension = dim;
            this.index = HnswIndex.newBuilder(dim, DistanceFunctions.FLOAT_COSINE_DISTANCE, maxItems)
                    .withM(m)
                    .withEfConstruction(efConstruction)
                    .build();

            // populate from fallback store if any
            for (Map.Entry<String, float[]> e : store.entrySet()) {
                index.add(new SimpleItem(e.getKey(), e.getValue()));
            }
            // clear fallback store to avoid double storage
            store.clear();
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void add(String id, float[] vector) {
        if (vector == null) return;
        lock.writeLock().lock();
        try {
            if (index == null) {
                // buffer until we can initialize index
                store.put(id, Arrays.copyOf(vector, vector.length));
                // try to initialize now
                ensureIndex(vector.length);
                return;
            }
            index.add(new SimpleItem(id, Arrays.copyOf(vector, vector.length)));
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public void remove(String id) {
        lock.writeLock().lock();
        try {
            if (index != null) {
                try {
                    // remove requires the item id and a timestamp/lock; use remove(id, 0) if supported
                    boolean removed = index.remove(id, 0L);
                    if (!removed) {
                        // rebuild index without this id
                        Collection<com.github.jelmerk.hnswlib.core.Item<String, float[]>> items = index.items();
                        HnswIndex<String, float[], Item<String, float[]>, Float> newIndex = HnswIndex.newBuilder(dimension, index.getDistanceFunction(), index.getMaxItemCount())
                                .withM(index.getM())
                                .withEfConstruction(index.getEfConstruction())
                                .build();
                        for (com.github.jelmerk.hnswlib.core.Item<String, float[]> it : items) {
                            if (!Objects.equals(it.id(), id)) {
                                newIndex.add(it);
                            }
                        }
                        this.index = newIndex;
                    }
                } catch (UnsupportedOperationException | NoSuchMethodError e) {
                    // some HNSW implementations may not support remove; fall back to rebuild pattern
                    Collection<com.github.jelmerk.hnswlib.core.Item<String, float[]>> items = index.items();
                    HnswIndex<String, float[], Item<String, float[]>, Float> newIndex = HnswIndex.newBuilder(dimension, index.getDistanceFunction(), index.getMaxItemCount())
                            .withM(index.getM())
                            .withEfConstruction(index.getEfConstruction())
                            .build();
                    for (com.github.jelmerk.hnswlib.core.Item<String, float[]> it : items) {
                        if (!Objects.equals(it.id(), id)) {
                            newIndex.add(it);
                        }
                    }
                    this.index = newIndex;
                }
            } else {
                store.remove(id);
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public List<String> query(float[] q, int topK) {
        if (q == null || topK <= 0) return Collections.emptyList();
        lock.readLock().lock();
        try {
            if (index == null) {
                // fallback brute-force
                return store.entrySet().stream()
                        .map(e -> new AbstractMap.SimpleEntry<>(e.getKey(), cosine(q, e.getValue())))
                        .sorted((a, b) -> Double.compare(b.getValue(), a.getValue()))
                        .limit(topK)
                        .map(Map.Entry::getKey)
                        .collect(Collectors.toList());
            }

            List<SearchResult<Item<String, float[]>, Float>> results = index.findNearest(q, topK);
            List<String> ids = new ArrayList<>(results.size());
            for (SearchResult<Item<String, float[]>, Float> r : results) {
                ids.add(r.item().id());
            }
            return ids;
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    public void rebuildFromDatabase() throws Exception {
        // Build a fresh index from the persisted VectorRepository. This will clear any in-memory
        // store/index and populate using the stored vectors (vectorBlob or vectorJson).
        if (vectorRepository == null) {
            throw new IllegalStateException("VectorRepository not available for rebuild");
        }
        lock.writeLock().lock();
        try {
            // clear current state
            if (index != null) {
                // HnswIndex does not expose a close; just drop the reference and let GC reclaim resources
                index = null;
            }
            store.clear();
            // Paged scan to avoid loading all vectors into memory at once
            int page = 0;
            int dim = -1;
            while (true) {
                Page<VectorRecord> p = vectorRepository.findAll(PageRequest.of(page, rebuildPageSize));
                if (!p.hasContent()) break;
                for (VectorRecord r : p.getContent()) {
                    try {
                        byte[] blob = r.getVectorBlob();
                        float[] v = null;
                        if (blob != null && blob.length > 0) v = VectorServiceHelper.gzipBytesToFloatArrayStatic(blob);
                        else v = VectorServiceHelper.jsonToFloatArray(r.getVectorJson());
                        if (v != null) { dim = v.length; break; }
                    } catch (Exception ex) {
                        // skip malformed
                    }
                }
                if (dim != -1) break;
                if (!p.hasNext()) break;
                page++;
            }
            if (dim == -1) return;
            ensureIndex(dim);
            // second pass: add all vectors page by page
            page = 0;
            while (true) {
                Page<VectorRecord> p = vectorRepository.findAll(PageRequest.of(page, rebuildPageSize));
                if (!p.hasContent()) break;
                for (VectorRecord r : p.getContent()) {
                    try {
                        byte[] blob = r.getVectorBlob();
                        float[] v = null;
                        if (blob != null && blob.length > 0) v = VectorServiceHelper.gzipBytesToFloatArrayStatic(blob);
                        else v = VectorServiceHelper.jsonToFloatArray(r.getVectorJson());
                        if (v == null) continue;
                        index.add(new SimpleItem(r.getId(), v));
                    } catch (Exception ex) {
                        // skip malformed
                    }
                }
                if (!p.hasNext()) break;
                page++;
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    @Override
    public long size() {
        lock.readLock().lock();
        try {
            if (index != null) return index.size();
            return store.size();
        } finally {
            lock.readLock().unlock();
        }
    }

    private double cosine(float[] a, float[] b) {
        if (a == null || b == null) return -1.0;
        int n = Math.min(a.length, b.length);
        double dot=0, na=0, nb=0;
        for (int i=0;i<n;i++) { dot += a[i]*b[i]; na += a[i]*a[i]; nb += b[i]*b[i]; }
        if (na==0 || nb==0) return -1.0;
        return dot / (Math.sqrt(na)*Math.sqrt(nb));
    }

    @Override
    public void persistTo(java.nio.file.Path file) throws Exception {
        lock.readLock().lock();
        try {
            if (index != null) {
                // serialize a deterministic Map of id->vector extracted from the index
                Map<String, float[]> persistMap = new HashMap<>();
                for (com.github.jelmerk.hnswlib.core.Item<String, float[]> it : index.items()) {
                    persistMap.put(it.id(), Arrays.copyOf(it.vector(), it.vector().length));
                }
                List<String> ids = new ArrayList<>();
                for (String k : persistMap.keySet()) ids.add(k);
                log.info("persistTo: extracted map size={} from index size={} ids={}", persistMap.size(), index.size(), ids);
                try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
                    oos.writeObject(persistMap);
                }
                // record last persisted metadata on explicit persist
                lastPersistedPath = file.toAbsolutePath().toString();
                lastPersistedAt = java.time.Instant.now();
                log.info("Persisted HNSW index (as map) to {}", lastPersistedPath);
                return;
            }
            // fallback: serialize the buffer store
            try (ObjectOutputStream oos = new ObjectOutputStream(new BufferedOutputStream(new FileOutputStream(file.toFile())))) {
                oos.writeObject(this.store);
            }
        } finally {
            lock.readLock().unlock();
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public void loadFrom(java.nio.file.Path file) throws Exception {
        lock.writeLock().lock();
        try {
            // Prefer our serialized Map format first (deterministic), then fall back to jelmerk index load
            boolean handled = false;
            try (ObjectInputStream ois = new ObjectInputStream(new BufferedInputStream(new FileInputStream(file.toFile())))) {
                Object o = ois.readObject();
                if (o instanceof Map) {
                    // replace current index/store with the persisted map
                    Map<String, float[]> persistedMap = (Map<String, float[]>) o;
                    List<String> keys = new ArrayList<>(persistedMap.keySet());
                    log.info("loadFrom: deserialized map size={} keys={}", persistedMap.size(), keys);
                    // determine dimension
                    int dim = -1;
                    for (float[] v : persistedMap.values()) { if (v != null) { dim = v.length; break; } }
                    if (dim == -1) return;
                    // clear current state
                    index = null;
                    store.clear();
                    ensureIndex(dim);
                    for (Map.Entry<String, float[]> e : persistedMap.entrySet()) {
                        if (e.getValue() != null) index.add(new SimpleItem(e.getKey(), e.getValue()));
                    }
                    List<String> idxIds = new ArrayList<>();
                    for (com.github.jelmerk.hnswlib.core.Item<String, float[]> it : index.items()) idxIds.add(it.id());
                    log.info("loadFrom: index size after load={} ids={}", index.size(), idxIds);
                    handled = true;
                }
            } catch (Exception ex) {
                // not a Map or failed to deserialize; we'll try jelmerk load next
            }

            if (!handled) {
                try {
                    HnswIndex<String, float[], Item<String, float[]>, Float> loaded = HnswIndex.load(file);
                    this.index = loaded;
                    this.dimension = loaded.getDimensions();
                    List<String> idxIds2 = new ArrayList<>();
                    for (com.github.jelmerk.hnswlib.core.Item<String, float[]> it : index.items()) idxIds2.add(it.id());
                    log.info("loadFrom: loaded jelmerk HnswIndex with size={} ids={}", index.size(), idxIds2);
                } catch (Exception ex) {
                    throw ex;
                }
            }
        } finally {
            lock.writeLock().unlock();
        }
    }

    // allow runtime reconfiguration of HNSW params (rebuilds index)
    public synchronized void reconfigure(int newM, int newEfConstruction, int newMaxItems) throws Exception {
        lock.writeLock().lock();
        try {
            this.m = newM;
            this.efConstruction = newEfConstruction;
            this.maxItems = newMaxItems;
            // if we have an index, rebuild it with new params while preserving items
            Collection<com.github.jelmerk.hnswlib.core.Item<String, float[]>> items = null;
            if (index != null) items = index.items();
            int dim = this.dimension;
            if (dim <= 0) return;
            HnswIndex<String, float[], Item<String, float[]>, Float> newIndex = HnswIndex.newBuilder(dim, DistanceFunctions.FLOAT_COSINE_DISTANCE, maxItems)
                    .withM(m)
                    .withEfConstruction(efConstruction)
                    .build();
            if (items != null) {
                for (com.github.jelmerk.hnswlib.core.Item<String, float[]> it : items) {
                    newIndex.add(it);
                }
            }
            this.index = newIndex;
        } finally {
            lock.writeLock().unlock();
        }
    }

    public Map<String,Integer> getHnswParams() {
        Map<String,Integer> out = new HashMap<>();
        out.put("m", m);
        out.put("efConstruction", efConstruction);
        out.put("maxItems", maxItems);
        return out;
    }

    public int getDimensions() { return this.dimension; }

    @PreDestroy
    public void autoPersistOnShutdown() {
        if (!autoPersistEnabled) {
            log.info("Auto-persist disabled (hnsw.auto.persist.enabled=false); skipping persist on shutdown");
            return;
        }
        try {
            long sz = size();
            if (sz < autoPersistMinSize) {
                log.info("Auto-persist skipped: index size {} < min-size {}", sz, autoPersistMinSize);
                return;
            }
            if (persistPath == null || persistPath.isBlank()) {
                log.warn("persistPath not configured; skipping auto-persist");
                return;
            }
            java.nio.file.Path p = java.nio.file.Path.of(persistPath);
            java.nio.file.Files.createDirectories(p.getParent());
            persistTo(p);
            lastPersistedPath = p.toAbsolutePath().toString();
            lastPersistedAt = java.time.Instant.now();
            log.info("HNSW persisted to {} (size={})", lastPersistedPath, size());
        } catch (Exception e) {
            log.error("Failed to auto-persist HNSW index: {}", e.getMessage(), e);
        }
    }

    // expose last persist metadata for admin queries
    public java.util.Map<String,Object> getLastPersistInfo() {
        java.util.Map<String,Object> out = new java.util.HashMap<>();
        out.put("path", lastPersistedPath);
        out.put("timestamp", lastPersistedAt == null ? null : lastPersistedAt.toString());
        out.put("autoPersistEnabled", autoPersistEnabled);
        out.put("autoPersistMinSize", autoPersistMinSize);
        return out;
    }

    // runtime accessors for auto-load flag (admin control)
    public boolean isAutoLoadEnabled() {
        return this.autoLoadEnabled;
    }

    public void setAutoLoadEnabled(boolean enabled) {
        this.autoLoadEnabled = enabled;
    }
}

// simple Item implementation for jelmerk HNSW
class SimpleItem implements com.github.jelmerk.hnswlib.core.Item<String, float[]> {
    private final String id;
    private final float[] vector;

    SimpleItem(String id, float[] vector) {
        this.id = id;
        this.vector = vector;
    }

    @Override
    public String id() { return id; }

    @Override
    public float[] vector() { return vector; }

    @Override
    public int dimensions() { return vector != null ? vector.length : 0; }

    @Override
    public long version() { return 0L; }
}
