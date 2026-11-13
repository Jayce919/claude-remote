import { execFileSync } from 'node:child_process';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

// 无构建步骤，这里只做语法门禁：逐个跑 node --check，任何一个文件语法错就失败。
const root = path.dirname(path.dirname(fileURLToPath(import.meta.url)));
const files = ['server.js'];
for (const name of fs.readdirSync(path.join(root, 'lib')).filter((f) => f.endsWith('.js')).sort()) {
  files.push(path.join('lib', name));
}

let failed = 0;
for (const rel of files) {
  const file = path.join(root, rel);
  try {
    execFileSync(process.execPath, ['--check', file], { stdio: 'pipe' });
    console.log(`ok   ${rel}`);
  } catch (err) {
    failed += 1;
    console.error(`FAIL ${rel}`);
    console.error(String(err.stderr || err.message));
  }
}

if (failed) {
  console.error(`\n${failed} 个文件有语法错误`);
  process.exit(1);
}
console.log(`\n${files.length} 个文件语法检查通过`);
