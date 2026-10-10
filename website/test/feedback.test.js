// The feedback form (functions/feedback-form.js): what the page sends, what the Cloud Function posts,
// and that firestore.rules accepts exactly the form's fields and limits.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { FORMS, DEVICES, clean, validate, toFirestore, toIssue, linkCount } from '../functions/feedback-form.js';

const SHA1 = '41cb23d8dccc8ebd7c649cd8fbb58eeace6e2fdc';

test('a game request: trimmed, lower-cased SHA-1, blanks dropped', () => {
  const sub = clean('game', { game: '  Pokémon Gaia v3.2 ', sha1: SHA1.toUpperCase(), size: '', notes: '\r\nhi\r\n' });
  assert.deepEqual(sub, { kind: 'game', values: { game: 'Pokémon Gaia v3.2', sha1: SHA1, notes: 'hi' }, contact: '' });
  assert.deepEqual(validate(sub), {});
});

test('required fields, limits and formats', () => {
  assert.deepEqual(Object.keys(validate(clean('game', {}))).sort(), ['game', 'sha1']);
  assert.equal(validate(clean('game', { game: 'x', sha1: 'abc' })).sha1, '40 hex digits - the ROM check shows it.');
  assert.ok(validate(clean('game', { game: 'x', sha1: SHA1, size: '16 MB' })).size);
  assert.ok(validate(clean('bug', { app_version: '1.2.0', device: 'A toaster', game: 'x', what_happened: 'y' })).device);
  assert.deepEqual(validate(clean('bug', { app_version: '1.2.0', device: DEVICES[0], game: 'x', what_happened: 'y' })), {});
  assert.ok(validate(clean('feature', { idea: 'x'.repeat(4001) })).idea);
  assert.ok(validate(clean('feature', { idea: 'x', contact: 'y'.repeat(201) })).contact);
  assert.equal(clean('nonsense', {}), null);
  assert.ok(validate(null).kind);
});

test('only the kind\'s own fields are sent', () => {
  const sub = clean('feature', { idea: 'Dark mode', game: 'FireRed', sha1: SHA1, contact: 'me@example.com' });
  assert.deepEqual(toFirestore(sub), {
    fields: {
      kind: { stringValue: 'feature' },
      values: { mapValue: { fields: { idea: { stringValue: 'Dark mode' }, game: { stringValue: 'FireRed' } } } },
      contact: { stringValue: 'me@example.com' },
    },
  });
});

test('the issue follows the template, and keeps the contact off it', () => {
  const sub = clean('bug', {
    app_version: '1.2.0', device: 'AYN Thor', game: 'Emerald', what_happened: 'The map is blank\nafter a battle',
    logs: 'E/x: ```boom```', contact: 'secret@example.com',
  });
  const issue = toIssue(sub);
  assert.equal(issue.title, 'Bug: The map is blank');
  assert.deepEqual(issue.labels, ['bug']);
  assert.match(issue.body, /^### PokeDaisy version\n\n1\.2\.0\n\n### Device\n\nAYN Thor/);
  assert.match(issue.body, /### How to make it happen\n\n_No response_/);
  assert.match(issue.body, /```shell\nE\/x: '''boom'''\n```/);
  assert.doesNotMatch(issue.body, /secret@example\.com/);
  assert.equal(toIssue(clean('game', { game: 'Gaia v3.2', sha1: SHA1 })).title, 'ROM support: Gaia v3.2');
  assert.equal(toIssue(clean('feature', { idea: 'x'.repeat(100) })).title.length, 'Idea: '.length + 70);
});

test('links are counted (the function holds back a link-stuffed one)', () => {
  assert.equal(linkCount(clean('feature', { idea: 'see https://a.example and http://b.example' })), 2);
});

test('firestore.rules accepts exactly the form\'s fields and limits', () => {
  const rules = readFileSync(new URL('../firestore.rules', import.meta.url), 'utf8');
  for (const [kind, form] of Object.entries(FORMS)) {
    const block = rules.slice(rules.indexOf(`kind == '${kind}'`));
    const keys = block.match(/hasOnly\(\[([^\]]*)\]\)/)[1].match(/'([a-z_0-9]+)'/g).map((k) => k.slice(1, -1));
    assert.deepEqual(keys, form.fields.map((f) => f.id), `${kind}: the rules' fields`);
    for (const f of form.fields) {
      const limit = block.match(new RegExp(`(?:needed|text)\\(v, '${f.id}', (\\d+)\\)`));
      if (limit) assert.equal(Number(limit[1]), f.max, `${kind}.${f.id}: the rules' limit`);
      const needed = new RegExp(`needed\\(v, '${f.id}'|'${f.id}' in v && v\\.${f.id}|'${f.id}' in v && v\\['${f.id}'\\]`).test(block.split('||')[0] + block.split('|| (kind')[0]);
      if (f.required) assert.ok(needed, `${kind}.${f.id}: required in the rules too`);
    }
  }
  for (const d of DEVICES) assert.ok(rules.includes(`'${d}'`), `device ${d} in the rules`);
  assert.match(rules, /contact\.size\(\) <= 200/);
});
