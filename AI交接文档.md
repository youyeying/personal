# AI 开发交接文档（个人记录系统）

> 本文档供接手开发的 AI 完整阅读。请先理解「你是谁、在为谁开发、这是什么气质的项目」，再动手写任何一行代码。**设计理念章节（第三节）是本文档的灵魂，请逐字读完。**

---

## 一、你的角色与协作方式

### 1.1 角色设定
你的名字叫**小幽**，快手效率工程部门的 AI 助手，与 Kim（协同办公 IM）、Docs 深度打通。你定位是**全栈开发 + UI 设计一体化**助手：既能产出可运行代码，也能输出专业 UI 思路、设计规范、组件方案。气质：严谨务实、高效直接，兼顾工程落地和视觉体验；优先给可直接复制运行的代码，UI 方案清晰可落地，不做脱离开发现实的空想设计。

### 1.2 协作者（用户）画像
- **全栈开发者**，日常同时负责后端开发、前端页面、UI 实现和组件开发——不需要科普基础概念，直接上方案和代码
- 技术栈：前端 Vue3/React/Nuxt3/TS/TailwindCSS/Element Plus；后端 SpringBoot/MyBatis-Plus/MySQL/Redis
- 审美：**简约、现代、专业、克制**；讨厌花哨难维护的效果
- 偏好：UI 方案必须可开发落地，不能脱离前端技术限制；先给方案思路再写代码；同一需求可给多套方案写明优缺点

### 1.3 沟通规则（强约定）
- **全程中文回复**（代码、变量名、技术标识符保持英文；技术名词可用中文括注）
- 需求信息不足时精准列出缺失条件，不无依据猜测；非关键细节用合理默认值并明确说明
- 只提 UI 需求时：先输出结构思路，再输出可直接使用的前端代码
- 优先解决根因，不做表面补丁；保持改动聚焦，不顺手修无关问题
- 修复 bug 优先追根因；代码可读性优先，注释恰到好处不过度冗余
- 小改动（非大改）不需要浏览器验证，编译/type-check 通过即可交付
- 交互反馈要明显，让用户能感知操作效果（历史教训：按钮点了没反应会被反馈）

---

## 二、项目概述

**个人记录系统**（单体全栈，目录 `e:\personal`）：

| 层 | 技术栈 | 目录 |
|---|---|---|
| 前端 | Vue3 `<script setup lang="ts">` + Vite + Element Plus + ECharts + SCSS | `e:\personal\front` |
| 后端 | SpringBoot + MyBatis-Plus + MySQL（用户在 IDEA 中手动运行） | `e:\personal\backend` |
| 文档 | 需求/设计/数据库文档 + 开发日志 | `e:\personal\database`、根目录 |

**业务模块**（独立路由页面，侧边栏平级入口）：首页概览、记账、健康、锻炼、饮食、学习、每日总结、周报、操作日志、开发日志、个人中心。

**核心数据口径**（改动前必须理解）：
- 锻炼消耗按「记录时体重快照」计算，历史不随当前体重变化
- 每天实际消耗 = 1.2×BMR（久坐基准）+ 当天锻炼净消耗
- 饮食预算 = 1.2×BMR + 今日锻炼净消耗 − 目标缺口（dietTargetGap，范围 -9999~3000，用户自定义）
- 缺口 ÷ 7700 ≈ 预估减脂 kg

---

## 三、设计理念：「书卷气」（本文档的灵魂）

### 3.1 一句话内核

**这套 UI 是一本纸质手账，不是一个 SaaS 后台。**

用户每天在这里记体重、记饭、记锻炼、记账——这是「写手账」的行为，界面应该像一张安静温柔的纸，让记录这件事有仪式感、不吵闹。所有设计决策都从这个隐喻出发。

### 3.2 起源

整套视觉从**登录页壁纸**中提取三色构建：暖米白（纸）、陶土棕（暖棕红）、墨绿灰（暗部）。之后所有页面都延续这套「从壁纸长出来」的颜色，保证全站一体。

### 3.3 隐喻系统：每个 token 都对应一种纸的物理属性

定义在 `front/src/styles/theme.scss`（全站唯一定义处，禁止在别处硬编码颜色）：

```scss
--sk-canvas: #f2ede4;      // 页面背景 = 纸（暖米白，不是纯白）
--sk-surface: #faf7f0;     // 卡片背景 = 纸面（比背景亮半档，"纸叠纸"层次）
--sk-hairline: #e0d8ca;    // 分隔线 = 纸纹（极浅暖棕，几乎只是暗示）
--sk-ink: #2c312f;         // 主文本 = 墨（墨绿灰，不是纯黑，墨水落在暖纸上自然偏暖）
--sk-primary: #a8765a;     // 主行动色 = 陶土棕（全站唯一，暖棕红）
--sk-error: #b04a3a; --sk-warning: #a8821f; --sk-teal: #3f7a72; --sk-success: #3d7a55;  // 语义色：全部压暖、降饱和，像植物颜料而非荧光笔
```

**理解要点**：
- 关键词是**暖**。纸是暖的、墨是暖的、绿和蓝都往灰暖里压。这是和普通「白底蓝字后台」的根本区别
- 阴影极轻（`0 1px 3px rgba(26,25,23,.06)`）——纸叠在纸上不会产生戏剧性投影，V2 还要更轻（「耳语阴影」）
- 圆角克制：按钮 8px / 卡片 12px，**不做超大圆角和胶囊按钮**（那是塑料感，不是纸感）

### 3.4 字体三线策略

```scss
--sk-font-family: 'LXGW WenKai Screen', ...   // 正文：霞鹜文楷（手写楷体，书卷气的核心载体）
--sk-font-cute: 'ZCOOL QingKe HuangYou', ...   // 装饰：站酷庆科黄油体（仅加载/庆祝等圆润场景）
--sk-font-mono: 'JetBrains Mono', ...          // 数据：等宽（所有数字、日期、热量、时间码）
```

- **霞鹜文楷是气质的根**——手写楷体让整个界面像「写」出来的账本，换掉它等于换掉灵魂
- **数字必须等宽 + tabular-nums**（`.num` 类）：数据对齐是手账的可信感来源
- 字号响应式：clamp 手机 16px → 34 寸 26px，层级紧凑（标题与正文差距 ≤1.6 倍）——纸上的字不会悬殊太大

### 3.5 色彩纪律：一主 + 模块各色 + 语义四色

1. **全站唯一主行动色**：陶土棕 `--sk-primary`，只有它承担「主按钮/主强调」
2. **模块主色机制（重要）**：`--sk-mod` 由 `Home.vue` 布局层按当前路由写入 `.app-layout` 容器（定义在 `store/theme.ts`），组件内一律 `var(--sk-mod)` + `color-mix(in srgb, ...)` 派生深浅：

| 模块 | accent | | 模块 | accent |
|---|---|---|---|---|
| 首页 | `#a8765a` | | 饮食 | `#7a8c3e` 暖黄绿 |
| 记账 | `#c08a3e` | | 学习 | `#4f7a8c` |
| 健康 | `#3f7a72` | | 每日总结 | `#a8716a` |
| 锻炼 | `#b0653f` | | 操作日志 | `#6b7a66` |
| 个人中心 | `#8a8a80` | | 开发日志 | `#5f7a8c` |

   全部是**暖色系内协调**的低饱和色（蓝绿都压灰），与画布/墨色融合
3. **语义色克制稳定**：成功绿/错误红/警告金/消耗 teal——只用于状态表达，不当装饰

### 3.6 明暗主题：两套都是暖的

深色模式不是「冷黑科技风」，而是**深暖墨**（`html.dark`）：画布 `#1d1b17`（深暖墨）、卡片 `#272420`、主色提亮一级仍是陶土系。token 层覆盖即可整体换肤；但 **ECharts 是 canvas 渲染，不解析 CSS 变量**——必须用 `cssVar()` 取实际值 + `watchTheme()` 重绘（见 `utils/theme.ts`、`utils/useECharts.ts`）。

### 3.7 动效哲学：克制、短、只服务理解

- 列表更新用 `TransitionGroup`：新增条目淡入下落 0.25s、删除淡出上移——像纸条被轻放/抽走
- **禁止**花哨入场动画、视差、大面积动效——纸是安静的
- 加载遮罩**必须不透明**（表面色覆盖）；「静默刷新」只用于保存/删除后的后台联动，真实加载路径不许半透明闪烁

### 3.8 反面清单（做了必返工，都是和「纸」矛盾的科技炫技）

- ❌ 紫色渐变、通用 AI 模板感
- ❌ 过度悬浮卡片、戏剧性投影
- ❌ 超大圆角、胶囊按钮、高字重标题
- ❌ emoji 代替功能图标（图标一律 SVG / lucide / Element）
- ❌ 组件内硬编码色值
- ❌ 荧光色、高饱和撞色

### 3.9 V2 演进方向（设计稿已出，等用户确认后落地）

往「更静、更纸、更有数据叙事感」推进，三个参考对象（都和书卷气同路）：

1. **Notion 暖色极简** → 暖灰阶文字 `rgba(28,30,26,.88/.62/.42)` 替代墨绿灰（从「冷墨」变「暖纸墨」）；2 层耳语阴影
2. **fitness-photo-calorie Logbook 美学** → 30 天记录热力图（GitHub 式格点，色随模块主色深浅，把「记录」变成可看见的坚持）；环形进度（Apple Watch 心智，SVG 无依赖）
3. **Kalo 语义色 token** → 图表维度固定色全站一致：
   - `--c-intake: #7a8c3e` 摄入 / `--c-burn: #3f7a72` 消耗 / `--c-gap: #3d7a55` 缺口正向 / `--c-over: #b04a3a` 超标
   - 三大营养素：蛋白蓝 `#4a6fa5` / 脂肪橙 `#c9862f` / 碳水紫 `#8a5fa0`

**V2 已于 2026-09-05 全站落地**（原设计稿仅作历史参考）：token 暖灰阶+耳语阴影、ProgressRing 双环、RecordHeatmap 30 天热力图、图表语义色 token（--c-intake/--c-burn/--c-gap/--c-over + 蛋白蓝/脂肪橙/碳水紫）均已进生产代码。

---

## 四、设计习惯（用户强约束，最高优先级）

1. **先出设计稿给用户确认，再落地代码**（流程不可倒置）
2. UI 方案优先考虑可开发落地，不脱离前端技术限制
3. 响应式覆盖桌面/平板/移动端；各页面有最小宽度约定（开发日志 550px、健康 450px、饮食 485px）；窄屏时环缩小至 64px、热力图截断 21 天
4. 保存/删除后的列表联动：优先「本地插入 + 静默刷新」，避免整表重拉闪烁
5. 数据库业务表优先软删除、审计字段、历史保留；敏感操作明确校验
6. 后端 Controller → Service → Mapper 分层、REST 规范、统一响应、业务异常 + 全局异常处理
7. 代码、需求、数据库、设计说明保持同步；文档说明文件职责和关键方法

### 工程结构约定
- `.vue` 文件**不写 `<style>` 块**；页面样式放 `views/<module>/<module>.scss` 由根组件 `@use` 引入；公共组件样式放组件目录内
- 类名用模块前缀 + BEM 式：饮食 `fd__/fdi__/fds__/fdh__`、锻炼 `exi__/exa__` 等，选择器隔离避免全局污染
- 可复用逻辑抽 `utils/`（纯函数）或 `composables/`；通用 UI 抽 `components/`
- 组件不强行拆分：按复用性 + 复杂度判断，简单元素不组件化
- API 一律走封装层

---

## 五、当前任务进度（截至 2026-09-05 深夜，V2 已全站落地）

### 5.1 今日已完成（7 轮，全部 type-check / mvnw test / 浏览器实测通过）

1. **V2「书卷气」视觉全站落地（APP_VERSION 2.0.0）**：
   - token 层：暖灰阶文本 rgba(28,30,26,.88/.62/.42) + 2 层耳语阴影 + 图表语义色（--c-intake 摄入 #7a8c3e / --c-burn 消耗 #3f7a72 / --c-gap #3d7a55 / --c-over #b04a3a / 蛋白蓝/脂肪橙/碳水紫，暗色自动提亮）
   - 新公共组件：`components/ProgressRing/`（SVG 环形进度，超预算自动红，≤560px 缩 64px）+ `components/RecordHeatmap/`（GitHub 式格点，五档色阶随模块主色，≤560px 截 21 格）
   - 逐页接入：饮食记录页横幅双环+页尾 30 天双行热力图、统计页语义色+营养达标面板（DRIs 2023 医学参考：蛋白 1.2g/kg、脂肪 25% 供能、碳水 50%、钠 ≤2000mg、纤维 ≥25g）、首页能量结余卡摄入环、锻炼分析页 --c-burn+热力图+「连续锻炼 N 天」成就、健康打卡页 30 天热力图（含体脂更深一档）
2. **饮食功能补齐**：食物编辑（GroupedChips 加 showEdit 铅笔，自定义食物弹窗新增/编辑两用+5 营养素字段）；历史修改弹窗食物/动作下拉换 el-select filterable 可搜索+默认份量带出；统计口径修正（**当天没有饮食记录不计入缺口**，DayRow.counted 标记，首页预估减脂同口径）；统计页行布局修复（值列 minmax+nowrap 不再换行）
3. **窄屏适配补齐**：每日总结弹窗 560px 降单列；锻炼/学习历史 DataList cardBelow=560 卡片化（学习弃 hideBelow）；饮食历史两行卡片重排
4. **数据导出**：后端 ExportController/ExportService（GET /export/{module}.csv 六模块 CSV + /export/all.json 全量备份）；前端 api/export.ts + 个人中心「数据导出」弹窗
5. **周报页 /report**：侧边栏「周报」入口（主色 #8a6a4f），本周/本月/上月，全前端聚合三组指标（记账收支结余 / 锻炼+饮食缺口+体重 / 学习+心情）；已修复收支方向 bug（type===2 才是收入）
6. **命令面板 Ctrl+K**：`components/CommandPalette/`，快捷 5 项+导航 10 模块+切主题，搜索/↑↓/Enter/Esc 完整键盘交互；主题切换抽 utils/theme.ts 共享
7. **后端四件**：springdoc 3.1.0 接口文档（/swagger-ui/index.html，全局 Bearer）；字典 Caffeine 缓存 5 分钟（foodItems/exerciseItems/expenseCategories，写操作 @CacheEvict）；@RateLimit 限流（登录 5/分、注册 3/分、上传 10、5/分）；数据库每日备份（database/backup-db.ps1 + 计划任务 PersonalRecordDBBackup 每日 03:00，备份至 database/backups/ 保留 14 份）
8. **单元测试**：前端 Vitest（npm run test，26 例：MET/BMR 全公式）+ 后端 JUnit5+Mockito（mvnw test，17 例：FoodService 校验/限流窗口/CSV 转义）
9. **收尾四件（深夜补做）**：① 分页条窄屏换行修复——PagePager 公共组件 ResizeObserver 观察父级容器三档收缩 layout（≥560 完整 / 340~560 去 total / <340 仅翻页，**必须观察父级**：各页 __pager 父容器 flex+右对齐会把自身 shrink 到内容宽，观察自身会死循环），浏览器实测三档单行；② 死代码清理——全项目组件/api/store 均有引用，唯 utils/fileReader.ts 零引用已删除；③ 版本号三处对齐 2.1.0（config.ts / package.json / pom.xml，后端 jar 名变为 backend-2.1.0.jar）；④ 数据库设计文档 + schema.sql 补全（版本对齐 v2.1.0、user 表补 diet_target_gap、补 development_session/feature_log/饮食三表共 5 个缺失明细章节、消耗口径更新为 v1.29.0 新公式）

### 5.2 待办（很少了）

1. 开发日志录入：今日功能记录已含全部 9 轮（含深夜收尾四件），用户自行「导入 md」收尾
2. PWA（手机桌面图标+离线）：**用户明确暂不做**，等 H5 二期（App 端信息架构已有规划，见前端设计文档 5.13）
3. 换机注意：备份计划任务注册在当前电脑，新机器需重新 `schtasks /Create`（见 7.3）；GitHub 仓库 Public，注意不要提交 application.properties / 锻炼.md / database/backups/
4. 分页条若后续有新页面接入：直接用 PagePager 组件即自带窄屏自适应，无需单独处理

---

## 六、关键组件/工具速查

| 名称 | 位置 | 用途 |
|---|---|---|
| `useECharts` | `utils/useECharts.ts` | ECharts 生命周期（宽 0 保护/resize/主题重绘/dispose） |
| `useTrendChart` | `utils/useTrendChart.ts` | 趋势图样板（色板含语义色 intake/burn/gap/over + 轴 + 网格） |
| `cssVar` / `watchTheme` / `toggleDarkTheme` | `utils/theme.ts` | 取 CSS 变量实际值 / 明暗监听 / 主题切换（ThemeToggle 与命令面板共用） |
| `GroupedChips` | `components/GroupedChips/` | 分组 chips（groups/activeId/showFav/showEdit/addLabel；select/toggle-fav/edit/add） |
| `ProgressRing` | `components/ProgressRing/` | SVG 环形进度（value/max 超量封顶变红/ringColor/label/size） |
| `RecordHeatmap` | `components/RecordHeatmap/` | 记录热力图（rows 多行多维度/level 0-4 色阶随 --sk-mod） |
| `CommandPalette` | `components/CommandPalette/` | Ctrl+K 命令面板（快捷/导航/系统三组命令） |
| `MetricCard` | `components/MetricCard/` | 指标卡（tone: ''/ok/err/accent） |
| `BlockTitle` | `components/BlockTitle/` | 卡片标题（title + hint + #aside 插槽） |
| `LoadingMask` | `components/LoadingMask/` | 不透明加载遮罩 |
| `RecordDetailDialog` | `components/RecordDetailDialog/` | 列表行详情弹窗（多页复用） |
| `DataList` | `components/DataList/` | 泛型列表（hideBelow 列隐藏 / cardBelow 卡片化降级） |
| `recordNetKcal` | `utils/exercise.ts` | 单条锻炼记录净消耗（体重快照优先） |
| `fetchAllRecords` | `utils/fetchAll.ts` | 循环翻页取全量（后端每页上限 100） |
| `fillDaysRange` / `groupByDate` | `utils/daysSeries.ts` | 日期补齐 / 按天聚合 |
| 后端 `ExportService` | `service/ExportService.java` | CSV 六模块导出 + JSON 全量备份 |
| 后端 `RateLimit` | `common/RateLimit.java` + `interceptor/RateLimitInterceptor.java` | 注解式限流（IP+方法 60s 窗口） |

---

## 七、工程协作约定（每轮开发必须执行）

### 7.1 文档三件套
1. 功能设计先落需求规格文档（新增模块先补条目）
2. 实现后同步 前端设计文档.md / 后端设计文档.md（版本号递增，`front/src/config.ts` 的 APP_VERSION 同步）
3. 收尾创建「今日功能记录-YYYY-MM-DD.md」，格式 `### [类型] 模块 标题` + 描述行；类型只允许 **新增/修改/删除/修复**；**每条合并后 ≤500 字**（后端 varchar(500)，超限会中断批量录入）

### 7.2 开发日志工作流（每天结束必须）
```
POST /api/dev/session/start   （当天有进行中会话则复用）
POST /api/dev/features       （逐条录入，可从 md 草稿「导入 md」解析）
POST /api/dev/session/end     （结束当天会话）
```

### 7.3 运行环境
- 后端在 IDEA 中由用户手动运行（**Spring Boot 4.1 + Java 26**，分页需 VM 参数 `--enable-final-field-mutation=ALL-UNNAMED`）；AI 只改代码，**需要重启/执行 SQL 时提醒用户手动处理**
- `application.properties` 已 gitignore（含凭证），模板在 `.example`
- 文件上传上限 10MB，MaxUploadSizeExceededException 返回 400
- GitHub 仓库为 Public；锻炼.md（纪录速度/MET 数据）、database/backups/ 不允许上传
- 接口文档：后端启动后浏览器开 `http://localhost:8080/swagger-ui/index.html`（springdoc 3.1.0，右上角 Authorize 粘 accessToken 可试调需登录接口）
- 测试命令：前端 `npm run test`（Vitest 26 例）/ `npm run type-check`；后端 `.\mvnw.cmd test`（JUnit 17 例）/ `.\mvnw.cmd compile`
- **数据库每日备份**：`database/backup-db.ps1`（凭证自动读 application.properties，mysqldump 导出 gzip，保留 14 份）；计划任务 `PersonalRecordDBBackup` 每日 03:00 自动执行，备份落在 `database/backups/`；**换电脑需重新注册**：
  ```
  schtasks /Create /TN "PersonalRecordDBBackup" /TR "powershell.exe -ExecutionPolicy Bypass -File <项目路径>\database\backup-db.ps1" /SC DAILY /ST 03:00 /F
  ```
  （ps1 文件必须 UTF-8 带 BOM，否则 Windows PowerShell 按 GBK 解析中文注释报语法错）
- 限流已生效：登录 5 次/分、注册 3 次/分、附件上传 10 次/分、头像上传 5 次/分（超限 429「操作过于频繁」）

---

## 八、避坑清单（历史教训，改动前自查）

- MET 公式必须用 `MET = 1 + (baseMET−1)×速度比`（过原点线性会算出负消耗）
- 锻炼消耗的「时长」不能同时做强度乘子和消耗乘子（会矛盾：短时高速反而消耗少）
- 分页内嵌套分页（两级 Tab）移动端体验差，不采用
- ECharts 容器 v-if 重建时先销毁旧实例；宽 0 时 250ms×3 重试兜底
- 操作日志 module 字段前端需有中文映射（MODULE_LABELS），否则显示裸英文
- 保存后重算预算用 watch(TARGET_GAP) 避免 F5 时序竞态
- 前后端文件大小限制要一致（曾因前端 2MB/后端 1MB 导致 500）
- 列表关键字段（日期/类型/时间）固定宽度不省略，仅内容列可省略
- **记账 type 口径：1 支出 / 2 收入**（周报页曾写反，工资被算进支出；跨模块聚合先查源模块 API 定义）
- **统计行布局禁止固定值列宽**：fds__dist-row 用 minmax(64px,max-content)/minmax(72px,1fr)/max-content + nowrap（固定 90px 曾导致长文本全换行）
- **当天没有饮食记录不计入缺口**（用户确认口径）：DayRow.counted 标记，日均/周对照/预估减脂均只按记录天
- **后端 Spring Boot 4.1 无 jackson-databind（Jackson 3 改包名）**：Controller 手动 ObjectMapper 编译不过，返回 Map/实体由 Spring MVC 自动序列化
- **ps1 脚本必须 UTF-8 带 BOM**（否则 Windows PowerShell 5.1 按 GBK 解析中文报语法错）
- springdoc 3.1.0 首次下载 jar 可能损坏（zip empty）：删 `~/.m2/repository/org/springdoc` 重下
- Mockito strict 模式：未用到的 stub 报 UnnecessaryStubbing——stub 挪进用例内按需打

### ⚠️ 编辑回滚问题（本项目最高频的坑，2026-09-05 单日 15+ 次）
- **症状**：Edit 工具返回「成功」但文件实际未变（import 行、interface 字段、token 段、pom 属性均中过招）
- **防御**：① 每次编辑后立即 `Select-String` 验证关键内容落盘；② 全量重写用 Write（从未回滚）替代多次小 Edit；③ 每轮结束跑 type-check / mvnw test 兜底
- **隐蔽变体**：模板有标签但 import 丢失时 Vue3 静默忽略不报错——**新增组件挂载后必须浏览器实测一次**

---

## 九、下一步行动指引

1. 开发日志收尾（用户操作）：开发日志页「导入 md」录入今日功能记录，结束会话后删除临时 md
2. 若接手新需求：先读 `需求规格文档.md` 与「前端/后端设计文档.md」对应章节，按第四节设计习惯执行（先方案后代码）
3. 长期方向（用户已确认）：PWA 暂缓等 H5 二期；成就体系不优先（书卷气克制）；其余无排期
4. 每轮开发固定动作：改完跑 `npm run type-check`（改公式加跑 `npm run test`；后端 `mvnw test`）→ 编辑点 Select-String 验证落盘 → 文档三件套同步 → 今日功能记录追加

---

*交接人：小幽（TraeCode）· 2026-09-05 深夜 · V2 落地完成之际*
