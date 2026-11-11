-- ============================================================
-- 易租宝 · 完整建表脚本（20 张表，纯 DDL，无任何业务数据）
-- 用法：mysql -uroot -p rental_db < rental-platform/src/main/resources/sql/schema.sql
-- 说明：由云端结构导出提取，已剔除全部 INSERT 与 AUTO_INCREMENT 值
-- ============================================================
CREATE DATABASE IF NOT EXISTS `rental_db` DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci;
USE `rental_db`;

CREATE TABLE `house_image` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '图片ID',
  `house_id` bigint NOT NULL COMMENT '所属房源',
  `image_url` varchar(500) NOT NULL COMMENT '图片地址',
  `caption` varchar(500) DEFAULT NULL COMMENT '图片描述（CLIP 文字检索靠它 —— 讲义05：图片没文字，靠caption被文字命中）',
  `vector_id` varchar(128) DEFAULT NULL COMMENT 'CLIP 向量ID（img 索引）',
  `ocr_text` text COMMENT 'OCR 识别出的文字（合同扫描件用）',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_house` (`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='房源图片（多模态）';
CREATE TABLE `kb_chunk` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '分片ID',
  `doc_id` bigint NOT NULL COMMENT '所属文档',
  `chunk_index` int NOT NULL COMMENT '序号',
  `parent_text` text COMMENT '父块（喂给 LLM，约2000字）',
  `child_text` text NOT NULL COMMENT '子块（用于检索，约500字）',
  `vector_id` varchar(128) DEFAULT NULL COMMENT 'FAISS/ES 中的向量ID',
  `token_count` int DEFAULT NULL COMMENT 'Token 数',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_doc` (`doc_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='文档分片（父子分片：child检索命中→parent喂LLM）';
CREATE TABLE `kb_document` (
  `id` bigint NOT NULL AUTO_INCREMENT COMMENT '文档ID',
  `user_id` bigint DEFAULT NULL COMMENT '维护人（管理员）',
  `title` varchar(255) NOT NULL COMMENT '文档标题',
  `source_type` varchar(20) NOT NULL COMMENT 'LAW-法规 / GUIDE-攻略 / POLICY-政策',
  `city` varchar(50) DEFAULT NULL COMMENT '适用城市（空=全国）',
  `content` longtext COMMENT '原文',
  `chunk_count` int DEFAULT '0' COMMENT '分片数',
  `status` tinyint DEFAULT '1' COMMENT '1正常 0删除',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_type_city` (`source_type`,`city`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='知识库文档';
CREATE TABLE `t_contract_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `house_id` bigint DEFAULT NULL COMMENT '关联房源',
  `title` varchar(200) DEFAULT NULL COMMENT '合同名称',
  `image_url` varchar(500) DEFAULT NULL COMMENT '合同图片URL',
  `content_text` text COMMENT 'OCR/用户输入的合同文本',
  `ai_analysis` text COMMENT 'AI分析结果JSON',
  `risk_points` text COMMENT '风险点JSON',
  `overall_risk` varchar(20) DEFAULT NULL COMMENT '综合风险: HIGH/MEDIUM/LOW',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING-处理中, COMPLETED-完成',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_demo_seed_record` (
  `batch` varchar(40) NOT NULL,
  `kind` varchar(20) NOT NULL,
  `ordinal` int NOT NULL,
  `entity_id` bigint NOT NULL,
  PRIMARY KEY (`batch`,`kind`,`ordinal`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_guide_comment` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `guide_id` bigint NOT NULL,
  `user_id` bigint NOT NULL,
  `content` text NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_guide` (`guide_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_guide_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `guide_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_guide` (`user_id`,`guide_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_house` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `landlord_id` bigint NOT NULL COMMENT '房东用户ID',
  `title` varchar(200) NOT NULL COMMENT '房源标题',
  `description` text COMMENT '房源描述',
  `city` varchar(50) NOT NULL COMMENT '城市',
  `district` varchar(50) DEFAULT NULL COMMENT '区域',
  `address` varchar(300) NOT NULL COMMENT '详细地址',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `price` decimal(10,2) NOT NULL COMMENT '月租金(元)',
  `area` decimal(8,2) DEFAULT NULL COMMENT '面积(㎡)',
  `bedrooms` int DEFAULT '1' COMMENT '卧室数',
  `living_rooms` int DEFAULT '0' COMMENT '客厅数',
  `bathrooms` int DEFAULT '1' COMMENT '卫生间数',
  `floor_info` varchar(50) DEFAULT NULL COMMENT '楼层信息(如: 12/28层)',
  `orientation` varchar(20) DEFAULT NULL COMMENT '朝向(南/北/东/西/南北)',
  `decoration` varchar(20) DEFAULT NULL COMMENT '装修(精装/简装/毛坯)',
  `facilities` varchar(500) DEFAULT NULL COMMENT '设施JSON: ["冰箱","洗衣机","空调"]',
  `subway_station_id` bigint DEFAULT NULL COMMENT '最近地铁站ID',
  `distance_to_subway` int DEFAULT NULL COMMENT '距地铁站距离(米)',
  `cover_image` varchar(500) DEFAULT NULL COMMENT '封面图URL',
  `images` text COMMENT '图片JSON数组',
  `contact_phone` varchar(20) DEFAULT NULL COMMENT '联系电话',
  `view_count` int DEFAULT '0' COMMENT '浏览次数',
  `status` varchar(20) DEFAULT 'PENDING' COMMENT 'PENDING-待审, APPROVED-上架, REJECTED-拒绝, OFFLINE-下架',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`),
  KEY `idx_landlord` (`landlord_id`),
  KEY `idx_status` (`status`),
  KEY `idx_price` (`price`),
  KEY `idx_subway` (`subway_station_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_house_favorite` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `user_id` bigint NOT NULL,
  `house_id` bigint NOT NULL,
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_house` (`user_id`,`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_house_report` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `house_id` bigint NOT NULL,
  `reporter_id` bigint NOT NULL,
  `category` varchar(40) NOT NULL,
  `content` varchar(2000) NOT NULL,
  `evidence_ids` text NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `response` varchar(1000) DEFAULT NULL,
  `handled_by` bigint DEFAULT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `handled_at` datetime DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_report_owner` (`reporter_id`,`created_at`),
  KEY `idx_report_status` (`status`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_house_trust` (
  `house_id` bigint NOT NULL,
  `owner_id` bigint NOT NULL,
  `publisher_type` varchar(20) NOT NULL,
  `applicant_name` varchar(80) NOT NULL,
  `identity_id` varchar(36) NOT NULL,
  `property_id` varchar(36) NOT NULL,
  `authorization_id` varchar(36) DEFAULT NULL,
  `photo_ids` text NOT NULL,
  `deposit_months` int NOT NULL,
  `payment_months` int NOT NULL,
  `service_fee` decimal(12,2) NOT NULL,
  `other_fees` varchar(1000) NOT NULL,
  `utility_terms` varchar(1000) NOT NULL,
  `status` varchar(20) NOT NULL DEFAULT 'PENDING',
  `review_note` varchar(1000) DEFAULT NULL,
  `reviewed_by` bigint DEFAULT NULL,
  `reviewed_at` datetime DEFAULT NULL,
  `submitted_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `confirmed_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `consent_version` varchar(40) NOT NULL,
  PRIMARY KEY (`house_id`),
  KEY `idx_trust_status` (`status`,`submitted_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_landlord_review` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `reviewer_id` bigint NOT NULL COMMENT '评价人',
  `landlord_id` bigint NOT NULL COMMENT '被评价房东',
  `house_id` bigint DEFAULT NULL COMMENT '相关房源',
  `score` int NOT NULL COMMENT '评分1-5',
  `content` text COMMENT '评价内容',
  `is_anonymous` tinyint DEFAULT '0' COMMENT '是否匿名',
  `status` varchar(20) DEFAULT 'APPROVED' COMMENT 'APPROVED/PENDING/REJECTED',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_landlord` (`landlord_id`),
  KEY `idx_house` (`house_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_private_conversation` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `house_id` bigint NOT NULL,
  `tenant_id` bigint NOT NULL,
  `landlord_id` bigint NOT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  `updated_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_private_house_tenant` (`house_id`,`tenant_id`),
  KEY `idx_private_tenant` (`tenant_id`,`updated_at`),
  KEY `idx_private_landlord` (`landlord_id`,`updated_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_private_draft` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `conversation_id` bigint NOT NULL,
  `content` text NOT NULL,
  `expires_at` datetime(6) NOT NULL,
  `sent_message_id` bigint DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  KEY `idx_private_draft_owner` (`owner_id`,`expires_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_private_message` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `conversation_id` bigint NOT NULL,
  `sender_id` bigint NOT NULL,
  `content` text NOT NULL,
  `source` varchar(12) NOT NULL DEFAULT 'USER',
  `request_id` varchar(80) NOT NULL,
  `read_at` datetime(6) DEFAULT NULL,
  `created_at` datetime(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  PRIMARY KEY (`id`),
  UNIQUE KEY `uq_private_request` (`sender_id`,`request_id`),
  KEY `idx_private_history` (`conversation_id`,`id`),
  KEY `idx_private_unread` (`conversation_id`,`read_at`,`sender_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_rental_guide` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `author_id` bigint NOT NULL,
  `title` varchar(200) NOT NULL,
  `content` text NOT NULL,
  `category` varchar(50) DEFAULT NULL COMMENT '分类: 看房技巧/合同知识/搬家经验/区域推荐',
  `cover_image` varchar(500) DEFAULT NULL,
  `view_count` int DEFAULT '0',
  `like_count` int DEFAULT '0',
  `comment_count` int DEFAULT '0',
  `is_featured` tinyint DEFAULT '0' COMMENT '精选',
  `status` varchar(20) DEFAULT 'APPROVED' COMMENT 'APPROVED/PENDING/REJECTED',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP,
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_author` (`author_id`),
  KEY `idx_category` (`category`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_subway_station` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `city` varchar(50) NOT NULL COMMENT '城市',
  `line_name` varchar(50) NOT NULL COMMENT '线路名(如: 1号线)',
  `station_name` varchar(100) NOT NULL COMMENT '站名',
  `latitude` decimal(10,7) DEFAULT NULL COMMENT '纬度',
  `longitude` decimal(10,7) DEFAULT NULL COMMENT '经度',
  `sort_order` int DEFAULT '0' COMMENT '在线路中的顺序',
  PRIMARY KEY (`id`),
  KEY `idx_city` (`city`),
  KEY `idx_line` (`city`,`line_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_trust_audit` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `house_id` bigint DEFAULT NULL,
  `actor_id` bigint NOT NULL,
  `action` varchar(40) NOT NULL,
  `note` varchar(1000) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_audit_house` (`house_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_trust_file` (
  `id` varchar(36) NOT NULL,
  `owner_id` bigint NOT NULL,
  `house_id` bigint DEFAULT NULL,
  `kind` varchar(20) NOT NULL,
  `mime` varchar(80) NOT NULL,
  `bytes` bigint NOT NULL,
  `sha256` varchar(64) NOT NULL,
  `created_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `idx_trust_file_owner` (`owner_id`),
  KEY `idx_trust_hash` (`sha256`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
CREATE TABLE `t_user` (
  `id` bigint NOT NULL AUTO_INCREMENT,
  `username` varchar(50) NOT NULL,
  `password` varchar(255) NOT NULL,
  `email` varchar(100) DEFAULT NULL,
  `phone` varchar(20) DEFAULT NULL,
  `bio` varchar(500) DEFAULT NULL,
  `points` int DEFAULT '0',
  `level` varchar(20) DEFAULT '普通用户',
  `status` tinyint DEFAULT '1',
  `create_time` datetime DEFAULT CURRENT_TIMESTAMP,
  `update_time` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  `role` varchar(20) DEFAULT 'TENANT' COMMENT '角色: TENANT-租客, LANDLORD-房东, ADMIN-管理员',
  `real_name` varchar(50) DEFAULT NULL COMMENT '真实姓名',
  `id_card` varchar(18) DEFAULT NULL COMMENT '身份证号(房东必填)',
  `avatar` varchar(255) DEFAULT NULL COMMENT '头像URL',
  `gender` varchar(10) DEFAULT NULL COMMENT '性别',
  PRIMARY KEY (`id`),
  UNIQUE KEY `username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
