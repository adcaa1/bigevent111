# BigEvent

<p align="center">
  <strong>基于 Spring Boot + AI 的内容管理与智能问答平台</strong>
</p>

<p align="center">
  <a href="#-技术栈">技术栈</a> •
  <a href="#-功能特性">功能特性</a> •
  <a href="#-快速开始docker-一键启动">快速开始</a> •
  <a href="#-本地开发">本地开发</a> •
  <a href="#-接口文档">接口文档</a> •
  <a href="#-常见问题">常见问题</a>
</p>

---

## 📖 项目简介

BigEvent 是一个面向个人/团队的内容管理与智能问答平台，支持文章发布、知识库文档管理、即时通讯以及基于大语言模型的 AI 助手。项目采用主流的 Spring Boot 生态构建，集成了 LangChain4j 实现 RAG（检索增强生成）和 AI Agent 能力，可对接任何兼容 OpenAI API 规范的大模型服务。

> 项目定位：生产级内容管理 + AI 知识库问答的后端服务，可与任意前端（Vue/React/小程序）对接。

---

## 🚀 技术栈

| 层级 | 技术 |
| --- | --- |
| 后端框架 | Spring Boot 3.2.2、Java 17 |
| 数据持久层 | MyBatis、MySQL 8.0、PageHelper |
| 缓存/向量库 | Redis（redislabs/redisearch，支持向量检索） |
| 消息队列 | RabbitMQ 3（管理界面默认端口 15672） |
| 搜索引擎 | Elasticsearch 8.x |
| AI 框架 | LangChain4j 1.0.1-beta6 |
| 大模型 | 通义千问（qwen3.7-plus / text-embedding-v2），通过阿里云百炼兼容 OpenAI API 调用 |
| 实时通讯 | Spring WebSocket |
| 认证授权 | JWT（java-jwt） |
| 文件存储 | 本地磁盘 + 阿里云 OSS |
| 构建工具 | Maven、Docker、Docker Compose |

---

## ✨ 功能特性

### 1. 用户与权限
- 用户注册/登录，JWT Token 认证
- 部门（Department）管理
- 用户在线状态（Redis String + 60s TTL）

### 2. 文章管理
- 文章的增删改查、分页列表
- 文章分类管理
- 支持草稿/已发布状态

### 3. 广场与社交
- 用户推荐广场（支持随机推荐 / 按发文数排序）
- 关注/取消关注
- 用户粉丝数、关注数、文章数统计（冗余字段 + 定时同步）

### 4. 即时通讯
- WebSocket 长连接实时消息推送
- 单聊消息
- 群聊：创建群、邀请成员、设置管理员、移除成员、修改群信息、退群/解散群
- 消息未读、历史消息查询

### 5. AI 助手
- **通用 AI 聊天**：支持流式输出，Redis 存储对话记忆
- **AI 文章管理 Agent**：自然语言管理文章（添加、查询、更新、删除）
- **AI 图书助手 Agent**：自然语言管理知识库文档/图书（列出、搜索、查看、删除、重新处理、添加文本知识）

### 6. RAG 知识库
- 文档上传：支持 PDF、Word、TXT、Markdown 等格式
- 文档解析与向量化（text-embedding-v2）
- 向量存储：Redisearch（HNSW + COSINE，1536 维）
- 混合检索：向量相似度 + Elasticsearch 关键词搜索，RRF 融合排序
- 知识库问答：基于上传文档进行 RAG 流式问答

---

## 📁 项目结构

```text
bigevent/
├── docker/                         # Docker 相关配置
│   ├── init.sql                    # MySQL 初始化脚本（首次启动自动执行）
│   └── migrate_user_count.sql      # 用户统计字段迁移脚本（可选）
├── src/main/java/com/example/bigevent/
│   ├── config/                     # 配置类（Redis、LangChain4j、WebSocket 等）
│   ├── controller/                 # REST/WebSocket 控制器
│   ├── domain/                     # 实体类
│   ├── mapper/                     # MyBatis Mapper
│   ├── service/                    # 业务逻辑（含 AI Agent、RAG、聊天等）
│   ├── repository/                 # Redis 数据访问
│   ├── util/                       # 工具类
│   └── BigEventApplication.java    # 启动类
├── src/main/resources/
│   ├── application.properties      # 应用配置文件
│   ├── logback-spring.xml          # 日志配置
│   └── static/                     # 静态资源
├── Dockerfile                      # 多阶段构建镜像
├── docker-compose.yml              # 一键启动编排
├── pom.xml                         # Maven 配置
└── README.md                       # 本文件
```

---

## 🐳 快速开始：Docker 一键启动

### 环境要求

- Docker 20.10+
- Docker Compose 2.0+
- 至少 4GB 可用内存（建议 6GB）

### 1. 获取源码

```bash
git clone https://github.com/yourusername/bigevent.git
cd bigevent
```

### 2. 配置 AI API Key

项目需要一个大模型 API Key 才能使用 AI 功能。默认使用阿里云百炼（兼容 OpenAI API），请在启动前设置环境变量：

**Linux / macOS：**

```bash
export OPENAI_API_KEY=your_api_key_here
```

**Windows PowerShell：**

```powershell
$env:OPENAI_API_KEY="your_api_key_here"
```

**Windows CMD：**

```cmd
set OPENAI_API_KEY=your_api_key_here
```

> 如需使用其他兼容 OpenAI API 的模型，修改 `src/main/resources/application.properties` 中的 `langchain4j.open-ai.*` 配置即可。

### 3. 启动全部服务

```bash
docker-compose up -d --build
```

Docker Compose 会自动构建 Spring Boot 应用镜像，并启动以下服务：

| 服务 | 容器名 | 端口 | 说明 |
| --- | --- | --- | --- |
| MySQL | bigevent-mysql | 3306 | 数据库，root 密码 `040926` |
| Redis | bigevent-redis | 6379 | 缓存 + 向量库（RediSearch） |
| RabbitMQ | bigevent-rabbitmq | 5672 / 15672 | 消息队列，管理界面 http://localhost:15672 |
| Spring Boot App | bigevent-app | 8080 | 后端 API 服务 |

首次启动会执行 `docker/init.sql` 自动建表。

### 4. 验证启动

```bash
# 查看所有容器状态
docker-compose ps

# 查看应用日志
docker-compose logs -f app

# 测试接口
curl http://localhost:8080/
```

### 5. 停止服务

```bash
docker-compose down
```

如需删除数据卷（清空 MySQL 数据）：

```bash
docker-compose down -v
```

---

## 💻 本地开发

### 环境要求

- JDK 17+
- Maven 3.9+
- MySQL 8.0（端口 3306）
- Redis（**必须**使用 redislabs/redisearch:latest，支持向量检索）
- RabbitMQ 3（端口 5672 / 15672）
- Elasticsearch 8.x（端口 9200，可选，RAG 混合检索需要）

### 1. 创建数据库

```sql
CREATE DATABASE big_event CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
```

### 2. 导入表结构

执行 `docker/init.sql` 中的全部 SQL 语句。

> 注意：`init.sql` 必须使用 UTF-8 编码，UTF-16 会导致初始化失败。

### 3. 启动中间件

如果你本地没有安装 MySQL/Redis/RabbitMQ，可以使用 Docker 只启动中间件：

```bash
docker-compose up -d mysql redis rabbitmq
```

### 4. 配置 application.properties

根据本地环境修改 `src/main/resources/application.properties`：

```properties
# MySQL
spring.datasource.url=jdbc:mysql://localhost:3306/big_event
spring.datasource.username=root
spring.datasource.password=040926

# Redis
spring.data.redis.host=localhost
spring.data.redis.port=6379

# RabbitMQ
spring.rabbitmq.host=localhost
spring.rabbitmq.port=5672
spring.rabbitmq.username=guest
spring.rabbitmq.password=guest

# Elasticsearch（可选）
elasticsearch.host=localhost
elasticsearch.port=9200

# RAG 文件上传路径
rag.document.upload-path=/your/local/path/KnowledgeBase
```

### 5. 设置 API Key 并启动

```bash
# 设置 API Key（示例为阿里云百炼）
export OPENAI_API_KEY=your_api_key_here

# 编译并启动
./mvnw clean compile
./mvnw spring-boot:run
```

Windows：

```powershell
$env:OPENAI_API_KEY="your_api_key_here"
.\mvnw.cmd clean compile
.\mvnw.cmd spring-boot:run
```

启动成功后访问：http://localhost:8080

---

## ⚙️ 核心配置说明

### AI 模型配置

```properties
# 对话模型
langchain4j.open-ai.chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.chat-model.model-name=qwen3.7-plus
langchain4j.open-ai.chat-model.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1

# 流式对话模型
langchain4j.open-ai.streaming-chat-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.streaming-chat-model.model-name=qwen3.7-plus
langchain4j.open-ai.streaming-chat-model.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1

# Embedding 模型
langchain4j.open-ai.embedding-model.api-key=${OPENAI_API_KEY}
langchain4j.open-ai.embedding-model.model-name=text-embedding-v2
langchain4j.open-ai.embedding-model.base-url=https://dashscope.aliyuncs.com/compatible-mode/v1
```

### RAG 检索配置

```properties
rag.search.vector-top-k=20          # 向量检索 TopK
rag.search.keyword-top-k=20         # 关键词检索 TopK
rag.search.vector-weight=1.0        # 向量分数权重
rag.search.keyword-weight=0.7       # 关键词分数权重
rag.search.rrf-k=60                 # RRF 融合参数
rag.search.min-score=0.3            # 向量相似度阈值
```

### 文件上传

```properties
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
rag.document.upload-path=/your/path/KnowledgeBase
```

---

## 📚 接口文档

### 在线接口

后端启动后，接口可通过 Postman 或 Apifox 直接调用。

### 主要 Controller

| Controller | 功能 |
| --- | --- |
| `AgentController` | AI 图书助手、AI 文章管理 Agent |
| `Articlecontroller` | 文章 CRUD、分类 |
| `ChatController` | 通用 AI 聊天、RAG 问答/管理 |
| `ChatMessageController` | WebSocket 聊天消息 |
| `DepartmentController` | 部门管理 |
| `FileUploadController` | 文件上传 |
| `FollowController` | 关注/粉丝 |
| `OnlineStatusController` | 在线状态 |
| `SquareController` | 广场推荐 |
| `Usercontroller` | 用户注册/登录/个人信息 |

### 前端对接文档

项目已整理部分前端对接文档：

- [AI 图书助手前端对接文档](.trae/documents/frontend_book_agent_integration.md)

其他接口可参照 Controller 源码或结合 Swagger（如已集成）使用。

---

## 🔑 默认账号

项目没有内置默认账号，首次使用需要调用注册接口创建用户：

```http
POST /user/register
Content-Type: application/json

{
  "username": "admin",
  "password": "123456",
  "phone": "13800138000"
}
```

然后登录获取 JWT Token：

```http
POST /user/login
Content-Type: application/json

{
  "username": "admin",
  "password": "123456"
}
```

---

## 🗄️ 数据库表概览

| 表名 | 说明 |
| --- | --- |
| `user` | 用户表 |
| `department` | 部门表 |
| `article` | 文章表 |
| `category` | 文章分类表 |
| `follow` | 关注关系表 |
| `chat_group` | 群聊表 |
| `chat_group_member` | 群成员表 |
| `chat_message` | 聊天消息表 |
| `ai_conversation` | AI 会话表 |
| `user_fact` | AI 用户事实/长期记忆表 |
| `knowledge_doc` | 知识库文档/图书表 |
| `knowledge_chunk` | 文档分块向量表 |
| `knowledge_image` | 知识库图片表 |

---

## ⚠️ 常见问题

### 1. Docker 启动时报 `Communications link failure`

说明 Spring Boot 应用启动时 MySQL 还没准备好。`docker-compose.yml` 已配置 `depends_on` + `condition: service_healthy`，一般会自动等待。如果仍失败，可手动重启 app：

```bash
docker-compose restart app
```

### 2. Redis 向量索引创建失败

请确认使用的是 `redislabs/redisearch:latest` 镜像，而不是普通 Redis。普通 Redis 不支持 RediSearch 模块，会导致向量索引创建失败。

### 3. RAG 问答或文档上传不工作

- 检查 `OPENAI_API_KEY` 是否设置
- 检查 `rag.document.upload-path` 路径是否存在且应用有写入权限
- 查看日志中是否有 `KnowledgeDoc` 或 `RedisEmbeddingStore` 相关错误

### 4. AI 文章管理/图书助手调用工具时报错

可能是 LangChain4j 工具调用内部异常。已在 Controller 层增加 30 秒超时兜底，避免前端一直转圈。如遇问题请查看 `logs/error.log`。

### 5. 日志文件在哪里

应用启动后会在项目根目录创建 `logs/` 文件夹：

- `logs/app.log`：INFO 及以上级别日志
- `logs/error.log`：ERROR 级别日志

---

## 🤝 贡献指南

欢迎提交 Issue 和 Pull Request。提交前请确保：

1. 代码可以正常编译：`mvn clean compile`
2. 遵循现有代码风格
3. 关键修改请补充说明

---

## 📄 License

本项目仅供学习交流使用。

---

> 如有问题，欢迎提交 Issue 或联系作者。
