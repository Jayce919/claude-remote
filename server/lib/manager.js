import fs from 'node:fs';
import path from 'node:path';
import { randomUUID } from 'node:crypto';
import { Conversation } from './conversation.js';
import { CHANNEL_IDS, readChannels } from './channels.js';

export class Manager {
  constructor({ config, store }) {
    this.config = config;
    this.store = store;
    this.conversations = new Map();
    this.clients = new Set();
    for (const meta of store.load()) {
      meta.messages = Array.isArray(meta.messages) ? meta.messages : [];
      this.conversations.set(meta.id, new Conversation(meta, this));
    }
  }

  list() {
    return [...this.conversations.values()]
      .map((c) => c.summary())
      .sort(
        (a, b) =>
          Number(b.pinned) - Number(a.pinned) ||
          (b.updatedAt || 0) - (a.updatedAt || 0),
      );
  }

  /**
   * 标题 + 消息正文的全文搜索。只查用户文本和助手正文／思考块，
   * 工具入参不查，避免把大段 JSON 拼进来拖慢响应。
   */
  search(query) {
    const q = String(query || '').trim().toLowerCase();
    if (!q) return [];
    const hits = [];
    for (const conversation of this.conversations.values()) {
      if (this.matches(conversation, q)) hits.push(conversation.summary());
    }
    return hits.sort(
      (a, b) =>
        Number(b.pinned) - Number(a.pinned) ||
        (b.updatedAt || 0) - (a.updatedAt || 0),
    );
  }

  matches(conversation, q) {
    if (String(conversation.meta.title || '').toLowerCase().includes(q)) return true;
    for (const message of conversation.messages) {
      if (String(message.text || '').toLowerCase().includes(q)) return true;
      for (const block of message.blocks || []) {
        if (block && typeof block.text === 'string' && block.text.toLowerCase().includes(q)) {
          return true;
        }
      }
    }
    return false;
  }

  create(meta = {}) {
    const now = Date.now();
    const id = randomUUID();
    // 通道跟着对话走：同一时间两个会话可以分别走 DeepSeek 和 OpenCode
    const channel = CHANNEL_IDS.includes(meta.channel) ? meta.channel : this.config.defaultChannel;
    let model = meta.model ?? this.config.defaultModel;
    if (meta.channel) {
      // 客户端明确选了通道时，模型名必须来自该通道：别的通道的模型上游不认，
      // 首轮就会 400。列表读不到时保持原值，交给 CLI 自己的默认模型。
      const models = readChannels(this.config).channels.find((c) => c.id === channel)?.models || [];
      if (models.length && !models.includes(model)) model = models[0];
    }
    const full = {
      id,
      title: meta.title || '',
      cwd: meta.cwd || this.config.defaultCwd,
      model,
      effort: meta.effort ?? this.config.defaultEffort,
      permissionMode: meta.permissionMode || this.config.defaultPermissionMode,
      channel,
      sessionId: null,
      createdAt: now,
      updatedAt: now,
      pinnedAt: 0,
      messages: [],
    };
    const conversation = new Conversation(full, this);
    this.conversations.set(id, conversation);
    this.save();
    this.broadcast({ t: 'conv-list', conversations: this.list() });
    return conversation;
  }

  get(id) {
    return this.conversations.get(id) || null;
  }

  async remove(id) {
    const conversation = this.conversations.get(id);
    if (!conversation) return false;
    await conversation.close();
    this.conversations.delete(id);
    this.save();
    this.broadcast({ t: 'conv-list', conversations: this.list() });
    return true;
  }

  save() {
    const conversations = [...this.conversations.values()].map((conversation) => ({
      ...conversation.meta,
      messages: conversation.messages.slice(-120).map((message) => {
        if (!message.images || !message.images.length) return message;
        const images = message.images.map((image) => ({
          name: image.name,
          mediaType: image.mediaType,
          preview: image.preview && image.preview.length < 400000 ? image.preview : '',
        }));
        return { ...message, images };
      }),
    }));
    this.store.save({ conversations });
  }

  addClient(res) {
    this.clients.add(res);
    res.on('close', () => this.clients.delete(res));
  }

  /** 新客户端接入时，把还没回答的选框／权限确认重新推一遍 */
  replayPending() {
    for (const conversation of this.conversations.values()) conversation.replayPending();
  }

  broadcast(event) {
    const payload = `data: ${JSON.stringify(event)}\n\n`;
    for (const res of this.clients) {
      try {
        res.write(payload);
      } catch {
        this.clients.delete(res);
      }
    }
  }

  /** 电脑端 Claude Code 用过的项目目录，作为工作目录候选 */
  knownWorkspaces() {
    const root = path.join(process.env.USERPROFILE || '', '.claude', 'projects');
    const found = new Set();
    try {
      for (const entry of fs.readdirSync(root, { withFileTypes: true })) {
        if (!entry.isDirectory()) continue;
        const dir = path.join(root, entry.name);
        const files = fs
          .readdirSync(dir)
          .filter((f) => f.endsWith('.jsonl'))
          .map((f) => path.join(dir, f))
          .sort((a, b) => fs.statSync(b).mtimeMs - fs.statSync(a).mtimeMs);
        for (const file of files.slice(0, 3)) {
          try {
            const head = fs.readFileSync(file, 'utf8').slice(0, 4000);
            const match = /"cwd"\s*:\s*"([^"]+)"/.exec(head);
            if (match && fs.existsSync(match[1])) {
              found.add(match[1]);
              break;
            }
          } catch {
            /* 读不到就跳过 */
          }
        }
      }
    } catch {
      return [];
    }
    return [...found].slice(0, 40);
  }
}
