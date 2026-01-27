# Protein Detection Training Loop

This file instructs Claude Code to autonomously improve the protein detection algorithm.

## Training Task

You are training the protein detection algorithm in `ProteinDatabase.kt` by:
1. Fetching random products from OpenFoodFacts
2. Evaluating if protein detection is correct
3. Adding test cases
4. Fixing the algorithm when tests fail

## Step-by-Step Process

### Step 1: Fetch a Random Product

Run this command to get a new product:
```bash
python scripts/fetch_random_product.py --protein
```

This outputs a JSON test case with ingredients.

### Step 2: Analyze the Ingredients

Look at the `ingredients` field and determine:
- **expected_detected**: Which proteins SHOULD be detected (actual ingredients)
- **expected_not_detected**: Which proteins should NOT be detected (trace warnings, allergens, emulsifiers like lecithin)

Key rules:
- "May contain X" = trace warning, do NOT detect
- "Produced in a facility that processes X" = trace warning, do NOT detect
- "Allergen information: Contains X" = allergen warning, do NOT detect
- "Soya lecithin" or "soy lecithin" = emulsifier, NOT a protein source
- Actual ingredients listed = DO detect

### Step 3: Add to Test Cases

Edit `app/src/test/resources/protein_test_cases.json`:
- Add the test case with correct `expected_detected` and `expected_not_detected`
- Make sure the `id` is unique

### Step 4: Run Tests

```bash
gradlew.bat :app:testDebugUnitTest
```

### Step 5: If Tests Fail - Fix the Algorithm

If tests fail, edit `app/src/main/java/com/proteinscannerandroid/ProteinDatabase.kt`:
- Add missing keywords to protein sources
- Add trace warning phrases to RULE 0
- Fix detection logic as needed

Then run tests again until they pass.

### Step 6: Repeat

After tests pass, fetch another product and repeat.

## Success Criteria

**If running a Ralph Wiggum loop**: Follow PROMPT.md instructions (process all products in the run).

**If running manually**: Add test cases until you've processed 10+ new products with all tests passing.

## Files to Edit

| File | Purpose |
|------|---------|
| `app/src/test/resources/protein_test_cases.json` | Add test cases |
| `app/src/main/java/com/proteinscannerandroid/ProteinDatabase.kt` | Fix detection algorithm |

## Current Test Status

Run `gradlew.bat :app:testDebugUnitTest` to see current pass/fail status.

## Important Notes

- Always run tests after making changes
- Don't add duplicate test cases (check existing IDs)
- Be conservative with expected_detected - only include actual protein ingredients
- German compound words like "Molkenprotein" should detect "Whey Protein"
