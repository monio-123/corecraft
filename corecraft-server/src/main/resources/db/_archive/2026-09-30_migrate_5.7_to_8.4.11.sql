-- =============================================================================
-- 一次性迁移脚本：corecraft 库  MySQL 5.7.29  ->  MySQL 8.4.11
--
-- 源：172.168.87.32:3306 / corecraft   （5.7.29）
-- 目标：106.53.205.69:3306 / corecraft （8.4.11）
-- 生成日期：2026-09-30
-- 用途：把源库 13 张表 + 55 行数据整体搬迁到 8.4 实例
-- 触发时机：仅本次迁移执行一次。目标库当前为空库，执行后请归档本文件。
--
-- 相比源库做的两处适配（8.4 必需）：
--   1. 移除整数显示宽度：bigint(20)/int(11)/tinyint(3)/tinyint(4) -> 无宽度
--      原因：MySQL 8.0.19 起移除整数显示宽度，8.4 迁移必须去括号。
--      例外：tinyint(1) 保留 —— MySQL 明确保留它以兼容 BOOLEAN 别名。
--      影响：bigint(20) 与 bigint 均为 8 字节，int(11) 与 int 均为 4 字节，
--            tinyint(3/4/1) 与 tinyint 均为 1 字节 —— 存储与取值范围完全不变。
--   2. utf8mb3 -> utf8mb4：sys_user / sys_role / sys_permission /
--      sys_user_role / sys_role_permission / oauth2_registered_client
--      原为 DEFAULT CHARSET=utf8(=utf8mb3, utf8_general_ci)，改为 utf8mb4。
--      原因：utf8mb3 自 8.0.28 起 deprecated，且无法存 4 字节字符（emoji）。
--      影响：utf8mb4 是 utf8mb3 的超集，现有数据逐字节兼容，排序规则
--            utf8_general_ci -> utf8mb4_0900_ai_ci 均为不区分大小写，
--            现有唯一索引（uk_user_category_name / uk_topic_tag 等）不受影响。
--
-- 保持不变：kp_* 5 张表本就是 utf8mb4/utf8mb4_unicode_ci，原样搬迁；
--           kp_topic 等表的列级 COLLATE 与表级相同属冗余，已折叠到表级，
--           行为完全等价；tinyint(1) 保留；UNSIGNED 保留（8.4 仍支持）。
-- 数据保真：55 行全部为源库原样搬运，包含 is_delete=1 的软删数据
--           （kp_topic 4 行：id 2/4/5/6；其余表无软删行），未做任何过滤。
--
-- ⚠ 执行者必读（本脚本实战踩过的两个坑）：
--   1. 手抄 INSERT 时会吞掉连续换行。
--      kp_topic id=8 的 content 在 ``` 之后有一段 Markdown 段落空行（\n\n），
--      手工转录时两次都只写进了 1 个 \n，导致正文丢一个空行。
--      后果：行数校验完全正常（10 行还是 10 行），只有逐行内容比对才暴露。
--      对策：导入后必须跑第三段的逐行 CRC32 校验，不能只数行数。
--   2. kp_topic.update_time 带 ON UPDATE CURRENT_TIMESTAMP。
--      任何 UPDATE 修数据（包括修 content）都会静默刷新 update_time。
--      对策：修数据时同一条 UPDATE 里显式回写 update_time 原始值，
--            显式赋值不会被 ON UPDATE 覆盖。
--   3. 校验禁用 GROUP_CONCAT：默认 group_concat_max_len=1024 会静默截断，
--      两端同样截断还会产生"指纹一致"的假阳性。本脚本第三段改用
--      SUM(CRC32(...)) 数值聚合，无长度上限。
-- =============================================================================

CREATE DATABASE IF NOT EXISTS `corecraft` DEFAULT CHARACTER SET utf8mb4;
USE `corecraft`;

-- 建议导入时用（8.4 严格模式更严，导入期放宽避免误报）：
--   SET SESSION sql_mode = 'NO_ENGINE_SUBSTITUTION';


-- =============================================================================
-- 一、建表（13 张，顺序：先父后子，纯为可读性，无外键约束）
-- =============================================================================

CREATE TABLE `sys_user` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  `is_delete` tinyint unsigned NOT NULL DEFAULT '0',
  `username` varchar(20) NOT NULL,
  `password` varchar(100) NOT NULL,
  `dept_id` bigint unsigned DEFAULT NULL,
  `account_status` tinyint unsigned NOT NULL DEFAULT '0' COMMENT '0: 正常使用 1: 被禁用',
  `nickname` varchar(100) DEFAULT NULL,
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像地址',
  `email` varchar(30) DEFAULT NULL,
  `mobile` varchar(20) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=6 DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  `is_delete` tinyint unsigned NOT NULL DEFAULT '0',
  `name` varchar(100) NOT NULL,
  `code` varchar(30) DEFAULT NULL,
  `description` varchar(1000) DEFAULT NULL,
  `is_enabled` tinyint unsigned NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=5 DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  `is_delete` tinyint unsigned NOT NULL DEFAULT '0',
  `name` varchar(100) NOT NULL,
  `code` varchar(30) NOT NULL,
  `sort` int NOT NULL DEFAULT '0',
  `is_enabled` tinyint unsigned NOT NULL DEFAULT '1',
  `type` varchar(20) NOT NULL COMMENT 'MENU,OP,API;',
  `parent_id` bigint DEFAULT NULL,
  `meta` text,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_user_role` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  `is_delete` tinyint unsigned NOT NULL DEFAULT '0',
  `user_id` bigint unsigned NOT NULL,
  `role_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=4 DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_role_permission` (
  `id` bigint unsigned NOT NULL AUTO_INCREMENT,
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT NULL,
  `is_delete` tinyint unsigned NOT NULL DEFAULT '0',
  `role_id` bigint unsigned NOT NULL,
  `permission_id` bigint unsigned NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB AUTO_INCREMENT=161 DEFAULT CHARSET=utf8mb4;

CREATE TABLE `oauth2_registered_client` (
  `id` varchar(100) NOT NULL,
  `client_id` varchar(100) NOT NULL,
  `client_id_issued_at` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `client_secret` varchar(200) DEFAULT NULL,
  `client_secret_expires_at` timestamp NULL DEFAULT NULL,
  `client_name` varchar(200) NOT NULL,
  `client_authentication_methods` varchar(1000) NOT NULL,
  `authorization_grant_types` varchar(1000) NOT NULL,
  `redirect_uris` varchar(1000) DEFAULT NULL,
  `post_logout_redirect_uris` varchar(1000) DEFAULT NULL,
  `scopes` varchar(1000) NOT NULL,
  `client_settings` varchar(2000) NOT NULL,
  `token_settings` varchar(2000) NOT NULL,
  PRIMARY KEY (`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE `sys_dict_type` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `code` varchar(100) NOT NULL COMMENT '字典编码',
  `name` varchar(100) NOT NULL COMMENT '字典名称',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_type_code` (`code`)
) ENGINE=InnoDB AUTO_INCREMENT=3 DEFAULT CHARSET=utf8mb4 COMMENT='字典类型表';

CREATE TABLE `sys_dict_item` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '主键',
  `dict_type_id` bigint NOT NULL COMMENT '字典类型ID',
  `label` varchar(100) NOT NULL COMMENT '展示标签',
  `value` varchar(100) NOT NULL COMMENT '字典值',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `css_class` varchar(100) DEFAULT NULL COMMENT '样式标记',
  `remark` varchar(255) DEFAULT NULL COMMENT '备注',
  `is_enabled` tinyint(1) NOT NULL DEFAULT '1' COMMENT '是否启用',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `is_delete` tinyint(1) NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_sys_dict_item_type_value` (`dict_type_id`,`value`),
  KEY `idx_sys_dict_item_type_id` (`dict_type_id`)
) ENGINE=InnoDB AUTO_INCREMENT=2 DEFAULT CHARSET=utf8mb4 COMMENT='字典项表';

CREATE TABLE `kp_topic` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `title` varchar(100) NOT NULL DEFAULT '' COMMENT '知识点标题（未填写时前端展示"未命名"）',
  `content` text COMMENT '知识点内容（Markdown）',
  `parent_topic_id` bigint DEFAULT NULL COMMENT '父知识点ID（自关联层级）',
  `tree_id` bigint DEFAULT NULL COMMENT '所属知识树ID（按 tag 自动分组回填）',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_parent_topic` (`parent_topic_id`),
  KEY `idx_tree_id` (`tree_id`),
  KEY `idx_create_time` (`create_time`)
) ENGINE=InnoDB AUTO_INCREMENT=11 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点';

CREATE TABLE `kp_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL COMMENT '所属用户ID',
  `name` varchar(50) NOT NULL COMMENT '标签名称',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_category_name` (`user_id`,`name`),
  KEY `idx_user_id` (`user_id`)
) ENGINE=InnoDB AUTO_INCREMENT=8 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='标签主数据';

CREATE TABLE `kp_topic_tag` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `topic_id` bigint NOT NULL COMMENT '知识点ID',
  `tag_id` bigint NOT NULL COMMENT '标签ID',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_tag` (`topic_id`,`tag_id`),
  KEY `idx_tag_id` (`tag_id`)
) ENGINE=InnoDB AUTO_INCREMENT=15 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点-标签关联';

CREATE TABLE `kp_topic_relation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `topic_id` bigint NOT NULL COMMENT '知识点ID',
  `related_topic_id` bigint NOT NULL COMMENT '关联知识点ID',
  `relation_type` varchar(20) NOT NULL DEFAULT 'related' COMMENT '关联类型：prerequisite/extends/contrast/related',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_topic_relation` (`topic_id`,`related_topic_id`),
  KEY `idx_related_topic` (`related_topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点关联';

CREATE TABLE `kp_topic_quiz` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `topic_id` bigint NOT NULL COMMENT '知识点ID',
  `question_type` varchar(20) NOT NULL DEFAULT 'single' COMMENT '题目类型：single/multiple',
  `question` varchar(500) NOT NULL COMMENT '题目内容',
  `options` json NOT NULL COMMENT '选项数组',
  `answer` int NOT NULL COMMENT '正确答案索引',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `create_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `is_delete` tinyint NOT NULL DEFAULT '0',
  PRIMARY KEY (`id`),
  KEY `idx_topic_id` (`topic_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='知识点自测题';


-- =============================================================================
-- 二、数据（51 行；字面量由源库 QUOTE() 生成，中文/换行/反斜杠均已正确转义）
-- =============================================================================

-- sys_user (4)
INSERT INTO `sys_user` VALUES
('1','2025-05-29 17:26:00',NULL,'0','mo','$2a$10$s/dhfLDaoMdYUU0dPBEt/uOXxPa16UPzaD.cGJA.2fuJ0BASH8ya6',NULL,'0','','/api/files/avatars/f7a5764f1ab248eca73b533eebf7e93d.png','',''),
('2','2025-12-19 09:26:07',NULL,'0','timo','$2a$10$s/dhfLDaoMdYUU0dPBEt/uOXxPa16UPzaD.cGJA.2fuJ0BASH8ya6',NULL,'0',NULL,NULL,NULL,''),
('4','2026-02-24 17:55:45',NULL,'0','77712','$2a$10$v6YPbjllejjjfXgMVoK1CORBYa2x3/o5BN1c8poV882tEpqb.5UYa',NULL,'0',NULL,NULL,NULL,'17546547841'),
('5','2026-05-21 09:15:48',NULL,'0','999','$2a$10$c6Q4Ksem5Klf6AZlKGFk4u8KUsCFAwD0FvE3QJBKjYmuUZyWrrISG',NULL,'0','666',NULL,'','15645698745');

-- sys_role (4)
INSERT INTO `sys_role` VALUES
('1','2025-11-03 15:48:19',NULL,'0','系统管理员','ROLE_ADMIN','','1'),
('2','2025-11-03 15:48:19',NULL,'0','普通用户','ROLE_USER','','1'),
('3','2026-05-18 09:20:14',NULL,'0','admin','admin','admin','1'),
('4','2026-05-21 09:38:18',NULL,'0','666','555','','1');

-- sys_permission (8)
INSERT INTO `sys_permission` VALUES
('1','2026-08-05 16:38:10','2026-08-05 16:38:10','0','系统管理','system','1','1','GROUP',NULL,'{"icon":"Setting"}'),
('2','2026-08-05 16:38:10','2026-08-05 16:38:10','0','用户管理','user:manage','1','1','MENU','1','{"path":"/users","icon":"User"}'),
('3','2026-08-05 16:38:10','2026-08-05 16:38:10','0','角色管理','role:manage','2','1','MENU','1','{"path":"/roles","icon":"UserFilled"}'),
('4','2026-08-05 16:38:10','2026-08-05 16:38:10','0','资源管理','permission:manage','3','1','MENU','1','{"path":"/permissions","icon":"Lock"}'),
('5','2026-08-05 16:38:10','2026-08-05 16:38:10','0','字典管理','dict:manage','4','1','MENU','1','{"path":"/dicts","icon":"CollectionTag"}'),
('11','2026-08-05 16:38:11','2026-08-05 16:38:11','0','corecraft-web','corecraft-web','3','1','GROUP',NULL,'{"icon":"MagicStick"}'),
('12','2026-08-05 16:38:11','2026-09-24 09:29:01','0','随手记','corecraft-web:learn','1','1','MENU','11','{"path":"/corecraft-web/learn","icon":"MagicStick"}'),
('14','2026-09-21 18:00:13',NULL,'0','知识点详情','corecraft-web:topic-detail','2','1','MENU','11','{"path":"/corecraft-web/topic/:id","routeName":"CorecraftWebTopicDetail"}');

-- sys_user_role (3)
INSERT INTO `sys_user_role` VALUES
('1','2025-11-03 15:49:54',NULL,'0','1','2'),
('2','2025-11-03 15:49:54',NULL,'0','1','1'),
('3','2026-05-21 15:41:55',NULL,'0','5','2');

-- sys_role_permission (8)
INSERT INTO `sys_role_permission` VALUES
('143','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','1'),
('144','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','2'),
('145','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','3'),
('146','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','4'),
('147','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','5'),
('157','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','11'),
('158','2026-08-05 16:38:11','2026-08-05 16:38:11','0','1','12'),
('160','2026-09-21 18:00:13',NULL,'0','1','14');

-- oauth2_registered_client (1)
INSERT INTO `oauth2_registered_client` VALUES
('1','corecraft','2025-05-26 10:20:27','$2a$10$HrkNdp52hTCZcUqzDVju4./Vsmi7mufwI8.2qmhtvXxilchfYOpS2',NULL,'client-id','client_secret_basic','refresh_token,authorization_code','http://localhost:8081/login/oauth2/code/web-client','','read','{"@class":"java.util.Collections$UnmodifiableMap","settings.client.require-proof-key":false,"settings.client.require-authorization-consent":false}','{"@class":"java.util.Collections$UnmodifiableMap","settings.token.reuse-refresh-tokens":true,"settings.token.id-token-signature-algorithm":["org.springframework.security.oauth2.jose.jws.SignatureAlgorithm","RS256"],"settings.token.access-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.access-token-format":{"@class":"org.springframework.security.oauth2.server.authorization.settings.OAuth2TokenFormat","value":"self-contained"},"settings.token.refresh-token-time-to-live":["java.time.Duration",3600.000000000],"settings.token.authorization-code-time-to-live":["java.time.Duration",300.000000000],"settings.token.device-code-time-to-live":["java.time.Duration",300.000000000]}');

-- sys_dict_type (2)
INSERT INTO `sys_dict_type` VALUES
('1','123','123','123','1','2026-05-15 16:07:34','2026-05-15 16:07:34','0'),
('2','234','234','','1','2026-06-11 11:52:01','2026-06-11 11:52:01','0');

-- sys_dict_item (1)
INSERT INTO `sys_dict_item` VALUES
('1','1','1','1','0','','','1','2026-06-11 11:51:52','2026-06-11 11:51:52','0');

-- kp_tag (7)
INSERT INTO `kp_tag` VALUES
('1','1','MyBatis','2026-08-28 14:07:44','2026-08-28 14:07:44','0'),
('2','1','测试','2026-08-28 14:29:55','2026-08-28 14:29:55','0'),
('3','1','序列化','2026-08-28 15:21:16','2026-08-28 15:21:16','0'),
('4','1','HTTP','2026-09-23 14:38:20','2026-09-23 14:38:20','0'),
('5','1','nacos','2026-09-23 16:30:43','2026-09-23 16:30:43','0'),
('6','1','HTTPS','2026-09-23 17:14:54','2026-09-23 17:14:54','0'),
('7','1','基本数据类型','2026-09-23 18:37:54','2026-09-23 18:37:54','0');

-- kp_topic_tag (7)
INSERT INTO `kp_topic_tag` VALUES
('4','1','1','2026-08-28 14:29:55','2026-08-28 14:29:55','0'),
('5','1','2','2026-08-28 14:29:55','2026-08-28 14:29:55','0'),
('7','3','3','2026-08-28 15:21:16','2026-08-28 15:21:16','0'),
('9','8','5','2026-09-23 16:30:43','2026-09-23 16:30:43','0'),
('12','7','6','2026-09-23 17:14:59','2026-09-23 17:14:59','0'),
('13','9','7','2026-09-23 18:37:54','2026-09-23 18:37:54','0'),
('14','10','7','2026-09-23 18:41:22','2026-09-23 18:41:22','0');

-- kp_topic (10；含 is_delete=1 的软删数据 3 行：id 2/4/5/6 中被标记的，见下)
INSERT INTO `kp_topic` VALUES
('1','1','MyBatis-Plus Db.saveBatch 批量插入退化问题156','MyBatis-Plus Db.saveBatch 批量插入退化问题\n\n问题现象： Db.saveBatch 调用时，如果同一批次内的实体对象存在字段值为 null 的情况，会导致 SQL 模板不一致，批量插入退化为逐条 INSERT，性能大幅下降。\n\n根因： MyBatis-Plus 的 insert 是动态 SQL，null 字段不参与 INSERT 列。当同一批内部分行有某字段、部分行为 null 时，生成的 SQL 字符串不同      ：\n',NULL,'1','2026-08-28 14:07:32','2026-08-28 14:07:32','0'),
('2','1','','测试大王的阿达',NULL,'1','2026-08-28 14:08:12','2026-08-28 15:17:02','1'),
('3','1','jackson的convertValue与BeanUtils.copyProperties区别','Jackson 序列化/反序列化遵循 JSON 属性名：\n● convertValue 会把对象先转换成 JSON，再映射到目标对象。\nBeanUtils.copyProperties(source, target) 直接 按 Java 字段名或 getter/setter 名复制\n所以当源对象和目标对象 Java 字段名一样，但 @JsonProperty 改名后 JSON 名字不一样，或者两边 @JsonProperty 不一致时，convertValue 会按 JSON 名字映射，而不是 Java 字段名，这会导致字段复制**失败。**\n',NULL,'3','2026-08-28 15:21:00','2026-08-28 15:21:00','0'),
('4','1','','测测测',NULL,NULL,'2026-09-21 16:01:12','2026-09-23 18:38:48','1'),
('5','1','','### **6666adca**\n',NULL,NULL,'2026-09-21 16:07:57','2026-09-23 18:38:44','1'),
('6','1','','Jackson 序列化/反序列化遵循 JSON 属性名：\n● convertValue 会把对象先转换成 JSON，再映射到目标对象。\nBeanUtils.copyProperties(source, target) 直接 按 Java 字段名或 getter/setter 名复制\n所以当源对象和目标对象 Java 字段名一样，但 @JsonProperty 改名后 JSON 名字不一样，或者两边 @JsonProperty 不一致时，convertValue 会按 JSON 名字映射，而不是 Java 字段名，这会导致字段复制**失败。**\n',NULL,NULL,'2026-09-21 16:23:56','2026-09-23 18:38:40','1'),
('7','1','生成SSL证书','*mkdir ssl*\n\n<br />\n\n```\nopenssl req -x509 -nodes -days 3650 -newkey rsa:2048 \\\n-keyout ./server.key \\\n-out ./server.crt\n\n执行完，当前目录就生成两个文件：\n`server.key`、`server.crt`\n\nchmod 644 server.crt\n```\n\nchmod 600 server.key',NULL,'6','2026-09-23 14:37:28','2026-09-23 14:37:28','0'),
('8','1','','nacos配置中心的配置文件拉取配置成\n\n```\n      - nacos:application-@profiles.active@.yml\n      - nacos:${spring.application.name}-@profiles.active@.yml\n```\n而不是\n\n```\n      - optional:nacos:application-@profiles.active@.yml\n      - optional:nacos:${spring.application.name}-@profiles.active@.yml\n```\n\n<br />\n\n会在远端nacos的配置文件格式错误时报错\n\nconfig=NacosItemConfig {group=\'dev\', dataId=\'wssbop-service-dev.yml\', suffix=\'yml\', refreshEnabled=true, preference=null}}\' via location \'nacos:wssbop-service-dev.yml\' does not exist',NULL,'5','2026-09-23 16:30:31','2026-09-23 16:30:31','0'),
('9','1','','当使用浮点基本数据类型（如 double、float）进行除法时，如果除数是零，结果会是 Double.NaN（不是一个数字，Not a Number）。这是IEEE 754浮点数标准定义的行为，Java遵循这个标准。当使用整数类型（如 int 或 long）进行除法时，如果除数是零，会抛出一个 ArithmeticException 异常。这是因为整数除法不允许除以零，并且Java运行时环境会检测到这种情况并抛出异常。',NULL,'7','2026-09-23 18:37:09','2026-09-23 18:37:09','0'),
('10','1','对象/基本数据类型转列表','Arrays.asList 方案只能将对象数组转化为列表，如果转化基本数据类型会将其视为一个整体放入列表\n\n基本数据类型可使用\n\nArrays.stream(nums).boxed().collect(Collectors.toList());\n\n<br />\n\n<br />\n\n基本数据类型可使用\n\nArrays.stream(nums).boxed().collect(Collectors.toList());\n',NULL,'7','2026-09-23 18:40:30','2026-09-23 18:40:30','0');

-- kp_topic_relation (0) / kp_topic_quiz (0)：源库为空表，仅建表不插数据


-- =============================================================================
-- 三、导入后校验（逐条结果应与源库完全一致）
-- =============================================================================

SELECT 'sys_user' t, COUNT(*) c FROM sys_user
UNION ALL SELECT 'sys_role', COUNT(*) FROM sys_role
UNION ALL SELECT 'sys_permission', COUNT(*) FROM sys_permission
UNION ALL SELECT 'sys_user_role', COUNT(*) FROM sys_user_role
UNION ALL SELECT 'sys_role_permission', COUNT(*) FROM sys_role_permission
UNION ALL SELECT 'oauth2_registered_client', COUNT(*) FROM oauth2_registered_client
UNION ALL SELECT 'sys_dict_type', COUNT(*) FROM sys_dict_type
UNION ALL SELECT 'sys_dict_item', COUNT(*) FROM sys_dict_item
UNION ALL SELECT 'kp_topic', COUNT(*) FROM kp_topic
UNION ALL SELECT 'kp_tag', COUNT(*) FROM kp_tag
UNION ALL SELECT 'kp_topic_tag', COUNT(*) FROM kp_topic_tag
UNION ALL SELECT 'kp_topic_relation', COUNT(*) FROM kp_topic_relation
UNION ALL SELECT 'kp_topic_quiz', COUNT(*) FROM kp_topic_quiz;
-- 期望：4 / 4 / 8 / 3 / 8 / 1 / 2 / 1 / 10 / 7 / 7 / 0 / 0

-- 确认无残留整数显示宽度
SELECT table_name, column_name, column_type
FROM information_schema.columns
WHERE table_schema = 'corecraft' AND column_type REGEXP '^(tiny|small|medium|int|bigint)\\([0-9]+\\)'
  AND column_type <> 'tinyint(1)';
-- 期望：0 行

-- 确认无残留 utf8mb3
SELECT table_name, table_collation
FROM information_schema.tables
WHERE table_schema = 'corecraft'
  AND (table_collation LIKE 'utf8\\_%' OR table_collation = 'utf8_general_ci');
-- 期望：0 行

-- ---------------------------------------------------------------------------
-- 3.1 逐行内容指纹（★ 必跑，行数校验不能替代 ★）
--     在源库和目标库各跑一次，逐行比对 count 与 checksum 是否完全相同。
--     2026-09-30 实战：行数 10/10 全对，但 kp_topic id=8 的 content 少了一个
--     \n（Markdown 段落空行）—— 只有这一层校验能发现。
--     已验证一致的值（源 = 目标）：
--       sys_user 4/8948067244        sys_role 4/5965192779
--       sys_permission 8/13093173014  sys_user_role 3/8431996670
--       sys_role_permission 8/11527073960
--       oauth2_client 1/1399524621   sys_dict_type 2/4943459525
--       sys_dict_item 1/2089274859   kp_tag 7/18065509036
--       kp_topic_tag 7/16165959658   kp_topic 10/26597712079
-- ---------------------------------------------------------------------------
SELECT 'sys_user' t, COUNT(*) c, SUM(CRC32(CONCAT_WS('|',id,create_time,IFNULL(update_time,'~'),is_delete,username,password,IFNULL(dept_id,'~'),account_status,IFNULL(nickname,'~'),IFNULL(avatar,'~'),IFNULL(email,'~'),mobile))) k FROM sys_user
UNION ALL SELECT 'sys_role',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,create_time,IFNULL(update_time,'~'),is_delete,name,IFNULL(code,'~'),IFNULL(description,'~'),is_enabled))) FROM sys_role
UNION ALL SELECT 'sys_permission',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,create_time,IFNULL(update_time,'~'),is_delete,name,code,sort,is_enabled,type,IFNULL(parent_id,'~'),IFNULL(meta,'~')))) FROM sys_permission
UNION ALL SELECT 'sys_user_role',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,create_time,IFNULL(update_time,'~'),is_delete,user_id,role_id))) FROM sys_user_role
UNION ALL SELECT 'sys_role_permission',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,create_time,IFNULL(update_time,'~'),is_delete,role_id,permission_id))) FROM sys_role_permission
UNION ALL SELECT 'oauth2_client',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,client_id,client_id_issued_at,IFNULL(client_secret,'~'),IFNULL(client_secret_expires_at,'~'),client_name,client_authentication_methods,authorization_grant_types,IFNULL(redirect_uris,'~'),IFNULL(post_logout_redirect_uris,'~'),scopes,client_settings,token_settings))) FROM oauth2_registered_client
UNION ALL SELECT 'sys_dict_type',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,code,name,IFNULL(remark,'~'),is_enabled,create_time,update_time,is_delete))) FROM sys_dict_type
UNION ALL SELECT 'sys_dict_item',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,dict_type_id,label,value,sort,IFNULL(css_class,'~'),IFNULL(remark,'~'),is_enabled,create_time,update_time,is_delete))) FROM sys_dict_item
UNION ALL SELECT 'kp_tag',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,user_id,name,create_time,update_time,is_delete))) FROM kp_tag
UNION ALL SELECT 'kp_topic_tag',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,topic_id,tag_id,create_time,update_time,is_delete))) FROM kp_topic_tag
UNION ALL SELECT 'kp_topic',COUNT(*),SUM(CRC32(CONCAT_WS('|',id,user_id,title,IFNULL(content,'~'),IFNULL(parent_topic_id,'~'),IFNULL(tree_id,'~'),create_time,update_time,is_delete))) FROM kp_topic;
