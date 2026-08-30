# 个人记录系统（Personal Record）

个人自用的生活记录系统：**记账 · 健康（体重 + 锻炼）· 学习 · 每日总结 · 开发日志**，首页一屏看全貌，手机为主、桌面可用。

前端 Vue 3 + Element Plus，后端 Spring Boot 4 + MyBatis-Plus，数据库 MySQL 8。详细设计见仓库内三份文档（[前端设计文档](./前端设计文档.md) / [后端设计文档](./后端设计文档.md) / [数据库设计文档](./database/数据库设计文档.md)）。

## 更新日志（最新在上）

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
├── front/                前端（Vite 工程，入口 src/index.ts，端口 5173）
│   └── src/
│       ├── views/        页面（记账/健康/学习/每日总结/开发日志/操作日志/个人中心/首页）
│       ├── components/   公共组件（DataList/PagePanel/DateRangePicker 等）
│       ├── api/          接口封装（自动带 token、统一报错）
│       └── utils/        工具（含 exercise.ts 锻炼热量算法）
├── backend/              后端（Spring Boot，端口 8080）
│   └── src/main/resources/application.properties   ← 本地配置，自己创建（见下）
│   └── src/main/resources/application.properties.example   ← 配置模板（仓库内）
├── database/
│   ├── schema.sql        数据库初始化脚本（建库 + 12 张表）
│   └── 数据库设计文档.md
└── *.md                  需求/前端/后端设计文档
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

然后编辑 `application.properties`，只改两处：

```properties
# ======= 数据源：改成你自己的 MySQL =======
spring.datasource.username=你的_mysql_用户名
spring.datasource.password=你的_mysql_密码
# 若数据库不在本机，把 localhost:3306 改成你的地址
spring.datasource.url=jdbc:mysql://localhost:3306/personal_record?useUnicode=true&characterEncoding=utf8&useSSL=false&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true

# ======= 认证：部署到公网务必换掉 =======
app.jwt.secret=换成你自己的_jwt_secret_随机长字符串
# 本地 http 保持 false；HTTPS 部署改为 true
app.session.cookie-secure=false
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

### 第 4 步：启动前端

```bash
cd front
npm install
npm run dev
```

浏览器打开 **http://localhost:5173**（Vite 代理 `/api` → 后端 8080，无需额外配置）。

## 默认账号

| 用户名 | 密码 |
|---|---|
| `admin` | `admin123` |

首次进入后建议在「个人中心」修改密码（修改后密码 1 个月内不能再次修改）。账号由后端首次启动自动写入数据库，不会出现在任何 SQL 文件里。

> 安全提示：源码中预留的默认账号已脱敏（admin/admin123）；正式部署请务必修改密码并更换 `application.properties` 中的 JWT 密钥。

## 常见问题

| 问题 | 处理 |
|---|---|
| 后端 500 / 连不上数据库 | 检查第 2 步 `spring.datasource.username/password` 是否改成你自己的 |
| 改了后端代码没生效 | 后端无热更新，改完需重启 |
| 前端端口被占用 | Vite 会自动 +1（5174）；或改 `front/vite.config.ts` 的 `port` |
| 头像/附件无法上传 | 确认 `backend/uploads/` 目录可写；大小上限 10MB |
| 上传超过 10MB | 前后端均为 10MB 上限，需同时调整第 2 步配置与前端校验 |
| 我忘记数据库账号 | 用 MySQL root 执行 `ALTER USER 'root'@'localhost' IDENTIFIED BY '新密码';` 后同步改到 application.properties |

## 模块清单

记账 · 健康（体重打卡 + 体重趋势 + 锻炼）· 学习（附笔记文件）· 每日总结（含班表）· 开发日志（多段会话 + 汇总）· 操作日志 · 个人中心（含修改密码）· 首页概览