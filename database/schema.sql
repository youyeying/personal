-- =====================================================
-- 个人记录系统 数据库初始化脚本
-- 数据库：personal_record
-- 版本：v2.1.0（17 张表全部完成，与前/后端设计文档版本对齐）
-- 表清单：user / auth_session / expense_category / expense_record / weight_record
--         / learn_record / note_file / daily_note / operation_log
--         / development_session / feature_log / shift_record
--         / exercise_item / exercise_record / food_item / food_record / food_meal_template
-- 预置数据：默认收支分类 14 条 / 默认锻炼动作 9 条 / 默认食物 39 条（均 user_id=1，注册时后端复制给新用户）
-- =====================================================

-- 1. 建库（已存在则跳过）
CREATE DATABASE IF NOT EXISTS `personal_record`
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_unicode_ci;

USE `personal_record`;

-- =====================================================
-- 2. 用户表
-- =====================================================
CREATE TABLE `user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      VARCHAR(30)  NOT NULL COMMENT '登录名 1-30位',
  `password`      VARCHAR(100) NOT NULL COMMENT '密码哈希(明文8-16位)',
  `phone`         VARCHAR(11)  NOT NULL COMMENT '手机号 11位',
  `nickname`      VARCHAR(20)  DEFAULT NULL COMMENT '昵称 1-20位',
  `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像本地相对路径',
  `target_weight` DECIMAL(5,2) DEFAULT NULL COMMENT '目标体重kg',
  `age`           TINYINT UNSIGNED DEFAULT NULL COMMENT '年龄(岁)',
  `height`        DECIMAL(5,1) DEFAULT NULL COMMENT '身高(cm)',
  `gender`        VARCHAR(10) DEFAULT NULL COMMENT '性别:male男/female女',
  `diet_target_gap` INT NOT NULL DEFAULT 500 COMMENT '每日目标热量缺口kcal(饮食预算=1.2BMR+锻炼-缺口,用户自定义;0=维持,负=增肌)',
  `password_updated_at` DATETIME DEFAULT NULL COMMENT '上次修改密码时间(首次为空可免限修改,改后开始一月冷却)',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
  UNIQUE KEY `uk_phone_deleted` (`phone`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='用户表';

-- =====================================================
-- 2.5 会话表（双 token 认证，2026-08-25 新增）
--    每个「设备」一行，存 refresh token 的 SHA-256 哈希
--    刷新时 rotation 原地更新哈希 + 滚动续期（默认 24h），旧 token 立即失效
--    一个用户可多设备同时登录（手机/电脑各一行）；登出/改密时软删
-- =====================================================
CREATE TABLE `auth_session` (
  `id`                 BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`            BIGINT       NOT NULL COMMENT '所属用户',
  `refresh_token_hash` VARCHAR(64)  NOT NULL COMMENT 'refresh token 的 SHA-256 十六进制哈希',
  `expires_at`         DATETIME     NOT NULL COMMENT 'refresh 过期时间（每次刷新滚动 +24h）',
  `device_name`        VARCHAR(100) DEFAULT NULL COMMENT '设备名（浏览器 · 系统，仅展示用）',
  `device_key`         VARCHAR(64)  NOT NULL DEFAULT '' COMMENT '设备指纹（UA 的 SHA-256），登录时按 用户+指纹 复用同设备会话',
  `created_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`         DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`            TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_hash_deleted` (`refresh_token_hash`, `deleted`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_devkey` (`user_id`, `device_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='认证会话表';

-- =====================================================
-- 3. 收支分类表
--    多用户：user_id 关联用户，分类按用户隔离
-- =====================================================
CREATE TABLE `expense_category` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
  `name`       VARCHAR(20)  NOT NULL COMMENT '分类名 1-20位',
  `type`       TINYINT      NOT NULL COMMENT '1支出/2收入',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_name_type_deleted` (`user_id`, `name`, `type`, `deleted`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收支分类表';

-- =====================================================
-- 4. 预置默认分类（user_id=1 为初始用户，注册后其余用户由后端复制默认分类）
--    支出 type=1，收入 type=2
-- =====================================================
INSERT INTO `expense_category` (`user_id`, `name`, `type`, `sort_order`) VALUES
-- 支出
(1, '餐饮', 1, 1),
(1, '交通', 1, 2),
(1, '购物', 1, 3),
(1, '居住', 1, 4),
(1, '娱乐', 1, 5),
(1, '医疗', 1, 6),
(1, '学习', 1, 7),
(1, '人情', 1, 8),
(1, '其他', 1, 9),
-- 收入
(1, '工资', 2, 1),
(1, '副业', 2, 2),
(1, '理财', 2, 3),
(1, '红包', 2, 4),
(1, '其他', 2, 5);

-- =====================================================
-- 5. 收支记录表
--    金额 DECIMAL(10,2) 保留两位小数
-- =====================================================
CREATE TABLE `expense_record` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT        NOT NULL COMMENT '所属用户',
  `type`        TINYINT       NOT NULL COMMENT '1支出/2收入',
  `category_id` BIGINT        NOT NULL COMMENT '收支分类',
  `amount`      DECIMAL(10,2) NOT NULL COMMENT '金额(两位小数)',
  `note`        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `record_date` DATE          NOT NULL COMMENT '记账日期(可补记)',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='收支记录表';

-- =====================================================
-- 6. 体重记录表
--    同一天允许多次记录（早晚各一次），不设同日唯一
-- =====================================================
CREATE TABLE `weight_record` (
  `id`          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT        NOT NULL COMMENT '所属用户',
  `weight`      DECIMAL(5,2)  NOT NULL COMMENT '体重kg',
  `body_fat`    DECIMAL(4,2)  DEFAULT NULL COMMENT '体脂率(可选)',
  `waist`       DECIMAL(4,2)  DEFAULT NULL COMMENT '腰围cm(可选)',
  `note`        VARCHAR(255)  DEFAULT NULL COMMENT '备注',
  `record_date` DATE          NOT NULL COMMENT '记录日期(可补记)',
  `created_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT       NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='体重记录表';

-- =====================================================
-- 7. 学习记录表
--    收获笔记 content 存文本；PDF/截图等附件走 note_file 表
-- =====================================================
CREATE TABLE `learn_record` (
  `id`          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT       NOT NULL COMMENT '所属用户',
  `title`       VARCHAR(100) NOT NULL COMMENT '学习主题 1-100位',
  `content`     TEXT         DEFAULT NULL COMMENT '收获笔记(文本)',
  `duration`    INT          DEFAULT NULL COMMENT '时长(分钟)',
  `way`         VARCHAR(20)  NOT NULL COMMENT '方式:阅读/视频/课程/实践/其他',
  `mastery`     TINYINT      DEFAULT NULL COMMENT '掌握程度 1-5',
  `learn_date`  DATE         NOT NULL COMMENT '学习日期(可补记)',
  `created_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `learn_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习记录表';

-- =====================================================
-- 8. 学习附件表
--    文件存本地 uploads/note/ 目录，数据库只存相对路径
-- =====================================================
CREATE TABLE `note_file` (
  `id`              BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`         BIGINT       NOT NULL COMMENT '所属用户',
  `learn_record_id` BIGINT       NOT NULL COMMENT '所属学习记录',
  `file_name`       VARCHAR(255) NOT NULL COMMENT '原始文件名',
  `file_path`       VARCHAR(255) NOT NULL COMMENT '本地相对路径(含UUID文件名)',
  `file_type`       VARCHAR(20)  NOT NULL COMMENT '类型:pdf/png/jpg',
  `file_size`       BIGINT       NOT NULL COMMENT '文件大小(字节)',
  `created_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`      DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`         TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_learn_record_id` (`learn_record_id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='学习附件表';

-- =====================================================
-- 9. 每日总结表
--    同用户同日仅一条总结（唯一约束含 deleted，软删除后可重建）
-- =====================================================
CREATE TABLE `daily_note` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
  `note_date`  DATE         NOT NULL COMMENT '日期',
  `mood`       VARCHAR(20)  DEFAULT NULL COMMENT '心情',
  `content`    TEXT         DEFAULT NULL COMMENT '今日小结',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date_deleted` (`user_id`, `note_date`, `deleted`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='每日总结表';

-- =====================================================
-- 10. 操作日志表
--     记录用户关键操作（登录/增删改等），用于审计追溯
-- =====================================================
CREATE TABLE `operation_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '操作者',
  `module`     VARCHAR(20)  NOT NULL COMMENT '操作模块:EXPENSE/WEIGHT/LEARN/USER/NOTE/EXERCISE/FOOD',
  `action`     VARCHAR(20)  NOT NULL COMMENT '操作动作:CREATE/UPDATE/DELETE/RESTORE/LOGIN/REGISTER',
  `target_id`  BIGINT       DEFAULT NULL COMMENT '操作对象ID',
  `content`    VARCHAR(500) DEFAULT NULL COMMENT '操作内容描述',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_created_at` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='操作日志表';

-- =====================================================
-- 11. 开发会话表
--     一天可多次开始/结束（多段），每次记录一段；每日时长=当天所有段之和
-- =====================================================
CREATE TABLE `development_session` (
  `id`               BIGINT   NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_date`     DATE     NOT NULL COMMENT '开发日期(可同日多条=多段)',
  `start_time`       DATETIME NOT NULL COMMENT '本段开发开始时间',
  `end_time`         DATETIME DEFAULT NULL COMMENT '本段开发结束时间',
  `duration_minutes` INT      DEFAULT NULL COMMENT '本段开发时长(分钟)',
  `status`           TINYINT  NOT NULL DEFAULT 0 COMMENT '状态:0进行中/1已结束',
  `created_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`       DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_date` (`session_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开发会话表';

-- =====================================================
-- 12. 功能变更记录表
--     开发过程中记录新增/修改/删除的功能，挂在开发会话下
-- =====================================================
CREATE TABLE `feature_log` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `session_id` BIGINT       NOT NULL COMMENT '所属开发会话',
  `type`       VARCHAR(10)  NOT NULL COMMENT '变更类型:新增/修改/删除/修复',
  `module`     VARCHAR(20)  NOT NULL COMMENT '所属模块:记账/健康/学习/每日总结/开发日志/操作日志/个人中心/首页概览/认证/安全/布局/通用/系统/文档/前端/后端(前端allow-create可自定义)',
  `content`    VARCHAR(500) NOT NULL COMMENT '功能变更描述',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='功能变更记录表';

-- =====================================================
-- 13. 班表记录表
--     按「每月21日 → 次月20日」一个班期，每天一个班次（早/中/晚/休等）
--     批量上传一个班期（约30天），同日唯一
-- =====================================================
CREATE TABLE `shift_record` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
  `shift_date` DATE         NOT NULL COMMENT '日期',
  `shift_name` VARCHAR(20)  NOT NULL COMMENT '班次名称:早班/中班/晚班/休息等',
  `note`       VARCHAR(200) DEFAULT NULL COMMENT '备注(可选)',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_date_deleted` (`user_id`, `shift_date`, `deleted`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='班表记录表';

-- =====================================================
-- 14. 锻炼动作字典表（v1.26.0 新增）
--     每个动作：类型 + 基础 MET + 参考速度（强度系数基准），用户可自定义
--     type: strength力量(个数+分钟) / cardio有氧计数(个数+分钟) / plank平板(秒) / walk散步(距离+分钟) / stairs爬楼梯(层数+次数)
--     base_met=0 表示由速度/规则动态计算（如散步按速度定档）
--     注册新用户由后端复制 user_id=1 的默认动作
-- =====================================================
CREATE TABLE `exercise_item` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
  `name`       VARCHAR(20) NOT NULL COMMENT '动作名 1-20位(保留用户叫法)',
  `type`       VARCHAR(10) NOT NULL COMMENT '类型:strength/cardio/plank/walk/cycling/stairs',
  `base_met`   DECIMAL(3,1) NOT NULL DEFAULT 0 COMMENT '基础MET(0=动态计算,Compendium官方条目值)',
  `ref_speed`  INT         DEFAULT NULL COMMENT '参考速度(个/分钟,用户平均节奏=中等强度基准,非计数类为空)',
  `max_speed`  INT         DEFAULT NULL COMMENT '速度上限(个/分钟,世界纪录封顶防MET爆炸,缺省参考速度×3)',
  `has_weight` TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否记重量',
  `has_hand`   TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '是否记左右手',
  `sort_order` INT         NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锻炼动作字典表';

-- =====================================================
-- 15. 锻炼记录表（v1.26.0 新增）
--     只存原始参数（重量/个数/分钟/距离/层数/次数/秒/手），大卡由前端按 MET 公式计算，
--     不落库（公式迭代时历史记录自动跟随，无需重算迁移）
-- =====================================================
CREATE TABLE `exercise_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '所属用户',
  `exercise_id` BIGINT      NOT NULL COMMENT '动作(EXERCISE_ITEM 外键)',
  `record_date` DATE        NOT NULL COMMENT '锻炼日期(可补记)',
  `weight`      DECIMAL(5,2) DEFAULT NULL COMMENT '重量kg(力量,自重动作留空)',
  `reps`         INT         DEFAULT NULL COMMENT '个数(力量)',
  `minutes`      DECIMAL(4,1) DEFAULT NULL COMMENT '分钟(力量/walk/cycling)',
  `distance`     DECIMAL(6,3) DEFAULT NULL COMMENT '公里(walk/cycling,支持3位小数)',
  `floors`      INT         DEFAULT NULL COMMENT '一次爬几层(stairs)',
  `times`       INT         DEFAULT NULL COMMENT '爬几次(stairs)',
  `seconds`     INT         DEFAULT NULL COMMENT '秒(plank)',
  `hand`        VARCHAR(10) DEFAULT NULL COMMENT '左/右手:left/right/both(单手动作)',
  `note`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `body_weight` DECIMAL(5,2) DEFAULT NULL COMMENT '记录时体重快照kg(历史消耗固定,不随当前体重变)',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锻炼记录表';

-- =====================================================
-- 16. 预置默认锻炼动作（user_id=1 为初始用户，注册后后端复制）
--     base_met = Compendium 官方条目值；ref_speed = 用户平均节奏；max_speed = 世界纪录封顶
-- =====================================================
INSERT INTO `exercise_item` (`user_id`, `name`, `type`, `base_met`, `ref_speed`, `max_speed`, `has_weight`, `has_hand`, `sort_order`) VALUES
(1, '床上平躺举哑铃', 'strength', 3.5, 17, 90, 1, 1, 1),
(1, '平肩俯卧撑',     'strength', 3.8, 28, 80, 0, 0, 2),
(1, '臀桥',           'strength', 3.8, 30, 60, 0, 0, 3),
(1, '臂力棒',         'strength', 3.5, 25, 90, 1, 0, 4),
(1, '平板支撑',       'plank',    2.8, NULL, NULL, 0, 0, 5),
(1, '散步',           'walk',     0.0, NULL, NULL, 0, 0, 6),
(1, '骑行',           'cycling',  0.0, NULL, NULL, 0, 0, 7),
(1, '爬楼梯',         'stairs',   8.0, NULL, NULL, 0, 0, 8),
(1, '高抬腿击掌',     'cardio',   7.5, 48, 115, 0, 0, 9);

-- =====================================================
-- 17. 食物字典表（v1.36.0 新增，饮食模块）
--     只存每100g营养数值，摄入营养由前端按份量实时折算（大卡不落库，与锻炼 MET 同模式）
--     favorite=1 收藏；default_grams 为默认份量参考（个/根/碗/盒等，来自成熟App常见值）
--     注册新用户由后端复制 user_id=1 的默认食物
-- =====================================================
CREATE TABLE `food_item` (
  `id`            BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`       BIGINT      NOT NULL COMMENT '所属用户',
  `name`          VARCHAR(30) NOT NULL COMMENT '食物名',
  `type`          VARCHAR(10) NOT NULL COMMENT '类型:staple主食/protein肉蛋/veg蔬菜/fruit水果/snack零食饮品/other',
  `kcal`          DECIMAL(6,1) NOT NULL DEFAULT 0 COMMENT '每100g热量kcal',
  `protein`       DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '每100g蛋白质g',
  `fat`           DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '每100g脂肪g',
  `carbs`         DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '每100g碳水g',
  `sodium`        INT         NOT NULL DEFAULT 0 COMMENT '每100g钠mg',
  `fiber`         DECIMAL(5,1) NOT NULL DEFAULT 0 COMMENT '每100g膳食纤维g',
  `default_grams` DECIMAL(6,1) DEFAULT NULL COMMENT '默认份量g(个/根/碗/盒等参考值,成熟App口径)',
  `unit_label`    VARCHAR(10) DEFAULT NULL COMMENT '默认单位标签:个/根/碗/盒/ml/块/杯/份/片/把/袋/罐',
  `favorite`      TINYINT(1)  NOT NULL DEFAULT 0 COMMENT '收藏 0否/1是',
  `sort_order`    INT         NOT NULL DEFAULT 0 COMMENT '排序(同组内)',
  `created_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_type` (`user_id`, `type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='食物字典表(每100g营养)';

-- =====================================================
-- 18. 饮食记录表（v1.36.0 新增）
--     只存食物 + 份量，营养由前端按每100g数值 × 份量 ÷ 100 实时计算
-- =====================================================
CREATE TABLE `food_record` (
  `id`          BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`     BIGINT      NOT NULL COMMENT '所属用户',
  `food_id`     BIGINT      NOT NULL COMMENT '食物(FOOD_ITEM 外键)',
  `record_date` DATE        NOT NULL COMMENT '饮食日期(可补记)',
  `meal_type`   VARCHAR(10) NOT NULL COMMENT '餐次:breakfast/lunch/dinner/snack',
  `grams`       DECIMAL(7,1) NOT NULL COMMENT '份量g(默认单位带出可改)',
  `note`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='饮食记录表';

-- =====================================================
-- 19. 整餐模板表（v1.36.0 新增）
--     把常吃的一餐存为模板（如「工作日早餐」），一键复制到任意日期
--     items 存 JSON 数组：[{foodId, grams, mealType}]
-- =====================================================
CREATE TABLE `food_meal_template` (
  `id`         BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT       NOT NULL COMMENT '所属用户',
  `name`       VARCHAR(20)  NOT NULL COMMENT '模板名(如:工作日早餐)',
  `items`      TEXT         NOT NULL COMMENT 'JSON[{foodId,grams,mealType}]',
  `sort_order` INT          NOT NULL DEFAULT 0 COMMENT '排序',
  `created_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`    TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='整餐模板表';

-- =====================================================
-- 20. 预置默认食物（user_id=1 为初始用户，注册后后端复制）
--     营养值参考成熟App(薄荷健康等)常见口径，默认份量为个/根/碗等常见量
-- =====================================================
INSERT INTO `food_item` (`user_id`, `name`, `type`, `kcal`, `protein`, `fat`, `carbs`, `sodium`, `fiber`, `default_grams`, `unit_label`, `sort_order`) VALUES
-- 主食
(1, '米饭',       'staple',  116, 2.6, 0.3, 25.9, 2,   0.3, 200, '碗', 1),
(1, '粥',         'staple',   46, 1.1, 0.3,  9.9, 1,   0.1, 250, '碗', 2),
(1, '面条',       'staple',  110, 3.9, 0.4, 22.8, 1,   0.2, 250, '碗', 3),
(1, '馒头',       'staple',  223, 7.0, 1.1, 47.0, 165, 1.3, 100, '个', 4),
(1, '全麦面包',   'staple',  246,10.1, 3.4, 44.3, 420, 6.8,  35, '片', 5),
(1, '燕麦',       'staple',  377,10.1, 6.6, 66.7, 4,  10.6,  40, '碗', 6),
(1, '红薯',       'staple',   90, 1.6, 0.2, 20.7, 55,  3.0, 150, '个', 7),
(1, '玉米',       'staple',  112, 4.0, 1.2, 22.8, 1,   2.9, 150, '根', 8),
(1, '土豆',       'staple',   77, 2.0, 0.2, 17.2, 6,   2.2, 150, '个', 9),
-- 肉蛋
(1, '鸡蛋',       'protein', 144,13.3, 8.8,  2.8, 131, 0.0,  50, '个', 1),
(1, '鸡胸肉',     'protein', 133,24.6, 2.5,  0.6,  34, 0.0, 150, '份', 2),
(1, '瘦猪肉',     'protein', 143,20.3, 6.2,  1.5,  57, 0.0, 100, '份', 3),
(1, '牛肉',       'protein', 125,20.2, 4.2,  1.2,  66, 0.0, 100, '份', 4),
(1, '鱼肉',       'protein', 113,17.9, 4.3,  0.0,  52, 0.0, 150, '份', 5),
(1, '虾',         'protein',  99,21.2, 0.9,  0.5, 165, 0.0, 100, '份', 6),
(1, '豆腐',       'protein',  84, 6.6, 3.4,  3.5,   7, 0.4, 100, '块', 7),
(1, '牛奶',       'protein',  54, 3.0, 3.2,  3.4,  37, 0.0, 250, '盒', 8),
(1, '酸奶',       'protein',  72, 2.5, 2.7,  9.3,  50, 0.0, 200, '杯', 9),
-- 蔬菜
(1, '西兰花',     'veg',      36, 4.1, 0.6,  4.3,  18, 1.6, 150, '份', 1),
(1, '青菜',       'veg',      15, 1.5, 0.3,  2.2,  73, 1.1, 200, '份', 2),
(1, '西红柿',     'veg',      20, 0.9, 0.2,  4.0,   5, 1.2, 150, '个', 3),
(1, '黄瓜',       'veg',      16, 0.8, 0.2,  2.9,   5, 0.5, 150, '根', 4),
(1, '胡萝卜',     'veg',      39, 1.0, 0.2,  8.8,  69, 2.8, 100, '根', 5),
(1, '菠菜',       'veg',      28, 2.6, 0.3,  4.5,  85, 1.7, 150, '份', 6),
(1, '生菜',       'veg',      15, 1.3, 0.3,  2.0,  32, 0.7, 100, '份', 7),
(1, '冬瓜',       'veg',      12, 0.4, 0.2,  2.6,   3, 0.7, 200, '份', 8),
-- 水果
(1, '苹果',       'fruit',    53, 0.4, 0.2, 13.7,   2, 1.7, 200, '个', 1),
(1, '香蕉',       'fruit',    93, 1.4, 0.2, 22.0,   1, 1.2, 100, '根', 2),
(1, '橙子',       'fruit',    48, 0.8, 0.2, 11.1,   1, 0.6, 200, '个', 3),
(1, '西瓜',       'fruit',    31, 0.5, 0.3,  6.8,   3, 0.4, 300, '块', 4),
(1, '葡萄',       'fruit',    45, 0.4, 0.3, 10.3,   2, 0.4, 200, '串', 5),
(1, '梨',         'fruit',    51, 0.4, 0.2, 13.1,   1, 2.6, 200, '个', 6),
(1, '蓝莓',       'fruit',    57, 0.7, 0.3, 14.5,   1, 2.4, 100, '盒', 7),
-- 零食饮品
(1, '豆浆',       'snack',    31, 3.0, 1.6,  1.2,   1, 0.6, 250, 'ml', 1),
(1, '可乐',       'snack',    43, 0.0, 0.0, 10.6,   5, 0.0, 330, 'ml', 2),
(1, '坚果混合',   'snack',   645,20.0,54.0, 20.0, 350, 7.0,  30, '把', 3),
(1, '巧克力',     'snack',   589, 4.3,40.1, 53.4,  24, 1.5,  20, '块', 4),
(1, '薯片',       'snack',   548, 7.0,37.6, 49.2, 525, 4.0,  30, '袋', 5),
(1, '蛋糕',       'snack',   348, 6.0,13.9, 53.1, 250, 0.6,  80, '块', 6);
