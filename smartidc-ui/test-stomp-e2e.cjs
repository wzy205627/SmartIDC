const { Client } = require('./node_modules/@stomp/stompjs');

let emqxToken = null;

async function getEmqxToken() {
  if (emqxToken) return emqxToken;
  const loginRes = await fetch('http://localhost:18083/api/v5/login', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ username: 'admin', password: 'smartidc123' })
  });
  const data = await loginRes.json();
  if (!data.token) {
    throw new Error('EMQX Login failed: ' + JSON.stringify(data));
  }
  emqxToken = data.token;
  return emqxToken;
}

async function publishEmqx(rackCode, temp, humidity = 48.0, voltage = 220.0, current = 10.5, power = 2.31) {
  const token = await getEmqxToken();
  const url = 'http://localhost:18083/api/v5/publish';
  const topic = `/sys/smartidc/000000/rack/${rackCode}/telemetry`;
  const payloadObj = {
    tenantId: '000000',
    rackCode: rackCode,
    deviceKey: `TH-${rackCode}-001`,
    temperature: temp,
    humidity: humidity,
    voltage: voltage,
    currentAmp: current,
    powerKw: power,
    timestamp: Date.now()
  };
  const body = {
    topic: topic,
    payload: JSON.stringify(payloadObj),
    qos: 1
  };

  const res = await fetch(url, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
      'Authorization': 'Bearer ' + token
    },
    body: JSON.stringify(body)
  });
  if (!res.ok) {
    const text = await res.text();
    throw new Error(`EMQX Publish failed: ${res.status} ${text}`);
  }
}

async function sleep(ms) {
  return new Promise((resolve) => setTimeout(resolve, ms));
}

async function main() {
  console.log('=== [T2.4 验收测试] 1. 使用 @stomp/stompjs 连接后端 WebSocket 端点 ws://localhost:8080/ws/smartidc ===');

  let resolveConnected;
  const connectedPromise = new Promise((resolve) => { resolveConnected = resolve; });

  const telemetryReceived = [];
  const alarmsReceived = [];

  const client = new Client({
    brokerURL: 'ws://localhost:8080/ws/smartidc',
    reconnectDelay: 0,
    heartbeatIncoming: 10000,
    heartbeatOutgoing: 10000,
    onConnect: (frame) => {
      console.log('✅ STOMP 成功连接至 Spring Boot WebSocket Broker！');
      resolveConnected();
    },
    onStompError: (frame) => {
      console.error('❌ STOMP 错误:', frame);
    }
  });

  client.activate();
  await connectedPromise;

  console.log('\n=== 2. 订阅 /topic/rack-telemetry/A-03 与 /topic/alarms ===');
  client.subscribe('/topic/rack-telemetry/A-03', (msg) => {
    const payload = JSON.parse(msg.body);
    console.log('📡 [STOMP 推屏] 收到机柜遥测流报文:', payload);
    telemetryReceived.push(payload);
  });

  client.subscribe('/topic/alarms', (msg) => {
    const payload = JSON.parse(msg.body);
    console.log('🚨 [STOMP 推屏] 收到告警事件广播:', payload);
    alarmsReceived.push(payload);
  });
  console.log('✅ 订阅成功挂载！');

  await sleep(1000);

  console.log('\n=== 3. 验证单机柜实时遥测推屏 (/topic/rack-telemetry/A-03) ===');
  console.log('向 EMQX 投递正常动环遥测 (A-03: 25.3℃, 222.4V)...');
  await publishEmqx('A-03', 25.3, 46.5, 222.4, 10.2, 2.26);

  // 等待遥测推屏到达
  let tStart = Date.now();
  while (telemetryReceived.length === 0 && Date.now() - tStart < 4000) {
    await sleep(200);
  }
  if (telemetryReceived.length === 0) {
    throw new Error('❌ 未在预期时间内收到 /topic/rack-telemetry/A-03 遥测推屏');
  }
  const telItem = telemetryReceived[telemetryReceived.length - 1];
  if (telItem.rackCode !== 'A-03' || Number(telItem.temperature) !== 25.3) {
    throw new Error(`❌ 遥测推屏报文内容不符合预期: ${JSON.stringify(telItem)}`);
  }
  console.log('✅ 单机柜秒级遥测推屏校验 100% 通过！温度:', telItem.temperature, '℃, 电压:', telItem.voltage, 'V');

  console.log('\n=== 4. 验证高危超温告警全双工推屏 (/topic/alarms) ===');
  console.log('向 EMQX 连续注入 3 次超温越限报文 (A-03: 36.8℃) 推进消抖状态机...');
  for (let i = 0; i < 3; i++) {
    await publishEmqx('A-03', 36.8, 52.0, 220.0, 12.5, 2.75);
    await sleep(400);
  }

  // 等待 TRIGGERED 告警推屏
  tStart = Date.now();
  let triggeredAlarm = null;
  while (!triggeredAlarm && Date.now() - tStart < 5000) {
    triggeredAlarm = alarmsReceived.find(a => a.action === 'TRIGGERED' && a.rackCode === 'A-03');
    if (!triggeredAlarm) await sleep(200);
  }

  if (!triggeredAlarm) {
    throw new Error('❌ 未在预期时间内收到 /topic/alarms TRIGGERED 告警广播');
  }
  console.log('✅ 高危告警即时推屏校验 100% 通过！', {
    action: triggeredAlarm.action,
    alarmId: triggeredAlarm.alarmId,
    rackCode: triggeredAlarm.rackCode,
    alarmLevel: triggeredAlarm.alarmLevel,
    metricValue: triggeredAlarm.metricValue
  });
  if (triggeredAlarm.alarmLevel !== 'CRITICAL') {
    throw new Error(`预期告警等级为 CRITICAL，实际为 ${triggeredAlarm.alarmLevel}`);
  }

  console.log('\n=== 5. 验证自动消警推屏 (/topic/alarms) ===');
  console.log('向 EMQX 投递安全指标报文 (A-03: 24.2℃ <= 28℃迟滞恢复线)，启动消警观察期...');
  await publishEmqx('A-03', 24.2, 45.0, 220.0, 9.5, 2.09);
  console.log('等待 3.5 秒消警稳定观察期 (recoveryStableTimeMs=3000ms)...');
  await sleep(3500);
  console.log('再次发送持续稳定安全报文触发正式 CLEARED...');
  await publishEmqx('A-03', 24.2, 45.0, 220.0, 9.5, 2.09);

  // 等待 CLEARED 告警推屏
  tStart = Date.now();
  let clearedAlarm = null;
  while (!clearedAlarm && Date.now() - tStart < 6000) {
    clearedAlarm = alarmsReceived.find(a => a.action === 'CLEARED' && (a.rackCode === 'A-03' || a.alarmId === triggeredAlarm.alarmId));
    if (!clearedAlarm) await sleep(200);
  }

  if (!clearedAlarm) {
    throw new Error('❌ 未在预期时间内收到 /topic/alarms CLEARED 消警广播');
  }
  console.log('✅ 自动消警即时推屏校验 100% 通过！', {
    action: clearedAlarm.action,
    alarmId: clearedAlarm.alarmId,
    rackCode: clearedAlarm.rackCode
  });

  console.log('\n================================================================');
  console.log('🎉🎉🎉 [Task T2.4 验收测试] WebSocket + STOMP 实时通知管道');
  console.log('      - WebSocket 连接建立与 STOMP 握手:       PASS');
  console.log('      - /topic/rack-telemetry/{rackCode} 秒级推屏: PASS');
  console.log('      - /topic/alarms 高危告警即刻推屏:         PASS');
  console.log('      - /topic/alarms 动环恢复自动消警推屏:      PASS');
  console.log('================================================================');

  client.deactivate();
  process.exit(0);
}

main().catch((err) => {
  console.error('❌ 测试异常失败:', err);
  process.exit(1);
});
