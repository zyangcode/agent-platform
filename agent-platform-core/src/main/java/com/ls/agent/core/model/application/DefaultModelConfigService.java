package com.ls.agent.core.model.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.command.CreateModelConfigCommand;
import com.ls.agent.core.model.command.CreateModelProviderCommand;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.model.dto.ModelProviderDTO;
import com.ls.agent.core.model.entity.ModelConfigEntity;
import com.ls.agent.core.model.entity.ModelProviderEntity;
import com.ls.agent.core.model.mapper.ModelConfigMapper;
import com.ls.agent.core.model.mapper.ModelProviderMapper;
import com.ls.agent.core.support.security.SecretEncryptor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * 模型配置服务的默认实现。
 * 负责管理模型供应商信息（如 OpenAI）和具体的模型配置（如 GPT-4）。
 */
@Service
public class DefaultModelConfigService implements ModelConfigService {

    /** 模型供应商持久层接口 */
    private final ModelProviderMapper providerMapper;
    /** 模型配置持久层接口 */
    private final ModelConfigMapper configMapper;
    /** 敏感信息（如 API Key）加密器 */
    private final SecretEncryptor secretEncryptor;
    /** JSON 解析处理器 */
    private final ObjectMapper objectMapper;

    /**
     * 构造函数注入依赖。
     */
    public DefaultModelConfigService(
            ModelProviderMapper providerMapper,
            ModelConfigMapper configMapper,
            SecretEncryptor secretEncryptor,
            ObjectMapper objectMapper
    ) {
        this.providerMapper = providerMapper;
        this.configMapper = configMapper;
        this.secretEncryptor = secretEncryptor;
        this.objectMapper = objectMapper;
    }

    /**
     * 创建模型供应商。
     *
     * @param command 创建供应商命令，包含 API Key 等敏感信息
     * @return 创建后的供应商 DTO
     */
    @Override
    @Transactional
    public ModelProviderDTO createProvider(CreateModelProviderCommand command) {
        // 1. 构造供应商实体
        ModelProviderEntity provider = new ModelProviderEntity();
        // 2. 规范化并设置名称
        provider.setName(ModelValidation.normalizeRequired(command.name(), "name"));
        // 3. 设置供应商类型（如 OPENAI）
        provider.setProviderType(ModelValidation.normalizeRequired(command.providerType(), "providerType"));
        // 4. 设置 API 基础地址
        provider.setBaseUrl(ModelValidation.normalizeRequired(command.baseUrl(), "baseUrl"));
        // 5. 对 API Key 进行加密处理后再存储，确保数据库安全
        provider.setApiKeyEncrypted(secretEncryptor.encrypt(command.apiKey()));
        // 6. 设置初始状态为激活
        provider.setStatus(ModelConstants.STATUS_ACTIVE);
        // 7. 插入数据库
        providerMapper.insert(provider);
        // 8. 转换并返回 DTO
        return toProviderDTO(provider);
    }

    @Override
    public List<ModelProviderDTO> listActiveProviders() {
        return providerMapper.selectList(new LambdaQueryWrapper<ModelProviderEntity>()
                        .eq(ModelProviderEntity::getStatus, ModelConstants.STATUS_ACTIVE)
                        .orderByDesc(ModelProviderEntity::getCreatedAt))
                .stream()
                .map(this::toProviderDTO)
                .toList();
    }

    /**
     * 在指定供应商下创建模型配置。
     *
     * @param command 模型配置命令，包含模型名称、参数等
     * @return 创建后的模型配置 DTO
     */
    @Override
    @Transactional
    public ModelConfigDTO createModelConfig(CreateModelConfigCommand command) {
        // 1. 校验供应商 ID 不能为空
        Long providerId = ModelValidation.requireNonNull(command.providerId(), "providerId");
        // 2. 检查供应商是否存在且处于激活状态
        ModelProviderEntity provider = providerMapper.selectById(providerId);
        if (provider == null || !ModelConstants.STATUS_ACTIVE.equals(provider.getStatus())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Model provider is unavailable");
        }

        // 3. 构造模型配置实体
        ModelConfigEntity config = new ModelConfigEntity();
        config.setProviderId(providerId); // 关联供应商
        // 4. 设置模型技术名称（如 gpt-4）
        config.setModelName(ModelValidation.normalizeRequired(command.modelName(), "modelName"));
        // 5. 设置界面显示名称
        config.setDisplayName(ModelValidation.normalizeRequired(command.displayName(), "displayName"));
        // 6. 解析并设置模型能力（从 JSON 字符串转为 JsonNode）
        config.setCapabilities(parseCapabilities(command.capabilitiesJson()));
        // 7. 设置默认生成温度，如果未提供则默认为 0.7
        config.setDefaultTemperature(command.defaultTemperature() == null
                ? new BigDecimal("0.70")
                : command.defaultTemperature());
        // 8. 设置最大上下文 Token 数
        config.setMaxContextTokens(ModelValidation.requireNonNull(command.maxContextTokens(), "maxContextTokens"));
        // 9. 设置初始状态为激活
        config.setStatus(ModelConstants.STATUS_ACTIVE);
        // 10. 插入数据库
        configMapper.insert(config);
        // 11. 转换并返回 DTO
        return toConfigDTO(config);
    }

    @Override
    public ModelConfigDTO disableModelConfig(Long modelConfigId) {
        ModelConfigEntity config = configMapper.selectById(ModelValidation.requireNonNull(modelConfigId, "modelConfigId"));
        if (config == null) throw new BizException(ErrorCode.REQUEST_INVALID, "Model config not found");
        config.setStatus(ModelConstants.STATUS_DISABLED);
        configMapper.updateById(config);
        return toConfigDTO(config);
    }

    /**
     * 获取所有处于激活状态的模型配置列表。
     */
    @Override
    public ModelConfigDTO getActiveModelConfig(Long modelConfigId) {
        Long id = ModelValidation.requireNonNull(modelConfigId, "modelConfigId");
        ModelConfigEntity config = configMapper.selectById(id);
        if (config == null || !ModelConstants.STATUS_ACTIVE.equals(config.getStatus())) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Model config is unavailable");
        }
        return toConfigDTO(config);
    }

    @Override
    public List<ModelConfigDTO> listActiveModelConfigs() {
        // 查询所有状态为 ACTIVE 的模型，按创建时间倒序排列
        return configMapper.selectList(new LambdaQueryWrapper<ModelConfigEntity>()
                        .eq(ModelConfigEntity::getStatus, ModelConstants.STATUS_ACTIVE)
                        .orderByDesc(ModelConfigEntity::getCreatedAt))
                .stream()
                .map(this::toConfigDTO)
                .toList();
    }

    /**
     * 将 JSON 字符串解析为 JsonNode 对象。
     * 用于处理模型能力字段（Capabilities）。
     */
    private JsonNode parseCapabilities(String capabilitiesJson) {
        // 如果为空则使用默认的空 JSON 对象
        String json = capabilitiesJson == null || capabilitiesJson.isBlank() ? "{}" : capabilitiesJson;
        try {
            // 使用 Jackson 解析 JSON
            return objectMapper.readTree(json);
        } catch (JsonProcessingException e) {
            // 解析失败抛出业务异常
            throw new BizException(ErrorCode.REQUEST_INVALID, "capabilitiesJson must be valid JSON");
        }
    }

    /**
     * 供应商实体转 DTO。
     */
    private ModelProviderDTO toProviderDTO(ModelProviderEntity entity) {
        return new ModelProviderDTO(
                entity.getId(),
                entity.getName(),
                entity.getProviderType(),
                entity.getBaseUrl(),
                entity.getStatus()
        );
    }

    /**
     * 模型配置实体转 DTO。
     */
    private ModelConfigDTO toConfigDTO(ModelConfigEntity entity) {
        return new ModelConfigDTO(
                entity.getId(),
                entity.getProviderId(),
                entity.getModelName(),
                entity.getDisplayName(),
                entity.getCapabilities(),
                entity.getDefaultTemperature(),
                entity.getMaxContextTokens(),
                entity.getStatus()
        );
    }
}
