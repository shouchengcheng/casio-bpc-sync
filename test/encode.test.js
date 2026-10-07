import test from 'node:test';
import assert from 'node:assert/strict';
import {
  beijingParts,
  decodeFrame,
  encodeFrame,
  encodeMinute,
  secondEnvelope,
} from '../src/encode.js';

function xorBits(symbols) {
  let x = 0;
  for (const symbol of symbols) {
    x ^= (symbol >> 1) & 1;
    x ^= symbol & 1;
  }
  return x;
}

test('正午帧：小时为 0、下午为 1，星期日以外的周三字段正确', () => {
  const symbols = encodeFrame({
    year: 2026,
    month: 10,
    day: 7,
    hour: 12,
    minute: 0,
    second: 0,
  });

  assert.equal(symbols.length, 20);
  assert.equal(symbols[0], null);
  assert.deepEqual(symbols.slice(1), [
    0, 0, 0, 0, 0, 0, 0, 0, 3, 2, 0, 1, 3, 2, 2, 1, 2, 2, 0,
  ]);
  assert.equal((symbols[10] >> 1) & 1, 1);
  assert.equal(symbols[10] & 1, xorBits(symbols.slice(1, 10)));
  assert.equal(symbols[19] & 1, xorBits(symbols.slice(11, 19)));
});

test('周日编码为 7', () => {
  const symbols = encodeFrame({
    year: 2026,
    month: 10,
    day: 11,
    hour: 0,
    minute: 0,
    second: 0,
  });
  assert.equal(symbols[8], 1);
  assert.equal(symbols[9], 3);
  assert.equal((symbols[8] << 2) | symbols[9], 7);
});

test(':20 帧的秒字段为 1，13 点编成小时 1 且下午', () => {
  const symbols = encodeFrame({
    year: 2026,
    month: 10,
    day: 7,
    hour: 13,
    minute: 20,
    second: 20,
  });
  assert.equal(symbols[1], 1);
  assert.equal(symbols[3], 0);
  assert.equal(symbols[4], 1);
  assert.equal((symbols[10] >> 1) & 1, 1);
  assert.equal(symbols[5], 1);
  assert.equal(symbols[6], 1);
  assert.equal(symbols[7], 0);
  assert.equal(symbols[10] & 1, xorBits(symbols.slice(1, 10)));
  assert.equal(symbols[19] & 1, xorBits(symbols.slice(11, 19)));
});

test('年份 64 权落在秒 19 的高位，低 6 位为 0', () => {
  const symbols = encodeFrame({
    year: 2064,
    month: 1,
    day: 1,
    hour: 0,
    minute: 0,
    second: 0,
  });
  assert.equal(symbols[16], 0);
  assert.equal(symbols[17], 0);
  assert.equal(symbols[18], 0);
  assert.equal((symbols[19] >> 1) & 1, 1);
  assert.equal(symbols[19] & 1, xorBits(symbols.slice(11, 19)));
});

test('编码再解码回到帧起始的北京时间', () => {
  const cases = [
    { year: 2026, month: 10, day: 7, hour: 12, minute: 0, second: 0 },
    { year: 2026, month: 10, day: 11, hour: 0, minute: 0, second: 0 },
    { year: 2026, month: 10, day: 7, hour: 13, minute: 20, second: 20 },
    { year: 2026, month: 12, day: 31, hour: 23, minute: 59, second: 40 },
    { year: 2064, month: 1, day: 1, hour: 0, minute: 0, second: 0 },
  ];

  for (const input of cases) {
    const decoded = decodeFrame(encodeFrame(input));
    assert.equal(decoded.ok, true, JSON.stringify(decoded));
    assert.equal(decoded.year, input.year);
    assert.equal(decoded.month, input.month);
    assert.equal(decoded.day, input.day);
    assert.equal(decoded.hour, input.hour);
    assert.equal(decoded.minute, input.minute);
    assert.equal(decoded.second, input.second);
  }
});

test('一分钟含三帧，秒字段依次为 0、1、2', () => {
  const symbols = encodeMinute({
    year: 2026,
    month: 10,
    day: 7,
    hour: 8,
    minute: 5,
  });
  assert.equal(symbols.length, 60);
  assert.equal(symbols[0], null);
  assert.equal(symbols[20], null);
  assert.equal(symbols[40], null);
  assert.equal(symbols[1], 0);
  assert.equal(symbols[21], 1);
  assert.equal(symbols[41], 2);
});

test('标准包络开头静音，反相改为开头发声，帧标志整秒满幅或整秒静音', () => {
  assert.deepEqual(secondEnvelope(0, false), [
    { t: 0, gain: 0 },
    { t: 0.1, gain: 1 },
  ]);
  assert.deepEqual(secondEnvelope(3, false), [
    { t: 0, gain: 0 },
    { t: 0.4, gain: 1 },
  ]);
  assert.deepEqual(secondEnvelope(null, false), [{ t: 0, gain: 1 }]);
  assert.deepEqual(secondEnvelope(1, true), [
    { t: 0, gain: 1 },
    { t: 0.2, gain: 0 },
  ]);
  assert.deepEqual(secondEnvelope(null, true), [{ t: 0, gain: 0 }]);
});

test('纪元毫秒按东八区拆成北京时间', () => {
  const parts = beijingParts(Date.UTC(2026, 9, 7, 4, 16, 20));
  assert.deepEqual(parts, {
    year: 2026,
    month: 10,
    day: 7,
    hour: 12,
    minute: 16,
    second: 20,
    ms: 0,
  });
});

test('非整帧秒被拒绝', () => {
  assert.throws(() =>
    encodeFrame({
      year: 2026,
      month: 10,
      day: 7,
      hour: 12,
      minute: 0,
      second: 15,
    }),
  );
});
