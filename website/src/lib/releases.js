// GitHub releases, read once at build time (the deploy workflow passes GITHUB_TOKEN and also
// rebuilds when a release is published). Offline, the site builds without them.
const REPO = 'lidor30/pokedaisy';
export const REPO_URL = `https://github.com/${REPO}`;

let cached;

export async function getReleases() {
  if (cached) return cached;
  try {
    const headers = { Accept: 'application/vnd.github+json', 'User-Agent': 'pokedaisy-website' };
    if (process.env.GITHUB_TOKEN) headers.Authorization = `Bearer ${process.env.GITHUB_TOKEN}`;
    const res = await fetch(`https://api.github.com/repos/${REPO}/releases?per_page=50`, { headers });
    if (!res.ok) throw new Error(`HTTP ${res.status}`);
    const list = await res.json();
    cached = list
      .filter((r) => !r.draft && !r.prerelease)
      .map((r) => ({
        tag: r.tag_name,
        version: r.tag_name.replace(/^v/, ''),
        name: r.name || r.tag_name,
        date: r.published_at,
        url: r.html_url,
        body: r.body ?? '',
        apk: r.assets.find((a) => a.name.endsWith('.apk'))?.browser_download_url,
      }));
  } catch (e) {
    console.warn(`[releases] couldn't read GitHub releases: ${e.message}`);
    cached = [];
  }
  return cached;
}

export function formatDate(iso) {
  return new Date(iso).toLocaleDateString('en-GB', { day: 'numeric', month: 'short', year: 'numeric' }).toUpperCase();
}

const escape = (s) => s.replace(/&/g, '&amp;').replace(/</g, '&lt;').replace(/>/g, '&gt;');

function inline(s) {
  return escape(s)
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/\[([^\]]+)\]\((https?:[^)\s]+)\)/g, '<a href="$2">$1</a>');
}

/** The release notes' markdown: ## headings, - bullets, **bold**, `code`, links. */
export function markdown(md) {
  let html = '';
  let inList = false;
  for (const raw of md.replace(/\r/g, '').split('\n')) {
    const line = raw.trimEnd();
    const bullet = line.match(/^\s*[-*] (.*)/);
    if (!bullet && inList) { html += '</ul>'; inList = false; }
    if (bullet) {
      if (!inList) { html += '<ul>'; inList = true; }
      html += `<li>${inline(bullet[1])}</li>`;
    } else if (/^#{1,6} /.test(line)) {
      html += `<h4>${inline(line.replace(/^#+ /, ''))}</h4>`;
    } else if (line.trim()) {
      html += `<p>${inline(line)}</p>`;
    }
  }
  if (inList) html += '</ul>';
  return html;
}

/** The first bullets under a release's "New" heading (or its first bullets), for the home page. */
export function highlights(md, n = 4) {
  const lines = md.replace(/\r/g, '').split('\n');
  const from = lines.findIndex((l) => /^#+ New/i.test(l));
  const bullets = lines.slice(from + 1).filter((l) => /^\s*[-*] /.test(l)).map((l) => l.replace(/^\s*[-*] /, ''));
  return bullets.slice(0, n).map(inline);
}
