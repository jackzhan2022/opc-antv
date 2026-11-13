# 创作者平台原型结构对齐

用户要求继续开发，并明确问题是“创作者平台页面结构不一样”。本轮以已讨论的 `preview/creator-admin-preview.html` 和 `preview/creator-list.png` 为设计依据，恢复创作者自己的业务导航；统一管理中心保留无侧栏。

## 页面结构

- 桌面左侧216px导航：资源管理→创作者资源库；管理员的基础配置→职业分类、技能标签；系统设置→角色权限。底部返回信计X平台。顶部面包屑、真实成员姓名与退出；移动端抽屉导航，可键盘关闭。
- 资源库为原型紧凑工具栏、职业/城市筛选、更多筛选（标签、维护人、来源）、全部/最近更新（最近7天）/已归档真实计数、姓名头像与档案标识、职业、方向标签、城市、联系方式、维护人/更新日期、查看与编辑。URL保存筛选及页码，详情/编辑返回时恢复，不把联系方式关键词放持久浏览器存储。
- 详情为左侧档案卡（人员档案/修改记录），分基础资料、职业与能力、作品、备注；右侧维护信息、参考报价、复制摘要、归档恢复。只渲染真实数据；不使用原型样例人名/数值。
- 编辑为左侧分组表单，右侧四段锚点目录；基础资料、职业与能力、代表作品、补充信息；底部保存/取消。新增个人简介、人民币参考报价及计价单位；报价与单位同时填写或同时为空。
- 职业分类为左侧真实职业列表与使用数量、右侧职业名称/说明/启用管理；技能标签为分类导航和标签卡片，支持新建/编辑/启停。保持既有自定义职业和标签兼容，配置作为推荐选项，不破坏已登记历史字符串。
- 角色权限页展示当前已实现的两种角色规则与统一平台成员管理入口，不伪造可编辑的独立角色引擎。普通成员可维护公司共享档案，管理员额外维护职业标签配置。

## 数据与API契约

原有业务与认证API兼容。Creator/Input新增可选 `intro:string`（最多2000）、`quoteAmount:number|null`（0～9999999999.99，最多2位小数）、`quoteUnit:string`（条/集/分钟/天/项目）；原数据读取为空，旧客户端省略新字段时更新保留已有值，不能意外清空。审计只记录变更字段名。

列表新增 `maintainerId`、`source`、`recent`，recent为最近7天更新；`GET /creator-api/creators/summary`返回 `{all,recent,archived}`，其值按当前非tab筛选条件统计。`GET /creator-api/creators/filter-options`返回已有真实 `{cities:string[],sources:string[]}`，维护人沿用/members。

配置 `GET /creator-api/dictionaries?kind=PROFESSION|TAG|SOURCE&includeDisabled=true|false` 返回数组；每项 `{id,kind,name,category,description,enabled,version,usageCount}`。POST同路径，PATCH `/dictionaries/{id}`，写入仅ADMIN且需CSRF。Input `{kind,name,category,description,enabled,version?}`。name对职业/标签50字、来源200，category50，description500。分类为自由文本，默认“专业能力”；kind不可更改；已被引用项改名返回409 `dictionary_in_use`（可改说明/分类/启用），同kind名称唯一，冲突409 `dictionary_name_exists`，版本冲突409 `version_conflict`。不存在404 `dictionary_not_found`。不提供物理删除。已用数量查询真实业务表，来源读creator_record.source。

新增Flyway V2配置表，由配置模块初始化实际职业/标签/来源历史值并预置职业分类（真实字典配置，不是演示创作者）；V3补档案简介/报价。线上仅增量迁移，禁止修改V1或清库。配置写入以数据库守卫串行处理名称/引用检查并记录配置审计。

## 交付与验证

前端从原型结构而非简化顶栏重新检查；测试普通成员和管理员导航、配置增改启停、结构与原功能、移动布局、回退筛选。后端测试配置权限/CSRF/唯一/版本/引用、记录新字段及旧客户端兼容、真实统计筛选。H2与专用MySQL回归，浏览器构建测试，独立复核后备份创作者库并只更新创作者服务/静态产物。保留原平台登录配置、产物和数据库。
