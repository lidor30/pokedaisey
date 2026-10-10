// Turns each website feedback submission (Firestore `feedback/{id}`, written by src/pages/feedback.astro
// under firestore.rules) into a GitHub issue on lidor30/pokedaisy, in the matching issue template's
// layout and labels. The submission is checked again here (the rules can't read every limit), one with
// lots of links is held back as likely spam, and the result is written onto the document: `issue`
// (number + url) or `status` ('held' / 'invalid' / 'failed'). The sender's contact never leaves Firestore.
//
// Needs the Blaze plan and a fine-grained GitHub token with Issues: read and write on that one repository:
//   firebase functions:secrets:set GITHUB_TOKEN
//   firebase deploy --only functions,firestore:rules
import { onDocumentCreated } from 'firebase-functions/v2/firestore';
import { defineSecret } from 'firebase-functions/params';
import { logger } from 'firebase-functions';
import { initializeApp } from 'firebase-admin/app';
import { FieldValue } from 'firebase-admin/firestore';
import { clean, validate, linkCount, toIssue } from './feedback-form.js';

initializeApp();

const GITHUB_TOKEN = defineSecret('GITHUB_TOKEN');
const REPO = 'lidor30/pokedaisy';
/** More links than this in one submission: held for a look in the console instead of posted. */
const MAX_LINKS = 3;

export const feedbackToGithub = onDocumentCreated(
  { document: 'feedback/{id}', secrets: [GITHUB_TOKEN], region: 'us-central1', maxInstances: 2 },
  async (event) => {
    const snap = event.data;
    if (!snap) return;
    const data = snap.data();
    const sub = clean(data.kind, data.values ?? {});
    const done = (fields) => snap.ref.update({ ...fields, processedAt: FieldValue.serverTimestamp() });
    if (!sub || Object.keys(validate({ ...sub, contact: '' })).length) return done({ status: 'invalid' });
    if (linkCount(sub) > MAX_LINKS) return done({ status: 'held' });
    const issue = toIssue(sub);
    try {
      const res = await fetch(`https://api.github.com/repos/${REPO}/issues`, {
        method: 'POST',
        headers: {
          Authorization: `Bearer ${GITHUB_TOKEN.value()}`,
          Accept: 'application/vnd.github+json',
          'X-GitHub-Api-Version': '2022-11-28',
          'User-Agent': 'pokedaisy-feedback',
        },
        body: JSON.stringify(issue),
      });
      if (!res.ok) throw new Error(`GitHub ${res.status}: ${(await res.text()).slice(0, 300)}`);
      const created = await res.json();
      return done({ issue: { number: created.number, url: created.html_url } });
    } catch (e) {
      logger.error('feedback: issue not created', { id: event.params.id, error: String(e) });
      return done({ status: 'failed' });
    }
  },
);
