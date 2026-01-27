#!/usr/bin/env python3
"""
Protein Algorithm Training Script

This script is designed for iterative improvement of the protein detection algorithm
using the Ralph Wiggum technique with Claude Code.

WORKFLOW:
1. Fetch a random product from OpenFoodFacts
2. Run the protein detection algorithm (via Gradle test)
3. Claude evaluates if detection is correct
4. If wrong, Claude:
   a) Updates expected_detected/expected_not_detected in test case
   b) Fixes ProteinDatabase.kt if algorithm is wrong
5. Run tests again to verify fix
6. Repeat

USAGE:
    python train_protein_algorithm.py           # Interactive training session
    python train_protein_algorithm.py --auto    # Auto-fetch and test
"""

import json
import subprocess
import sys
import os
from pathlib import Path
import requests
import random

# Paths
SCRIPT_DIR = Path(__file__).parent
PROJECT_DIR = SCRIPT_DIR.parent
TEST_CASES_FILE = PROJECT_DIR / "app/src/test/resources/protein_test_cases.json"
PROTEIN_DB_FILE = PROJECT_DIR / "app/src/main/java/com/proteinscannerandroid/ProteinDatabase.kt"

OPENFOODFACTS_API = "https://world.openfoodfacts.org/api/v2"

def fetch_protein_product():
    """Fetch a product likely to contain protein"""
    protein_categories = [
        "protein bars", "protein powder", "milk", "cheese", "yogurt",
        "meat", "chicken", "fish", "tofu", "legumes", "nuts",
        "protein shake", "sports nutrition", "dairy", "snacks"
    ]

    category = random.choice(protein_categories)
    page = random.randint(1, 10)

    params = {
        "fields": "code,product_name,brands,ingredients_text,nutriments",
        "page_size": 20,
        "page": page,
        "categories_tags_en": category
    }

    try:
        response = requests.get(f"{OPENFOODFACTS_API}/search", params=params, timeout=15)
        response.raise_for_status()
        data = response.json()

        products = data.get("products", [])
        valid_products = [
            p for p in products
            if p.get("ingredients_text") and len(p.get("ingredients_text", "")) > 30
        ]

        if valid_products:
            return random.choice(valid_products)

    except requests.RequestException as e:
        print(f"Error fetching product: {e}")

    return None

def load_test_cases():
    """Load existing test cases"""
    if TEST_CASES_FILE.exists():
        with open(TEST_CASES_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"description": "Test cases", "version": "1.0", "test_cases": []}

def save_test_cases(data):
    """Save test cases"""
    with open(TEST_CASES_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def add_test_case_for_training(product):
    """Add a product as a test case for Claude to evaluate"""
    barcode = product.get("code", "unknown")
    name = product.get("product_name", "Unknown")
    brand = product.get("brands", "")
    ingredients = product.get("ingredients_text", "")
    protein = product.get("nutriments", {}).get("proteins_100g")

    # Clean ingredients
    ingredients = " ".join(ingredients.replace("\n", " ").split())

    test_case = {
        "id": f"training_{barcode}",
        "name": f"{brand} - {name}" if brand else name,
        "source": "openfoodfacts_training",
        "barcode": barcode,
        "ingredients": ingredients,
        "expected_detected": ["NEEDS_EVALUATION"],
        "expected_not_detected": [],
        "notes": f"Protein: {protein}g/100g. AWAITING CLAUDE EVALUATION."
    }

    data = load_test_cases()

    # Remove any existing training case with this barcode
    data["test_cases"] = [tc for tc in data["test_cases"] if tc["id"] != test_case["id"]]
    data["test_cases"].append(test_case)
    save_test_cases(data)

    return test_case

def run_gradle_tests():
    """Run the protein detection tests via Gradle"""
    print("\n" + "="*60)
    print("RUNNING PROTEIN DETECTION TESTS")
    print("="*60)

    # Change to project directory
    os.chdir(PROJECT_DIR)

    try:
        # Run Gradle test
        if sys.platform == "win32":
            cmd = ["gradlew.bat", "test", "--tests", "ProteinDetectionTest"]
        else:
            cmd = ["./gradlew", "test", "--tests", "ProteinDetectionTest"]

        result = subprocess.run(
            cmd,
            capture_output=True,
            text=True,
            timeout=300
        )

        print(result.stdout)
        if result.stderr:
            print("STDERR:", result.stderr)

        if result.returncode == 0:
            print("\n" + "="*60)
            print("ALL TESTS PASSING")
            print("="*60)
            return True
        else:
            print("\n" + "="*60)
            print("TESTS FAILED - NEEDS FIX")
            print("="*60)
            return False

    except subprocess.TimeoutExpired:
        print("Test execution timed out!")
        return False
    except FileNotFoundError:
        print("Gradle not found. Make sure you're in the project directory.")
        return False

def print_training_status():
    """Print current training status"""
    data = load_test_cases()
    total = len(data["test_cases"])
    needs_eval = sum(1 for tc in data["test_cases"] if "NEEDS_EVALUATION" in tc.get("expected_detected", []))
    manual = sum(1 for tc in data["test_cases"] if tc.get("source") == "manual")
    trained = sum(1 for tc in data["test_cases"] if tc.get("source") == "openfoodfacts")

    print("\n" + "="*60)
    print("TRAINING STATUS")
    print("="*60)
    print(f"Total test cases: {total}")
    print(f"  - Manual: {manual}")
    print(f"  - From OpenFoodFacts: {trained}")
    print(f"  - Awaiting evaluation: {needs_eval}")
    print("="*60)

def main():
    import argparse
    parser = argparse.ArgumentParser(description="Train protein detection algorithm")
    parser.add_argument("--auto", action="store_true", help="Auto-fetch product and run")
    parser.add_argument("--test", action="store_true", help="Just run tests")
    parser.add_argument("--status", action="store_true", help="Show training status")
    parser.add_argument("--fetch", action="store_true", help="Fetch new product for training")
    args = parser.parse_args()

    if args.status:
        print_training_status()
        return

    if args.test:
        success = run_gradle_tests()
        sys.exit(0 if success else 1)

    if args.fetch or args.auto:
        print("\nFetching random product from OpenFoodFacts...")
        product = fetch_protein_product()

        if product:
            test_case = add_test_case_for_training(product)
            print("\n" + "="*60)
            print("NEW TRAINING CASE ADDED")
            print("="*60)
            print(f"ID: {test_case['id']}")
            print(f"Name: {test_case['name']}")
            print(f"Ingredients: {test_case['ingredients'][:200]}...")
            print(f"\nFile: {TEST_CASES_FILE}")
            print("="*60)
            print("\nNEXT STEPS FOR CLAUDE:")
            print("1. Look at the ingredients above")
            print("2. Determine which protein sources should be detected")
            print("3. Update expected_detected and expected_not_detected in the test case")
            print("4. Run tests with: python train_protein_algorithm.py --test")
            print("5. If tests fail, fix ProteinDatabase.kt")
            print("="*60)

            if args.auto:
                run_gradle_tests()
        else:
            print("Failed to fetch product")

    if not any([args.auto, args.test, args.status, args.fetch]):
        # Default: show help
        parser.print_help()
        print("\n")
        print_training_status()

if __name__ == "__main__":
    main()
