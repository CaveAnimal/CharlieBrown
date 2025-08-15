package com.example.codetools;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.ByteArrayInputStream;
import java.io.DataInputStream;
import java.util.zip.GZIPInputStream;

public final class VectorServiceHelper {

    private static final ObjectMapper mapper = new ObjectMapper();

    public static float[] gzipBytesToFloatArrayStatic(byte[] blob) throws Exception {
        try (GZIPInputStream gzis = new GZIPInputStream(new ByteArrayInputStream(blob));
             DataInputStream dis = new DataInputStream(gzis)) {
            int len = dis.readInt();
            float[] arr = new float[len];
            for (int i = 0; i < len; i++) arr[i] = dis.readFloat();
            return arr;
        }
    }

    public static float[] jsonToFloatArray(String json) throws Exception {
        if (json == null) return null;
        return mapper.readValue(json, float[].class);
    }
}
