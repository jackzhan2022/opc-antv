# 统一平台服务器部署记录

最新补充：创作者已单独更新到 `20261113-creator-structure`，其数据库为V3；本次统一平台产物、配置与进程均保持不变。统一平台仍为此前的 `20261113-creators` 后端版本。创作者部署备份、核验与真实身份验收边界见 `../../creator-platform/docs/verification.md`；下文保留各历史阶段记录。

更新时间：2026-11-13。当前已提供企微内静默授权、普通浏览器扫码和手动成员管理，要求已关联平台成员才能登录。首版真实企微登录与退出已验收；最新版本的真实企微内免扫码登录尚待用户重新打开应用验收。不代表完整统一管理平台或全部生产安全待办已完成。

## 已部署资源

| 项目 | 当前配置 |
| --- | --- |
| 入口 | `https://x.aixmax.cn/`，登录页 `/login`，工作台 `/workspace` |
| 服务器 | `43.138.147.3`，SSH 用户 `ubuntu` |
| 发布标识 | `20261113-wecom-client`，保留此前 `20261113-members` 与 `20261113-initial` 产物 |
| 前端 | `/var/www/sinx-platform/releases/20261113-wecom-client`，`current` 软链指向该版本 |
| 后端 | `/opt/sinx-platform/releases/20261113-wecom-client/app.jar`，`current` 软链指向该版本 |
| 后端服务 | `sinx-platform.service`，已启用开机启动，独立系统账号 `sinx-platform` |
| 后端监听 | `127.0.0.1:18088`，只通过 Nginx 同源 `/api/` 访问 |
| 数据库 | 独立容器 `sinx-platform-mysql`，MySQL `8.0.46` |
| 数据库监听 | `127.0.0.1:13316`，不向公网开放 |
| 数据卷 | `sinx-platform-mysql-data`，应用数据库 `sinx_platform` |
| 数据排序规则 | `utf8mb4_bin`，外部身份键区分大小写 |
| 应用配置 | `/etc/sinx-platform/application.env`，root 所有，权限 600 |
| 数据库配置 | `/etc/sinx-platform/mysql.env`，root 所有，权限 600 |
| 配置目录 | `/etc/sinx-platform`，权限 700 |
| 部署清单 | `/etc/sinx-platform/deployment.json`，不包含密钥 |

后端使用 `prod` profile、正式 HTTPS 入口和 Secure Cookie。数据库与后端分别限制资源，未复用其他业务数据库凭据。JVM 最大堆为 256 MB，服务总内存上限 640 MB；数据库容器内存上限 512 MB。部署后抽查后端约 204 MB、数据库约 171 MB，无服务重启。

部署文件来源：`deploy/sinx-platform.service` 与 `deploy/x.aixmax.cn.app.conf`。原 `deploy/x.aixmax.cn.conf` 保留为此前只有证书、业务返回 503 的配置。

## 首版身份源与实际验收（20261113-initial）

- 企微 AgentId `1000004`，可信域名、Web 回调域与企业可信 IP 已配置。服务器凭据交换成功，企微接受服务器来源 IP。
- 企微登录已启用；飞书尚未创建应用，入口保持禁用。
- 真实账号完成授权，回调后进入工作台并显示成员姓名。刷新后仍保持会话。
- 已通过界面退出；退出后再次打开 `/workspace`，自动跳回 `/login`。
- 首个真实成员已落库，角色为 `MEMBER`。未配置初始管理员；管理员字段中误填的 Secret 已清空，未将其当作 userid 或授予角色。
- 工作台显示真实空应用列表；当前尚无创作者系统接入、应用管理 CRUD、成员管理 CRUD 或跨系统 SSO/OIDC。

截图：`screenshots/production-login.jpg`、`screenshots/production-workspace.jpg`。

## 首版检查结果（20261113-initial）

- 本轮 `mvn package`：22 项测试通过；使用 H2 测试配置，未对正式数据库运行清理型测试。
- 本轮前端类型检查、构建通过；Playwright 8 项通过。首次浏览器测试因缺少 Playwright 自带 Chromium 未能启动；指定本机 Edge 可执行文件后全部通过，未更改测试断言。
- 传输前后 JAR 与前端归档 SHA-256 一致。
- 公网 `/login`、`/workspace` 返回 HTML 200，`/api/health` 返回 `UP`；匿名 `/api/me`、`/api/applications` 返回 401。
- `/api/auth/wecom/start` 跳转到企微官方登录地址，回调为 `https://x.aixmax.cn/api/auth/wecom/callback`，包含一次性 state。
- 登录启动请求签发的会话 Cookie 带有 `Secure`、`HttpOnly`、`SameSite=Lax`。无 CSRF 的 POST 退出请求被拒绝；已登录的界面退出工作流通过。
- 正式数据库迁移成功，已登记首个真实内部成员。
- 企微域名校验文件仍返回 200 且内容一致；HTTP ACME 校验路径及自动续期规则保留。

## 备份与恢复边界

首次证书配置前备份：`/var/backups/sinx-platform-20261113T063319Z`。

本次发布前的站点配置备份：`/var/backups/sinx-platform-release-20261113-initial/x.aixmax.cn.conf`。该备份只恢复证书及域名校验站点，业务请求将恢复为 503，不会恢复数据库内容。

若需暂时关闭业务入口，可将该配置恢复到 `/etc/nginx/sites-available/x.aixmax.cn.conf`，先执行 `sudo nginx -t`，通过后重载 Nginx；然后停止 `sinx-platform.service`。保留数据库容器和数据卷。后续版本按独立发布目录更新，保留此前产物以便切回。

本次成员管理发布前已增加一次性完整数据库备份：`/var/backups/sinx-platform-release-20261113-members/sinx_platform.sql`，文件600、目录700，均由root持有。该目录另有旧应用配置、发布清单和原前后端软链路径。

V2 已改变登录准入策略并将旧自动登记身份移入待关联。不能只切回旧JAR就认为完成回滚；旧版会自动登记成员且与新表约束不兼容。出现故障优先停止本服务并修复，若必须恢复旧版本，应在停机和明确恢复方案下同时恢复对应数据库、配置及前后端，避免覆盖新数据。尚未建立定期数据库备份和恢复演练。

## 成员管理版本发布与检查（20261113-members）

- 发布前从正式库核对唯一已完成真实企微登录的本人账号，明确将该既有成员设为首位管理员。身份键从数据库在服务端内存读取，未输出 subject、凭据或授权令牌。
- 停止本服务后完成完整数据库及配置备份；新产物上传前后SHA-256一致。分别创建独立前后端发布目录，切换软链并启动，未修改其他服务或Nginx配置。
- Flyway V2 迁移成功；当前1名启用管理员、1个已关联企微身份、0个待关联账号、1条管理员初始化日志。一次性初始化完成标记已保存，临时 SINX_BOOTSTRAP_* 配置已从文件删除，不会在后续重启重新提权。
- 新账号必须先完成企业身份验证，再由管理员在「成员管理 → 待关联账号」手动关联；未关联不能登录，不自动创建成员。角色、停用状态及关联变更使旧会话失效。
- 后端36项测试与打包通过；本机独立MySQL回归通过；前端类型检查、正式构建及15项Playwright通过。独立审查发现的既有ADMIN升级初始化问题已用失败测试复现、修复并复查。
- 公网 `/login`、`/workspace`、`/members` HTML 200，且HTML摘要与本次产物相同；`/api/health`为UP。匿名成员、应用及3个管理列表接口均401；无CSRF的退出请求403。企微授权跳转和Secure / HttpOnly / SameSite=Lax会话Cookie正常。
- 已核对管理员角色、身份关联、迁移版本、初始化日志和备份权限。升级会清空旧内存会话，用户需重新登录；本轮没有伪造生产会话，升级后的真实登录及管理页面操作尚待本人重新登录验收。
- `screenshots/member-management-preview.png`、`member-binding-preview.png`、`member-management-mobile.png`来自正式前端构建的接口桩测试，包含虚构测试成员，不能作为生产成员数据截图。

## 企微内免扫码版本（20261113-wecom-client）

- 企微内打开登录页自动开始 snsapi_base 静默授权；普通浏览器继续现有企业Web扫码。手机 wxwork 与桌面 WXWork 标记均识别，普通微信不当作企微。
- 两个入口共享回调、auth/getuserinfo、企业校验、手动关联准入和会话签发。未关联仍拒绝登录，User-Agent不能授予身份或权限。
- 授权错误页不自动重试；主动退出停留 /login?logged_out=1，手动登录仍可用。
- 后端41项测试及独立本机MySQL回归通过，JAR打包成功；前端类型检查、正式构建与25项Playwright通过。只读独立审查无阻断项。
- 发布前后产物SHA-256一致；比较JAR内迁移文件确认与成员管理版本完全相同。仅更新前后端发布软链并重启本服务，数据库、应用凭据及Nginx配置未修改。
- 发布记录备份位于 /var/backups/sinx-platform-release-20261113-wecom-client，保留上一版软链及部署清单；原有成员管理版本完整数据库备份继续保留。该次发布无表结构变更，可协调切回20261113-members的前后端产物；不能跨回自动注册的initial版本。
- 公网健康、匿名接口401、CSRF403及HTML与产物一致均已验证。模拟手机/桌面企微User-Agent的线上请求跳转到open.weixin.qq.com，scope=snsapi_base、agentid、回调和片段正确；普通浏览器及普通微信继续Web登录。四类入口的Cookie均为Secure / HttpOnly / SameSite=Lax。
- 服务运行正常。最新真实企微客户端完成授权、进入工作台及成员管理仍待本人验收；请求头模拟和接口桩测试不代表真人授权已完成。

## 密钥与待办

应用 Secret 由用户在被 Git 忽略的本机 `sinx-platform/.local/deployment-wecom.env` 中填写，再经 SSH 加密传输到服务器；服务器临时凭据文件在发布结束后已删除。正式凭据仅供后台使用，未打包进前端产物或仓库。

排查误填管理员字段时，该字段中的 Secret 曾被错误输出到工具日志，已向用户说明。用户于 2026-11-13 明确选择「暂不重置，先保留当前配置」，当前未进行轮换，服务配置保持原值。轮换保留为后续待办；不得在文档中记录密钥值。后续获得更新指示后，只更新本机私密文件与服务器配置，并重启本服务重新核对凭据。

其他待办：

- 前端依赖审计仍为 0 critical、4 high、23 moderate、10 low，未在本次服务器配置中更改依赖；详细来源见 README。当前不宣称已通过全部生产依赖安全门禁。
- 首位管理员已按核实的本人企微身份一次性初始化；以后通过成员管理分配角色，初始化配置不会反复提权。
- 当前单实例内存会话；重启服务需要重新登录，多实例需要共享 Session。
- 按后续实际需求补齐定期备份和恢复演练、应用管理、创作者系统接入及跨系统 SSO；通讯录同步与自动匹配尚未实现。


## 2026-11-13 创作者首版发布：20261113-creators

统一平台后端升级至 `/opt/sinx-platform/releases/20261113-creators`，前端沿用原工作台产物，通过真实应用API显示创作者入口；平台数据库增量V3提供OAuth2授权表及兑换事务守卫，不修改V1/V2。企微配置及已关联管理员保留，飞书继续禁用。

创作者独立后端 `/opt/creator-platform/current/app.jar`，systemd `creator-platform.service`，低权限系统用户 `creator-platform`，localhost:18089；前端 `/var/www/creator-platform/current`；独立数据库 `creator_platform`、账号 `creator`，与平台复用该专有MySQL进程但不共用写库账号。root-only配置 `/etc/creator-platform/application.env`，客户端随机秘密仅保存在两个服务配置内。

网关仅为本域名增加 `/creators/` 静态路径与 `/creator-api/` 独立代理，原企业校验、ACME、平台API和其他站点文件保留。公网只读检查均通过，业务表0条，无生产测试资料。Cookie和登录边界见创作者验证记录；真实企业账号登录后的业务验收仍待本人进行。

发布前备份 `/var/backups/sinx-platform-release-20261113-creators/`，含 `sinx_platform.sql`、原平台配置/清单及Nginx配置，权限600/目录700。失败恢复脚本会停止并取消创作者开机启动、恢复旧平台产物/配置/网关；保留增量OAuth表、独立创作者库及配置用于排查，不自动删业务数据。该脚本是首次发布器，重复发布需要另写更新流程。

本机验证：统一平台51项MySQL测试通过，创作者22项MySQL测试通过，真实双后端HTTP专项1项通过，创作者前端10项浏览器流程及类型检查/生产构建通过。完整记录见 `../../creator-platform/docs/verification.md`。
