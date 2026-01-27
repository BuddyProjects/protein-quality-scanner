# Protein Algorithm Training System

This system enables iterative improvement of the protein detection algorithm using the "Ralph Wiggum" technique with Claude Code.

## How It Works

1. **Test Cases**: JSON file stores ingredient texts with expected protein detections
2. **Kotlin Tests**: JUnit tests verify the algorithm against all test cases
3. **Training Scripts**: Python scripts fetch new products from OpenFoodFacts
4. **Iterative Loop**: Claude evaluates, fixes, and repeats until all tests pass

## Files

- `protein_test_cases.json` - Test cases (in app/src/test/resources/)
- `ProteinDetectionTest.kt` - JUnit test class
- `fetch_random_product.py` - Fetches products from OpenFoodFacts
- `train_protein_algorithm.py` - Main training orchestrator
- `run_training.bat` - Windows batch script for quick commands

## Quick Start

### Run Tests
```bash
# From project root
./gradlew test --tests ProteinDetectionTest

# Or using the script
cd scripts
python train_protein_algorithm.py --test
```

### Fetch New Training Product
```bash
cd scripts
python train_protein_algorithm.py --fetch
```

### Check Training Status
```bash
cd scripts
python train_protein_algorithm.py --status
```

## Using with Ralph Wiggum Technique

To use Claude Code in an autonomous loop:

```bash
/ralph-wiggum:ralph-loop "Improve the protein detection algorithm by: 1) Fetching a new product from OpenFoodFacts 2) Evaluating what proteins should be detected 3) Updating the test case with correct expected values 4) Running tests 5) If tests fail, fix ProteinDatabase.kt 6) Repeat until all tests pass" --max-iterations 20
```

## Test Case Format

```json
{
  "id": "unique_id",
  "name": "Human readable name",
  "source": "manual|openfoodfacts",
  "ingredients": "The ingredient text to analyze",
  "expected_detected": ["Protein Name 1", "Protein Name 2"],
  "expected_not_detected": ["Protein That Should NOT Match"],
  "notes": "Explanation of why this test case exists"
}
```

## Manual Training Workflow

1. Run `python fetch_random_product.py --protein` to get a random protein product
2. Analyze the ingredients and determine correct protein sources
3. Add to test_cases.json with proper expected_detected values
4. Run tests: `./gradlew test --tests ProteinDetectionTest`
5. If tests fail, fix `ProteinDatabase.kt`
6. Repeat

## Key Detection Rules in ProteinDatabase.kt

- **Trace Warnings**: "may contain", "kann spuren von", etc. are excluded
- **German Compounds**: "hartweizen", "vollmilch", etc. are recognized
- **Lecithin Exception**: "soya lecithin" is NOT a protein source
- **Word Boundaries**: Short keywords need proper boundaries
- **Ordinal Weighting**: First protein gets highest weight in PDCAAS calculation
