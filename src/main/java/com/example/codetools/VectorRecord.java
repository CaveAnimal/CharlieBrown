package com.example.codetools;

import jakarta.persistence.*;

@Entity
@Table(name = "vectors", indexes = {
        @Index(name = "idx_app", columnList = "application_id")
})
public class VectorRecord {

    @Id
    private String id;

    @Column(name = "application_id", nullable = false)
    private String applicationId;

    private String path;

    @Lob
    @Column(name = "content", columnDefinition = "CLOB")
    private String content;

    // compressed binary embedding blob (GZIPped float bytes)
    @Lob
    @Column(name = "vector_blob", columnDefinition = "BLOB")
    private byte[] vectorBlob;

    // keep JSON for human inspectability (optional)
    @Lob
    @Column(name = "vector", columnDefinition = "CLOB")
    private String vectorJson;

    @Column(name = "checksum")
    private String checksum;

    @Column(name = "chunk_index")
    private Integer chunkIndex;

    @Column(name = "start_offset")
    private Integer startOffset;

    @Column(name = "end_offset")
    private Integer endOffset;

    @Column(name = "metadata", columnDefinition = "CLOB")
    private String metadata;

    private long createdAt;

    public VectorRecord() {}

    // getters/setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getApplicationId() { return applicationId; }
    public void setApplicationId(String applicationId) { this.applicationId = applicationId; }
    public String getPath() { return path; }
    public void setPath(String path) { this.path = path; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public byte[] getVectorBlob() { return vectorBlob; }
    public void setVectorBlob(byte[] vectorBlob) { this.vectorBlob = vectorBlob; }
    public String getVectorJson() { return vectorJson; }
    public void setVectorJson(String vectorJson) { this.vectorJson = vectorJson; }
    public String getChecksum() { return checksum; }
    public void setChecksum(String checksum) { this.checksum = checksum; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public Integer getStartOffset() { return startOffset; }
    public void setStartOffset(Integer startOffset) { this.startOffset = startOffset; }
    public Integer getEndOffset() { return endOffset; }
    public void setEndOffset(Integer endOffset) { this.endOffset = endOffset; }
    public String getMetadata() { return metadata; }
    public void setMetadata(String metadata) { this.metadata = metadata; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
