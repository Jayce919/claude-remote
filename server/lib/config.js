import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import crypto from 'node:crypto';
import { desktopUpstreamEnv } from './ccswitch.js';
import { channelEnv, CHANNEL_IDS } from './channels.js';

const SETTINGS_FILE = path.join(os.homedir(), '.claude', 'settings.json');
const LIVE_STATE_FILE = path.join(os.homedir(), '.cc-switch', 'live-state.json');

function fileMtime(file) {
  try {
    return fs.statSync(file).mtimeMs;
  } catch {
    return 0;
  }
}

const DEFAULTS = {
  host: '127.0.0.1',
  // 备用监听地址：填 Tailscale IP（如 100.x.y.z）就会在同一个端口上再开一个
  // 只对 tailnet 可见的入口。MagicDNS 名失效时手机仍能直连，留空则关闭。
  altHost: '',
  port: 8787,
  token: '',
  claudePath: path.join(os.homedir(), '.local', 'bin', 'claude.exe'),
  defaultCwd: os.homedir(),
  defaultModel: '',
  defaultEffort: '',
  defaultPermissionMode: 'auto',
  /** 会话级通道：新会话默认走哪个直连上游（deepseek / opencode） */
  defaultChannel: 'deepseek',
  // 内置的两条通道预设。只有 cc-switch 里确实存在对应供应商、且能读到凭据时才会
  // 出现在接口里；否则服务端会把它们过滤掉，客户端不显示通道切换入口。
  channels: {
    deepseek: {
      label: 'DeepSeek 官方',
      models: ['deepseek-flash', 'deepseek-v4-pro', 'deepseek-chat', 'deepseek-v4-flash-vision-exp'],
    },
    opencode: {
      label: 'OpenCode 订阅',
      models: ['claude-opus-5', 'claude-sonnet-5', 'claude-haiku-5-5', 'minimax-m3', 'deepseek-v4.1-flash'],
    },
  },
  models: [
    'deepseek-v4-pro',
    'deepseek-v4-flash-vision-exp',
    'deepseek-flash',
    'deepseek-chat',
    'mimo-v2-pro',
    'claude-sonnet',
    'claude-sonnet-5',
    'claude-opus-5',
    'claude-haiku-5-5',
  ],
  efforts: ['low', 'medium', 'high', 'xhigh', 'max'],
  permissionModes: ['auto', 'default', 'acceptEdits', 'plan', 'dontAsk'],
  // 经 tailscale serve 访问时，这些 tailnet 登录名免令牌直接进（留空则只能用令牌）
  allowedLogins: [],
  idleTimeoutMs: 30 * 60 * 1000,
};

export function loadConfig(root) {
  const file = path.join(root, 'config.json');
  let user = {};
  if (fs.existsSync(file)) {
    try {
      user = JSON.parse(fs.readFileSync(file, 'utf8'));
    } catch {
      user = {};
    }
  }
  const config = { ...DEFAULTS, ...user };
  config.channels = config.channels && typeof config.channels === 'object' ? config.channels : {};
  // 配置写错通道名时不能让服务起不来：和会话里的旧字段一样回落 deepseek
  if (!CHANNEL_IDS.includes(config.defaultChannel)) config.defaultChannel = 'deepseek';
  activeDefaultChannel = config.defaultChannel;
  if (!config.token) {
    config.token = crypto.randomBytes(24).toString('base64url');
    fs.writeFileSync(file, JSON.stringify(config, null, 2), 'utf8');
  }
  config.dataFile = path.join(root, 'data', 'conversations.json');
  config.root = root;
  return config;
}

/**
 * 当前配置的默认通道。applyUpstreamEnv 在进程启动和热刷新时都会用到，
 * 而调用方（conversation.js）手里只有 manager.config，所以在这里留一份。
 */
let activeDefaultChannel = 'deepseek';

/**
 * 把上游地址与令牌读进进程环境。
 *
 * 优先走「通道直连」：cc-switch 里 DeepSeek / OpenCode 自己的 base URL + 令牌，
 * 不经过代理，也不依赖桌面端开了哪个供应商。
 * 通道凭据缺失（数据库读不到、供应商被删）时才退回原来的方案：
 * 走代理的 /claude-desktop 路径，用 cc-switch 的 gateway token 鉴权，
 * 模型映射由代理按桌面端路由表完成；桌面端没有路由表时，
 * 退回 ~/.claude/settings.json 里的 claude 路由。
 *
 * 无论走哪条，都必须压过启动时继承的环境变量——从桌面端终端启动时父进程带着
 * 桌面端专用通道，令牌不对会返回 401。
 */
export function applyUpstreamEnv() {
  const direct = channelEnv(activeDefaultChannel);
  let desktop = null;
  let env = {};

  if (direct) {
    env = { ...direct };
  } else {
    try {
      env = JSON.parse(fs.readFileSync(SETTINGS_FILE, 'utf8')).env || {};
    } catch {
      env = {};
    }
    desktop = desktopUpstreamEnv();
    if (desktop) {
      env = { ...env, ANTHROPIC_BASE_URL: desktop.baseUrl, ANTHROPIC_AUTH_TOKEN: desktop.authToken };
    }
  }

  for (const key of ['ANTHROPIC_BASE_URL', 'ANTHROPIC_AUTH_TOKEN', 'ANTHROPIC_API_KEY']) {
    if (env[key]) process.env[key] = env[key];
  }
  // 直连和桌面端通道都只认 Bearer 令牌，残留的 API key 会让 SDK 改发 x-api-key
  if (direct || desktop) delete process.env.ANTHROPIC_API_KEY;

  for (const key of [
    'CLAUDE_CODE_PROVIDER_MANAGED_BY_HOST',
    'CLAUDE_CODE_HOST_AUTH_ENV_VAR',
    'CLAUDE_CODE_SESSION_ID',
    'CLAUDE_CODE_MESSAGING_SOCKET',
    'ANTHROPIC_MODEL',
  ]) {
    delete process.env[key];
  }
  process.env.CLAUDE_CODE_ENABLE_ASK_USER_QUESTION_TOOL = 'true';
  lastSettingsMtime = fileMtime(SETTINGS_FILE);
  lastLiveStateMtime = fileMtime(LIVE_STATE_FILE);
  return env;
}

let lastSettingsMtime = -1;
let lastLiveStateMtime = -1;

/**
 * cc-switch 里换供应商会改 live-state.json（claude 路由还可能重写 settings.json）。
 * 每次建立新的 SDK query 前调用一次：任一变过就重新应用环境变量，
 * 这样不用重启本服务也能跟上桌面端的切换。
 */
export function refreshUpstreamIfChanged() {
  const settingsMtime = fileMtime(SETTINGS_FILE);
  const liveMtime = fileMtime(LIVE_STATE_FILE);
  if (settingsMtime === lastSettingsMtime && liveMtime === lastLiveStateMtime) return false;
  applyUpstreamEnv();
  return true;
}
