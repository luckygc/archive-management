import { existsSync, readFileSync, readdirSync } from 'node:fs';
import { dirname, join, relative, resolve, sep } from 'node:path';

const root = process.cwd();
const errors = [];

function local(path) {
  const absolute = resolve(root, path);
  const fromRoot = relative(root, absolute);
  if (fromRoot === '..' || fromRoot.startsWith(`..${sep}`)) {
    errors.push(`路径超出仓库：${path}`);
    return null;
  }
  return absolute;
}

function read(path) {
  const absolute = local(path);
  if (!absolute || !existsSync(absolute)) {
    errors.push(`缺少文件：${path}`);
    return '';
  }
  return readFileSync(absolute, 'utf8');
}

function directoryNames(path) {
  const absolute = local(path);
  if (!absolute || !existsSync(absolute)) {
    errors.push(`缺少目录：${path}`);
    return [];
  }
  return readdirSync(absolute, { withFileTypes: true })
    .filter((entry) => entry.isDirectory())
    .map((entry) => entry.name)
    .sort();
}

function specFileNames(path) {
  const absolute = local(path);
  if (!absolute || !existsSync(absolute)) {
    errors.push(`缺少目录：${path}`);
    return [];
  }
  return readdirSync(absolute, { withFileTypes: true })
    .filter((entry) => entry.isFile() && /^SPEC-[^/\\]+\.md$/u.test(entry.name))
    .map((entry) => entry.name)
    .sort();
}

const projectSpec = read('SPEC.md');
for (const heading of ['目标与用户', '能力与验收', '技术与命令', '项目结构', '代码风格', '测试策略', '工程边界', '完成判据']) {
  if (!projectSpec.includes(`## ${heading}`)) errors.push(`SPEC.md 缺少章节：${heading}`);
}

const stableNames = specFileNames('specs');
const stableIndex = read('specs/README.md');
for (const name of stableNames) {
  read(`specs/${name}`);
  if (!stableIndex.includes(`(${name})`)) errors.push(`稳定规格索引缺少：${name}`);
}

const taskNames = directoryNames('tasks');
const taskIndex = read('tasks/README.md');
for (const name of taskNames) {
  for (const filename of ['SPEC.md', 'plan.md', 'todo.md']) read(`tasks/${name}/${filename}`);
  if (!taskIndex.includes(`(${name}/SPEC.md)`)) errors.push(`变更索引缺少：${name}`);
  const deltaPath = `tasks/${name}/specs`;
  if (existsSync(local(deltaPath))) {
    for (const capability of specFileNames(deltaPath)) {
      read(`${deltaPath}/${capability}`);
      const taskSpec = read(`tasks/${name}/SPEC.md`);
      if (!taskSpec.includes(`(specs/${capability})`)) {
        errors.push(`变更规格缺少增量索引：${name}/${capability}`);
      }
    }
  }
}

const markdownFiles = ['SPEC.md', 'README.md', 'AGENTS.md'];
function collectMarkdown(path) {
  const absolute = local(path);
  if (!absolute || !existsSync(absolute)) return;
  for (const entry of readdirSync(absolute, { withFileTypes: true })) {
    const child = join(path, entry.name);
    if (entry.isDirectory()) collectMarkdown(child);
    else if (entry.isFile() && entry.name.endsWith('.md')) markdownFiles.push(child);
  }
}
for (const path of ['docs', 'specs', 'tasks', '.codex/skills']) collectMarkdown(path);

for (const path of markdownFiles) {
  const content = read(path);
  if (content.includes('openspec/') || content.includes('PRODUCT.md') || content.includes('DESIGN.md') || content.includes('CONTEXT.md')) {
    errors.push(`${path} 仍引用旧文档位置`);
  }
  for (const match of content.matchAll(/\]\(([^)]+)\)/g)) {
    const target = match[1].replace(/^<|>$/g, '').split('#', 1)[0];
    if (!target || /^(?:[a-z]+:|\/\/)/i.test(target)) continue;
    const absolute = resolve(dirname(local(path)), decodeURIComponent(target));
    const fromRoot = relative(root, absolute);
    if (fromRoot === '..' || fromRoot.startsWith(`..${sep}`) || !existsSync(absolute)) {
      errors.push(`${path} 中的链接无效：${match[1]}`);
    }
  }
}

for (const path of ['docs/design-system.md', 'docs/domain-glossary.md']) read(path);

if (errors.length) {
  for (const error of [...new Set(errors)]) console.error(error);
  process.exitCode = 1;
} else {
  console.log(`治理检查通过：${stableNames.length} 份稳定规格、${taskNames.length} 个进行中变更、${markdownFiles.length} 份 Markdown 文档`);
}
