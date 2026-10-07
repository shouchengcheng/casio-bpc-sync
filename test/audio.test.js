import test from 'node:test';
import assert from 'node:assert/strict';
import { audioWhen, upcomingSecondMarks } from '../src/audio.js';

test('音频触发时刻扣除输出延迟和用户微调', () => {
  const when = audioWhen({
    ctxCurrentTime: 10,
    beijingNowMs: 5_000,
    targetBeijingMs: 6_000,
    outputLatencySec: 0.05,
    trimMs: 10,
  });
  assert.equal(when, 10.94);
});

test('只排尚未排过、且落在两分钟窗口内的整秒', () => {
  assert.deepEqual(upcomingSecondMarks(10_500, 3_000, 0), [11_000, 12_000, 13_000]);
  assert.deepEqual(upcomingSecondMarks(10_500, 3_000, 12_000), [12_000, 13_000]);
});
