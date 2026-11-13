# 成员管理与关联后登录 Implementation Plan

> **For agentic workers:** 使用 executing-plans 在本会话逐项实施；不创建额外工作树或委派任务。当前仓库无初始提交，保留用户现有未跟踪文件，不进行全量暂存或提交。

**Goal:** 外部身份须由管理员关联后登录，提供可用的手动成员管理。

**Architecture:** JDBC/Flyway 分离待关联身份和已绑定身份，成员会话版本使访问变更立即在下一次请求失效。管理员写事务共用守卫行锁，保护最后一位管理员和身份唯一性。前端沿用现有顶部导航及 Ant Design 表格、表单与弹窗。

**Tech Stack:** Java 17 / Spring Boot 4 / Spring Security / JDBC / MySQL；React 18 / Umi 4 / Ant Design 5；JUnit / MockMvc / Playwright。

## Task 1：登录准入回归

- [x] 修改 LoginSessionTest：既有成功登录用显式成员/身份夹具；新增未关联授权不得创建成员、不得访问 /api/me 的用例，先运行 `mvn -Dtest=LoginSessionTest test` 观察失败。
- [x] 新增 V2 迁移、待关联身份仓储，调整 MemberRepository.login：校验企业后查绑定；不存在则登记 pending 并返回 identity_unlinked；存在则检查 enabled，保留平台姓名。
- [x] AuthController 会话保存成员 sessionVersion；过滤器核对会话版本；增加登录页 identity_unlinked 提示。
- [x] 再运行 LoginSessionTest，预期全部通过。

## Task 2：成员管理与一次性初始化

- [x] 新增 MemberManagementTest：普通成员403、匿名401、无CSRF403、创建成员、关联后双渠道同一成员、冲突409、最后管理员保护409、停用/降级/解除关联使旧会话失效、审计可查。先运行测试验证缺失行为。
- [x] 新增 member/MemberManagementService.java，统一事务、守卫行锁、分页、输入校验、审计写入；新增 MemberAdminController.java 与业务错误响应。
- [x] SecurityConfiguration 添加 `/api/admin/**` 的 ADMIN 要求；会话版本防止旧角色继续使用。
- [x] 新增 AdministratorBootstrap.java 与专用 SINX_BOOTSTRAP_* 配置，覆盖新环境及旧成员预关联，初始化完成后不可重复提权；增加幂等和错误企业测试。
- [x] 运行后端完整 `mvn test` 和 `mvn package`。

## Task 3：管理页面

- [x] 新增 frontend/tests/members.spec.ts，先验证缺少管理入口、未关联提示及成员操作页的失败。
- [x] 提取 components/PlatformHeader.tsx 共用顶部导航和退出；新增 pages/Members.tsx 与成员/待关联/审计子组件； services/api.ts 加入明确的业务错误消息、分页和 CSRF 写请求。
- [x] 成员可按姓名搜索、创建和编辑；待关联账号可搜索启用成员后绑定；成员详情可解除绑定；操作日志分页查看。所有请求展示加载、失败和重试状态，提交中禁用重复操作。
- [x] 运行 `npm run typecheck`、`npm run build`、配置本机 Edge 后 `npm test`；检查桌面/390px截图。

## Task 4：发布及记录

- [x] 在本机专用可清理 MySQL 测试库验证迁移和后端回归，禁止向正式库执行测试。
- [x] 自审登录、权限、事务锁及前端请求；更新 README、需求说明、技术选型、验证文档和 CLAUDE.md。
- [x] 对独立正式数据库创建权限600的发布前备份，确认已验证的现有成员是唯一待初始化目标，备份不打印内容。
- [x] 使用新发布目录传输产物并核对SHA-256；明确配置初始管理员身份并切换软链重启本服务；保留既有Nginx、TLS和其他业务配置。
- [x] 验证健康、匿名401、普通入口、初始化审计及数据库迁移，撤下临时初始化配置；升级后的真实登录待用户重新登录验收；本轮已完成公网匿名回归和真实数据库初始化核验，不伪造会话。部署记录已注明该验收边界。
