/**
 * 读取 cc-switch 的当前状态，供 /api/models 和 bootstrap 使用。
 *
 * 手机端跟随桌面端（app_type='claude-desktop'）的路由方案：
 * - 当前供应商在 live-state.json 的 apps['claude-desktop'].proxy_route
 * - 模型映射在 provider.meta.claudeDesktopModelRoutes（claude 槽位 → 上游真实模型）
 * - 代理的 /claude-desktop 路径要 gateway token（cc-switch settings 表），否则 401
 * 桌面端没有路由表时，退回原来的 claude 路由候选集。
 */
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { createRequire } from 'node:module';

const require = createRequire(import.meta.url);

const HOME = os.homedir();
const LIVE_STATE = path.join(HOME, '.cc-switch', 'live-state.json');
const DB_PATH = path.join(HOME, '.cc-switch', 'cc-switch.db');
const SETTINGS = path.join(HOME, '.claude', 'settings.json');
const DESKTOP_APP = 'claude-desktop';
const DESKTOP_PATH = '/claude-desktop';
const PROXY_FALLBACK = 'http://127.0.0.1:15721';

function readJson(file) {
  try {
    return JSON.parse(fs.readFileSync(file, 'utf8'));
  } catch {
    return null;
  }
}

function mtimeMs(file) {
  try {
    return fs.statSync(file).mtimeMs;
  } catch {
    return 0;
  }
}

function num(value) {
  const n = Number(value);
  return Number.isFinite(n) ? n : 0;
}

let sqlite = null;
let sqliteChecked = false;

/** 只读句柄；调用方负责 close（lib/channels.js 也用它读 providers 表） */
export function openDb() {
  if (!sqliteChecked) {
    sqliteChecked = true;
    try {
      // node:sqlite 需要 Node >= 22.5；系统 Node 20 上会抛错，走降级路径
      sqlite = require('node:sqlite');
    } catch {
      sqlite = null;
    }
  }
  if (!sqlite) return null;
  try {
    return new sqlite.DatabaseSync(DB_PATH, { readOnly: true });
  } catch {
    return null;
  }
}

function parseMeta(provider) {
  try {
    return JSON.parse(provider?.meta || '{}') || {};
  } catch {
    return {};
  }
}

function desktopRoutes(provider) {
  const table = parseMeta(provider).claudeDesktopModelRoutes || {};
  return Object.entries(table).map(([slug, value]) => ({
    slug,
    model: String(value?.model || slug),
    label: String(value?.labelOverride || value?.model || slug),
    supports1m: Boolean(value?.supports1m),
  }));
}

function gatewayToken(db) {
  if (!db) return '';
  try {
    return db.prepare("select value from settings where key='claude_desktop_gateway_token'").get()?.value || '';
  } catch {
    return '';
  }
}

function desktopState(db, live) {
  const app = live?.apps?.[DESKTOP_APP];
  const route = app?.proxy_route || '';
  if (!route || !db) return null;
  let provider = null;
  try {
    provider = db
      .prepare("select id, name, meta from providers where app_type='claude-desktop' and id=?")
      .get(route);
  } catch {
    provider = null;
  }
  if (!provider) return null;
  const routes = desktopRoutes(provider);
  if (!routes.length) return null;
  return {
    provider,
    providerId: route,
    mode: app?.mode || 'unknown',
    routes,
    token: gatewayToken(db),
  };
}

function proxyOrigin(settingsEnv) {
  try {
    return new URL(String(settingsEnv?.ANTHROPIC_BASE_URL || PROXY_FALLBACK)).origin;
  } catch {
    return PROXY_FALLBACK;
  }
}

function desktopBaseUrl(settingsEnv) {
  return `${proxyOrigin(settingsEnv)}${DESKTOP_PATH}`;
}

/** 供应商 settings_config 里所有 *MODEL 变量的值；通道没配 models 时用它兜底 */
export function modelsFromProviderConfig(provider) {
  if (!provider?.settings_config) return [];
  try {
    const cfg = JSON.parse(provider.settings_config);
    const env = cfg.env || {};
    const out = [];
    for (const [key, value] of Object.entries(env)) {
      if (key.includes('MODEL') && typeof value === 'string' && value.trim()) out.push(value.trim());
    }
    return out;
  } catch {
    return [];
  }
}

function recentModels(db, providerId, days = 7) {
  if (!db || !providerId) return [];
  try {
    const since = Math.floor(Date.now() / 1000) - days * 86400;
    const rows = db
      .prepare(
        `select model, count(*) n from proxy_request_logs
         where app_type='claude' and provider_id=? and created_at>=?
           and model is not null and model != '' and (status_code between 200 and 299)
         group by model order by n desc limit 20`,
      )
      .all(providerId, since);
    return rows.map((r) => r.model);
  } catch {
    return [];
  }
}

/**
 * 手机端要走的桌面端上游：/claude-desktop 代理 + gateway token。
 * 桌面端没有路由表（例如官方供应商）时返回 null，由调用方沿用 settings.json。
 */
export function desktopUpstreamEnv() {
  const db = openDb();
  try {
    const desktop = desktopState(db, readJson(LIVE_STATE));
    if (!desktop?.token) return null;
    const settings = readJson(SETTINGS) || {};
    return {
      baseUrl: desktopBaseUrl(settings.env || {}),
      authToken: desktop.token,
      provider: desktop.provider?.name || '',
      providerId: desktop.providerId,
      routes: desktop.routes,
    };
  } finally {
    try {
      db?.close();
    } catch {
      /* ignore */
    }
  }
}

// ---------------------------------------------------------------- 用量统计

function windowStats(db, since) {
  const row =
    db
      .prepare(
        `select count(*) requests,
                sum(case when status_code between 200 and 299 then 1 else 0 end) success,
                sum(input_tokens) input,
                sum(output_tokens) output,
                sum(cache_read_tokens) cacheRead,
                sum(cache_creation_tokens) cacheCreation,
                sum(cast(total_cost_usd as real)) costUsd
           from proxy_request_logs
          where created_at >= ?`,
      )
      .get(since) || {};
  const input = num(row.input);
  const output = num(row.output);
  const cacheRead = num(row.cacheRead);
  const cacheCreation = num(row.cacheCreation);
  return {
    requests: num(row.requests),
    success: num(row.success),
    input,
    output,
    cacheRead,
    cacheCreation,
    totalTokens: input + output + cacheRead + cacheCreation,
    costUsd: num(row.costUsd),
  };
}

/** 当日 / 近 7 天 / 近 30 天的请求、token 与花费，外加 30 天每日曲线 */
export function readUsage() {
  const now = new Date();
  const startToday = new Date(now);
  startToday.setHours(0, 0, 0, 0);
  const nowSec = Math.floor(now.getTime() / 1000);
  const sinceMonth = nowSec - 30 * 86400;

  const db = openDb();
  if (!db) {
    return {
      available: false,
      generatedAt: Date.now(),
      currencyRate: 7.2,
      error: 'node:sqlite 不可用，请用 Node 22.5 以上启动',
      windows: {},
      daily: [],
      topModels: [],
    };
  }
  try {
    const daily = db
      .prepare(
        `select date(datetime(created_at, 'unixepoch', 'localtime')) date,
                count(*) requests,
                sum(input_tokens + output_tokens + cache_read_tokens + cache_creation_tokens) tokens,
                sum(cast(total_cost_usd as real)) costUsd
           from proxy_request_logs
          where created_at >= ?
          group by date
          order by date`,
      )
      .all(sinceMonth);
    const topModels = db
      .prepare(
        `select model, count(*) requests,
                sum(input_tokens + output_tokens + cache_read_tokens + cache_creation_tokens) tokens,
                sum(cast(total_cost_usd as real)) costUsd
           from proxy_request_logs
          where created_at >= ?
          group by model
          order by tokens desc
          limit 6`,
      )
      .all(nowSec - 7 * 86400);

    return {
      available: true,
      generatedAt: Date.now(),
      currencyRate: 7.2,
      windows: {
        today: { label: '今日', ...windowStats(db, Math.floor(startToday.getTime() / 1000)) },
        week: { label: '近 7 天', ...windowStats(db, nowSec - 7 * 86400) },
        month: { label: '近 30 天', ...windowStats(db, sinceMonth) },
      },
      daily: daily.map((d) => ({
        date: String(d.date || ''),
        requests: num(d.requests),
        tokens: num(d.tokens),
        costUsd: num(d.costUsd),
      })),
      topModels: topModels.map((m) => ({
        model: String(m.model || '未知模型'),
        requests: num(m.requests),
        tokens: num(m.tokens),
        costUsd: num(m.costUsd),
      })),
    };
  } catch (err) {
    return {
      available: false,
      generatedAt: Date.now(),
      currencyRate: 7.2,
      error: String(err?.message || err),
      windows: {},
      daily: [],
      topModels: [],
    };
  } finally {
    try {
      db.close();
    } catch {
      /* ignore */
    }
  }
}

/** 单个 Claude Code 会话的累计用量；用于打开历史对话时还原 token 总数和上下文 */
export function sessionUsage(sessionId) {
  if (!sessionId) return null;
  const db = openDb();
  if (!db) return null;
  try {
    const row =
      db
        .prepare(
          `select count(*) requests,
                  sum(input_tokens) input,
                  sum(output_tokens) output,
                  sum(cache_read_tokens) cacheRead,
                  sum(cache_creation_tokens) cacheCreation,
                  sum(cast(total_cost_usd as real)) costUsd
             from proxy_request_logs
            where session_id = ?`,
        )
        .get(sessionId) || {};
    const latest = db
      .prepare(
        `select input_tokens, cache_read_tokens, cache_creation_tokens, model, created_at, rowid as rid
           from proxy_request_logs
          where session_id = ?
          order by created_at desc, rowid desc
          limit 50`,
      )
      .all(sessionId);
    const ctxOf = (r) =>
      num(r.input_tokens) + num(r.cache_read_tokens) + num(r.cache_creation_tokens);
    // 同一个 session_id 下混着子代理和标题生成这类小请求，它们的上下文只有几千，
    // 直接取最新一行会让「上下文」忽大忽小（12 万 → 1 万）。这里只在最近的
    // "满上下文"请求里取最新的那条：先算这 50 行的最大值，再取第一条不小于一半的。
    const maxCtx = latest.reduce((m, r) => Math.max(m, ctxOf(r)), 0);
    const mainRow = latest.find((r) => ctxOf(r) * 2 >= maxCtx) || latest[0] || null;
    const input = num(row.input);
    const output = num(row.output);
    const cacheRead = num(row.cacheRead);
    const cacheCreation = num(row.cacheCreation);
    return {
      requests: num(row.requests),
      input,
      output,
      cacheRead,
      cacheCreation,
      totalTokens: input + output + cacheRead + cacheCreation,
      contextTokens: mainRow ? ctxOf(mainRow) : 0,
      lastModel: String(mainRow?.model || ''),
      costUsd: num(row.costUsd),
    };
  } catch {
    return null;
  } finally {
    try {
      db.close();
    } catch {
      /* ignore */
    }
  }
}

function desktopProviderState(desktop, env) {
  const seen = new Set();
  const models = [];
  const modelLabels = {};
  const modelRoutes = {};
  const visionModels = [];
  for (const route of desktop.routes) {
    // 槽位 → 上游真实模型的对照表始终全量返回，
    // 客户端要用它把 claude-* 槽位名换算成实际调用的第三方模型名。
    // supports1m 让手机端知道上下文窗口是 200k 还是 1M。
    modelRoutes[route.slug] = {
      model: route.model,
      label: route.label,
      supports1m: Boolean(route.supports1m),
    };
    // 多个槽位可能映射到同一个上游模型（DeepSeek 就是四个槽位全指向 deepseek-flash），
    // 只保留第一条，避免下拉框里出现四行同样的名字。
    if (seen.has(route.model)) continue;
    seen.add(route.model);
    models.push(route.slug);
    modelLabels[route.slug] = route.label;
    if (/vision|vl|omni/i.test(route.model)) visionModels.push(route.slug);
  }
  return {
    provider: desktop.provider?.name || '(未知供应商)',
    providerId: desktop.providerId,
    mode: desktop.mode,
    source: DESKTOP_APP,
    upstream: desktopBaseUrl(env),
    activeModel: env.ANTHROPIC_MODEL || '',
    models,
    modelLabels,
    modelRoutes,
    visionModels,
    revision: `${desktop.providerId}:${mtimeMs(SETTINGS)}:${mtimeMs(LIVE_STATE)}`,
    sqliteAvailable: Boolean(sqlite),
  };
}

/**
 * @param {string[]} fallbackModels config.json 里的 models，作为兜底
 */
export function readProviderState(fallbackModels = []) {
  const live = readJson(LIVE_STATE);
  const settings = readJson(SETTINGS) || {};
  const env = settings.env || {};
  const db = openDb();
  try {
    const desktop = desktopState(db, live);
    if (desktop) return desktopProviderState(desktop, env);

    const route = live?.apps?.claude?.proxy_route || '';
    const mode = live?.apps?.claude?.mode || 'unknown';

    let provider = null;
    let providerId = '';
    if (db) {
      try {
        const rows = db
          .prepare("select id, name, is_current, settings_config from providers where app_type='claude'")
          .all();
        provider =
          rows.find((r) => r.id === route) ||
          rows.find((r) => r.is_current === 1) ||
          rows[0] ||
          null;
        providerId = provider?.id || route;
      } catch {
        provider = null;
      }
    }

    const activeModel = env.ANTHROPIC_MODEL || '';
    const fromConfig = modelsFromProviderConfig(provider);
    const fromLogs = recentModels(db, providerId);

    const models = [...new Set([activeModel, ...fromConfig, ...fromLogs, ...fallbackModels].filter(Boolean))];
    const visionModels = models.filter((m) => /vision|vl|omni/i.test(m));

    return {
      provider: provider?.name || '(未知供应商)',
      providerId,
      mode,
      source: 'claude',
      upstream: env.ANTHROPIC_BASE_URL || '',
      activeModel,
      models,
      modelLabels: {},
      modelRoutes: {},
      visionModels,
      revision: `${route}:${mtimeMs(SETTINGS)}:${mtimeMs(LIVE_STATE)}`,
      sqliteAvailable: Boolean(sqlite),
    };
  } finally {
    try {
      db?.close();
    } catch {
      /* ignore */
    }
  }
}
