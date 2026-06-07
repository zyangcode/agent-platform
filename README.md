# Agent Platform

## 快速启动（Docker Compose）

```bash
# 1. 配置环境变量
cp .env.example .env
# 编辑 .env 填入 JWT_SECRET、SILICONFLOW_API_KEY 等

# 2. 构建
mvn package -DskipTests
npm --prefix agent-platform-frontend run build
docker compose build

# 3. 启动
docker compose up -d

# 4. 访问
open http://localhost
```

## 服务端口

| 服务 | 端口 | 说明 |
|------|------|------|
| Frontend (Nginx) | 80 | 浏览器入口 |
| Web | 8080 | 后端 API + JWT 鉴权 |
| Gateway | 8081 | AI 调用治理 |
| PostgreSQL | 15432 | 数据库 |
| Qdrant | 6333/6334 | 向量库 |

## 开发环境

```bash
# 启动基础设施
docker compose -f docker-compose.dev.yml up -d

# IDEA 启动 Web (8080) 和 Gateway (8081)
# 前端
npm --prefix agent-platform-frontend run dev

# 访问
open http://127.0.0.1:5173
```
