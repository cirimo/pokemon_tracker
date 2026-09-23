# Prompt 3a — Every form gets its own sprite

> Run this after M2, before M3. Fresh Claude Code session at the repo root, in **plan mode**.
> Read `CLAUDE.md`, `docs/dataset-pipeline.md`, `docs/adr/0003-dataset-pipeline.md` and
> **`docs/adr/0005-sprites.md`** first. This session is dataset work. It touches the
> pipeline, the curated layer and the checked-in assets, and **no app code at all**.

---

You maintain the dataset pipeline for a **shiny living dex tracker**: an Android app for one
person (me) that mirrors my Pokémon HOME boxes. The app ships every shiny sprite in the APK
and makes no network calls at runtime. Fetching belongs to `tools/dataset-pipeline`, which
runs on a desktop and whose output is checked in.

## What is wrong

M2 put the box grid on screen, and the Unown Dex box showed 28 identical silhouettes.
**364 of the 1387 bundled sprites are byte-for-byte copies of another sprite.** Every
cosmetic form got its base form's art:

| Group | Identical files |
|---|---|
| Alcremie (`0869-*`) | 63 |
| Unown (`0201-*`) | 28 |
| Vivillon (`0666-*`) | 20 |
| Furfrou (`0676-*`) | 10 |
| Minior (`0774-*`) | 6 |
| Flabébé / Floette / Florges (`0669/0670/0671-*`) | 5–6 each |
| Deerling / Sawsbuck seasons (`0585/0586-*`) | 4 each |
| Burmy (`0412-*`) | 3 |
| Many two-file groups, mostly `-f` gender forms (e.g. `0112-f` = `0112`) | 2 each |

Reproduce it before you believe it:

```bash
cd core/data/src/main/assets/sprites
md5sum *.webp | awk '{print $1}' | sort | uniq -c | sort -rn | awk '$1>1'
```

In the app this is not cosmetic. Uncaught slots are drawn as silhouettes, so whole form
boxes (Unown Dex, Vivillon Patt., Alcremie 1–3, Trims & Flowers) read as rows of the same
shape. Those are exactly the boxes where telling forms apart matters most.

### Why it happened

`tools/dataset-pipeline/src/build-sprites.ts` resolves PokéAPI sprites by `refs.pkApiId`
first and `refs.pkApiFormId` second. ADR 0005 records that order as the fix for "the id
trap": form ids resolved only 1259/1387, pokemon ids resolved 1387/1387. But for a cosmetic
form the pokemon id **is the base species**, so it always resolves, just to the wrong
picture. "1387/1387 resolved" measured whether a file came back, not whether it was the
right file. Treat that as the lesson: **measure correctness, not coverage.**

Also note the builder **skips any sprite file that already exists**. A regenerated run
will not fix anything unless the affected files are deleted or refetch is forced.

### A second, smaller bug in the same place

Four Unown forms, `unown-n`, `unown-o`, `unown-u` and `unown-w`, have `displayName`
`"Unown"` where every other letter has `"Unown (B)"` and so on. Search shows four rows called
plain "Unown". Find where the pipeline builds `displayName` (`build-reference.ts`,
`upstream.ts`). The exact letters N, O, U, W suggest something is being read as a word or a
flag rather than a letter. Fix it at the source, not with a curated override.

```bash
python -c "import sqlite3;print(sqlite3.connect('core/data/src/main/assets/dataset/reference.db').execute(\"select id, displayName from variant where dexNum=201\").fetchall())"
```

## Research first, then stop

Before changing anything, measure, the way ADR 0005 did:

1. **For each affected variant, which sources have distinct shiny art?** Start with
   PokéAPI's own `other/home/shiny` set keyed by the *form's* pokemon id (the `10xxx` ids),
   then other PokéAPI sets, then any other source you find. Report coverage per group,
   verified by **comparing image bytes, not by HTTP 200**.
2. **For the gender forms,** say which pairs genuinely have identical art in HOME and which
   only look identical because we fetched the base. Some species really do look the same;
   the data must say so explicitly rather than by accident.
3. **Licensing.** ADR 0005 states plainly what bundling PokéAPI's images means. Any new
   source gets the same honest reading, written into the ADR.
4. **Size.** The file count does not change, so the APK should stay near 27 MB against a
   30 MB budget. Confirm it.

Then **stop and report** with a recommendation for anything genuinely close, for example
"Alcremie's 63 sweets have distinct art only in source X, whose licence is Y". I will have an
opinion on which sources are acceptable.

## Constraints

- **No runtime network, ever.** Fetching happens here, on a desktop. The app gains no
  permission and no dependency.
- **`variant.spriteFile` does not change.** Names are keyed on upstream `nid`, and the app
  and any catch record neither know nor care where the bytes came from. No Room schema
  change, no migration.
- **Sprites are in Git LFS** (`.gitattributes`). Commit them the way they are committed
  today.
- **The curated layer is `data/curated/*.yaml`,** hand-edited, with the source cited in a
  comment beside every row it justifies. That is where any allow-list lives.
- **If a validator fails, the dataset is wrong. Fix the data, not the validator.**

## Deliverables

1. **Resolution that picks the form's own art** where a source has it, with a fallback
   that is explicit rather than silent. The comment and the ADR explain the order and why.
2. **A new validator: no two variants may share sprite bytes,** unless the pair is
   allow-listed in the curated layer with a citation that says the art really is identical.
   It must fail the build today and pass after your fix. That is how we know it would have
   caught this.
3. **The Unown `displayName` fix,** with a pipeline test.
4. **Pipeline tests** (`npm test` in `tools/dataset-pipeline`) for the resolution order and
   the new validator.
5. **Regenerated assets.** Follow `docs/dataset-pipeline.md`. The content manifest will
   change, because the `variant` table changes. CI checks the manifest, not the bytes (ADR
   0003, amended 2026-09-23), so commit the `.db` and the manifest together.
6. **ADR 0005 amended**, dated. The "id trap" section currently says the opposite of what
   is true. Keep what was learned and correct the conclusion.

Commit order, one logical change each:

1. Pipeline change and tests.
2. Sprite regeneration.
3. Dataset regeneration.
4. Docs.

Every commit builds: `./gradlew test detekt lintDebug :design-system:designCheck assembleDebug`.

## Before you say it works

Install the debug build on the emulator or my phone and **look at** the Unown Dex,
Vivillon Patt., Alcremie 1–3 and Trims & Flowers boxes. Look at them uncaught, as
silhouettes, and after marking a few caught, in colour. Some Alcremie sweets may be nearly
identical in silhouette; that is fine if the colour versions differ. Tell me what you saw,
not what the validator said.
