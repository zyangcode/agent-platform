package com.ls.agent.core.experience.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import com.ls.agent.core.experience.api.ExperienceSkillResolver;
import com.ls.agent.core.experience.dto.ExperienceSkillDTO;
import com.ls.agent.core.experience.entity.ExperienceSkillEntity;
import com.ls.agent.core.experience.mapper.ExperienceSkillMapper;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

public class DefaultExperienceSkillResolver implements ExperienceSkillResolver {

    private static final String STATUS_ACTIVE = "ACTIVE";
    private static final int FETCH_MULTIPLIER = 10;
    private static final int MIN_KEYWORD_LENGTH = 2;
    private static final Pattern KEYWORD_SPLITTER = Pattern.compile(
            "[\\s,./;:'\"\\[\\]{}|_=+\\-!@#$%^&*()\\u3000\\u3001\\uff0c\\u3002\\uff01\\uff1f\\uff1b\\uff1a]+");

    private final ExperienceSkillMapper experienceSkillMapper;

    public DefaultExperienceSkillResolver(ExperienceSkillMapper experienceSkillMapper) {
        this.experienceSkillMapper = experienceSkillMapper;
    }

    @Override
    public List<ExperienceSkillDTO> resolve(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId,
            String profileType,
            String userInput,
            int limit
    ) {
        int safeLimit = Math.max(1, limit);
        List<String> keywords = extractKeywords(userInput);
        List<ExperienceSkillEntity> candidates = experienceSkillMapper.selectList(
                baseWrapper(tenantId, applicationId, userId, profileId)
                        .orderByDesc(ExperienceSkillEntity::getUpdatedAt)
                        .last("limit " + safeLimit * FETCH_MULTIPLIER)
        );
        if (candidates == null || candidates.isEmpty()) {
            return List.of();
        }
        // 全部注入，不做关键词匹配，由 token 预算控制裁剪
        return candidates.stream().limit(safeLimit).map(this::toDTO).toList();
    }

    private LambdaQueryWrapper<ExperienceSkillEntity> baseWrapper(
            Long tenantId,
            Long applicationId,
            Long userId,
            Long profileId
    ) {
        return new LambdaQueryWrapper<ExperienceSkillEntity>()
                .eq(ExperienceSkillEntity::getTenantId, tenantId)
                .eq(ExperienceSkillEntity::getStatus, STATUS_ACTIVE)
                .and(w -> w.isNull(ExperienceSkillEntity::getApplicationId)
                        .or().eq(ExperienceSkillEntity::getApplicationId, applicationId))
                .and(w -> w.isNull(ExperienceSkillEntity::getUserId)
                        .or().eq(ExperienceSkillEntity::getUserId, userId))
                .and(w -> w.isNull(ExperienceSkillEntity::getProfileId)
                        .or().eq(ExperienceSkillEntity::getProfileId, profileId));
    }

    private int score(ExperienceSkillEntity skill, List<String> keywords, Long profileId, String profileType) {
        int score = countHits(skill, keywords) * 10;
        if (matchesDomain(skill, profileType)) {
            score += 8;
        }
        if (matchesProfile(skill, profileId)) {
            score += 3;
        }
        if (skill.getApplicationId() != null) {
            score += 2;
        }
        if (skill.getUserId() != null) {
            score += 2;
        }
        return score;
    }

    private boolean matchesProfile(ExperienceSkillEntity skill, Long profileId) {
        return skill.getProfileId() != null && Objects.equals(skill.getProfileId(), profileId);
    }

    private boolean matchesDomain(ExperienceSkillEntity skill, String profileType) {
        return hasText(skill.getDomain())
                && hasText(profileType)
                && skill.getDomain().equalsIgnoreCase(profileType);
    }

    private int countHits(ExperienceSkillEntity skill, List<String> keywords) {
        if (keywords.isEmpty()) {
            return 0;
        }
        String userText = String.join(" ", keywords).toLowerCase(Locale.ROOT);
        String[] triggers = skill.getTriggerKeywords() == null ? new String[0] : skill.getTriggerKeywords();
        int hits = 0;
        for (String trigger : triggers) {
            if (trigger != null && !trigger.isBlank()
                    && userText.contains(trigger.strip().toLowerCase(Locale.ROOT))) {
                hits++;
            }
        }
        return hits;
    }

    private List<String> extractKeywords(String userInput) {
        if (!hasText(userInput)) {
            return List.of();
        }
        return Arrays.stream(KEYWORD_SPLITTER.split(userInput.strip()))
                .map(String::strip)
                .filter(word -> word.length() >= MIN_KEYWORD_LENGTH)
                .distinct()
                .limit(10)
                .toList();
    }

    private ExperienceSkillDTO toDTO(ExperienceSkillEntity skill) {
        String[] keywords = skill.getTriggerKeywords();
        return new ExperienceSkillDTO(
                skill.getId(),
                skill.getCode(),
                skill.getName(),
                skill.getDomain(),
                keywords == null ? List.of() : List.of(keywords),
                truncate(skill.getContent())
        );
    }

    private String truncate(String content) {
        if (content == null) {
            return "";
        }
        int maxLen = 500;
        return content.length() <= maxLen ? content : content.substring(0, maxLen) + "...";
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
