# QA log

One section per QA pass, newest last. A pass is one agent run through part of `tools/qa/CHECKLIST.md` on the seeded
emulator. How to run one: `tools/qa/README.md`. Append only; fix status is updated in place on the finding.

## Format

Each pass starts with a heading and a header block:

```
## Pass <n>: <areas covered>
Date: YYYY-MM-DD · Build: <git short hash> debug · AVD: phone30|phone35 · Seed: tools/qa/seed.sh (date of data)
Covered: <checklist sections and line numbers visited>
Not covered: <what was skipped and why>
Logcat: <crashes, ANRs, StrictMode lines seen, or "clean">
```

Each finding is a block:

```
### Q<pass>-<n> · S<1-4> · <area> · <one-line title>
Status: open | fixed in <hash> | wontfix (<why>) | cannot reproduce
Steps: 1. ... 2. ... 3. ...
Expected: ...
Actual: ...
Screenshot: design/qa/<file>.png
Suspect: <path/File.kt, if obvious; otherwise "unknown">
```

Rules:
- **ID**: `Q<pass>-<n>`, n counts from 1 within the pass and is never reused. A retest of a finding keeps its ID.
- **Severity**
  - **S1**: crash, ANR that is the app's fault, data loss, or an unsafe allergen result (a dairy food shown as
    dairy-free or clear, a warning cleared by a model or an import).
  - **S2**: broken function or wrong numbers (totals that do not add up, wrong units, a button that does nothing).
  - **S3**: layout or copy (clipping, overlap, wrong ripple, bad contrast, stale or inaccurate text).
  - **S4**: polish (spacing, animation feel, nicer empty state).
- **Area**: one of Shell, Today, Nutrition, Drinks, Recipes, Eating out, Allergens, Train, Programs, Progress, You,
  Settings, Backup, Wear, Other. Add the checklist line, for example `Nutrition C.23`.
- **Steps** must be reproducible from a seeded install with no setup beyond what is written.
- **Expected and actual** are one or two lines each; quote the text seen on screen.
- **Screenshot** is a path under `design/qa/`, named `q<pass>-<n>-<slug>.png`. An S1 or S2 without one needs a reason.
- **Suspect** is a hint, not a diagnosis. Say "unknown" if you did not look.
- Say what was seen on the emulator and what has not been seen on a device. Never claim more.

A pass ends with a summary line: counts per severity, and whether a retest is needed after fixes. The loop is test, fix,
retest; stop when a full pass over every area logs no S1, S2 or S3 finding.

---

(No passes yet. The first pass appends below this line.)
