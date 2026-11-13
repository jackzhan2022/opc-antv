# 创作者原型结构对齐实施计划

**Goal:** 恢复已讨论的创作者原型导航、列表、详情、分组表单与配置页面，保留现有统一登录。

**Architecture:** 创作者前端结构重排；独立配置模块与增量V2迁移；档案兼容扩展V3；统一平台保持现有接口。独立文件任务按subagent-driven-development执行并复核，主任务集成、测试和发布。不提交整个未初始化仓库。

**Tech Stack:** React18/Umi4/Ant Design5，Java17/Spring Boot4/JDBC/Flyway/MySQL。

- [x] 后端配置任务：新增dictionary包、V2迁移和DictionaryConfigurationTest；配置GET与ADMIN写入、历史值/职业预置、真实引用计数、唯一及乐观锁、配置审计。可修改CreatorSecurityConfiguration配置路由权限，不动业务包。
- [x] 前端配置任务：新增config-api.ts、Occupations/Tags/Roles页面与configuration.spec.ts，在.umirc.ts注册静态路由先于/:id；页面独立样式configuration.css。读取真实配置，失败可重试，不使用演示业务数据。
- [x] 前端结构任务：layouts/index.tsx/global.css/Registry/Detail/Editor/api.ts以及creators.spec.ts；按原型恢复壳层、两栏详情与表单、统计/更多筛选/URL保留状态，接入配置推荐值和新简介报价。
- [x] 主任务业务：在CreatorModels/CreatorService/CreatorController增加简介报价与兼容处理、筛选统计/filter-options及V3迁移；先补失败测试后实现。
- [x] 集成：接口对齐、类型检查、前端完整工作流和截图；H2/MySQL回归；独立需求/质量复核并修复。
- [x] 交付：更新文档；先备份创作者库与产物配置，再发布仅创作者更新；校验真实路由/服务/空或真实数据保留、统一平台服务不受影响，提供入口及实际验收边界。

