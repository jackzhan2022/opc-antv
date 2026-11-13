# 创作者平台第一版

2026-11-13。用户已确认上一轮范围并明确要求开始开发。

## 范围与体验

独立的 `creator-platform/frontend`、`creator-platform/backend` 和数据库。第一版发布在统一域名 `/creators/`，API 为 `/creator-api/`，独立部署，以后可独立拆仓和迁域名。统一平台工作台显示真实创作者管理入口。采用白色顶部导航、克制蓝色、清晰表格和少量提示文字，不放统计大屏或虚构数据。

已启用且已关联的内部成员均可访问和维护，公司共享数据。外部编剧、剪辑等是资料档案，不是登录账号。平台管理员管理成员身份，创作者系统不复制企业凭据、不提供演示登录。

页面：创作者列表（搜索、职业/标签/城市筛选、正常/归档切换、分页）；新建/编辑档案；详情（资料/操作记录）；登录失败与无权限状态。资料包含姓名、手机、微信、城市、多职业、标签、来源、维护人、作品链接、备注。姓名和至少一个职业必填，其他资料选填；维护人默认本人，可选择启用的内部成员。

归档保留资料和历史，归档后不能编辑；支持恢复。新增、编辑、归档、恢复保存操作记录。版本号控制并发更新，冲突提示刷新后重试。作品仅保存http/https链接，本次不包含文件上传、批量导入、跟进、项目或结算。

## 登录与权限

统一平台提供标准OAuth2授权码登录（Spring Authorization Server），创作者后台作为机密客户端，固定回调、state、PKCE、后端交换授权码。opaque access token和授权数据在统一平台独立表中保存，浏览器仅持有各应用的HttpOnly Session Cookie；令牌不进入地址栏或浏览器存储。客户端密钥只保存在各服务配置中。

统一平台授权入口 `/api/sso/authorize`，令牌 `/api/sso/token`，内省 `/api/sso/introspect`，撤销 `/api/sso/revoke`，用户资料 `/api/sso/userinfo`，启用成员列表 `/api/sso/members`。客户端scope为`creator`，授权码一次使用、短期有效，access token最长8小时，不提供refresh grant。服务端内省每次核对成员启用状态和签发时session_version，成员停用/降级/身份变更后旧令牌立即失效。用户资料只返回平台成员ID、姓名和角色。两个系统不直接查询对方数据库。

平台尚未登录时保留授权请求，完成企微/飞书验证后回到授权流程；只恢复固定授权路径，不能通过参数任意跳转。创作者端使用Spring OAuth2 Client建立独立会话，每个受保护API在线内省令牌，失败时关闭访问。退出当前应用撤销其令牌并清除本地会话；不宣称已实现所有系统的全局退出。CSRF保护所有业务写入。

## 业务API契约

以下路径均相对于 `/creator-api`：

| 方法/路径 | 契约 |
| --- | --- |
| GET /health | `{status:"UP"}` |
| GET /me | `{id,displayName,role}`；匿名401 |
| GET /auth/csrf | `{token,headerName}` |
| GET /oauth2/authorization/sinx | 开始统一平台登录 |
| POST /auth/logout | 撤销本应用令牌，清除会话，204 |
| GET /members | 启用成员`[{id,displayName}]` |
| GET /creators | q,profession,tag,city,archived=false,page=1,pageSize=20，返回Page |
| POST /creators | 创建Input，201及完整Creator |
| GET /creators/{id} | 完整Creator，缺失404 |
| PUT /creators/{id} | Input和version，返回Creator；旧version 409 |
| POST /creators/{id}/archive | `{version}`，返回Creator |
| POST /creators/{id}/restore | `{version}`，返回Creator |
| GET /creators/{id}/audit | page=1,pageSize=20，返回Page |

`Page<T>={items:T[],total:number,page:number,pageSize:number}`。

`Input={name,phone,wechat,city,professions:string[],tags:string[],source,maintainerId,works:{title,url}[],notes}`。

`Creator=Input+{id,maintainerName,archived:boolean,version:number,createdAt,updatedAt}`。

`Audit={id,actorName,action:"CREATED"|"UPDATED"|"ARCHIVED"|"RESTORED",detail,createdAt}`；detail只写操作/变更字段名称，不写联系方式原值。

错误统一 `{code}`：unauthenticated(401)、forbidden(403)、invalid_request(400)、creator_not_found(404)、version_conflict(409)、creator_archived(409)、maintainer_unavailable(400)、platform_unavailable(503)。HTTP错误不包含上游令牌、密钥或个人信息。

后端跨任务约定：`com.sinx.creator.auth.CurrentMember` 提供静态 `from(Authentication)` 和 `id()/displayName()/role()`；`com.sinx.creator.directory.MemberDirectory` 由业务任务定义，方法 `List<MemberOption> list(Authentication)`，`MemberOption(String id,String displayName)` 为接口嵌套public record，实际服务由登录任务提供@Bean。业务控制器传入Authentication调用目录验证维护人。

## 验证与上线

授权码/回调/CSRF、未关联与停用、令牌撤销和版本失效通过服务端测试。业务覆盖共享查询、分页筛选、字段与URL校验、审计事务、并发版本和归档恢复；H2与专用本机MySQL均回归。前端浏览器测试覆盖完整增改查归档流程、错误、空状态、移动布局。生产不写演示创作者数据或伪造本人会话，真实登录后的业务验收由本人进行。

上线前备份统一平台和独立创作者数据库，校验产物与迁移，独立服务和回滚记录。沿用现有技术栈，不因首版业务引入额外基础设施。
