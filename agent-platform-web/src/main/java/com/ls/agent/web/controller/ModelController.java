package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.core.model.api.ModelConfigService;
import com.ls.agent.core.model.command.CreateModelConfigCommand;
import com.ls.agent.core.model.command.CreateModelProviderCommand;
import com.ls.agent.core.model.dto.ModelConfigDTO;
import com.ls.agent.core.model.dto.ModelProviderDTO;
import com.ls.agent.web.dto.CreateModelConfigRequest;
import com.ls.agent.web.dto.CreateModelProviderRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 模型管理控制器。
 * 包含模型供应商管理（管理员）和模型配置查询等接口。
 */
@RestController
public class ModelController {

    /** 模型配置业务服务 */
    private final ModelConfigService modelConfigService;

    /**
     * 构造函数注入模型配置服务。
     */
    public ModelController(ModelConfigService modelConfigService) {
        // 初始化服务实例
        this.modelConfigService = modelConfigService;
    }

    /**
     * [管理员接口] 创建模型供应商。
     *
     * @param request 供应商创建请求，包含名称、类型、API 地址和密钥
     * @return 包含创建成功的供应商信息
     */
    @PostMapping("/api/admin/model-providers")
    public ApiResponse<ModelProviderDTO> createProvider(@Valid @RequestBody CreateModelProviderRequest request) {
        // 调用业务层创建模型供应商（如 OpenAI, Anthropic）
        return ApiResponse.success(modelConfigService.createProvider(new CreateModelProviderCommand(
                request.name(),         // 供应商名称
                request.providerType(), // 供应商类型
                request.baseUrl(),      // API 基础 URL
                request.apiKey()        // API 密钥（后台会加密存储）
        )));
    }

    @GetMapping("/api/admin/model-providers")
    public ApiResponse<List<ModelProviderDTO>> listProviders() {
        return ApiResponse.success(modelConfigService.listActiveProviders());
    }

    /**
     * [管理员接口] 创建模型配置。
     *
     * @param request 模型配置请求，包含模型代号、能力 JSON、上下文长度等
     * @return 包含创建成功的模型配置信息
     */
    @PostMapping("/api/admin/model-configs")
    public ApiResponse<ModelConfigDTO> createModelConfig(@Valid @RequestBody CreateModelConfigRequest request) {
        // 调用业务层在指定供应商下创建模型配置（如 gpt-4o, claude-3-5-sonnet）
        return ApiResponse.success(modelConfigService.createModelConfig(new CreateModelConfigCommand(
                request.providerId(),       // 关联的供应商 ID
                request.modelName(),        // 模型技术标识
                request.displayName(),      // 界面显示名称
                request.capabilitiesJson(), // 模型能力 JSON (是否支持 Tool Use 等)
                request.defaultTemperature(), // 默认生成随机度
                request.maxContextTokens()  // 最大上下文 Token 数
        )));
    }

    /**
     * 查询所有可用的模型配置列表。
     *
     * @return 激活状态的模型配置列表
     */
    @GetMapping("/api/model-configs")
    public ApiResponse<List<ModelConfigDTO>> listModelConfigs() {
        // 调用业务层获取当前系统中所有可供 Agent 绑定的激活模型
        return ApiResponse.success(modelConfigService.listActiveModelConfigs());
    }
}
