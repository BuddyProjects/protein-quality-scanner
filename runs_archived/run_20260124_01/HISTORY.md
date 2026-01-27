# Protein Detection Training - Run run_20260124_01

Started: 2026-01-24 21:51:13.769171
Products to process: 100

## Progress Tracker

| # | Product | Proteins Found | Tests | Fixes Made |
|---|---------|---------------|-------|------------|
| 1 | SPAGHETTI N° 5 (8076800195057) | Wheat Protein | PASS | None |
| 2 | (skipped - corrupted data) | - | SKIP | None |
| 3 | INTEGRALE PENNE RIGATE (8076809529433) | Wheat Protein | PASS | Added Danish keywords |
| 4 | Penne Rigate N°73 (8076802085738) | Wheat Protein | PASS | None |
| 5 | indoumi goût poulet (0089686120073) | Wheat, Yeast | PASS | Added flavor exclusion |
| 6 | Fusilli (8076802085981) | Wheat Protein | PASS | None |
| 7 | Nouille instantanées goût poulet (8852018101024) | Wheat, Yeast | PASS | None |
| 8 | Pâtes spaghetti au blé complet (8076809529419) | Wheat Protein | PASS | None |
| 9 | Lasagne all'uovo (8076800376999) | Wheat, Egg | PASS | Added Italian "grano" |
| 10 | Tortellini ricotta & épinards (8001665714372) | Milk, Wheat, Egg, Yeast | PASS | Added exclusions |
| 11 | Medium Egg Noodles (5000354924712) | Wheat, Egg | PASS | Added "may also contain" |
| 12 | Pâtes spaghetti n°5 1kg (8076800105056) | Wheat | PASS | None |
| 13 | Tortelloni Ricotta (4056489009191) | Milk, Wheat, Egg, Rice, Yeast | PASS | None |
| 14 | Rana tortelloni (8001665705073) | Milk, Whey, Wheat, Egg, Yeast | PASS | Fixed corn/starch exclusion |
| 15 | Sliced White Sourdough (5025125000006) | Wheat | PASS | None |
| 16 | Proper Sourdough (5025125000112) | Wheat | PASS | None |
| 17 | Sourdough Grains & Seeds (5025125000037) | Wheat, Flax, Sunflower | PASS | None |
| 18 | Tartine croustillante (7300400481595) | Yeast | PASS | None |
| 19 | Pain de mie Seigle & Graines (3760049798609) | Wheat, Flax, Sunflower, Barley, Yeast | PASS | Trace exclusion fixed |
| 20 | Sourdough (5025125000129) | Wheat, Spelt | PASS | None |
| 21 | FIBRES (7300400481588) | Wheat, Oat, Sesame | PASS | None |
| 22 | Krisprolls complets (7311070032611) | Wheat, Barley, Yeast | PASS | None |
| 23 | 14 Maxi Tranches (3029330022428) | Wheat, Yeast, Corn | PASS | None |
| 24 | Dark Rye Crispbread (5010265002911) | (none) | PASS | None |
| 25 | Pain De Mie Bio (3760049790214) | Wheat, Yeast | PASS | None |
| 26 | Roggen Vollkornbrot (20006105) | Wheat, Yeast | PASS | None |
| 27 | Seed Sensations Seven Seeds (5010003064744) | Many | PASS | Added "soya" keyword, linseed exclusion |
| 28 | Bakery Co. Bagels (5020364010113) | Wheat, Yeast, Corn, Barley | PASS | None |
| 29 | Rye & Mixed Seed Sourdough (5057967395088) | Multi | PASS | None |
| 30 | Filets de maquereau (6111162001218) | Fish | PASS | None |
| 31 | Sardines Huile d'Olive (3019081238643) | Fish | PASS | None |
| 32 | Joly Thon Entier (6111162000181) | Tuna | PASS | None |
| 33 | Atún con tomate (8410111000707) | Tuna | PASS | Added soy oil exclusion |
| 34 | NAKD PROTEIN (5060088700105) | Peanut, Hazelnut | PASS | None |
| 35 | Plain Sardines (3263670162219) | Fish | PASS | None |
| 36 | TAM thon entier (3341430510019) | Tuna | PASS | Added soy oil exclusion |
| 37 | Filets Maquereaux Vin Blanc (3165958350014) | Fish | PASS | None |
| 38 | Filet maquereaux naturel (3165950211726) | Fish | PASS | None |
| 39 | Filets Maquereaux moutarde (3165957058010) | Fish | PASS | None |
| 40 | Jben (6111242106949) | Milk, Dairy | PASS | None |
| 41 | Cream cheese (6111246721278) | Milk, Dairy | PASS | None |
| 42 | Carré Frais (3480341000674) | Milk | PASS | None |
| 43 | Philadelphia (7622201693916) | Milk | PASS | None |
| 44 | Carré Frais Nature (3480341000636) | Milk, Dairy | PASS | None |
| 45 | Paysan Breton Fromage (3412290070101) | Milk | PASS | None |
| 46 | Kiri carré 8p (6111028000980) | Milk | PASS | None |

## Algorithm Improvements

| Iteration | Change | Language | Reason |
|-----------|--------|----------|--------|
| 3 | Added "durumhvede", "hvede" to Wheat Protein | Danish | Danish durum wheat detection |
| 5-10 | Added flavor exclusion for Chicken Protein | Multi | Exclude arôme/flavor/aroma terms |
| 9 | Added "grano duro", "grano" to Wheat Protein | Italian | Italian durum wheat detection |
| 10 | Added corn fiber exclusion | Multi | Exclude "fibres végétales (mais" |
| 10 | Added nutmeg exclusion for Nut Protein | Multi | Exclude "noix de muscade" |
| 10 | Added "oeufs", "oeuf" to Egg Protein | French | Alternative French egg spelling |
| 11 | Added "may also contain" to trace warnings | English | Additional allergen phrase |
| 11 | Added starch exclusion for Pea Protein | Multi | Exclude pea starch |
| 14 | Enhanced corn exclusion for "(from" context | Multi | Exclude corn in starch/fiber contexts |
| 19 | Added "traces éventuelles d'" and variants | French | More French trace warning patterns |
| 27 | Added "soya", "soya flour" to Soy Protein | English | British spelling of soy |
| 27 | Added linseed exclusion for Lentil Protein | Multi | Distinguish linseed (flax) from linse (lentil) |
| 33-36 | Added "huile végétale (soja" and variants to Soy exclusion | French | Exclude soy oil in various formats |

## Iteration Log

### Product 1: SPAGHETTI N° 5
- **Barcode**: 8076800195057
- **Ingredients**: DURUM WHEAT SEMOLINA PASTA. May contain traces of soy and mustard.
- **Expected detected**: Wheat Protein (durum wheat semolina)
- **Expected NOT detected**: Soy Protein (trace warning only)
- **Test result**: PASS
- **Fixes**: None required

### Product 2: (skipped)
- **Barcode**: 5285000396437
- **Reason**: Corrupted OCR data, no readable ingredients

### Product 3: INTEGRALE PENNE RIGATE
- **Barcode**: 8076809529433
- **Ingredients**: Fuldkorns _durumhvede_, Vand
- **Expected detected**: Wheat Protein (durumhvede = durum wheat in Danish)
- **Test result**: PASS (after fix)
- **Fixes**: Added "durumhvede", "hvede" to Wheat Protein keywords

### Products 4-10: Batch processed
- Multiple pasta products with wheat, eggs, milk
- Key fixes: Added Italian/Danish keywords, flavor exclusions, nutmeg exclusion

### Products 11-20: Batch processed
- Bread and pasta products
- Key fixes: Enhanced trace warning patterns, corn/starch exclusions

### Products 21-30: Batch processed
- Crispbreads, seeded breads, fish
- Key fixes: Added "soya" keyword, linseed/lentil disambiguation

### Products 31-39: Batch processed
- Fish products (sardines, tuna, mackerel), protein bar
- Key fixes: Extended soy oil exclusion patterns

### Products 40-46: Batch processed
- Cheese products (cream cheese, fresh cheese)
- Key fixes: None needed

### Last Processed Index: 46

---

## Training Summary

**Total Products Processed**: 46 (excluding 1 skipped due to corrupted data)
**Total Test Cases Added**: 45 new test cases
**Total Test Cases in File**: 212
**All Tests Passing**: Yes

### Key Algorithm Improvements Made:
1. Added Danish wheat keywords (durumhvede, hvede)
2. Added Italian wheat keywords (grano duro, grano)
3. Added French egg alternative spellings (oeufs, oeuf)
4. Added British soy spelling (soya, soya flour)
5. Added chicken/poultry flavor exclusion (arôme de poulet, etc.)
6. Added nutmeg exclusion (noix de muscade ≠ nut protein)
7. Added linseed/lentil disambiguation
8. Enhanced corn starch/fiber exclusion
9. Extended soy oil exclusion patterns
10. Enhanced French trace warning patterns

