// The website's feedback form (src/pages/feedback.astro) for players without a GitHub account: a game
// request, a bug report or a feature idea. One definition, shared by the page (fields, checks, the
// Firestore write) and the Cloud Function (index.js) that turns a submission into a GitHub issue - so
// they can't drift apart. Field ids and labels mirror .github/ISSUE_TEMPLATE/*.yml, and the limits here
// are firestore.rules' limits: change the three together.

export const DEVICES = ['AYN Thor', 'Retroid Pocket Duo', 'Another dual-screen device', 'A single-screen device'];

/** [id, label, kind ('input' | 'textarea' | 'select'), max length, required, hint]. */
const field = (id, label, type, max, required = false, hint = '', placeholder = '') => ({ id, label, type, max, required, hint, placeholder });

export const FORMS = {
  game: {
    title: 'Request a game',
    issueTitle: (v) => `ROM support: ${v.game}`,
    labels: ['enhancement'],
    fields: [
      field('game', 'Game and version', 'input', 120, true, '', 'Pokémon Unbound v2.1.1.1'),
      field('official_page', 'Official page', 'input', 300, false, 'Where the hack is published (its PokéCommunity thread, website, Discord...). Never a ROM.'),
      field('game_code', 'Game code', 'input', 20, false, '', 'BPRE (rev 0)'),
      field('size', 'Size (bytes)', 'input', 12),
      field('sha1', 'SHA-1', 'input', 40, true, 'The ROM check fills this in - drop your ROM there first.'),
      field('notes', 'Anything else', 'textarea', 4000),
    ],
  },
  bug: {
    title: 'Report a bug',
    issueTitle: (v) => `Bug: ${firstLine(v.what_happened, 70)}`,
    labels: ['bug'],
    fields: [
      field('app_version', 'PokeDaisy version', 'input', 20, true, 'Top-screen Settings > VERSION.', '1.2.0'),
      field('device', 'Device', 'select', 40, true),
      field('device_model', 'Device model and Android version', 'input', 120, false, '', 'Retroid Pocket 6, Android 13'),
      field('game', 'Game', 'input', 120, true, 'The game and its version (the ROM check shows both).', 'Pokémon Unbound v2.1.1.1'),
      field('what_happened', 'What happened?', 'textarea', 4000, true, 'What you saw, and what you expected instead.'),
      field('steps', 'How to make it happen', 'textarea', 4000, false, '', '1. Open the PARTY tab in a trainer battle\n2. ...'),
      field('logs', 'Logs', 'textarea', 20000, false, 'Optional - `adb logcat -s pokedaisy` output if you have it.'),
    ],
  },
  feature: {
    title: 'Suggest a feature',
    issueTitle: (v) => `Idea: ${firstLine(v.idea, 70)}`,
    labels: ['enhancement'],
    fields: [
      field('idea', 'What would you like?', 'textarea', 4000, true, 'What it would do, and when you\'d use it.'),
      field('game', 'Game', 'input', 120, false, 'If it\'s about one game in particular.'),
    ],
  },
};

/** An optional way to reach the sender. Kept in Firestore only - never put on the public issue. */
export const CONTACT_MAX = 200;

const SHA1 = /^[0-9a-f]{40}$/;

function firstLine(text, max) {
  const line = String(text ?? '').trim().split('\n')[0].trim();
  return line.length > max ? `${line.slice(0, max - 1)}…` : line;
}

/** The submission's values, trimmed: kind, the kind's fields (blank ones dropped) and the contact. */
export function clean(kind, raw) {
  const form = FORMS[kind];
  if (!form) return null;
  const values = {};
  for (const f of form.fields) {
    let v = String(raw[f.id] ?? '').replace(/\r\n/g, '\n').trim();
    if (f.id === 'sha1') v = v.toLowerCase();
    if (v) values[f.id] = v;
  }
  const contact = String(raw.contact ?? '').trim();
  return { kind, values, contact };
}

/** Problems with a [clean]ed submission, by field id (empty = fine to send). */
export function validate(sub) {
  const errors = {};
  const form = sub && FORMS[sub.kind];
  if (!form) return { kind: 'Pick what you\'re sending.' };
  for (const f of form.fields) {
    const v = sub.values[f.id];
    if (!v) { if (f.required) errors[f.id] = 'Needed.'; continue; }
    if (v.length > f.max) errors[f.id] = `At most ${f.max} characters.`;
    else if (f.id === 'sha1' && !SHA1.test(v)) errors[f.id] = '40 hex digits - the ROM check shows it.';
    else if (f.id === 'size' && !/^[0-9]{1,12}$/.test(v)) errors[f.id] = 'A number of bytes.';
    else if (f.id === 'device' && !DEVICES.includes(v)) errors[f.id] = 'Pick one.';
  }
  if (sub.contact.length > CONTACT_MAX) errors.contact = `At most ${CONTACT_MAX} characters.`;
  return errors;
}

/** The Firestore REST document for a valid submission: `{ fields: { kind, contact?, values: {map} } }`. */
export function toFirestore(sub) {
  const values = Object.fromEntries(Object.entries(sub.values).map(([k, v]) => [k, { stringValue: v }]));
  const fields = { kind: { stringValue: sub.kind }, values: { mapValue: { fields: values } } };
  if (sub.contact) fields.contact = { stringValue: sub.contact };
  return { fields };
}

/** Links in the text: a submission full of them is held back (spam) instead of becoming an issue. */
export function linkCount(sub) {
  return Object.values(sub.values).join('\n').match(/https?:\/\//g)?.length ?? 0;
}

/** The GitHub issue for a submission: the issue form's own layout (### Label, then the answer). */
export function toIssue(sub) {
  const form = FORMS[sub.kind];
  const body = form.fields.map((f) => {
    const v = sub.values[f.id];
    const answer = !v ? '_No response_' : f.id === 'logs' ? `\`\`\`shell\n${v.replace(/```/g, "'''")}\n\`\`\`` : v;
    return `### ${f.label}\n\n${answer}`;
  });
  body.push('---\n_Sent from the website\'s form (no GitHub account)._');
  return { title: form.issueTitle(sub.values), body: body.join('\n\n'), labels: form.labels };
}
