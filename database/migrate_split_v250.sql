-- =====================================================
-- 拆分用户端/开发端：数据库迁移（v2.5.0）
-- 1. 新增 admin_user 表（开发账号）
-- 2. auth_session 加 user_type（区分业务用户/开发账号会话）
-- 3. 预置基础数据（食物/动作/分类）迁移为全局模板 user_id=0
-- =====================================================

-- 1. admin_user 表（开发账号）
CREATE TABLE IF NOT EXISTS `admin_user` (
  `id`            BIGINT       NOT NULL AUTO_INCREMENT COMMENT '主键',
  `username`      VARCHAR(30)  NOT NULL COMMENT '登录名 1-30位',
  `password`      VARCHAR(100) NOT NULL COMMENT '密码哈希(8-16位,含数字+大小写字母)',
  `phone`         VARCHAR(11)  DEFAULT NULL COMMENT '手机号 11位',
  `nickname`      VARCHAR(20)  DEFAULT NULL COMMENT '昵称',
  `avatar`        VARCHAR(255) DEFAULT NULL COMMENT '头像本地相对路径',
  `created_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at`    DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `deleted`       TINYINT      NOT NULL DEFAULT 0 COMMENT '软删除 0否/1是',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username_deleted` (`username`, `deleted`),
  UNIQUE KEY `uk_phone_deleted` (`phone`, `deleted`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='开发账号表(管理员)';

-- 2. auth_session 加 user_type（存量会话默认 1=业务用户）
ALTER TABLE `auth_session`
  ADD COLUMN `user_type` TINYINT NOT NULL DEFAULT 1 COMMENT '用户类型:1=业务用户user表/2=开发账号admin_user表' AFTER `user_id`;

-- 3. 预置基础数据迁移为全局模板（user_id=0）
-- 3.1 预置食物（schema 清单 39 基础 + 13 火锅，共 52 条）→ user_id=0；用户自定义(卤鸡腿/脱骨鸡爪/豆小月腐竹/鸡胸肉饺子/黑胡椒鸡排)留在 user_id=1
UPDATE `food_item` SET `user_id`=0, `favorite`=0 WHERE `user_id`=1 AND `name` IN (
  '米饭','粥','面条','馒头','全麦面包','燕麦','红薯','玉米','土豆',
  '鸡蛋','鸡胸肉','瘦猪肉','牛肉','鱼肉','虾','豆腐','牛奶','酸奶',
  '西兰花','青菜','西红柿','黄瓜','胡萝卜','菠菜','生菜','冬瓜',
  '苹果','香蕉','橙子','西瓜','葡萄','梨','蓝莓',
  '豆浆','可乐','坚果混合','巧克力','薯片','蛋糕',
  '烤猪肉串','煮羊排','肥牛卷','牛百叶(毛肚)','牛肚','羊肉卷','瘦羊肉片',
  '牛上脑','牛里脊','牛腩','牛舌','牛黄喉','羊上脑'
);
-- 3.2 预置锻炼动作（9 条）→ user_id=0
UPDATE `exercise_item` SET `user_id`=0 WHERE `user_id`=1;
-- 3.3 预置收支分类（14 条）→ user_id=0
UPDATE `expense_category` SET `user_id`=0 WHERE `user_id`=1;
