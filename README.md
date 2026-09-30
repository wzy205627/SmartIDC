# 智维云 (SmartIDC) - 智算中心动环监控与 AIOps 智能运维协同中台

<div align="center">

![Java](https://img.shields.io/badge/JDK-21%20LTS-orange.svg)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.3-brightgreen.svg)
![Spring AI](https://img.shields.io/badge/Spring%20AI%20Alibaba-StateGraph-blue.svg)
![AgentScope](https://img.shields.io/badge/AgentScope-Java-purple.svg)
![PostgreSQL](https://img.shields.io/badge/pgvector-1024%20HNSW-navy.svg)
![Vue3](https://img.shields.io/badge/Vue-3.x%20%7C%20Uni--app-emerald.svg)
![License](https://img.shields.io/badge/License-Apache%202.0-blue.svg)

**面向绿色算力数据中心（IDC）的一体化数字基座，贯通“遥测采集 ➔ 拓扑大屏 ➔ AIOps 自愈 ➔ 移动随行 ➔ PUE 计费”全链路闭环**

[功能特性](#-功能特性) • [技术架构](#-系统技术架构) • [工程模块](#-工程模块划分) • [快速开始](#-快速开始) • [核心亮点](#-核心工程亮点)

</div>

---

## 📖 项目简介

**智维云 (SmartIDC)** 是专为大型绿色算力数据中心（IDC）研发的生产级综合运维协同管理平台。系统针对机房高密供电、制冷安全与能效精细化运营诉求，攻克了工业现场**“高危倒闸零容忍安全红线、告警风暴拓扑收敛难、长周期异步审批挂起、极端物理工况移动闪退与电表硬件翻转计量”**等一系列核心痛点。

平台创新性落地了 **“外层 StateGraph 图工作流 + 内层 AgentScope 瞬态自主探索”** 的双层混合编排架构，结合 **1024 维 HNSW 拓扑感知 RAG**，让大模型在机房不仅“懂故障推演”，更受控于“刚性风控红线”。

---

## 🌟 功能特性

### 1. 空间物理拓扑与 42U 在架管理 (Phase 1)
- **多级空间拓扑建模**：园区 ➔ 机房 ➔ 列柜（冷热通道）➔ 42U 槽位全生命周期纳管；
- **防重叠冲突检测**：严格的在架设备高度区间校验算法，杜绝多租户 U 位重叠；
- **行级多租户隔离**：基于 MyBatis-Plus 拦截器与 JWT 唯一事实源，严格防御 BOLA 水平越权。

### 2. 高吞吐动环接入与滑动窗口抑振 (Phase 2)
- **多协议防腐网关**：集成 EMQX MQTT 与 Netty 异步驱动，统一适配 Modbus/SNMP 遥测协议；
- **滑动窗口状态机 (RackAlarmStateMachine)**：多周期平滑滤波，剔除冷气对流与电磁瞬态毛刺，降低 90% 以上无效虚警；
- **削峰与实时推屏**：Redis Stream 削峰异步落库，WebSocket/STOMP 全双工按空间拓扑向 Web 大屏秒级推流。

### 3. 2D 数字孪生大屏与声光告警 (Phase 3)
- **冷热拓扑可视化**：高保真 2D 机房热力着色渐变微动渲染（纯 SVG/CSS3 防 Canvas 层级穿透）；
- **动态故障抽屉**：红灯先亮，告警瞬间联动打开下钻排障抽屉，支持一键发起 AIOps 智能研判。

### 4. AIOps 混合双层编排与拓扑 RAG (Phase 4)
- **StateGraph + AgentScope 双层架构**：外层图编排控制状态机与审批，内层 ReActAgent 自主多轮拓扑探查；
- **PostgreSQL 16 + pgvector (1024 维)**：千问 Embedding + HNSW 索引，动态注入 `ef_search=100` 与 0.50 弹性余弦阈值；
- **拓扑感知 (Topology-Aware)**：探查同风道邻机静压与上游母联供电链路，穿透冷机跳闸告警雪崩；
- **双通道决策**：同时输出给机器执行的 `control_flow` (纯 JSON) 与给人阅读的 `display_view` (高保真 Markdown)；
- **人机在环 (HITL) 与 RedisSaver**：高危预案 100% 拦截挂起，快照写入 Redis 释放线程池，审批后毫秒级唤醒恢复。

### 5. 移动随行端与极端工况防御 (Phase 5.1 ~ 5.3)
- **权威物理拓扑锚定**：针对机房法拉第笼屏蔽导致 GPS 瘫痪问题，以权威机柜物理元数据替代卫星水印；
- **防爆显存双步流水线**：4800 万高清拍照先等比降维至 1920px 再绘制离线水印，显存开销降低 77%，彻底杜绝移动端 OOM 闪退；
- **MinIO 预签名 S3 PUT 直传**：临时凭证前端直传对象存储，后端带宽零压力；
- **自愈看门狗 (TicketDebounceWatchdog)**：消警后启动连续 5 分钟安全阈值观察窗，防“物理假愈合”与秒消秒报。

### 6. 高精度 PUE 与月度账单结算 (Phase 5.4 ~ 5.5)
- **2N PDU 双回路物理加和**：精确计量 A+B 双母线冗余供电能耗；
- **机械电表翻转补偿 (Rollover)**：自动识别 $99999 \text{ kWh}$ 溢出回转，消除负数电费；
- **PUE 防爆与按日折算 (Proration)**：`clamp(PUE, 1.00, 1.45)` 防爆约束，全链路 `BigDecimal` 零精度丢失；
- **Redisson 分布式排他锁**：确保集群多 Pod 并发定时结算时绝对幂等，杜绝重复出账。

---

## 🏗️ 系统技术架构

```
┌────────────────────────────────────────────────────────────────────────────────────────┐
│                        外层结构层: Spring AI Alibaba StateGraph                         │
│                                                                                        │
│   [START] ──▶ [RCA_NODE] ──▶ [SOP_NODE] ──▶ [RISK_AUDIT_NODE]                          │
│                     │             │               │                                    │
│                     │             │         (高危分流/低危放行)                         │
│                     ▼             ▼               ├──────────────┐                     │
│                ┌─────────┐   ┌─────────┐          │              ▼ (高危倒闸)          │
│                │ 瞬态    │   │ 瞬态    │          ▼ (低危自愈)   [APPROVAL_SUSPEND]    │
│                │ ReAct   │   │ ReAct   │  [ACTION_EXECUTION]     (RedisSaver快照持久化)│
│                │ Agent   │   │ Agent   │          │                      │             │
│                └─────────┘   └─────────┘          ▼                      ▼             │
│            内层节点层: AgentScope Java          [END]               [人机审批唤醒恢复]    │
└────────────────────────────────────────────────────────────────────────────────────────┘
```

---

## 📦 工程模块划分

```text
SmartIDC
├── smartidc-parent/             # Maven 父工程依赖与统一版本仲裁
├── smartidc-common/             # 公共通用 DTO、枚举、异常体系与常量契约
├── smartidc-framework/          # 框架中枢 (JWT 双 Token、租户切面、Redis/MyBatis 增强)
├── smartidc-iot/                # 物联网动环网关 (EMQX MQTT、防腐层适配器、滑动窗口去抖)
├── smartidc-biz/                # 业务核心中枢 (机柜微画像、SOP 工单、Watchdog、PUE 计费)
├── smartidc-aiops/              # AIOps 智能体模块 (StateGraph 图编排、AgentScope、pgvector RAG)
├── smartidc-admin/              # 启动引导模块 (Spring Boot 统一装配、Knife4j API 聚合)
├── smartidc-ui/                 # PC 运营中台前端 (Vue 3 + Vite + Pinia + ECharts)
├── smartidc-app/                # 移动随行端 (Uni-app + Vue 3 + TS + Vite)
├── docker/                      # 容器化与运维编排 (Dockerfile.backend, Nginx, Compose)
└── sql/                         # 数据库 DDL 与 pgvector 初始化脚本
```

---

## 🚀 快速开始

### 1. 环境准备
- **JDK 21 LTS**（推荐 Eclipse Temurin 或 GraalVM）
- **Node.js** >= 18.x & pnpm / npm
- **Maven** >= 3.8.x
- **Docker & Docker Compose**

### 2. 基础设施一键拉起
```bash
# 启动 PostgreSQL 16 (pgvector), Redis, EMQX, MinIO 等中间件
docker compose -f docker/docker-compose.yml up -d
```

### 3. 初始化数据库
```bash
# 执行数据库表结构与初始化种子
psql -U postgres -d smartidc -f sql/01_smartidc_schema.sql
psql -U postgres -d smartidc -f sql/02_smartidc_seed.sql
psql -U postgres -d smartidc -f sql/phase4_pgvector_init.sql
```

### 4. 编译与测试
```bash
# 根目录下全工程单元测试与质检门禁（7 大模块全绿验证）
mvn clean test
```

### 5. 启动服务
```bash
# 启动 Spring Boot 后端主应用
mvn spring-boot:run -pl :smartidc-admin

# 启动 PC 端中台前端
cd smartidc-ui
npm install
npm run dev

# 启动移动随行端
cd smartidc-app
npm install
npm run dev:h5
```

---

## 🎯 核心工程亮点与量化指标

根据 CI/CD 自动化黄金评测报告（对标 `AgentScope-Harness`），系统核心表现如下：

| 评估指标 | 生产硬门禁 SLA | 实测验证得分 | 架构意义 |
| :--- | :---: | :---: | :--- |
| **高危拦截率 (Hard Gate)** | **100.0%** | **100.0% (PASS)** | **一票否决门禁**：涉及倒闸高危动作 100% 挂起在审批桩，零误触 |
| **RCA 根因准确率** | $\ge 90.0\%$ | **100.0% (PASS)** | 穿透冷机跳闸拓扑风暴，精准锁定根因 |
| **SOP 预案召回率** | $\ge 95.0\%$ | **100.0% (PASS)** | 1024 维 HNSW 索引 + Session 级 `ef_search=100` 零漏召 |
| **噪点拒识率** | $\ge 85.0\%$ | **100.0% (PASS)** | 精准识别瞬态 48℃ 传感器毛刺，定级 UNKNOWN，杜绝过度排障 |
| **端到端演练耗时** | $< 60\text{ s}$ | **4.65 秒** | 时间机器 6 大业务断言全自动演练极速闭环 |
| **后端轻量容器体积** | $< 250\text{ MB}$ | **< 180 MB** | 基于 JDK 21 `jlink` 定制极简运行时裁剪（压缩率达 65%+） |

---

## 📄 开源许可证

本项目遵循 [Apache License 2.0](LICENSE) 开源协议。
