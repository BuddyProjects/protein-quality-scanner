# Protein Detection Training Loop - Ralph Wiggum Style

## CRITICAL: USE ONLY THIS RUN FOLDER

**YOUR RUN FOLDER: `runs\run_20260127_01`**

DO NOT look at or use any other run folders. ONLY use the files in `runs\run_20260127_01`.
If you see other run folders in the `runs/` directory, IGNORE THEM completely.

---

## CRITICAL RULE - UPDATE HISTORY.MD AFTER EVERY PRODUCT

**BEFORE you process another product, you MUST update HISTORY.md with:**
1. The product name and barcode
2. What proteins you expected vs detected
3. Whether tests passed or failed
4. Any fixes you made to the algorithm

Pattern: ANALYZE -> ADD TEST -> RUN TESTS -> FIX IF NEEDED -> **LOG TO HISTORY.MD** -> repeat

---

## Your Task

You are training the protein detection algorithm by processing 100 pre-fetched products.

### Files Location
- **Products file**: `runs\run_20260127_01/products.json` (100 products to process)
- **History file**: `runs\run_20260127_01/HISTORY.md` (your progress log)
- **Test cases**: `app/src/test/resources/protein_test_cases.json`
- **Algorithm**: `app/src/main/java/com/proteinscannerandroid/ProteinDatabase.kt`

### For Each Product:

1. **Read** the product from products.json (process in order, check HISTORY.md for last processed index)

2. **Analyze** the ingredients:
   - Identify actual protein sources (soy, milk, eggs, meat, nuts, legumes, grains, etc.)
   - Identify NON-protein mentions (trace warnings, lecithin, oils, starch)
   - Consider all three languages: English, German, French

3. **Add Test Case** to `protein_test_cases.json`:
   ```json
   {
     "id": "off_<barcode>",
     "name": "<product name>",
     "source": "openfoodfacts",
     "barcode": "<barcode>",
     "ingredients": "<ingredients text>",
     "expected_detected": ["<actual protein sources>"],
     "expected_not_detected": ["<trace warnings, lecithin, etc>"],
     "notes": "<brief note>"
   }
   ```

4. **Run Tests**: `gradlew.bat :app:testDebugUnitTest`

5. **If Tests Fail**:
   - Read the failure message
   - Fix ProteinDatabase.kt (add missing keywords, fix exclusion rules)
   - Consider: does this fix need to be applied in all 3 languages?
   - Run tests again until they pass

6. **Update HISTORY.md** with results

7. **Repeat** for next product

### Success Criteria

- Process all 100 products
- All tests pass
- Algorithm improvements are consistent across languages (EN/DE/FR)

When ALL products are processed and tests pass, output:
```
<promise>TRAINING COMPLETE</promise>
```

### Key Rules

1. **Trace warnings are NOT proteins**: "may contain", "kann spuren", "peut contenir"
2. **Lecithin/oil are NOT proteins**: soy lecithin, sunflower oil, etc.
3. **Starch is NOT protein**: amidon, stärke, starch
4. **Language consistency**: When adding a keyword in one language, add equivalents in others
5. **Be conservative**: Only expect detection of actual protein ingredients

### Common Protein Keywords by Language

| Protein | English | German | French |
|---------|---------|--------|--------|
| Milk | milk, dairy | milch, molke | lait, lactosérum |
| Egg | egg, albumin | ei, eiweiß | œuf, albumine |
| Soy | soy, soya | soja | soja |
| Wheat | wheat, gluten | weizen, gluten | blé, gluten |
| Pea | pea | erbse | pois |

## MANDATORY WORKFLOW

**STEP 1: READ** - Check HISTORY.md for last processed product index
**STEP 2: GET** - Read next product from products.json
**STEP 3: ANALYZE** - Determine expected_detected and expected_not_detected
**STEP 4: ADD** - Add test case to protein_test_cases.json
**STEP 5: TEST** - Run gradlew.bat :app:testDebugUnitTest
**STEP 6: FIX** - If tests fail, fix ProteinDatabase.kt and re-test
**STEP 7: LOG** - Update HISTORY.md IMMEDIATELY (BLOCKING!)
**STEP 8: DECIDE** - All done? -> output promise, More products? -> STEP 1
