-- 生成操作日志表
CREATE TABLE IF NOT EXISTS `generator_generation_log` (
  `id` varchar(64) NOT NULL COMMENT '主键',
  `table_id` varchar(64) DEFAULT NULL COMMENT '表ID',
  `scheme_id` varchar(64) DEFAULT NULL COMMENT '方案ID',
  `template_scheme_id` varchar(64) DEFAULT NULL COMMENT '模板方案ID',
  `entity_name` varchar(255) DEFAULT NULL COMMENT '实体名',
  `operation_type` varchar(32) NOT NULL COMMENT '操作类型: DRY_RUN / GENERATE',
  `dry_run_id` varchar(64) DEFAULT NULL COMMENT '预检ID，GENERATE时关联到对应的DRY_RUN',
  `file_count` int(11) DEFAULT '0' COMMENT '文件总数',
  `new_count` int(11) DEFAULT '0' COMMENT '新增文件数',
  `overwrite_count` int(11) DEFAULT '0' COMMENT '覆盖文件数',
  `skip_count` int(11) DEFAULT '0' COMMENT '跳过文件数',
  `risk_count` int(11) DEFAULT '0' COMMENT '风险文件数',
  `result_json` text COMMENT '预检结果JSON',
  `create_date` datetime DEFAULT NULL COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_dry_run_id` (`dry_run_id`),
  KEY `idx_table_id` (`table_id`),
  KEY `idx_operation_type` (`operation_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='代码生成操作日志';
