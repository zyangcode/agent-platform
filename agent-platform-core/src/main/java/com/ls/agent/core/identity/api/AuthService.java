package com.ls.agent.core.identity.api;

import com.ls.agent.core.identity.command.LoginCommand;
import com.ls.agent.core.identity.command.RegisterCommand;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.identity.dto.LoginResult;
import com.ls.agent.core.identity.dto.RegisterResult;

public interface AuthService {

    RegisterResult register(RegisterCommand command);

    LoginResult login(LoginCommand command);

    CurrentUserDTO getCurrentUser(Long userId);
}
