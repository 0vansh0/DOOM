import { readdir, readFile, stat } from 'node:fs/promises';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

export async function markdownFiles(root) {
  const result = [];
  async function walk(directory) {
    for (const entry of await readdir(directory, { withFileTypes: true })) {
      if (entry.name === 'node_modules' || entry.name === 'build' || entry.name === 'dist' || entry.name === '.git') continue;
      const absolute = path.join(directory, entry.name);
      if (entry.isDirectory()) await walk(absolute);
      else if (entry.isFile() && entry.name.toLowerCase().endsWith('.md')) result.push(absolute);
    }
  }
  await walk(root);
  return result;
}

export async function brokenLocalLinks(root, files) {
  const broken = [];
  for (const source of files) {
    const content = (await readFile(source, 'utf8')).replace(/```[\s\S]*?```/g, '');
    const links = [...content.matchAll(/\[[^\]]*\]\(([^)\s]+)(?:\s+[^)]*)?\)/g)];
    for (const match of links) {
      const raw = match[1].replaceAll('&amp;', '&');
      if (!raw || raw.startsWith('#') || /^(?:https?:|mailto:|tel:|data:)/i.test(raw)) continue;
      const target = decodeURIComponent(raw.split('#')[0].split('?')[0]);
      if (!target) continue;
      const absolute = path.resolve(path.dirname(source), target);
      try {
        await stat(absolute);
      } catch {
        broken.push({ source: path.relative(root, source), target: raw });
      }
    }
  }
  return broken;
}

async function main() {
  const repository = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '../..');
  const files = await markdownFiles(repository);
  const broken = await brokenLocalLinks(repository, files);
  if (broken.length) {
    for (const item of broken) console.error(`${item.source}: missing local target ${item.target}`);
    console.error(`Documentation link check failed: ${broken.length} missing target(s) in ${files.length} Markdown files.`);
    process.exitCode = 1;
    return;
  }
  console.log(`Documentation link check passed: ${files.length} Markdown files, no missing local targets.`);
}

if (process.argv[1] && path.resolve(process.argv[1]) === fileURLToPath(import.meta.url)) await main();
