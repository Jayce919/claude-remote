const BASE = 'http://127.0.0.1:8787';
const TOKEN = process.argv[2];
const HOME = (process.env.USERPROFILE || process.cwd()).replace(/\\/g, '/');
const cookie = `cm_auth=${encodeURIComponent(TOKEN)}`;
const H = { 'Content-Type': 'application/json', Cookie: cookie };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const events = [];

async function main() {
  const res = await fetch(`${BASE}/api/events`, { headers: { Cookie: cookie } });
  const reader = res.body.getReader();
  const decoder = new TextDecoder();
  let buffer = '';
  (async () => {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      buffer += decoder.decode(value, { stream: true });
      let i;
      while ((i = buffer.indexOf('\n\n')) !== -1) {
        const chunk = buffer.slice(0, i);
        buffer = buffer.slice(i + 2);
        const line = chunk.split('\n').find((l) => l.startsWith('data: '));
        if (line) events.push(JSON.parse(line.slice(6)));
      }
    }
  })();

  await sleep(500);
  const created = await fetch(`${BASE}/api/conversations`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({ cwd: HOME, permissionMode: 'default' }),
  }).then((r) => r.json());
  const convId = created.conversation.id;

  const command = `请运行命令 powershell -NoProfile -Command "Set-Content -Path ${HOME}/perm-test.txt -Value hi"`;
  console.log('发送:', command);
  await fetch(`${BASE}/api/send`, {
    method: 'POST',
    headers: H,
    body: JSON.stringify({
      conversationId: convId,
      text: command,
      permissionMode: 'default',
      model: 'deepseek-v4-pro',
    }),
  });

  let approved = false;
  for (let i = 0; i < 120 && !approved; i += 1) {
    await sleep(500);
    const p = events.find((e) => e.t === 'permission' && e.convId === convId);
    if (p) {
      approved = true;
      console.log('\n>>> 收到权限请求:', p.toolName, '|', String(p.decisionReason || '').slice(0, 80));
      console.log('>>> 输入:', JSON.stringify(p.input).slice(0, 200));
      console.log('>>> 模拟手机点「允许」');
      await fetch(`${BASE}/api/control`, {
        method: 'POST',
        headers: H,
        body: JSON.stringify({ conversationId: convId, toolUseID: p.toolUseID, action: 'allow' }),
      });
    }
  }

  for (let i = 0; i < 120; i += 1) {
    await sleep(500);
    if (events.some((e) => e.t === 'result' && e.convId === convId)) break;
  }

  const toolResult = events.find((e) => e.t === 'tool-result' && e.convId === convId);
  console.log('\n是否收到权限请求:', approved);
  console.log('工具结果:', toolResult ? String(toolResult.content).slice(0, 200) : '(无)');
  console.log('最终回答:', String(events.find((e) => e.t === 'result' && e.convId === convId)?.result || '(无)').slice(0, 200));
  console.log('事件类型:', [...new Set(events.filter(e=>e.convId===convId||!e.convId).map((e) => e.t))].join(' -> '));
  reader.cancel().catch(() => {});
  process.exit(0);
}

main().catch((err) => {
  console.error('失败:', err);
  process.exit(1);
});
