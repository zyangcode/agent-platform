package com.ls.agent.core.rag.api;

import com.ls.agent.core.rag.dto.EmbeddingVectorDTO;

public interface EmbeddingService {

    EmbeddingVectorDTO embed(String text);
}
