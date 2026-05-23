package com.ls.agent.gateway.filter;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.security.api.SecurityEventService;
import com.ls.agent.core.security.api.SensitiveDataScanner;
import com.ls.agent.core.security.command.RecordSecurityEventCommand;
import com.ls.agent.core.security.dto.SensitiveDataFindingDTO;
import com.ls.agent.gateway.dto.GatewayChatRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class SensitiveDataFilter {

    private static final Logger log = LoggerFactory.getLogger(SensitiveDataFilter.class);

    private static final String LOCATION_REQUEST_MESSAGE = "REQUEST_MESSAGE";
    private static final String ACTION_BLOCK = "BLOCK";

    private final SensitiveDataScanner sensitiveDataScanner;
    private final SecurityEventService securityEventService;

    public SensitiveDataFilter(
            SensitiveDataScanner sensitiveDataScanner,
            SecurityEventService securityEventService
    ) {
        this.sensitiveDataScanner = sensitiveDataScanner;
        this.securityEventService = securityEventService;
    }

    public void scanRequest(String traceId, Long tenantId, Long applicationId, Long userId, GatewayChatRequest request) {
        List<SensitiveDataFindingDTO> findings = scanMessage(request == null ? null : request.message());
        if (findings.isEmpty()) {
            return;
        }
        findings.forEach(finding -> recordEvent(traceId, tenantId, applicationId, userId, finding));
        throw new BizException(ErrorCode.SECURITY_BLOCKED, "Request contains sensitive data");
    }

    private List<SensitiveDataFindingDTO> scanMessage(String message) {
        try {
            List<SensitiveDataFindingDTO> findings = sensitiveDataScanner.scan(message, LOCATION_REQUEST_MESSAGE);
            return findings == null ? List.of() : findings;
        } catch (Exception ex) {
            throw new BizException(ErrorCode.SECURITY_BLOCKED, "Sensitive data scan failed");
        }
    }

    private void recordEvent(
            String traceId,
            Long tenantId,
            Long applicationId,
            Long userId,
            SensitiveDataFindingDTO finding
    ) {
        try {
            securityEventService.record(new RecordSecurityEventCommand(
                    traceId,
                    tenantId,
                    applicationId,
                    userId,
                    finding.eventType(),
                    finding.location(),
                    finding.sourceTextHash(),
                    finding.maskedSample(),
                    ACTION_BLOCK
            ));
        } catch (Exception ex) {
            log.warn("Security event record failed, traceId={}, eventType={}", traceId, finding.eventType(), ex);
        }
    }
}
