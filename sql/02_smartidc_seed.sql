-- ====================================================================
-- 《智维云 (SmartIDC)》初始化种子数据 - 02_smartidc_seed.sql
-- ====================================================================

SET NAMES utf8mb4;

-- 1. 初始化预设用户 (密码统一默认: 123456 的明文/BCrypt哈希)
-- $2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2 对应 123456
INSERT INTO `sys_user` (`user_id`, `tenant_id`, `username`, `password`, `nick_name`, `role_key`, `phone`, `status`) VALUES
(1, '000000', 'admin', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '系统超级管理员', 'admin', '13800000000', 0),
(2, '000000', 'supervisor_zhang', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '张主管 (IDC值班主管)', 'supervisor', '13800000001', 0),
(3, '000000', 'engineer_li', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '李工 (驻场运维工程师)', 'engineer', '13800000002', 0),
(4, 'T10001', 'tenant_demo', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '示范租户管理员', 'tenant_admin', '13800000003', 0);

-- 2. 初始化示范机柜资产 (华东01-A区: A-01 至 A-06 平台方自营; 华东02-B区: B-01 至 B-02 示范托管租户)
INSERT INTO `idc_rack` (`rack_id`, `tenant_id`, `room_name`, `rack_code`, `total_u`, `used_u`, `power_rating`, `status`) VALUES
(1, '000000', '华东01-A区', 'A-01', 42, 0, 5.00, 0),
(2, '000000', '华东01-A区', 'A-02', 42, 1, 5.00, 1),
(3, '000000', '华东01-A区', 'A-03', 42, 8, 6.00, 1),
(4, '000000', '华东01-A区', 'A-04', 42, 0, 5.00, 0),
(5, '000000', '华东01-A区', 'A-05', 42, 0, 4.00, 0),
(6, '000000', '华东01-A区', 'A-06', 42, 0, 5.00, 0),
(7, 'T10001', '华东02-B区', 'B-01', 42, 8, 6.00, 1),
(8, 'T10001', '华东02-B区', 'B-02', 42, 0, 5.00, 0);

-- 3. 初始化动环与 IT 设备
INSERT INTO `idc_device` (`device_id`, `tenant_id`, `rack_id`, `device_name`, `device_type`, `iot_device_key`, `start_u`, `u_height`, `rated_power`, `status`) VALUES
(1, '000000', 3, '机柜A-03温湿度传感器', 'SENSOR_TEMP', 'TH-A03-001', 1, 1, 0.05, 1),
(2, '000000', 3, '机柜A-03智能PDU电表', 'SENSOR_UPS', 'PDU-A03-001', 2, 1, 0.10, 1),
(3, '000000', 3, '核心交换机 H3C-S6800', 'IT_SWITCH', 'SW-A03-001', 40, 2, 0.80, 1),
(4, '000000', 3, 'GPU计算服务器 Inspur-NF5468', 'IT_SERVER', 'SRV-A03-001', 20, 4, 2.20, 1),
(5, '000000', 2, '机柜A-02温湿度传感器', 'SENSOR_TEMP', 'TH-A02-001', 1, 1, 0.05, 1),
(9, 'T10001', 7, '华东算力-高密GPU训练节点', 'IT_SERVER', 'SRV-B01-001', 20, 4, 2.50, 1),
(10, 'T10001', 7, '华东算力-汇聚交换机', 'IT_SWITCH', 'SW-B01-001', 40, 2, 0.80, 1),
(11, 'T10001', 7, 'B-01智能PDU电表', 'SENSOR_UPS', 'PDU-B01-001', 2, 1, 0.10, 1),
(12, 'T10001', 7, 'B-01温湿度传感器', 'SENSOR_TEMP', 'TH-B01-001', 1, 1, 0.05, 1);
