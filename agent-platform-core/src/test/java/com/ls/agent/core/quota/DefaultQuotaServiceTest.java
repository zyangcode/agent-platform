package com.ls.agent.core.quota;

import com.baomidou.mybatisplus.core.conditions.Wrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.quota.application.DefaultQuotaService;
import com.ls.agent.core.quota.command.CommitQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReserveQuotaCommand;
import com.ls.agent.core.quota.dto.QuotaReservationDTO;
import com.ls.agent.core.quota.entity.QuotaConfigEntity;
import com.ls.agent.core.quota.entity.QuotaReservationEntity;
import com.ls.agent.core.quota.mapper.QuotaConfigMapper;
import com.ls.agent.core.quota.mapper.QuotaReservationMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DefaultQuotaServiceTest {

    private final QuotaConfigMapper configMapper = mock(QuotaConfigMapper.class);
    private final QuotaReservationMapper reservationMapper = mock(QuotaReservationMapper.class);
    private final DefaultQuotaService service = new DefaultQuotaService(configMapper, reservationMapper);

    @Test
    void reserveAllowsRequestWhenNoQuotaConfigExists() {
        when(configMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        QuotaReservationDTO result = service.reserve(new ReserveQuotaCommand(
                "tr_1",
                1L,
                20001L,
                10001L,
                1000L
        ));

        ArgumentCaptor<QuotaReservationEntity> captor = ArgumentCaptor.forClass(QuotaReservationEntity.class);
        verify(reservationMapper).insert(captor.capture());
        assertThat(captor.getValue().getTraceId()).isEqualTo("tr_1");
        assertThat(captor.getValue().getEstimatedTokens()).isEqualTo(1000L);
        assertThat(captor.getValue().getStatus()).isEqualTo("RESERVED");
        assertThat(result.status()).isEqualTo("RESERVED");
    }

    @Test
    void reserveTouchesActiveQuotaConfigVersionForCasPreDeduct() {
        when(configMapper.selectOne(any(Wrapper.class))).thenReturn(activeApplicationConfig(2000L));
        when(configMapper.update(any(QuotaConfigEntity.class), any(Wrapper.class))).thenReturn(1);

        service.reserve(new ReserveQuotaCommand(
                "tr_1",
                1L,
                20001L,
                10001L,
                1000L
        ));

        ArgumentCaptor<QuotaConfigEntity> configCaptor = ArgumentCaptor.forClass(QuotaConfigEntity.class);
        verify(configMapper).update(configCaptor.capture(), any(Wrapper.class));
        assertThat(configCaptor.getValue().getVersion()).isEqualTo(1);
    }

    @Test
    void reserveRejectsWhenSingleRequestLimitExceeded() {
        when(configMapper.selectOne(any(Wrapper.class))).thenReturn(activeApplicationConfig(500L));

        assertThatThrownBy(() -> service.reserve(new ReserveQuotaCommand(
                "tr_1",
                1L,
                20001L,
                10001L,
                1000L
        )))
                .isInstanceOf(BizException.class)
                .extracting("code")
                .isEqualTo(ErrorCode.QUOTA_EXCEEDED.getCode());

        verify(reservationMapper, never()).insert(any(QuotaReservationEntity.class));
    }

    @Test
    void commitUpdatesOnlyReservedReservation() {
        QuotaReservationEntity reservation = reserved("tr_1", 0);
        when(reservationMapper.selectOne(any(Wrapper.class))).thenReturn(reservation);
        when(reservationMapper.update(any(QuotaReservationEntity.class), any(Wrapper.class))).thenReturn(1);

        service.commit(new CommitQuotaReservationCommand("tr_1", 800L));

        ArgumentCaptor<QuotaReservationEntity> captor = ArgumentCaptor.forClass(QuotaReservationEntity.class);
        verify(reservationMapper).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getStatus()).isEqualTo("COMMITTED");
        assertThat(captor.getValue().getActualTokens()).isEqualTo(800L);
    }

    @Test
    void releaseIsIdempotentWhenReservationAlreadyFinalized() {
        when(reservationMapper.selectOne(any(Wrapper.class))).thenReturn(null);

        assertThatCode(() -> service.release(new ReleaseQuotaReservationCommand("tr_1")))
                .doesNotThrowAnyException();

        verify(reservationMapper, never()).update(any(QuotaReservationEntity.class), any(Wrapper.class));
    }

    @Test
    void releaseUpdatesOnlyReservedReservation() {
        QuotaReservationEntity reservation = reserved("tr_1", 0);
        when(reservationMapper.selectOne(any(Wrapper.class))).thenReturn(reservation);
        when(reservationMapper.update(any(QuotaReservationEntity.class), any(Wrapper.class))).thenReturn(1);

        service.release(new ReleaseQuotaReservationCommand("tr_1"));

        ArgumentCaptor<QuotaReservationEntity> captor = ArgumentCaptor.forClass(QuotaReservationEntity.class);
        verify(reservationMapper).update(captor.capture(), any(Wrapper.class));
        assertThat(captor.getValue().getStatus()).isEqualTo("RELEASED");
    }

    private QuotaConfigEntity activeApplicationConfig(long singleRequestLimit) {
        QuotaConfigEntity config = new QuotaConfigEntity();
        config.setId(10L);
        config.setTenantId(1L);
        config.setSubjectType("APPLICATION");
        config.setSubjectId(20001L);
        config.setDailyLimitTokens(0L);
        config.setMonthlyLimitTokens(0L);
        config.setSingleRequestLimitTokens(singleRequestLimit);
        config.setStatus("ACTIVE");
        config.setVersion(0);
        return config;
    }

    private QuotaReservationEntity reserved(String traceId, int version) {
        QuotaReservationEntity reservation = new QuotaReservationEntity();
        reservation.setTraceId(traceId);
        reservation.setTenantId(1L);
        reservation.setApplicationId(20001L);
        reservation.setUserId(10001L);
        reservation.setEstimatedTokens(1000L);
        reservation.setStatus("RESERVED");
        reservation.setVersion(version);
        return reservation;
    }
}
