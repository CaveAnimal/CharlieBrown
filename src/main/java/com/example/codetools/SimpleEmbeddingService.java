package com.example.codetools;

import org.springframework.stereotype.Service;

@Service
public class SimpleEmbeddingService implements EmbeddingService {
    // deterministic stub: convert string to simple fixed-size vector using hash
    @Override
    public float[] embed(String text) {
        int dim = 64;
        float[] v = new float[dim];
        int h = text.hashCode();
        for (int i = 0; i < dim; i++) {
            h = 31 * h + i;
            v[i] = ((h % 1000) - 500) / 500.0f; // pseudo values in [-1,1]
        }
        return v;
    }
}
