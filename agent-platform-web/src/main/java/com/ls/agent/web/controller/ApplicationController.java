package com.ls.agent.web.controller;

import com.ls.agent.common.response.ApiResponse;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.identity.command.CreateApplicationCommand;
import com.ls.agent.core.identity.dto.ApiKeyDTO;
import com.ls.agent.core.identity.dto.ApplicationDTO;
import com.ls.agent.core.identity.dto.CreateApplicationResult;
import com.ls.agent.core.identity.dto.RevokeApiKeyResult;
import com.ls.agent.web.dto.CreateApplicationRequest;
import com.ls.agent.web.security.CurrentUser;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 应用管理控制器。
 * 提供应用的创建、列表查询以及 API 密钥管理等接口。
 */
@RestController
@RequestMapping("/api/applications")
public class ApplicationController {

    /**
     * 应用业务服务。
     */
    private final ApplicationService applicationService;

    /**
     * 构造函数注入应用服务。
     *
     * @param applicationService 应用服务实例
     */
    public ApplicationController(ApplicationService applicationService) {
        // 将注入的服务实例赋值给成员变量
        this.applicationService = applicationService;
    }

    /**
     * 创建新应用。
     *
     * @param currentUser 当前登录用户上下文
     * @param request 创建应用请求 DTO
     * @return 包含创建结果的通用响应体
     */
    @PostMapping
    public ApiResponse<CreateApplicationResult> create(
            CurrentUser currentUser,
            @Valid @RequestBody CreateApplicationRequest request
    ) {
        // 调用业务层创建应用，将 DTO 转换为 Command 对象，包含租户 ID 和所有者 ID
        CreateApplicationResult result = applicationService.createApplication(new CreateApplicationCommand(
                currentUser.tenantId(), // 从当前用户上下文中获取租户 ID
                currentUser.userId(),   // 从当前用户上下文中获取用户 ID
                request.name(),         // 获取应用名称
                request.description()   // 获取应用描述
        ));
        // 返回成功响应，包含创建的应用结果
        return ApiResponse.success(result);
    }

    /**
     * 分页查询当前用户的应用列表。
     *
     * @param currentUser 当前登录用户上下文
     * @param pageNo 页码，默认第 1 页
     * @param pageSize 每页条数，默认 20 条
     * @return 分页应用数据的通用响应体
     */
    @GetMapping
    public ApiResponse<PageResult<ApplicationDTO>> list(
            CurrentUser currentUser,
            @RequestParam(name = "pageNo", defaultValue = "1") int pageNo,
            @RequestParam(name = "pageSize", defaultValue = "20") int pageSize
    ) {
        // 调用业务层进行分页查询
        return ApiResponse.success(applicationService.pageApplications(
                currentUser.tenantId(),       // 租户过滤
                currentUser.userId(),         // 用户过滤
                pageNo,                       // 当前页码
                Math.min(pageSize, 100)       // 限制最大每页条数为 100，防止性能问题
        ));
    }

    /**
     * 获取指定应用的 API 密钥列表。
     *
     * @param currentUser 当前登录用户上下文
     * @param applicationId 应用 ID
     * @return API 密钥列表的通用响应体
     */
    @GetMapping("/{applicationId}/api-keys")
    public ApiResponse<List<ApiKeyDTO>> listApiKeys(
            CurrentUser currentUser,
            @PathVariable("applicationId") Long applicationId
    ) {
        // 调用业务层获取 API 密钥列表
        return ApiResponse.success(applicationService.listApiKeys(
                currentUser.tenantId(),       // 租户校验
                currentUser.userId(),         // 权限校验
                applicationId                 // 目标应用 ID
        ));
    }

    /**
     * 撤销指定的 API 密钥。
     *
     * @param currentUser 当前登录用户上下文
     * @param applicationId 应用 ID
     * @param apiKeyId 密钥 ID
     * @return 包含撤销结果的通用响应体
     */
    @PostMapping("/{applicationId}/api-keys/{apiKeyId}/revoke")
    public ApiResponse<RevokeApiKeyResult> revokeApiKey(
            CurrentUser currentUser,
            @PathVariable("applicationId") Long applicationId,
            @PathVariable("apiKeyId") Long apiKeyId
    ) {
        // 调用业务层执行撤销操作
        return ApiResponse.success(applicationService.revokeApiKey(
                currentUser.tenantId(),       // 租户校验
                currentUser.userId(),         // 权限校验
                applicationId,                // 目标应用 ID
                apiKeyId                      // 目标密钥 ID
        ));
    }
}
