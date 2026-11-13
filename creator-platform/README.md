# 创作者平台

公司共享的外部职业人员档案。内部已启用成员经信计 X 平台登录后维护资料；编剧、剪辑等外部人员是档案主体，不产生登录账号。

## 工程边界

`frontend/` 和 `backend/` 为独立工程，可分别拆仓构建部署。后端拥有独立 MySQL 数据库及账号，通过标准 OAuth2 授权码与 PKCE 接入统一平台，不读取统一平台数据库。浏览器只持有 HttpOnly 会话 Cookie，令牌保存在后端会话。

首版路径为 `https://x.aixmax.cn/creators/`，API `/creator-api/`。目前只支持单实例内存会话；重启后需要重新登录。退出只注销当前应用。统一平台停用成员或变更其身份、角色后，原令牌在下次业务请求失效。

## 第一版功能

- 列表、搜索姓名/手机/微信、职业/标签/城市筛选、分页、归档列表。
- 新建、编辑、详情；多职业、标签、联系方式、城市、来源、维护人、作品链接、备注。
- 姓名与至少一项职业必填；维护人默认为当前成员，可选启用成员。维护人不用于数据隔离。
- 归档恢复、乐观锁并发冲突、同事务操作记录。记录只保存变更字段名称，不复制联系方式旧值。

## 原型结构对齐

创作者系统使用独立业务侧栏，包含创作者资源库，以及管理员可见的职业分类、技能标签、角色权限。统一平台工作台继续使用无侧栏布局。资源库提供全部、近7日更新、归档三个真实计数，支持维护人和来源筛选；筛选及页码保存在URL中，查看、编辑和返回时保留。

详情采用资料与维护信息两栏；编辑分为基础资料、职业与能力、代表作品、内部备注，提供目录和固定保存操作。个人简介上限2000字，参考报价金额与单位成对填写，最多两位小数。旧版更新请求省略新增字段时保留原值，清空报价需显式传 `quoteAmount:null, quoteUnit:""`。

职业、标签及来源配置真实入库，维护仅限ADMIN；成员可使用启用选项及自行补充档案取值。被档案使用的配置不可改名，可修改说明或停用；停用不清除历史档案。职业引用计数包含归档档案。角色页展示当前ADMIN/MEMBER内置权限，成员身份与角色分配仍在统一平台管理，不支持自定义角色引擎。

不包含文件上传、批量导入、跟进、合同、项目或结算。本版没有创作者演示数据。

## 本地运行

Java 17、Maven、Node.js、MySQL 8。前端使用 React 18 / Umi 4 / Ant Design 5；后端 Spring Boot 4 / Spring Security 7 / JDBC / Flyway。

后端配置通过环境变量或忽略的 `backend/config/application-local.yml` 提供：

| 变量 | 含义 |
| --- | --- |
| `CREATOR_PORT` | 后端端口，默认8081 |
| `CREATOR_DB_URL` / `CREATOR_DB_USERNAME` / `CREATOR_DB_PASSWORD` | 独立业务库与账号 |
| `SINX_BASE_URL` | 浏览器访问的统一平台根地址，默认 http://localhost:8000 |
| `SINX_BACKEND_URL` | 后端调用统一平台地址，默认 http://localhost:8080 |
| `CREATOR_PUBLIC_BASE_URL` | 浏览器访问创作者的根地址，默认 http://localhost:8002，不包含 /creators |
| `CREATOR_SSO_CLIENT_ID` | 固定客户端 creator-platform |
| `CREATOR_SSO_CLIENT_SECRET` | 与统一平台相同的随机客户端密钥，至少32字符 |
| `CREATOR_COOKIE_SECURE` | 本地HTTP可为false，prod强制true |

统一平台同时设置 `CREATOR_SSO_ENABLED=true`、相同客户端凭据、`CREATOR_SSO_REDIRECT_URI=http://localhost:8002/creator-api/login/oauth2/code/sinx`、`CREATOR_APPLICATION_URL=http://localhost:8002/creators/`。回调固定注册；浏览器地址不能使用内网后端地址。

在 `backend/` 运行 `mvn spring-boot:run`；在 `frontend/` 运行 `npm ci`、`npm run dev`（8002）。统一平台前端8000应代理其后端8080，创作者前端代理8081。更多前端约定见 `frontend/README.md`。

## 测试

后端 `mvn test` 默认使用H2；业务测试会清理创作者表，认证测试用本地受控HTTP服务，禁止指向生产数据库。真实MySQL回归使用专用可清理测试库及 `SPRING_DATASOURCE_*` 覆盖。前端 `npm run typecheck`、`npm run build`、`npm test`，浏览器测试使用8002及接口桩。

两个真实后端的HTTP集成测试位于统一平台测试工程，测试专用企业身份适配器与独立创作者JVM均只用于测试，不能发布成登录入口。具体命令和结果见 `docs/verification.md`。

## 部署

前端部署 `frontend/dist/`，后端发布 `backend/target/creator-platform-0.1.0-SNAPSHOT.jar`，生产启用 `SPRING_PROFILES_ACTIVE=prod`。Nginx对 `/creator-api/` 代理时保留路径，前端子路由回退至 `/creators/index.html`。回调及授权接口关闭包含query的访问日志。

`deploy/creator-platform.service` 为独立低权限服务；`deploy/install-first-release.py` 为本次现有服务器的首次发布脚本，强校验既有版本、产物哈希、旧迁移未改和新库不存在，自动备份并协调恢复。脚本只适合该首次发布，不能当作通用更新器反复执行。秘密在服务器生成、只写root可读配置，不输出到日志。

`deploy/update-structure-release.py` 专用于从 `20261113-creators` 更新原型结构版本，支持 `--check` 只读预检。它验证V1原文及已应用迁移校验和，只允许新增V2配置表与V3简介报价字段；备份创作者数据库和产物位置后，在私有端口验证迁移及既有业务资料，再启用正式服务。统一平台产物、配置、PID和Nginx保持不变。回退保留增量表字段；已升级到V3但回退旧产物时可用新release名重试，部分DDL失败需按根用户备份诊断。该脚本不用于其他基础版本。

失败恢复保留增量OAuth表、独立业务库及配置供排查，不自动删库；旧统一平台应用可使用保留的增量表继续运行。正式数据进入后，禁止直接运行测试或删除数据库重装。
