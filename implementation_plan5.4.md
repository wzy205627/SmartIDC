# 📱 Phase 5.4 实施方案：PUE 能效计算与月度租赁电费结算引擎 (工业生产级)

> **阶段定位**：研发 IDC 商业变现与精细化能源结算中枢。彻底攻克 **A+B 双路（2N PDU）冗余供电建模、电表物理翻转（Roll-over）补偿、抄表缺失插值修补、公共自用 IT 分母校正、热力学 PUE 边界截断（Clamping）、非自然月按天日折算（Proration）、高精度 BigDecimal 财务对账、Redisson 分布式调度防并发踩踏与冲正审计链** 4 大类 9 个致命暗坑。  
> **核心基准**：严格契合 [SDS.md](file:///d:/SmartIDC/SDS.md)、[all_plan.md](file:///d:/SmartIDC/all_plan.md) 与 [01_smartidc_schema.sql](file:///d:/SmartIDC/sql/01_smartidc_schema.sql)。  
> **执行职责**：聚焦于能耗电表差值、PUE 计算、租金日折算、阶梯计费与月度批处理入库；严格遵循纯 Java 规范（无 Lombok），坚决不使用 `double/float` 算钱。

---

## 一、 工业级架构设计与时序流转全景

```mermaid
graph TD
    T[每月1日 02:00 Spring Task 分布式定时任务触发] --> L{获取 Redisson 分布式结算锁: lockAtMostFor 30m}
    L -- 抢锁失败 --> END_SKIP[其他 Pod 已在执行，本实例安全跳过]
    L -- 抢锁成功 --> D1[1. 冻结抄表快照: 提取上月 23:59:59 对应基准读数]
    
    D1 --> D2[2. 物理拓扑电表核算: A+B 双路 2N PDU 加和 / 翻转补偿 / 缺失插补]
    D2 --> D3[3. PUE 能效安全核算: 纳入公共/自用 IT 负载, 执行 clamp 1.00 ~ 1.45]
    D3 --> D4[4. 租金日天数折算 (Prorated) + 阶梯分段电费 + PUE 制冷公摊 (全链路 BigDecimal)]
    D4 --> D5[5. 生成账单快照持久化至 idc_billing_statement (状态: 0-未支付)]
    D5 --> D6[6. 记录结算审计日志与告警 / 提供管理员幂等手动重算端点]
```

---

## 二、 4 大类 9 个关键暗坑针对性工业级改造

### 1. 物理计量与拓扑层缺陷治理

#### 1.1 A+B 双路冗余供电（2N PDU）物理建模
- **现实工况**：IDC 机房标准高可用机柜标配 A 路与 B 路两条独立母线 PDU，分别由不同的 UPS/变压器回路供电。
- **数学建模**：单机柜月度实际耗电量必须是双回路物理电表的差值累加：
  $$E_{\text{rack}} = \left(E_{\text{cur}}^{\text{PDU\_A}} - E_{\text{last}}^{\text{PDU\_A}}\right) + \left(E_{\text{cur}}^{\text{PDU\_B}} - E_{\text{last}}^{\text{PDU\_B}}\right)$$
- 若机柜仅配置单路 PDU，则 B 路电量自动计为 0，具备向下兼容性。

#### 1.2 电表物理翻转（Roll-over）与换表清零补偿
- **现实工况**：硬件智能电表具有最大量程上限（如 $99999.9\text{ kWh}$）。当数值跑满翻转或电表烧损更换时，$E_{\text{cur}} < E_{\text{last}}$，直接相减会导致巨大的负数。
- **补偿与拦截算法**：
  ```java
  public static final BigDecimal METER_MAX_CAPACITY = BigDecimal.valueOf(100000.0); // 默认 10 万度量程

  BigDecimal delta = curKwh.subtract(lastKwh);
  if (delta.compareTo(BigDecimal.ZERO) < 0) {
      // 触发翻转溢出补偿
      delta = curKwh.add(METER_MAX_CAPACITY).subtract(lastKwh);
      log.warn("[电表翻转补偿] 检测到读数回退，已执行物理翻转补偿: cur={}, last={}, delta={}", curKwh, lastKwh, delta);
  }
  ```

#### 1.3 抄表时点数据漂移与缺失兜底
- **现实工况**：月度结算时若某台智能电表 Modbus 通信故障未能及时上报零点读数。
- **兜底算法**：读取该机柜上报历史中最接近结算时标（如 23:50~23:59）的有效快照；若当月整月无上报，基于机柜额定负荷 $P_{\text{rated}}$ 与平均开机率执行安全保底插补（或打上 `DATA_MISSING_ESTIMATED` 标记人工复核），杜绝直接置 0 漏收。

---

### 2. PUE 能效计算与热力学边界失控治理

#### 2.1 纳入公共自用 IT 负载（$E_{\text{IT\_Self}}$）与空置待机（$E_{\text{IT\_Idle}}$）
- **现实工况**：机房常年运行着 IDC 自身的网络核心交换机、动环监控主机、门禁监控等自用 IT 基础设备，以及部分空置机柜的待机负荷。若分母仅计算租户 IT 电量，分母严重偏小，导致算出的 PUE 人为虚高，引发租户拒绝摊费的严重商务纠纷。
- **修正公式**：
  $$PUE_{\text{actual}} = \frac{E_{\text{Total}}}{\sum_{\text{All Tenants}} E_{\text{IT}} + E_{\text{IT\_Self}} + E_{\text{IT\_Idle}}}$$
  - $E_{\text{Total}}$ 为机房高压进线总电表当月总电量；
  - $E_{\text{IT\_Self}}$ 与 $E_{\text{IT\_Idle}}$ 由系统自动归集属于平台运营租户（`000000`）及未分配机架的能耗。

#### 2.2 违反热力学第二定律“PUE < 1.0”除零与物理极值截断（Clamping）
- **边界防线**：
  1. **除零保护**：当总 IT 耗电量 $\sum E_{\text{IT\_All}} == 0$ 时，直接锁定基准默认值 $1.25$（或跳过制冷公摊）；
  2. **物理极值截断（Clamping）**：根据热力学第二定律，机房输入总能耗不可能小于设备做功，$PUE \ge 1.00$；同时为保障客户权益，合同设定 **PUE 封顶上限**（默认 $1.45$，超过部分由机房自负）：
     $$PUE_{\text{bill}} = \text{clamp}(PUE_{\text{actual}}, 1.00, PUE_{\text{contract\_cap}})$$

---

### 3. 财务计费模型与金额核算规约

#### 3.1 租期未满整月的非自然月日折算（Proration）
- **现实工况**：租户可能在月中任意一天起租或退租，按整月 3500 元硬收必将引发法律纠纷。
- **按日折算公式**：
  $$Fee_{\text{rent}}(r) = 3500.00 \times \frac{\text{ActualActiveDays}}{\text{DaysInMonth}}$$
  - $\text{DaysInMonth}$ 为该结算月份的实际天数（例如 28、29、30 或 31 天）；
  - $\text{ActualActiveDays}$ 为机架在该月份内处于在租状态的实际天数。

#### 3.2 阶梯电费统计粒度明确（单机柜独立定额 `PER_RACK`）
- **业务标准**：
  - 首档基准（$\le 3,000\text{ kWh/机柜}$）：$0.85\text{ 元/kWh}$；
  - 超额二档（$> 3,000\text{ kWh/机柜}$）：$1.10\text{ 元/kWh}$；
  - 采用单柜独立核算（`PER_RACK`），鼓励租户在名下各个机架间均衡分配算力与散热负载。

#### 3.3 浮点精度黑洞防御：全链路高精度 `BigDecimal`
- **代码硬性红线**：
  - 严禁在任何计费计算中使用 `double` 或 `float`；
  - 能耗电量（kWh）：`BigDecimal`，中间计算保留 4 位小数，四舍五入；
  - 结算金额（元）：`BigDecimal`，保留 2 位小数，采用 `RoundingMode.HALF_UP`。

---

### 4. 分布式调度与生产定时任务暗坑治理

#### 4.1 集群多实例并发踩踏与 Redisson 分布式锁
- **现实工况**：多节点集群部署时，K8s 多个 Pod 上的 `@Scheduled` 会在每月 1 日 02:00 同时被触发，导致数据库并发锁死与账单重复生成。
- **排他调度保护**：
  - 调度任务使用 Redisson 获取分布式锁：`smartidc:billing:lock:monthly:{month}`；
  - 设置 `tryLock(0, 30, TimeUnit.MINUTES)`，抢锁失败立即退出并打印日志，确保全集群同一时刻仅有一个 Pod 执行结算。

#### 4.2 账单防篡改与幂等冲正审计（Audit Trail）
- **唯一索引保障**：数据库表 `idc_billing_statement` 具备唯一键 `uk_tenant_month(tenant_id, billing_month)`；
- **幂等补结模式**：
  - 提供管理员手动结算接口：`POST /api/v1/billing/settle?month=2026-08&tenantId=&force=false`；
  - 若已存在未支付账单且指定 `force=true`，记录历史调账审计日志后再执行冲正更新；若已支付（`payment_status = 1`）则强行阻断并拒绝修改。

---

## 三、 详细契约与代码规范

### 1. 数据库映射实体 (`IdcBillingStatement.java`)
- 纯 Java 标准 POJO（手写 Getter/Setter/构造器，无 Lombok）：
  ```java
  package com.smartidc.biz.domain.entity;

  import com.baomidou.mybatisplus.annotation.IdType;
  import com.baomidou.mybatisplus.annotation.TableId;
  import com.baomidou.mybatisplus.annotation.TableName;
  import java.io.Serializable;
  import java.math.BigDecimal;
  import java.time.LocalDateTime;

  @TableName("idc_billing_statement")
  public class IdcBillingStatement implements Serializable {
      @TableId(type = IdType.AUTO)
      private Long billId;
      private String tenantId;
      private String billingMonth;    // 格式: 2026-08
      private BigDecimal rackRentalFee;
      private BigDecimal powerKwh;
      private BigDecimal powerFee;
      private BigDecimal pueFactor;
      private BigDecimal pueShareFee;
      private BigDecimal totalAmount;
      private Integer paymentStatus;  // 0-未支付, 1-已支付, 2-逾期
      private LocalDateTime createTime;

      // 纯手写完整构造器、Getter 与 Setter
  }
  ```

### 2. 计费结算核心服务接口与实现 (`smartidc-biz`)
- **`IdcBillingService.java`**：
  ```java
  package com.smartidc.biz.service;

  import com.smartidc.biz.domain.entity.IdcBillingStatement;
  import java.util.List;

  public interface IdcBillingService {
      /**
       * 执行指定月份的全机房月度计费自动结算 (带 Redisson 分布式锁)
       *
       * @param billingMonth 结算月份 (如 2026-08)
       * @param forceRecompute 是否强制重算未支付账单
       * @return 生成/更新的账单清单
       */
      List<IdcBillingStatement> executeMonthlySettlement(String billingMonth, boolean forceRecompute);

      /**
       * 查询指定租户的月度账单历史
       */
      List<IdcBillingStatement> listStatementsByTenant(String tenantId);

      /**
       * 管理员分页查询所有租户账单
       */
      List<IdcBillingStatement> listStatementsByMonth(String billingMonth);
  }
  ```

- **`IdcBillingScheduledTask.java`**：
  ```java
  package com.smartidc.biz.engine.billing;

  import com.smartidc.biz.service.IdcBillingService;
  import org.redisson.api.RLock;
  import org.redisson.api.RedissonClient;
  import org.slf4j.Logger;
  import org.slf4j.LoggerFactory;
  import org.springframework.scheduling.annotation.Scheduled;
  import org.springframework.stereotype.Component;

  import java.time.LocalDate;
  import java.time.format.DateTimeFormatter;
  import java.util.concurrent.TimeUnit;

  /**
   * 月度租赁电费自动结算批处理定时任务
   */
  @Component
  public class IdcBillingScheduledTask {

      private static final Logger log = LoggerFactory.getLogger(IdcBillingScheduledTask.class);

      private final IdcBillingService idcBillingService;
      private final RedissonClient redissonClient;

      public IdcBillingScheduledTask(IdcBillingService idcBillingService, RedissonClient redissonClient) {
          this.idcBillingService = idcBillingService;
          this.redissonClient = redissonClient;
      }

      /**
       * 每月 1 日凌晨 02:00 自动触发上月账单结算
       */
      @Scheduled(cron = "0 0 2 1 * ?")
      public void runMonthlyBillingSettlement() {
          LocalDate lastMonthDate = LocalDate.now().minusMonths(1);
          String billingMonth = lastMonthDate.format(DateTimeFormatter.ofPattern("yyyy-MM"));

          String lockKey = "smartidc:billing:lock:monthly:" + billingMonth;
          RLock lock = redissonClient.getLock(lockKey);

          try {
              // 尝试加锁 0 秒，持有锁最长 30 分钟，抢锁失败立即退出
              if (lock.tryLock(0, 30, TimeUnit.MINUTES)) {
                  try {
                      log.info("[月度计费任务] 成功获取分布式调度锁，开始结算 [{}] 账单", billingMonth);
                      idcBillingService.executeMonthlySettlement(billingMonth, false);
                      log.info("[月度计费任务] [{}] 账单结算圆满完成", billingMonth);
                  } finally {
                      if (lock.isHeldByCurrentThread()) {
                          lock.unlock();
                      }
                  }
              } else {
                  log.info("[月度计费任务] 其他集群节点正在执行 [{}] 账单结算，本节点跳过", billingMonth);
              }
          } catch (InterruptedException e) {
              Thread.currentThread().interrupt();
              log.error("[月度计费任务] 调度加锁被中断", e);
          }
      }
  }
  ```

### 3. REST 控制器端点 (`IdcBillingController.java`)
- 统一定义在 `com.smartidc.biz.controller.IdcBillingController`：
  - `POST /api/v1/billing/settle`：管理员手动触发月度账单结算（支持 `month`, `force`）；
  - `GET /api/v1/billing/statements`：多维分页/按月份查询账单明细；
  - `GET /api/v1/billing/my-statements`：租户查看自身账单历史（带多租户上下文隔离）。

---

## 四、 T5.4 验收清单与质检基准

在向用户汇报前，必须完成以下 4 大项验收验证：

| 序号 | 验证维度 | 验证操作与断言标准 | 验收结论 |
| :--- | :--- | :--- | :--- |
| **V1** | **A+B 双路电表与翻转补偿测试** | 模拟 A 路电量 1200kWh、B 路电表物理翻转（99990 ➔ 100，补偿后增量 110kWh），断言机柜总耗电量为准确的 1310kWh，0 负数异常。 | 待验证 |
| **V2** | **PUE 热力学边界与自用 IT 分母校正** | 模拟机房总能耗 24000kWh，租户 IT 16000kWh，公共自用 IT 3200kWh；断言核算 PUE 为 1.25；当总能耗异常导致 PUE < 1.0 时自动 clamp 截断至 1.00，当超过上限时截断至 1.45。 | 待验证 |
| **V3** | **租金日折算与阶梯电费高精度核算** | 模拟月中 16 号起租（9 月 30 天，在租 15 天），断言固定租金为准确的 1750.00 元；用电量 3500kWh，首档 3000kWh×0.85 + 超额 500kWh×1.10 = 3100.00 元，全链路精确到分。 | 待验证 |
| **V4** | **Redisson 分布式锁防多节点并发测试** | 模拟并发双线程同时调用结算，断言只有一个线程成功抢占执行，另一个线程安全跳过；生成账单精准入库 `idc_billing_statement`，0 重复记录。 | 待验证 |
