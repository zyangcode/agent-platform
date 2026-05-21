# MyBatis-Plus 调用链路 — 用户注册为例

> 从 HTTP 请求到底层数据库，全程只写一个空的 Mapper 接口，MyBatis-Plus 自动搞定所有 SQL。

---

## 1. 完整调用链一览

```text
浏览器 POST /api/auth/register  {"username":"zhangsan","password":"123456"}
  │
  ▼
┌─ Web 模块 ─────────────────────────────────────────────────────┐
│  RegisterController.register()                                  │
│    ├─ @Validated 校验 RegisterRequest DTO                       │
│    ├─ 转换 RegisterRequest → RegisterCommand                    │
│    └─ 调用 AuthService.register(command)  ← 这是 core.xxx.api 接口 │
└────────────────────────────────────────────────────────────────┘
  │
  ▼
┌─ Core 模块 ─────────────────────────────────────────────────────┐
│  AuthServiceImpl.register(command)   @Transactional              │
│    ├─ ① userMapper.selectCount(...)   检查用户名是否已存在       │
│    ├─ ② passwordEncoder.encode(...)   密码 bcrypt 加密           │
│    ├─ ③ userMapper.insert(userEntity)  保存用户 → 自动回填 id   │
│    └─ ④ userRoleMapper.insert(...)     绑定默认 USER 角色        │
└────────────────────────────────────────────────────────────────┘
  │
  ▼
┌─ MyBatis-Plus ──────────────────────────────────────────────────┐
│  UserMapper extends BaseMapper<UserEntity>   ← 你的代码只有这一行│
│    ├─ selectCount → SELECT COUNT(*) FROM users WHERE ...        │
│    ├─ insert     → INSERT INTO users (...) VALUES (...)          │
│    └─ insert     → INSERT INTO user_roles (...) VALUES (...)     │
└────────────────────────────────────────────────────────────────┘
  │
  ▼
┌─ PostgreSQL ────────────────────────────────────────────────────┐
│  users 表、user_roles 表                                         │
└────────────────────────────────────────────────────────────────┘
```

---

## 2. 要写哪些类

| 层 | 类 | 位置 | 说明 |
|----|-----|------|------|
| Web | `RegisterRequest` | `web.dto` | 前端入参 DTO |
| Web | `RegisterResponse` | `web.dto` | 前端出参 DTO |
| Web | `RegisterController` | `web.auth` | HTTP 入口，调 Core 接口 |
| Core | `RegisterCommand` | `core.identity.command` | Core 用例入参 |
| Core | `RegisterResult` | `core.identity.dto` | Core 用例出参 |
| Core | `AuthService` | `core.identity.api` | 接口定义 |
| Core | `AuthServiceImpl` | `core.identity.application` | 接口实现，@Service |
| Core | `UserEntity` | `core.identity.entity` | 数据库实体 |
| Core | `UserMapper` | `core.identity.mapper` | 继承 BaseMapper，**里面是空的** |

---

## 3. 哪些是 MyBatis-Plus 帮你自动生成的

| 方法 | 自动生成的 SQL | 你需要写的代码 |
|------|---------------|---------------|
| `userMapper.selectCount(wrapper)` | `SELECT COUNT(*) FROM users WHERE tenant_id=? AND username=?` | Lambda 条件：`.eq(UserEntity::getUsername, name)` |
| `userMapper.insert(user)` | `INSERT INTO users (...) VALUES (...)` | `userMapper.insert(user)` —— 就这一行 |
| `userMapper.selectById(id)` | `SELECT * FROM users WHERE id=?` | `userMapper.selectById(id)` |
| `userMapper.updateById(user)` | `UPDATE users SET ... WHERE id=?` | `userMapper.updateById(user)` |
| `userMapper.selectPage(page, wrapper)` | `SELECT * FROM users WHERE ... LIMIT ? OFFSET ?` | Lambda 条件 + Page 对象 |

---

## 4. 关键规则

```text
1. Controller 只调 core.xxx.api 接口，不碰 Mapper
2. Mapper 放在表归属包（users → core.identity.mapper）
3. @Transactional 只在 Application Service 上
4. Request/Command/Entity 三种对象不混用
5. LambdaQueryWrapper 代替 SQL 字符串：.eq(Entity::getField, value)
```
