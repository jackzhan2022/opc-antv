# 创作者平台第一版 Implementation Plan

**Goal:** 从统一工作台免重复扫码进入独立创作者系统，完成共享档案的增改查与归档。

**Architecture:** Spring Authorization Server OAuth2授权码与opaque token；独立Spring OAuth2 Client服务逐请求核对成员状态；独立MySQL业务表和Umi前端。完整API和接口类型见同日设计文档。

**Tech Stack:** React18/Umi4/Ant Design5，Java17/Spring Boot4/Spring Security7/JDBC/Flyway/MySQL。

当前仓库无初始提交，不全量暂存用户现有文件。按subagent-driven-development拆分互不重叠目录，主任务负责登录交接、集成、复核与部署，子任务分别完成业务服务和前端。

- [x] 核对Spring官方授权服务器配置、JDBC表和客户端API，锁定兼容依赖；保存设计和固定API契约。
- [x] 业务后端先写失败测试，再实现 creator-platform/backend 下独立档案、职业、标签、作品、审计表与查询/写入服务；运行 `mvn -q test`。
- [x] 前端先写工作流测试，再实现 creator-platform/frontend 的列表、编辑、详情和操作记录；使用ui-ux-pro-max并沿用已有克制风格；运行类型检查、构建及Playwright。
- [x] 主任务扩展统一平台的标准OAuth2授权服务、JDBC持久化、固定客户端、内省成员版本校验、登录后授权续接和工作台真实应用入口；添加并通过安全边界测试。
- [x] 主任务完成创作者OAuth2客户端与独立会话、逐请求内省、成员目录代理和本应用退出；测试401/CSRF/撤销与身份角色更新。
- [x] 集成三项产物，完成H2和专用本机MySQL回归、前端完整工作流、两系统真实HTTP授权协议测试；修复独立审查问题，确认不生成生产假数据。
- [x] 更新运行文档和部署方案；准备独立配置、数据库、服务和路径，先备份再发布，只更新本域名的必要路由，检查其他Nginx配置不变。
- [x] 线上验证健康、静态产物、安全Cookie、匿名拦截、授权跳转和业务表空状态；记录本人真实登录/业务操作待验收边界，提供入口。


完成记录：20261113-creators已发布，公网只读检查通过。真实企微成员到创作者的上线后人工验收待本人进行；没有创建生产演示资料。验证细节见creator-platform/docs/verification.md。
