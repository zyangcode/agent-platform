package com.ls.agent.core.quota.dto;

public record QuotaReservationDTO(
        String traceId,
        Long tenantId,
        Long applicationId,
        Long userId,
        Long estimatedTokens,
        Long actualTokens,
        String status,
        Integer version
) {
}
