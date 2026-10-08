// Renders the built /cover/ page (src/pages/cover.astro) to public/og.png with headless Chrome:
// the site's social image (og:image). Run `npm run cover` after changing the logo or the cover.
import { spawn } from 'node:child_process';
import { mkdtempSync, rmSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { createServer } from 'node:http';
import { readFile } from 'node:fs/promises';
import { existsSync } from 'node:fs';
import { extname, join, resolve } from 'node:path';

const dist = resolve('dist');
const out = resolve('public/og.png');
const CHROME = process.env.CHROME ?? [
  '/Applications/Google Chrome.app/Contents/MacOS/Google Chrome',
  '/usr/bin/google-chrome',
  '/usr/bin/chromium',
].find(existsSync);
if (!CHROME) throw new Error('no Chrome found - set CHROME=/path/to/chrome');

const TYPES = { '.html': 'text/html', '.css': 'text/css', '.js': 'text/javascript', '.png': 'image/png', '.webp': 'image/webp', '.ttf': 'font/ttf', '.svg': 'image/svg+xml' };
const server = createServer(async (req, res) => {
  let path = join(dist, decodeURIComponent(new URL(req.url, 'http://x').pathname));
  if (path.endsWith('/')) path = join(path, 'index.html');
  let body;
  try {
    body = await readFile(path);
  } catch {
    res.writeHead(404).end();
    return;
  }
  res.writeHead(200, { 'Content-Type': TYPES[extname(path)] ?? 'application/octet-stream' }).end(body);
}).listen(0);
const { port } = server.address();

// Async, so this process's server can answer Chrome while it renders; a throwaway profile, so a
// Chrome that's already open doesn't take the job over.
const profile = mkdtempSync(join(tmpdir(), 'pokedaisy-cover-'));
try {
  rmSync(out, { force: true });
  const chrome = spawn(CHROME, [
    '--headless=new', '--disable-gpu', '--hide-scrollbars', '--no-first-run', `--user-data-dir=${profile}`,
    '--force-device-scale-factor=1', '--window-size=1280,640', '--virtual-time-budget=4000',
    `--screenshot=${out}`, `http://localhost:${port}/cover/`,
  ], { stdio: 'ignore' });
  // Chrome on macOS can stay up after writing the shot: wait for the file, then close it.
  const started = Date.now();
  while (!existsSync(out)) {
    if (Date.now() - started > 60000) { chrome.kill(); throw new Error('Chrome timed out'); }
    await new Promise((r) => setTimeout(r, 250));
  }
  await new Promise((r) => setTimeout(r, 500));
  chrome.kill();
  console.log(`wrote ${out}`);
} finally {
  server.close();
  try {
    rmSync(profile, { recursive: true, force: true, maxRetries: 5, retryDelay: 200 });
  } catch {
    // Chrome may still be closing; the OS clears its temp dir.
  }
}
