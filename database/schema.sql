-- =====================================================
-- 个人记录系统 数据库初始化脚本
-- 数据库：personal_record
-- 版本：v1.0.0（10 张表全部完成）
-- 表清单：user / expense_category / expense_record / weight_record / learn_record / note_file / daily_note / operation_log / development_session / feature_log
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
  `age`           TINYINT UNSIGNED DEFAULT NULL COMMENT '年龄(岁),BMR 二期用',
  `height`        DECIMAL(5,1) DEFAULT NULL COMMENT '身高(cm),BMR 二期用',
  `gender`        VARCHAR(10) DEFAULT NULL COMMENT '性别:male男/female女, BMR 二期用',
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
  `module`     VARCHAR(20)  NOT NULL COMMENT '操作模块:EXPENSE/WEIGHT/LEARN/USER/NOTE/EXERCISE',
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
  `module`     VARCHAR(20)  NOT NULL COMMENT '所属模块:记账/健康/学习/系统/其他',
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
--     type: strength力量(个数+分钟) / plank平板(秒) / walk散步(距离+分钟) / stairs爬楼梯(层数+次数)
--     base_met=0 表示由速度/规则动态计算（如散步按速度定档）
--     注册新用户由后端复制 user_id=1 的默认动作
-- =====================================================
CREATE TABLE `exercise_item` (
  `id`         BIGINT      NOT NULL AUTO_INCREMENT COMMENT '主键',
  `user_id`    BIGINT      NOT NULL COMMENT '所属用户',
  `name`       VARCHAR(20) NOT NULL COMMENT '动作名 1-20位(保留用户叫法)',
  `type`       VARCHAR(10) NOT NULL COMMENT '类型:strength/plank/walk/stairs',
  `base_met`   DECIMAL(3,1) NOT NULL DEFAULT 0 COMMENT '基础MET(0=动态计算)',
  `ref_speed`  INT         DEFAULT NULL COMMENT '参考速度(个/分钟,强度系数基准,非力量类为空)',
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
  `reps`        INT         DEFAULT NULL COMMENT '个数(力量/平板)',
  `minutes`     DECIMAL(4,1) DEFAULT NULL COMMENT '分钟(力量/walk)',
  `distance`    DECIMAL(4,1) DEFAULT NULL COMMENT '公里(walk)',
  `floors`      INT         DEFAULT NULL COMMENT '一次爬几层(stairs)',
  `times`       INT         DEFAULT NULL COMMENT '爬几次(stairs)',
  `seconds`     INT         DEFAULT NULL COMMENT '秒(plank)',
  `hand`        VARCHAR(10) DEFAULT NULL COMMENT '左/右手:left/right/both(单手动作)',
  `note`        VARCHAR(255) DEFAULT NULL COMMENT '备注',
  `created_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`  DATETIME    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`     TINYINT     NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  KEY `idx_user_date` (`user_id`, `record_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='锻炼记录表';

-- =====================================================
-- 16. 预置默认锻炼动作（user_id=1 为初始用户，注册后后端复制）
-- =====================================================
INSERT INTO `exercise_item` (`user_id`, `name`, `type`, `base_met`, `ref_speed`, `has_weight`, `has_hand`, `sort_order`) VALUES
(1, '床上平躺举哑铃', 'strength', 3.5, 12, 1, 1, 1),
(1, '平肩俯卧撑',     'strength', 4.0, 15, 0, 0, 2),
(1, '臀桥',           'strength', 3.5, 15, 0, 0, 3),
(1, '臂力棒',         'strength', 3.0, 10, 1, 0, 4),
(1, '平板支撑',       'plank',    4.0, NULL, 0, 0, 5),
(1, '散步',           'walk',     0.0, NULL, 0, 0, 6),
(1, '爬楼梯',         'stairs',   8.0, NULL, 0, 0, 7);
