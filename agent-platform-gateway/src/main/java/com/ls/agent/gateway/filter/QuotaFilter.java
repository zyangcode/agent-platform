package com.ls.agent.gateway.filter;

import com.ls.agent.core.quota.api.QuotaService;
import com.ls.agent.core.quota.command.CommitQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReserveQuotaCommand;
import org.springframework.stereotype.Component;

@Component
public class QuotaFilter {

    private static final long DEFAULT_ESTIMATED_TOKENS = 1000L;

    private final QuotaService quotaService;

    public QuotaFilter(QuotaService quotaService) {
        this.quotaService = quotaService;
    }

    public void reserve(String traceId, Long tenantId, Long applicationId, Long userId) {
        quotaService.reserve(new ReserveQuotaCommand(
                traceId,
                tenantId,
                applicationId,
                userId,
                DEFAULT_ESTIMATED_TOKENS
        ));
    }

    public void commit(String traceId, Long actualTokens) {
        quotaService.commit(new CommitQuotaReservationCommand(traceId, actualTokens));
    }

    public void release(String traceId) {
        quotaService.release(new ReleaseQuotaReservationCommand(traceId));
    }
}
