package com.ls.agent.core.quota.api;

import com.ls.agent.core.quota.command.CommitQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReleaseQuotaReservationCommand;
import com.ls.agent.core.quota.command.ReserveQuotaCommand;
import com.ls.agent.core.quota.dto.QuotaReservationDTO;

public interface QuotaService {

    QuotaReservationDTO reserve(ReserveQuotaCommand command);

    void commit(CommitQuotaReservationCommand command);

    void release(ReleaseQuotaReservationCommand command);
}
