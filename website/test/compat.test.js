import { test } from 'node:test';
import assert from 'node:assert/strict';
import { deflateRawSync } from 'node:zlib';
import { readFileSync } from 'node:fs';
import { checkFile, readHeader, verdict } from '../src/lib/compat.js';

const data = JSON.parse(readFileSync(new URL('../src/data/compat.json', import.meta.url)));
const MB = 1024 * 1024;

function gba(code, rev = 0) {
  const b = new Uint8Array(0x200);
  b.set([...code].map((c) => c.charCodeAt(0)), 0xac);
  b[0xbc] = rev;
  return b;
}

const v = (code, rev, size, sha1 = '0'.repeat(40)) => verdict(data, { platform: 'gba', code, rev, size, sha1 });

test('header', () => {
  assert.deepEqual(readHeader(gba('BPRE', 1)), { platform: 'gba', code: 'BPRE', rev: 1 });
  const gb = new Uint8Array(0x150);
  gb.set([0xce, 0xed, 0x66, 0x66], 0x104);
  assert.deepEqual(readHeader(gb), { platform: 'gb' });
  assert.deepEqual(readHeader(new Uint8Array(0x200)), { platform: null });
});

test('FireRed / Emerald: known dumps, unknown small ROMs, big hacks', () => {
  assert.deepEqual(v('BPRE', 1, 16 * MB, '41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc'),
    { status: 'supported', title: 'Pokémon FireRed' });
  assert.equal(v('BPEE', 0, 16 * MB).status, 'likely');
  assert.deepEqual(v('BPRE', 0, 32 * MB, 'b4776b82a4c7915d0fadeaa27e013523f99dfd94'),
    { status: 'supported', title: 'Pokémon Unbound', version: 'v2.1.1.1', base: 'Pokémon FireRed' });
  assert.equal(v('BPEE', 0, 32 * MB).reason, 'unknown-hack');
  // R.O.W.E. plays, but its companion is still in progress.
  assert.deepEqual(v('BPEE', 0, 32 * MB, '81bd0f4bfa1c04ab2c6faab1bddd10e8a390ea77'),
    { status: 'unsupported', reason: 'in-progress', title: 'Pokémon R.O.W.E.', version: 'v2.1.9.1', base: 'Pokémon Emerald' });
});

test('other retail games: revisions and size', () => {
  assert.equal(v('AXVE', 2, 16 * MB).status, 'supported');
  assert.equal(v('BPES', 1, 16 * MB).reason, 'revision');
  assert.equal(v('BPGE', 1, 32 * MB).reason, 'retail-hack');
  assert.equal(v('AGBJ', 0, 4 * MB).reason, 'other-game');
});

test('Game Boy: Yellow only', () => {
  assert.equal(verdict(data, { platform: 'gb', sha1: 'cc7d03262ebfaf2f06772c1a480c7d9d5f4a38e1' }).title, 'Pokémon Yellow');
  assert.equal(verdict(data, { platform: 'gb', sha1: '0'.repeat(40) }).reason, 'gb');
});

test('a zipped ROM is judged by the ROM inside', async () => {
  const rom = gba('AXPE', 1);
  const name = new TextEncoder().encode('Sapphire.gba');
  const packed = deflateRawSync(rom);
  const local = new Uint8Array(30 + name.length);
  const lv = new DataView(local.buffer);
  lv.setUint32(0, 0x04034b50, true); lv.setUint16(8, 8, true);
  lv.setUint32(18, packed.length, true); lv.setUint32(22, rom.length, true); lv.setUint16(26, name.length, true);
  local.set(name, 30);
  const central = new Uint8Array(46 + name.length);
  const cv = new DataView(central.buffer);
  cv.setUint32(0, 0x02014b50, true); cv.setUint16(10, 8, true);
  cv.setUint32(20, packed.length, true); cv.setUint32(24, rom.length, true); cv.setUint16(28, name.length, true);
  central.set(name, 46);
  const eocd = new Uint8Array(22);
  const ev = new DataView(eocd.buffer);
  ev.setUint32(0, 0x06054b50, true); ev.setUint16(8, 1, true); ev.setUint16(10, 1, true);
  ev.setUint32(12, central.length, true); ev.setUint32(16, local.length + packed.length, true);
  const zip = new Uint8Array([...local, ...packed, ...central, ...eocd]);

  const r = await checkFile(data, 'Sapphire.zip', zip);
  assert.equal(r.name, 'Sapphire.gba');
  assert.equal(r.code, 'AXPE');
  assert.equal(r.size, rom.length);
  assert.equal(r.verdict.status, 'supported');
});
