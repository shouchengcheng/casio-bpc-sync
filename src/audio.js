import { beijingParts, encodeFrame, secondEnvelope } from './encode.js';

const HORIZON_MS = 120_000;
const LEAD_SEC = 0.08;
const EXTEND_MS = 30_000;

export function audioWhen({
  ctxCurrentTime,
  beijingNowMs,
  targetBeijingMs,
  outputLatencySec,
  trimMs,
}) {
  return (
    ctxCurrentTime +
    (targetBeijingMs - beijingNowMs) / 1000 -
    outputLatencySec -
    trimMs / 1000
  );
}

export function upcomingSecondMarks(beijingNowMs, horizonMs, scheduledUntilMs) {
  const horizon = beijingNowMs + horizonMs;
  let mark = Math.ceil(beijingNowMs / 1000) * 1000;
  if (mark < scheduledUntilMs) mark = scheduledUntilMs;
  const marks = [];
  for (; mark < horizon; mark += 1000) marks.push(mark);
  return marks;
}

function sameOptions(left, right) {
  return (
    left &&
    right &&
    left.frequency === right.frequency &&
    left.waveform === right.waveform &&
    left.invert === right.invert &&
    left.trimMs === right.trimMs
  );
}

export function createTransmitter() {
  let ctx = null;
  let osc = null;
  let gain = null;
  let timer = null;
  let running = false;
  let startedPerf = 0;
  let scheduledUntilMs = 0;
  let applied = null;
  let getBeijingNow = () => Date.now();
  let getOptions = () => ({
    frequency: 13700,
    waveform: 'sine',
    invert: false,
    trimMs: 0,
  });

  function outputLatencySec() {
    if (!ctx) return 0;
    if (Number.isFinite(ctx.outputLatency)) return ctx.outputLatency;
    if (Number.isFinite(ctx.baseLatency)) return ctx.baseLatency;
    return 0;
  }

  function teardownGraph() {
    if (osc) {
      try {
        osc.stop();
      } catch {
        // 振荡器还没开始时 stop 会抛错，忽略即可。
      }
      osc.disconnect();
    }
    if (gain) gain.disconnect();
    osc = null;
    gain = null;
    scheduledUntilMs = 0;
  }

  function scheduleAhead(options) {
    const beijingNowMs = getBeijingNow();
    const ctxNow = ctx.currentTime;
    const latency = outputLatencySec();
    const marks = upcomingSecondMarks(beijingNowMs, HORIZON_MS, scheduledUntilMs);
    for (const mark of marks) {
      const when = audioWhen({
        ctxCurrentTime: ctxNow,
        beijingNowMs,
        targetBeijingMs: mark,
        outputLatencySec: latency,
        trimMs: options.trimMs,
      });
      if (when < ctx.currentTime + LEAD_SEC) continue;
      const parts = beijingParts(mark);
      const frameSecond = parts.second - (parts.second % 20);
      const symbols = encodeFrame({ ...parts, second: frameSecond });
      const envelope = secondEnvelope(symbols[parts.second % 20], options.invert);
      try {
        for (const point of envelope) {
          gain.gain.setValueAtTime(point.gain, when + point.t);
        }
      } catch {
        // 这一秒已经落到声卡时间轴后面，留给后面的整秒继续排。
      }
    }
    if (marks.length > 0) scheduledUntilMs = marks[marks.length - 1] + 1000;
  }

  function rebuild(options) {
    teardownGraph();
    applied = { ...options };
    gain = ctx.createGain();
    gain.gain.value = 0;
    osc = ctx.createOscillator();
    osc.type = options.waveform === 'square' ? 'square' : 'sine';
    osc.frequency.value = options.frequency;
    osc.connect(gain);
    gain.connect(ctx.destination);
    osc.start(ctx.currentTime + 0.05);
    scheduleAhead(applied);
  }

  function extend() {
    if (!running || !ctx) return;
    const options = getOptions();
    if (!sameOptions(options, applied)) {
      rebuild(options);
      return;
    }
    scheduleAhead(options);
  }

  return {
    get running() {
      return running;
    },
    outputLatencySec,
    elapsedMs() {
      if (!running) return 0;
      return performance.now() - startedPerf;
    },
    async start({ getBeijingNow: clock, getOptions: optionsFn }) {
      getBeijingNow = clock;
      getOptions = optionsFn;
      const AudioCtx = globalThis.AudioContext || globalThis.webkitAudioContext;
      if (!AudioCtx) throw new Error('这个浏览器没有声卡接口');
      if (!ctx || ctx.state === 'closed') ctx = new AudioCtx({ sampleRate: 48000 });
      if (ctx.state === 'suspended') await ctx.resume();
      running = true;
      startedPerf = performance.now();
      try {
        rebuild(getOptions());
      } catch (error) {
        running = false;
        teardownGraph();
        throw error;
      }
      if (timer) clearInterval(timer);
      timer = setInterval(extend, EXTEND_MS);
    },
    refresh() {
      extend();
    },
    async stop() {
      running = false;
      if (timer) clearInterval(timer);
      timer = null;
      teardownGraph();
      applied = null;
      if (ctx && ctx.state !== 'closed') {
        const closing = ctx;
        ctx = null;
        await closing.close();
      }
    },
  };
}
