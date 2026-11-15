# AGENTS.md

## 项目与当前范围

本仓库包含需求文档、HTML 原型及信计 X 平台（SinX Platform）、创作者平台独立工程。用户已退出探索模式并授权开发。统一平台已部署到 x.aixmax.cn（20261114-app-access），提供企微内静默授权与普通浏览器扫码、飞书授权、已关联成员准入、Cookie Session、品牌感门户、退出、手动成员及身份关联管理、角色和状态管理、操作审计、OAuth2授权服务、应用管理与按成员授权。本人企微账号已一次性预关联为首位管理员；飞书和企微均已启用，首版真实登录已验证，本次重启后的真人登录、应用管理及创作者业务仍待本人验收。禁止引入演示登录绕过。服务器资源与当前安全待办见 sinx-platform/docs/server-deployment.md。

统一平台与创作者平台是两个独立系统，分别拥有前后端、数据库和未来仓库/部署。sinx-platform/、creator-platform/ 均可整体拆为独立仓库。创作者第一版已实现公司共享档案和OAuth2授权码+PKCE单点登录；本次部署和验收记录见creator-platform/docs/verification.md。

## 代码目录与命令

- sinx-platform/frontend/：React 18、Umi 4、Ant Design 5；npm ci、npm run dev、npm run typecheck、npm run build、npm test（Playwright）。
- sinx-platform/backend/：Java 17、Spring Boot 4、Spring Security、JDBC、Flyway；mvn test、mvn package、mvn spring-boot:run。
- creator-platform/frontend/、backend/：同类独立工程；前端8002，API context-path /creator-api；生产页面/creators/，独立数据库creator_platform。
- sinx-platform/README.md：本地配置、企业授权设置、部署、已验证边界。
- preview/：独立 HTML 交互原型，含模拟数据；不得复用其模拟认证至正式工程。

## 认证边界

- 后端交换授权码并确认企业身份，平台独立成员主键与外部身份表分开。
- 身份键为 provider + tenant + subject，数据库使用区分大小写排序规则；禁止用姓名/邮箱自动合并。
- 未关联的企业身份只登记 pending_identity，禁止自动创建平台成员或登录。初始管理员使用独立 SINX_BOOTSTRAP_* 部署配置一次性预关联，旧 ADMIN_SUBJECTS 不再用于登录提权。
- 管理接口 `/api/admin/**` 要求 ADMIN；管理员写操作、身份审批和登录登记共用数据库守卫行锁。角色/启用状态变化和身份变更递增 session_version，旧会话下一次请求失效；保护最后一位有可用登录身份的管理员。
- 一次性 OAuth state 绑定 Session/提供方、五分钟过期；会话登录后更新、POST 需要 CSRF、退出即失效。
- 企微 wxwork 客户端标记仅选择 snsapi_base 网页授权入口，不能替代后端身份及成员准入验证。登录错误页不自动授权；主动退出跳到 /login?logged_out=1，避免免登循环。
- 生产启用 prod 配置强制 Secure Cookie。当前只支持单实例内存 Session。
- 统一平台Spring Authorization Server提供/api/sso授权服务，固定creator-platform机密客户端、固定回调及强制PKCE，授权码一次使用；数据库事务守卫覆盖完整兑换过程。创作者每次业务请求在线内省平台成员版本，Cookie与数据库分别独立，token不进入浏览器存储。退出仅当前应用，不宣称全局退出。
- V4应用授权要求启用成员、应用启用与显式成员授权；管理员的业务访问也需授权，平台管理权限不受此限制。签发、兑换和内省检查应用/授权版本，撤权再授权不得恢复旧凭据。V4仅一次性保留既有启用成员的创作者访问权限，新成员默认无应用权限。链接应用只控制工作台入口，不宣称控制目标系统；旧后端回退前必须关闭SSO，避免重新开放已撤权限。
- 密钥只放忽略的配置或环境变量，不输出秘密、上游令牌或包含秘密的异常消息。

## 数据和产品约定

外部编剧、剪辑等是创作者档案，不是内部登录账号。创作者数据公司共享，维护人不用于数据隔离，具体操作按授权控制。

统一平台工作台无左侧栏，白色顶部导航、克制的应用目录；登录沿用浅蓝网格与卡片视觉，少量文字。未接入应用时显示真实空状态，不伪造数据。

创作者系统按preview/creator-admin-preview.html恢复独立216px业务侧栏，资源库/职业分类/技能标签/角色权限，与统一平台无侧栏要求分开。档案含简介及参考报价、两栏详情、四组表单、URL保留筛选与页码。字典配置仅ADMIN可写；角色页只展示真实内置策略，成员管理回统一平台。V2/V3为增量迁移，已被引用配置不可改名。禁止添加虚假导入导出入口、假计数或把原型动态字段编辑器当作已实现功能。

## 验证和维护

后端默认测试使用 H2，真实 MySQL 回归通过 SPRING_DATASOURCE_* 指向专用可清理的测试库。LoginSessionTest、MemberManagementTest 会清理成员、身份、待关联及审计数据，严禁对生产库运行。前端 Playwright 自动构建并启动8000端口静态测试服务，测试前不要占用此端口。

本机独立数据库位于 sinx-platform/.local/，使用3307，与既有 MySQL80 服务分开；该目录与 backend/config/application-local.yml 不提交。不要读取或打印其中密钥。请保持 docs/需求说明.md、docs/技术选型.md 和工程 README 与实际实现同步。
