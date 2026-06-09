package com.ls.agent.core.rag.api;

import com.ls.agent.core.rag.dto.RagSearchResultDTO;
import com.ls.agent.core.rag.dto.SemanticCacheLookupCommand;
import com.ls.agent.core.rag.dto.SemanticCachePutCommand;

import java.util.List;
import java.util.Optional;

public interface SemanticCacheService {

    boolean enabled();

    Optional<List<RagSearchResultDTO>> lookup(SemanticCacheLookupCommand command);

    void put(SemanticCachePutCommand command);

    static SemanticCacheService noop() {
        return new SemanticCacheService() {
            @Override
            public boolean enabled() {
                return false;
            }

            @Override
            public Optional<List<RagSearchResultDTO>> lookup(SemanticCacheLookupCommand command) {
                return Optional.empty();
            }

            @Override
            public void put(SemanticCachePutCommand command) {
            }
        };
    }
}
