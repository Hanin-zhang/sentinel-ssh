# SSH 代理堡垒机系统

## 项目简介

本项目是一个基于 **Spring Boot + Apache MINA SSHD** 自研的 **SSH 代理堡垒机（SSH Bastion / Jump Server）**。

在传统运维场景中，多台后端 Linux 服务器直接暴露 SSH 端口，存在口令爆破、高危命令误操作、操作无审计等风险。本系统作为用户与后端服务器之间的**统一 SSH 接入层**：用户只连接堡垒机，由堡垒机完成认证、命令审查、全量审计后，再将合法数据转发到真实后端服务器。后端 Linux 服务器通过 docker compose 部署，各自暴露 SSH 端口，由堡垒机按轮询策略接入。

配套提供 Web 管理端（前端单页 `index.html`），实现仪表盘、审计日志、用户管理、统计中心、AI 安全中心等运维可视化能力。

## ✨核心功能

- **SSH 代理转发**：用户 ↔ 堡垒机 ↔ 后端服务器的双向数据转发，交互式 Shell 透传，多台后端服务器轮询分配。
- **RBAC 角色权限控制**：三档角色（`admin` / `ops` / `guest`）命令名白名单，`ops` 覆盖约 180 条运维命令，`guest` 仅 52 条只读诊断命令，`admin` 全量放行。
- **两级命令安全审查**：静态规则引擎（同步，<1ms）→ 高危直接拦截、白名单快速放行、灰区命令放行后异步 DeepSeek AI 语义审查。
- **高危告警与实时推送**：AI 判定 HIGH 时写入高危审计日志、SSE 实时推送管理员，反弹 Shell / 后门类别自动强制断连止损；MEDIUM 静默记录。
- **全量命令审计**：每条命令（含用户、IP、目标服务器、拦截状态）落库 `audit_log`，状态 0/1/2/3 区分来源，支持多条件分页检索。
- **统计分析**：个人/全局统计、近 7 天风险趋势、危险命令用户排行榜（基于真实审计数据）。
- **AI 安全中心**：命令风险分析接口（静态规则 + AI 双重判定）、危险命令排行、每日 0 点由 AI 基于审计数据自动生成安全策略建议。
- **用户管理与邮箱验证码注册**：管理员验权后新增用户；用户可通过邮箱验证码两步注册（角色强制 `guest`），验证码发送带本地限流。
- **在线会话管理**：在线会话池、心跳检测（20 分钟无活动自动回收）、管理员强制踢人、后端服务器 TCP 健康检查。

## 🛠技术栈

| 分类 | 组件 | 版本 / 说明 |
|------|------|-------------|
| 语言 / 框架 | JDK 17 · Spring Boot 3.2.2 · Maven | |
| SSH 代理 | Apache MINA SSHD | 2.16.0（`sshd-core` + `sshd-netty`，Netty IO 后端支撑高并发连接） |
| ORM | MyBatis-Plus 3.5.14 + `mybatis-plus-jsqlparser` | 内置分页插件 `PaginationInnerInterceptor` |
| 数据库 | MySQL 8.x（`mysql-connector-j`） | |
| 本地缓存 | Caffeine | 验证码缓存、发送限流（1 分钟冷却 / 10 分钟 5 次配额 / 5 分钟过期） |
| AI 审查 | DeepSeek API（OpenAI 兼容 `chat/completions`） | 通过 `RestTemplate` 调用，异步语义审查 |
| 邮件 | `spring-boot-starter-mail`（阿里云 SMTP） | 验证码邮件发送 |
| 实时推送 | SSE（`SseEmitter`） | 高危告警实时推送给管理员 |
| 接口文档 | springdoc-openapi 2.3.0 | Swagger UI |
| 异步 | `@Async` + 自定义线程池 | 未引入消息队列，异步通过独立线程池实现 |
| 预留未启用 | `spring-boot-starter-websocket` · Web 终端 | 后端处于预留态 |

## 📐系统架构

```
用户 (SSH Client)                     Web 管理端 (index.html)
      │  TCP :52020 (密码认证)                │  HTTP :8080/api
      ▼                                      ▼
┌──────────────────────────────────────────────────────────────┐
│                    SSH 代理堡垒机 (Spring Boot)               │
│  ① 认证: SshServer(52020) → LoginUtil 校验用户名/密码          │
│  ② 接入: ListenManager → 轮询选择后端服务器(BackendManager)    │
│  ③ 转发: ProxyForwarder 双向通道(ioExecutor)                   │
│        └─ 命令审查 CommandReviewService                        │
│             ├─ Phase1 静态规则引擎 (同步 <1ms)                  │
│             ├─ Phase2 DeepSeek AI (异步, alertExecutor)        │
│  ④ 审计: 全量落库 audit_log; 告警 SSE 推送 + 强制断连           │
└──────────────────────────────────────────────────────────────┘
      │  TCP :22 等 (角色账号)
      ▼
┌────────────────────────────────────────┐
│ 后端 Linux 服务器(多台, docker compose 部署)│
│  每台暴露 SSH 端口, 堡垒机轮询接入         │
└────────────────────────────────────────┘

     MySQL(user / sys_role / backend_server / audit_log / dashboard_stat)
     Caffeine 本地缓存(验证码 + 限流)   DeepSeek API(AI 审查)   SSE(告警推送)
```

**命令审查流程（两级，异步不阻塞终端）：**

```
用户输入命令
   │
   ├─ 第一层: 角色命令名白名单 (PermissionUtil, O(1) HashSet)
   │    └─ 不在角色白名单 → 拦截(权限不足)
   │
   ├─ Phase 1: StaticRuleEngine 四层漏斗 (同步, <1ms)
   │    ├─ 命中 BLOCK (37 条)      → 直接拦截, 不回传后端
   │    ├─ 命中 ALLOW (25 条)      → 白名单快速放行
   │    ├─ 命中 SUSPICIOUS (52 条) → 放行 + 触发异步 Phase 2
   │    └─ 默认                     → 放行
   │
   └─ Phase 2: DeepSeek AI 语义审查 (@Async, 1~3s)
        ├─ HIGH   → 高危审计(status=2) + SSE 推送 + 反弹Shell/后门强制断连
        ├─ MEDIUM → 静默记录(status=3)
        ├─ LOW/SAFE → 忽略
        └─ AI 异常 → Fail Open 降级放行
```

## 💡核心亮点与性能优化

**1. 三层纵深防御，静态兜底 + AI 补灰区**
- 第一层：角色命令名白名单（`admin` 全量 / `ops` ≈180 条 / `guest` 52 条），`HashSet` O(1) 查找。
- 第二层：静态规则引擎四层漏斗 `BLOCK(37) → ALLOW(25) → SUSPICIOUS(52) → 默认放行`，覆盖 `rm -rf /`、Fork 炸弹、多语言反弹 Shell、挖矿、SSH 后门、容器逃逸、`LD_PRELOAD` 劫持等攻击载荷。**铁律**：ALLOW 白名单规则一律排除 `> >> < | ; &`，杜绝 `cat > /etc/...` 通过重定向绕过审查。
- 第三层：DeepSeek AI 对灰区命令做语义级二次确认，识别静态正则难以覆盖的混淆变体。

**2. 性能设计：同步极快、异步不阻塞**
- 静态规则全部**预编译**（`static final Pattern`），同步判定耗时 <1ms，命令数据在 IO 线程池内直接复用，零额外网络往返。
- 90%+ 常规运维命令走 ALLOW 快速通道，仅灰区命令触发异步 AI 审查（设计目标占比 <5%），终端交互零感知。
- AI 审查、告警写入与 SSH 数据转发**线程池隔离**（`alertExecutor` 独立 2~8 线程），AI 超时不会拖垮数据通道。
- 完整压测数据待补充：已提供并发压测脚本（`ssh_load_test.py`，10 线程 / 1000 次连接），实际吞吐、延迟指标 `[需要补充真实指标]`。

**3. AI 审查高可用：多道容错 + Fail Open**
- 网络异常 → `AiReviewResult.fallback()` 降级放行；JSON 解析失败 → 提取 JSON 子串重试；仍失败 → 关键词兜底判定。
- 所有异步路径最外层 `try/catch`，AI 不可用不影响 SSH 会话（Fail Open 设计，牺牲审查覆盖换取可用性）。

**4. 验证码防刷：本地缓存限流，零依赖 Redis**
- 基于 Caffeine 实现三层限流：1 分钟发送冷却、10 分钟最多 5 次（`AtomicInteger` + CAS 原子计数）、验证码 5 分钟过期，缓存上限 2 万条自动淘汰；重发即覆盖旧码，杜绝过期码复用。
- 单机场景无需引入 Redis，降低部署复杂度。

**5. 线程池精细化分工（5 组）**
- `listenExecutor(1/1)` 监听新连接 → `workExecutor(8/20)` 认证与连接建立 → `ioExecutor(10/30)` 双向数据转发长任务 → `alertExecutor(2/8)` AI 审查 + 告警 → `recommendExecutor(1/2)` 每日策略生成。队列满时统一 `CallerRunsPolicy` 回退执行，不丢任务。

**6. 实时告警 + 自动止损**
- 高危告警经 SSE（`SseEmitter`，15s 心跳保活）实时推送给在线管理员；AI 判定"反弹 Shell / 后门"类别时异步强制断开会话，将损失窗口压缩到最小。

**7. 全量审计驱动数据与策略闭环**
- 每条命令（含用户、IP、服务器、状态）落库；`status` 0/1/2/3 区分"正常 / 静态拦截 / AI 高危 / AI 中危"，为统计与告警来源分类提供依据。
- 近 7 天风险趋势、危险命令排行榜基于真实审计 SQL 聚合；AI 每日 0 点读取审计统计生成安全策略建议并缓存，前端直接读取缓存，不重复调用 AI。

## 🚀快速部署启动

### 1. 环境要求
- JDK 17+、Maven 3.6+
- MySQL 8.0+
- （可选）DeepSeek API Key，用于 AI 命令审查；无 Key 时系统自动降级放行，静态规则仍生效
- （可选）可用的 SMTP 邮箱账号，用于验证码注册；无邮件服务时验证码功能不可用
- 后端 Linux 服务器：经 docker compose 部署、暴露 SSH 端口的宿主机或容器，用于被代理接入

### 2. 数据库初始化说明
> ⚠️ 当前仓库**未提供 `schema.sql` 初始化脚本**，需手动建库建表。

```sql
CREATE DATABASE IF NOT EXISTS `sshproxy-project` DEFAULT CHARSET utf8mb4 COLLATE utf8mb4_general_ci;
```

需创建 5 张表（字段以 `entity` 包下的实体类为准）：
| 表名 | 用途 | 关键字段 |
|------|------|----------|
| `user` | 用户表 | username(唯一)、password、role(admin/ops/guest)、status、email、public_key、danger_total_num |
| `sys_role` | 角色表 | role_name、role_code(admin/ops/guest)、password(代理连后端的角色账号密码)、status |
| `backend_server` | 后端服务器表 | server_name、host、port、username、password、online、connection_count |
| `audit_log` | 命令审计日志表 | user_id、username、command、status(0/1/2/3)、server_id、client_ip、create_time |
| `dashboard_stat` | 看板汇总表 | id=1 固定一行，total_cmd_num、total_danger_cmd_num |

初始化建议：至少插入 3 条 `sys_role`（admin/ops/guest，并配置各角色连后端服务器的密码）、1 个 admin 用户、1 条以上 `backend_server` 记录（在线状态置 true）。

### 3. 配置文件修改说明
```bash
cp src/main/resources/application-example.yaml src/main/resources/application.yaml
```
修改 `application.yaml`：
```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/sshproxy-project?useSSL=false&serverTimezone=Asia/Shanghai
    username: <数据库账号>
    password: <数据库密码>
  mail:                # 验证码邮件（可选）
    host: smtp.dm.aliyun.com
    username: <邮箱> / password: <邮箱密码>

ssh:
  public-key-path: <SSH 私钥路径>   # 代理自身 HostKey 相关路径
  timeout: 20000

deepseek:             # AI 审查（可选）
  api-key: <DeepSeek Key>
  base-url: https://api.deepseek.com
  model: <模型名>

adminPassword: <新增用户时的管理员验权密码>
```
> ⚠️ `application.yaml` 为本地敏感配置（含真实数据库密码、API Key），**严禁提交到 Git**，已加入 `.gitignore`，多人协作请使用 `application-example.yaml` 占位模板。

### 4. 项目启动步骤
```bash
# 编译打包
mvn clean package -DskipTests

# 启动
java -jar target/SSHProxy-project-0.0.1-SNAPSHOT.jar
```
IDE 中直接运行 `SshProxyProjectApplication` 亦可。启动后：
- **HTTP API / 前端看板**：`http://localhost:8080/api`（前端 `index.html` 置于项目根目录，可用静态服务或直接打开）
- **SSH 代理端口**：`localhost:52020`
- **Swagger 文档**：`http://localhost:8080/api/swagger-ui/index.html`
- 验证 SSH 接入：`ssh -p 52020 <用户名>@localhost`，输入数据库用户密码

## 📂项目目录结构

```
SSHProxy-project/
├── pom.xml                              # Maven 依赖与版本管理
├── src/main/java/com/zhanghan/sshproxyproject/
│   ├── SshProxyProjectApplication.java  # 启动类(@EnableAsync + @EnableScheduling)
│   ├── common/
│   │   ├── Constants.java               # 命令审查规则库: BLOCK 37 / ALLOW 25 / SUSPICIOUS 52
│   │   └── utils/
│   │       ├── PermissionUtil.java      # 角色命令名白名单(三层防线第一层)
│   │       ├── ProxyForwarder.java      # 双向转发 + 命令审查编排 + 审计落库
│   │       ├── CaffeineUtil.java        # 验证码缓存与发送限流
│   │       └── LoginUtil.java / UserHolder.java / EmailUtil.java ...
│   ├── config/
│   │   ├── SshServerConfig.java         # SSHD 代理服务器(52020)
│   │   ├── ThreadPoolConfig.java        # 5 组线程池定义
│   │   ├── MybatisPlusConfig.java       # 分页插件
│   │   ├── OpenApiConfig.java           # Swagger 文档 + authorization 鉴权
│   │   └── CorsConfig / MvcConfig / MailConfig / SshClientConfig ...
│   ├── core/
│   │   ├── review/StaticRuleEngine.java # 静态规则引擎(Phase 1, 四层漏斗)
│   │   ├── proxy/ListenManager.java     # 连接接入 + 二次认证 + 后端连接
│   │   └── server/BackendManager.java   # 后端服务器轮询分配
│   ├── controller/                      # REST: auth/user/dashboard/audit/statistics/ai/session/alert
│   ├── service/
│   │   ├── CommandReviewService.java    # 两级审查编排器(Phase 1 + Phase 2)
│   │   ├── DeepSeekService.java         # AI 调用 + 多道解析容错
│   │   ├── AlertService.java            # 高危告警 + SSE 推送 + 强制断连
│   │   └── RecommendationService.java   # AI 策略建议(每日生成 + 缓存)
│   ├── session/SessionManager.java      # 在线会话池 + 强制断连
│   ├── listener/                        # SSH 登录/登出监听、审计日志监听
│   ├── schedule/                        # 定时任务(看板/心跳/健康检查/策略刷新)
│   └── mapper/ entity/ dto/ vo/         # 数据访问 / 实体 / 出入参
└── src/main/resources/application.yaml # 本地敏感配置(不入库)
    src/main/resources/application-example.yaml # 部署占位模板
```

## 📋注意事项

- **认证方式**：目前启用**用户名 + 密码**认证（用户密码与数据库明文比对）；公钥认证代码已实现但处于注释态（未启用）。代理连后端使用 `sys_role` 表中的角色账号密码自动登录。
- **密码安全**：用户密码与数据库密码均为**明文存储/比对**，未使用 BCrypt 加密，仅适合学习/课设环境；生产环境必须升级为加盐哈希。
- **AI 审查的 Fail Open**：DeepSeek 不可用时，灰区命令会被放行且不审查（静态规则仍兜底）。若要"AI 挂则全拦截"，需改为 Fail Closed 策略。
- **静态规则的边界**：正则匹配可被混淆绕过（反斜杠转义、变量展开、命令替换 `$(...)` 等），AI 异步审查是补位而非最终防线；已知改进方向为本地嵌入模型 + 向量语义匹配。
- **审计状态位**：`audit_log.status` = 0 正常 / 1 静态拦截 / 2 AI 高危 / 3 AI 中危；AI 高危与中危命令以 `[AI-HIGH]` / `[AI-MEDIUM]` 前缀标记。
- **看板已知隐患**：`DashboardData.totalCmdNum/totalDangerCmdNum` 为 `AtomicLong` 字段，`refreshData()` 每 3 秒将今日命令数 `addAndGet` 累加到总数，存在重复累加风险；`AtomicLong` 直出 JSON 序列化需验证前端兼容性。
- **Web 终端（在线终端）为预留态**：前端有入口，后端 `TerminalController` / `WebTerminalEndpoint` / `WebSocketConfig` 处于注释/预留，未启用，请勿在生产环境依赖。
- **mapper-locations** 配置指向 `classpath:mapper/*.xml`，该目录不存在（SQL 全部使用注解），属冗余配置，无实际影响。
- **数据库与部署脚本缺失**：仓库暂无 `schema.sql` 与本项目自身的 Dockerfile/docker-compose（后端服务器本身为 docker compose 部署，与本项目无关）；建议后续补充一键初始化与容器化部署。
- **敏感信息**：`application.yaml` 内含真实密钥，提交代码前务必清理；轮询后端为空时 `BackendManager.select()` 存在除零隐患，启动初始化已校验非空。
- **压测数据**：README 中所有时间指标（静态 <1ms、AI 1~3s、灰区 <5%）为代码注释中的**设计目标**，非实测数据，投稿/答辩前建议用根目录 `ssh_load_test.py` 实测后替换 `[需要补充真实指标]`。
