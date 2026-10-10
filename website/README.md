# PokeDaisy website

A static [Astro](https://astro.build) site: the home page and the ROM check. Node 22.12+ (`nvm use`).

```sh
npm install
npm run dev      # http://localhost:4321
npm test         # the ROM check's rules (node:test)
npm run build    # -> dist/
npm run cover    # re-renders public/og.png (the social cover) from src/pages/cover.astro with headless Chrome
```

The look: a light, modern page with every border, button and icon in whole pixels and every word in
Pixel Operator. Sprites (the PokeDaisy daisy-ball mark included) are character grids in `src/lib/pixel.js`, drawn
as SVG by `Pixel.astro`; window frames are pixel 9-slice SVGs (`frameSvg`) used through `.f-<name>` classes.

The PARTY / BATTLE demos (`PartyDemo.astro`, `BattleDemo.astro` on `AppScreen.astro`) redraw the app's own screens in
HTML at its layout and colours (PartyScreen.kt's FireRedPartyPalette, BattleControlsScreen.kt's buttons, BattleInfoScreen.kt's
pills) and play themselves (`src/lib/demo.js`). No game art: a type's emblem stands in for each Pokémon's icon. Their CSS
is scoped under `.app` - global class names like `.bar` once collided with the site's own.

## ROM check

`src/pages/compatibility.astro` reads a dropped ROM (or the ROM inside a `.zip`) **in the browser** —
nothing is uploaded — and judges it with `src/lib/compat.js`, the same rules as the app's
`CompanionSupport.isSupported`, over `src/data/compat.json`.

`compat.json` is generated from the app's own tables (`TelemetrySampler.SUPPORTED_HACK_SHA1S`,
`otherRetailConfig`, `GameTitles.BY_SHA1`, ...) by `app/src/test/.../SiteDataExportTest.kt`, which fails
while the checked-in file is stale. After adding a game to the app:

```sh
UPDATE_SITE_DATA=1 ./gradlew :app:testDebugUnitTest --tests '*SiteDataExportTest'
```

## Deploy

Firebase Hosting, project `pokedaisy` → https://pokedaisy.web.app (`firebase.json`, `.firebaserc`).
`.github/workflows/website.yml` deploys the live site only when an app version is released: publishing a
GitHub release (not a pre-release) builds and deploys `website/` as it is at that release's tag, so the game
table and ROM check always match the APK people can download. Pushes to `main` only test and build. Pull
requests from this repo get a temporary preview channel, linked in a PR comment. To redeploy or roll back,
run the workflow by hand (Actions > Website > Run workflow) with a release tag, e.g. `v1.1.4`. It needs
the repo secret `FIREBASE_SERVICE_ACCOUNT_POKEDAISY`: a service account JSON key with the
*Firebase Hosting Admin*, *API Keys Viewer* and *Cloud Run Viewer* roles.

By hand (this skips the release rule - deploy from a release tag's checkout): `npm run build && npx firebase-tools deploy --only hosting` (or `hosting:channel:deploy <name>` for a preview).

## Feedback form (no GitHub account)

`/feedback/` (`src/pages/feedback.astro`) lets anyone request a game, report a bug or suggest a feature. The
fields, limits and the GitHub issue's layout live in one file, `functions/feedback-form.js`, which mirrors
`.github/ISSUE_TEMPLATE/*.yml`; `firestore.rules` holds the same limits (`test/feedback.test.js` fails if they
drift). A submission is written straight to Firestore's `feedback` collection (write-only: nobody can read one
back through the API) and the Cloud Function in `functions/index.js` posts it as a GitHub issue, then notes the
issue number on the document. The optional contact (email / Discord) stays in Firestore; it's never on the issue.
A submission with more than 3 links is held (`status: 'held'`) for a look in the Firebase console.

One-time setup (none of it goes through the website workflow, which deploys hosting only):

1. Firebase console > Firestore: create the database if it isn't there yet.
2. Switch the project to the **Blaze** plan (Cloud Functions and Secret Manager need it; this traffic stays
   within the free tier).
3. Make a GitHub **fine-grained token**: repository access *only* `lidor30/pokedaisy`, permission
   *Issues: Read and write*, nothing else. Then, from `website/`:
   `npx firebase-tools functions:secrets:set GITHUB_TOKEN` (paste the token).
4. Deploy the rules and the function: `npx firebase-tools deploy --only firestore:rules,functions`
   (redo after changing `firestore.rules` or `functions/`).

Without step 2-4's function, submissions still land in Firestore (rules deployed) - read them in the console.
Rotate the token by setting the secret again and redeploying the function. Test the rules locally with
`npx firebase-tools emulators:exec --only firestore "<script>"` (no project needed).
