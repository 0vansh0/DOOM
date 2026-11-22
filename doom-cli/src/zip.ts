import { crc32, inflateRawSync } from 'node:zlib';
import { check, compressedLimit, entryLimit, expandedLimit } from './format.js';

export function readZip(bytes: Buffer): Map<string, Buffer> {
    const end = bytes.length - 22;
    check(end >= 0 && bytes.length <= compressedLimit && bytes.readUInt32LE(end) === 0x06054b50 &&
        bytes.readUInt32LE(end + 4) === 0 && bytes.readUInt16LE(end + 20) === 0, 'Expected a canonical ZIP.');
    const count = bytes.readUInt16LE(end + 10);
    const start = bytes.readUInt32LE(end + 16);
    check(count >= 2 && count <= 50 && bytes.readUInt16LE(end + 8) === count &&
        bytes.readUInt32LE(end + 12) === end - start, 'Invalid ZIP directory.');
    const entries = new Map<string, Buffer>();
    const spans: [number, number][] = [];
    let position = start, total = 0;
    for (let i = 0; i < count; i++) {
        check(position + 46 <= end && bytes.readUInt32LE(position) === 0x02014b50, 'Invalid ZIP central header.');
        const flags = bytes.readUInt16LE(position + 8);
        const method = bytes.readUInt16LE(position + 10);
        const crc = bytes.readUInt32LE(position + 16);
        const compressed = bytes.readUInt32LE(position + 20);
        const expanded = bytes.readUInt32LE(position + 24);
        const length = bytes.readUInt16LE(position + 28);
        const mode = bytes.readUInt16LE(position + 40) & 0xf000;
        check([0, 2048].includes(flags) && [0, 8].includes(method) && length >= 1 && length <= 51 &&
            position + 46 + length <= end && bytes.readUInt32LE(position + 30) === 0 &&
            bytes.readUInt16LE(position + 34) === 0 && [0, 0x8000].includes(mode) &&
            (bytes.readUInt16LE(position + 38) & 0x18) === 0, 'Unsupported ZIP entry.');
        const name = bytes.toString('latin1', position + 46, position + 46 + length);
        total += expanded;
        check(compressed > 0 && expanded > 0 && expanded <= entryLimit(name) && total <= expandedLimit &&
            !entries.has(name), 'Invalid entry size or duplicate ZIP entry.');
        const local = bytes.readUInt32LE(position + 42);
        const data = local + 30 + length;
        check(data + compressed <= start && bytes.readUInt32LE(local) === 0x04034b50 &&
            bytes.readUInt16LE(local + 6) === flags && bytes.readUInt16LE(local + 8) === method &&
            bytes.readUInt32LE(local + 14) === crc && bytes.readUInt32LE(local + 18) === compressed &&
            bytes.readUInt32LE(local + 22) === expanded && bytes.readUInt16LE(local + 26) === length &&
            bytes.readUInt16LE(local + 28) === 0 && bytes.toString('latin1', local + 30, data) === name,
        'ZIP headers disagree.');
        const block = bytes.subarray(data, data + compressed);
        let contents = block;
        if (method === 8) {
            const result = inflateRawSync(block, { maxOutputLength: expanded + 1, info: true }) as unknown as {
                buffer: Buffer; engine: { bytesWritten: number }
            };
            check(result.engine.bytesWritten === compressed, 'Trailing compressed data.');
            contents = result.buffer;
        }
        check(contents.length === expanded && crc32(contents) === crc, 'Invalid ZIP expansion or checksum.');
        entries.set(name, contents);
        spans.push([local, data + compressed]);
        position += 46 + length;
    }
    check(position === end, 'Unexpected data in ZIP directory.');
    let next = 0;
    for (const [from, to] of spans.sort((a, b) => a[0] - b[0])) {
        check(from === next, 'Overlapping or unlisted ZIP entry.');
        next = to;
    }
    check(next === start, 'Unexpected bytes before ZIP directory.');
    return entries;
}

export function writeZip(files: Map<string, Buffer>): Buffer {
    const locals: Buffer[] = [];
    const central: Buffer[] = [];
    let offset = 0;
    for (const name of [...files.keys()].sort()) {
        const label = Buffer.from(name, 'ascii');
        const contents = files.get(name)!;
        const checksum = crc32(contents);
        const local = Buffer.alloc(30);
        local.writeUInt32LE(0x04034b50);
        local.writeUInt16LE(20, 4);
        local.writeUInt16LE(2048, 6);
        local.writeUInt16LE(33, 12);
        local.writeUInt32LE(checksum, 14);
        local.writeUInt32LE(contents.length, 18);
        local.writeUInt32LE(contents.length, 22);
        local.writeUInt16LE(label.length, 26);
        locals.push(local, label, contents);
        const header = Buffer.alloc(46);
        header.writeUInt32LE(0x02014b50);
        header.writeUInt16LE(0x314, 4);
        header.writeUInt16LE(20, 6);
        header.writeUInt16LE(2048, 8);
        header.writeUInt16LE(33, 14);
        header.writeUInt32LE(checksum, 16);
        header.writeUInt32LE(contents.length, 20);
        header.writeUInt32LE(contents.length, 24);
        header.writeUInt16LE(label.length, 28);
        header.writeUInt32LE(0x81a40000, 38);
        header.writeUInt32LE(offset, 42);
        central.push(header, label);
        offset += local.length + label.length + contents.length;
    }
    const tail = Buffer.alloc(22);
    tail.writeUInt32LE(0x06054b50);
    tail.writeUInt16LE(files.size, 8);
    tail.writeUInt16LE(files.size, 10);
    tail.writeUInt32LE(central.reduce((size, bytes) => size + bytes.length, 0), 12);
    tail.writeUInt32LE(offset, 16);
    const archive = Buffer.concat([...locals, ...central, tail]);
    check(archive.length <= compressedLimit, 'Package exceeds 10 MiB.');
    return archive;
}
