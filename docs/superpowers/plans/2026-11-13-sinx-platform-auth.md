# SinX Platform 登录基础工程实施计划

> **For agentic workers:** 使用 executing-plans 在本会话逐项执行，用户已明确授权正式开发。

**Goal:** 创建独立的统一平台前后端，支持飞书及企业微信授权登录、平台会话、工作台与退出。

**Architecture:** 浏览器通过同源 /api 访问 Spring Boot；后端适配两个身份源，使用平台成员表和服务端 Cookie Session。创作者平台保持独立，本期不实现下游 SSO 协议、不创建模拟生产登录。

**Tech Stack:** React 18 / Umi 4 / Ant Design 5、Java 17 / Spring Boot 4.0、JDBC / Flyway / MySQL。依赖锁定于实际配置。

## 文件与职责
- sinx-platform/frontend：Umi 路由、登录页面、受保护的工作台、类型化 API。
- sinx-platform/backend：认证提供方、一次性 state、成员持久化、Spring Security 会话与 CSRF、数据库迁移。
- sinx-platform/deploy：Nginx 同源代理示例。
- sinx-platform/README.md：环境、配置、启动、飞书/企微控制台步骤与验证边界。

## 执行清单
- [x] 创建独立目录、依赖配置、配置模板和测试骨架。仓库没有初始提交，使用 codex/sinx-platform-auth 分支在原目录创建新工程，不移动已有原型。
- [x] 先运行后端 HTTP 安全测试，确认未实现行为失败：匿名访问、登录状态、CSRF、state 回放和过期、租户拒绝、停用成员、会话与退出。
- [x] 实现最小认证切片。接口：GET /api/auth/providers、GET /api/auth/csrf、GET /api/auth/{provider}/start、GET /api/auth/{provider}/callback、GET /api/me、GET /api/applications、POST /api/auth/logout、GET /api/health。
- [x] 使用受控 HTTP 服务测试两个官方协议适配器；不调用真实企业账户。
- [x] 实现 React 登录和工作台，顶部导航无侧栏；未配置身份源提供清晰状态，禁止假登录。
- [x] 运行前端类型检查和构建；浏览器验证登录页面、错误反馈、移动端与匿名跳转。
- [x] 验证后端构建、迁移和数据库兼容性；无 Docker 时使用隔离的测试环境，不更改已有 MySQL。
- [x] 更新运行文档与本地配置指引，记录已验证项和真实企业联调待办。

## 验收命令
- backend: mvn test；mvn package
- frontend: npm ci；npm run typecheck；npm run build
- browser: Playwright HTTP 工作流（含提供方桩）与响应式检查。

## 决策
身份键为 provider + tenant + subject，绝不根据姓名/邮箱自动合并。首位登录者不自动成为管理员，管理员 subject 需显式配置。成员每次受保护访问检查启用状态。本期单实例内存会话；可撤销且退出失效，重启需要重新登录；未来多实例再引入共享会话。外部身份令牌不返回前端。生产使用 HTTPS + Secure Cookie。

## 验证结果
2026-11-13：后端 MySQL 回归与打包 22 项通过，前端类型/构建通过，Playwright 8 项通过。详细记录见 sinx-platform/docs/verification.md。真实飞书与企微企业应用尚未创建，配置后继续真实联调。依赖审计余项已列为上线前待办。

## 服务器部署续记（2026-11-13）

- [x] 配置正式域名、独立 Nginx 站点、企微校验文件、证书与续期。
- [x] 保存企微可信域名与可信 IP，配置 CorpID、AgentId 和应用 Secret，服务器令牌交换通过。
- [x] 部署独立 MySQL 数据卷、数据库账号和仅监听本机的数据库容器。
- [x] 发布独立前端与后台服务，启用 prod profile、Secure Cookie 和服务开机启动，保留 ACME 与企微校验路径。
- [x] 重新通过 22 项后端测试、前端类型/构建和 8 项浏览器测试；校验上传产物 hash。
- [x] 真实用户完成企微授权，验证工作台、刷新保持会话、退出及退出后匿名拦截。
- [ ] 处理依赖审计余项及数据库备份恢复演练；飞书、管理员指定、管理 CRUD 与下游 SSO 后续单独推进。
- [ ] 排查误填管理员字段时 Secret 曾误入工具日志，已向用户说明；用户于 2026-11-13 选择暂不重置、保留现有配置，轮换留待后续处理。
