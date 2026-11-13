const BASE = 'http://127.0.0.1:8787';
const TOKEN = process.argv[2];

const cookie = `cm_auth=${encodeURIComponent(TOKEN)}`;
const H = { 'Content-Type': 'application/json', Cookie: cookie };

const events = [];
let convId = null;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function main() {
  const res = await fetch(`${BASE}/api/events`, { headers: { Cookie: cookie } });
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';

  const pump = (async () => {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let index;
      while ((index = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, index);
        buffer = buffer.slice(index + 2);
        const line = chunk.split('\n').find((l) => l.startsWith('data: '));
        if (!line) continue;
        const ev = JSON.parse(line.slice(6));
        events.push(ev);
        const label = ev.t;
        if (ev.t === 'delta') process.stdout.write(`\r  流式增量: ${JSON.stringify(ev.text.slice(0, 40))}          `);
        else if (ev.t === 'block') console.log(`\n  [块] ${ev.block.type}`, JSON.stringify(ev.block).slice(0, 220));
        else if (ev.t === 'msg') console.log('  [用户消息]', ev.msg.text);
        else if (ev.t === 'assistant-start') console.log('  [助手开始]');
        else if (ev.t === 'question') console.log('  [选框]', JSON.stringify(ev.questions || ev.input).slice(0, 300));
        else if (ev.t === 'permission') console.log('  [需确认]', ev.toolName);
        else if (ev.t === 'tool-result') console.log('  [工具结果]', String(ev.content).slice(0, 160));
        else if (ev.t === 'result') console.log('  [结果]', ev.subtype, '耗时', ev.durationMs, 'ms');
        else console.log(`  [${label}]`, JSON.stringify(ev).slice(0, 200));
      }
    }
  })();

  await sleep(600);
  const created = await fetch(`${BASE}/api/conversations`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({ cwd: (process.env.USERPROFILE || process.cwd()).replace(/\\/g, '/') }),
  }).then((r) => r.json());
  convId = created.conversation.id;
  console.log('新建对话:', convId, '\n');

  console.log('发送: 你好，请先用 AskUserQuestion 问我「要不要继续」，选项「继续」「停止」。');
  await fetch(`${BASE}/api/send`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      conversationId: convId,
      text: '你好，请先用 AskUserQuestion 问我「要不要继续」，选项「继续」「停止」。',
      permissionMode: 'default',
    }),
  });

  // 等待选框事件
  for (let i = 0; i < 40; i += 1) {
    await sleep(500);
    const q = events.find((e) => e.t === 'question');
    if (q) {
      console.log('\n>>> 模拟在手机上点击选项:', q.questions[0].options[0].label);
      const answers = {};
      for (const item of q.questions) answers[item.question] = item.options[0].label;
      await fetch(`${BASE}/api/control`, {
        method: 'POST',
        headers: H,
        body: JSON.stringify({ conversationId: convId, toolUseID: q.toolUseID, action: 'answer', answers }),
      });
      break;
    }
  }

  for (let i = 0; i < 60; i += 1) {
    await sleep(500);
    if (events.some((e) => e.t === 'result')) break;
  }

  console.log('\n\n===== 事件序列 =====');
  for (const ev of events) {
    if (ev.t === 'delta') continue;
    console.log(' ', ev.t, JSON.stringify(ev).slice(0, 200));
  }
  const result = events.find((e) => e.t === 'result');
  console.log('\n最终回答:', result ? result.result : '(无)');
  reader.cancel().catch(() => {});
  await pump;
  process.exit(0);
}

main().catch((err) => {
  console.error('测试失败:', err);
  process.exit(1);
});
