import fs from 'node:fs';
import path from 'node:path';

export class Store {
  constructor(file) {
    this.file = file;
    this.timer = null;
  }

  load() {
    try {
      const data = JSON.parse(fs.readFileSync(this.file, 'utf8'));
      return Array.isArray(data.conversations) ? data.conversations : [];
    } catch {
      return [];
    }
  }

  /** 去抖保存：高频写盘会拖慢流式输出 */
  save(payload) {
    if (this.timer) clearTimeout(this.timer);
    this.timer = setTimeout(() => this.flush(payload), 400);
  }

  flush(payload) {
    this.timer = null;
    let tmp = null;
    try {
      fs.mkdirSync(path.dirname(this.file), { recursive: true });
      // 先写临时文件再原子替换，避免 36MB 的 conversations.json 写到一半崩了损坏数据
      tmp = `${this.file}.tmp-${process.pid}`;
      fs.writeFileSync(tmp, JSON.stringify(payload, null, 2), 'utf8');
      fs.renameSync(tmp, this.file);
    } catch (err) {
      if (tmp) {
        try {
          fs.rmSync(tmp, { force: true });
        } catch {
          /* 临时文件清不掉就算了 */
        }
      }
      console.error('[store] 保存失败:', err.message);
    }
  }
}
