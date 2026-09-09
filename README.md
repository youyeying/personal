# 个人记录系统（Personal Record）

个人自用的生活记录系统：**记账 · 健康（体重）· 锻炼 · 饮食 · 学习 · 每日总结 · 周报 · 开发日志**，首页一屏看全貌，手机为主、桌面可用。

前端 Vue 3 + Element Plus，后端 Spring Boot 4 + MyBatis-Plus，数据库 MySQL 8。详细设计见仓库内三份文档（[前端设计文档](./前端设计文档.md) / [后端设计文档](./后端设计文档.md) / [数据库设计文档](./database/数据库设计文档.md)）。

## 更新日志（最新在上）

- **v2.5.4（2026-09-10 · 拆分收尾修复 + CORS 收紧）**
  - 修复 开发端端口与文档不符：`apps/admin-app/vite.config.ts` 5173→**5174**（README/前端设计文档/记忆均为 5174 口径；双站点同端口时谁后启动谁被挤到 5174，按文档打开 5174/admin/login 会踩坑）
  - 修复 新部署开发端无法登录：`application.properties.example` 补 `app.admin.username/password`（DataInitializer v2.5.0 起读配置创建开发账号，模板缺项时空库启动仅 warn 跳过）；README 第 2 步改「必改三处」
  - 修复 根目录一键脚本：`packages/shared` 补 `type-check` script（`npm run type-check --workspaces` 不再中断）；根 `package.json` 补 `test` 转发（26 例 Vitest 全仓可跑）
  - 修改 CORS 收紧：`addMapping("/**") + allowedOriginPatterns("*")` → 仅 `/api/**` + localhost 双端口四来源——双端均走 Vite 代理同源，纯纵深防御无功能影响
  - 修改 开发端三个模板页 .vue 内联样式 `.tmpl-page__ops` 收敛进 `templateManage.scss`（回归「.vue 不写样式内容」约定）
  - 清理 删除 `apps/user-app/package-lock.json` 拆分残留；AppLogo 清理 dev-log/operation-log 失效图标 key；.gitignore 补 `.workbuddy/`；后端设计文档两处 admin/admin123 过时描述修正
  - 同步 本工作区补齐上游 v2.5.1~2.5.3 认证加固（logout 按 site / 会话复用按 userType / 单飞锁复位 / vite 移除 changeOrigin）
- **v2.5.3（2026-09-09 · 静默刷新单飞锁修复）**
  - 修复 静置一段时间后必掉登录：共享层 tokenManager 的单飞锁 `refreshing` 在刷新完成后从未复位——下次 accessToken 过期再进刷新逻辑时，`if (refreshing) return refreshing` 直接复用上一次已 resolve 的旧 Promise，拿到**早已过期的旧 accessToken**（并未真正调 /auth/refresh），重放再 401 后被踢回登录页；表现为整页加载后只有第一次过期刷新正常，之后连续使用跨过第二个 15 分钟或静置几小时回来必要求重新登录。现 Promise 结束后复位单飞锁，每次过期都真正换新
  - 修改 版本号三处对齐 2.5.3
- **v2.5.2（2026-09-09 · 登录态稳定性修复）**
  - 修复 同浏览器双端反复掉登录：登录会话复用查询只按「用户+设备指纹」未按 userType 过滤——业务用户与开发账号 id 数值相同（同为 1）时两端共用并互相覆写同一行 `auth_session`（refreshTokenHash 被后登录端替换），另一端 Cookie 对不上库被反复踢登录；现按「用户+userType+设备」三条件隔离会话行
  - 修复 非 localhost 访问（127.0.0.1/局域网 IP/公网隧道）必掉登录：vite 代理 `changeOrigin` 把 Host 改写为 `localhost:8080`，后端 `/auth/refresh` 的 Origin 同源校验（Origin host vs Host host）必然不匹配抛「非法的跨域请求」；双端代理移除改写，Host 原样透传，校验恢复语义
  - 修改 修改密码撤销会话按 userType 过滤（同 id 数值场景不再误删开发端会话）；版本号三处对齐 2.5.2
- **v2.5.0（2026-09-08 · 认证隔离加固）**
  - 修复 另一端反复掉登录：logout 改按 `?site=` 只清本站点会话/Cookie，杜绝登出连座误删另一站点（同浏览器双 Cookie 共存）；refresh 并发宽容按调用站点 userType 过滤、限流放宽 60/分；前端跨 tab 同步 key 站点后缀隔离，双端完全互不影响
  - 新增 packages/shared 双端共享层（工具/token/request 工厂唯一实现），后续改一处两端生效
- **v2.5.0（2026-09-08）**
  - 新增 拆分用户端/开发端两套系统：前端 Monorepo 双站点（`apps/user-app` 用户端 + `apps/admin-app` 开发端）+ 后端 `admin_user` 开发账号表 + 开发端登录（`/admin/login`）+ 开发页（开发日志/操作日志/基础数据管理三种模板页），业务接口与开发向接口按用户类型隔离（AuthInterceptor + JWT userType）
  - 新增 双 Cookie 会话隔离（`refresh_token` / `refresh_token_admin`），/auth/refresh 按 `?site=` 读对应 Cookie 并加限流与并发宽容；RefreshToken 滚动 rotation、SHA-256 哈希落库
  - 新增 共享层 `packages/shared`：双端重复工具（format/fetchAll/daysSeries/confirm/theme/useECharts/mdDraft/validators）收敛唯一实现；认证与请求封装参数化工厂（createTokenManager(site)/createRequestApi），双端 vite alias 直引源码
  - 修复 双站点频繁掉登录：①前端双端共用 localStorage key 互相清空 token → 站点后缀隔离；②后端 refresh 宽容查询未按 userType 过滤（同 UA 串会话）→ 按调用站点过滤；③refresh 限流 20→60 次/分
  - 修改 锻炼负重动作消耗模型（哑铃/臂力棒）为「强度+做功」：MET 按速度比定强度（封顶 2.5×参考）+ 负重做功（重量×9.8×0.35m×次数÷25%），9.5kg×40次×30s ≈ 7.4 kcal（对齐豆包量级）；全站消耗展示统一 roundKcal 保留 1 位小数，消除浮点尾差（2453.2000000003 等）
- **v2.2.0（2026-09-05）**
  - 修改 主题 token 前缀全站改名 `--cb-` → `--sk-`：56 个变量（theme.scss 唯一定义处）值不变，58 个文件（.vue/.scss/.ts）所有 `var()` 引用 + 图表 cssVar 字面量统一迁移；明暗主题实测取值正确，前端/后端设计文档 token 名同步
  - 修复 图表容器窄屏横向溢出：全站 7 处 ECharts 容器（饮食统计/健康趋势/学习统计/记账趋势/锻炼分析×2/开发日志汇总）统一补 `overflow:hidden + min-width:0`，终止内部布局把面板/卡片撑宽的「两层横向」冒泡；297px 极窄实测全站无横向滚轮
  - 修改 版本号三处对齐 2.2.0（config.ts / package.json / pom.xml），后端打包产物名变为 backend-2.2.0.jar
- **v2.1.1（2026-09-05）**
  - 新增 饮食记录页支持按日期补记：餐次栏旁日期选择（默认今天，`max=今天` 限制未来），切到过去日期记录即补记到那天；横幅「今日已摄入」恒按今日口径，历史按天分组与统计自动纳入
  - 修改 开发日志「导入 md / 结束开发」体验：逐条录入失败不中断 + 成功/失败回执（失败条标红保留、修正后一键补录）；提交前本地预检（类型/模块/内容 ≤500 字，超长提前拦截）；导入自动过滤当天已录入的重复内容
  - 修复 预算与周缺口浮点误差（`55.919999…kcal` / `-1355.919999…kcal` 等长小数归整） 与 统计页进度条轨道起点未对齐（dist 行共享列轨）
- **v2.1.0（2026-09-05）**
  - 新增 周报模块 `/report`：本周/本月/上月三档周期，全前端聚合三组指标（记账收支结余 / 锻炼净消耗+饮食缺口÷7700 预估减脂+体重变化 / 学习时长+写总结天数+心情 Top），消耗口径与全站一致
  - 新增 命令面板 `Ctrl+K`：快捷（记一笔/打卡/记饮食/记学习/写总结）+ 导航（10 模块）+ 系统（切主题），搜索/↑↓/Enter/Esc 完整键盘交互
  - 新增 数据导出：个人中心「数据导出」弹窗，六模块 CSV（Excel 直开）+ 全量 JSON 备份
  - 修复 分页条窄屏换行：PagePager 公共组件按父级容器宽度三档自适应收缩（≥560 完整 / 340~560 去总条数 / <340 仅翻页），全站 7 个分页页面生效
  - 修改 版本号三处对齐 2.1.0（config.ts / package.json / pom.xml），后端打包产物名变为 backend-2.1.0.jar
  - 修改 死代码清理（utils/fileReader.ts 零引用删除）+ 数据库设计文档/schema.sql 补全同步（补 5 个表明细章节、消耗公式口径更新）
- **v2.0.0（2026-09-05）**
  - 新增 V2「书卷气」视觉升级全站落地：暖灰阶文本 + 耳语阴影 + 图表语义色 token；新公共组件 ProgressRing（环形进度）+ RecordHeatmap（GitHub 式热力图，饮食/锻炼/健康打卡三处复用）
  - 新增 饮食统计「营养达标」面板：日均摄入 vs 医学每日参考值（DRIs 2023：蛋白 1.2g/kg、脂肪 25% 供能、碳水 50%、钠 ≤2000mg、纤维 ≥25g）
  - 修复 缺口统计口径：当天没有饮食记录不计入缺口（累计缺口/周对照/日均摄入分母只按记录天，首页预估减脂同口径）
  - 新增 后端四件：springdoc 3.1.0 在线接口文档（/swagger-ui）、字典 Caffeine 缓存（写后失效）、@RateLimit 限流（登录/注册/上传）、数据库每日 03:00 定时备份（保留 14 份）
  - 新增 单元测试：前端 Vitest 26 例（MET/BMR 全公式）+ 后端 JUnit5 17 例（FoodService/限流/CSV 转义）
- **v1.36.0（2026-09-05）**
  - 新增 饮食独立模块 `/food`（侧边栏入口 + 主色 #7a8c3e）：记录/统计/历史三子页；食物字典 39 条预置（每100g 热量+蛋白/脂肪/碳水/钠/纤维）+ 自定义；六大营养实时预览；搜索 + 收藏 + 默认份量快速录入
  - 新增 整餐模板收藏：把常吃的一餐存为模板（如「工作日早餐」）一键复制到任意日期；历史页支持按天复制整餐到今日
  - 新增 目标摄入预算：每日预算 = 1.2BMR + 锻炼净 − 目标缺口（**缺口由用户自定义**，0=维持体重，负=增肌，饮食记录页横幅齿轮设置）；记录页实时显示「今日剩余额度」，吃超变红
  - 新增 能量结余统计：摄入 vs 实际消耗双折线（消耗复用锻炼口径）+ 周累计缺口 ÷ 7700 ≈ 预估减脂 + 缺口 vs 实际体重周对照（验证记录准确性）+ 餐次/营养构成 + 最爱食物 Top5
  - 修改 首页每日消耗卡升级为能量结余卡：左块本周实际日均 + 本周摄入 + 预估减脂；右块今日结余（预算−摄入）
  - 修复 后端编译环境：pom 显式 `useIncrementalCompilation=false`，规避 JDK 26 增量编译与 Lombok 的符号解析偶发问题
- **v1.35.0（2026-09-03）**
  - 废除 活动系数档位体系（auto 自动推荐 / manual 手动档，user 表 activity_level/activity_source 字段删除）：系数把运动消耗全天摊开导致 TDEE 虚高（散步多即被推荐 ×1.55），改为「每日实际消耗 = BMR×1.2 久坐基准 + 当天锻炼净消耗」
  - 修改 首页每日消耗卡：左块由「TDEE（BMR×活动系数）」改为「本周平均实际消耗」（周一~今天，周实际总消耗 ÷ 已过天数，含今天实时），右块「今日实际消耗」保留
  - 修改 个人中心身体数据弹窗移除活动系数自动/手动设置，仅保留年龄/身高/性别
  - 新增 锻炼独立模块 `/exercise`：自健康页迁出（健康页恢复体重三项——打卡/趋势/历史），侧边栏独立入口 + 主色 #b0653f，模块内四子页——打卡 / 统计 / 历史 / **消耗分析**（新）；App 二期规划健康+锻炼合并为一个 Tab 内分页（网易云音乐式）
  - 新增 消耗分析子页：按天查看**每天实际消耗 = BMR×1.2 久坐基准 + 当天锻炼净消耗**（BMR 取最新体重+体脂率；无体脂率退化为仅锻炼净消耗并提示）；区间汇总 + 每天锻炼净消耗柱状图 + 每天实际消耗折线图（久坐基准 markLine）+ 按天明细表；数据前端本地聚合，不新增后端接口（MET 口径唯一）
  - 修复 后端编译错误：AuthService.updateProfile 残留已删除字段（activityLevel/activitySource）的引用
- **v1.34.0（2026-09-02）**
  - 修复 散步距离精度：锻炼记录 distance 字段由 DECIMAL(4,1) 提升为 DECIMAL(6,3) 支持 3 位小数（输入 3.95 不再被四舍五入为 4.0，速度档位与消耗计算更准确）
  - 新增 锻炼·骑行动作：距离+分钟 → 平均速度 → Compendium 2024 道路骑行六档 MET（4.0~16.8），复用散步字段零新增库结构；速度 >50km/h 拦截防分钟填错；爬坡暂不记录（300m/20km 对等效距离影响 <0.1%，真实影响在速度档位，留待坡度方案）
- **v1.33.0（2026-08-31）**
  - 修改 全站首屏加载态审计修复：首页概览改不透明 LoadingMask 并按数据源拆分（主体/每日总消耗独立加载），身体数据弹窗活动系数推荐、记账/健康/学习最近记录速览、锻炼今日列表均改不透明遮罩，杜绝「先出页面后出数据」
  - 修复 记账概览日期范围：改为自然周（周一→今天）与自然月（月初→今天），不再按固定 30 天往前推（此前 8/31 会漏掉 8/1；2 月 28/29、30/31 天月均正确处理）
  - 新增 记账概览「可支配余额」（全量累计收入−支出，发薪自动+工资，不随范围跳变）+ 发薪日提醒（每月最后一个工作日，月底遇周末提前发放）
  - 修改 每日总结小汇总：「收入」行改为「锻炼净消耗」（收入非每日有，锻炼消耗更有参考价值；后端 summary 返回当日锻炼记录，前端按 MET 公式体重快照计算）
  - 修改 首页「今日收入」指标卡改为「可支配余额」（全量累计收入−支出，与记账概览口径一致）
  - 修改 记账概览默认展示「全部」范围（收入非每日有，默认全量概览；可支配余额不随范围变）
  - 修改 开发日志今日开发时长动态显示：侧栏 tag 与页面标题每分钟轻量轮询今日汇总（进行中会话时长实时增长，无需进入页面才刷新）
  - 修复 开发日志汇总柱状图偶发不显示：汇总视图 v-if 重建容器导致旧 ECharts 实例绑定失效 + 接口过快返回时容器宽高为 0 的竞态，改为切回时 dispose 重建 + nextTick 后渲染 + 短重试兜底
  - 重构 新增公共组件 RecordDetailDialog：5 个页面（操作日志/开发日志/学习历史/锻炼历史/体重历史）的「行详情弹窗」统一收敛；key-value 行 + 等宽/长文 + 具名插槽自定义（类型徽标/星级/附件链接），各页删除重复模板与样式
- **v1.32.0（2026-08-31）**
  - 新增 首页「每日总消耗」卡：双口径展示 TDEE（BMR×活动系数）与今日实际消耗（基础×1.2 + 今日锻炼净消耗）
  - 新增 锻炼记录净消耗公共函数 recordNetKcal（首页与锻炼组件复用，体重快照优先）
- **v1.31.0（2026-08-31）**
  - 新增 活动系数自动推荐：按近 7 天锻炼记录时长自动映射档位（参考 WHO/ACSM 运动建议），可一键采纳或手动覆盖（auto/manual 来源标记）
- **v1.30.0（2026-08-31）**
  - 新增 基础代谢 BMR（Katch-McArdle 公式：370+21.6×瘦体重，瘦体重由最新体重+体脂率计算），个人中心「身体数据」弹窗展示
  - 新增 活动系数档位 1-5（久坐1.2~极高1.9），BMR × 活动系数 = 每日总消耗 TDEE
- **v1.29.0（2026-08-31）**
  - 重构 锻炼消耗算法（速度→MET + 等效分钟双轨）：速度越快强度越高（MET 按 Compendium 官方条目值，恒≥1 净消耗不为负），总量按个数（等效分钟=个数÷参考速度）——做得多必多耗，用时填错不再虚高
  - 新增 动作速度上限 max_speed（世界纪录封顶防 MET 爆炸，自定义动作缺省参考速度×3）
  - 新增 爬楼梯按秒/层分档 MET（快爬 8.8 ~ 慢爬 4.2，两端锚定 Compendium 楼梯条目）
  - 校准 预置动作 baseMET 对齐 Compendium 官方值（高抬腿 7.5、臀桥/俯卧撑 3.8、平板 2.8），参考速度按个人平均节奏（高抬腿 48、臀桥 30 等）
  - 新增 锻炼记录体重快照（body_weight）：保存时记录当时体重，历史消耗固定不随当前体重变化
- **v1.27.1（2026-08-30 · 安全）**
  - 仓库默认账号脱敏：源码（`DataInitializer`）与各处文档的默认账号改为 `admin / admin123`；仅空库首次启动才会创建，本地已有账号不受影响
- **v1.27.0（2026-08-30）**
  - 新增 开发日志「汇总」Tab：近 7/30 天/全部 的开发时长、会话数、功能条数，按天柱状图 + 按模块分布 + 按类型计数
  - 修改 全项目「保存/删除后闪烁」：记账明细、健康历史、学习历史、每日总结、开发日志改为静默刷新（真实加载仍用不透明遮罩）
  - 修改 操作日志模块枚举补 EXERCISE（锻炼模块不再显示裸英文）
  - 修改 锻炼散步速度超常校验（>12km/h 拦截，防分钟误填算出怪数字）
- **v1.26.0（2026-08-30）**
  - 新增 健康·锻炼模块：动作字典 + 锻炼记录，前端打卡/统计/历史三子页
  - 新增 锻炼热量算法（MET 体系）：散步按速度定档、力量按强度系数，输出净消耗 + 总消耗
  - 新增 锻炼记录复制复用；时长按分钟+秒录入；臂力棒支持重量；俯卧撑/臂力棒默认双手
  - 新增 user 表身体数据字段（age/height/gender），个人中心可维护
- **v1.25.0（2026-08-29）**
  - 新增 首页概览真实数据落地（体重/锻炼/学习/支出概览）
  - 修改 个人中心交互重做（概要卡 + 设置列表），支持清除目标体重
- **v1.24.0（2026-08-27）**
  - 修改 各内容页响应式适配（根 padding 统一 clamp、列表降级多列卡片、隐藏次要列）
  - 新增 日期范围选择、DataList 响应式列隐藏 / 卡片化降级等公共组件能力
  - 更早版本变化见设计文档

## 技术栈

| 层 | 技术 |
|---|---|
| 前端 | Vue 3.5（`<script setup lang="ts">`）、Vite 8、TypeScript、Element Plus、Pinia、Vue Router、ECharts |
| 后端 | Java 26、Spring Boot 4.1、MyBatis-Plus、JWT + RefreshToken 双 Token |
| 数据库 | MySQL 8（utf8mb4） |
| 构建 | 前端 npm / 后端 Maven（自带 mvnw） |

## 目录结构

```
├── apps/                   前端（Monorepo，npm workspaces）
│   ├── user-app/           用户端（Vite 工程，端口 5173）
│   │   └── src/
│   │       ├── views/      业务页面（记账/健康/锻炼/饮食/学习/每日总结/周报/个人中心/首页）
│   │       ├── components/ 公共组件（DataList/PagePanel/DateRangePicker 等）
│   │       ├── api/        接口封装（自动带 token、统一报错）
│   │       └── utils/      工具（含 exercise.ts 锻炼热量算法）
│   └── admin-app/          开发端（端口 5174：/admin/login + 开发日志 + 操作日志 + 基础数据管理）
├── packages/
│   └── shared/             双端共享层（纯工具 + token/请求工厂，双端 vite alias 直引源码）
├── backend/                后端（Spring Boot，端口 8080）
│   └── src/main/resources/application.properties   ← 本地配置，自己创建（含开发账号 app.admin.*，见下）
├── database/
│   ├── schema.sql          数据库初始化脚本（建库 + 18 张表）
│   └── 数据库设计文档.md
└── *.md                    需求/前端/后端设计文档
```

## 环境要求

- **JDK 17+**（本项目 Java 26 实测通过）
- **Maven**：无需安装，用仓库自带 `mvnw`/`mvnw.cmd`
- **MySQL 8.0+**（本地 3306）
- **Node.js 20.19+**（Vite 8 要求）+ npm

## 快速开始

### 第 1 步：建数据库

```bash
mysql -uroot -p < database/schema.sql
```

脚本自带建库（`personal_record`，utf8mb4）。若已存在此库会直接复用，不会覆盖数据。

### 第 2 步：配置后端（重点）

**仓库不包含 `application.properties`**——里面是数据库账号密码等本机敏感信息，为避免泄露已通过 `.gitignore` 排除，请你自己创建一份：

```bash
cd backend/src/main/resources
cp application.properties.example application.properties
```

然后编辑 `application.properties`，必改三处：

```properties
# ======= 1. 数据源：改成你自己的 MySQL =======
spring.datasource.username=你的_mysql_用户名
spring.datasource.password=你的_mysql_密码
# 若数据库不在本机，把 localhost:3306 改成你的地址
spring.datasource.url=jdbc:mysql://localhost:3306/personal_record?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true

# ======= 2. 认证：部署到公网务必换掉 =======
app.jwt.secret=换成你自己的_jwt_secret_随机长字符串
# 本地 http 保持 false；HTTPS 部署改为 true
app.session.cookie-secure=false

# ======= 3. 开发账号：admin_user 空库首次启动时自动创建（不配置则开发端无法登录） =======
app.admin.username=你的_开发账号_用户名
app.admin.password=换成你自己的_开发账号_密码
```

其余项（端口、文件上传大小、逻辑删除等）一般无需改动。

### 第 3 步：启动后端

```bash
cd backend
./mvnw spring-boot:run          # Windows 用 .\mvnw.cmd spring-boot:run
```

- 后端跑在 `http://localhost:8080`
- **首次启动自动创建默认账号**（见下方），并预置记账分类、锻炼动作字典
- 前端上传的文件保存在 `backend/uploads/`（仓库已忽略）

> 提示：Java 26 + MyBatis-Plus 分页若有 final 字段反射警告，在 IDEA 启动配置 VM options 加
> `--enable-final-field-mutation=ALL-UNNAMED`

### 第 4 步：启动前端（Monorepo，两个站点）

```bash
# 根目录安装 workspace 依赖
npm install

# 用户端（端口 5173）
npm run dev --workspace @personal/user-app

# 开发端（端口 5174）
npm run dev --workspace @personal/admin-app
```

浏览器打开 **http://localhost:5173**（用户端）或 **http://localhost:5174/admin/login**（开发端，Vite 代理 `/api` → 后端 8080，无需额外配置）。

## 默认账号

| 站点 | 用户名 | 密码 |
|---|---|---|
| 用户端 `/login` | 业务账号（注册或既有 youyeying） | 见个人中心 |
| 开发端 `/admin/login` | `xyloveyh` | 配置于 `backend/src/main/resources/application.properties` 的 `app.admin.password` |

用户端业务账号注册即用；开发账号由后端启动时按配置自动写入 `admin_user` 表（admin_user 为空时），不出现任何 SQL 文件。开发账号登录后仅能访问开发日志/操作日志/基础数据管理，业务用户看不到这些页面。

> 安全提示：正式部署请务必修改 `application.properties` 中的开发账号密码与 JWT 密钥。

## 常见问题

| 问题 | 处理 |
|---|---|
| 后端 500 / 连不上数据库 | 检查第 2 步 `spring.datasource.username/password` 是否改成你自己的 |
| 改了后端代码没生效 | 后端无热更新，改完需重启 |
| 前端端口被占用 | Vite 会自动 +1；或改 `apps/{user-app,admin-app}/vite.config.ts` 的 `port` |
| 头像/附件无法上传 | 确认 `backend/uploads/` 目录可写；大小上限 10MB |
| 上传超过 10MB | 前后端均为 10MB 上限，需同时调整第 2 步配置与前端校验 |
| 我忘记数据库账号 | 用 MySQL root 执行 `ALTER USER 'root'@'localhost' IDENTIFIED BY '新密码';` 后同步改到 application.properties |

## 模块清单

记账 · 健康（体重打卡 + 体重趋势）· 锻炼（打卡 + 统计 + 历史 + 消耗分析）· 饮食（记录 + 统计 + 历史，目标缺口预算）· 学习（附笔记文件）· 每日总结（含班表）· 周报（跨模块周/月复盘）· 开发日志（多段会话 + 汇总）· 操作日志 · 个人中心（含修改密码 + 数据导出）· 首页概览