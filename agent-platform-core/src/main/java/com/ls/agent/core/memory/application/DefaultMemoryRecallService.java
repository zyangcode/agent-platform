package com.ls.agent.core.memory.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.core.memory.api.MemoryRecallService;
import com.ls.agent.core.memory.dto.MemoryDTO;
import com.ls.agent.core.memory.entity.MemoryEntity;
import com.ls.agent.core.memory.mapper.MemoryMapper;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class DefaultMemoryRecallService implements MemoryRecallService {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int FETCH_MULTIPLIER = 10;
    private static final Pattern KEYWORD_SPLITTER = Pattern.compile(
            "[\\s,./;:'\"\\[\\]{}|_=+\\-!@#$%^&*()"
                    + "，。！？；：、“”‘’]+");
    private static final int MIN_KEYWORD_LENGTH = 2;

    private final MemoryMapper memoryMapper;

    public DefaultMemoryRecallService(MemoryMapper memoryMapper) {
        this.memoryMapper = memoryMapper;
    }

    @Override
    public List<MemoryDTO> recall(Long tenantId, Long applicationId, Long userId, Long profileId, String query, int limit) {
        List<String> keywords = extractKeywords(query);
        LambdaQueryWrapper<MemoryEntity> wrapper = baseWrapper(tenantId, applicationId, userId, profileId);

        // Add keyword-based filtering if keywords exist
        if (!keywords.isEmpty()) {
            wrapper.and(w -> {
                for (int i = 0; i < keywords.size(); i++) {
                    if (i == 0) {
                        w.like(MemoryEntity::getContent, keywords.get(i));
                    } else {
                        w.or().like(MemoryEntity::getContent, keywords.get(i));
                    }
                }
            });
        }

        wrapper.orderByDesc(MemoryEntity::getUpdatedAt)
                .last("limit " + Math.max(1, limit * FETCH_MULTIPLIER));

        List<MemoryEntity> candidates = memoryMapper.selectList(wrapper);
        if ((candidates == null || candidates.isEmpty()) && !keywords.isEmpty()) {
            candidates = memoryMapper.selectList(baseWrapper(tenantId, applicationId, userId, profileId)
                    .orderByDesc(MemoryEntity::getUpdatedAt)
                    .last("limit " + Math.max(1, limit * FETCH_MULTIPLIER)));
        }
        if (candidates == null) {
            candidates = List.of();
        }

        // Score by keyword match count, then by recency
        List<MemoryEntity> scored = candidates.stream()
                .sorted(Comparator
                        .<MemoryEntity>comparingInt(m -> keywords.isEmpty() ? 0 : countKeywordHits(m.getContent(), keywords))
                        .reversed()
                        .thenComparing(MemoryEntity::getUpdatedAt, Comparator.nullsLast(Comparator.reverseOrder())))
                .toList();

        return scored.stream()
                .limit(Math.max(1, limit))
                .map(m -> new MemoryDTO(m.getMemoryType(), truncateContent(m.getContent())))
                .toList();
    }

    private LambdaQueryWrapper<MemoryEntity> baseWrapper(Long tenantId, Long applicationId, Long userId, Long profileId) {
        return new LambdaQueryWrapper<MemoryEntity>()
                .eq(MemoryEntity::getTenantId, tenantId)
                .eq(MemoryEntity::getUserId, userId)
                .eq(MemoryEntity::getStatus, STATUS_ACTIVE)
                .and(w -> w.isNull(MemoryEntity::getApplicationId)
                        .or().eq(MemoryEntity::getApplicationId, applicationId))
                .and(w -> w.isNull(MemoryEntity::getProfileId)
                        .or().eq(MemoryEntity::getProfileId, profileId));
    }

    /** Extract meaningful keywords from the user query. */
    private List<String> extractKeywords(String query) {
        if (query == null || query.isBlank()) {
            return List.of();
        }
        return Arrays.stream(KEYWORD_SPLITTER.split(query.strip()))
                .map(String::strip)
                .filter(word -> word.length() >= MIN_KEYWORD_LENGTH)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    /** Count how many keywords appear in the content. */
    private int countKeywordHits(String content, List<String> keywords) {
        if (content == null || content.isBlank()) {
            return 0;
        }
        String lower = content.toLowerCase();
        int hits = 0;
        for (String kw : keywords) {
            if (lower.contains(kw.toLowerCase())) {
                hits++;
            }
        }
        return hits;
    }

    /** Truncate memory content to a reasonable length for context injection. */
    private String truncateContent(String content) {
        if (content == null) {
            return "";
        }
        int maxLen = 300;
        if (content.length() <= maxLen) {
            return content;
        }
        return content.substring(0, maxLen) + "...";
    }
}
