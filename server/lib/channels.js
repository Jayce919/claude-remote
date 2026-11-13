/**
 * 会话级双通道：每个对话可以选一个直连上游（DeepSeek 官方 / OpenCode 订阅），
 * 不经 cc-switch 的 /claude-desktop 代理，直接用该供应商自己的 base URL 和令牌。
 *
 * 为什么按主机名而不是 provider id 认领：cc-switch 会给同一个供应商在
 * app_type=claude 和 claude-desktop 下各存一行，id 不同、凭据相同；
 * 主机名是这些行唯一稳定的共同点。
 */
import { openDb, modelsFromProviderConfig } from './ccswitch.js';

export const CHANNEL_IDS = ['deepseek', 'opencode'];

const CHANNEL_HOSTS = {
  deepseek: 'api.deepseek.com',
  opencode: 'opencode.ai',
};

/** 选行时看近 7 天的成功请求数：同一个供应商可能留了多把旧 key，用得最多的那把才是现役 */
const LOOKBACK_DAYS = 7;

/** 从 base URL 认出通道；认不出（含 cc-switch 代理地址、官方 Anthropic）返回空串 */
function channelOf(baseUrl) {
  let host = '';
  try {
    host = new URL(String(baseUrl)).hostname.toLowerCase();
  } catch {
    return '';
  }
  for (const id of CHANNEL_IDS) {
    if (host.includes(CHANNEL_HOSTS[id])) return id;
  }
  return '';
}

/** 一行 providers 记录里的直连凭据；两个变量缺一个都不算可用 */
function providerEnv(row) {
  try {
    const cfg = JSON.parse(row?.settings_config || '{}') || {};
    const env = cfg.env || {};
    const base = String(env.ANTHROPIC_BASE_URL || '').trim();
    const token = String(env.ANTHROPIC_AUTH_TOKEN || '').trim();
    if (!base || !token) return null;
    return { base, token, env };
  } catch {
    return null;
  }
}

function successCounts(db, since) {
  const counts = new Map();
  try {
    const rows = db
      .prepare(
        `select provider_id, count(*) n from proxy_request_logs
          where created_at >= ? and status_code between 200 and 299
          group by provider_id`,
      )
      .all(since);
    for (const row of rows) counts.set(String(row.provider_id || ''), Number(row.n) || 0);
  } catch {
    /* 老库缺这张表时按 0 算，退化成「取第一行」 */
  }
  return counts;
}

/**
 * 解析出每个通道选中的那一行（含完整令牌，只在本进程内流转）。
 * 数据库打不开或读失败时返回空表，调用方据此判定通道不可用。
 */
function resolveChannels(ids) {
  const found = new Map();
  let db = null;
  try {
    db = openDb();
    if (!db) return found;
    // cc-switch 的库是 delete 日志模式、默认不等待锁：它正在写请求日志时
    // 读请求可能直接拿到 SQLITE_BUSY，那会把整个通道误判成「凭据不可用」。
    try {
      db.exec('pragma busy_timeout = 2000');
    } catch {
      /* 旧驱动不支持就按默认行为读 */
    }
    const rows = db.prepare('select id, name, app_type, settings_config from providers').all();
    const counts = successCounts(db, Math.floor(Date.now() / 1000) - LOOKBACK_DAYS * 86400);
    const best = new Map();
    for (const row of rows) {
      const parsed = providerEnv(row);
      if (!parsed) continue;
      const id = channelOf(parsed.base);
      if (!id || !ids.includes(id)) continue;
      const score = counts.get(String(row.id)) || 0;
      const prev = best.get(id);
      // 同分（包括全都为 0）保留先出现的那行，结果稳定可复现
      if (prev && score <= prev.score) continue;
      best.set(id, {
        ...parsed,
        score,
        provider: String(row.name || ''),
        providerId: String(row.id || ''),
        row,
      });
    }
    for (const id of ids) {
      const hit = best.get(id);
      if (hit) found.set(id, hit);
    }
    return found;
  } catch {
    return found;
  } finally {
    try {
      db?.close();
    } catch {
      /* ignore */
    }
  }
}

function uniqueModels(list) {
  const seen = new Set();
  const out = [];
  for (const raw of list) {
    const name = String(raw || '').trim();
    if (!name || seen.has(name)) continue;
    seen.add(name);
    out.push(name);
  }
  return out;
}

/** 通道 id 非法时一律回落 deepseek，客户端和旧会话都按这个默认值处理 */
export function normalizeChannelId(value) {
  return CHANNEL_IDS.includes(value) ? value : 'deepseek';
}

/**
 * 给 /api/channels 和 bootstrap 用的通道列表。
 * 返回值里绝不出现完整令牌：客户端只需要知道「有没有凭据」和「末 4 位」。
 */
export function readChannels(config = {}) {
  const defs =
    config && typeof config.channels === 'object' && config.channels ? config.channels : {};
  const found = resolveChannels(CHANNEL_IDS);
  const channels = CHANNEL_IDS.map((id) => {
    const hit = found.get(id) || null;
    const def = defs[id] || {};
    const configured = uniqueModels(Array.isArray(def.models) ? def.models : []);
    // config.json 里的列表是人工确认过的顺序，优先用；没配置才退回供应商 env 里的 *MODEL 变量
    const models = configured.length
      ? configured
      : uniqueModels(hit ? modelsFromProviderConfig(hit.row) : []);
    return {
      id,
      label: String(def.label || id),
      provider: hit?.provider || '',
      providerId: hit?.providerId || '',
      available: Boolean(hit),
      models,
      visionModels: models.filter((m) => /vision|vl|omni/i.test(m)),
      // 只回末 4 位：客户端要能让用户确认「当前是哪把 key」，又不能拿到完整令牌。
      // 极短的令牌整串都可能是有效凭据，干脆全遮。
      tokenHint: hit ? (hit.token.length > 8 ? hit.token.slice(-4) : '****') : '',
    };
  });
  // 只把真正能用的通道交给客户端。没装 cc-switch、或者预设的两个供应商都没有凭据时
  // 返回空列表，客户端据此隐藏通道切换入口，退回「单上游 + config.json 的 models」模式。
  // 这样默认配置就是能跑的，不需要先去配供应商。
  const usable = channels.filter((c) => c.available);
  const wanted = normalizeChannelId(config.defaultChannel);
  const defaultChannel = usable.some((c) => c.id === wanted)
    ? wanted
    : usable[0]?.id || wanted;
  return { defaultChannel, channels: usable };
}

/**
 * 某通道的直连 env，拿不到返回 null（调用方回退到进程环境里的默认上游）。
 * 每次调用都重新读库：cc-switch 里换 key / 删供应商要能立刻生效。
 */
export function channelEnv(id) {
  if (!CHANNEL_IDS.includes(id)) return null;
  const hit = resolveChannels([id]).get(id);
  if (!hit) return null;
  return { ANTHROPIC_BASE_URL: hit.base, ANTHROPIC_AUTH_TOKEN: hit.token };
}
