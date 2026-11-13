# 企业微信应用配置现状

记录日期：2026-11-13。根据企微管理后台、正式服务器和真实登录验收结果核对。下文保留此前排查记录；当前状态以本节为准。

## 本次已完成

| 项目 | 结果 |
| --- | --- |
| DNS | `x.aixmax.cn` 已解析到 `43.138.147.3` |
| SSH | 本机生产密钥以 `ubuntu` 登录成功，保留严格主机密钥检查 |
| 独立 Nginx 站点 | `/etc/nginx/sites-available/x.aixmax.cn.conf`，已启用 |
| 企微校验文件 | 公网 HTTP / HTTPS 均返回 200，内容与原始文件一致 |
| OAuth 可信域名 | `x.aixmax.cn`，归属校验通过，后台显示已启用 |
| 企业可信 IP | `43.138.147.3`，用户确认后已核对保存，后台显示已配置 1 个 IP |
| HTTPS 证书 | Let’s Encrypt，覆盖 `x.aixmax.cn`，正常证书校验通过 |
| 证书有效期 | 至 `2027-02-11 05:35:22 UTC` |
| 自动续期 | `certbot.timer` active / enabled，本域名模拟续期成功 |
| 企业应用凭据 | 已配置到 root 专用服务端文件，服务器换取应用令牌成功 |
| 业务发布 | 独立前端、后端与数据库已部署，正式登录页与健康接口均 200 |
| 真实登录验收 | 企微授权 → 工作台 → 刷新 → 退出 → 匿名拦截均通过 |
| 成员角色 | 首个成员为 `MEMBER`，尚未指定初始管理员 |

已保存企微配置截图：`screenshots/wecom-ip-saved.jpg`；可信域名截图：`screenshots/wecom-domain-verified.jpg`。

证书位于 `/etc/letsencrypt/live/x.aixmax.cn/fullchain.pem`，私钥保留在服务器，不复制到项目。续期使用 webroot `/var/www/sinx-platform/verification`。部署 hook `/etc/letsencrypt/renewal-hooks/deploy/sinx-platform-nginx-reload` 仅针对本证书检查并重载 Nginx，已单独执行验证。

实际通过的续期检查：`certbot renew --cert-name x.aixmax.cn --dry-run --non-interactive --no-random-sleep-on-renew`，返回本域名模拟续期成功。模拟续期没有替换生产证书。

操作前配置备份：`/var/backups/sinx-platform-20261113T063319Z`。原有 52 个 Nginx 配置文件 hash 未改变，原抽查业务站点状态保持一致。`nginx -t` 通过，原有其他站点重复 MIME / server_name 告警未在本次范围修改。

**业务已发布为内部登录联调版本。** 正式登录页可访问、企微真实登录已通过。独立数据库为 MySQL 8.0.46，后端仅监听 `127.0.0.1:18088`；数据库仅监听 `127.0.0.1:13316`。部署路径、服务配置和验收见 [服务器部署记录](server-deployment.md)。飞书、管理 CRUD 与下游 SSO 尚未接入。

**密钥轮换暂缓：** 排查误填管理员字段时，Secret 曾误入工具日志，已向用户说明。用户于 2026-11-13 明确选择「暂不重置，先保留当前配置」，因此保留现有凭据和服务配置；轮换仍作为后续待办，不在文档记录密钥内容。

部署配置副本：`../deploy/x.aixmax.cn.http.conf`（首次申请证书）、`../deploy/x.aixmax.cn.conf`（此前业务 503 站点）与 `../deploy/x.aixmax.cn.app.conf`（当前业务站点）。发布后保留了校验文件与 ACME webroot 规则。

## 以下为本次修复前的历史排查记录

### 此前已保存

| 项目 | 结果 |
| --- | --- |
| 应用名称 | 信计 X 平台 |
| 应用介绍 | 公司内部统一登录与应用入口，用于访问创作者管理等内部业务系统。 |
| 应用主页 | `https://x.aixmax.cn/` |

结果截图：`screenshots/wecom-app-config.jpg`。

### 此前已有配置

- AgentId：`1000004`。管理页 URL 末尾的长编号不是后端需要的 AgentId。
- 企业微信授权登录：网页应用已启用。
- Web 网页授权回调域：已有 `x.aixmax.cn`，无需重复修改。
- 可见范围：页面显示公司组织「信计网络」，本次未改动。
- 后端出口公网 IP：用户已确认为 `43.138.147.3`，待域名归属校验后添加到企微。
- DNS：本机查询 `x.aixmax.cn` 的 A 记录已返回 `43.138.147.3`，无需继续按“尚未解析”处理。

未读取或重新生成 Secret，也未修改应用凭据、管理员或可见范围。

### 此前受阻项目（现已解决）

1. 尝试将 OAuth2.0 网页授权可信域名保存为 `x.aixmax.cn`，后台返回「域名所有权检验不通过」，未保存成功。页面还说明域名备案主体需与当前企业主体相同或有关联关系。
2. 企业可信 IP 页面不允许直接填写，提示「配置企业可信IP前，请先设置可信域名或设置接收消息服务器URL」。本平台当前不提供企微接收消息服务器，后续应完成可信域名校验后配置后端出口公网 IP。截图：`screenshots/wecom-trusted-ip-blocker.jpg`。
3. 用户补充 IP 后重新打开企微可信 IP 配置，仍显示上述前置条件提示，无可填写 IP 的输入框，`43.138.147.3` 尚未保存到企微。
4. 本机公网探测仍未取得 HTTP 校验文件内容（连接被关闭）；访问 HTTPS 登录页返回证书域名不匹配错误 `ERR_TLS_CERT_ALTNAME_INVALID`。未绕过证书校验，不能将 DNS 解析成功视为校验文件或 HTTPS 已就绪。
5. 进一步检查证书错误携带的公开信息：当前服务器返回的证书 CN/SAN 均仅为 `entai.aixmax.cn`，未覆盖 `x.aixmax.cn`。这说明请求尚未由覆盖 x 域名的有效证书处理；在登录服务器核对虚拟主机配置前，不修改现有 entai 站点。

### 此前 SSH 排查记录（现已解决）

用户已授权使用本机 SSH 密钥检查 `43.138.147.3`。该 IP 在本机 known_hosts 中有记录，连接保留严格主机密钥检查。`antv_prod_ed25519` 与 `id_ed25519` 以 `root` 连接均收到权限拒绝，尚未登录或修改任何服务器配置；正在等待正确的 SSH 用户名，或指定的密钥路径/非默认端口。

### 校验文件本地副本

文件保存于被 Git 忽略的本机目录：

```text
D:/需求文档/opc-antv/sinx-platform/.local/wecom-domain-verification/WW_verify_vtwLRfnVCpw6BxoN.txt
```

该文件现已按原名部署，企微控制台给出的地址可返回原始文件内容：

```text
http://x.aixmax.cn/WW_verify_vtwLRfnVCpw6BxoN.txt
```

该请求不能被前端 SPA 回退为 index.html。应保留校验文件原始内容，不将其内容当作 App Secret。域名归属校验、可信域名和后端出口公网 IP 均已配置。

后端已配置企业 CorpID、此应用 Secret、AgentId `1000004` 与正式入口 `https://x.aixmax.cn`，真实企业登录已验证。
