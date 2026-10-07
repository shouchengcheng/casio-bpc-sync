const SHANGHAI_OFFSET_MS = 8 * 60 * 60 * 1000;
const PULSE_WIDTH = [0.1, 0.2, 0.3, 0.4];

export function beijingParts(epochMs) {
  const shifted = new Date(epochMs + SHANGHAI_OFFSET_MS);
  return {
    year: shifted.getUTCFullYear(),
    month: shifted.getUTCMonth() + 1,
    day: shifted.getUTCDate(),
    hour: shifted.getUTCHours(),
    minute: shifted.getUTCMinutes(),
    second: shifted.getUTCSeconds(),
    ms: shifted.getUTCMilliseconds(),
  };
}

export function epochMsFromBeijing(parts) {
  return Date.UTC(
    parts.year,
    parts.month - 1,
    parts.day,
    parts.hour - 8,
    parts.minute,
    parts.second,
    parts.ms || 0,
  );
}

export function weekdayMon1(year, month, day) {
  const jsDay = new Date(Date.UTC(year, month - 1, day)).getUTCDay();
  return jsDay === 0 ? 7 : jsDay;
}

function xorSymbols(symbols) {
  let parity = 0;
  for (const symbol of symbols) {
    parity ^= (symbol >> 1) & 1;
    parity ^= symbol & 1;
  }
  return parity;
}

export function encodeFrame(parts) {
  const { year, month, day, hour, minute, second } = parts;
  if (second !== 0 && second !== 20 && second !== 40) {
    throw new Error(`帧起始秒必须是 0、20 或 40，收到 ${second}`);
  }

  const hour12 = hour % 12;
  const pm = hour >= 12 ? 1 : 0;
  const year2 = year % 100;
  const weekday = weekdayMon1(year, month, day);
  const symbols = new Array(20);

  symbols[0] = null;
  symbols[1] = second / 20;
  symbols[2] = 0;
  symbols[3] = hour12 >> 2;
  symbols[4] = hour12 & 3;
  symbols[5] = minute >> 4;
  symbols[6] = (minute >> 2) & 3;
  symbols[7] = minute & 3;
  symbols[8] = weekday >> 2;
  symbols[9] = weekday & 3;
  symbols[10] = (pm << 1) | xorSymbols(symbols.slice(1, 10));
  symbols[11] = (day >> 4) & 1;
  symbols[12] = (day >> 2) & 3;
  symbols[13] = day & 3;
  symbols[14] = month >> 2;
  symbols[15] = month & 3;
  symbols[16] = (year2 >> 4) & 3;
  symbols[17] = (year2 >> 2) & 3;
  symbols[18] = year2 & 3;
  symbols[19] = ((year2 >> 6) << 1) | xorSymbols(symbols.slice(11, 19));
  return symbols;
}

export function encodeMinute(parts) {
  const symbols = [];
  for (const second of [0, 20, 40]) {
    symbols.push(...encodeFrame({ ...parts, second }));
  }
  return symbols;
}

export function decodeFrame(symbols, century = 2000) {
  if (!Array.isArray(symbols) || symbols.length !== 20) {
    return { ok: false, error: '帧长度必须是 20' };
  }
  for (let index = 1; index < 20; index += 1) {
    const symbol = symbols[index];
    if (!Number.isInteger(symbol) || symbol < 0 || symbol > 3) {
      return { ok: false, error: `秒 ${index} 不是四进制符号` };
    }
  }

  if ((symbols[10] & 1) !== xorSymbols(symbols.slice(1, 10))) {
    return { ok: false, error: '前半校验失败' };
  }
  if ((symbols[19] & 1) !== xorSymbols(symbols.slice(11, 19))) {
    return { ok: false, error: '后半校验失败' };
  }
  if (symbols[1] > 2) {
    return { ok: false, error: '秒字段无效' };
  }

  const hour12 = (symbols[3] << 2) | symbols[4];
  const minute = (symbols[5] << 4) | (symbols[6] << 2) | symbols[7];
  const weekday = (symbols[8] << 2) | symbols[9];
  const day = ((symbols[11] & 1) << 4) | (symbols[12] << 2) | symbols[13];
  const month = (symbols[14] << 2) | symbols[15];
  if (hour12 > 11 || minute > 59 || weekday < 1 || weekday > 7) {
    return { ok: false, error: '时间字段超出范围' };
  }
  if (month < 1 || month > 12 || day < 1 || day > 31) {
    return { ok: false, error: '日期超出范围' };
  }

  const year2 =
    (symbols[16] << 4) |
    (symbols[17] << 2) |
    symbols[18] |
    (((symbols[19] >> 1) & 1) << 6);
  const year = century + year2;
  if (weekdayMon1(year, month, day) !== weekday) {
    return { ok: false, error: '星期与日期不符' };
  }

  const pm = (symbols[10] >> 1) & 1;
  return {
    ok: true,
    year,
    month,
    day,
    hour: hour12 + (pm ? 12 : 0),
    minute,
    second: symbols[1] * 20,
    weekday,
    pm,
  };
}

export function secondEnvelope(symbol, invert) {
  if (symbol === null) {
    return [{ t: 0, gain: invert ? 0 : 1 }];
  }
  const width = PULSE_WIDTH[symbol];
  if (invert) {
    return [
      { t: 0, gain: 1 },
      { t: width, gain: 0 },
    ];
  }
  return [
    { t: 0, gain: 0 },
    { t: width, gain: 1 },
  ];
}
