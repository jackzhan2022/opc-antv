# 创作者平台前端

独立 React 18 / Umi 4 / Ant Design 5 工程。正式数据全部来自独立创作者后端，没有演示登录或演示数据。

## 本地开发

需要 Node.js 22 及以上。

```powershell
npm ci
npm run dev
```

开发服务默认使用 8002 端口，访问 `http://localhost:8002/creators/`；设置 `PORT` 可覆盖开发端口。`/creator-api` 代理到 `http://localhost:8081`。本地真实登录需要配置后端与统一平台的 OAuth2 固定回调，前端不会伪造登录状态。

```powershell
npm run typecheck
npm run build
npm test
```

Playwright 会自动构建并使用 8002 端口启动静态产物服务，因此测试前需停止占用该端口的开发服务。默认使用本机 Edge，亦可通过 `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` 指定浏览器路径。测试中的接口桩仅存在于 `tests/`，静态测试服务不会提供后端或模拟认证。

## 部署契约

产物为 `dist/`，部署路径为 `/creators/`，SPA 子路由回退到该目录的 `index.html`，静态资源也使用 `/creators/` 前缀。API 使用 `/creator-api`，该路径必须转发到独立后端。页面包括列表 `/creators/`、新增 `/creators/new`、详情 `/creators/:id`、编辑 `/creators/:id/edit`、配置 `/creators/occupations`、`/creators/tags`、角色策略 `/creators/roles` 和状态页 `/creators/auth-error`。配置及角色页面仅管理员可见，后端另行校验配置写权限。

任何 API 返回 401 都跳转 `/creator-api/oauth2/authorization/sinx`，登录失败状态页不自动重试，以免循环。`/creators/auth-error?code=forbidden` 显示无权限状态。写入及退出先获取后端 CSRF token，使用后端返回的请求头名称发送。浏览器仅使用后端会话，不存储访问令牌。

统一平台链接默认 `/`。未来迁域时在构建前设置 `SINX_PLATFORM_URL` 为可信的统一平台完整地址。此环境变量是构建配置，不应包含密钥。

## 验证范围

浏览器测试覆盖真实空状态、新增必填校验、作品 URL 校验、启用维护人选择、详情/操作记录、编辑、版本冲突保留输入、归档确认与恢复、搜索/筛选/分页、匿名跳转、权限与服务错误、CSRF 退出，以及 390px/375px 布局和键盘操作。测试数据只进入测试接口桩，真实 SSO 和正式业务数据由集成与本人验收确认。

设计依据：创作者原型独立216px业务侧栏、顶部面包屑、蓝色主操作和系统中文字体。桌面详情和编辑为两栏，手机使用可键盘关闭的导航抽屉。统一平台继续无侧栏。ui-ux-pro-max 的设计系统搜索在结构上偏向营销落地页，收窄内部表格查询后未得到可验证的产品匹配，未直接套用搜索配色/字体。采用其通用表格、可见标签、键盘焦点、移动端内部滚动及加载/错误反馈原则。
# 合同模块补充（2026-11-14）

新增 `/contracts`、`/contract-templates` 和创作者详情「合同记录」标签。支持Word/PDF原文件导入、腾讯模板配置/编号关联、草稿编辑与预览、管理员确认发起、状态同步和PDF归档下载。上传沿用现有CSRF封装，FormData由浏览器设置multipart boundary，不手动设置JSON Content-Type。

合同模板和合同数据来自真实后端；电子签未配置时展示接入状态，模板原文件仍可导入。浏览器测试中的接口桩仅用于测试，正式页面不提供模拟签署或演示账号。实际腾讯联调与服务端配置见 `../docs/contracts.md`。
