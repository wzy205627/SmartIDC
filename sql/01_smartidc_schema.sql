-- ====================================================================
-- 《智维云 (SmartIDC)》数据库初始化脚本 - 01_smartidc_schema.sql
-- 严格基于 SDS.md 第四节数据库与存储设计
-- ====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

-- ----------------------------
-- 1. IDC机柜资产表
-- ----------------------------
DROP TABLE IF EXISTS `idc_rack`;
CREATE TABLE `idc_rack` (
  `rack_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '机架主键ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID (多租户隔离)',
  `room_name` VARCHAR(64) NOT NULL COMMENT '所属机房/区域 (如: 华东01-A区)',
  `rack_code` VARCHAR(32) NOT NULL COMMENT '机架编号 (如: A-03)',
  `total_u` INT NOT NULL DEFAULT 42 COMMENT '总可用U位 (默认42U)',
  `used_u` INT NOT NULL DEFAULT 0 COMMENT '已占用U位',
  `power_rating` DECIMAL(6,2) NOT NULL DEFAULT 5.00 COMMENT '额定供电容量(kVA)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-空闲, 1-托管使用中, 2-维保锁定',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`rack_id`),
  UNIQUE KEY `uk_room_code` (`tenant_id`, `room_name`, `rack_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IDC机柜资产表';

-- ----------------------------
-- 2. 机房设备资产与传感器表
-- ----------------------------
DROP TABLE IF EXISTS `idc_device`;
CREATE TABLE `idc_device` (
  `device_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `rack_id` BIGINT NOT NULL COMMENT '所在机柜ID',
  `device_name` VARCHAR(64) NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(32) NOT NULL COMMENT '设备类型: SENSOR_TEMP(温湿度), SENSOR_UPS(UPS), IT_SERVER(服务器), IT_SWITCH(网络交换机)',
  `iot_device_key` VARCHAR(64) DEFAULT NULL COMMENT '绑定的物联网平台 DeviceKey',
  `start_u` INT DEFAULT NULL COMMENT '起始U位位置',
  `u_height` INT DEFAULT 1 COMMENT '占用U位高度',
  `rated_power` DECIMAL(6,2) DEFAULT 0.50 COMMENT '额定功率(kW)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '运行状态: 0-离线, 1-正常, 2-告警, 3-故障',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`device_id`),
  KEY `idx_rack_id` (`rack_id`),
  KEY `idx_iot_key` (`iot_device_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房设备资产与传感器表';

-- ----------------------------
-- 3. 动环遥测时序快照表 (按天 Range 分区)
-- ----------------------------
DROP TABLE IF EXISTS `idc_telemetry_snapshot`;
CREATE TABLE `idc_telemetry_snapshot` (
  `snapshot_id` BIGINT NOT NULL AUTO_INCREMENT,
  `device_id` BIGINT NOT NULL COMMENT '设备/传感器ID',
  `rack_id` BIGINT NOT NULL COMMENT '机柜ID',
  `temperature` DECIMAL(4,1) DEFAULT NULL COMMENT '实时温度(℃)',
  `humidity` DECIMAL(4,1) DEFAULT NULL COMMENT '实时湿度(%RH)',
  `voltage` DECIMAL(6,2) DEFAULT NULL COMMENT '输入电压(V)',
  `current_amp` DECIMAL(6,2) DEFAULT NULL COMMENT '工作电流(A)',
  `power_kw` DECIMAL(6,2) DEFAULT NULL COMMENT '实时功耗(kW)',
  `sample_time` DATETIME NOT NULL COMMENT '遥测采样时间戳',
  PRIMARY KEY (`snapshot_id`, `sample_time`),
  KEY `idx_device_time` (`device_id`, `sample_time`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动环遥测时序快照表'
PARTITION BY RANGE (TO_DAYS(sample_time)) (
    PARTITION p20260914 VALUES LESS THAN (TO_DAYS('2026-09-15')),
    PARTITION p20260915 VALUES LESS THAN (TO_DAYS('2026-09-16')),
    PARTITION p20260916 VALUES LESS THAN (TO_DAYS('2026-09-17')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
);

-- ----------------------------
-- 4. 动环告警事件表
-- ----------------------------
DROP TABLE IF EXISTS `idc_alarm_event`;
CREATE TABLE `idc_alarm_event` (
  `alarm_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '告警主键ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `device_id` BIGINT NOT NULL COMMENT '触发设备ID',
  `rack_id` BIGINT NOT NULL COMMENT '关联机柜ID',
  `alarm_level` VARCHAR(16) NOT NULL COMMENT '告警等级: WARNING(预警), ERROR(一般), CRITICAL(严重)',
  `alarm_type` VARCHAR(32) NOT NULL COMMENT '类型: TEMP_HIGH(过温), POWER_FAIL(断电), WATER_LEAK(水浸)',
  `metric_value` VARCHAR(32) NOT NULL COMMENT '触发告警时的遥测指标值',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-触发中, 2-已派单处理, 3-已消除, 4-已标记误报',
  `rca_summary` TEXT DEFAULT NULL COMMENT 'Spring AI 自动推导的根因摘要',
  `trigger_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '告警触发时间',
  `clear_time` DATETIME DEFAULT NULL COMMENT '消除时间',
  PRIMARY KEY (`alarm_id`),
  KEY `idx_status_level` (`status`, `alarm_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动环告警事件表';

-- ----------------------------
-- 5. 运维巡检与 AIOps 智能工单表
-- ----------------------------
DROP TABLE IF EXISTS `idc_work_ticket`;
CREATE TABLE `idc_work_ticket` (
  `ticket_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '工单ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `alarm_id` BIGINT DEFAULT NULL COMMENT '关联的告警事件ID (可为空)',
  `title` VARCHAR(128) NOT NULL COMMENT '工单标题',
  `ticket_type` VARCHAR(32) NOT NULL COMMENT '类型: ALARM_REPAIR(故障排障), ROUTINE_CHECK(常规巡检), ASSET_MOVE(资产移机)',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待分配, 1-排障中, 2-挂起待审批, 3-已批准待执行, 4-已拒绝, 5-待消警复核, 6-已办结',
  `operator_id` BIGINT DEFAULT NULL COMMENT '当前指派运维工程师ID',
  `approver_id` BIGINT DEFAULT NULL COMMENT '人机协同审批主管ID',
  `checkpoint_id` VARCHAR(64) DEFAULT NULL COMMENT 'Spring AI Alibaba Graph 运行时状态快照ID',
  `sop_guide` TEXT DEFAULT NULL COMMENT 'RAG 检索召回的排障 SOP 建议',
  `process_notes` TEXT DEFAULT NULL COMMENT '现场处置记录与排障反馈',
  `evidence_object_key` VARCHAR(256) DEFAULT NULL COMMENT 'MinIO存证对象Key',
  `evidence_hash` VARCHAR(64) DEFAULT NULL COMMENT '照片SHA-256指纹',
  `rack_location` VARCHAR(128) DEFAULT NULL COMMENT '机架权威物理位置',
  `resolve_time` DATETIME DEFAULT NULL COMMENT '服务端消警时间戳',
  `finish_time` DATETIME DEFAULT NULL COMMENT '办结时间',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`ticket_id`),
  KEY `idx_checkpoint` (`checkpoint_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维巡检与 AIOps 智能工单表';

-- ----------------------------
-- 6. 租户机房能耗与租赁计费结算表
-- ----------------------------
DROP TABLE IF EXISTS `idc_billing_statement`;
CREATE TABLE `idc_billing_statement` (
  `bill_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '账单主键ID',
  `tenant_id` VARCHAR(20) NOT NULL COMMENT '租户ID',
  `billing_month` VARCHAR(7) NOT NULL COMMENT '结算月份: 如 2026-08',
  `rack_rental_fee` DECIMAL(10,2) NOT NULL COMMENT '机位租赁固定费用(元)',
  `power_kwh` DECIMAL(10,2) NOT NULL COMMENT '当月实际耗电度数(kWh)',
  `power_fee` DECIMAL(10,2) NOT NULL COMMENT '设备电费总额(元)',
  `pue_factor` DECIMAL(4,2) NOT NULL DEFAULT 1.25 COMMENT '当月机房核算 PUE 能效因子',
  `pue_share_fee` DECIMAL(10,2) NOT NULL COMMENT '制冷与动环公摊能耗费(元)',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '应付总金额',
  `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态: 0-未支付, 1-已扣款结清, 2-逾期欠费',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '账单生成时间',
  PRIMARY KEY (`bill_id`),
  UNIQUE KEY `uk_tenant_month` (`tenant_id`, `billing_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户机房能耗与租赁计费结算表';

-- ----------------------------
-- 7. 基础 RBAC 用户表 (配合多租户与鉴权)
-- ----------------------------
DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
  `user_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID',
  `username` VARCHAR(64) NOT NULL COMMENT '用户账号',
  `password` VARCHAR(128) NOT NULL COMMENT '密码哈希',
  `nick_name` VARCHAR(64) NOT NULL COMMENT '用户昵称',
  `role_key` VARCHAR(32) NOT NULL COMMENT '角色标识: admin, supervisor, engineer, auditor',
  `phone` VARCHAR(20) DEFAULT NULL COMMENT '手机号码',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '账号状态: 0-正常, 1-停用',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`user_id`),
  UNIQUE KEY `uk_tenant_username` (`tenant_id`, `username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户信息表';

SET FOREIGN_KEY_CHECKS = 1;
