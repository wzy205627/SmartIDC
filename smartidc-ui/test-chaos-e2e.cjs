const { Client } = require('./node_modules/@stomp/stompjs');

const API_BASE = 'http://localhost:8080/api/v1';

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function request(url, options = {}) {
  const res = await fetch(API_BASE + url, {
    headers: { 'Content-Type': 'application/json', ...(options.headers || {}) },
    ...options
  });
  const data = await res.json();
  if (data.code !== 200) {
    throw new Error(`API Error [${url}]: ${data.msg || JSON.stringify(data)}`);
  }
  return data.data;
}

async function main() {
  console.log('========================================================================');
  console.log('🧪 [Task T2.5 验收测试] 动环物联网 Mock 仿真器与混沌故障演练全链路验收');
  console.log('========================================================================\n');

  // 1. 建立 STOMP WebSocket 客户端
  console.log('=== 1. 建立 STOMP WebSocket 连接与频道监听 ===');
  let resolveConnected;
  const connectedPromise = new Promise((resolve) => { resolveConnected = resolve; });

  const telemetryStream = [];
  const alarmStream = [];

  const stompClient = new Client({
    brokerURL: 'ws://localhost:8080/ws/smartidc',
    reconnectDelay: 0,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: () => {
      console.log('✅ STOMP 成功连接至 Spring Boot WebSocket 网关！');
      resolveConnected();
    },
    onStompError: (frame) => {
      console.error('❌ STOMP 错误:', frame);
    }
  });

  stompClient.activate();
  await connectedPromise;

  stompClient.subscribe('/topic/rack-telemetry/A-03', (msg) => {
    const p = JSON.parse(msg.body);
    telemetryStream.push(p);
  });

  stompClient.subscribe('/topic/alarms', (msg) => {
    const p = JSON.parse(msg.body);
    console.log('🚨 [STOMP 告警推屏]', p.action, p.alarmLevel, p.rackCode, p.metricValue, p.rcaSummary || '');
    alarmStream.push(p);
  });

  // 2. 验证后台定时心跳自动发生
  console.log('\n=== 2. 验证 Mock 模拟器定时心跳自动发生 ===');
  const status1 = await request('/mock/status');
  console.log('模拟器当前运行状态:', {
    running: status1.running,
    chaosMode: status1.chaosMode,
    intervalMs: status1.intervalMs,
    managedRacks: status1.managedRacks,
    totalPacketsSent: status1.totalPacketsSent
  });

  if (!status1.running) {
    await request('/mock/start', { method: 'POST' });
  }

  // 等待 3 秒观察报文自增与遥测流推屏
  const initCount = status1.totalPacketsSent || 0;
  console.log('等待 3 秒接收自动投递遥测流...');
  await sleep(3200);

  const status2 = await request('/mock/status');
  console.log(`累计投递报文数: ${initCount} ➔ ${status2.totalPacketsSent} (增量 +${status2.totalPacketsSent - initCount})`);
  if (status2.totalPacketsSent <= initCount) {
    throw new Error('❌ Mock 模拟器未在后台自动递增投递报文！');
  }

  if (telemetryStream.length === 0) {
    throw new Error('❌ 未从 STOMP 收到 A-03 的自主心跳遥测流！');
  }
  const lastTel = telemetryStream[telemetryStream.length - 1];
  console.log('✅ 收到实时自主心跳遥测推屏: A-03 温度:', lastTel.temperature, '℃, 电压:', lastTel.voltage, 'V, 功率:', lastTel.powerKw, 'kW');

  // 3. 验证瞬态偶发毛刺消抖拦截 (0 误报告警)
  console.log('\n=== 3. 验证瞬态偶发毛刺消抖拦截 (De-bounce: 0 误报) ===');
  const alarmsBeforeGlitch = (await request('/alarm/active') || []).filter(a => a.rackCode === 'A-03');
  console.log(`注入前 A-03 活动告警数: ${alarmsBeforeGlitch.length}`);

  console.log('调用 POST /api/v1/mock/inject-glitch 注入单次 42℃ 极高温毛刺...');
  await request('/mock/inject-glitch?rackCode=A-03', { method: 'POST' });

  // 等待 3 秒观察
  console.log('等待 3 秒消抖窗口评估...');
  await sleep(3000);

  const alarmsAfterGlitch = (await request('/alarm/active') || []).filter(a => a.rackCode === 'A-03');
  console.log(`毛刺注入后 A-03 活动告警数: ${alarmsAfterGlitch.length}`);
  if (alarmsAfterGlitch.length > alarmsBeforeGlitch.length) {
    throw new Error('❌ 瞬态毛刺未被消抖引擎拦截，产生了误报告警！');
  }
  console.log('✅ 瞬态信号毛刺消抖验证 100% 通过！零误报告警产生！');

  // 4. 验证单机柜持续高危超温注入与推屏
  console.log('\n=== 4. 验证单机柜持续高危超温注入 (OVERHEAT) ===');
  console.log('调用 POST /api/v1/mock/inject-overheat 注入 A-03 持续 38.5℃ 高温...');
  alarmStream.length = 0; // 清空捕获
  await request('/mock/inject-overheat?rackCode=A-03', { method: 'POST' });

  // 等待 TRIGGERED CRITICAL 告警
  let tStart = Date.now();
  let triggeredAlarm = null;
  while (!triggeredAlarm && Date.now() - tStart < 8000) {
    triggeredAlarm = alarmStream.find(a => a.action === 'TRIGGERED' && a.rackCode === 'A-03' && a.alarmLevel === 'CRITICAL');
    if (!triggeredAlarm) await sleep(300);
  }

  if (!triggeredAlarm) {
    throw new Error('❌ 持续超温注入后，未在预期内收到 A-03 的 CRITICAL 告警推屏');
  }
  console.log('✅ A-03 严重超温告警推屏验证 100% 通过！告警ID:', triggeredAlarm.alarmId, '指标值:', triggeredAlarm.metricValue);

  // 5. 验证一键复位与自动消警
  console.log('\n=== 5. 验证一键复位与自动消警闭环 (RESET) ===');
  console.log('调用 POST /api/v1/mock/reset 恢复安全工况 (≤24℃)...');
  await request('/mock/reset', { method: 'POST' });

  // 等待 CLEARED 告警推屏 (需等待 3 秒稳定观察期)
  tStart = Date.now();
  let clearedAlarm = null;
  while (!clearedAlarm && Date.now() - tStart < 8000) {
    clearedAlarm = alarmStream.find(a => a.action === 'CLEARED' && (a.rackCode === 'A-03' || a.alarmId === triggeredAlarm.alarmId));
    if (!clearedAlarm) await sleep(300);
  }

  if (!clearedAlarm) {
    throw new Error('❌ 复位正常后，未在预期内收到 CLEARED 消警推屏！');
  }
  console.log('✅ 自动消警闭环验证 100% 通过！消警机柜:', clearedAlarm.rackCode, '告警ID:', clearedAlarm.alarmId);

  // 6. 验证机房集群告警风暴空间关联聚合
  console.log('\n=== 6. 验证机房告警风暴空间关联归并 (STORM) ===');
  console.log('调用 POST /api/v1/mock/inject-storm 触发 A-01, A-02, A-03 并发高温...');
  alarmStream.length = 0;
  await request('/mock/inject-storm', { method: 'POST' });

  // 等待空间聚合主告警推屏
  tStart = Date.now();
  let stormMasterAlarm = null;
  while (!stormMasterAlarm && Date.now() - tStart < 10000) {
    stormMasterAlarm = alarmStream.find(a => a.action === 'TRIGGERED' && a.rcaSummary && a.rcaSummary.includes('空间聚合'));
    if (!stormMasterAlarm) await sleep(300);
  }

  if (!stormMasterAlarm) {
    throw new Error('❌ 告警风暴注入后，未在预期内捕获到机房空间聚合主告警！');
  }
  console.log('✅ 告警风暴空间聚合验证 100% 通过！主告警摘要:', stormMasterAlarm.rcaSummary);

  // 7. 演练完毕一键复位
  console.log('\n=== 7. 测试收尾一键复位 ===');
  await request('/mock/reset', { method: 'POST' });
  console.log('已复位安全工况');

  console.log('\n========================================================================');
  console.log('🎉🎉🎉 [Task T2.5 验收测试] 动环遥测 Mock 模拟器与故障演练中枢全部通过！');
  console.log('      - 自动心跳发生与高频遥测广播:           PASS');
  console.log('      - 瞬态偶发信号毛刺消抖 (零误报):         PASS');
  console.log('      - 单机柜严重超温高危声光推屏:           PASS');
  console.log('      - 安全指标回落自动消警闭环:             PASS');
  console.log('      - 机房同区域告警风暴空间关联聚合:       PASS');
  console.log('========================================================================');

  stompClient.deactivate();
  process.exit(0);
}

main().catch((err) => {
  console.error('❌ 测试异常失败:', err);
  process.exit(1);
});
