#!/usr/bin/env node
import { createHash } from 'node:crypto';
import { closeSync, lstatSync, mkdirSync, openSync, readSync, readdirSync, writeFileSync } from 'node:fs';
import { basename, join, resolve } from 'node:path';
import { check, compressedLimit, entryLimit, expandedLimit, schemaRoot, validateFiles } from './format.js';
import { readZip, writeZip } from './zip.js';
import { runScenarios } from './simulator.js';

function readBounded(path: string | URL, maximum: number): Buffer {
    const info = lstatSync(path);
    check(info.isFile() && info.size > 0 && info.size <= maximum, 'Invalid file type or size.');
    const fd = openSync(path, 'r');
    try {
        const bytes = Buffer.alloc(info.size + 1);
        let size = 0;
        while (size < bytes.length) {
            const count = readSync(fd, bytes, size, bytes.length - size, null);
            if (count === 0) break;
            size += count;
        }
        check(size === info.size, 'Input changed while reading.');
        return bytes.subarray(0, size);
    } finally {
        closeSync(fd);
    }
}

function sourceFiles(root: string): Map<string, Buffer> {
    check(lstatSync(root).isDirectory(), 'Expected a source directory.');
    const files = new Map<string, Buffer>();
    let total = 0;
    function add(name: string) {
        check(files.size < 50, 'Archive exceeds 50 entries.');
        const limit = Math.min(entryLimit(name), expandedLimit - total);
        const bytes = readBounded(join(root, name), limit);
        total += bytes.length;
        files.set(name, bytes);
    }
    for (const entry of readdirSync(root, { withFileTypes: true })) {
        if (entry.name === 'README.md' || entry.name === 'test-scenario.json') {
            check(entry.isFile(), 'Developer-only README and scenario files must be regular files.');
        } else if (entry.name === 'assets') {
            check(entry.isDirectory(), 'assets must be a directory.');
            for (const asset of readdirSync(join(root, entry.name))) add('assets/' + asset);
        } else add(entry.name);
    }
    return files;
}

const sha256 = (bytes: Buffer) => createHash('sha256').update(bytes).digest('hex');
const templates: Record<string, string> = {
    'battery-glow': 'battery-glow', 'power-card': 'power-card', 'focus-pulse': 'focus-pulse',
    'low-battery-card': 'low-battery-card', 'basic-event-driven': 'basic-event-driven',
    'battery-triggered': 'battery-triggered', 'overlay-based': 'overlay-based',
    'conditional-workflow': 'conditional-workflow', 'multi-action-workflow': 'multi-action-workflow',
    'dynamic-settings': 'dynamic-settings', 'optional-capability': 'optional-capability',
    'shizuku-safe': 'shizuku-safe'
};
const usage = `DOOM CLI — public declarative mod tooling
Usage:
  doom create <name> [--template <${Object.keys(templates).join('|')}>]
  doom validate <directory|package.doommod>
  doom package <directory>
  doom inspect <package.doommod>
  doom test <project-directory> <scenario.json>
  doom capabilities
  doom --help`;

function run(command: string, input: string, template = 'battery-glow') {
    const path = resolve(input);
    switch (command) {
        case 'create': {
            const slug = basename(input);
            check(input === slug && /^[a-z][a-z0-9-]{0,38}[a-z0-9]$/.test(slug), 'Use a lowercase project name such as amber-charge.');
            const folder = templates[template];
            check(folder !== undefined, 'Unknown SDK template. Use --help to list supported templates.');
            const seed = new URL('templates/' + folder + '/', schemaRoot);
            const manifest = JSON.parse(readBounded(new URL('doom.json', seed), 65536).toString('utf8'));
            manifest.id = 'local.' + slug.replaceAll('-', '');
            manifest.name = slug.split('-').filter(Boolean).map(word => word[0].toUpperCase() + word.slice(1)).join(' ');
            manifest.version = '1.0.0';
            manifest.author = 'Your name';
            manifest.icon = '';
            manifest.description = 'A public declarative DOOM SDK template: ' + template + '.';
            const generated = Buffer.from(JSON.stringify(manifest, null, 2) + '\n');
            const definition = readBounded(new URL('mod.json', seed), 262144);
            validateFiles(new Map([['doom.json', generated], ['mod.json', definition]]));
            const readme = readBounded(new URL('README.md', seed), 65536);
            const scenario = readBounded(new URL('test-scenario.json', seed), 1024 * 1024);
            mkdirSync(path);
            writeFileSync(join(path, 'doom.json'), generated, { flag: 'wx' });
            writeFileSync(join(path, 'mod.json'), definition, { flag: 'wx' });
            writeFileSync(join(path, 'README.md'), readme, { flag: 'wx' });
            writeFileSync(join(path, 'test-scenario.json'), scenario, { flag: 'wx' });
            console.log(path);
            return;
        }
        case 'validate': {
            const files = lstatSync(path).isDirectory() ? sourceFiles(path) : readZip(readBounded(path, compressedLimit));
            const { manifest } = validateFiles(files);
            console.log('Valid: ' + manifest.id);
            return;
        }
        case 'package': {
            const files = sourceFiles(path);
            validateFiles(files);
            const archive = writeZip(files);
            writeFileSync(path + '.doommod', archive, { flag: 'wx' });
            console.log(path + '.doommod ' + sha256(archive));
            return;
        }
        case 'inspect': {
            const archive = readBounded(path, compressedLimit);
            const files = readZip(archive);
            console.log(JSON.stringify({
                ...validateFiles(files), sha256: sha256(archive),
                files: [...files].map(([name, bytes]) => ({ name, size: bytes.length, sha256: sha256(bytes) }))
            }, null, 2));
            return;
        }
        case 'test': {
            check(template !== undefined, 'doom test requires a scenario JSON path.');
            const result = runScenarios(path, resolve(template));
            if (result.failed) process.exitCode = 1;
            return;
        }
        default: throw new Error(usage);
    }
}

try {
    const args = process.argv.slice(2);
    if (args.length === 0 || args.some(arg => ['--help', '-h'].includes(arg)) || args.length === 1 && args[0] === 'help') {
        console.log(usage);
    } else if (args.length === 1 && args[0] === 'capabilities') {
        console.log(readBounded(new URL('v2/registry.json', schemaRoot), 65536).toString('utf8'));
    } else if (args.length === 3 && args[0] === 'test') {
        run(args[0], args[1], args[2]);
    } else {
        check(args.length === 2 || (args.length === 4 && args[0] === 'create' && args[2] === '--template'), usage);
        run(args[0], args[1], args[3]);
    }
} catch (error) {
    console.error(error instanceof Error ? error.message : error);
    process.exitCode = 2;
}
