# 信计 X 平台 / SinX Platform

公司内部统一平台，独立于创作者平台。企业授权验证身份后，只有已关联且启用的平台成员可以登录；未关联账号进入待关联列表，由管理员核对处理。提供手动成员管理、双渠道身份关联、应用目录、按成员授权和操作记录。未配置的渠道保持禁用，没有演示账号或假登录。

## 目录与运行条件

- frontend/：React 18.3.1、Umi 4.7.32、Ant Design 5.29.3，独立构建静态文件。
- backend/：Java 17、Spring Boot 4.0.9，独立打包 JAR，Flyway 管理 MySQL 表结构。
- deploy/nginx.conf：同源 /api 反向代理示例，对外部署时放在 HTTPS 网关后。
- deploy/x.aixmax.cn.conf：此前仅配置证书的独立站点，包含企微校验文件与业务 503 占位；对应的 .http.conf 用于首次申请证书。
- deploy/x.aixmax.cn.app.conf：已部署业务的正式域名入口；deploy/sinx-platform.service 为后台独立系统服务配置。
- compose.yml：可选的开发 MySQL 8.4；本机已用独立 MySQL 8.0.45 实例验证，不依赖原有 MySQL 服务数据。

需要 Node.js 22+（本机验证 24.14）、npm、JDK 17+、Maven 3.9+、MySQL 8.0+。前后端可以分别部署；浏览器入口需通过网关同源访问后端，避免跨域 Cookie 的额外复杂性。

## 本机当前运行环境

本次在 127.0.0.1:3307 初始化了专用 MySQL，数据位于 .local/mysql-data；开发库 sinx_platform 与测试库 sinx_platform_test 分开。没有修改原来的 MySQL80 服务。随机数据库凭据只保存在被忽略的 .local/db-credentials.json 和 backend/config/application-local.yml。

已提供 .local/start-mysql.ps1 与 .local/stop-mysql.ps1 便于本机重启或关闭专用实例；这些是机器配置，不提交仓库。不要再同时启动 compose 的 3307 端口。

## 本地启动

在 backend/ 下运行：

    mvn spring-boot:run

后端默认 http://localhost:8080。从 backend/ 启动才能加载 ./config/application-local.yml；也可直接设置环境变量。

在 frontend/ 下运行：

    npm ci
    npm run dev

浏览器访问 http://localhost:8000/login。/api 代理到后端 8080。飞书和企微均未配置时会显示“暂未开通”；/workspace 匿名访问自动跳到登录。

## 本地前端联调线上后端

在 `frontend/` 执行 `npm run dev:online`，启动本机8000端口并将 `/api` 代理至 `https://x.aixmax.cn`。`http://localhost:8000/api/health` 可用于核对连通性；普通 `npm run dev` 仍连接本地8080后端。

线上OAuth回调固定为正式域名，localhost与正式域名的Cookie独立。需要查看真实登录后的本地新界面时，在另一个终端运行 `npm run preview:online`。它打开独立Edge联调窗口，在正式域名下仅替换前端HTML和静态资源为本地开发产物，API、创作者页面及企业登录请求访问线上服务。地址栏保留正式域名以使用真实回调和Secure Cookie；本地资源请求不转发登录Cookie。请用真实企业账号登录，所有业务操作均作用于线上数据。

联调使用被Git忽略的 `.local/online-preview/browser-profile/`，状态文件仅记录页面路径与本地卡片数量，不记录授权码或Cookie。关闭联调窗口即可结束预览；不要在此模式启动期间运行占用8000端口的Playwright测试。不需要调整线上配置、授权回调或数据库。

## 新环境配置

1. 准备独立 MySQL 数据库和应用账号；创建数据库时使用 utf8mb4 / utf8mb4_bin，外部身份键必须区分大小写。
2. 复制 backend/config/application-local.example.yml 为 application-local.yml，填写数据库连接。不要提交密钥。
3. 如需用 Docker 创建开发库，在 sinx-platform/.env 写入 SINX_DB_PASSWORD 和 SINX_MYSQL_ROOT_PASSWORD 两个随机密码，然后执行 docker compose up -d mysql。将相同应用密码填入后端本地配置。
4. 启动后端会自动执行 db/migration 中的增量迁移。

主要环境变量：SINX_DB_URL、SINX_DB_USERNAME、SINX_DB_PASSWORD、SINX_PORT、SINX_PUBLIC_BASE_URL。身份源可用下面的变量，或在 application-local.yml 中填写对应配置。

两种方式择一使用：导入的 application-local.yml 会覆盖主配置中的同名项，因此本地文件填写了具体值时，下面的 FEISHU_* / WECOM_*、SINX_DB_* 等简写变量不会覆盖这些值。纯环境变量部署时不要复制本地配置；本机使用本地文件时直接修改文件并重启。标准 Spring 环境变量（如 SPRING_DATASOURCE_URL、SINX_AUTH_FEISHU_ENABLED）仍可覆盖对应配置项。

| 渠道 | 必需配置 | 可选配置 |
| --- | --- | --- |
| 飞书 | FEISHU_ENABLED=true、FEISHU_APP_ID、FEISHU_APP_SECRET、FEISHU_TENANT_KEY | 初始管理员使用下述显式初始化配置 |
| 企业微信 | WECOM_ENABLED=true、WECOM_CORP_ID、WECOM_AGENT_ID、WECOM_APP_SECRET | 初始管理员使用下述显式初始化配置 |

旧 FEISHU_ADMIN_SUBJECTS / WECOM_ADMIN_SUBJECTS 不再用于登录提权。初始管理员须在部署前明确核实身份，再临时设置 SINX_BOOTSTRAP_PROVIDER（feishu / wecom）、SINX_BOOTSTRAP_TENANT_ID、SINX_BOOTSTRAP_SUBJECT_ID、SINX_BOOTSTRAP_DISPLAY_NAME；关联旧成员时另设 SINX_BOOTSTRAP_MEMBER_ID。初始化在启动阶段事务性建立成员和身份关联并记录审计，数据库完成标记防止以后重启重新提权。完成后移除临时初始化配置；勿将 Secret 填入身份字段。

## 创建飞书企业自建应用

正式域名已确定为 `x.aixmax.cn`，本机最新 DNS 查询已返回 `43.138.147.3`。按此域名整理的飞书、企微创建流程和可复制参数见 [企业应用配置清单](docs/enterprise-app-setup.md)。

1. 在本公司飞书开放平台创建企业自建应用，记录 App ID / App Secret。
2. 配置网页授权登录的回调地址：对外平台域名 + /api/auth/feishu/callback。SINX_PUBLIC_BASE_URL 必须与浏览器使用的入口一致，不能填后端内网地址。
3. 按应用控制台要求开通用户身份与基础信息所需权限，确认用户信息接口能返回 open_id、name、tenant_key；本期不申请全量通讯录同步。
4. 设置应用可用范围并发布至内部测试人员，确认本企业 tenant_key，作为后端允许登录的企业标识。
5. 填入本地配置，将 enabled 改为 true，重启后端。使用非管理员账号测试登录、刷新、退出，再测试错误企业账号被拒绝。

服务端通过 authen/v2/oauth/token 交换授权码，再通过 authen/v1/user_info 验证身份；不向前端传回飞书令牌。

## 创建企业微信自建应用

1. 在本公司企业微信管理后台创建自建应用，记录企业 CorpID、应用 AgentID、该应用 Secret。
2. 为应用配置 Web 网页授权回调域名 / OAuth 可信域名。回调地址：对外平台域名 + /api/auth/wecom/callback。
3. 配置测试成员可见范围，以及后台要求的企业可信 IP（后端出口公网 IP）和域名归属验证。真实联调通常需要管理员可配置的域名与 HTTPS 入口；只填 localhost 不代表企微能回调。
4. 给予本应用读取授权成员基础资料的权限。本实现调用 user/get 验证成员属于应用可访问范围且 status=1，并读取姓名；无法读取时拒绝登录，不扩大申请全量通讯录权限。
5. 配置 WECOM_*，重启后端后使用企业微信登录。外部联系人的 openid 不会建立内部账号。

普通浏览器使用官方 login.work.weixin.qq.com/wwlogin/sso/login（CorpApp）扫码 / 企微快速登录。企微内置浏览器识别 wxwork 标记后，自动使用 open.weixin.qq.com/connect/oauth2/authorize 的 snsapi_base 静默授权，回调仍为同一 /api/auth/wecom/callback，后端通过 auth/getuserinfo 验证身份，再执行原有成员关联校验。User-Agent 只选择入口，不能证明身份或权限。应用 access_token 仅在后端缓存，并在失效时最多刷新重试一次。

已有会话直接进入工作台；授权错误页不自动重试，未关联账号仍提示联系管理员。主动退出后使用 /login?logged_out=1 停留登录页，避免在企微内立即重新登录；手动点击登录可以重新发起授权。不需要新增 Secret、回调地址或全量通讯录权限。

## 成员与权限边界

- 身份键为 provider + tenant_id + subject_id。相同身份重复登录使用同一平台成员。
- 两个渠道下的同名、同手机号或同邮箱人员不自动关联。管理员在「成员管理 → 待关联账号」核对身份并关联到已启用成员，同一成员可有飞书和企微两个登录身份。
- 未关联的真实企业身份只记入 pending_identity，不创建平台成员，不建立登录态，页面提示联系管理员。外部创作者档案不属于登录账号。
- 初始管理员通过独立的显式部署配置预先关联；第一位登录者不会自动提权。以后角色由管理页面维护。
- 管理员可创建成员、修改姓名/角色/启用状态、关联及解除关联；管理接口要求 ADMIN 与 CSRF。普通成员不能读取管理列表或操作记录。
- 角色或启用状态变化、关联及解除关联均递增会话版本；旧会话在后续请求失效，重新启用不会恢复旧会话。平台姓名不被上游姓名覆盖。
- 最后一位启用且拥有当前可用渠道身份的管理员不可被降级、停用或解除最后一个可用身份。数据库守卫行锁保护并发变更；成功管理操作保存审计。
- V2 升级保留 V1 自动登记的成员，将旧身份转入待关联。上线前必须备份数据库并预先配置已核实的首位管理员，避免升级后无人可登录。
- 工作台 /api/applications 仅返回当前成员已获授权、启用且接入可用的应用；创作者还需 CREATOR_SSO_ENABLED=true。管理员的业务访问同样需要应用授权，平台管理权限不受此限制。
- 工作台采用品牌感门户布局：白色68px顶部导航、浅蓝灰背景、1040px内容容器、两列白色应用卡片；小屏改为单列。卡片以图标、名称、说明和「进入应用」提示呈现真实入口，整卡为原生链接，支持键盘操作；无应用时仍显示真实空状态。顶栏、内容和页脚共用对齐规则，成员管理保留1280px内容宽度，不新增侧栏或模拟业务指标。
- 当前使用单实例内存 Session，8 小时闲置超时；退出立即作废，停用成员在下一次受保护请求时被拦截。重启服务会清空会话；多副本部署前需要共享 Session。
- 跨系统OAuth2授权码+PKCE已接入创作者平台；OIDC、全局单点退出、全量通讯录同步、自动匹配、通用OAuth客户端注册与离职事件同步仍未实现。业务系统不得共享浏览器存储或直接读写本平台数据库。

## 应用管理与授权（2026-11-14）

管理员通过顶部「应用管理」进入应用目录、应用授权与操作记录。支持登记链接应用、编辑名称/说明/图标/顺序、启停、按成员分配或撤销权限、搜索和按授权状态筛选。应用标识创建后不可修改，保留应用记录并通过停用控制使用。修改带版本校验，审计与业务变更在同一事务提交。

创作者为已接入的单点登录应用，地址、固定客户端、回调和密钥继续由服务端配置管理，不在此页面修改。新登记的应用为链接入口，地址只接受HTTPS或本机localhost/127.0.0.1开发HTTP。链接授权只控制工作台可见性，目标系统须自行执行访问控制；登记链接不等于已接入单点登录。

单点登录在发放授权码、兑换令牌及逐请求内省时检查应用启用状态和成员授权。撤权递增该成员的应用授权版本，停用/重新启用递增应用访问版本；重新授权不会恢复旧授权码或旧令牌。应用撤权不注销统一平台会话，管理员仍可管理授权。授权变更、成员管理与OAuth签发/兑换共用数据库守卫锁，兑换继续保留原有一次性事务守卫。

V4为增量迁移：登记真实创作者应用，并仅在升级时为既有启用成员保留原有访问权限；新成员默认无应用授权。旧OAuth授权缺少应用版本标记，升级后需重新进入创作者应用。停用成员不能新增授权，已有授权在成员停用期间不可使用；授权人数仅统计启用成员。

本功能已于2026-11-14发布为 `20261114-app-access`，正式数据库V4，发布前完整备份和核验记录见服务器部署文档。本人需重新登录验收管理操作。回滚时不能直接切回不检查应用授权的旧后端，否则会重新开放已撤销的权限；必须先关闭SSO/业务入口，再按完整恢复方案处理。生产数据库严禁执行测试清理。

## 接口

| 方法 / 路径 | 行为 |
| --- | --- |
| GET /api/health | 后端存活检查，不等同于数据库就绪检查 |
| GET /api/auth/providers | 登录渠道及配置状态，不返回密钥 |
| GET /api/auth/{provider}/start | 设置一次性 state，跳转企业授权 |
| GET /api/auth/{provider}/callback | 校验 state、企业和成员，建立平台会话 |
| GET /api/auth/csrf | 返回当前会话的 CSRF token / headerName |
| GET /api/me | 当前成员基本信息；匿名 401 |
| GET /api/applications | 当前成员已授权、启用且接入可用的应用入口 |
| POST /api/auth/logout | 携带 CSRF header 注销会话，成功 204 |
| GET /api/admin/members?q=&page=1&enabledOnly=false | 管理员分页查询成员，每页20项，返回已关联身份 |
| POST /api/admin/members | 管理员新增成员，需 displayName、role、enabled |
| PATCH /api/admin/members/{id} | 编辑姓名、角色、启用状态 |
| GET /api/admin/pending-identities?page=1 | 待关联账号 |
| POST /api/admin/pending-identities/{id}/bind | 使用 memberId 关联成员 |
| DELETE /api/admin/members/{memberId}/identities/{identityId} | 解除账号关联，退回待关联列表 |
| GET /api/admin/audit?page=1 | 分页查询操作记录 |
| GET /api/admin/applications | 应用目录与真实授权人数 |
| POST /api/admin/applications | 登记链接应用，不自动授权成员 |
| PATCH /api/admin/applications/{id} | 编辑、排序、启停；携带version |
| GET /api/admin/applications/{id}/members?q=&filter=all&page=1 | 分页成员授权列表，filter支持all/granted/ungranted |
| PATCH /api/admin/applications/{id}/members/{memberId} | 分配/撤权，携带granted及当前授权version；未创建授权时为0 |
| GET /api/admin/applications/audit?page=1 | 分页查询应用和授权操作记录 |

登录使用 HttpOnly / SameSite=Lax 的 SINX_SESSION Cookie，支持授权跳转返回。state 五分钟过期、一次使用、绑定浏览器会话与身份源。POST 默认启用 CSRF；页面根据后端状态展示，不承担最终鉴权。

## 检查与构建

backend/：

    mvn test
    mvn package

默认测试使用 H2 MySQL 模式执行同一套 Flyway 迁移。验证真实 MySQL 时，将 SPRING_DATASOURCE_URL、SPRING_DATASOURCE_USERNAME、SPRING_DATASOURCE_PASSWORD 指向专用测试库再执行 mvn test。LoginSessionTest 每项会清理平台成员和身份表，只能使用可清理的测试库。本次已在独立 MySQL 8.0.45 测试库执行通过。

frontend/：

    npm run typecheck
    npm run build
    npx playwright install chromium
    npm test

Playwright 自动构建并在本机8000端口启动静态测试服务，使用接口桩验证正式构建产物，避免 Umi 开发模式的冷编译占位页影响结果；不要同时占用测试端口。ProviderProtocolTest 使用受控 HTTP 响应验证渠道协议；LoginSessionTest 与 MemberManagementTest 验证真实后端鉴权和数据库事务。这些均不替代真实企业配置联调。可用 PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH 指定已有 Chromium。

### 依赖审计待办

2026-11-13 的 npm audit 结果仍有 4 项 high、23 项 moderate、10 项 low，critical 为 0。4 项 high 均来自 Umi 的 @umijs/history → query-string 6 → decode-uri-component 0.2 依赖链；已更新可兼容的 path-to-regexp、PostCSS、Vite 间接依赖。不能直接把解码库覆盖到 ESM 版本，否则会破坏上游 CommonJS 调用。正式上线前需通过兼容的 Umi 修复版或评估替代依赖解决并重新审计；当前工程不宣称已经通过生产依赖安全门禁。

## 部署

前端 dist/ 与后端 target/sinx-platform-0.1.0-SNAPSHOT.jar 分别发布。后端设置 SPRING_PROFILES_ACTIVE=prod、真实数据库凭据和 HTTPS 的 SINX_PUBLIC_BASE_URL；生产配置强制 Secure Cookie。网关同源代理 /api，Cookie 不设置跨系统共享 Domain，不需要开放跨域 CORS。

单实例部署，后端仅对网关开放。代理和监控不要记录 OAuth 回调的 query，也不要对接口请求/响应开启包含密钥和令牌的日志。统一平台已增加标准OAuth2授权服务，支持独立创作者系统，部署与验收见其验证记录。

2026-11-13 已在 `43.138.147.3` 配置 `x.aixmax.cn` 的独立站点、企微校验文件、有效期至 2027-02-11 的证书与自动续期，并发布独立前后端及 MySQL 8.0.46 数据库。2026-11-14统一平台更新到 `20261114-app-access`，飞书和企微均启用，提供应用管理与成员授权。后端与数据库均只监听localhost，服务开机启动；保留域名校验与ACME路径。路径、备份、验收边界和密钥轮换待办见 [服务器部署记录](docs/server-deployment.md)。

## 官方接口依据

- 飞书：获取 user_access_token（authen/v2/oauth/token）、获取用户信息（authen/v1/user_info），飞书开放平台官方认证文档。
- 企业微信：Web 登录组件（文档 98152）、获取用户登录身份（98176）、构造网页授权链接（91022）、获取访问用户身份（91023）、获取 access_token（91039）、读取成员。
- Spring Boot 4.0 系统要求、Spring Security CSRF 与服务端会话文档。


## 创作者系统单点登录

Spring Authorization Server提供 `/api/sso/authorize`、`/api/sso/token`、`/api/sso/introspect`、`/api/sso/revoke`，及受Bearer保护的 `/api/sso/userinfo` 和 `/api/sso/members`。固定机密客户端 `creator-platform`、scope `creator`，强制PKCE/S256，生产固定回调 `https://x.aixmax.cn/creator-api/login/oauth2/code/sinx`。授权码1分钟、一次使用，opaque access token最长8小时，无refresh grant。

`CREATOR_SSO_ENABLED` 默认false。启用时配置 `CREATOR_SSO_CLIENT_ID`、至少32字符的 `CREATOR_SSO_CLIENT_SECRET`、`CREATOR_SSO_REDIRECT_URI`、`CREATOR_APPLICATION_URL`。秘密只写服务器配置，同步到创作者后端；不得写前端配置或聊天。V3使用Spring官方JDBC授权表和应用的兑换守卫表；完整兑换在同一事务行锁下执行，避免并发消费。

授权签发绑定原成员session_version，兑换与内省核对启用状态和版本，原权限变更后旧授权不会升级。登录后仅恢复固定授权路径。两个系统不共享数据库和会话Cookie；仅本应用退出，不声明全局单点退出。详见 `../creator-platform/README.md`。
