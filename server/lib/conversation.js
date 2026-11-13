import { query } from '@anthropic-ai/claude-agent-sdk';
import { randomUUID } from 'node:crypto';
import { refreshUpstreamIfChanged } from './config.js';
import { channelEnv, readChannels, CHANNEL_IDS } from './channels.js';
import { syncSessionTitle } from './desktopSync.js';

/** 把模型返回的标题压成一行：去掉代码围栏、前缀、引号和结尾标点 */
function cleanTitle(raw, max = 24) {
  let text = String(raw || '')
    .replace(/```[\s\S]*?```/g, '')
    .split('\n')
    .map((line) => line.trim())
    .filter(Boolean)[0] || '';
  text = text
    .replace(/^(标题|对话标题|title)\s*[:：]\s*/i, '')
    .replace(/^["'“”‘’「」『』《》]+/, '')
    .replace(/["'“”‘’「」『』《》]+$/, '')
    .replace(/[。！？!?；;，,、]+$/, '')
    .trim();
  const chars = Array.from(text);
  if (chars.length > max) text = chars.slice(0, max).join('').trim();
  return text;
}

/** 用户在手机上点了停止，或 hook 拦下导致的中止，都算「已停止」 */
function isAbortedReason(reason) {
  return (
    reason === 'aborted_streaming' ||
    reason === 'aborted_tools' ||
    reason === 'hook_stopped' ||
    reason === 'stop_hook_prevented'
  );
}

function normalizeToolContent(content) {
  if (typeof content === 'string') return content;
  if (Array.isArray(content)) {
    return content
      .map((c) => (typeof c === 'string' ? c : c?.type === 'text' ? c.text : JSON.stringify(c)))
      .join('\n');
  }
  if (content == null) return '';
  return JSON.stringify(content);
}

/**
 * 把本次会话要用的上游地址/令牌交给 SDK 的 flag-settings 覆盖层。
 * env 由调用方决定：通道直连时是该通道的凭据，回退时是进程环境里的默认上游。
 * 覆盖层优先级最高，压过用户级 settings.json 里的 env。
 */
function upstreamFlagSettings(env) {
  const baseUrl = env?.ANTHROPIC_BASE_URL;
  const token = env?.ANTHROPIC_AUTH_TOKEN;
  if (!baseUrl || !token) return undefined;
  return { env: { ANTHROPIC_BASE_URL: baseUrl, ANTHROPIC_AUTH_TOKEN: token } };
}

/**
 * 一个对话 = 一个常驻的 Claude Code 会话。
 * 通过 SDK 的流式输入模式，用户消息可以持续喂进去，模型有完整上下文。
 */
export class Conversation {
  constructor(meta, manager) {
    this.meta = meta;
    this.manager = manager;
    // 双通道上线前的旧会话没有 channel 字段，按 deepseek 处理并补写回去
    if (!CHANNEL_IDS.includes(this.meta.channel)) this.meta.channel = 'deepseek';
    this.messages = Array.isArray(meta.messages) ? meta.messages : [];
    this.events = [];
    this.queue = [];
    this.pending = new Map();
    this.open = [];
    this.handle = null;
    this.currentMsgId = null;
    this.needsResume = Boolean(meta.sessionId);
    this.waiter = null;
    this.titleRequested = false;
    /** 用户点过停止，等 result 回来时据此把消息标成「已停止」（老 CLI 可能不带 terminal_reason） */
    this.stopRequested = false;
    this.lastActivity = Date.now();
    /** 最近一次 API 调用的上下文大小（输入 + 缓存读取 + 缓存写入） */
    this.lastContextTokens = 0;
    /** 本条消息的起始时刻与首个内容增量的时刻，用来算真实 tok/s */
    this.msgStartedAt = 0;
    this.msgFirstTokenAt = 0;
    /** 已累计过用量的 assistant message id，避免重复计数 */
    this.usageSeen = new Set();
    /** 已出队但还没收到 result 的用户消息 id（按出队顺序）。助手回复据此插到对应位置 */
    this.turnUserIds = [];
  }

  get id() {
    return this.meta.id;
  }

  emit(ev) {
    ev.convId = this.meta.id;
    this.events.push(ev);
    if (this.events.length > 600) this.events.splice(0, this.events.length - 600);
    this.manager.broadcast(ev);
  }

  async send({ id, text = '', displayText, images = [], model, effort, permissionMode, cwd, channel }) {
    // 切通道要重启 CLI 进程，必须排在其它参数前面：setChannel 之后再加消息，
    // 新会话会带着新上游和（必要时）新模型 resume 原来的 sessionId。
    if (channel !== undefined && channel !== this.meta.channel) await this.setChannel(channel);
    if (model !== undefined && model !== this.meta.model) {
      this.meta.model = model;
      if (this.handle) await this.handle.setModel(model || undefined).catch(() => {});
    }
    if (permissionMode !== undefined && permissionMode !== this.meta.permissionMode) {
      this.meta.permissionMode = permissionMode;
      if (this.handle) await this.handle.setPermissionMode(permissionMode).catch(() => {});
    }
    if (effort !== undefined && effort !== this.meta.effort) {
      this.meta.effort = effort;
      if (this.handle) await this.handle.applyFlagSettings({ effortLevel: effort || null }).catch(() => {});
    }
    if (cwd && cwd !== this.meta.cwd) {
      this.meta.cwd = cwd;
      this.meta.sessionId = null;
      this.needsResume = false;
      await this.close();
    }

    // displayText 是界面上看到的原文；text 里可能还带着上传文件路径，只喂给模型
    const msg = {
      id: id || randomUUID(),
      role: 'user',
      text: typeof displayText === 'string' ? displayText : text,
      images,
      ts: Date.now(),
    };
    this.messages.push(msg);
    this.emit({ t: 'msg', msg });
    this.meta.updatedAt = Date.now();
    this.manager.save();

    const content = [{ type: 'text', text }];
    for (const img of images) {
      content.push({
        type: 'image',
        source: { type: 'base64', media_type: img.mediaType, data: img.data },
      });
    }
    this.queue.push({
      id: msg.id,
      type: 'user',
      message: { role: 'user', content },
    });
    await this.ensureQuery();
    this.kick();
  }

  async ensureQuery() {
    if (this.handle) {
      this.kick();
      return;
    }
    // 供应商可能在 cc-switch 里被换过，建会话前先对齐上游
    refreshUpstreamIfChanged();
    // 会话级通道优先：直连该通道自己的 base/令牌。通道被删或数据库读不到时，
    // refreshUpstreamIfChanged 刚写好的进程环境就是默认上游，正好当兜底。
    const direct = channelEnv(this.meta.channel);
    if (!direct && CHANNEL_IDS.includes(this.meta.channel)) {
      this.emit({ t: 'error', message: `通道 ${this.channelLabel()} 凭据不可用，已回退到默认上游` });
    }
    const upstream = direct || process.env;
    const self = this;
    const prompt = {
      async *[Symbol.asyncIterator]() {
        if (process.env.CM_DEBUG) console.error('[debug] prompt iterable opened');
        while (true) {
          if (!self.queue.length) {
            await new Promise((resolve) => {
              self.waiter = resolve;
            });
          }
          while (self.queue.length) {
            const item = self.queue.shift();
            if (process.env.CM_DEBUG) console.error('[debug] prompt yield user message');
            // 带上 userId，客户端就知道这条排队消息正式开始处理了
            self.emit({ t: 'status', state: 'running', userId: item.id });
            // 记下本轮由哪条用户消息触发；助手回复结束时插到它后面而不是末尾，
            // 打断/插入场景里用户的新消息可能先于被打断的回复进入 messages
            if (item.type === 'user') self.turnUserIds.push(item.id);
            yield { type: item.type, message: item.message };
          }
        }
      },
    };

    const options = {
      pathToClaudeCodeExecutable: this.manager.config.claudePath,
      cwd: this.meta.cwd,
      permissionMode: this.meta.permissionMode,
      includePartialMessages: true,
      // 用户级 ~/.claude/settings.json 的 env 优先级高于进程环境，会把
      // ANTHROPIC_BASE_URL 拽回 cc-switch 根路径（DeepSeek 通道，余额为 0 → 402）。
      // 用 flag-settings 覆盖层（优先级最高）把服务端算好的上游再钉一次，
      // 用户级设置（全局 CLAUDE.md 等）照常加载。
      settings: upstreamFlagSettings(upstream),
      canUseTool: (toolName, input, opts) => this.handleTool(toolName, input, opts),
    };
    if (this.meta.model) options.model = this.meta.model;
    if (this.meta.effort) options.effort = this.meta.effort;
    if (this.needsResume && this.meta.sessionId) options.resume = this.meta.sessionId;
    this.needsResume = false;

    this.handle = query({ prompt, options });
    this.emit({ t: 'status', state: 'running' });
    this.pump();
  }

  kick() {
    if (this.waiter) {
      const resolve = this.waiter;
      this.waiter = null;
      resolve();
    }
  }

  async pump() {
    // 记住自己消费的是哪个 handle：切通道或换目录时 close() 会把 this.handle 换掉，
    // 旧进程退出时不能再发 idle，否则手机端会以为新会话已经结束。
    const handle = this.handle;
    try {
      for await (const message of handle) this.onMessage(message);
    } catch (err) {
      this.emit({ t: 'error', message: String(err?.message || err) });
    } finally {
      if (this.handle === handle) this.emit({ t: 'status', state: 'idle' });
      this.manager.save();
    }
  }

  onMessage(message) {
    this.lastActivity = Date.now();
    if (process.env.CM_DEBUG) {
      const detail =
        message.type === 'system' ? `system/${message.subtype}`
        : message.type === 'stream_event' ? `stream/${message.event?.type}`
        : message.type;
      const extra =
        message.subtype === 'api_retry' || message.subtype === 'init'
          ? ' ' + JSON.stringify(message).slice(0, 400)
          : '';
      console.error(`[debug] ${this.meta.id.slice(0, 8)} <- ${detail}${extra}`);
    }
    switch (message.type) {
      case 'system':
        if (message.subtype === 'init') {
          if (message.session_id) this.meta.sessionId = message.session_id;
          this.syncDesktopTitle();
          this.emit({
            t: 'session',
            sessionId: message.session_id,
            model: message.model,
            cwd: message.cwd,
          });
          this.manager.save();
        }
        return;
      case 'stream_event':
        this.onStreamEvent(message.event, message.parent_tool_use_id);
        return;
      case 'assistant':
        this.onAssistant(message);
        return;
      case 'user':
        for (const block of message.message?.content || []) {
          if (block.type === 'tool_result') {
            this.emit({
              t: 'tool-result',
              toolUseId: block.tool_use_id,
              content: normalizeToolContent(block.content),
              isError: Boolean(block.is_error),
            });
            this.applyToolResult(block.tool_use_id, block);
          }
        }
        return;
      case 'result': {
        const msgId = this.currentMsgId;
        this.currentMsgId = null;
        // 本轮结束，弹出对应的用户消息；下一个 result 对应下一条
        this.turnUserIds.shift();
        const interrupted = this.stopRequested || isAbortedReason(message.terminal_reason);
        const errored = Boolean(message.is_error) || String(message.subtype || '').startsWith('error_');
        this.stopRequested = false;
        if (msgId) {
          const target = this.messages.find((m) => m.id === msgId);
          if (target) {
            if (errored) target.state = 'error';
            else if (interrupted) target.state = 'interrupted';
          }
        }
        this.emit({
          t: 'result',
          subtype: message.subtype,
          isError: message.is_error,
          durationMs: message.duration_ms,
          cost: message.total_cost_usd,
          numTurns: message.num_turns,
          result: message.result,
          terminalReason: message.terminal_reason || null,
          msgId: msgId || undefined,
          queued: this.queue.length > 0,
        });
        this.manager.save();
        void this.maybeGenerateTitle();
        return;
      }
      default:
    }
  }

  onStreamEvent(event, parentToolUseId) {
    if (!event) return;
    if (event.type === 'message_start') {
      // 子代理（Task）的请求上下文只有几千，拿它覆盖主对话的上下文会让占比乱跳
      if (parentToolUseId) return;
      // 每次 API 调用开始时上报当前上下文大小；input 与缓存三部分之和就是这次
      // 真正送进模型的提示词长度，客户端用它算上下文占比。
      const usage = event.message?.usage || {};
      this.lastContextTokens =
        (Number(usage.input_tokens) || 0) +
        (Number(usage.cache_read_input_tokens) || 0) +
        (Number(usage.cache_creation_input_tokens) || 0);
      // 记录本条消息的起止时间，用来算真实输出速度（tok/s）
      this.msgStartedAt = Date.now();
      this.msgFirstTokenAt = 0;
      // 直连通道的请求不进 cc-switch 日志，上下文大小只能靠自己记一份
      this.noteUsage({ contextTokens: this.lastContextTokens, turnOutput: 0, tps: 0 });
      this.emit({
        t: 'usage',
        contextTokens: this.lastContextTokens,
        deltaInput: 0,
        deltaOutput: 0,
        deltaCacheRead: 0,
        deltaCacheCreation: 0,
      });
      return;
    }
    if (event.type === 'message_delta') {
      // output_tokens 是这条消息最终生成的 token 数，assistant 消息里还会有一次，
      // 这里只用来更新「本轮输出」，不计入会话总量，避免重复。
      const output = Number(event.usage?.output_tokens);
      if (Number.isFinite(output)) {
        // 真实出字速度：从第一个内容增量到最后一次统计的耗时，
        // 不含排队和首字延迟，比客户端按字符估算准
        let tps = 0;
        const first = Number(this.msgFirstTokenAt) || 0;
        if (first && output > 0) {
          const decodeMs = Date.now() - first;
          if (decodeMs > 50) tps = Math.round((output / (decodeMs / 1000)) * 10) / 10;
        }
        this.emit({
          t: 'usage',
          contextTokens: this.lastContextTokens,
          deltaInput: 0,
          deltaOutput: 0,
          deltaCacheRead: 0,
          deltaCacheCreation: 0,
          turnOutput: output,
          tps,
        });
        this.noteUsage({ contextTokens: this.lastContextTokens, turnOutput: output, tps });
      }
      return;
    }
    if (event.type === 'content_block_start') {
      this.open.push({
        index: event.index,
        type: event.content_block?.type || 'text',
        text: '',
        json: '',
      });
      return;
    }
    if (event.type !== 'content_block_delta') return;
    // 第一个内容增量到达的时刻，作为出字速度的起点（不含排队与首字延迟）
    if (!this.msgFirstTokenAt) this.msgFirstTokenAt = Date.now();
    let block = this.open.find((b) => b.index === event.index);
    if (!block) {
      this.open.push({ index: event.index, type: 'text', text: '', json: '' });
      block = this.open[this.open.length - 1];
    }
    const delta = event.delta || {};
    if (delta.type === 'text_delta') {
      block.text += delta.text || '';
      this.emit({ t: 'delta', kind: 'text', text: delta.text || '' });
    } else if (delta.type === 'thinking_delta') {
      block.text += delta.thinking || '';
      this.emit({ t: 'delta', kind: 'thinking', text: delta.thinking || '' });
    } else if (delta.type === 'input_json_delta') {
      block.json += delta.partial_json || '';
    }
  }

  onAssistant(message) {
    this.trackUsage(message);
    for (const block of message.message?.content || []) {
      if (block.type === 'text') {
        this.pushBlock({ type: 'text', text: block.text });
      } else if (block.type === 'thinking') {
        const open = this.takeOpen('thinking');
        this.pushBlock({ type: 'thinking', text: block.thinking ?? open?.text ?? '' });
      } else if (block.type === 'tool_use') {
        this.pushBlock({
          type: 'tool',
          id: block.id,
          name: block.name,
          input: block.input,
          status: 'pending',
        });
      }
    }
  }

  /**
   * 把一次 API 调用的 usage 作为增量发给客户端。
   * 客户端累加后就是「本对话」的消耗；上下文大小单独由 lastContextTokens 表示。
   */
  trackUsage(message) {
    const usage = message?.message?.usage || message?.usage;
    if (!usage) return;
    const id = message?.message?.id || '';
    if (id && this.usageSeen.has(id)) return;
    if (id) {
      this.usageSeen.add(id);
      if (this.usageSeen.size > 4000) this.usageSeen.clear();
    }
    const deltaInput = Number(usage.input_tokens) || 0;
    const deltaOutput = Number(usage.output_tokens) || 0;
    const deltaCacheRead = Number(usage.cache_read_input_tokens) || 0;
    const deltaCacheCreation = Number(usage.cache_creation_input_tokens) || 0;
    if (!deltaInput && !deltaOutput && !deltaCacheRead && !deltaCacheCreation) return;
    this.emit({
      t: 'usage',
      contextTokens: this.lastContextTokens,
      deltaInput,
      deltaOutput,
      deltaCacheRead,
      deltaCacheCreation,
    });
  }

  /**
   * 记下最近一次 API 调用的用量快照，供重启后还原上下文。
   * 落盘走 manager.save 的 400ms 去抖，流式帧不会每次都写整份 data 文件。
   */
  noteUsage({ contextTokens, turnOutput, tps }) {
    this.meta.lastUsage = {
      contextTokens: Number(contextTokens) || 0,
      turnOutput: Number(turnOutput) || 0,
      tps: Number(tps) || 0,
      at: Date.now(),
    };
    this.manager.save();
  }

  takeOpen(type) {
    const index = this.open.findIndex((b) => b.type === type);
    if (index === -1) return null;
    return this.open.splice(index, 1)[0];
  }

  pushBlock(block) {
    const msgId = this.ensureAssistantMessage();
    const target = this.messages.find((m) => m.id === msgId);
    if (target) target.blocks.push(block);
    this.emit({ t: 'block', msgId, block });
  }

  ensureAssistantMessage() {
    if (!this.currentMsgId) {
      this.currentMsgId = randomUUID();
      const msg = { id: this.currentMsgId, role: 'assistant', ts: Date.now(), blocks: [] };
      // 插到它所回答的那条用户消息之后，而不是永远追加到末尾。
      // 打断/插入时，用户的新消息会先于被打断的助手回复进入 messages，
      // 直接 push 会把被打断的回复放到对话最后，顺序就乱了。
      const anchorId = this.turnUserIds[0];
      const anchorIndex = anchorId ? this.messages.findIndex((m) => m.id === anchorId) : -1;
      if (anchorIndex !== -1) this.messages.splice(anchorIndex + 1, 0, msg);
      else this.messages.push(msg);
      this.emit({ t: 'assistant-start', msgId: this.currentMsgId });
    }
    return this.currentMsgId;
  }

  /**
   * 首轮回复结束后生成短标题。
   *
   * 直接走上游的 Messages API，不占用当前会话上下文，也不改动 messages。
   * 失败时退回首条用户消息的压缩摘要，保证列表里不会永远显示「未命名对话」。
   */
  async maybeGenerateTitle() {
    if (this.meta.title || this.titleRequested) return;
    const firstUser = this.messages.find((m) => m.role === 'user' && String(m.text || '').trim());
    if (!firstUser) return;
    this.titleRequested = true;

    const fallback = () => {
      const title = cleanTitle(firstUser.text);
      if (title && !this.meta.title) this.applyTitle(Array.from(title).slice(0, 18).join('').trim());
    };

    refreshUpstreamIfChanged();
    // 标题请求也走本会话的通道：默认上游可能是另一家的 token，模型名对不上
    const direct = channelEnv(this.meta.channel);
    const base = String((direct?.ANTHROPIC_BASE_URL ?? process.env.ANTHROPIC_BASE_URL) || '').replace(/\/+$/, '');
    const token =
      direct?.ANTHROPIC_AUTH_TOKEN ?? process.env.ANTHROPIC_AUTH_TOKEN ?? process.env.ANTHROPIC_API_KEY ?? '';
    if (!base) {
      fallback();
      return;
    }

    const assistantText = this.messages
      .filter((m) => m.role === 'assistant')
      .flatMap((m) => (m.blocks || []).filter((b) => b.type === 'text').map((b) => b.text || ''))
      .join('\n')
      .trim();
    const prompt = [
      '请为下面这段对话生成一个简洁的标题。',
      '要求：',
      '- 6 到 14 个汉字，专有名词或代码名可以保留英文',
      '- 概括用户在做什么，不要照抄第一句话',
      '- 不加引号、书名号、句号、序号或「标题：」前缀',
      '- 只输出标题本身',
      '',
      `用户：${String(firstUser.text || '').slice(0, 800)}`,
      `助手：${assistantText.slice(0, 600) || '（暂无回复）'}`,
    ].join('\n');

    try {
      const res = await fetch(`${base}/v1/messages`, {
        method: 'POST',
        headers: {
          'content-type': 'application/json',
          'anthropic-version': '2023-06-01',
          authorization: `Bearer ${token}`,
        },
        body: JSON.stringify({
          model: this.meta.model || undefined,
          max_tokens: 320,
          messages: [{ role: 'user', content: prompt }],
        }),
        signal: AbortSignal.timeout(15000),
      });
      if (!res.ok) throw new Error(`HTTP ${res.status}`);
      const data = await res.json();
      const raw = (data.content || [])
        .filter((block) => block.type === 'text')
        .map((block) => block.text || '')
        .join(' ')
        .trim();
      const title = cleanTitle(raw);
      if (title) this.applyTitle(title);
      else fallback();
    } catch (err) {
      if (process.env.CM_DEBUG) console.error('[title] 生成失败：', err?.message || err);
      fallback();
    }
  }

  applyTitle(title) {
    if (!title || this.meta.title) return;
    this.meta.title = title;
    this.meta.updatedAt = Date.now();
    this.manager.save();
    this.manager.broadcast({ t: 'conv-list', conversations: this.manager.list() });
    this.syncDesktopTitle();
  }

  /** 手机端手动重命名：覆盖自动生成的标题，并同步回桌面端会话名 */
  rename(title) {
    const clean = cleanTitle(title, 60);
    if (!clean) return null;
    this.meta.title = clean;
    this.meta.updatedAt = Date.now();
    this.manager.save();
    this.manager.broadcast({ t: 'conv-list', conversations: this.manager.list() });
    this.syncDesktopTitle();
    return this.summary();
  }

  /** 置顶只影响列表排序，不改变对话内容 */
  setPinned(pinned) {
    this.meta.pinnedAt = pinned ? Date.now() : 0;
    this.manager.save();
    this.manager.broadcast({ t: 'conv-list', conversations: this.manager.list() });
    return this.summary();
  }

  /** 通道的中文名，用于错误提示 */
  channelLabel() {
    const def = this.manager.config.channels?.[this.meta.channel];
    return def?.label || this.meta.channel;
  }

  /**
   * 切换本会话的直连通道。
   *
   * CLI 进程的上游是启动时通过 flag-settings 写死的，不能热替换，
   * 所以要 interrupt + close 掉当前进程，等下一轮 send 用新通道的 env
   * 带着原来的 sessionId resume —— 对话上下文在 CLI 的会话文件里，不会丢。
   */
  async setChannel(channelId) {
    if (!CHANNEL_IDS.includes(channelId)) return false;
    if (channelId === this.meta.channel) return false;
    this.meta.channel = channelId;
    this.meta.updatedAt = Date.now();
    if (this.handle) {
      await this.handle.interrupt().catch(() => {});
      await this.close();
    }
    // 必须沿用原会话文件，否则切通道等于新开一个对话
    this.needsResume = true;

    const channel = readChannels(this.manager.config).channels.find((c) => c.id === channelId);
    const models = channel?.models || [];
    // 模型名是上游内部名，跨通道基本不通用（claude-opus-5 在 DeepSeek 不存在）；
    // 不在新通道列表里就落到第一个，手机端收到 meta 事件后同步下拉框
    if (models.length && !models.includes(this.meta.model)) {
      this.meta.model = models[0];
      this.emit({ t: 'meta', model: this.meta.model });
    }
    this.manager.save();
    return true;
  }

  /**
   * 把当前标题同步到桌面端：手机端和桌面端共用同一套 CLI 会话文件，
   * 写好标题后，桌面端的会话选择器里显示的就是正式标题而不是首句原文。
   */
  syncDesktopTitle() {
    if (!this.meta.sessionId || !this.meta.title) return;
    void syncSessionTitle(this.meta.sessionId, this.meta.title);
  }

  applyToolResult(toolUseId, block) {
    const content = normalizeToolContent(block.content);
    const isError = Boolean(block.is_error);
    for (const message of this.messages) {
      if (message.role !== 'assistant') continue;
      for (const item of message.blocks) {
        if (item.type === 'tool' && item.id === toolUseId) {
          item.result = content;
          item.isError = isError;
          item.status = isError ? 'error' : 'done';
        }
      }
    }
  }

  async handleTool(toolName, input, opts) {
    const toolUseID = opts?.toolUseID || randomUUID();
    const isQuestion = toolName === 'AskUserQuestion';
    const payload = {
      toolUseID,
      toolName,
      input,
      questions: isQuestion ? input.questions || [] : undefined,
      decisionReason: opts?.decisionReason,
      blockedPath: opts?.blockedPath,
    };
    this.emit({ t: isQuestion ? 'question' : 'permission', ...payload });
    return await new Promise((resolve) => {
      const entry = {
        resolve,
        input,
        kind: isQuestion ? 'question' : 'permission',
        toolName,
        timer: null,
      };
      // 用户一直不回答（手机丢后台、断网、人走开）时不能永久卡住这个工具调用：
      // 到点自动按拒绝处理，让 Claude Code 能继续往下走。
      const timeoutMs = Number(this.manager.config.idleTimeoutMs) || 0;
      if (timeoutMs > 0) {
        entry.timer = setTimeout(() => {
          if (!this.pending.has(toolUseID)) return;
          this.pending.delete(toolUseID);
          this.emit({ t: 'control-resolved', toolUseID, action: 'deny', timeout: true });
          resolve({ behavior: 'deny', message: '用户长时间未响应，已自动拒绝' });
        }, timeoutMs);
      }
      this.pending.set(toolUseID, entry);
    });
  }

  /** 刷新页面后重新把待回答的选框推给客户端 */
  replayPending() {
    for (const [toolUseID, entry] of this.pending) {
      this.emit({
        t: entry.kind === 'question' ? 'question' : 'permission',
        toolUseID,
        toolName: entry.kind === 'question' ? 'AskUserQuestion' : entry.toolName,
        input: entry.input,
        questions: entry.kind === 'question' ? entry.input.questions || [] : undefined,
      });
    }
  }

  async control({ toolUseID, action, answers, message }) {
    const entry = this.pending.get(toolUseID);
    if (!entry) return false;
    this.pending.delete(toolUseID);
    if (entry.timer) clearTimeout(entry.timer);
    if (action === 'deny') {
      this.emit({ t: 'control-resolved', toolUseID, action: 'deny' });
      entry.resolve({ behavior: 'deny', message: message || '用户在手机上拒绝了该操作' });
    } else if (action === 'answer') {
      this.emit({ t: 'control-resolved', toolUseID, action: 'answer', answers });
      entry.resolve({ behavior: 'allow', updatedInput: { ...entry.input, answers } });
    } else {
      this.emit({ t: 'control-resolved', toolUseID, action: 'allow' });
      entry.resolve({ behavior: 'allow', updatedInput: entry.input });
    }
    return true;
  }

  async stop() {
    if (!this.handle) return false;
    this.stopRequested = true;
    await this.handle.interrupt().catch(() => {});
    return true;
  }

  async close() {
    if (!this.handle) return;
    const handle = this.handle;
    this.handle = null;
    this.kick();
    // 会话被关闭（如切换工作目录）后，未完成轮次不再有 result 回来，清掉以免错位
    this.turnUserIds = [];
    try {
      await handle.return?.(undefined);
    } catch {
      /* 生成器已被消费完 */
    }
    for (const [, entry] of this.pending) {
      if (entry.timer) clearTimeout(entry.timer);
      entry.resolve({ behavior: 'deny', message: '会话已关闭' });
    }
    this.pending.clear();
  }

  summary() {
    return {
      id: this.meta.id,
      title: this.meta.title,
      cwd: this.meta.cwd,
      model: this.meta.model,
      effort: this.meta.effort,
      permissionMode: this.meta.permissionMode,
      channel: this.meta.channel,
      sessionId: this.meta.sessionId,
      createdAt: this.meta.createdAt,
      updatedAt: this.meta.updatedAt,
      live: Boolean(this.handle),
      messageCount: this.messages.length,
      pinned: Boolean(this.meta.pinnedAt),
    };
  }
}
