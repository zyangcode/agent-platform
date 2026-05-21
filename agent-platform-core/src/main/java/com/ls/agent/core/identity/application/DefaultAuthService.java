package com.ls.agent.core.identity.application;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ls.agent.common.error.BizException;
import com.ls.agent.common.error.ErrorCode;
import com.ls.agent.core.identity.api.AuthService;
import com.ls.agent.core.identity.api.PasswordHasher;
import com.ls.agent.core.identity.command.LoginCommand;
import com.ls.agent.core.identity.command.RegisterCommand;
import com.ls.agent.core.identity.dto.CurrentUserDTO;
import com.ls.agent.core.identity.dto.LoginResult;
import com.ls.agent.core.identity.dto.RegisterResult;
import com.ls.agent.core.identity.entity.RoleEntity;
import com.ls.agent.core.identity.entity.UserEntity;
import com.ls.agent.core.identity.entity.UserRoleEntity;
import com.ls.agent.core.identity.mapper.RoleMapper;
import com.ls.agent.core.identity.mapper.UserMapper;
import com.ls.agent.core.identity.mapper.UserRoleMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class DefaultAuthService implements AuthService {

    private final UserMapper userMapper;
    private final RoleMapper roleMapper;
    private final UserRoleMapper userRoleMapper;
    private final PasswordHasher passwordHasher;

    public DefaultAuthService(
            UserMapper userMapper,
            RoleMapper roleMapper,
            UserRoleMapper userRoleMapper,
            PasswordHasher passwordHasher
    ) {
        this.userMapper = userMapper;
        this.roleMapper = roleMapper;
        this.userRoleMapper = userRoleMapper;
        this.passwordHasher = passwordHasher;
    }

    /**
     * 用户注册业务实现。
     * <p>
     * 流程包括：
     * 1. 规范化并校验输入参数（用户名、密码、昵称）
     * 2. 检查同租户下用户名是否已存在
     * 3. 对密码进行哈希加密
     * 4. 持久化用户信息到数据库
     * 5. 为新用户分配默认的 "USER" 角色
     * 6. 返回注册结果信息
     *
     * @param command 注册命令，包含用户名、密码和显示名称
     * @return 注册成功的用户信息结果
     * @throws BizException 如果用户名已存在或默认角色配置丢失
     */
    @Override
    @Transactional
    public RegisterResult register(RegisterCommand command) {
        // 1. 规范化并校验用户名（去除空格，确保不为空）
        String username = IdentityValidation.normalizeRequired(command.username(), "username");
        // 2. 规范化并校验密码
        String password = IdentityValidation.normalizeRequired(command.password(), "password");
        // 3. 规范化并校验显示名称
        String displayName = IdentityValidation.normalizeRequired(command.displayName(), "displayName");

        // 4. 查询当前租户下是否存在同名用户
        Long existing = userMapper.selectCount(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getTenantId, IdentityConstants.DEFAULT_TENANT_ID)
                .eq(UserEntity::getUsername, username));
        // 5. 如果用户已存在，抛出业务异常
        if (existing > 0) {
            throw new BizException(ErrorCode.REQUEST_INVALID, "Username already exists");
        }

        // 6. 创建新的用户实体对象
        UserEntity user = new UserEntity();
        // 7. 设置租户 ID（默认为系统默认租户）
        user.setTenantId(IdentityConstants.DEFAULT_TENANT_ID);
        // 8. 设置用户名
        user.setUsername(username);
        // 9. 使用加密器对明文密码进行哈希加密存储
        user.setPasswordHash(passwordHasher.hash(password));
        // 10. 设置用户显示名称
        user.setDisplayName(displayName);
        // 11. 设置用户初始状态为激活
        user.setStatus(IdentityConstants.STATUS_ACTIVE);
        // 12. 执行数据库插入操作，保存用户信息
        userMapper.insert(user);

        // 13. 获取系统预定义的普通用户角色信息
        RoleEntity userRole = roleMapper.selectOne(new LambdaQueryWrapper<RoleEntity>()
                .eq(RoleEntity::getCode, IdentityConstants.ROLE_USER));
        // 14. 如果系统中没有配置该角色，抛出系统内部错误
        if (userRole == null) {
            throw new BizException(ErrorCode.INTERNAL_ERROR, "Default USER role does not exist");
        }

        // 15. 创建用户与角色的关联关系对象
        UserRoleEntity binding = new UserRoleEntity();
        // 16. 设置关联的用户 ID（插入后会自动生成 ID）
        binding.setUserId(user.getId());
        // 17. 设置关联的角色 ID
        binding.setRoleId(userRole.getId());
        // 18. 执行数据库插入操作，绑定用户角色
        userRoleMapper.insert(binding);

        // 19. 构造并返回注册结果 DTO
        return new RegisterResult(user.getId(), user.getTenantId(), user.getUsername(), user.getDisplayName());
    }

    /**
     * 用户登录业务实现。
     * <p>
     * 验证逻辑：
     * 1. 校验用户名和密码是否为空。
     * 2. 根据用户名查询处于激活状态的用户实体。
     * 3. 校验用户输入的明文密码与数据库中的哈希密码是否匹配。
     *
     * @param command 登录命令，包含用户名和密码
     * @return 登录结果，包含当前用户信息
     * @throws BizException 如果用户不存在、状态非激活或密码错误，抛出认证失败异常
     */
    @Override
    public LoginResult login(LoginCommand command) {
        // 1. 校验并规范化输入的用户名
        String username = IdentityValidation.normalizeRequired(command.username(), "username");
        // 2. 校验并规范化输入的密码
        String password = IdentityValidation.normalizeRequired(command.password(), "password");

        // 3. 在默认租户下根据用户名查找用户
        UserEntity user = userMapper.selectOne(new LambdaQueryWrapper<UserEntity>()
                .eq(UserEntity::getTenantId, IdentityConstants.DEFAULT_TENANT_ID)
                .eq(UserEntity::getUsername, username));

        // 4. 账号校验：如果用户不存在，或者用户状态不是“激活”状态，则抛出 401 未授权异常
        if (user == null || !IdentityConstants.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        // 5. 密码校验：将输入的明文密码与数据库存储的哈希值进行比对
        if (!passwordHasher.matches(password, user.getPasswordHash())) {
            // 如果比对失败，抛出 401 未授权异常
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }

        // 6. 登录成功，将实体转换为 DTO 并封装进 LoginResult 返回
        return new LoginResult(toCurrentUser(user));
    }

    @Override
    public CurrentUserDTO getCurrentUser(Long userId) {
        UserEntity user = userMapper.selectById(userId);
        if (user == null || !IdentityConstants.STATUS_ACTIVE.equals(user.getStatus())) {
            throw new BizException(ErrorCode.AUTH_UNAUTHORIZED);
        }
        return toCurrentUser(user);
    }

    private CurrentUserDTO toCurrentUser(UserEntity user) {
        List<Long> roleIds = userRoleMapper.selectList(new LambdaQueryWrapper<UserRoleEntity>()
                        .eq(UserRoleEntity::getUserId, user.getId()))
                .stream()
                .map(UserRoleEntity::getRoleId)
                .toList();
        List<String> roles = roleIds.isEmpty()
                ? List.of()
                : roleMapper.selectList(new LambdaQueryWrapper<RoleEntity>()
                        .in(RoleEntity::getId, roleIds))
                .stream()
                .map(RoleEntity::getCode)
                .toList();
        return new CurrentUserDTO(user.getId(), user.getTenantId(), user.getUsername(), user.getDisplayName(), roles);
    }
}
