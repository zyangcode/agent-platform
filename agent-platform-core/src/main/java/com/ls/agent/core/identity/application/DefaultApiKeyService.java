package com.ls.agent.core.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.ApiKeyService;
import com.ls.agent.core.identity.api.PasswordHasher;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.identity.entity.ApiKeyEntity;
import com.ls.agent.core.identity.entity.ApplicationEntity;
import com.ls.agent.core.identity.mapper.ApiKeyMapper;
import com.ls.agent.core.identity.mapper.ApplicationMapper;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
public class DefaultApiKeyService implements ApiKeyService {

    private static final String STATUS_ACTIVE = "ACTIVE";

    private final ApiKeyMapper apiKeyMapper;
    private final ApplicationMapper applicationMapper;
    private final ApiKeyGenerator apiKeyGenerator;
    private final PasswordHasher passwordHasher;

    public DefaultApiKeyService(
            ApiKeyMapper apiKeyMapper,
            ApplicationMapper applicationMapper,
            ApiKeyGenerator apiKeyGenerator,
            PasswordHasher passwordHasher
    ) {
        this.apiKeyMapper = apiKeyMapper;
        this.applicationMapper = applicationMapper;
        this.apiKeyGenerator = apiKeyGenerator;
        this.passwordHasher = passwordHasher;
    }

    @Override
    public ApiKeyAuthResult authenticate(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            throw new BizException(ErrorCode.API_KEY_INVALID, "API key is required");
        }
        String prefix = apiKeyGenerator.prefixOf(apiKey);
        ApiKeyEntity matched = apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKeyEntity>()
                        .eq(ApiKeyEntity::getKeyPrefix, prefix))
                .stream()
                .filter(candidate -> isActive(candidate) && passwordHasher.matches(apiKey, candidate.getKeyHash()))
                .findFirst()
                .orElseThrow(() -> new BizException(ErrorCode.API_KEY_INVALID, "Invalid API key"));

        ApplicationEntity application = applicationMapper.selectById(matched.getApplicationId());
        if (application == null
                || !STATUS_ACTIVE.equals(application.getStatus())
                || !matched.getTenantId().equals(application.getTenantId())) {
            throw new BizException(ErrorCode.APPLICATION_DISABLED, "Application is unavailable");
        }
        return new ApiKeyAuthResult(matched.getTenantId(), matched.getApplicationId(), application.getOwnerUserId());
    }

    private boolean isActive(ApiKeyEntity apiKey) {
        return STATUS_ACTIVE.equals(apiKey.getStatus())
                && (apiKey.getExpiresAt() == null || apiKey.getExpiresAt().isAfter(LocalDateTime.now()))
                && apiKey.getRevokedAt() == null;
    }
}
