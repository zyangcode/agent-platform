package com.ls.agent.core.model.application;

import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelInvokeService;
import com.ls.agent.core.model.command.ModelInvokeCommand;
import com.ls.agent.core.model.dto.ModelInvokeResult;
import com.ls.agent.core.model.dto.ModelMessage;
import com.ls.agent.core.model.dto.ModelUsageDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 模型调用服务的默认实现。
 * 负责实际的模型请求分发，包括 Mock 模型和未来接入的真实 LLM。
 */
@Service
public class DefaultModelInvokeService implements ModelInvokeService {

    /** 模型配置持久层 */
    private final ModelConfigMapper configMapper;
    /** 模型供应商持久层 */
    private final ModelProviderMapper providerMapper;

    /**
     * 构造函数注入依赖。
     */
    public DefaultModelInvokeService(ModelConfigMapper configMapper, ModelProviderMapper providerMapper) {
        this.configMapper = configMapper;
        this.providerMapper = providerMapper;
    }

    /**
     * 执行模型调用。
     *
     * @param command 调用命令，包含模型 ID 和消息历史
     * @return 调用结果，包含生成的消息和 Token 消耗
     */
    @Override
    public ModelInvokeResult invoke(ModelInvokeCommand command) {
        // 1. 校验模型配置 ID 不能为空
        Long modelConfigId = ModelValidation.requireNonNull(command.modelConfigId(), "modelConfigId");
        // 2. 检查模型配置是否存在且已激活
        ModelConfigEntity config = configMapper.selectById(modelConfigId);
        if (config == null || !ModelConstants.STATUS_ACTIVE.equals(config.getStatus())) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model config is unavailable");
        }
        // 3. 检查模型所属的供应商是否可用
        ModelProviderEntity provider = providerMapper.selectById(config.getProviderId());
        if (provider == null || !ModelConstants.STATUS_ACTIVE.equals(provider.getStatus())) {
            throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Model provider is unavailable");
        }
        // 4. 如果是 Mock 模型，则调用本地模拟逻辑
        if (ModelConstants.MODEL_MOCK_CHAT.equals(config.getModelName())) {
            return mockChat(config, command.messages());
        }
        // 5. 阶段 1 暂不支持真实 LLM 调用
        throw new BizException(ErrorCode.MODEL_INVOKE_FAILED, "Real model invocation is not enabled in stage 1 yet");
    }

    /**
     * 模拟对话逻辑（Mock Chat）。
     * 用于在没有真实 API Key 的情况下测试系统流程。
     */
    private ModelInvokeResult mockChat(ModelConfigEntity config, List<ModelMessage> messages) {
        // 1. 提取最后一条用户消息
        String lastUserMessage = messages == null ? "" : messages.stream()
                .filter(message -> "user".equals(message.role()))
                .map(ModelMessage::content)
                .reduce((first, second) -> second)
                .orElse("");
        // 2. 构造模拟回复：如果是空则返回默认，否则回显用户消息
        String assistantMessage = "[mock-chat] " + (lastUserMessage.isBlank()
                ? "Hello, this is a mock model response."
                : "Echo: " + lastUserMessage);
        // 3. 估算 Token 消耗
        int promptTokens = estimateTokens(lastUserMessage);
        int completionTokens = estimateTokens(assistantMessage);
        // 4. 封装结果返回
        return new ModelInvokeResult(
                config.getId(),
                config.getModelName(),
                assistantMessage,
                new ModelUsageDTO(promptTokens, completionTokens, promptTokens + completionTokens, true)
        );
    }

    /**
     * 简单的 Token 估算算法。
     * 规则：大约 4 个字符计为 1 个 Token。
     */
    private int estimateTokens(String text) {
        if (text == null || text.isBlank()) {
            return 0;
        }
        // 向上取整的简单实现
        return Math.max(1, (text.length() + 3) / 4);
    }
}
