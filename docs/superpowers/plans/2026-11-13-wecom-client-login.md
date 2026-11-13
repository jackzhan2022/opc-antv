# 企微客户端免扫码登录 Implementation Plan

**Goal:** 企微内打开免扫码，普通浏览器保留扫码，沿用成员关联准入。

**Architecture:** 后端按客户端环境选择授权地址，使用同一安全回调。前端对未登录企微用户自动授权，并处理错误和主动退出以避免回环。

**Tech Stack:** Spring Boot / Spring Security、React / Umi、JUnit / MockMvc、Playwright。

按当前会话逐项执行。当前仓库无初始提交，已有工程为未跟踪文件；在用户现有分支就地修改，保留本次所改文件的私有副本，不全量暂存或创建空工作树。

- [x] 备份本次涉及的既有文件，添加后端授权入口及state回归测试，运行 `mvn -q test -Dtest=ProviderProtocolTest,WecomClientLoginTest,OAuthStateTest`，确认新增行为测试失败。
- [x] 扩展 `IdentityProvider` 的上下文重载、企微地址选择、Controller转交User-Agent和字母数字state；保持飞书兼容，运行所有后端测试。41项通过。
- [x] 添加 `frontend/tests/wecom-login.spec.ts`，覆盖企微自动登录、普通浏览器/微信不自动、错误页不自动、未开通、已登录、退出和手动重试。新增自动登录和退出测试先失败。
- [x] 修改 `pages/Login.tsx`、`components/PlatformHeader.tsx`，使新增测试通过；更新既有退出断言。前端类型检查、构建、25项Playwright通过。
- [x] 完成独立本机MySQL回归与后端打包，自审授权与会话边界；README已同步，部署与验证文档随发布结果更新。
- [x] 新建独立前后端发布目录，校验产物摘要；仅切换本平台软链并重启服务，不改数据库、密钥或Nginx。发布20261113-wecom-client成功，旧产物及发布记录保留。
- [x] 验证公网普通/企微User-Agent的授权参数、Secure Cookie、匿名接口和服务健康；记录真实企微客户端待本人验收的边界。所有检查通过；真人企微内授权仍待用户打开应用确认。
