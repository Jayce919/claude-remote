/**
 * 双通道解析的离线自检：直接读本机 cc-switch 数据库，不发网络请求。
 * 只关心三件事——两条通道都能认出来、凭据能取到、完整令牌不会外泄。
 */
import assert from 'node:assert/strict';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadConfig } from '../lib/config.js';
import { readChannels, channelEnv, CHANNEL_IDS } from '../lib/channels.js';

const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const config = loadConfig(root);

let passed = 0;
function check(name, fn) {
  try {
    fn();
    passed += 1;
    console.log(`ok   ${name}`);
  } catch (err) {
    console.error(`FAIL ${name}`);
    throw err;
  }
}

const state = readChannels(config);

check('返回默认通道，且只有 deepseek / opencode 两条', () => {
  assert.ok(CHANNEL_IDS.includes(state.defaultChannel));
  assert.deepEqual(state.channels.map((c) => c.id), ['deepseek', 'opencode']);
});

check('两条通道的凭据都能解析（available 为 true）', () => {
  for (const channel of state.channels) {
    assert.equal(channel.available, true, `${channel.id} 不可用`);
    assert.ok(channel.providerId, `${channel.id} 缺少 providerId`);
    assert.ok(channel.provider, `${channel.id} 缺少 provider 名称`);
  }
});

check('两条通道都有非空模型列表', () => {
  for (const channel of state.channels) {
    assert.ok(Array.isArray(channel.models) && channel.models.length > 0, `${channel.id} 模型为空`);
    assert.equal(new Set(channel.models).size, channel.models.length, `${channel.id} 模型有重复`);
    assert.ok(Array.isArray(channel.visionModels), `${channel.id} 缺少 visionModels`);
  }
});

check('返回值里没有完整令牌', () => {
  const text = JSON.stringify(state);
  assert.ok(!text.includes('sk-'), '返回值里出现了 sk- 开头的令牌');
  assert.ok(!text.includes('oc_sk_'), '返回值里出现了 oc_sk_ 开头的令牌');
  for (const channel of state.channels) {
    assert.ok(String(channel.tokenHint || '').length <= 4, `${channel.id} 的 tokenHint 过长`);
  }
});

check('channelEnv(deepseek) 指向 DeepSeek 官方', () => {
  const env = channelEnv('deepseek');
  assert.ok(env, 'deepseek 没有返回 env');
  assert.ok(env.ANTHROPIC_BASE_URL.includes('api.deepseek.com'), env.ANTHROPIC_BASE_URL);
  assert.ok(env.ANTHROPIC_AUTH_TOKEN.length > 10);
});

check('channelEnv(opencode) 指向 OpenCode', () => {
  const env = channelEnv('opencode');
  assert.ok(env, 'opencode 没有返回 env');
  assert.ok(env.ANTHROPIC_BASE_URL.includes('opencode.ai'), env.ANTHROPIC_BASE_URL);
  assert.ok(env.ANTHROPIC_AUTH_TOKEN.length > 10);
});

check('非法通道 id 返回 null', () => {
  for (const id of ['', 'nope', 'claude-desktop', undefined, null]) {
    assert.equal(channelEnv(id), null, `${String(id)} 不该有 env`);
  }
});

check('两条通道选中的不是同一份凭据', () => {
  assert.notEqual(channelEnv('deepseek').ANTHROPIC_AUTH_TOKEN, channelEnv('opencode').ANTHROPIC_AUTH_TOKEN);
});

console.log(`\n${passed} 项检查全部通过`);
