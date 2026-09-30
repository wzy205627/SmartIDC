import assert from 'node:assert';
import { parseRackCode } from '../src/utils/rackCodeParser.ts';

console.log('=== 开始执行 V1: 扫码解析容错测试 ===');

const testCases = [
  {
    desc: '完整 URL Query (大写)',
    input: 'https://smartidc.corp/profile?rackCode=RACK-A01',
    expected: 'RACK-A01'
  },
  {
    desc: '完整 URL Query (小写 & code 别名)',
    input: 'https://smartidc.corp/assets?code=rack-a01',
    expected: 'RACK-A01'
  },
  {
    desc: '完整 URL Path',
    input: 'https://smartidc.corp/assets/r/RACK-A01',
    expected: 'RACK-A01'
  },
  {
    desc: 'SmartIDC 专属深层链接 (smartidc://)',
    input: 'smartidc://rack/RACK-A01',
    expected: 'RACK-A01'
  },
  {
    desc: 'SmartIDC 深层链接小写',
    input: 'smartidc://rack/rack-a01',
    expected: 'RACK-A01'
  },
  {
    desc: '纯小写编码',
    input: 'rack-a01',
    expected: 'RACK-A01'
  },
  {
    desc: '首尾空格与换行净化',
    input: '   \n  RACK-A01 \t  ',
    expected: 'RACK-A01'
  },
  {
    desc: '下划线分隔命名',
    input: 'RACK_A01',
    expected: 'RACK-A01'
  },
  {
    desc: '短机柜区域编号 (A-01)',
    input: 'A-01',
    expected: 'RACK-A01'
  },
  {
    desc: '紧凑无短杠编号 (A01)',
    input: 'A01',
    expected: 'RACK-A01'
  },
  {
    desc: '冷通道现场脏数据带前缀',
    input: '[QR] RACK-A01 (ZONE-A)',
    expected: 'RACK-A01'
  }
];

let passed = 0;
for (const tc of testCases) {
  const actual = parseRackCode(tc.input);
  try {
    assert.strictEqual(actual, tc.expected);
    console.log(`[PASS] ${tc.desc}: "${tc.input}" => "${actual}"`);
    passed++;
  } catch (err) {
    console.error(`[FAIL] ${tc.desc}: "${tc.input}" => 期望 "${tc.expected}", 实际 "${actual}"`);
    throw err;
  }
}

console.log(`\n=== V1 扫码容错单测通过: ${passed}/${testCases.length} 全部断言成功 ===`);
