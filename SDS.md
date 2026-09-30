《智维云 (SmartIDC)：企业级数据中心动环监控与 AIOps 智能运维平台》系统设计规范书
一、 项目介绍、业务背景与应用场景
1.1 项目定位与核心价值
智维云（SmartIDC） 是一套面向中大型企业自有数据中心、云服务商及托管机房的软硬件一体化绿色动环（动力与环境）监控、资产管理与 AIOps 智能运维协同中台。
系统在底层基于阿里云 IoT / EMQX 实现温湿度、UPS、精密空调、配电柜等多维动环遥测数据的毫秒级采集与告警；在中层依托若依（RuoYi）多租户与 RBAC 权限体系进行空间资产与工单状态机流转；在上层深度整合 Spring AI Alibaba (Graph & MCP)，构建具备“动环异常实时感知 → AIOps 根因分析（RCA） → 应急预案（SOP）推荐 → 人机在环受控排障”的生产级闭环。

1.2 行业痛点与应用场景
机房动环盲区与告警风暴：

痛点：传统机房各子系统（供电、制冷、消防、动环）数据割裂。一旦空调断电或压缩机故障，往往瞬间触发数十条衍生告警（如多机柜同时高温越限），运维人员深陷“告警风暴”，极难在 5 分钟黄金时间内判断初始根因。

场景：金融机房、IDC 托管中心实时大屏监控、断电/过温声光报警联动。

重度依赖专家经验与排障滞后：

痛点：传统设备维保靠纸质巡检或静态工单，现场工程师处理冷机跳闸、UPS 旁路切换等高危操作必须反复翻阅厚重 SOP，处理周期（MTTR）长。

场景：工程师通过微信小程序（Uni-app）扫码机柜/设备二维码，AI 即时推送当前机柜动环健康画像及历史维修排障 SOP。

PUE 能耗核算与租赁结算繁琐：

痛点：多租户托管模式下，机柜空间占用租金、设备实际用电度数（阶梯电价）与公摊制冷能耗（PUE）通常由财务手工拉表格核算，易错漏且账期拖延。

场景：按月全自动生成机柜租金账单与 PUE 分摊账单，支持租户线上对账与在线充值。
二、 需求分析与领域建模 (DDD)
2.1 角色用例模型
驻场运维工程师（Field Engineer）：使用移动随行端（小程序）扫码巡检、接收越限告警推送、执行排障自愈工单、填报现场消警记录。

IDC 运营主管 / 审批人（Supervisor）：Web 端监控综合态势大屏；接收高危操作（如服务器紧急断电、备用发电机冷启动）的人机协同审批卡片，一键决策。

机房资产与客户管理员（Asset Admin）：负责租户管理、机房楼层/机架U位拓扑规划、设备上架/移机/下架办理。

财务与能效审计员（Auditor）：设定阶梯电价规则、监控整机房 PUE 能效指标、审核每月托管租金与能耗对账单。

2.2 核心领域聚合根与实体设计 (DDD)
┌─────────────────────────────────────────────────────────────────────────────┐
│ 1. 空间与资产聚合 (Space & Asset Aggregate)                                  │
│   · 聚合根: Room (机房)                                                     │
│   · 实体: Cabinet (机柜/机架) -> Slot (U位资源, 1U-42U)                      │
│   · 实体: ItDevice (IT设备: 服务器/交换机) -> Module (双电源模块/网卡)        │
│   · 实体: Tenant (托管租户)                                                 │
├─────────────────────────────────────────────────────────────────────────────┤
│ 2. 动环物联聚合 (IoT Telemetry Aggregate)                                   │
│   · 聚合根: SensorDevice (传感器物模型: 温湿度/UPS/水浸/电表)                │
│   · 值对象: TelemetryMetric (遥测采样值: 温度、湿度、电压、有功功率、电流)  │
│   · 实体: AlarmRule (多级报警阈值规则: 预警/告警/严重)                      │
├─────────────────────────────────────────────────────────────────────────────┤
│ 3. 告警与 AIOps 决策聚合 (Alarm & AIOps Aggregate)                          │
│   · 聚合根: AlarmEvent (告警事件单)                                         │
│   · 值对象: RCAContext (根因分析上下文: 拓扑受损链路、上下游关联设备)       │
│   · 实体: AgentCheckpoint (Graph 状态断点快照)                              │
├─────────────────────────────────────────────────────────────────────────────┤
│ 4. 工单流转与能效计费聚合 (Ticket & Billing Aggregate)                       │
│   · 聚合根: WorkTicket (运维巡检/故障处置工单)                              │
│   · 实体: MeteringRecord (机柜电表读数流水)                                 │
│   · 实体: BillStatement (月度能耗分摊结算单)                                │
└─────────────────────────────────────────────────────────────────────────────┘
2.3 状态机模型设计
A. 动环告警风暴抑制状态机
遥测数据高频上报时，引入滑动窗口震荡抑制算法，避免阈值临界点频繁报警：

[遥测数据超限] ──> (PENDING 疑似触发) 
                         │ (连续3次采样越限 或 超限持续10s)
                         ▼
                   (ACTIVE 真实报警) ──> [触发 WebSocket 广播 & 派发 AI 根因推导]
                         │
             ┌───────────┴───────────┐
             ▼ (指标恢复安全阈值)    ▼ (运维现场消警或置为误报)
   (CLEARED 自动恢复)       (CLOSED 已手动关闭)
B. AIOps 智能工单与人机在环状态机
(CREATED) ──> [AI 匹配 SOP / 推导根因] ──> (AUTO_TRIAGING 智能排障中)
                                                  │
             ┌────────────────────────────────────┴────────────────────────────────────┐
             ▼ (只读排查/常规低风险工单)                                               ▼ (判定为高危操作: 切电/重启冷机)
   (ASSIGNED 自动派发运维)                                                   (HITL_SUSPENDED 挂起等待审批)
             │                                                                         │
             ▼                                                              ┌──────────┴──────────┐
   (PROCESSING 现场处理中)                                                  ▼ (主管批准)          ▼ (主管驳回)
             │                                                       (APPROVED 恢复执行)    (REJECTED 已终止)
             ▼                                                              │
   (RESOLVED 待复核消警) ───────────────────────────────────────────────────┘
             │
             ▼
   (COMPLETED 已归档归档)
三、 功能架构与产品原型交互设计
3.1 总体功能架构矩阵 (对标图二布局)
Plaintext
┌────────────────────────────────────────────────────────────────────────────────────────┐  ┌──────────────────┐
│ 移动随行端 (Uni-app 微信小程序 - 驻场工程师与租户视角)                                 │  │   第三方生态集成 │
├───────────────┬────────────────┬─────────────────┬─────────────────┬───────────────────┤  ├──────────────────┤
│   快捷接入    │    动环排障    │    工单流转     │    空间申请     │     费用结算      │  │  阿里云 IoT 平台 │
├───────────────┼────────────────┼─────────────────┼─────────────────┼───────────────────┤  │  (物模型与接入)  │
│ · SSO 单点登录│ · 扫码查看机柜 │ · 现场拍照提单  │ · 租户入驻报备  │ · 月度电费推送    │  ├──────────────────┤
│ · 个人工作待办│ · 温湿度实时查 │ · 异常告警接单  │ · 机架U位扩容   │ · 微信支付/对账   │  │   微信支付 / 结算│
│ · 临时出入机房│ · 应急 SOP 查阅│ · 处理过程记录  │ · 临时操作授权  │ · 欠费阈值预警    │  ├──────────────────┤
│ · 历史报修记录│ · 现场一键消警 │ · 故障办结归档  │ · 备件领用登记  │ · 电子发票开具    │  │  MinIO / 阿里云 OSS│
└───────────────┴────────────────┴─────────────────┴─────────────────┴───────────────────┘  │  (巡检现场存证)  │
                                                                                            ├──────────────────┤
┌────────────────────────────────────────────────────────────────────────────────────────┐  │  通义千问大模型  │
│ 管理运维中台 (Admin Web 控制台 - 调度指挥与运营主管视角)                               │  │  (RCA 根因分析)  │
├───────────────┬────────────────┬───────────────────────────────────────────────────────┤  ├──────────────────┤
│   系统协同    │  数字孪生大屏  │                       动环实时监测与告警              │  │  企业微信 / 钉钉 │
├───────────────┼────────────────┼───────────────────┬───────────────────────────────────┤  │  (告警秒级触达)  │
│ · 账号密码/MFA│ · 动环 2D 拓扑 │ · 实时越限报警池  │ · 告警风暴抑制规则                │  ├──────────────────┤
│ · 租户角色切换│ · PUE 能效分析 │ · 声光报警联动    │ · 告警向工单自动转化              │  │  Prometheus 体系 │
│ · 退出与注销  │ · 空间容量热力 │ · 历史告警回溯    │ · 传感器离线监测                  │  │  (服务器指标监控)│
├───────────────┼────────────────┴───────────────────┴───────────────────────────────────┤  └──────────────────┘
│   资产全生命周期管理                                  │   AIOps 智能诊断与编排中枢    │
├───────────────────────────────────┬───────────────────┼───────────────────────────────┤
│ · 机房/列头柜/机架U位拓扑分配     │ · 资产维保到期预警│ · 故障 SOP 知识切片管理 (RAG) │
│ · IT 设备入库、上架、移机、退役   │ · 备品备件库存预警│ · Graph 状态快照挂起与手动唤醒│
│ · 租户托管资产归属台账            │ · 资产拓扑依赖梳理│ · Agent 思考链追踪与 Prompt调优│
├───────────────────────────────────┴───────────────────┴───────────────────────────────┤
│   财务能耗结算中心                                    │   消息协同中心                │
├───────────────────────────────────┬───────────────────┼───────────────────────────────┤
│ · 租户机柜租金月度账单            │ · 欠费断电预警机制│ · WebSocket 告警弹窗          │
│ · 阶梯电费 + PUE 公摊核算         │ · 保证金扣减与流水│ · 钉钉/企微告警规则绑定       │
├───────────────────────────────────┴─────────────那么AgentScope这个框架呢？其内部的Harness、Skills的2的──────┴───────────────────────────────┤
│   权限体系与底层基座 (RuoYi 改造)                                                     │
├───────────────────────────────────┬───────────────────┬───────────────────────────────┤
│ · 多租户数据强隔离 (TenantId)     │ · 岗位与用户管理  │ · JSqlParser SQL AST 安全校验 │
│ · 机房级行级数据权限切面          │ · RBAC 菜单鉴权   │ · Spring Task 巡检与计费调度  │
└───────────────────────────────────────────────────────────────────────────────────────┘
3.2 交互产品原型设计 (Web 中台端)
┌─────────────────────────────────────────────────────────────────────────────────┐
│ 智维云 SmartIDC 监控指挥中心                  [租户: 华东一区] [主管: 张工]     │
├──────────────────────────────────────┬──────────────────────────────────────────┤
│ [模块 1: 实时 2D 机房动环拓扑大屏]   │ [模块 2: 告警事件与 AIOps 排障联动抽屉]  │
│                                      │                                          │
│ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐ ┌───┐  │ 🚨 严重告警: #AL-9021                    │
│ │A01│ │A02│ │A03│ │A04│ │A05│ │A06│  │ 设备: 精密空调 #AC-02 (A区) 压缩机跳闸   │
│ │22℃│ │23℃│ │28℃│ │24℃│ │23℃│ │22℃│  │ 触发时间: 2026-09-14 18:40:12             │
│ └───┘ └───┘ └───┘ └───┘ └───┘ └───┘  ├──────────────────────────────────────────┤
│   ▲           ▲                      │ [Spring AI Alibaba 根因推导链 (RCA)]:    │
│  正常      [过温报警]                │  [√] 聚合关联告警: 机柜 A03、A04 温度越限│
│                                      │  [√] 拓扑依赖追溯: 关联精密空调 #AC-02   │
│ PUE 指标: 1.28 (优)                  │  [√] 调取 SOP 知识库: 《冷机跳闸应急处置》│
│ 总负载: 384.5 kW                     │  [!] 判定操作等级: 涉及启动备用冷机(HIGH)│
│                                      ├──────────────────────────────────────────┤
│ 当前活动告警: 3 起 (1 严重 / 2 预警) │ [人机协同审批卡片 (Human-in-the-Loop)]:  │
│ 自动派单率: 92%                      │ 建议动作: 自动激活备用制冷回路 (AC-03)    │
│ 挂起审批: 1 项                       │ 流程状态: 【已挂起等待主管张工审批】     │
│                                      │                                          │
│                                      │ [  批准执行切换  ]    [  转人工处置  ]   │
└──────────────────────────────────────┴──────────────────────────────────────────┘
四、 数据库与存储设计 (MySQL 8.0 DDL)
针对高频物联网数据与严格业务审计，设计以下 6 张核心领域表：

SQL
-- 1. 机架/机柜基础表
CREATE TABLE `idc_rack` (
  `rack_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '机架主键ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000' COMMENT '租户ID (多租户隔离)',
  `room_name` VARCHAR(64) NOT NULL COMMENT '所属机房/区域 (如: 华东01-A区)',
  `rack_code` VARCHAR(32) NOT NULL COMMENT '机架编号 (如: A-03)',
  `total_u` INT NOT NULL DEFAULT 42 COMMENT '总可用U位 (默认42U)',
  `used_u` INT NOT NULL DEFAULT 0 COMMENT '已占用U位',
  `power_rating` DECIMAL(6,2) NOT NULL DEFAULT 5.00 COMMENT '额定供电容量(kVA)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-空闲, 1-托管使用中, 2-维保锁定',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`rack_id`),
  UNIQUE KEY `uk_room_code` (`tenant_id`, `room_name`, `rack_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='IDC机柜资产表';

-- 2. IT与动环设备表
CREATE TABLE `idc_device` (
  `device_id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '设备ID',
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000',
  `rack_id` BIGINT NOT NULL COMMENT '所在机柜ID',
  `device_name` VARCHAR(64) NOT NULL COMMENT '设备名称',
  `device_type` VARCHAR(32) NOT NULL COMMENT '设备类型: SENSOR_TEMP(温湿度), SENSOR_UPS(UPS), IT_SERVER(服务器), IT_SWITCH(网络交换机)',
  `iot_device_key` VARCHAR(64) DEFAULT NULL COMMENT '绑定的物联网平台 DeviceKey',
  `start_u` INT DEFAULT NULL COMMENT '起始U位位置',
  `u_height` INT DEFAULT 1 COMMENT '占用U位高度',
  `rated_power` DECIMAL(6,2) DEFAULT 0.50 COMMENT '额定功率(kW)',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '运行状态: 0-离线, 1-正常, 2-告警, 3-故障',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`device_id`),
  KEY `idx_rack_id` (`rack_id`),
  KEY `idx_iot_key` (`iot_device_key`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='机房设备资产与传感器表';

-- 3. 动环时序遥测汇总采样表 (按天分区或配合 Redis 缓存冷热分离)
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
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 
PARTITION BY RANGE (TO_DAYS(sample_time)) (
    PARTITION p20260914 VALUES LESS THAN (TO_DAYS('2026-09-15')),
    PARTITION p20260915 VALUES LESS THAN (TO_DAYS('2026-09-16')),
    PARTITION p_future VALUES LESS THAN MAXVALUE
) COMMENT='动环遥测时序快照表';

-- 4. 告警事件记录表
CREATE TABLE `idc_alarm_event` (
  `alarm_id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000',
  `device_id` BIGINT NOT NULL,
  `rack_id` BIGINT NOT NULL,
  `alarm_level` VARCHAR(16) NOT NULL COMMENT '告警等级: WARNING(预警), ERROR(一般), CRITICAL(严重)',
  `alarm_type` VARCHAR(32) NOT NULL COMMENT '类型: TEMP_HIGH(过温), POWER_FAIL(断电), WATER_LEAK(水浸)',
  `metric_value` VARCHAR(32) NOT NULL COMMENT '触发告警时的遥测指标值',
  `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 1-触发中, 2-已派单处理, 3-已消除, 4-已标记误报',
  `rca_summary` TEXT DEFAULT NULL COMMENT 'Spring AI 自动推导的根因摘要',
  `trigger_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `clear_time` DATETIME DEFAULT NULL,
  PRIMARY KEY (`alarm_id`),
  KEY `idx_status_level` (`status`, `alarm_level`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='动环告警事件表';

-- 5. AIOps 智能运维工单与状态快照表
CREATE TABLE `idc_work_ticket` (
  `ticket_id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(20) NOT NULL DEFAULT '000000',
  `alarm_id` BIGINT DEFAULT NULL COMMENT '关联的告警事件ID (可为空)',
  `title` VARCHAR(128) NOT NULL COMMENT '工单标题',
  `ticket_type` VARCHAR(32) NOT NULL COMMENT '类型: ALARM_REPAIR(故障排障), ROUTINE_CHECK(常规巡检), ASSET_MOVE(资产移机)',
  `status` TINYINT NOT NULL DEFAULT 0 COMMENT '状态: 0-待分配, 1-排障中, 2-挂起待审批, 3-已批准待执行, 4-已拒绝, 5-待消警复核, 6-已办结',
  `operator_id` BIGINT DEFAULT NULL COMMENT '当前指派运维工程师ID',
  `approver_id` BIGINT DEFAULT NULL COMMENT '人机协同审批主管ID',
  `checkpoint_id` VARCHAR(64) DEFAULT NULL COMMENT 'Spring AI Alibaba Graph 运行时状态快照ID',
  `sop_guide` TEXT DEFAULT NULL COMMENT 'RAG 检索召回的排障 SOP 建议',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
  PRIMARY KEY (`ticket_id`),
  KEY `idx_checkpoint` (`checkpoint_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='运维巡检与 AIOps 智能工单表';

-- 6. 能耗阶梯与机柜租赁账单表
CREATE TABLE `idc_billing_statement` (
  `bill_id` BIGINT NOT NULL AUTO_INCREMENT,
  `tenant_id` VARCHAR(20) NOT NULL,
  `billing_month` VARCHAR(7) NOT NULL COMMENT '结算月份: 如 2026-08',
  `rack_rental_fee` DECIMAL(10,2) NOT NULL COMMENT '机位租赁固定费用(元)',
  `power_kwh` DECIMAL(10,2) NOT NULL COMMENT '当月实际耗电度数(kWh)',
  `power_fee` DECIMAL(10,2) NOT NULL COMMENT '设备电费总额(元)',
  `pue_factor` DECIMAL(4,2) NOT NULL DEFAULT 1.25 COMMENT '当月机房核算 PUE 能效因子',
  `pue_share_fee` DECIMAL(10,2) NOT NULL COMMENT '制冷与动环公摊能耗费(元)',
  `total_amount` DECIMAL(10,2) NOT NULL COMMENT '应付总金额',
  `payment_status` TINYINT NOT NULL DEFAULT 0 COMMENT '支付状态: 0-未支付, 1-已扣款结清, 2-逾期欠费',
  `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  PRIMARY KEY (`bill_id`),
  UNIQUE KEY `uk_tenant_month` (`tenant_id`, `billing_month`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='租户机房能耗与租赁计费结算表';
五、 核心接口契约设计 (API Contracts)
5.1 IoT 设备遥测上报 (MQTT Topic 报文格式)
Topic 规范：/sys/smartidc/{tenantId}/rack/{rackCode}/telemetry

Payload 报文规范 (JSON)：

JSON
{
  "timestamp": 1789468812000,
  "rackCode": "A-03",
  "sensors": [
    {"type": "TEMP", "val": 28.4, "unit": "C"},
    {"type": "HUMIDITY", "val": 45.2, "unit": "%RH"},
    {"type": "UPS_VOLTAGE", "val": 220.5, "unit": "V"},
    {"type": "CURRENT", "val": 16.2, "unit": "A"}
  ]
}
5.2 AIOps 智能诊断与交互会话接口
Endpoint：POST /api/v1/aiops/chat/diagnose

协议类型：text/event-stream (SSE 流式输出)

Request Body：

JSON
{
  "alarmId": 9021,
  "userQuery": "机柜 A-03 发生过温告警，请帮我分析根因并给出紧急处理建议。"
}
SSE 响应流：

HTTP
event: thought
data: {"step": "RCA_TopologyTracing", "msg": "正在关联机房 2D 拓扑，排查与机柜 A-03 处于同一制冷风道的相邻设备..."}

event: thought
data: {"step": "SOP_RAG_Retrieval", "msg": "正在检索《数据中心高密机柜热失控与冷机跳闸处置预案》..."}

event: hitl_interrupt
data: {
  "status": "SUSPENDED",
  "ticketId": 30041,
  "checkpointId": "chk_idc_7a1b9f",
  "recommendedAction": "建议调用备用冷机回路并临时限制机柜 A-03 非核心负载",
  "approverRole": "ROLE_IDC_SUPERVISOR",
  "summary": "该操作涉及冷机启停控制与配电参数下发，流程已自动挂起，已推送审批卡片至值班主管。"
}
5.3 主管审批与断点恢复接口
Endpoint：POST /api/v1/aiops/ticket/resume-action

Request Body：

JSON
{
  "ticketId": 30041,
  "checkpointId": "chk_idc_7a1b9f",
  "decision": "APPROVED",
  "comment": "现场确认温度持续上升，同意切换备用制冷回路。"
}
Response：

JSON
{
  "code": 200,
  "message": "已恢复流程执行，成功通过 Nacos MCP 向控制网关下发备用回路开启指令",
  "data": {
    "ticketId": 30041,
    "status": "APPROVED_EXECUTING",
    "executedTool": "mcp_cooling_switch_controller"
  }
}
六、 技术架构与选型依据
6.1 核心技术栈选型矩阵
技术选型	版本/组件	选型技术依据与架构收益
底层核心	Spring Boot 3.3.x + JDK 21	启用 Virtual Threads（虚拟线程），在处理数十路并发 SSE 长连接与 WebSocket 告警时，避免因阻塞消耗操作系统级线程。
Agent 引擎	Spring AI Alibaba 1.0+	原生适配通义千问大模型生态，利用其 Agent Framework 与 Graph API 实现高可靠的多节点循环状态机与故障断点续跑。
IoT 接入中间件	EMQX 5.x / 阿里云 IoT	工业级 MQTT Broker，支持单机数十万传感器连接与 QOS 1 级别遥测数据可靠投递，通过 AMQP/Webhook 转发后端。
实时通讯	Spring WebSocket + STOMP	实现监控指挥中心大屏、移动随行端的毫秒级声光告警弹窗广播与双向心跳自愈。
权限与中台	RuoYi-Vue-Plus	深度魔改若依成熟的 MyBatis-Plus、Spring Security 与数据权限切面，零成本继承企业级 RBAC 与租户隔离体系。
高并发缓冲	Redis 7.x + Redisson	充当遥测最新状态缓存、防告警重复弹窗位图、分布式互斥锁（防止主管并发双击审批）。
知识向量检索	PostgreSQL (pgvector)	存储 IDC 设备的运维手册、故障维保历史与应急处置 SOP 切片，提供低延迟混合检索。
七、 企业级非功能性工程设计 (六大工程支柱)
7.1 业务与领域建模 (DDD 战术落地)
防腐层 (ACL) 隔离 IoT 报文：不同厂家的温湿度或空调协议各异（Modbus/RTU、SNMP、MQTT JSON）。系统在接入层设置 TelemetryAdapter 防腐层，统一转化为领域标准值对象 TelemetryMetric，防止外部异构协议污染核心业务实体。

7.2 系统架构与数据治理
高吞吐物联削峰与冷热分离：

热数据：最近 5 分钟内每个机柜的最新温湿度直接常驻 Redis Hash，供拓扑大屏高频渲染，毫秒级响应。

温数据：AMQP 消费端采用线程池（ThreadPoolTaskExecutor）进行批处理（每攒满 100 条或满 2 秒执行一次批量 INSERT），减轻 MySQL 写入压力。

冷数据：遥测快照表按日分区，超过 90 天的历史数据由定时任务归档压缩转储至低频 OSS 存储中。

数据安全脱敏：对外输出接口中，对服务器 IP、租户公司名称、敏感资产编号进行行级脱敏与掩码保护。

7.3 安全、合规与审计
行级数据权限切面：运维工程师仅能查询其被分配机房的数据；机房主管可以查看全区域。通过 AOP 切入 MyBatis-Plus 的 DataPermissionHandler，自动在 SQL 注入 WHERE room_id IN (...)。

高危操作命令 AST 审计：Agent 在执行任何设备调控、脚本下发工具前，必须通过 JSqlParser 及自定义正则表达式进行白名单防御，严禁执行越权操作。

操作日志不可篡改：所有包含“状态变更”、“人工审批”的动作，自动记入 idc_work_ticket_log，记录操作人 IP、Token 散列值与时间戳。

7.4 高可用与容灾恢复
Agent 状态快照机制 (State Checkpointing)：

人机协同审批中断期间，系统不占用任何内存中的长线程。Graph 会话状态（对话记录、提取的上下游参数）被序列化存入数据库。即使此时部署新版本重启微服务，主管在 2 小时后点击审批，系统依然能从快照反序列化恢复上下文，实现真正的长事务容灾。

防重防并发 (Idempotency)：

关键的审批恢复接口加入 Redis 分布式防重 Token，利用 Redisson 加锁保护 ticketId，彻底杜绝网络卡顿引发的双击重复执行问题。

7.5 全链路可观测性与运维 (Observability)
告警风暴抑制算法 (Deduplication & De-bounce)：

引入滑动时间窗口：若同一机房在 30 秒内因同一原因连续触发 5 次超限事件，自动聚合归并为一条主告警并提升危害等级，防止客户端被瞬间打满。

全链路分布式 Tracing：

基于 Micrometer / OpenTelemetry，将“传感器 MQTT 报文入库 → 阈值引擎判定告警 → Spring AI 根因推导 → WebSocket 广播”全流程赋予全局唯一 TraceId，便于定位慢调用。

7.6 工程效能与可交付性
Git Flow 与模块化解耦：采用典型的分层多模块架构（smartidc-common、smartidc-iot、smartidc-aiops、smartidc-biz、smartidc-admin），便于前后端协同与多团队并行开发。

容器化与自动化发布：编写多阶段构建的 Dockerfile（前端 Nginx 打包 + 后端 OpenJDK 运行时分离），配套 docker-compose.yml 快速一键拉起全套基础设施环境（MySQL + Redis + EMQX + PGVector），大幅缩短交付与复现周期。

八、 总结：为什么这份设计在技术面试中“无懈可击”？
彻底打破“培训班刻板印象”：没有任何关于“老人/护理/手环”的痕迹，取而代之的是当前极具市场热度的数据中心动环监控、机架能耗管理与工业物联架构。

完整保留原课程的硬核工程技术：原课程中的 MQTT 高并发接入、线程池批量消费、WebSocket 实时告警、Redis 缓存击穿防护、MySQL 索引底层优化、若依 RBAC 权限与 Uni-app 移动端等全部硬核知识点 100% 得到了更高级的承载。

AI 赋能具备极高商业价值：告别“调 API 生成文案”的伪 AI 模式，引入 Spring AI Alibaba Graph 的人机在环（Human-in-the-Loop）审批、断点持久化与 AIOps 根因分析，完美击中企业技术专家和架构师的考评痛点。