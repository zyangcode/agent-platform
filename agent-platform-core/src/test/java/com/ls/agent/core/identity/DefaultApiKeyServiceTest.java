package com.ls.agent.core.identity;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.PasswordHasher;
import com.ls.agent.core.identity.application.DefaultApiKeyService;
import com.ls.agent.core.identity.dto.ApiKeyAuthResult;
import com.ls.agent.core.identity.entity.ApiKeyEntity;
import com.ls.agent.core.identity.entity.ApplicationEntity;
import com.ls.agent.core.identity.mapper.ApiKeyMapper;
import com.ls.agent.core.identity.mapper.ApplicationMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class DefaultApiKeyServiceTest {

    private final ApiKeyMapper apiKeyMapper = mock(ApiKeyMapper.class);
    private final ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
    private final ApiKeyGenerator apiKeyGenerator = mock(ApiKeyGenerator.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final DefaultApiKeyService service = new DefaultApiKeyService(
            apiKeyMapper,
            applicationMapper,
            apiKeyGenerator,
            passwordHasher
    );

    @Test
    void authenticateReturnsTenantApplicationAndOwner() {
        when(apiKeyGenerator.prefixOf("sk-valid")).thenReturn("sk-valid");
        when(apiKeyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(apiKey()));
        when(passwordHasher.matches("sk-valid", "hash")).thenReturn(true);
        when(applicationMapper.selectById(20001L)).thenReturn(application());

        ApiKeyAuthResult result = service.authenticate("sk-valid");

        assertThat(result.tenantId()).isEqualTo(1L);
        assertThat(result.applicationId()).isEqualTo(20001L);
        assertThat(result.userId()).isEqualTo(10001L);
    }

    @Test
    void authenticateRejectsRevokedKey() {
        ApiKeyEntity apiKey = apiKey();
        apiKey.setStatus("REVOKED");
        when(apiKeyGenerator.prefixOf("sk-revoked")).thenReturn("sk-revok");
        when(apiKeyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(apiKey));

        assertThatThrownBy(() -> service.authenticate("sk-revoked"))
                .isInstanceOf(BizException.class);
    }

    @Test
    void authenticateRejectsExpiredKey() {
        ApiKeyEntity apiKey = apiKey();
        apiKey.setExpiresAt(LocalDateTime.now().minusDays(1));
        when(apiKeyGenerator.prefixOf("sk-expired")).thenReturn("sk-expir");
        when(apiKeyMapper.selectList(any(Wrapper.class))).thenReturn(List.of(apiKey));

        assertThatThrownBy(() -> service.authenticate("sk-expired"))
                .isInstanceOf(BizException.class);
    }

    private ApiKeyEntity apiKey() {
        ApiKeyEntity entity = new ApiKeyEntity();
        entity.setId(30001L);
        entity.setTenantId(1L);
        entity.setApplicationId(20001L);
        entity.setKeyPrefix("sk-valid");
        entity.setKeyHash("hash");
        entity.setStatus("ACTIVE");
        return entity;
    }

    private ApplicationEntity application() {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(20001L);
        entity.setTenantId(1L);
        entity.setOwnerUserId(10001L);
        entity.setStatus("ACTIVE");
        return entity;
    }
}
