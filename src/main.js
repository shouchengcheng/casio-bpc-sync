(function () {
const { beijingParts, decodeFrame, encodeFrame, encodeMinute, weekdayMon1 } = globalThis.BPC.encode;
const { createTransmitter } = globalThis.BPC.audio;
const { sourceLabel, syncClock } = globalThis.BPC.time;

const clock = {
  offsetMs: 0,
  ok: false,
  source: 'local',
  rtt: null,
  syncing: false,
  error: '',
};

const transmitter = createTransmitter();
const $ = (id) => document.getElementById(id);

function beijingNowMs() {
  return Date.now() + clock.offsetMs;
}

function readOptions() {
  return {
    frequency: Number($('frequency').value),
    waveform: $('waveform').value,
    invert: $('invert').checked,
    trimMs: Number($('trim').value),
  };
}

function pad(value) {
  return String(value).padStart(2, '0');
}

function formatOffset(offsetMs) {
  const rounded = Math.round(offsetMs);
  return `${rounded >= 0 ? '+' : ''}${rounded} ms`;
}

function formatElapsed(ms) {
  const total = Math.floor(ms / 1000);
  return `${pad(Math.floor(total / 60))}:${pad(total % 60)}`;
}

function currentFrameInput(now = beijingParts(beijingNowMs())) {
  return {
    year: now.year,
    month: now.month,
    day: now.day,
    hour: now.hour,
    minute: now.minute,
    second: now.second - (now.second % 20),
  };
}

function checkFrame(nowMs = beijingNowMs()) {
  const input = currentFrameInput(beijingParts(nowMs));
  const decoded = decodeFrame(encodeFrame(input));
  const matched =
    decoded.ok &&
    decoded.year === input.year &&
    decoded.month === input.month &&
    decoded.day === input.day &&
    decoded.hour === input.hour &&
    decoded.minute === input.minute &&
    decoded.second === input.second;
  return { ok: matched, input, decoded };
}

function setCheck(message, kind) {
  const node = $('check-result');
  node.textContent = message;
  node.className = kind || '';
}

function ensureBars() {
  const root = $('frames');
  if (root.childElementCount === 3) return;
  root.replaceChildren();
  for (const label of [':00', ':20', ':40']) {
    const frame = document.createElement('div');
    frame.className = 'frame';
    const name = document.createElement('div');
    name.className = 'frame-label';
    name.textContent = label;
    const cells = document.createElement('div');
    cells.className = 'cells';
    for (let index = 0; index < 20; index += 1) {
      const cell = document.createElement('div');
      cell.className = 'cell';
      cell.append(document.createElement('span'));
      cells.append(cell);
    }
    frame.append(name, cells);
    root.append(frame);
  }
}

function renderBars(now) {
  ensureBars();
  const symbols = encodeMinute(now);
  const cells = $('frames').querySelectorAll('.cell');
  cells.forEach((cell, index) => {
    const symbol = symbols[index];
    const kind = symbol === null ? 'marker' : `q${symbol}`;
    cell.className = `cell ${kind}${index === now.second ? ' current' : ''}`;
    const width = symbol === null ? 100 : (symbol + 1) * 10;
    cell.firstElementChild.style.width = `${width}%`;
  });
}

function render() {
  const now = beijingParts(beijingNowMs());
  $('beijing-time').textContent = `${pad(now.hour)}:${pad(now.minute)}:${pad(now.second)}`;
  const weekday = '一二三四五六日'[weekdayMon1(now.year, now.month, now.day) - 1];
  $('beijing-date').textContent = `${now.year}-${pad(now.month)}-${pad(now.day)} 星期${weekday}`;

  const status = $('sync-status');
  if (clock.syncing) {
    status.className = '';
    status.textContent = '正在向公网对时…';
  } else if (clock.ok) {
    status.className = '';
    status.textContent = `时间来源：${sourceLabel(clock.source)}，钟差 ${formatOffset(clock.offsetMs)}，往返 ${Math.round(clock.rtt)} ms`;
  } else {
    status.className = 'warn';
    status.textContent = clock.error
      ? `对时失败，正在使用电脑时钟。${clock.error}`
      : '对时失败，正在使用电脑时钟。请检查网络后重新对时。';
  }

  $('toggle').textContent = transmitter.running ? '停止' : '开始';
  $('elapsed').textContent = transmitter.running ? `已发射 ${formatElapsed(transmitter.elapsedMs())}` : '';
  $('trim-readout').textContent = `${readOptions().trimMs} ms（正值提前）`;
  $('invert-note').textContent = readOptions().invert ? '反相已打开：数据秒改为开头发声，帧标志整秒静音。' : '';
  const latencyMs = Math.round(transmitter.outputLatencySec() * 1000);
  $('latency').textContent = transmitter.running ? `已扣除声卡输出延迟 ${latencyMs} ms` : '开始后会自动扣除声卡输出延迟';
  renderBars(now);
}

function loadSuning(url) {
  return new Promise((resolve, reject) => {
    const callbackName = `bpcTime_${Date.now()}_${Math.floor(Math.random() * 10000)}`;
    const script = document.createElement('script');
    let settled = false;
    const timer = setTimeout(() => finish(new Error('苏宁对时超时')), 4000);

    function finish(error, data) {
      if (settled) return;
      settled = true;
      clearTimeout(timer);
      delete window[callbackName];
      script.remove();
      if (error) reject(error);
      else resolve(data);
    }

    window[callbackName] = (data) => finish(null, data);
    script.onerror = () => finish(new Error('苏宁对时失败'));
    script.src = `${url}?callback=${callbackName}`;
    document.head.append(script);
  });
}

async function resync() {
  clock.syncing = true;
  clock.error = '';
  render();
  $('resync').disabled = true;
  try {
    const result = await syncClock(globalThis.fetch, {
      loadJsonp: loadSuning,
      allowFetch: location.protocol !== 'file:',
    });
    clock.ok = result.ok;
    clock.offsetMs = result.offsetMs;
    clock.source = result.source;
    clock.rtt = result.rtt;
    if (!result.ok) {
      clock.error = result.samples.map((sample) => sourceLabel(sample.source)).join('、') + ' 都没有返回时间。';
    }
  } catch (error) {
    clock.ok = false;
    clock.offsetMs = 0;
    clock.source = 'local';
    clock.rtt = null;
    clock.error = error instanceof Error ? error.message : String(error);
  } finally {
    clock.syncing = false;
    $('resync').disabled = false;
    render();
  }
}

function showSelfCheck() {
  const result = checkFrame();
  if (result.ok) {
    const input = result.input;
    setCheck(
      `自检通过：${input.year}-${pad(input.month)}-${pad(input.day)} ${pad(input.hour)}:${pad(input.minute)}:${pad(input.second)}`,
      'ok',
    );
  } else {
    setCheck(`自检失败：${result.decoded.error || '解码结果与北京时间不一致'}`, 'bad');
  }
  return result.ok;
}

$('toggle').addEventListener('click', async () => {
  $('toggle').disabled = true;
  try {
    if (transmitter.running) {
      await transmitter.stop();
      setCheck('已停止发射。', '');
    } else {
      await transmitter.start({ getBeijingNow: beijingNowMs, getOptions: readOptions });
      setCheck('正在发射。把耳机喇叭贴在表背，并让手表强制接收。', 'ok');
    }
  } catch (error) {
    setCheck(error instanceof Error ? error.message : '无法打开发射', 'bad');
  } finally {
    $('toggle').disabled = false;
    render();
  }
});

$('resync').addEventListener('click', () => {
  resync();
});

$('self-check').addEventListener('click', showSelfCheck);

for (const id of ['frequency', 'waveform', 'invert']) {
  $(id).addEventListener('change', () => {
    transmitter.refresh();
    render();
  });
}
$('trim').addEventListener('input', render);
$('trim').addEventListener('change', () => {
  transmitter.refresh();
  render();
});

ensureBars();
render();
showSelfCheck();
resync();
setInterval(render, 200);
})();
