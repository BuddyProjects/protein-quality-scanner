# Protein Detection Training System

## Overview

This document describes the iterative training system used to improve the protein detection algorithm in the ProteinScannerAndroid app. The system uses real-world product data from OpenFoodFacts to continuously test and refine protein detection accuracy.

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                         Training Loop                                    │
│                                                                          │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐               │
│  │ OpenFoodFacts│───>│  Analyze &   │───>│  Add Test    │               │
│  │     API      │    │  Categorize  │    │    Case      │               │
│  └──────────────┘    └──────────────┘    └──────────────┘               │
│                                                 │                        │
│                                                 ▼                        │
│  ┌──────────────┐    ┌──────────────┐    ┌──────────────┐               │
│  │    Fix       │<───│   Analyze    │<───│  Run Tests   │               │
│  │  Algorithm   │    │   Failures   │    │   (JUnit)    │               │
│  └──────────────┘    └──────────────┘    └──────────────┘               │
│         │                                       ▲                        │
│         └───────────────────────────────────────┘                        │
│                      (repeat until pass)                                 │
└─────────────────────────────────────────────────────────────────────────┘
```

## Components

### 1. Core Detection Algorithm
**File:** `app/src/main/java/com/proteinscannerandroid/ProteinDatabase.kt`

The detection algorithm uses keyword matching with validation rules:

- **Protein Sources**: 50+ protein types with keywords in English, German, and French
- **PDCAAS/DIAAS Scores**: Quality ratings for each protein source
- **Validation Rules**: Context-aware rules to prevent false positives

Key validation rules:
- **Rule 0a**: Soy lecithin/oil exclusion (emulsifiers are not protein)
- **Rule 0b**: Trace warning detection (allergen warnings are not ingredients)
- **Rule 1**: Generic term validation (standalone vs compound words)
- **Rule 2**: Sunflower oil/lecithin exclusion

### 2. Test Cases Database
**File:** `app/src/test/resources/protein_test_cases.json`

JSON file containing 112+ test cases with:
```json
{
  "id": "off_3281780890006",
  "name": "Pierre Martinet - Trio de lentilles",
  "ingredients": "Lentilles cuites 68%...",
  "expected_detected": ["Lentil Protein", "Corn Protein"],
  "expected_not_detected": ["Milk Protein", "Soy Protein"],
  "notes": "French lentil salad - allergens are trace warnings"
}
```

### 3. Test Runner
**File:** `app/src/test/java/com/proteinscannerandroid/ProteinDetectionTest.kt`

JUnit test class that:
- Loads all test cases from JSON
- Runs detection on each product's ingredients
- Validates expected proteins are detected
- Validates excluded proteins are not detected
- Reports detailed pass/fail results

### 4. Product Fetcher
**File:** `scripts/fetch_random_product.py`

Python script to fetch products from OpenFoodFacts API:
```bash
# Fetch one random product
python scripts/fetch_random_product.py

# Fetch protein-rich products (recommended)
python scripts/fetch_random_product.py --protein

# Fetch multiple products
python scripts/fetch_random_product.py --protein --count 5

# Fetch specific product by barcode
python scripts/fetch_random_product.py --barcode 3281780890006
```

## How the Training Loop Works

### Step 1: Fetch a Random Product
```bash
python scripts/fetch_random_product.py --protein
```

Output example:
```json
{
  "id": "off_8000430172010",
  "name": "Galbani - Mascarpone",
  "ingredients": "Crème (lait) pasteurisée, correcteur d'acidité: acide citrique.",
  "expected_detected": [],
  "expected_not_detected": [],
  "notes": "Protein: 4.6g/100g"
}
```

### Step 2: Analyze Ingredients

Determine which proteins should be detected:

| Ingredient Type | Action |
|-----------------|--------|
| Actual protein ingredients (milk, chicken, tofu) | Add to `expected_detected` |
| Trace warnings ("may contain", "peut contenir") | Add to `expected_not_detected` |
| Emulsifiers (soy lecithin, sunflower lecithin) | Add to `expected_not_detected` |
| Oils (soybean oil, sunflower oil) | Add to `expected_not_detected` |

### Step 3: Add Test Case

Edit `app/src/test/resources/protein_test_cases.json` and add the test case with correct expectations.

### Step 4: Run Tests
```bash
.\gradlew.bat :app:testDebugUnitTest
```

### Step 5: Fix Failures

If tests fail, analyze the failure type:

| Failure Type | Fix Location |
|--------------|--------------|
| Missing protein detection | Add keyword to `ProteinDatabase.kt` |
| False positive from trace warning | Add pattern to `traceWarningPhrases` |
| False positive from emulsifier/oil | Add exclusion rule |
| Wrong protein category | Adjust keyword placement |

### Step 6: Repeat

Continue until all tests pass, then fetch more products.

## Common Fixes Applied During Training

### 1. Multi-Language Keywords

Added keywords for English, German, and French:

```kotlin
// Wheat Protein
keywords = listOf(
    "wheat protein", "wheat",           // English
    "weizen", "weizenprotein",          // German
    "blé", "ble", "farine de blé"       // French (with/without accents)
)
```

### 2. Trace Warning Patterns

Extended trace warning detection for all languages:

```kotlin
val traceWarningPhrases = listOf(
    // English
    "may contain", "traces of", "produced in a facility",
    // German
    "kann spuren von", "spuren von", "kann enthalten",
    // French
    "peut contenir", "traces de", "traces éventuelles de"
)
```

### 3. Allergen List Detection

Improved handling of long allergen lists:
```
"Peut contenir arachide, céleri, gluten, lait, oeuf, soja."
```

The algorithm now checks for periods between the warning phrase and keywords, not just character distance.

### 4. Emulsifier/Oil Exclusion

Soy lecithin and sunflower oil are excluded from protein detection:
```kotlin
// Soy lecithin exclusion patterns
val exclusionPatterns = listOf(
    "lecithin", "lécithine", "lezithin",
    "soybean oil", "huile de soja", "sojaöl"
)
```

## Running the Training

### Prerequisites
- Python 3.x with `requests` library
- Java JDK 11+
- Android Gradle plugin

### Quick Start

1. **Fetch a product:**
   ```bash
   python scripts/fetch_random_product.py --protein
   ```

2. **Add test case to JSON** (with correct expected values)

3. **Run tests:**
   ```bash
   .\gradlew.bat :app:testDebugUnitTest
   ```

4. **If tests fail, fix algorithm and re-run**

5. **Repeat**

### Automated Training with Claude Code

The `CLAUDE.md` file contains instructions for Claude Code to run training iterations autonomously:

```bash
# In Claude Code, simply ask:
"run 10 training iterations"
"do 30 more"
```

Claude will:
- Fetch products automatically
- Analyze ingredients and set expectations
- Add test cases
- Run tests
- Fix any failures
- Repeat for the requested number of iterations

## Test Results

Current status: **112 tests passing**

Categories covered:
- Dairy (milk, cheese, yogurt, butter, cream)
- Meat (chicken, beef, pork, turkey)
- Fish (salmon, tuna, mackerel, sardines)
- Plant proteins (pea, soy, rice, hemp)
- Nuts and seeds (almond, peanut, sunflower, pumpkin)
- Legumes (lentils, chickpeas, beans)
- Eggs
- Specialty (collagen, whey isolate, casein)

Languages tested:
- English
- German
- French

## Metrics

| Metric | Value |
|--------|-------|
| Total test cases | 112 |
| Protein sources | 50+ |
| Languages supported | 3 (EN, DE, FR) |
| Pass rate | 100% |

## Future Improvements

1. **More languages**: Add Spanish, Italian, Dutch keywords
2. **OCR integration**: Test with real OCR output (with typos/errors)
3. **Confidence scoring**: Return confidence levels for detections
4. **Negative test cases**: More products that should detect nothing
5. **Edge cases**: Unusual ingredient formats, abbreviations
