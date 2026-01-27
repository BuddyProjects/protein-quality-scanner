"""
Ralph Wiggum Loop - Protein Detection Training

Pre-fetches 100 products from OpenFoodFacts, then creates a run folder
for iterative training of the protein detection algorithm.
"""

import json
import requests
import time
import random
from pathlib import Path
from datetime import datetime

PROJECT_ROOT = Path(__file__).parent
RUNS_DIR = PROJECT_ROOT / "runs"
ARCHIVED_DIR = PROJECT_ROOT / "runs_archived"


def archive_old_runs():
    """Move ALL existing runs to archived folder to prevent confusion.

    Only the current run should exist in runs/ at any time.
    """
    if not RUNS_DIR.exists():
        return 0

    archived_count = 0
    ARCHIVED_DIR.mkdir(parents=True, exist_ok=True)

    for run_folder in RUNS_DIR.iterdir():
        if not run_folder.is_dir() or run_folder.name.startswith('.'):
            continue

        # Archive ALL runs, not just completed ones
        dest = ARCHIVED_DIR / run_folder.name
        if dest.exists():
            # Add timestamp suffix if already exists
            dest = ARCHIVED_DIR / f"{run_folder.name}_{int(time.time())}"
        run_folder.rename(dest)
        archived_count += 1
        print(f"  Archived run: {run_folder.name}")

    return archived_count

# OpenFoodFacts API settings
SEARCH_URL = "https://world.openfoodfacts.org/cgi/search.pl"
PRODUCT_URL = "https://world.openfoodfacts.org/api/v0/product"

# Categories likely to have protein
PROTEIN_CATEGORIES = [
    "en:dairy",
    "en:meats",
    "en:fish",
    "en:eggs",
    "en:legumes",
    "en:nuts",
    "en:cereals",
    "en:protein-bars",
    "en:tofu",
    "en:plant-based-foods",
    "en:cheeses",
    "en:yogurts",
    "en:sausages",
    "en:cold-cuts",
    "en:breads",
    "en:pasta",
    "en:breakfast-cereals",
]


def fetch_products_by_category(category: str, count: int = 20) -> list:
    """Fetch products from a specific category."""
    products = []
    try:
        params = {
            "action": "process",
            "tagtype_0": "categories",
            "tag_contains_0": "contains",
            "tag_0": category,
            "sort_by": "random",
            "page_size": count,
            "json": 1,
            "fields": "code,product_name,ingredients_text,brands"
        }

        response = requests.get(SEARCH_URL, params=params, timeout=30)
        if response.status_code == 200:
            data = response.json()
            for product in data.get("products", []):
                if product.get("ingredients_text") and len(product["ingredients_text"]) > 20:
                    products.append({
                        "barcode": product.get("code", "unknown"),
                        "name": product.get("product_name", "Unknown Product"),
                        "brand": product.get("brands", ""),
                        "ingredients": product.get("ingredients_text", ""),
                        "category": category
                    })
    except Exception as e:
        print(f"  Error fetching {category}: {e}")

    return products


def fetch_100_products() -> list:
    """Fetch ~100 products from various categories."""
    all_products = []
    seen_barcodes = set()

    print("Fetching products from OpenFoodFacts...")

    # Shuffle categories and fetch from each
    categories = PROTEIN_CATEGORIES.copy()
    random.shuffle(categories)

    for category in categories:
        if len(all_products) >= 100:
            break

        print(f"  Fetching from {category}...")
        products = fetch_products_by_category(category, count=15)

        for p in products:
            if p["barcode"] not in seen_barcodes and len(all_products) < 100:
                seen_barcodes.add(p["barcode"])
                all_products.append(p)

        time.sleep(1)  # Be nice to the API

    # If we don't have enough, try random search
    if len(all_products) < 100:
        print(f"  Only got {len(all_products)}, trying random search...")
        try:
            params = {
                "action": "process",
                "sort_by": "random",
                "page_size": 100 - len(all_products),
                "json": 1,
                "fields": "code,product_name,ingredients_text,brands"
            }
            response = requests.get(SEARCH_URL, params=params, timeout=30)
            if response.status_code == 200:
                data = response.json()
                for product in data.get("products", []):
                    if (product.get("ingredients_text") and
                        len(product["ingredients_text"]) > 20 and
                        product.get("code") not in seen_barcodes):
                        all_products.append({
                            "barcode": product.get("code", "unknown"),
                            "name": product.get("product_name", "Unknown Product"),
                            "brand": product.get("brands", ""),
                            "ingredients": product.get("ingredients_text", ""),
                            "category": "random"
                        })
                        if len(all_products) >= 100:
                            break
        except Exception as e:
            print(f"  Random search error: {e}")

    print(f"Fetched {len(all_products)} products total")
    return all_products


def get_prompt_template(run_folder: str, product_count: int) -> str:
    return f"""# Protein Detection Training Loop - Ralph Wiggum Style

## CRITICAL: USE ONLY THIS RUN FOLDER

**YOUR RUN FOLDER: `{run_folder}`**

DO NOT look at or use any other run folders. ONLY use the files in `{run_folder}`.
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

You are training the protein detection algorithm by processing {product_count} pre-fetched products.

### Files Location
- **Products file**: `{run_folder}/products.json` (100 products to process)
- **History file**: `{run_folder}/HISTORY.md` (your progress log)
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
   {{
     "id": "off_<barcode>",
     "name": "<product name>",
     "source": "openfoodfacts",
     "barcode": "<barcode>",
     "ingredients": "<ingredients text>",
     "expected_detected": ["<actual protein sources>"],
     "expected_not_detected": ["<trace warnings, lecithin, etc>"],
     "notes": "<brief note>"
   }}
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

- Process all {product_count} products
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
"""


def create_new_run() -> Path:
    """Create a new run folder with pre-fetched products."""
    RUNS_DIR.mkdir(parents=True, exist_ok=True)

    date_str = datetime.now().strftime("%Y%m%d")
    run_num = 1
    while True:
        folder_name = f"run_{date_str}_{run_num:02d}"
        folder_path = RUNS_DIR / folder_name
        if not folder_path.exists():
            break
        run_num += 1

    folder_path.mkdir(parents=True)

    # Fetch products
    products = fetch_100_products()

    # Save products.json
    products_file = folder_path / "products.json"
    with open(products_file, 'w', encoding='utf-8') as f:
        json.dump({
            "fetched_at": datetime.now().isoformat(),
            "count": len(products),
            "products": products
        }, f, indent=2, ensure_ascii=False)

    # Create HISTORY.md
    history_file = folder_path / "HISTORY.md"
    with open(history_file, 'w', encoding='utf-8') as f:
        f.write(f"# Protein Detection Training - Run {folder_name}\n\n")
        f.write(f"Started: {datetime.now()}\n")
        f.write(f"Products to process: {len(products)}\n\n")
        f.write("## Progress Tracker\n\n")
        f.write("| # | Product | Proteins Found | Tests | Fixes Made |\n")
        f.write("|---|---------|---------------|-------|------------|\n\n")
        f.write("## Algorithm Improvements\n\n")
        f.write("| Iteration | Change | Language | Reason |\n")
        f.write("|-----------|--------|----------|--------|\n\n")
        f.write("## Iteration Log\n\n")
        f.write("### Last Processed Index: 0\n\n")

    return folder_path, len(products)


if __name__ == "__main__":
    print("=" * 60)
    print("Ralph Wiggum Loop - Protein Detection Training")
    print("=" * 60)

    # Archive completed runs to prevent confusion
    print("\nChecking for completed runs to archive...")
    archived = archive_old_runs()
    if archived > 0:
        print(f"Archived {archived} completed run(s)")
    else:
        print("No completed runs to archive")

    run_folder, product_count = create_new_run()
    rel_path = run_folder.relative_to(PROJECT_ROOT)

    # Save PROMPT.md
    prompt_file = PROJECT_ROOT / "PROMPT.md"
    with open(prompt_file, 'w', encoding='utf-8') as f:
        f.write(get_prompt_template(str(rel_path), product_count))

    print(f"\nCreated run folder: {run_folder.name}")
    print(f"Products fetched: {product_count}")
    print(f"Prompt saved to: {prompt_file}")
    print(f"\nTo start the loop, run:")
    print(f'  /ralph-loop "Read PROMPT.md and follow all instructions." --max-iterations 150 --completion-promise "TRAINING COMPLETE"')
