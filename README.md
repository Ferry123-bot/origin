# 云枢智学

AI 赋能的智能面试训练平台。学员端完成刷题、错题本、学习路线与 AI 模拟面试；管理端负责学科/题库/课程/路线、学员与权限运营。

核心闭环：**学科 → 阶段 → 题目 / 课程 / 路线**，以及 **刷题 → 错题 → AI 面试 → 报告 → 补强**。

## 技术栈

| 层级 | 技术 |
|---|---|
| 学员端 / 管理端 | Vue 3、TypeScript、Vite、Pinia、Vue Router、Element Plus、ECharts |
| 后端 | Java 17、Spring Boot 3.3、Spring Security + JWT、MyBatis-Plus |
| 数据层 | MySQL 8.0、Redis 7、MinIO（S3 兼容） |
| AI | 服务端 OpenAI 兼容协议调用国内模型；不可用时关键词降级评分 |
| 实时反馈 | SSE（简答批改 / 面试评分进度） |

两端账号体系独立，接口前缀分别为 `/api/portal/v1` 与 `/api/admin/v1`。

## 仓库结构

```text
yszx/
├── portal-web/          # 学员端（开发默认 http://localhost:5173）
├── admin-web/           # 管理端（开发默认 http://localhost:5174/admin/）
├── backend/             # Spring Boot 后端（默认 http://localhost:8080）
├── deploy/              # Docker Compose：MySQL / Redis / MinIO
├── scripts/sql/         # 建表与种子数据（容器首次初始化时挂载）
├── docs/                # 需求、架构、数据库、API 与规范
├── prototype/           # 静态页面原型
└── cursor.md            # 本仓库 Agent 协作约定
```

## 环境要求

- JDK 17+
- Maven 3.9+
- Node.js 20+、pnpm 9+
- Docker / Docker Compose（数据层）

## 本地启动

### 1. 启动数据层

```bash
cd deploy
copy env\.env.example .env
docker compose up -d
```

默认会启动：

| 服务 | 本机端口（可用 `.env` 覆盖） | 说明 |
|---|---|---|
| MySQL | `3306` | 库名 / 用户 `yszx`，密码见 `.env` |
| Redis | `6379` | 需密码 |
| MinIO API / Console | `9000` / `9001` | 桶名默认 `yszx`，已开匿名读 |

`scripts/sql/schema.sql` 仅在 **MySQL 数据卷首次初始化** 时执行。若本机 `3306` 已被占用，可在 `deploy/.env` 把 `MYSQL_PORT` 改成其他端口（例如 `3307`），并与后端 `application-dev.yml` / 环境变量保持一致。

### 2. 配置大模型（可选）

简答批改与 AI 面试评分依赖 OpenAI 兼容接口。复制示例并填写：

```bash
copy backend\config\local.yml.example backend\config\local.yml
```

也可改用环境变量：`AI_BASE_URL`、`AI_MODEL`、`AI_API_KEY`。  
`backend/config/local.yml` 已加入 `.gitignore`，不要提交密钥。未配置或调用失败时，服务会降级为关键词评分。

### 3. 启动后端

```bash
cd backend
mvn -DskipTests spring-boot:run
```

默认端口 `8080`，开发配置读取 `application-dev.yml`（连接本机 Docker 暴露的 MySQL / Redis / MinIO）。

### 4. 启动前端

```bash
# 学员端
pnpm --dir portal-web install
pnpm --dir portal-web dev

# 管理端
pnpm --dir admin-web install
pnpm --dir admin-web dev
```

Vite 已将 `/api`、`/files` 代理到 `http://localhost:8080`。

| 应用 | 地址 |
|---|---|
| 学员端 | http://localhost:5173 |
| 管理端 | http://localhost:5174/admin/ |
| 后端 / Swagger | http://localhost:8080 ，文档路径 `/swagger-ui.html` |

## 默认账号

种子数据中的管理端超级管理员：

- 用户名：`admin`
- 初始密码：`Admin@123456`（上线前务必修改）

学员账号通过学员端注册，或在管理端「学员管理」中维护。两端登录态互不共用。

## 主要功能

### 学员端

- 首页：平台数据、学习统计、热门阶段、AI 面试介绍
- 题库 / 刷题：按学科与阶段筛选，顺序 / 随机练习，简答 SSE 评分
- 错题本：筛选、掌握标记、重做
- 学习路线：按路线顺序学习 B 站课程
- AI 面试：配置方向与题量、会话中评分、报告与历史记录（每日配额）

### 管理端

- 数据看板：概览指标、用户增长、题目分布、最近活动
- 内容：学科与阶段、题库（含审核上下架）、面试题库、B 站课程库、学习路线
- 学员管理、用户 / 角色权限、操作日志、系统基本设置

## 文档

| 文档 | 说明 |
|---|---|
| [docs/云枢智学-需求文档.md](docs/云枢智学-需求文档.md) | 业务需求 |
| [docs/design/01.功能模块梳理.md](docs/design/01.功能模块梳理.md) | 功能模块 |
| [docs/design/02.整体架构设计.md](docs/design/02.整体架构设计.md) | 架构与技术选型 |
| [docs/design/03.数据库设计.md](docs/design/03.数据库设计.md) | 表结构 |
| [docs/design/04.API接口设计.md](docs/design/04.API接口设计.md) | 接口契约 |
| [docs/rules/database-rules.md](docs/rules/database-rules.md) | 数据库规范 |
| [docs/rules/api-rules.md](docs/rules/api-rules.md) | API 规范 |
| [prototype/README.md](prototype/README.md) | 静态原型说明 |

## 构建与类型检查

```bash
# 后端
cd backend && mvn -DskipTests package

# 学员端
pnpm --dir portal-web typecheck
pnpm --dir portal-web build

# 管理端
pnpm --dir admin-web typecheck
pnpm --dir admin-web build
```

管理端生产资源以 `/admin/` 为 base path，需与反向代理路径一致。

## 说明

- 开发默认 profile 为 `dev`；密钥、JWT、数据库口令请勿提交仓库。
- 学员端题目数量仅统计 **已上架**（`status = 1`）题目。
- 原型目录 `prototype/` 可直接用浏览器打开预览交互稿，不依赖后端。
