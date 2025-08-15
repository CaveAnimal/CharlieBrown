package com.example.codetools;

import java.nio.file.Path;
import java.util.List;

public interface AnnIndex {
    void add(String id, float[] vector) throws Exception;
    void remove(String id) throws Exception;
    List<String> query(float[] q, int topK) throws Exception;
    void rebuildFromDatabase() throws Exception;
    long size();
    default void persistTo(Path file) throws Exception { }
    default void loadFrom(Path file) throws Exception { }
}
