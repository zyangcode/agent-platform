package com.ls.agent.core.model.provider;

import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import org.springframework.ai.chat.model.ChatModel;

public interface SpringAiChatModelFactory {

    ChatModel create(ModelConfigEntity config, ModelProviderEntity provider, String apiKey);
}
