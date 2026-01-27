# Protein Detection Training - Run run_20260124_03

Started: 2026-01-24 22:59:15.729731
Products to process: 100

## Progress Tracker

| # | Product | Proteins Found | Tests | Fixes Made |
|---|---------|---------------|-------|------------|
| 1-10 | Pasta products | Wheat, Egg, Milk, Yeast | PASS | None |
| 11-20 | Dairy & Pasta products | Milk, Wheat, Whey, Egg | PASS | Fixed créme typo |
| 21-35 | Nuts, Meats, Dairy | Various | PASS (existing) | None |
| 36 | Blanc de Poulet (3302740059193) | Chicken | PASS | None |
| 37 | Le Supérieur (3302740047367) | Pork | PASS | None |
| 38 | Le Supérieur -25% Sel (3302740047404) | Pork | PASS | None |
| 39 | Pechuga de Pavo (8480000057105) | Turkey | PASS | Added "pavo" |
| 40 | unsmoked back bacon (5060055251234) | Pork | PASS | None |
| 41 | HERTA Blanc Poulet (3154230800965) | Chicken | PASS | None |
| 42 | Rôti de Poulet (3302740039362) | Chicken, Yeast | PASS | None |
| 43 | ZERO NITRITE (3302740025136) | Pork | PASS | None |
| 44 | Jamón cocido (8480000592569) | Pork | PASS | None |
| 45 | Hacendado Turkey (8480000867865) | Turkey | PASS | Added "pavo" |
| 46 | HERTA Lardons (3154230802280) | Pork | PASS | None |
| 47 | Lincolnshire sausages (5013683305527) | Soy, Wheat, Yeast, Barley, Egg | PASS | None |
| 48 | Jamón cocido extra finas (8480000860743) | Pork | PASS | None |

## Algorithm Improvements

| Iteration | Change | Language | Reason |
|-----------|--------|----------|--------|
| 39-45 | Added "pavo" to Turkey Protein keywords | Spanish | "Pechuga de pavo" = turkey breast |
| 49-68 | Added "cerneaux de noix" to Walnut keywords | French | "cerneaux de noix" = walnut kernels |
| 49-68 | Added "isolats de protéines de pois" to Pea Protein Isolate keywords | French | Plural form of pea protein isolate |
| 49-68 | Added "flocons d'orge" to Barley keywords | French | Barley flakes |
| 49-68 | Increased soy oil context window to 25 chars | French | To capture "huile végétale (soja)" |
| 49-68 | Fixed soy oil exclusion to only check context BEFORE match | French | Prevent false exclusion when oil is separate ingredient |
| 49-68 | Fixed starch exclusion to only check context BEFORE match | All | Prevent false exclusion when starch is separate ingredient |

## Iteration Log

### Last Processed Index: 100

---

### Products 49-68 Summary
- Fixed 4 failing tests:
  1. Seeberger (walnut kernels "cerneaux de noix")
  2. TAM thon (soy oil "huile végétale (soja)" exclusion)
  3. Special Muesli (barley "flocons d'orge")
  4. Special K (pea protein isolate "isolats de protéines de pois")
- All 345 tests passing
- Improved oil and starch exclusion logic

### Products 69-100 Summary
- All products already had test cases from previous iterations
- Includes tofu products, protein bars, bread, oat milk
- All tests passing

---

## TRAINING COMPLETE

All 100 products processed successfully.
- Total test cases: 345
- All tests passing
- Algorithm improvements made:
  - Spanish "pavo" for Turkey Protein
  - French "cerneaux de noix" for Walnut Protein
  - French "isolats de protéines de pois" for Pea Protein Isolate
  - French "flocons d'orge" for Barley Protein
  - Improved soy oil exclusion logic (larger context, before-match only)
  - Improved starch exclusion logic (before-match only)

