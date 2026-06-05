package com.ls.agent.core.rag.dto;

import java.util.Arrays;

public record EmbeddingVectorDTO(
        String model,
        float[] values
) {

    public EmbeddingVectorDTO {
        model = model == null ? "" : model;
        values = values == null ? new float[0] : Arrays.copyOf(values, values.length);
    }

    @Override
    public float[] values() {
        return Arrays.copyOf(values, values.length);
    }

    public int dimension() {
        return values.length;
    }
}
