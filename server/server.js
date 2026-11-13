import http from 'node:http';
import fs from 'node:fs';
import net from 'node:net';
import os from 'node:os';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadConfig, applyUpstreamEnv } from './lib/config.js';
import { Store } from './lib/store.js';
import { Manager } from './lib/manager.js';
import { readProviderState, readUsage, sessionUsage } from './lib/ccswitch.js';
import { readChannels, channelEnv } from './lib/channels.js';

const root = path.dirname(fileURLToPath(import.meta.url));
const config = loadConfig(root);
const upstream = applyUpstreamEnv();
if (process.env.CM_DEBUG) {
  for (const key of ['ANTHROPIC_BASE_URL', 'ANTHROPIC_AUTH_TOKEN', 'ANTHROPIC_API_KEY', 'CLAUDE_CODE_HOST_AUTH_ENV_VAR', 'CLAUDE_CODE_PROVIDER_MANAGED_BY_HOST']) {
    const value = process.env[key];
    console.error(`[debug] env ${key} = ${value === undefined ? '(undefined)' : JSON.stringify(value)}`);
  }
}
const store = new Store(config.dataFile);
const manager = new Manager({ config, store });
const publicDir = path.join(root, 'public');
const uploadsDir = path.join(config.root, 'uploads');
const UPLOAD_MAX_AGE_MS = 24 * 60 * 60 * 1000;

// 手机上传的图片只供当次对话使用，过期的留着只占磁盘，定期清掉
function sweepUploads() {
  try {
    const cutoff = Date.now() - UPLOAD_MAX_AGE_MS;
    for (const name of fs.readdirSync(uploadsDir)) {
      const file = path.join(uploadsDir, name);
      try {
        const stat = fs.statSync(file);
        if (stat.isFile() && stat.mtimeMs < cutoff) {
          fs.rmSync(file, { force: true });
          console.log(`[uploads] 清理过期文件：${name}`);
        }
      } catch {
        /* 单个文件读/删失败就跳过 */
      }
    }
  } catch {
    /* uploads 目录不存在时忽略 */
  }
}

const MIME = {
  '.html': 'text/html; charset=utf-8',
  '.js': 'text/javascript; charset=utf-8',
  '.css': 'text/css; charset=utf-8',
  '.json': 'application/json; charset=utf-8',
  '.webmanifest': 'application/manifest+json; charset=utf-8',
  '.svg': 'image/svg+xml',
  '.png': 'image/png',
  '.ico': 'image/x-icon',
};

function parseCookies(header = '') {
  const out = {};
  for (const part of header.split(';')) {
    const index = part.indexOf('=');
    if (index === -1) continue;
    out[part.slice(0, index).trim()] = decodeURIComponent(part.slice(index + 1).trim());
  }
  return out;
}

// 经 tailscale serve 进来的请求会带 Tailscale-User-Login；
// serve 会把外部伪造的同名头剥掉，所以只可能是真身份。
const seenLogins = new Set();

function tailnetLogin(req) {
  const login = req.headers['tailscale-user-login'];
  return typeof login === 'string' && login ? login : '';
}

function authorized(req) {
  const cookies = parseCookies(req.headers.cookie || '');
  if (cookies.cm_auth && cookies.cm_auth === config.token) return true;
  const header = req.headers.authorization || '';
  if (header.startsWith('Bearer ') && header.slice(7) === config.token) return true;

  const login = tailnetLogin(req);
  if (!login) return false;
  const allowed = Array.isArray(config.allowedLogins) && config.allowedLogins.includes(login);
  if (!seenLogins.has(login)) {
    seenLogins.add(login);
    console.log(`[auth] ${allowed ? '放行' : '拒绝'} tailnet 身份：${login}`);
  }
  return allowed;
}

function sendJson(res, status, body) {
  const payload = JSON.stringify(body);
  res.writeHead(status, {
    'Content-Type': 'application/json; charset=utf-8',
    'Content-Length': Buffer.byteLength(payload),
    'Cache-Control': 'no-store',
  });
  res.end(payload);
}

/**
 * 直连通道的模型视图。字段与 readProviderState 保持一致，老客户端不用改；
 * 但 modelRoutes 恒为空——通道没有 claude-* 槽位映射，模型名就是上游真实模型名。
 */
function channelModelState(channelId) {
  const data = readChannels(config);
  const channel = data.channels.find((item) => item.id === channelId);
  if (!channel) return null;
  const env = channelEnv(channelId);
  const models = channel.models || [];
  return {
    provider: channel.provider || channel.label,
    providerId: channel.providerId,
    mode: 'direct',
    source: 'channel',
    upstream: env?.ANTHROPIC_BASE_URL || '',
    // 默认模型在新通道里不存在时用第一个，和 /api/models 原有的取值思路一致
    activeModel: models.includes(config.defaultModel) ? config.defaultModel : models[0] || '',
    models,
    modelLabels: {},
    modelRoutes: {},
    visionModels: channel.visionModels || [],
    revision: `channel:${channelId}:${channel.providerId}`,
    channel: channelId,
    available: channel.available,
  };
}

/**
 * cc-switch 只记录经它代理的请求；直连通道的请求不进日志，sessionUsage 只能返回 null。
 * 这时用会话里记下的最后一次 usage 事件还原上下文，字段名与 sessionUsage 对齐，
 * 客户端拿到的 usage 结构不变。
 */
function localUsage(meta) {
  const last = meta?.lastUsage;
  if (!last) return null;
  return {
    requests: 0,
    input: 0,
    output: 0,
    cacheRead: 0,
    cacheCreation: 0,
    totalTokens: 0,
    contextTokens: Number(last.contextTokens) || 0,
    lastModel: String(meta.model || ''),
    costUsd: 0,
    source: 'local',
  };
}

function readBody(req) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let size = 0;
    req.on('data', (chunk) => {
      size += chunk.length;
      if (size > 64 * 1024 * 1024) {
        reject(new Error('请求体过大'));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      const raw = Buffer.concat(chunks).toString('utf8');
      if (!raw) return resolve({});
      try {
        resolve(JSON.parse(raw));
      } catch (err) {
        reject(new Error('JSON 解析失败'));
      }
    });
    // 手机锁屏、切网、Tailscale 重连都会让请求半路消失，
    // Node 会用 aborted / ECONNRESET 销毁请求对象，这里要接住而不是抛出去。
    req.on('aborted', () => reject(Object.assign(new Error('aborted'), { code: 'ECONNRESET' })));
    req.on('error', reject);
  });
}

function serveStatic(req, res, pathname) {
  const rel = pathname === '/' ? '/index.html' : pathname;
  const file = path.join(publicDir, path.normalize(rel).replace(/^([/\\])+/, ''));
  if (!file.startsWith(publicDir)) {
    res.writeHead(403).end('forbidden');
    return;
  }
  fs.stat(file, (err, stat) => {
    if (err || !stat.isFile()) {
      res.writeHead(404, { 'Content-Type': 'text/plain; charset=utf-8' }).end('not found');
      return;
    }
    res.writeHead(200, {
      'Content-Type': MIME[path.extname(file).toLowerCase()] || 'application/octet-stream',
      'Content-Length': stat.size,
      'Cache-Control': 'no-cache',
    });
    fs.createReadStream(file).pipe(res);
  });
}

// 客户端半路断开时 Node 报的错，不是服务端故障，不该把整个进程带走
const DISCONNECT_CODES = new Set(['ECONNRESET', 'EPIPE', 'ECONNABORTED', 'ERR_STREAM_DESTROYED']);

function isDisconnect(err) {
  if (!err) return false;
  if (DISCONNECT_CODES.has(err.code)) return true;
  return err.message === 'aborted';
}

function handleRequestError(err, res) {
  if (isDisconnect(err)) {
    console.error(`[request] 客户端已断开：${err.code || err.message}`);
    try {
      res.destroy();
    } catch {}
    return;
  }
  if (err?.message === '请求体过大') {
    try {
      sendJson(res, 413, { error: '请求体过大' });
    } catch {
      try {
        res.destroy();
      } catch {}
    }
    return;
  }
  console.error('[request] 处理失败', err);
  if (res.headersSent) {
    try {
      res.end();
    } catch {}
    return;
  }
  try {
    sendJson(res, 500, { error: String(err?.message || err) });
  } catch {
    try {
      res.destroy();
    } catch {}
  }
}

async function handleRequest(req, res) {
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  const { pathname } = url;

  if (req.method === 'GET' && pathname === '/' && url.searchParams.get('token')) {
    if (url.searchParams.get('token') === config.token) {
      res.writeHead(302, {
        'Set-Cookie': `cm_auth=${encodeURIComponent(config.token)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=31536000`,
        Location: '/',
      });
      res.end();
      return;
    }
    res.writeHead(403, { 'Content-Type': 'text/plain; charset=utf-8' }).end('令牌无效');
    return;
  }

  if (pathname.startsWith('/api/')) {
    // 登录接口必须在鉴权检查之前，否则没有 cookie 时就进不来
    if (req.method === 'POST' && pathname === '/api/login') {
      const body = await readBody(req);
      if (body.token !== config.token) {
        sendJson(res, 403, { error: '令牌错误' });
        return;
      }
      res.writeHead(200, {
        'Content-Type': 'application/json; charset=utf-8',
        'Set-Cookie': `cm_auth=${encodeURIComponent(config.token)}; HttpOnly; SameSite=Strict; Path=/; Max-Age=31536000`,
      });
      res.end(JSON.stringify({ ok: true }));
      return;
    }

    if (!authorized(req)) {
      sendJson(res, 401, { error: '未授权' });
      return;
    }

    if (req.method === 'GET' && pathname === '/api/bootstrap') {
      // 模型列表跟随 cc-switch 桌面端路由表，实时读；读失败才用 config.json 兜底
      let live = null;
      try {
        live = readProviderState(config.models);
      } catch {
        live = null;
      }
      const models = live?.models?.length ? live.models : config.models;
      const defaultModel = models.includes(config.defaultModel) ? config.defaultModel : models[0] || '';
      // 双通道：客户端启动时就要知道有哪几条通道、默认走哪条
      let channelState = { defaultChannel: config.defaultChannel, channels: [] };
      try {
        channelState = readChannels(config);
      } catch {
        /* 数据库读不到时只给默认值，老字段照旧 */
      }
      sendJson(res, 200, {
        models,
        modelLabels: live?.modelLabels || {},
        modelRoutes: live?.modelRoutes || {},
        provider: live?.provider || '',
        modelsRevision: live?.revision || '',
        channels: channelState.channels,
        defaultChannel: channelState.defaultChannel,
        efforts: config.efforts,
        permissionModes: config.permissionModes,
        defaults: {
          cwd: config.defaultCwd,
          model: defaultModel,
          effort: config.defaultEffort,
          permissionMode: config.defaultPermissionMode,
        },
        conversations: manager.list(),
        workspaces: manager.knownWorkspaces(),
        upstream: upstream.ANTHROPIC_BASE_URL || '',
      });
      return;
    }

    if (req.method === 'GET' && pathname === '/api/events') {
      res.writeHead(200, {
        'Content-Type': 'text/event-stream; charset=utf-8',
        'Cache-Control': 'no-cache, no-transform',
        Connection: 'keep-alive',
        'X-Accel-Buffering': 'no',
      });
      res.write(`data: ${JSON.stringify({ t: 'conv-list', conversations: manager.list() })}\n\n`);
      manager.addClient(res);
      manager.replayPending();
      const keepAlive = setInterval(() => {
        try {
          res.write(': ping\n\n');
        } catch {
          clearInterval(keepAlive);
        }
      }, 25000);
      req.on('close', () => clearInterval(keepAlive));
      return;
    }

    if (req.method === 'GET' && pathname === '/api/channels') {
      // 通道列表含 available / models / tokenHint（只给末 4 位），不给完整令牌
      sendJson(res, 200, readChannels(config));
      return;
    }

    if (req.method === 'GET' && pathname === '/api/models') {
      // 带 channel 时给直连通道的视图；不带时沿用 cc-switch 桌面端路由，老客户端不受影响
      const channelId = url.searchParams.get('channel') || '';
      if (channelId) {
        try {
          const state = channelModelState(channelId);
          if (state) {
            sendJson(res, 200, state);
            return;
          }
        } catch {
          /* 通道读不出来时落到下面的老逻辑 */
        }
      }
      // 实时读 cc-switch 状态，不做缓存，保证切供应商后立刻能看到新列表
      try {
        sendJson(res, 200, readProviderState(config.models));
      } catch (err) {
        sendJson(res, 200, {
          provider: '(读取失败)',
          providerId: '',
          mode: 'unknown',
          source: '',
          upstream: upstream.ANTHROPIC_BASE_URL || '',
          activeModel: '',
          models: config.models,
          modelLabels: {},
          visionModels: [],
          revision: '',
          error: String(err?.message || err),
        });
      }
      return;
    }

    if (req.method === 'GET' && pathname === '/api/usage') {
      // 读 cc-switch 的请求日志，统计当日 / 7 天 / 30 天用量
      sendJson(res, 200, readUsage());
      return;
    }

    if (req.method === 'GET' && pathname === '/api/conversation') {
      const conversation = manager.get(url.searchParams.get('id'));
      if (!conversation) {
        sendJson(res, 404, { error: '对话不存在' });
        return;
      }
      // 历史会话的累计 token 与上下文从 cc-switch 日志还原；
      // 直连通道的请求不进日志：sessionUsage 要么返回 null，要么返回一条全零记录，
      // 这两种情况都改用会话里记下的 lastUsage 兜底。
      const logged = conversation.meta.sessionId ? sessionUsage(conversation.meta.sessionId) : null;
      const usage = logged && logged.requests > 0 ? logged : localUsage(conversation.meta) || logged;
      sendJson(res, 200, { meta: conversation.meta, messages: conversation.messages, usage });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/conversations') {
      const body = await readBody(req);
      const conversation = manager.create(body);
      sendJson(res, 200, { conversation: conversation.summary() });
      return;
    }

    if (req.method === 'DELETE' && pathname.startsWith('/api/conversations/')) {
      const id = decodeURIComponent(pathname.slice('/api/conversations/'.length));
      const ok = await manager.remove(id);
      sendJson(res, ok ? 200 : 404, { ok });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/rename') {
      const body = await readBody(req);
      const conversation = manager.get(body.conversationId);
      if (!conversation) {
        sendJson(res, 404, { error: '对话不存在' });
        return;
      }
      const summary = conversation.rename(body.title);
      if (!summary) {
        sendJson(res, 400, { error: '标题为空' });
        return;
      }
      sendJson(res, 200, { ok: true, conversation: summary });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/pin') {
      const body = await readBody(req);
      const conversation = manager.get(body.conversationId);
      if (!conversation) {
        sendJson(res, 404, { error: '对话不存在' });
        return;
      }
      sendJson(res, 200, { ok: true, conversation: conversation.setPinned(Boolean(body.pinned)) });
      return;
    }

    if (req.method === 'GET' && pathname === '/api/search') {
      sendJson(res, 200, { conversations: manager.search(url.searchParams.get('q') || '') });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/upload') {
      const body = await readBody(req);
      const raw = path.basename(String(body.name || 'file'));
      const safe = raw.replace(/[^\w.\-]/g, '_').slice(-80) || 'file';
      fs.mkdirSync(uploadsDir, { recursive: true });
      const file = path.join(uploadsDir, `${Date.now()}-${safe}`);
      fs.writeFileSync(file, Buffer.from(body.data || '', 'base64'));
      sendJson(res, 200, { path: file, name: safe });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/send') {
      const body = await readBody(req);
      const conversation = manager.get(body.conversationId);
      if (!conversation) {
        sendJson(res, 404, { error: '对话不存在' });
        return;
      }
      await conversation.send({
        id: body.id,
        text: body.text || '',
        displayText: body.displayText,
        images: Array.isArray(body.images) ? body.images : [],
        model: body.model,
        effort: body.effort,
        permissionMode: body.permissionMode,
        cwd: body.cwd,
        channel: body.channel,
      });
      sendJson(res, 200, { ok: true });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/control') {
      const body = await readBody(req);
      const conversation = manager.get(body.conversationId);
      if (!conversation) {
        sendJson(res, 404, { error: '对话不存在' });
        return;
      }
      let answers = body.answers;
      if (Array.isArray(answers)) {
        answers = Object.fromEntries(answers.map((a) => [a.question, a.answer]));
      }
      const ok = await conversation.control({
        toolUseID: body.toolUseID,
        action: body.action,
        answers,
        message: body.message,
      });
      sendJson(res, ok ? 200 : 404, { ok });
      return;
    }

    if (req.method === 'POST' && pathname === '/api/stop') {
      const body = await readBody(req);
      const conversation = manager.get(body.conversationId);
      if (!conversation) {
        sendJson(res, 404, { error: '对话不存在' });
        return;
      }
      await conversation.stop();
      sendJson(res, 200, { ok: true });
      return;
    }

    sendJson(res, 404, { error: '接口不存在' });
    return;
  }

  serveStatic(req, res, pathname);
}

const handleIncoming = (req, res) => {
  // 请求/响应对象自身也要挂错误监听，否则 socket 被对端关掉时会抛成未捕获异常
  req.on('error', () => {});
  res.on('error', () => {});
  handleRequest(req, res).catch((err) => handleRequestError(err, res));
};

const server = http.createServer(handleIncoming);

/**
 * 端口被占用时顺延到下一个。
 * Windows 上 0.0.0.0 和 127.0.0.1 可以同时 bind 同一个端口而不报错，结果是两个进程
 * 分走流量——所以先探测再监听，避免「看起来起来了、连上的却是别的进程」。
 */
async function findFreePort(start, attempts = 10) {
  for (let i = 0; i < attempts; i += 1) {
    const port = start + i;
    const busy = await new Promise((resolve) => {
      const socket = net.connect({ host: '127.0.0.1', port });
      const finish = (value) => {
        socket.destroy();
        resolve(value);
      };
      socket.setTimeout(400, () => finish(false));
      socket.once('connect', () => finish(true));
      socket.once('error', () => finish(false));
    });
    if (!busy) return port;
  }
  return 0;
}

const listenPort = (await findFreePort(config.port)) || config.port;
if (listenPort !== config.port) {
  console.log(`  端口 ${config.port} 已被占用（可能已经开着一个实例），改用 ${listenPort}。`);
}

// 备用入口：绑定 Tailscale IP，域名解析失败时手机可以直连这个地址。
// 只监听 tailnet 网卡，不向局域网/公网暴露；令牌校验照旧。
if (config.altHost) {
  const altServer = http.createServer(handleIncoming);
  altServer.on('error', (err) => {
    console.warn(`  备用入口 ${config.altHost}:${listenPort} 未启动：${err.message}`);
  });
  altServer.listen(listenPort, config.altHost, () => {
    console.log(`  备用访问   http://${config.altHost}:${listenPort}/`);
  });
}

server.listen(listenPort, config.host, () => {
  const base = upstream.ANTHROPIC_BASE_URL || '(未配置)';
  console.log('');
  console.log('  Claude Code 手机端服务已启动');
  console.log(`  本机访问   http://127.0.0.1:${listenPort}/`);
  // 手机要填的地址：只列手机真能用的——局域网私有段和 tailnet。
  // 代理软件（Clash 之类）的 TUN 网卡会冒出一个 198.18.x.x 的假地址，
  // 手机打过去是不通的，所以按网段过滤，不按网卡名字猜。
  const addrs = Object.values(os.networkInterfaces())
    .flat()
    .filter((i) => i && i.family === 'IPv4' && !i.internal)
    .map((i) => i.address);
  const tailnet = addrs.filter((ip) => /^100\.(6[4-9]|[7-9]\d|1[0-1]\d|12[0-7])\./.test(ip));
  const lan = addrs.filter(
    (ip) => /^10\./.test(ip) || /^192\.168\./.test(ip) || /^172\.(1[6-9]|2\d|3[01])\./.test(ip),
  );
  for (const ip of lan) console.log(`  手机访问   http://${ip}:${listenPort}/   （手机和电脑连同一个 Wi-Fi）`);
  for (const ip of tailnet) console.log(`  手机访问   http://${ip}:${listenPort}/   （装了 Tailscale）`);
  if (!lan.length && !tailnet.length) {
    console.log('  手机访问   （没找到可用地址：检查网络，或把 config.json 的 host 改成 0.0.0.0）');
  }
  console.log(`  访问令牌   ${config.token}`);
  console.log(`  上游接口   ${base}`);
  console.log(`  工作目录   ${config.defaultCwd}`);
  console.log('');
});

// 兜底：服务没有守护进程，一个坏连接不该让整个手机端掉线。
// 这里只记录不退出；真出了代码级 bug，日志里能看到完整栈。
process.on('unhandledRejection', (reason) => {
  if (isDisconnect(reason)) {
    console.error(`[unhandled] 客户端已断开：${reason.code || reason.message}`);
    return;
  }
  console.error('[unhandled] 未处理的 Promise 拒绝', reason);
});

process.on('uncaughtException', (err) => {
  if (isDisconnect(err)) {
    console.error(`[uncaught] 客户端已断开：${err.code || err.message}`);
    return;
  }
  console.error('[uncaught] 未捕获异常', err);
});

for (const signal of ['SIGINT', 'SIGTERM']) {
  process.on(signal, () => {
    store.flush({ conversations: [...manager.conversations.values()].map((c) => c.meta) });
    server.close(() => process.exit(0));
    setTimeout(() => process.exit(0), 1500);
  });
}

sweepUploads();
setInterval(sweepUploads, 6 * 60 * 60 * 1000).unref();
