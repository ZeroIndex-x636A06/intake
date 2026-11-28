# Feedback round 4

2026-11-13. The owner tested the preview build at 46f52e8 / 61c9d80 on their phone and sent screenshots. Each item is
what they said, then the work it became. "Mine" marks an interpretation the owner did not state.

| # | What they said | Work |
| --- | --- | --- |
| 1 | Healthier options nearby "doesn't work still" (it returned "Nothing found" within 1 mi). It should search within a 10–15 minute radius of the current location, use the menu import system, find the menu links itself, try without AI, then with AI, show each restaurant's results as soon as its menu is in, then move on to the next restaurant. | Agent A: nearby restaurants from a local-first source, automatic menu discovery, a per-restaurant pipeline with streamed results. |
| 2 | The nutrition-menu step does not offer AI import for PDFs and links. | Agent A |
| 3 | This link failed as the nutrition menu: https://fcs.osu.edu/sites/fcs/files/imce/PDFs/mcdonalds-nutrition-facts.pdf ("found no nutrition table … needs selectable text"). | Agent A |
| 4 | Progress pulls past data, but the weights seem "somewhat off a little". | Agent B: compare Health Connect records with what is shown (units, rounding, dedupe, day bucketing). |
| 5 | They can't see each weigh-in by dragging a finger across the graph. | Agent B: scrub tooltips on the Progress charts. |
| 6 | Recipes don't show up in the Log food list. | Agent E |
| 7 | A drink logged in Drinks shows next to a meal; it should only do that if it was logged with the meal. | Agent D |
| 8 | The animation when going back looks wrong; it should slide, the opposite of opening a page. | Agent C |
| 9 | Drinks should not be in the main Nutrition screen or the More screen. | Agent D. *Mine: drinks stay reachable from Log food and from Today's water card.* |
| 10 | They want custom versions of the built-in drinks, e.g. black coffee with 3 tsp sugar. | Agent D |
| 11 | Meal templates and similar features are tied to meal plans; people who don't use plans should still have them. | Agent E |
| 12 | Recipe Hunter should have a section for recipes that could be adapted to the allergen (cheddar → vegan cheddar), but refuse ones where the allergen is the dish (tres leches). Rules built around concepts, not those examples. | Agent F |
| 13 | A recipe saved from Recipe Hunter should show "Saved" on its card. | Agent F |
| 14 | The recipe screen needs a Cook button next to Log. | Agent E |
| 15 | Recipe Hunter did not pull images for every recipe. | Agent F |
