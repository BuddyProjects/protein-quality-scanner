# Protein Scanner Data Audit Report

**Date:** January 29, 2026  
**Auditor:** Research Subagent  
**File Audited:** `app/src/main/java/com/proteinscannerandroid/ProteinDatabase.kt`

## Executive Summary

Overall, the protein database contains mostly accurate data based on peer-reviewed scientific literature and FAO/WHO guidelines. However, several corrections and improvements are recommended, particularly for:

1. **Collagen/Gelatin PDCAAS** - Currently overstated at 0.08, should be 0
2. **Missing DIAAS values** for many proteins that have published data
3. **Missing limiting amino acids** for several protein sources
4. **Minor PDCAAS adjustments** for some proteins based on recent studies

---

## Methodology

Data was verified against:
- FAO Food and Nutrition Paper 92 (2013) - Dietary Protein Quality Evaluation
- PMC/PubMed peer-reviewed studies (2017-2025)
- Comprehensive reviews: Herreman et al. (2020), Mathai et al. (2017)
- Recent DIAAS studies from University of Illinois and other institutions

---

## Detailed Findings by Protein Source

### 🥛 DAIRY PROTEINS

#### Whey Protein (Concentrate, Isolate, Hydrolysate)
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 1.0 | 1.0 | ✅ Correct |
| DIAAS | 109 | 90-109 (varies by reference pattern) | ⚠️ High end |
| Limiting AA | None | None | ✅ Correct |
| Digestion | Fast | Fast | ✅ Correct |

**Notes:** DIAAS of 109 uses the 0.5-3 year FAO reference pattern. Some studies show lower values (~90-100) using different patterns. Value is acceptable but could note the variation.

**Source:** Mathai et al. 2017 (British Journal of Nutrition); PMC7760812

---

#### Casein Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 1.0 | 1.0 | ✅ Correct |
| DIAAS | 122 | 100-145 (form dependent) | ✅ Correct |
| Limiting AA | None | None | ✅ Correct |
| Digestion | Slow | Slow (6-8 hours) | ✅ Correct |

**Notes:** Casein's slow digestion is well-documented. It forms a gel in the stomach, delaying gastric emptying and providing sustained amino acid release. DIAAS of 122 for whole milk powder (FAO 2013, Table 4) is accurate.

**Source:** Boirie et al. 1997 (PNAS); FAO Food and Nutrition Paper 92

---

#### Milk Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 1.0 | 1.0 (1.21 untruncated) | ✅ Correct |
| DIAAS | 118 | 108-122 | ✅ Correct |
| Limiting AA | None | None | ✅ Correct |
| Digestion | Medium | Medium | ✅ Correct |

---

### 🥚 EGG PROTEINS

#### Egg Protein (Whole Egg, Egg White, Albumin)
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 1.0 | 1.0 (1.18 untruncated) | ✅ Correct |
| DIAAS | 108 | 105-113 (cooking method dependent) | ✅ Correct |
| Limiting AA | None | None (some studies show His as marginally limiting) | ✅ Correct |
| Digestion | Medium | Medium | ✅ Correct |

**Notes:** Egg protein has historically been the "gold standard" reference protein. Cooking method affects DIAAS: fried eggs ~113, boiled ~105-108.

**Source:** PMC11658930 (2024 DIAAS study on eggs)

---

### 🌱 PLANT PROTEINS - SOY

#### Soy Protein Isolate
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.95 | 0.91-1.0 | ✅ Correct |
| DIAAS | 91 | 84-92 | ✅ Correct |
| Limiting AA | Methionine | Methionine + Cysteine (SAA) | ✅ Correct |
| Digestion | Medium | Medium | ✅ Correct |

**Source:** PMC9552267; Herreman et al. 2020

---

#### Soy Protein (General)
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.85 | 0.85-0.91 | ✅ Correct |
| DIAAS | 91 | 84-91 | ✅ Correct |
| Limiting AA | Methionine | Methionine + Cysteine | ✅ Correct |

---

### 🌱 PLANT PROTEINS - LEGUMES

#### Pea Protein Isolate
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.85 | 0.78-0.91 | ✅ Correct |
| DIAAS | 70 | 66-87.6 | ⚠️ May be slightly low |
| Limiting AA | Methionine | SAA (Met+Cys), sometimes Tryptophan | ⚠️ Add Tryptophan |
| Digestion | Medium | Medium | ✅ Correct |

**Recommendation:** Add Tryptophan as a secondary limiting amino acid for pea protein.

**Source:** PMC7760812 (Table 2); ScienceDirect 2023 field pea study

---

#### Pea Protein (General)
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.73 | 0.60-0.84 | ✅ Correct |
| DIAAS | 70 | 60-70 | ✅ Correct |
| Limiting AA | Methionine | SAA, Tryptophan | ⚠️ Add Tryptophan |

---

#### Lupin Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.89 | 0.75-0.89 | ✅ Correct (high end) |
| DIAAS | Not listed | 68 | ❌ Should add |
| Limiting AA | Not listed | Methionine, Cysteine | ❌ Should add |
| Digestion | Not listed | Medium | ❌ Should add |

**Recommendation:** Add DIAAS value of 68 and limiting amino acids (Methionine + Cysteine).

**Source:** Herreman et al. 2020; Russo & Reggiani 2013

---

### 🌾 GRAIN PROTEINS

#### Rice Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.47 | 0.42-0.60 | ✅ Correct |
| DIAAS | 60 | 37-60 (form dependent) | ✅ Correct (high end) |
| Limiting AA | Lysine | Lysine, Threonine | ⚠️ Add Threonine |
| Digestion | Medium | Medium | ✅ Correct |

**Notes:** Brown rice DIAAS ~42, rice protein concentrate ~52-60. App value represents processed concentrate.

**Source:** PMC7760812; MDPI Foods 2019

---

#### Wheat Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.25 | 0.25-0.54 (gluten vs. whole) | ⚠️ Clarify |
| DIAAS | Not listed | 39 | ❌ Should add |
| Limiting AA | Not listed | Lysine | ❌ Should add |

**Notes:** PDCAAS of 0.25 is specifically for wheat gluten. Whole wheat flour is ~0.42-0.54.

**Recommendation:** Clarify that 0.25 applies to wheat gluten; consider adding separate entries for whole wheat flour (~0.42).

---

#### Quinoa Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.77 | 0.77-1.09 (processing dependent) | ✅ Correct |
| DIAAS | Not listed | Not widely published | N/A |
| Limiting AA | Not listed | Lysine (marginal), Leucine, Valine | ❌ Should add |

**Notes:** Often marketed as "complete protein" but has marginal limitations. Washed quinoa scores higher.

**Source:** PMC7434868

---

### ⚠️ PROBLEM PROTEINS - COLLAGEN & GELATIN

#### Collagen
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.08 | **0** | ❌ **INCORRECT** |
| DIAAS | 0 | 0 | ✅ Correct |
| Limiting AA | Tryptophan, Methionine, Lysine | Tryptophan (completely absent) | ⚠️ Clarify |
| Digestion | Fast | Fast | ✅ Correct |

**CRITICAL CORRECTION NEEDED:**
Collagen has a PDCAAS of **0 (zero)**, not 0.08. This is because collagen **completely lacks tryptophan** - one of the 9 essential amino acids. A protein that is missing an essential amino acid entirely gets a score of 0.

**Source:** Multiple peer-reviewed sources:
- "Collagen scores a 0 on the PDCAAS scale because of a complete lack of tryptophan" (Supply Side Journal, 2025)
- "Collagen protein lacks one indispensable amino acid (tryptophan) and is therefore categorized as an incomplete protein source" (ResearchGate 2019)
- "The Digestibility Score for collagen protein is 0 because it lacks..." (Barbell Medicine)

**Recommended Change:**
```kotlin
ProteinSource(
    name = "Collagen",
    pdcaas = 0.0,  // Changed from 0.08
    qualityCategory = "Incomplete",  // Changed from "Low"
    // ... rest unchanged
    notes = "⚠️ LACKS TRYPTOPHAN ENTIRELY - PDCAAS is 0. Not suitable as sole protein source for muscle building."
)
```

---

#### Gelatin
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.08 | **0** | ❌ **INCORRECT** |
| DIAAS | 0 | 0 | ✅ Correct |

**Same correction needed as collagen** - Gelatin is derived from collagen and shares the same amino acid deficiencies.

---

### 🌿 OTHER PLANT PROTEINS

#### Hemp Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.46 | 0.42-0.66 | ⚠️ Low end |
| DIAAS | Not listed | ~51 (hemp protein 1) | ❌ Should add |
| Limiting AA | Not listed | Lysine | ❌ Should add |

**Notes:** Hemp hearts (dehulled) score higher (~0.61-0.66) than hemp protein concentrates (~0.42-0.46).

**Source:** House et al. 2010; PMC10630821

**Recommendation:** Add DIAAS value (~51) and note Lysine as limiting amino acid.

---

#### Mycoprotein (Quorn)
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.91 | 0.91-0.996 | ✅ Correct |
| DIAAS | Not listed | Not widely published | N/A |
| Limiting AA | Not listed | Methionine + Cysteine | ❌ Should add |

**Recommendation:** Add limiting amino acids (Methionine + Cysteine).

**Source:** Cambridge Core 2010; Quorn Nutrition

---

### 🥩 ANIMAL PROTEINS

#### Beef Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 0.92 | 0.92 | ✅ Correct |
| DIAAS | Not listed | 100+ | ❌ Should add |
| Limiting AA | Not listed | None | ✅ Correct (by absence) |

---

#### Chicken/Turkey Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 1.0 | 0.95-1.0 | ✅ Correct |

---

#### Fish Protein
| Metric | App Value | Literature Value | Status |
|--------|-----------|-----------------|--------|
| PDCAAS | 1.0 | 1.0 | ✅ Correct |

---

## Summary of Corrections Needed

### 🔴 Critical (Factual Errors)

| Protein | Issue | Current | Correct |
|---------|-------|---------|---------|
| Collagen | PDCAAS too high | 0.08 | **0** |
| Gelatin | PDCAAS too high | 0.08 | **0** |

### 🟡 Important (Missing Data)

| Protein | Missing Data | Recommended Value |
|---------|--------------|-------------------|
| Lupin | DIAAS | 68 |
| Lupin | Limiting AA | Methionine, Cysteine |
| Wheat | DIAAS | 39 |
| Wheat | Limiting AA | Lysine |
| Hemp | DIAAS | 51 |
| Hemp | Limiting AA | Lysine |
| Quinoa | Limiting AA | Lysine (marginal), Valine |
| Mycoprotein | Limiting AA | Methionine, Cysteine |
| Pea Protein | Additional Limiting AA | Add Tryptophan |
| Rice Protein | Additional Limiting AA | Add Threonine |
| Beef | DIAAS | 100+ |

### 🟢 Minor (Enhancements)

1. **Add quality category "Incomplete"** for collagen/gelatin instead of "Low"
2. **Clarify wheat protein** - distinguish between wheat gluten (0.25) and whole wheat flour (0.42)
3. **Consider adding DIAAS for more proteins** as this is the FAO-recommended metric since 2013

---

## Proteins to Consider Adding

Based on market trends and user requests, consider adding:

| Protein | Suggested PDCAAS | Notes |
|---------|-----------------|-------|
| Potato Protein | 0.87-0.99 | Emerging plant protein with excellent quality |
| Insect Protein (Cricket) | 0.69 | Growing alternative protein market |
| Canola Protein | 0.88-1.0 | High quality plant protein |
| Fava Bean Protein | 0.60-0.67 | Popular in plant-based foods |
| Algae Protein (beyond Spirulina) | Varies | Sustainable protein source |

---

## Digestion Speed Verification

The digestion speed classifications are **accurate** based on scientific literature:

| Speed | Proteins | Verification |
|-------|----------|--------------|
| **Fast** | Whey, Whey Hydrolysate, Collagen | ✅ Confirmed - rapidly digested and absorbed |
| **Medium** | Milk, Egg, Soy, Pea, Rice, most plant proteins | ✅ Confirmed - moderate digestion rate |
| **Slow** | Casein | ✅ Confirmed - forms gel, 6-8 hour release |
| **Very Fast** | Whey Hydrolysate | ✅ Confirmed - pre-digested |

**Source:** Boirie et al. 1997 (PNAS); ScienceDirect 2020

---

## References

1. FAO (2013). Dietary protein quality evaluation in human nutrition. FAO Food and Nutrition Paper 92.

2. Herreman, L. et al. (2020). Comprehensive overview of the quality of plant- and animal-sourced proteins based on the digestible indispensable amino acid score. Food Science & Nutrition, 8, 5379-5391.

3. Mathai, J.K. et al. (2017). Values for digestible indispensable amino acid scores (DIAAS) for some dairy and plant proteins. British Journal of Nutrition, 117, 490-499.

4. House, J.D. et al. (2010). Evaluating the quality of protein from hemp seed. J. Agric. Food Chem., 58, 11801-11807.

5. PMC7760812 - Plant Proteins: Assessing Their Nutritional Quality (2020)

6. PMC11658930 - DIAAS in eggs and egg-containing meals (2024)

7. PMC11252030 - DIAAS: 10 years on (2024)

8. Wikipedia - Protein digestibility corrected amino acid score

---

## Appendix: DIAAS Reference Patterns (FAO 2013)

For reference, DIAAS can vary significantly based on which age group reference pattern is used:

| Age Group | Key Amino Acid Requirements (mg/g protein) |
|-----------|-------------------------------------------|
| Infant (0-6 mo) | Highest requirements |
| Child (6-36 mo) | Lys 57, SAA 27, Thr 31, Trp 8.5 |
| Older child/Adult | Lys 48, SAA 23, Thr 25, Trp 6.6 |

Most published DIAAS values use the 6-36 month reference pattern. The app's values are consistent with this approach.
