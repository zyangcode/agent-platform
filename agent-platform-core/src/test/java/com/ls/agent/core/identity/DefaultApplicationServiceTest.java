package com.ls.agent.core.identity;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.PasswordHasher;
import com.ls.agent.core.identity.application.DefaultApplicationService;
import com.ls.agent.core.identity.command.UpdateApplicationCommand;
import com.ls.agent.core.identity.dto.ApplicationDTO;
import com.ls.agent.core.identity.entity.ApplicationEntity;
import com.ls.agent.core.identity.mapper.ApiKeyMapper;
import com.ls.agent.core.identity.mapper.ApplicationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultApplicationServiceTest {

    private final ApplicationMapper applicationMapper = mock(ApplicationMapper.class);
    private final ApiKeyMapper apiKeyMapper = mock(ApiKeyMapper.class);
    private final ApiKeyGenerator apiKeyGenerator = mock(ApiKeyGenerator.class);
    private final PasswordHasher passwordHasher = mock(PasswordHasher.class);
    private final DefaultApplicationService service = new DefaultApplicationService(
            applicationMapper,
            apiKeyMapper,
            apiKeyGenerator,
            passwordHasher
    );

    @Test
    void updateApplicationRenamesOwnedActiveApplication() {
        when(applicationMapper.selectOne(any(Wrapper.class))).thenReturn(application());

        ApplicationDTO result = service.updateApplication(new UpdateApplicationCommand(
                1L,
                10001L,
                20001L,
                " Renamed App ",
                "new description"
        ));

        assertThat(result.applicationId()).isEqualTo(20001L);
        assertThat(result.name()).isEqualTo("Renamed App");
        assertThat(result.description()).isEqualTo("new description");

        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getName()).isEqualTo("Renamed App");
        assertThat(captor.getValue().getDescription()).isEqualTo("new description");
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void disableApplicationMarksOwnedActiveApplicationDisabled() {
        when(applicationMapper.selectOne(any(Wrapper.class))).thenReturn(application());

        ApplicationDTO result = service.disableApplication(1L, 10001L, 20001L);

        assertThat(result.status()).isEqualTo("DISABLED");
        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("DISABLED");
    }

    @Test
    void enableApplicationMarksOwnedDisabledApplicationActive() {
        when(applicationMapper.selectOne(any(Wrapper.class))).thenReturn(application("DISABLED"));

        ApplicationDTO result = service.enableApplication(1L, 10001L, 20001L);

        assertThat(result.status()).isEqualTo("ACTIVE");
        ArgumentCaptor<ApplicationEntity> captor = ArgumentCaptor.forClass(ApplicationEntity.class);
        verify(applicationMapper).updateById(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    void updateApplicationRejectsBlankName() {
        assertThatThrownBy(() -> service.updateApplication(new UpdateApplicationCommand(
                1L,
                10001L,
                20001L,
                " ",
                "description"
        ))).isInstanceOf(BizException.class);

        verify(applicationMapper, never()).updateById(any(ApplicationEntity.class));
    }

    private ApplicationEntity application() {
        return application("ACTIVE");
    }

    private ApplicationEntity application(String status) {
        ApplicationEntity entity = new ApplicationEntity();
        entity.setId(20001L);
        entity.setTenantId(1L);
        entity.setOwnerUserId(10001L);
        entity.setName("Demo App");
        entity.setDescription("old description");
        entity.setStatus(status);
        return entity;
    }
}
