package com.ls.agent.core.security.api;

import com.ls.agent.core.security.dto.SensitiveDataFindingDTO;

import java.util.List;

public interface SensitiveDataScanner {

    List<SensitiveDataFindingDTO> scan(String text, String location);
}
