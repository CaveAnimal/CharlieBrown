package com.example.codetools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.zip.GZIPInputStream;

public final class VectorUtils {
    private VectorUtils() {}

    public static float[] gzipBytesToFloatArray(byte[] blob) throws Exception {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(blob));
             DataInputStream dis = new DataInputStream(gzis)) {
            int len = dis.readInt();
            float[] arr = new float[len];
            for (int i = 0; i < len; i++) arr[i] = dis.readFloat();
            return arr;
        }
    }

    public static float[] jsonToFloatArray(String json) throws Exception {
        return new ObjectMapper().readValue(json, float[].class);
    }
}
