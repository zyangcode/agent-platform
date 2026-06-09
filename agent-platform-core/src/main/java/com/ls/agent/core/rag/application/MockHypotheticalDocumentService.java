package com.ls.agent.core.rag.application;

import com.ls.agent.core.rag.api.HypotheticalDocumentService;

import java.util.List;

public class MockHypotheticalDocumentService implements HypotheticalDocumentService {

    @Override
    public List<String> generate(String query, int maxDocuments) {
        if (query == null || query.isBlank() || maxDocuments <= 0) {
            return List.of();
        }
        return List.of("关于 " + query.strip() + " 的背景、约束、处理流程、异常原因和注意事项");
    }
}
