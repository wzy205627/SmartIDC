# 📱 Phase 5: 移动随行端 (Uni-app) 与 PUE 计费结算闭环实施计划 (全面修正版)

> **阶段定位**：研发驻场工程师移动随行端（微信小程序 / 移动H5），实现现场扫码、机柜微画像实时呈现、现场拍照加注防伪水印与一键消警闭环；并打通 IDC 智能电表差值、PUE 能效公摊与月度多租户阶梯电费自动结算引擎。  
> **核心基准**：严格契合 [SDS.md](file:///d:/SmartIDC/SDS.md)、[all_plan.md](file:///d:/SmartIDC/all_plan.md) 与 [01_smartidc_schema.sql](file:///d:/SmartIDC/sql/01_smartidc_schema.sql)。  
> **最新修正**：深度采纳工业级整改意见，已彻底攻克 T5.1（双 Token 鉴权、JWT 唯一事实源）、T5.2（扫码容错、BOLA 越权防护、Over-fetching 拆分、Redis 离线哨兵、CSS3 双环仪表）、T5.3（机架物理拓扑水印、先压缩后绘制防爆内存、S3 预签名直传、动环 5 分钟防抖归档）、T5.4（A+B 双路 2N 冗余电表、电表翻转补偿、自用 IT 分母校正、PUE 热力学截断、租金日折算、高精度 BigDecimal 财务对账、Redisson 分布式锁防多节点踩踏）与 T5.5（多阶段 `jlink` 裁剪轻量 JRE `< 180MB`、虚拟线程 Pinning 堆栈追踪、Nginx SSE 零缓冲代理、60 秒时间机器全链路端到端闭环验收）全部 5 大任务的关键缺陷。

---

## 一、 Phase 5 全景业务与数据流转拓扑

```mermaid
flowchart TD
    subgraph 移动随行端 (Uni-app / 微信小程序)
        A1[工程师登录 / 首绑认证 / 租户切换] --> A2[调起微信扫码 / 手电筒补光 / 手工兜底]
        A2 -->|鲁棒解析 rackCodeParser| A3[机柜微画像详情页]
        A3 --> A4[CSS3 双环动环仪表 5s 静默轮询]
        A3 --> A5[42U 插槽设备台账与活动告警]
        A3 --> A6[Tab 按需延迟加载历史维保工单]
        A6 --> A7[一键接单 / 挂起待备件 / 现场排障]
        A7 --> A8[拍照双步防爆流水线 1920px压缩 ➔ Canvas2D机架物理拓扑水印]
        A8 --> A9[申请S3预签名PUT凭证 ➔ 零AK/SK直传MinIO]
        A9 --> A10[提交现场消警 ➔ 服务端NTP授时打标与SHA-256存证绑定]
    end

    subgraph 后端协同与工单中枢 (smartidc-biz / smartidc-admin)
        B1[Auth: 双Token鉴权 2h Access + 7d Refresh 存Redis]
        B2[Security: JWT唯一事实源强制注入 TenantContext]
        B3[BOLA 拦截: 核查工程师对机柜的租户查看权限]
        B4[Mobile API: GET /rack/{code}/profile 微画像直查Redis]
        B5[Redis 离线哨兵: status=OFFLINE 杜绝NPE 500]
        B6[Mobile API: POST /oss/presigned-url S3预签名直传]
        B7[Ticket API: accept / suspend / resolve 状态机流转]
        B8[Watchdog: 动环5分钟滞后防抖自动归档 ➔ 7-COMPLETED 闭环消警]
        B9[Watchdog: 30分钟超时未回稳逃生通道 ➔ 打回2-排障中]
    end

    subgraph PUE能效与计费结算引擎 (smartidc-biz/billing)
        C1[每月1日 02:00 Spring Task 分布式调度] --> C2{Redisson 抢占结算互斥锁}
        C2 -- 抢锁失败 --> C3[其他 Pod 正在执行，本节点安全退出]
        C2 -- 抢锁成功 --> C4[物理表核算: A+B双路加和 + 翻转补偿 + 缺失插补]
        C4 --> C5[PUE热力学计算: 纳入自用IT分母, 执行 clamp 1.00 ~ 1.45]
        C5 --> C6[财务核算: 租金按日折算 + 阶梯分段电费 + PUE公摊 全链路BigDecimal]
        C6 --> C7[持久化生成账单快照: idc_billing_statement uk_tenant_month]
        C7 --> C8[提供管理员幂等手动重算端点 / 冲正审计日志]
    end

    subgraph 生产镜像与全链路闭环门禁 (T5.5 交付验证)
        D1[多阶段构建: jlink 裁剪定制 JRE 生产镜像 < 180MB]
        D2[生产 JVM: 虚拟线程 Pinning 追踪 + ZGC 分代自适应]
        D3[Nginx: proxy_buffering off 零缓冲保活 SSE 打字机流式推流]
        D4[Phase5EndToEndRehearsalTest: 60秒时间机器跑通全生命周期闭环]
    end

    A1 -.-> B1
    A1 -.-> B2
    A3 -.-> B3
    A3 -.-> B4
    B4 -.-> B5
    A8 -.-> B6
    A10 -.-> B7
    B7 --> B8
    B8 -.-> B9
    C7 -.-> A1
    D1 -.-> D2 -.-> D3 -.-> D4
```

---

## 二、 详细任务分解与工业级技术方案

### 📋 任务 1 (T5.1): Uni-app 移动随行端工程架构、双Token与多租户认证接入
> 详细落地方案见：[implementation_plan5.1.md](file:///d:/SmartIDC/implementation_plan5.1.md)（已 100% 验收通过）

---

### 🔍 任务 2 (T5.2): 移动端扫码巡检与机柜微画像引擎
> 详细落地方案见：[implementation_plan5.2.md](file:///d:/SmartIDC/implementation_plan5.2.md)（已 100% 验收通过）

---

### 📸 任务 3 (T5.3): 现场排障工单流转、双步防爆内存水印与 MinIO 预签名存证闭环
> 详细落地方案见：[implementation_plan5.3.md](file:///d:/SmartIDC/implementation_plan5.3.md)（已 100% 验收通过）

---

### ⚡ 任务 4 (T5.4): PUE 能效计算与月度租赁电费结算引擎 (工业生产级)
> 详细落地方案见：[implementation_plan5.4.md](file:///d:/SmartIDC/implementation_plan5.4.md)

---

### 🐳 任务 5 (T5.5): 生产级镜像打包、SSE零缓冲代理与端到端时间机器演练验收
> 详细落地方案见：[implementation_plan5.5.md](file:///d:/SmartIDC/implementation_plan5.5.md)

#### 1. 核心技术整改对齐
- **两阶段 `jlink` 运行时裁剪，实现后端镜像 `< 180MB`**：
  - 构建器基于 `eclipse-temurin:21-jdk-alpine`，运行 `jlink` 精准提取运行 Spring Boot 所需的最小模块集（`java.base,java.sql,java.net.http,java.desktop,java.management,java.naming,java.security.jgss,java.instrument,jdk.crypto.cryptoki,jdk.unsupported`），剔除 debug 与 man-pages，输出仅约 45MB 的定制 JRE；
  - 生产镜像基于 `alpine:3.19`（7MB），最终全镜像体积稳定在 150MB ~ 180MB 之间。
- **虚拟线程 Project Loom 与生产 JVM 参数加固**：
  - 启用 `-Djdk.tracePinnedThreads=full`，防范代码与三方库中 `synchronized` 导致的载体线程锁死卡顿；
  - 配置 `-XX:+UseZGC -XX:+ZGenerational` 与 `-XX:MaxRAMPercentage=75.0`，实现超低延迟 GC 与 K8s 内存自适应防 OOMKilled。
- **Nginx 代理针对 SSE 长连接的“零缓冲截流”治理**：
  - 针对 `/api/v1/aiops/diagnose/stream` 与移动端 API，配置 `proxy_buffering off; proxy_cache off; chunked_transfer_encoding on;`；
  - 彻底杜绝 Nginx 默认缓冲暂存数 KB 后一次性冲刷导致的大屏打字机推流瘫痪；配置 `try_files $uri $uri/ /index.html;` 解决 SPA 页面刷新 404。
- **全链路闭环“时间机器（Time-Travel）”自动化演练测试类**：
  - 编写 `Phase5EndToEndRehearsalTest.java` 串行演练测试类，内置时间快进调度机制；
  - **在 60 秒内严格跑通 6 大全生命周期闭环断言**：
    `遥测越限 ➔ 告警防抖 ➔ AI分析挂起 ➔ 主管在线审批 ➔ 移动扫码消警 ➔ 月底时间机器出账`。

---

## 三、 阶段 5 终极交付物与技术验收基准表 (Final Acceptance Matrix)

| 任务 | 交付物模块（Code & Config Artifacts） | 关键技术验证指标（Hard Verification Gates） |
| :--- | :--- | :--- |
| **T5.1** | • `smartidc-app/` (Vue3+TS+Pinia)<br>• `MobileAuthController.java`<br>• `request.ts` (401单飞刷新队列) | 1. 微信三态首绑逻辑完整，支持 `mock=true` 免密本地调试；<br>2. 短期 Access Token (2h) + 长期 Refresh Token (7d, Redis 持久化与秒级吊销)；<br>3. 租户以 JWT Claim 为唯一事实源，彻底屏蔽非法 `X-Tenant-Id`；<br>4. 机房弱网网络监听与基础字典持久化缓存。 |
| **T5.2** | • `pages/rack/profile.vue`<br>• `MobileRackController.java`<br>• `rackCodeParser.ts` (正则容错解析器) | 1. 扫码支持带参 URL、纯编码与手动输入三重容错；<br>2. BOLA 租户水平越权严格阻断（抛出 `403 FORBIDDEN`）；<br>3. Redis 动环数据缺失时返回 `OFFLINE` 哨兵结构，零 500 NPE 异常；<br>4. CSS3/SVG 双环仪表盘无 Canvas 层级穿透与模糊问题。 |
| **T5.3** | • `watermark.ts`<br>• `MinioStorageService.java`<br>• `MobileTicketController.java`<br>• `TicketDebounceWatchdog.java` | 1. 水印绑定“机房-楼层-列柜”权威物理拓扑，拒绝室内漂移 GPS；<br>2. 照片经等比压缩（最长边 $\le 1920\text{px}$）后再走 Canvas，显存降 77%，零 OOM 闪退；<br>3. S3 预签名 URL 直传 MinIO，前端零 AK/SK 泄露风险；<br>4. 动环需**连续 5 分钟**在阈值下防抖归档，超温 30 分钟触发逃生通道打回排障中。 |
| **T5.4** | • `IdcBillingStatement.java`<br>• `IdcBillingService.java`<br>• `IdcBillingScheduledTask.java` | 1. **A+B 双路 2N PDU 电量加和计算**，支持寄存器翻转补偿（杜绝负数）；<br>2. PUE 分母计入自用与空置 IT，启用 `clamp(PUE, 1.0, 1.45)` 防爆；<br>3. 固定租金按日折算（Proration），全程使用高精度 `BigDecimal` 算费；<br>4. **Redisson 分布式排他锁**，杜绝集群多 Pod 并发重复扣费出账。 |
| **T5.5** | • `Dockerfile.frontend` & `nginx.conf`<br>• `Dockerfile.backend` (多阶段 jlink)<br>• `Phase5EndToEndRehearsalTest.java` | 1. Nginx 开启 Gzip，配置 SSE 长连接 `proxy_buffering off` 零缓冲；<br>2. 后端采用 `jlink` 裁剪轻量 JRE，生产镜像体积 $< 180\text{MB}$；<br>3. 虚拟线程开启 Pinning 追踪与容器内存自适应；<br>4. **端到端演练测试类一键拉起，60 秒内完成全流程 100% 自动化闭环验收**。 |
