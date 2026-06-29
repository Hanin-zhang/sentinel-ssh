# SSHProxy-project

SSH 代理堡垒机服务 — 基于 Spring Boot + Apache SSHD 构建的轻量级 SSH 中间代理层，提供命令拦截、审计日志、RBAC 权限控制及实时监控看板。

## 架构概览

```
用户 (SSH Client)
    │
    │  TCP :52020 (密码/密钥认证)
    ▼
┌─────────────────────────────┐
│     SSH 代理层 (本项目)       │
│                             │
│  SshServer (52020)          │
│      │                      │
│  ListenManager              │
│      │                      │
│  ProxyForwarder ←→ 审计日志  │
│      │          ←→ 命令拦截  │
│      │          ←→ 权限校验  │
│      │                      │
│  SshClient ──────────────────│
└─────────────────────────────┘
    │
    │  TCP :22/:2222/...
    ▼
┌─────────────────┐
│  后端真实服务器   │
│  (多台, 轮询分配) │
└─────────────────┘

前端 Dashboard (HTTP :8080/api/*)
    │
    ▼
┌─────────────────┐
│  Spring MVC     │
│  REST API       │
│  MyBatis-Plus   │
│  MySQL          │
└─────────────────┘
```

## 技术栈

| 组件 | 版本 / 说明 |
|------|-------------|
| JDK | 17 |
| Spring Boot | 3.2.2 |
| Apache SSHD | 2.16.0 (sshd-core + sshd-netty) |
| MyBatis-Plus | 3.5.14 |
| MySQL | 8.x (mysql-connector-j) |
| Lombok | 最新 |
| WebSocket | spring-boot-starter-websocket (预留) |

## 项目结构

```
src/main/java/com/zhanghan/sshproxyproject/
├── SshProxyProjectApplication.java    # 启动类
├── common/
│   ├── Constants.java                 # 常量（危险命令黑名单、角色名等）
│   └── utils/
│       ├── LoginUtil.java             # 密码 / 公钥登录校验
│       ├── PermissionUtil.java        # 基于角色的命令权限判断
│       ├── ProxyForwarder.java        # 双向数据转发 + 命令拦截 + 审计
│       ├── StreamUtil.java            # (预留工具类)
│       └── UserHolder.java            # ThreadLocal 用户上下文
├── config/
│   ├── CorsConfig.java                # 跨域配置
│   ├── MapConfig.java                 # 全局 Map Bean 定义
│   ├── MvcConfig.java                 # 登录拦截器注册
│   ├── SshClientConfig.java           # 全局 SSH 客户端 Bean
│   ├── SshProperties.java             # SSH 配置属性映射
│   ├── SshServerConfig.java           # SSH 代理服务器 (端口 52020)
│   ├── ThreadPoolConfig.java          # 三组线程池定义
│   └── WebSocketConfig.java           # (预留, 暂未启用)
├── controller/
│   ├── AIRiskController.java          # AI 风险分析 / 排行榜
│   ├── AuditController.java           # 审计日志查询
│   ├── DashboardController.java       # 首页看板
│   ├── LoginController.java           # 用户登录/登出
│   ├── StaticsController.java         # 个人/全局统计
│   └── UserController.java            # 用户列表
├── core/
│   ├── client/
│   │   └── SessionManage.java         # (早期会话管理, 已废弃)
│   ├── server/
│   │   └── BackendManager.java        # 后端服务器列表 + 轮询算法
│   └── proxy/
│       ├── ListenManager.java         # 代理核心：接收用户请求，认证并连接后端
│       └── ProxyShellCommand.java     # SSH Shell 命令适配器
├── dto/
│   ├── LoginFormDTO.java              # 登录表单
│   ├── PageQueryDTO.java              # 分页查询参数
│   └── Result.java                    # 统一响应体
├── entity/
│   ├── AuditLog.java                  # 审计日志表
│   ├── BackendServer.java             # 后端服务器表
│   ├── CommandProcessor.java          # 命令处理封装 (预留)
│   ├── ConnectWay.java                # 连接方式 (预留)
│   ├── DangerCmdInfo.java             # 危险命令信息
│   ├── DashboardData.java             # 看板数据聚合
│   ├── SessionContext.java            # 会话上下文（双向转发用）
│   ├── SessionInfo.java               # 在线会话信息
│   ├── SysRole.java                   # 系统角色表
│   ├── TerminalSession.java           # Web终端会话 (预留)
│   └── User.java                      # 用户表
├── interceptor/
│   └── LoginInterceptor.java          # Token 登录拦截器
├── listener/
│   ├── AuditLogListener.java          # 审计日志事件监听
│   └── LoginListener.java             # SSH 会话登录/登出监听
├── mapper/
│   ├── AuditLogMapper.java            # 审计日志 Mapper
│   ├── BackendServerMapper.java       # 后端服务器 Mapper
│   ├── DashboardMapper.java           # 看板统计 Mapper
│   ├── SysRoleMapper.java             # 角色 Mapper
│   └── UserMapper.java                # 用户 Mapper
├── schedule/
│   └── OnlineUserTask.java            # 定时任务：心跳检测 + 每日统计重置
├── service/
│   ├── AuditLogServiceImpl.java       # 审计日志服务
│   ├── DashboardServiceImpl.java      # 看板数据服务
│   ├── IBackendServerServiceImpl.java # 后端服务器服务
│   ├── IUserServiceImpl.java          # 用户服务
│   ├── LoginServiceImpl.java          # 登录服务
│   ├── SysRoleServiceImpl.java        # 角色服务
│   └── TerminalServerImpl.java        # (预留, 暂未启用)
├── session/
│   └── SessionManager.java            # 在线会话池 + 危险命令统计
├── vo/
│   ├── AuditLogVO.java                # 审计日志视图对象
│   ├── BackendServerVO.java           # 后端服务器视图对象
│   └── UserVO.java                    # 用户视图对象
└── websocket/
    └── WebTerminalEndpoint.java       # (预留, 暂未启用)
```

## 核心流程

### 1. 用户 SSH 连接流程

```
用户 --SSH(:52020)--> SshServer
  │
  ├─ 第一次认证: PasswordAuthenticator / PublickeyAuthenticator
  │    └─ LoginUtil 校验用户名密码/公钥
  │
  ├─ 认证通过 → LoginListener.sessionEstablished()
  │
  └─ 分配 Shell → ProxyShellCommand.start()
       └─ ListenManager.handleRequest()
            ├─ 轮询选择后端服务器 (BackendManager.select)
            ├─ 第二次认证: 代理→后端 (使用角色密码)
            ├─ 创建 ChannelShell 到后端
            ├─ 写入在线会话池 (SessionInfo)
            └─ ProxyForwarder.forward()
                 ├─ forwardUserToBackend()  用户输入→后端
                 │    ├─ 命令危险检测 (BLOCK_COMMAND)
                 │    ├─ 角色权限校验 (PermissionUtil)
                 │    └─ 审计日志记录 (AuditLogListener)
                 └─ forwardBackendToUser()  后端输出→用户
```

### 2. 权限模型 (RBAC)

| 角色 | role_code | 可执行命令 |
|------|-----------|-----------|
| admin | admin | 所有命令 (*) |
| operator | operator | exit, ls, cd, cat, pwd, echo |
| guest | guest | exit, ls, pwd, cd |

危险命令黑名单（所有角色均拦截）：`rm -rf`, `mkfs`, `dd if`, `reboot`, `shutdown`, `wget | bash`, `curl | bash`, 挖矿相关等 30+ 条。

### 3. HTTP API

| 方法 | 路径 | 说明 |
|------|------|------|
| POST | `/api/auth/login` | 用户登录 → 返回 token |
| POST | `/api/auth/logout` | 用户登出 |
| GET | `/api/user/list` | 用户列表 |
| GET | `/api/dashboard/overview` | 看板概览数据 |
| GET | `/api/dashboard/servers` | 服务器列表 |
| GET | `/api/audit/list` | 审计日志分页查询 |
| GET | `/api/audit/detail/{id}` | 审计日志详情 |
| GET | `/api/statistics/personal` | 个人统计 |
| GET | `/api/statistics/global` | 全局统计 |
| GET | `/api/statistics/risk-trend` | 近7天风险趋势 |
| POST | `/api/ai/analyze` | AI 命令风险分析 |
| GET | `/api/ai/ranking` | 危险命令排行榜 |
| GET | `/api/ai/recommendations` | AI 策略推荐 |

所有接口（除 `/api/auth/**`）都需要在 Header 中携带 `authorization` token。

## 数据库表

| 表名 | 说明 |
|------|------|
| `user` | 用户表（用户名、密码、角色、公钥、危险命令计数） |
| `sys_role` | 系统角色表（角色名、角色编码、代理密码） |
| `backend_server` | 后端服务器表（IP、端口、在线状态、连接数） |
| `audit_log` | 审计日志表（用户、命令、服务器、是否拦截、时间） |
| `dashboard_stat` | 看板统计表（总命令数、总危险命令数） |

## 快速开始

### 环境要求

- JDK 17+
- Maven 3.6+
- MySQL 8.0+
- SSH 密钥对（用于代理服务器 HostKey）

### 1. 初始化数据库

```sql
CREATE DATABASE IF NOT EXISTS `sshproxy-project` DEFAULT CHARSET utf8mb4;

-- 创建表（参考实体类中的 @TableName 注解）
-- user, sys_role, backend_server, audit_log, dashboard_stat
```

### 2. 配置

复制 `src/main/resources/application-example.yaml` 为 `application.yaml`，修改数据库连接和 SSH 密钥路径：

```yaml
spring:
  datasource:
    url: jdbc:mysql://127.0.0.1:3306/sshproxy-project?useSSL=false&serverTimezone=Asia/Shanghai
    username: your_db_username
    password: your_db_password

ssh:
  public-key-path: C:\Users\HP\.ssh\id_rsa   # 修改为实际路径
  timeout: 20000
```

### 3. 启动

```bash
mvn clean package -DskipTests
java -jar target/SSHProxy-project-0.0.1-SNAPSHOT.jar
```

或者 IDE 中直接运行 `SshProxyProjectApplication`。

启动后：
- **HTTP API**: `http://localhost:8080/api`
- **SSH 代理**: `localhost:52020`

### 4. 测试 SSH 连接

```bash
ssh -p 52020 admin@localhost
# 输入数据库中对应用户的密码
```

## 线程池设计

| 线程池 | Bean 名 | 核心/最大线程 | 用途 |
|--------|---------|-------------|------|
| 监听线程池 | listenExecutor | 1/1 | 监听新 SSH 连接 |
| 工作线程池 | workExecutor | 8/20 | 处理认证和连接建立 |
| IO 线程池 | ioExecutor | 10/30 | 双向数据转发（长任务） |

## 定时任务

| 任务 | 频率 | 说明 |
|------|------|------|
| `refreshData()` | 每 3 秒 | 刷新看板数据（在线数、命令数等） |
| `onlineUserTask()` | 每 60 秒 | 心跳检测，移除超过 20 分钟未活动的会话 |
| `updateLoginCount()` | 每天 0 点 | 重置每日登录计数 |

## 已知待改进项

### 高优先级
- [ ] `DashboardData` 中 `AtomicLong` 字段无法被 Jackson 序列化，首页接口可能报错
- [ ] `BackendManager.select()` 在服务器列表为空时会抛 `ArithmeticException`
- [ ] `CorsConfig` 中 `allowCredentials(true)` 与 `allowedOriginPattern("*")` 冲突
- [ ] `BackendServerVO.status` 与 `BackendServer.online` 属性名不匹配，`BeanUtils.copyProperties` 无法正确复制在线状态
- [ ] `SshServerConfig` 中 `User` 实例被多线程共享，存在并发安全问题

### 中优先级
- [ ] 缺少 MyBatis-Plus 分页插件 Bean（`PaginationInnerInterceptor`），分页查询可能不生效
- [ ] 密码明文存储，建议使用 BCrypt
- [ ] `DashboardServiceImpl.refreshData()` 中 `totalCmdNum.addAndGet()` 逻辑导致数据重复累加
- [ ] `StaticsController.getRiskTrend()` 使用随机模拟数据，应改为真实 SQL 统计

### 低优先级
- [ ] `StreamUtil.java` 为空壳类
- [ ] `CommandProcessor.java`、`ConnectWay.java`、`DangerCmdInfo.java` 未被使用
- [ ] `SessionManage.java`（core/client）已废弃
- [ ] `mybatis-plus.mapper-locations` 指向不存在的 `classpath:mapper/*.xml`
- [ ] `SshClientConfig` 缺少 `@PreDestroy` 优雅关闭
- [ ] `IBackendServerService` 冗余导入 `AuditLog`
