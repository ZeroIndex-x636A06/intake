# Roadmap: Allergen-aware cooking guide

Status: **design note only. Nothing is built.** Raised in phone feedback round 5 (`docs/FEEDBACK-R5.md`, item 8). The owner
said: "This will need to be VERY WELL dialed in and will likely need to use AI. This is a feature that MUST work correctly."

That sentence is the requirement. If the feature cannot be made to work correctly, it does not ship. A guide that is right
most of the time is worse than no guide, because the person will trust it with their health.

## Goals

Given a dish or a recipe, say how to make it safely for the user's avoided allergens.

- Input: a recipe in My recipes, a Recipe Hunter result, a link import, or just a dish name ("chicken alfredo").
- Output: a plan the person can cook from. For each ingredient or step that carries an avoided allergen: what to use
  instead, how much, what changes in the method (cooking time, a binder, a thickener), what the dish will taste or behave
  like, and what to check on the pack.
- It builds on what exists. `domain/allergen/Substitutions.kt` already adapts recipes where the allergen is a small,
  swappable part, and refuses where it is the dish (`docs/ALLERGENS.md`, "Adapting a recipe"). The guide widens that:
  more dishes, method changes, and advice for dishes the engine refuses, which includes saying plainly that a dish cannot
  be made without its allergen.
- It is advice for cooking, not a diagnosis, not medical guidance, and not a promise. The wording says so.

Non-goals: replacing reading the label; certifying a kitchen or a product; advice for allergens the person has not
set up; anything that runs without a tap.

## Non-negotiable safety rules

These extend the project rules in `CLAUDE.md` and `docs/ALLERGENS.md`. A change that breaks one is a bug, whatever else it does.

1. **Local deterministic scanning has the final say.** The same local scanner that guards every other screen decides
   whether anything in the guide is allowed to be shown as safe. No score, model confidence or user setting overrides it.
2. **AI may only propose.** A model can suggest a substitute or a method change. It cannot declare anything safe, cannot
   clear a warning, cannot lower a warning's level, and its output is never stored as a verdict.
3. **Every proposed ingredient is re-scanned locally.** Each substitute, each added ingredient and the whole adapted
   ingredient list go back through the allergen scanner (strict rules, "check the label" tier included) before they are
   shown. A proposal that scans as containing an avoided allergen is dropped, and the drop is logged in the evaluation
   harness. This includes hidden forms (a "dairy-free" cheese made with casein, tamari when soy is avoided, oat milk
   when gluten is avoided, a stock cube with milk powder).
4. **Cross-contact warnings.** Even a safe ingredient list can be unsafe if the pan, board, fryer oil, grater or
   blender touched the allergen. The guide always lists the cross-contact points for the method (shared fryer, toaster,
   wooden spoons, a pasta water pot, a grill) and the "may contain" and "made in a facility" wording on packs. It states
   that the person's own allergy severity decides how much that matters.
5. **Never clear a warning.** The original recipe keeps its warning. A plant-based or labelled-free substitute is at best
   "check the label", never "clear" (the same rule as the adapted copy today). The guide's output is a suggestion plus
   a list of checks, not a replacement verdict.
6. **Label checks on substitutes.** Every substitute carries "check the label for X" with the specific things to look for
   on that product category (what a vegan butter, a gluten-free flour blend, a nut-free pesto, a dairy-free chocolate
   may hide). Packaged substitutes are never described as safe because of their category.
7. **Refuse rather than guess.** When the system cannot place an ingredient, cannot verify a proposal, or the dish is
   defined by the allergen, it says so. See "Clear refusal states" below.
8. **No AI without a tap, and not for the verdict.** AI runs only on an explicit user action, through `AiGateway`
   (`docs/SPEC-v3.md`, "AI policy"). The app works with no key. Without AI the guide still gives everything the
   deterministic engine can.
9. **Sending data:** only the dish or recipe text and the names of the avoided allergens go to the model, under the user's
   AI privacy setting. Nothing from the log or body data (`docs/PRIVACY.md`).

## The pipeline

Four stages. A recipe or a dish name goes in at the top; a guide, a confidence level or a refusal comes out.

1. **Deterministic first.** Run the existing `Substitutions` engine on the recipe's ingredient lines and title.
   - Lines it places: swap with its named substitute, quantity limits, confidence (High, Medium, Borderline), caveats.
   - Lines or whole dishes it refuses (defining dish, main ingredient, baking structure, count cap): record the reason.
   - Lines it cannot place (a flagged line with no swap class, a warning no line explains): hand them on as "unplaced".
   - Nothing here is AI, so a result with no unplaced lines is complete and needs no model.
2. **AI proposals for what it cannot place.** Only when the person taps, only for the unplaced lines and refused
   method questions. The model gets the dish, the unplaced lines, the allergens to avoid and the engine's own swaps (so it
   does not contradict them). It returns structured proposals: substitute, amount, method change, texture note, and the
   allergens it believes each substitute contains. It is told that it is not the authority.
3. **Local verification pass.** For every proposal:
   - re-scan the substitute and the full adapted ingredient list with the strict local scanner;
   - reject any proposal the scanner flags as containing an avoided allergen, and any whose claimed allergens (the
     model's own list) include one, because the model can only add warnings;
   - run the quantity and role checks from `Substitutions` on the proposed amounts;
   - attach the label checks (rule 6) and cross-contact points (rule 4) from fixed, human-written tables, not from the model;
   - mark anything the scanner could only place as "check the label".
4. **Confidence and refusal.** A guide gets one of the levels below, computed from the verification pass and not
   from what the model says about itself.
   - **Confident:** every flagged line was placed by the deterministic engine or by a verified proposal, all with the
     engine's High confidence (or a model proposal that is a close match to a known swap class), no borderline quantities.
   - **Worth trying:** placed, but some swaps are Medium or Borderline, or change the dish noticeably. Says what changes.
   - **Not reliable:** at least one part rests on an AI proposal with no matching swap class. Shown with the checks first, and
     with no "save adapted copy" button until the person has read them.
   - **Refused:** see below.

## Evaluation plan

The model's output cannot be trusted by reading a few examples. It is measured, on a fixed set, before release and again
after any change to the prompts, the model choice or the scanner.

- **The set.** A fixed list of dishes per allergen the app supports (milk, egg, wheat and gluten, soy, peanut, tree nut,
  sesame, fish, shellfish, and the multi-allergen cases people actually have, such as milk plus egg and wheat plus soy).
  Per allergen, about 30 dishes in four groups:
  - allergen is a small part (a cheese topping, a butter glaze), where a swap should work;
  - allergen is a main but replaceable part (cream sauce, a flour roux), where a swap should work with caveats;
  - allergen is the dish (cheesecake, mayonnaise, tempura, peanut satay), where the right answer is a refusal or a
    different dish;
  - traps: hidden forms (Worcestershire with anchovy, caesar dressing, pesto with pine nuts and parmesan, stock with
    milk solids, "natural flavors"), cross-contact (fryer shared with fish), and labelled-free products that carry
    another allergen.
- **Expected outcomes** are written by a person, per dish and per allergen, before any model is run: the verdict
  (Confident, Worth trying, Not reliable, Refused), the swaps that are acceptable, and the swaps that must never appear.
- **Pass criteria per case.** The verdict class is no more permissive than expected; no proposed ingredient contains an
  avoided allergen (checked by the local scanner and by a human review of the expected "must not appear" list); every
  required cross-contact and label warning is present; a refusal is a refusal.
- **Target before release:** 100% on the safety checks (no avoided allergen in any shown ingredient, no cleared warning,
  no missing required warning, every "defining dish" refused) over the whole set and over repeated runs, and at least
  90% on usefulness (a Confident or Worth trying answer where one was expected). A single safety failure blocks the
  release; it is fixed and the set is run again. The usefulness target can be missed and the feature still ships with
  more refusals; the safety target cannot.
- **Regression tests.** Every case that ever failed becomes a permanent test. The deterministic half (stage 1 and the
  verification pass) is plain unit tests that run on every build with recorded model outputs as fixtures. The live
  model half is a manual or scheduled run through the existing benchmark screen (`docs/BENCHMARK.md`) with its cost shown,
  never part of the unit tests and never run without a tap.
- **Repeatability.** Each case is run several times, since a model varies; the worst run counts.
- **On a device.** Reading the guide on a real phone, with a real recipe the owner cooks, is a release step. The emulator
  is not enough for the owner's confidence.

## UX: clear refusal states

The person should never have to guess whether the guide is saying "this is fine".

- **Refused: the allergen is the dish.** "This dish is built on milk. There is no swap that leaves the same dish." Offers
  a different recipe (Recipe Hunter with the allergen filter) rather than a weak substitute.
- **Refused: cannot verify.** "The check on this phone could not confirm a safe version." Shows what was tried. No adapted
  copy button.
- **Refused: not enough information.** The recipe has no ingredient list, or a line could not be read. Says which.
- **Offline or no AI.** Shows the deterministic result and says the AI part was not run, and why. Never an empty screen.
- **Not reliable** is amber with the checks first. **Confident** is never green: the strongest colour is the neutral
  "worth trying" tone, because nothing here is a clearance. The original warning stays visible on every screen of the guide.
- Every screen carries the standing line: "This is cooking advice. Read every label. It does not replace your doctor's
  advice."
- Saving an adapted copy runs the local scan again and saves it as "check the label" at best, with the swaps and checks in
  its notes (as the adapted copy does now).

## Open questions

- **Which allergens first?** Dairy is the one the owner has, and the engine is most developed there. Start with dairy
  and add others only when each has its own evaluated set, or support all nine at once with more refusals?
- **Does "non-dairy milk" read as "contains milk" or "check the label"?** Still open from round 5, and it decides how
  many substitutes the verification pass can accept.
- **How much cross-contact advice is safe to give?** The tables are written by a person. Who reviews them, and do we
  cite a source? Wrong or missing cross-contact advice is a safety failure too.
- **Which model?** The cheapest model that meets the safety target, or the best one? Should the guide let the person pick,
  or pin one per release so the evaluation applies to what runs?
- **Can the engine's swap classes grow enough that AI is needed only rarely?** Each new class is deterministic and testable;
  each AI proposal is not. A reasonable bet is to turn every verified, repeated AI proposal into a swap class.
- **Dish names with no recipe.** Is "how do I make chicken alfredo" a recipe the app generates (more AI, more risk), or
  does it only guide a recipe the person already has?
- **Method changes.** The engine handles quantities, not chemistry. Who owns the rules for "replace 2 eggs in a cake"?
- **Liability and wording.** What does the app say it is not? Needs the owner's decision before any wording is final.
- **Multi-allergen cases.** A substitute for milk that carries soy is useless to someone avoiding both; the engine already
  skips these. Is the evaluation set large enough to catch the combinations?
- **Offline model.** Is an on-device model ever good enough here? Probably not for a feature that must work correctly.
