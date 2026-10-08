// The ROM compatibility check, in the browser. The same rules as the app's
// CompanionSupport.isSupported (app/src/main/kotlin/com/pokedaisy/app/CompanionSupport.kt),
// over the tables SiteDataExportTest exports into src/data/compat.json. Change one, change both.
//
// Pure functions over bytes: the page reads the file, nothing is uploaded.

const GB_LOGO_START = [0xce, 0xed, 0x66, 0x66];
export const ROM_EXTENSIONS = ['.gba', '.gb', '.gbc'];

/** What the cart header says: platform, game code (0xAC) and revision (0xBC) for a GBA ROM. */
export function readHeader(bytes) {
  if (bytes.length >= 0x108 && GB_LOGO_START.every((b, i) => bytes[0x104 + i] === b)) {
    return { platform: 'gb' };
  }
  if (bytes.length < 0xc0) return { platform: null };
  const code = String.fromCharCode(...bytes.subarray(0xac, 0xb0));
  if (!/^[A-Z0-9]{4}$/.test(code)) return { platform: null };
  return { platform: 'gba', code, rev: bytes[0xbc] };
}

export async function sha1Hex(bytes) {
  const digest = await crypto.subtle.digest('SHA-1', bytes);
  return [...new Uint8Array(digest)].map((b) => b.toString(16).padStart(2, '0')).join('');
}

/**
 * The verdict for one ROM: `supported` (the companion knows this exact game), `likely`
 * (read as FireRed / Emerald - retail-sized but not a dump we know: a QoL build or a small
 * hack) or `unsupported`. `size` is the ROM's own size (not an archive's).
 */
export function verdict(data, { platform, code, rev, size, sha1 }) {
  if (platform === 'gb') {
    const hit = data.gameBoy.find((g) => g.sha1 === sha1);
    return hit
      ? { status: 'supported', title: hit.title, family: 'Game Boy' }
      : { status: 'unsupported', reason: 'gb' };
  }
  if (platform !== 'gba') return { status: 'unsupported', reason: 'not-a-rom' };

  const retail = data.retail.find((r) => r.code === code);
  if (retail) {
    if (size > data.maxBaseSize) return { status: 'unsupported', reason: 'retail-hack', base: retail.title };
    if (!retail.revs.includes(rev)) return { status: 'unsupported', reason: 'revision', base: retail.title };
    return { status: 'supported', title: data.titles[sha1] ?? retail.title };
  }

  const base = data.baseCodes[code];
  if (!base) return { status: 'unsupported', reason: 'other-game' };
  const hack = data.hacks.find((h) => h.sha1 === sha1);
  if (hack) return { status: 'supported', title: hack.title, version: hack.version, base };
  // Detected by the app, but its companion isn't ready yet: the game plays, the companion doesn't read it.
  const soon = (data.inProgress ?? []).find((h) => h.sha1 === sha1);
  if (soon) return { status: 'unsupported', reason: 'in-progress', title: soon.title, version: soon.version, base };
  if (size > data.maxBaseSize) return { status: 'unsupported', reason: 'unknown-hack', base };
  const title = data.titles[sha1];
  return title ? { status: 'supported', title } : { status: 'likely', base };
}

/**
 * The ROM inside a .zip (the first .gba / .gb / .gbc entry), unpacked with the browser's
 * own DecompressionStream. Returns { name, bytes } or null when there's none.
 */
export async function unzipRom(zip) {
  const view = new DataView(zip.buffer, zip.byteOffset, zip.byteLength);
  let eocd = -1;
  for (let i = zip.length - 22; i >= Math.max(0, zip.length - 22 - 0xffff); i--) {
    if (view.getUint32(i, true) === 0x06054b50) { eocd = i; break; }
  }
  if (eocd < 0) throw new Error('not a zip file');
  const count = view.getUint16(eocd + 10, true);
  let p = view.getUint32(eocd + 16, true);
  const decoder = new TextDecoder();
  for (let n = 0; n < count; n++) {
    if (view.getUint32(p, true) !== 0x02014b50) throw new Error('broken zip directory');
    const method = view.getUint16(p + 10, true);
    const compSize = view.getUint32(p + 20, true);
    const nameLen = view.getUint16(p + 28, true);
    const extraLen = view.getUint16(p + 30, true);
    const commentLen = view.getUint16(p + 32, true);
    const local = view.getUint32(p + 42, true);
    const name = decoder.decode(zip.subarray(p + 46, p + 46 + nameLen));
    p += 46 + nameLen + extraLen + commentLen;
    if (!ROM_EXTENSIONS.some((ext) => name.toLowerCase().endsWith(ext))) continue;
    if (compSize === 0xffffffff) throw new Error('zip64 archives aren\'t supported');
    const start = local + 30 + view.getUint16(local + 26, true) + view.getUint16(local + 28, true);
    const packed = zip.subarray(start, start + compSize);
    if (method === 0) return { name, bytes: packed };
    if (method !== 8) throw new Error(`unsupported zip compression (method ${method})`);
    const stream = new Blob([packed]).stream().pipeThrough(new DecompressionStream('deflate-raw'));
    return { name, bytes: new Uint8Array(await new Response(stream).arrayBuffer()) };
  }
  return null;
}

/** Everything the page shows for one dropped file: header, hash, size and the verdict. */
export async function checkFile(data, name, bytes) {
  const lower = name.toLowerCase();
  if (lower.endsWith('.7z')) return { name, verdict: { status: 'unsupported', reason: '7z' } };
  if (lower.endsWith('.zip')) {
    const inner = await unzipRom(bytes);
    if (!inner) return { name, verdict: { status: 'unsupported', reason: 'empty-zip' } };
    ({ name, bytes } = inner);
  }
  const header = readHeader(bytes);
  const sha1 = await sha1Hex(bytes);
  const info = { ...header, size: bytes.length, sha1 };
  return { name, ...info, verdict: verdict(data, info) };
}
