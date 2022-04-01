SET FOREIGN_KEY_CHECKS=0;
SET SQL_NOTES = 0;
-- ----------------------------
-- Table structure for object_list
-- ----------------------------
CREATE TABLE IF NOT EXISTS `object_list` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provider` varchar(32) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'COS' COMMENT '供应商',
  `bucket` varchar(64) CHARACTER SET utf8 COLLATE utf8_general_ci NOT NULL COMMENT '桶名称',
  `object_key` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci NOT NULL,
  `object_size` bigint(20) DEFAULT NULL,
  `meta_data` json DEFAULT NULL,
  `file_md5` char(32) CHARACTER SET sjis COLLATE sjis_japanese_ci DEFAULT NULL,
  `expires` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  `last_check_status` smallint(4) DEFAULT '0' COMMENT '最后检测状态，0:未检测，1：存在，2：存在但状态异常，-1检测失败',
  `start_time` datetime DEFAULT NULL,
  `last_check_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `provider` (`provider`,`bucket`,`object_key`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4;

-- ----------------------------
-- Table structure for options
-- ----------------------------
CREATE TABLE IF NOT EXISTS `options` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `option_key` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `option_value` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `option_key_uniq` (`option_key`)
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ----------------------------
-- Table structure for transfer
-- ----------------------------
CREATE TABLE IF NOT EXISTS `transfer` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `provider` varchar(10) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT 'qn' COMMENT 'cdn供应商',
  `bucket` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL,
  `target_provider` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '目标存储供应商',
  `target_bucket` varchar(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标存储bucket',
  `obj` varchar(512) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci DEFAULT NULL,
  `object_size` bigint(64) DEFAULT NULL,
  `cdn_domain` varchar(255) COLLATE utf8mb4_general_ci DEFAULT NULL COMMENT '下载加速域名，七牛有效',
  `transfer_status` smallint(4) DEFAULT '0' COMMENT '0:未同步，1:同步完成，-1同步失败',
  `create_time` datetime DEFAULT NULL,
  `update_time` datetime DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`id`),
  KEY `obj_bucket` (`bucket`,`obj`) USING BTREE,
  KEY `obj` (`obj`) USING BTREE
) ENGINE=InnoDB AUTO_INCREMENT=1 DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;
SET SQL_NOTES = 1;