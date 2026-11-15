# 信计 X 平台 / SinX Platform

公司内部统一登录与应用入口，以及独立的创作者资料管理系统。

| 工程 | 范围 | 说明 |
| --- | --- | --- |
| [sinx-platform](sinx-platform/README.md) | 统一登录、成员与身份关联、工作台、应用管理与成员授权、OAuth2授权服务 | 前后端独立工程 |
| [creator-platform](creator-platform/README.md) | 公司共享的外部编剧、剪辑等创作者档案与基础配置 | 独立前后端、数据库、部署 |
| [preview](preview/) | HTML交互原型 | 仅用于设计，含模拟数据 |
| [docs](docs/) | 需求、技术选择与实施记录 | 当前实现以各工程README和验证记录为准 |

前端采用 React / Umi / Ant Design，后端采用 Java 17 / Spring Boot / JDBC / Flyway / MySQL。各工程可分别拆仓。创作者通过统一平台 OAuth2 授权码与 PKCE 登录，不共享数据库账号或浏览器令牌。

本地启动与部署参数见各工程README。真实凭据放在忽略的本地配置或环境变量中；依赖、构建产物、数据库备份、运行日志与界面截图不提交。测试只使用专用测试库，禁止对生产数据库执行测试清理。
