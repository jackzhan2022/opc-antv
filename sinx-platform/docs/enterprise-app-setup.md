# 信计 X 平台：企业应用创建与配置

更新时间：2026-11-13。统一平台正式域名：`x.aixmax.cn`，A 记录为 `43.138.147.3`；后端出口公网 IP 也是此地址。独立 Nginx 站点、企微校验文件和 HTTPS 已配置，证书有效期至 2027-02-11，模拟续期通过。独立前后端及数据库已部署，企微真实登录与退出已验收，详情见 [服务器部署记录](server-deployment.md)。

企微应用现已创建，AgentId 为 `1000004`，主页、介绍、Web 授权回调域、OAuth 可信域名与企业可信 IP `43.138.147.3` 已配置。域名归属校验通过，实际状态见 [企微配置现状](wecom-configuration-status.md)。

## 1. 公共参数

| 项目 | 填写值 |
| --- | --- |
| 应用名称 | 信计 X 平台 |
| 英文名称（如有） | SinX Platform |
| 应用描述 | 公司内部统一登录与应用入口，用于访问创作者管理等内部业务系统。 |
| 应用主页 | `https://x.aixmax.cn/` |
| 登录页 | `https://x.aixmax.cn/login` |
| 工作台 | `https://x.aixmax.cn/workspace` |
| 首次版本号（建议） | `0.1.0` |
| 首次可用范围（建议） | 平台管理员与少量内部测试成员；验收后再扩大至公司成员 |

飞书与企微各创建一个企业内部自建应用，应用名称可相同，凭据分别保存。当前只接入成员登录，不配置消息机器人、事件订阅或消息接收服务器。

## 2. 飞书企业自建应用

1. 进入飞书开发者后台 `https://open.feishu.cn/app`，切换到公司所属企业，创建「企业自建应用」。按上表填写名称、描述并上传应用图标。
2. 在「凭证与基础信息」记录 App ID、App Secret；Secret 只保存在服务端本地配置或部署密钥中。
3. 如需从飞书工作台点击进入平台，添加「网页」应用能力，桌面端主页、移动端主页均填写 `https://x.aixmax.cn/`。浏览器授权登录本身与端内免登是不同能力；本期不依赖机器人功能或端内免登。
4. 在「开发配置 → 安全设置 → 重定向 URL」添加以下完整 URL：

   ```text
   https://x.aixmax.cn/api/auth/feishu/callback
   ```

   按原样填写，不加 `#/login`、通配符、code 或 state，也不要先做 URL 编码。授权请求里的编码与随机 state 由后端生成。
5. 当前代码仅使用用户信息的 `open_id`、`name`、`tenant_key`。官方用户信息接口基础字段无需额外 API scope；不必申请手机号、邮箱、全量通讯录或离线访问权限。
6. 获取本企业 `tenant_key`：在官方「获取用户信息」API 调试台选择本应用，使用本企业测试成员授权取得的 user_access_token 调用接口，读取响应 `data.tenant_key`；同一响应的 `data.open_id` 可用作初始管理员 subject。该成员须在应用测试/可用范围内。调试工具若要求临时回调地址，只添加工具明确显示的地址，勿自行猜测。
7. 「版本管理与发布 → 创建版本」，建议版本号 `0.1.0`，更新说明「首次接入信计 X 平台企业登录」，可用范围先选内部测试成员，提交企业管理员审核并按控制台提示发布。测试版本与正式版本的生效方式以控制台为准。

服务端所需参数：

| 参数 | 来源 |
| --- | --- |
| `FEISHU_APP_ID` | 凭证与基础信息中的 App ID |
| `FEISHU_APP_SECRET` | 同一应用的 App Secret |
| `FEISHU_TENANT_KEY` | 本企业用户信息响应 `data.tenant_key`；不是 App ID |
| `FEISHU_ADMIN_SUBJECTS`（可选） | 本应用下指定管理员的 `open_id`，多个以逗号分隔 |

管理员需在首次登记前配置。现有成员不会因为后续修改环境变量而自动更改角色。

## 3. 企业微信自建应用

1. 使用企业管理员进入 `https://work.weixin.qq.com/` 的管理后台，在「应用管理 → 自建 → 创建应用」填写名称、描述、图标，选择内部测试成员可见范围。
2. 从「我的企业 → 企业信息」记录企业 ID（CorpID）；从新建应用详情记录 AgentId 与该应用 Secret。不要使用其他应用或通讯录同步的 Secret。
3. 应用主页填写 `https://x.aixmax.cn/`。
4. 在应用详情的「企业微信授权登录 → Web 网页 → 设置授权回调域」填写：

   ```text
   x.aixmax.cn
   ```

   这里只填域名，不带 `https://` 或路径。代码中实际完整回调 URL 为：

   ```text
   https://x.aixmax.cn/api/auth/wecom/callback
   ```

5. 按控制台要求配置可信域名及域名归属校验；若还要求「网页授权及 JS-SDK → OAuth 可信域名」，同样填写 `x.aixmax.cn`。当前实现使用浏览器 Web 登录，不依赖 JS-SDK 签名或端内静默登录。
6. 在「企业可信 IP」配置实际调用企微 API 的后端出口公网 IP。若后端经过 NAT，填写 NAT 出口 IP；不能仅根据网站域名解析地址判断。域名/IP 等校验提示以本企业当前控制台为准。
7. 确保应用可访问测试成员的基础资料。当前后端需要通过 `user/get` 取得姓名并校验 `status=1`，只拿到登录 userid 而无权读取基础资料仍无法完成登录。不申请手机号、邮箱等敏感字段。

服务端所需参数：

| 参数 | 来源 |
| --- | --- |
| `WECOM_CORP_ID` | 本公司的企业 ID（CorpID） |
| `WECOM_AGENT_ID` | 本自建应用的 AgentId |
| `WECOM_APP_SECRET` | 本自建应用的 Secret |
| `WECOM_ADMIN_SUBJECTS`（可选） | 指定管理员的内部成员 userid，多个以逗号分隔 |

## 4. 对应当前后端的配置

目前本机已有 `backend/config/application-local.yml`。保留数据库连接，只将下列 `sinx` 段合并到该文件；不要再添加第二个同名顶层 `sinx` 键。以下为待填写示例，不包含真实凭据：

```yaml
sinx:
  public-base-url: https://x.aixmax.cn
  auth:
    feishu:
      enabled: false # 参数填写完成、域名与 HTTPS 就绪后再改 true
      client-id: '填写飞书 App ID'
      client-secret: '填写飞书 App Secret'
      tenant-id: '填写本企业 tenant_key'
      admin-subjects: [] # 如 ['ou_xxx']，首次登记前设置
    wecom:
      enabled: false # 可以单独启用一个渠道
      client-id: '填写企业 CorpID'
      tenant-id: '填写相同的企业 CorpID'
      client-secret: '填写本自建应用 Secret'
      agent-id: '填写 AgentId'
      admin-subjects: [] # 如 ['zhangsan']，必须是内部 userid
```

正式部署时启用 `prod` profile。如果选择纯环境变量部署，则不携带本地 `application-local.yml`，设置 `SPRING_PROFILES_ACTIVE=prod`、`SINX_PUBLIC_BASE_URL=https://x.aixmax.cn`，并按上表填写各渠道变量及 `FEISHU_ENABLED=true` / `WECOM_ENABLED=true`。本地文件与简写环境变量的覆盖关系见 README，勿混用造成配置被覆盖。

## 5. 部署进度与后续渠道联调

已完成 DNS、独立 Nginx 站点、有效 HTTPS 证书、自动续期与模拟续期，以及企微域名校验、可信域名和可信 IP。保留 `/var/www/sinx-platform/verification` 中的校验文件及 HTTP `/.well-known/acme-challenge/` 路径，后续业务发布不能覆盖这些规则。

企微渠道已经完成下列步骤；飞书渠道准备好应用后可按相同顺序联调：

1. 准备正式数据库和服务端应用凭据，设置 `prod` profile 与 `public-base-url=https://x.aixmax.cn`。
2. 发布前后端，将当前 HTTPS 站点的业务 503 占位替换为静态文件服务和同域 `/api/` 反向代理，检查 Nginx 配置后重载。
3. 确认登录页与 `/api/health` 可用，启用已配齐参数的身份渠道，重启后端。
4. 从平台登录页开始授权，不直接访问 callback：回调需要同一浏览器会话中的一次性 state。验证登录、刷新、退出及应用范围外账号被拒绝。

飞书应用尚未创建，可以独立准备应用参数，不影响先联调企微渠道。

## 官方与项目资料

- 飞书开发者后台：https://open.feishu.cn/app
- 飞书登录流程：https://open.feishu.cn/document/sso/web-application-sso/login-overview.md
- 飞书重定向 URL：https://open.feishu.cn/document/uYjL24iN/uYjN3QjL2YzN04iN2cDN.md
- 飞书获取用户信息（基础字段及权限说明）：https://open.feishu.cn/document/server-docs/authentication-management/login-state-management/get.md
- 飞书官方 API 调试操作示例（参考用户身份获取 Token 的部分，无需创建多维表格）：https://open.feishu.cn/document/introduction-2?lang=zh-CN
- 飞书应用可用范围与发布：https://open.feishu.cn/document/home/introduction-to-scope-and-authorization/availability.md
- 企微开启网页授权登录：https://developer.work.weixin.qq.com/document/path/98173
- 企微 Web 登录组件：https://developer.work.weixin.qq.com/document/path/98152
- 腾讯 CNB 官方企微接入说明（可信域名、可信 IP 及同一服务端调用链）：https://cnb.cool/cnb/self-host/oauth/wecom

本次网页工具未能直接读取企微文档正文；企微配置步骤以腾讯 CNB 官方接入说明交叉核对。具体菜单、域名归属要求和开放权限以管理员当前控制台为准。
