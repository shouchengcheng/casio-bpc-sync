(function () {
const TIME_SOURCES = [
  {
    id: 'suning',
    label: '苏宁',
    mode: 'jsonp',
    url: 'https://f.m.suning.com/api/ct.do',
    parse(data) {
      const ms = Number(data.currentTime);
      if (!Number.isFinite(ms)) throw new Error('苏宁时间无效');
      return ms;
    },
  },
  {
    id: 'worldtimeapi',
    label: 'WorldTimeAPI',
    mode: 'fetch',
    url: 'https://worldtimeapi.org/api/timezone/Asia/Shanghai',
    parse(data) {
      if (Number.isFinite(Number(data.unixtime))) return Number(data.unixtime) * 1000;
      const ms = Date.parse(data.utc_datetime);
      if (!Number.isFinite(ms)) throw new Error('WorldTimeAPI 时间无效');
      return ms;
    },
  },
  {
    id: 'timeapi',
    label: 'TimeAPI',
    mode: 'fetch',
    url: 'https://timeapi.io/api/Time/current/zone?timeZone=Asia/Shanghai',
    parse(data) {
      return parseShanghaiDateTime(data.dateTime);
    },
  },
];

function offsetFromExchange({ sentAt, receivedAt, serverMs }) {
  const rtt = receivedAt - sentAt;
  return {
    rtt,
    offsetMs: serverMs - (sentAt + rtt / 2),
  };
}

function pickBestSample(samples) {
  const ok = samples.filter((sample) => sample.ok);
  if (ok.length === 0) return null;
  return ok.reduce((best, sample) => (sample.rtt < best.rtt ? sample : best));
}

function parseShanghaiDateTime(dateTime) {
  const match = String(dateTime).match(
    /^(\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2})(?:\.(\d+))?/,
  );
  if (!match) throw new Error('TimeAPI 时间无效');
  const fraction = (match[2] || '0').slice(0, 3).padEnd(3, '0');
  const ms = Date.parse(`${match[1]}.${fraction}+08:00`);
  if (!Number.isFinite(ms)) throw new Error('TimeAPI 时间无效');
  return ms;
}

async function syncClock(fetchImpl = globalThis.fetch, extras = {}) {
  const loadJsonp = extras.loadJsonp;
  const allowFetch = extras.allowFetch !== false;
  const samples = [];
  for (const source of TIME_SOURCES) {
    const sentAt = Date.now();
    try {
      let data;
      if (source.mode === 'jsonp' && loadJsonp) {
        data = await loadJsonp(source.url);
      } else if (!allowFetch) {
        throw new Error('当前打开方式不能用普通请求对时');
      } else {
        const response = await fetchImpl(source.url, {
          cache: 'no-store',
          signal: AbortSignal.timeout(4000),
        });
        if (!response.ok) throw new Error(`HTTP ${response.status}`);
        data = await response.json();
      }
      const receivedAt = Date.now();
      const serverMs = source.parse(data);
      const { rtt, offsetMs } = offsetFromExchange({ sentAt, receivedAt, serverMs });
      samples.push({ ok: true, source: source.id, rtt, offsetMs, serverMs });
    } catch (error) {
      samples.push({
        ok: false,
        source: source.id,
        error: error instanceof Error ? error.message : String(error),
      });
    }
  }

  const best = pickBestSample(samples);
  if (!best) {
    return { ok: false, offsetMs: 0, source: 'local', rtt: null, samples };
  }
  return {
    ok: true,
    offsetMs: best.offsetMs,
    source: best.source,
    rtt: best.rtt,
    samples,
  };
}

function sourceLabel(id) {
  if (id === 'local') return '电脑时钟';
  return TIME_SOURCES.find((source) => source.id === id)?.label ?? id;
}

globalThis.BPC = globalThis.BPC || {};
globalThis.BPC.time = {
  TIME_SOURCES,
  offsetFromExchange,
  pickBestSample,
  syncClock,
  sourceLabel,
};
})();
