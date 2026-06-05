package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.EmbeddingService;
import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;

import java.util.Locale;

public class MockEmbeddingService implements EmbeddingService {

    public static final String MODEL = "mock-hash-embedding";
    public static final int DIMENSION = 768;

    @Override
    public EmbeddingVectorDTO embed(String text) {
        if (text == null || text.isBlank()) {
            return new EmbeddingVectorDTO(MODEL, new float[0]);
        }
        float[] values = new float[DIMENSION];
        for (String term : terms(text)) {
            int index = Math.floorMod(term.hashCode(), DIMENSION);
            values[index] = 1.0f;
        }
        return new EmbeddingVectorDTO(MODEL, values);
    }

    private String[] terms(String text) {
        String normalized = text.toLowerCase(Locale.ROOT)
                .replaceAll("[^\\p{IsHan}\\p{IsAlphabetic}\\p{IsDigit}]+", " ")
                .strip();
        if (normalized.isBlank()) {
            return new String[0];
        }
        return normalized.split("\\s+");
    }
}
