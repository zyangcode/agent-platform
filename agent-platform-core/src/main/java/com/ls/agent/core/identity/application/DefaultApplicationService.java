package com.ls.agent.core.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.common.response.PageResult;
import com.ls.agent.core.identity.api.ApiKeyGenerator;
import com.ls.agent.core.identity.api.ApplicationService;
import com.ls.agent.core.identity.api.PasswordHasher;
import com.ls.agent.core.identity.command.CreateApplicationCommand;
import com.ls.agent.core.identity.command.UpdateApplicationCommand;
import com.ls.agent.core.identity.dto.ApiKeyDTO;
import com.ls.agent.core.identity.dto.ApplicationDTO;
import com.ls.agent.core.identity.dto.CreateApplicationResult;
import com.ls.agent.core.identity.dto.CreatedApiKeyDTO;
import com.ls.agent.core.identity.dto.RevokeApiKeyResult;
import com.ls.agent.core.identity.entity.ApiKeyEntity;
import com.ls.agent.core.identity.entity.ApplicationEntity;
import com.ls.agent.core.identity.mapper.ApiKeyMapper;
import com.ls.agent.core.identity.mapper.ApplicationMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 应用管理服务的默认实现。
 * 处理应用的创建、分页查询、API 密钥的生成与撤销等业务逻辑。
 */
@Service
public class DefaultApplicationService implements ApplicationService {

    /** 应用持久层接口 */
    private final ApplicationMapper applicationMapper;
    /** API 密钥持久层接口 */
    private final ApiKeyMapper apiKeyMapper;
    /** API 密钥生成器 */
    private final ApiKeyGenerator apiKeyGenerator;
    /** 密码/密钥加密处理器 */
    private final PasswordHasher passwordHasher;

    /**
     * 构造函数注入依赖。
     */
    public DefaultApplicationService(
            ApplicationMapper applicationMapper,
            ApiKeyMapper apiKeyMapper,
            ApiKeyGenerator apiKeyGenerator,
            PasswordHasher passwordHasher
    ) {
        this.applicationMapper = applicationMapper;
        this.apiKeyMapper = apiKeyMapper;
        this.apiKeyGenerator = apiKeyGenerator;
        this.passwordHasher = passwordHasher;
    }

    /**
     * 创建新应用。
     * 在同一个事务中完成应用信息的保存和默认 API 密钥的生成。
     *
     * @param command 创建应用命令
     * @return 包含应用 ID 和初始 API 密钥的结果
     */
    @Override
    @Transactional
    public CreateApplicationResult createApplication(CreateApplicationCommand command) {
        // 1. 规范化应用名称（必填校验）
        String name = IdentityValidation.normalizeRequired(command.name(), "name");

        // 2. 构造并保存应用实体
        ApplicationEntity application = new ApplicationEntity();
        application.setTenantId(command.tenantId()); // 设置租户 ID
        application.setOwnerUserId(command.ownerUserId()); // 设置所有者 ID
        application.setName(name); // 设置应用名称
        application.setDescription(command.description()); // 设置描述
        application.setStatus(IdentityConstants.STATUS_ACTIVE); // 初始状态设为激活
        applicationMapper.insert(application); // 插入数据库

        // 3. 为新应用生成一个默认的 API 密钥
        String plainApiKey = apiKeyGenerator.generate(); // 生成明文密钥
        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.setTenantId(command.tenantId()); // 设置租户 ID
        apiKey.setApplicationId(application.getId()); // 关联刚创建的应用
        apiKey.setName("default"); // 默认名称为 "default"
        apiKey.setKeyPrefix(apiKeyGenerator.prefixOf(plainApiKey)); // 提取密钥前缀（用于显示）
        apiKey.setKeyHash(passwordHasher.hash(plainApiKey)); // 对密钥全文进行哈希加密存储
        apiKey.setStatus(IdentityConstants.STATUS_ACTIVE); // 设置密钥状态为激活
        apiKeyMapper.insert(apiKey); // 插入数据库

        // 4. 封装创建结果（注意：只有这里会返回明文密钥，后续将无法再次获取）
        CreatedApiKeyDTO createdApiKey = new CreatedApiKeyDTO(
                apiKey.getId(),
                apiKey.getName(),
                plainApiKey,
                apiKey.getKeyPrefix(),
                apiKey.getStatus()
        );
        return new CreateApplicationResult(application.getId(), application.getName(), application.getStatus(), createdApiKey);
    }

    /**
     * 分页查询应用列表。
     *
     * @param tenantId 租户 ID
     * @param ownerUserId 所有者 ID
     * @param pageNo 当前页码
     * @param pageSize 每页条数
     * @return 分页结果
     */
    @Override
    public PageResult<ApplicationDTO> pageApplications(Long tenantId, Long ownerUserId, int pageNo, int pageSize) {
        // 1. 构建分页查询条件：按租户和所有者过滤，按创建时间倒序排列
        Page<ApplicationEntity> page = applicationMapper.selectPage(
                Page.of(pageNo, pageSize),
                new LambdaQueryWrapper<ApplicationEntity>()
                        .eq(ApplicationEntity::getTenantId, tenantId)
                        .eq(ApplicationEntity::getOwnerUserId, ownerUserId)
                        .orderByDesc(ApplicationEntity::getCreatedAt)
        );
        // 2. 将实体列表转换为 DTO 列表
        List<ApplicationDTO> records = page.getRecords().stream()
                .map(this::toApplicationDTO)
                .toList();
        // 3. 封装并返回分页结果
        return PageResult.of(records, pageNo, pageSize, page.getTotal());
    }

    @Override
    @Transactional
    public ApplicationDTO updateApplication(UpdateApplicationCommand command) {
        ApplicationEntity application = getOwnedApplication(command.tenantId(), command.ownerUserId(), command.applicationId());
        application.setName(IdentityValidation.normalizeRequired(command.name(), "name"));
        application.setDescription(command.description());
        applicationMapper.updateById(application);
        return toApplicationDTO(application);
    }

    @Override
    @Transactional
    public ApplicationDTO disableApplication(Long tenantId, Long ownerUserId, Long applicationId) {
        ApplicationEntity application = getOwnedApplication(tenantId, ownerUserId, applicationId);
        application.setStatus(IdentityConstants.STATUS_DISABLED);
        applicationMapper.updateById(application);
        return toApplicationDTO(application);
    }

    @Override
    @Transactional
    public ApplicationDTO enableApplication(Long tenantId, Long ownerUserId, Long applicationId) {
        ApplicationEntity application = getOwnedApplication(tenantId, ownerUserId, applicationId);
        application.setStatus(IdentityConstants.STATUS_ACTIVE);
        applicationMapper.updateById(application);
        return toApplicationDTO(application);
    }

    @Override
    @Transactional
    public CreatedApiKeyDTO regenerateApiKey(Long tenantId, Long ownerUserId, Long applicationId) {
        ApplicationEntity application = getOwnedApplication(tenantId, ownerUserId, applicationId);
        // Revoke existing keys
        List<ApiKeyEntity> existing = apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getApplicationId, applicationId)
                .eq(ApiKeyEntity::getStatus, IdentityConstants.STATUS_ACTIVE));
        for (ApiKeyEntity key : existing) {
            key.setStatus(IdentityConstants.STATUS_REVOKED);
            apiKeyMapper.updateById(key);
        }
        // Generate new key
        String plainApiKey = apiKeyGenerator.generate();
        ApiKeyEntity apiKey = new ApiKeyEntity();
        apiKey.setTenantId(application.getTenantId());
        apiKey.setApplicationId(applicationId);
        apiKey.setName("default");
        apiKey.setKeyPrefix(apiKeyGenerator.prefixOf(plainApiKey));
        apiKey.setKeyHash(passwordHasher.hash(plainApiKey));
        apiKey.setStatus(IdentityConstants.STATUS_ACTIVE);
        apiKeyMapper.insert(apiKey);
        return new CreatedApiKeyDTO(apiKey.getId(), apiKey.getName(), plainApiKey,
                apiKey.getKeyPrefix(), apiKey.getStatus());
    }

    /**
     * 获取应用下的 API 密钥列表。
     *
     * @param tenantId 租户 ID
     * @param ownerUserId 所有者 ID
     * @param applicationId 应用 ID
     * @return API 密钥列表
     */
    @Override
    public void ensureApplicationOwned(Long tenantId, Long ownerUserId, Long applicationId) {
        getOwnedApplication(tenantId, ownerUserId, applicationId);
    }

    @Override
    public List<ApiKeyDTO> listApiKeys(Long tenantId, Long ownerUserId, Long applicationId) {
        // 1. 权限校验：确保用户拥有该应用且应用状态正常
        ApplicationEntity application = getOwnedApplication(tenantId, ownerUserId, applicationId);
        // 2. 查询该应用下的所有密钥，按创建时间倒序
        return apiKeyMapper.selectList(new LambdaQueryWrapper<ApiKeyEntity>()
                        .eq(ApiKeyEntity::getTenantId, tenantId)
                        .eq(ApiKeyEntity::getApplicationId, application.getId())
                        .orderByDesc(ApiKeyEntity::getCreatedAt))
                .stream()
                .map(this::toApiKeyDTO)
                .toList();
    }

    /**
     * 撤销 API 密钥。
     *
     * @param tenantId 租户 ID
     * @param ownerUserId 所有者 ID
     * @param applicationId 应用 ID
     * @param apiKeyId 密钥 ID
     * @return 撤销结果
     */
    @Override
    @Transactional
    public RevokeApiKeyResult revokeApiKey(Long tenantId, Long ownerUserId, Long applicationId, Long apiKeyId) {
        // 1. 校验应用所有权
        getOwnedApplication(tenantId, ownerUserId, applicationId);
        // 2. 查询目标密钥是否存在
        ApiKeyEntity apiKey = apiKeyMapper.selectOne(new LambdaQueryWrapper<ApiKeyEntity>()
                .eq(ApiKeyEntity::getId, apiKeyId)
                .eq(ApiKeyEntity::getTenantId, tenantId)
                .eq(ApiKeyEntity::getApplicationId, applicationId));
        if (apiKey == null) {
            // 密钥不存在抛出异常
            throw new BizException(ErrorCode.API_KEY_INVALID);
        }

        // 3. 更新密钥状态为已撤销，并记录撤销时间
        apiKey.setStatus(IdentityConstants.STATUS_REVOKED);
        apiKey.setRevokedAt(LocalDateTime.now());
        apiKeyMapper.updateById(apiKey);
        // 4. 返回结果
        return new RevokeApiKeyResult(apiKey.getId(), apiKey.getStatus());
    }

    /**
     * 校验并获取用户拥有的应用。
     *
     * @throws BizException 如果应用不存在或已禁用
     */
    private ApplicationEntity getOwnedApplication(Long tenantId, Long ownerUserId, Long applicationId) {
        ApplicationEntity application = applicationMapper.selectOne(new LambdaQueryWrapper<ApplicationEntity>()
                .eq(ApplicationEntity::getId, applicationId)
                .eq(ApplicationEntity::getTenantId, tenantId)
                .eq(ApplicationEntity::getOwnerUserId, ownerUserId));
        if (application == null || !IdentityConstants.STATUS_ACTIVE.equals(application.getStatus())) {
            throw new BizException(ErrorCode.APPLICATION_DISABLED);
        }
        return application;
    }

    /**
     * 将应用实体转换为 DTO。
     */
    private ApplicationDTO toApplicationDTO(ApplicationEntity entity) {
        return new ApplicationDTO(
                entity.getId(),
                entity.getName(),
                entity.getDescription(),
                entity.getStatus(),
                entity.getCreatedAt()
        );
    }

    /**
     * 将密钥实体转换为 DTO。
     */
    private ApiKeyDTO toApiKeyDTO(ApiKeyEntity entity) {
        return new ApiKeyDTO(
                entity.getId(),
                entity.getName(),
                entity.getKeyPrefix(),
                entity.getStatus(),
                entity.getLastUsedAt(),
                entity.getCreatedAt()
        );
    }
}
