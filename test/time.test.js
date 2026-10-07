import test from 'node:test';
import assert from 'node:assert/strict';
import '../src/time.js';

const { TIME_SOURCES, offsetFromExchange, pickBestSample, syncClock } = globalThis.BPC.time;

test('半个往返时延修正钟差', () => {
  const result = offsetFromExchange({
    sentAt: 1_000,
    receivedAt: 1_200,
    serverMs: 5_000,
  });
  assert.equal(result.rtt, 200);
  assert.equal(result.offsetMs, 5_000 - 1_100);
});

test('在成功样本里选取往返时延最小的来源', () => {
  const best = pickBestSample([
    { ok: false, source: 'suning', rtt: 10 },
    { ok: true, source: 'worldtimeapi', rtt: 400, offsetMs: 12 },
    { ok: true, source: 'timeapi', rtt: 180, offsetMs: 20 },
  ]);
  assert.equal(best.source, 'timeapi');
  assert.equal(best.offsetMs, 20);
});

test('没有成功样本时返回空', () => {
  assert.equal(pickBestSample([{ ok: false, source: 'suning' }]), null);
});

test('时间源都能从样例响应里解析出纪元毫秒', () => {
  const byId = Object.fromEntries(TIME_SOURCES.map((source) => [source.id, source]));

  assert.equal(
    byId.suning.parse({ code: '1', currentTime: 1_759_000_000_000 }),
    1_759_000_000_000,
  );
  assert.equal(
    byId.worldtimeapi.parse({
      unixtime: 1_759_000_000,
      utc_datetime: '2026-10-07T05:00:00.000000+00:00',
    }),
    1_759_000_000_000,
  );
  assert.equal(
    byId.timeapi.parse({ dateTime: '2026-10-07T13:00:00.000' }),
    Date.parse('2026-10-07T13:00:00.000+08:00'),
  );
  assert.equal(
    byId.timeapi.parse({ dateTime: '2026-10-07T13:24:25.5078903' }),
    Date.parse('2026-10-07T13:24:25.507+08:00'),
  );
});

test('全部时间源失败时回退到电脑时钟', async () => {
  const result = await syncClock(async () => {
    throw new Error('offline');
  });
  assert.equal(result.ok, false);
  assert.equal(result.offsetMs, 0);
  assert.equal(result.source, 'local');
  assert.equal(result.samples.length, TIME_SOURCES.length);
});

test('传入脚本回调时，苏宁不走会被跨域拦住的 fetch', async () => {
  let fetched = false;
  const result = await syncClock(
    async () => {
      fetched = true;
      throw new Error('cors');
    },
    {
      loadJsonp: async () => ({ currentTime: Date.now() }),
    },
  );
  assert.equal(fetched, true);
  assert.equal(result.ok, true);
  assert.equal(result.source, 'suning');
});

test('直接打开文件时只走脚本对时，不发普通请求', async () => {
  let fetched = false;
  const result = await syncClock(
    async () => {
      fetched = true;
      throw new Error('cors');
    },
    {
      allowFetch: false,
      loadJsonp: async () => ({ currentTime: 5_000 }),
    },
  );
  assert.equal(fetched, false);
  assert.equal(result.ok, true);
  assert.equal(result.source, 'suning');
});

test('依次请求后保留往返时延更短的成功源', async () => {
  const result = await syncClock(async (url) => {
    const href = String(url);
    if (href.includes('suning')) {
      await new Promise((resolve) => setTimeout(resolve, 40));
      return { ok: true, json: async () => ({ currentTime: Date.now() }) };
    }
    if (href.includes('worldtimeapi')) {
      return { ok: false, status: 404, json: async () => ({}) };
    }
    return {
      ok: true,
      json: async () => ({ dateTime: '2026-10-07T13:00:00.000' }),
    };
  });
  assert.equal(result.ok, true);
  assert.equal(result.source, 'timeapi');
});
