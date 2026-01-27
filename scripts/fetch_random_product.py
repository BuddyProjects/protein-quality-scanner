#!/usr/bin/env python3
"""
Random Product Fetcher for Protein Detection Training

This script fetches random products from OpenFoodFacts and outputs them
in a format suitable for adding to the test cases.

Usage:
    python fetch_random_product.py              # Fetch one random product
    python fetch_random_product.py --count 5    # Fetch 5 random products
    python fetch_random_product.py --category "protein bars"  # Fetch from category
"""

import json
import random
import requests
import argparse
import sys
from pathlib import Path

OPENFOODFACTS_API = "https://world.openfoodfacts.org/api/v2"
TEST_CASES_FILE = Path(__file__).parent.parent / "app/src/test/resources/protein_test_cases.json"

def fetch_random_product():
    """Fetch a random product from OpenFoodFacts with ingredients"""
    # Search for products with ingredients text
    search_url = f"{OPENFOODFACTS_API}/search"

    # Random page to get variety
    page = random.randint(1, 100)

    params = {
        "fields": "code,product_name,brands,ingredients_text,nutriments",
        "page_size": 20,
        "page": page,
        "tagtype_0": "states",
        "tag_contains_0": "contains",
        "tag_0": "en:ingredients-completed"
    }

    try:
        response = requests.get(search_url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()

        products = data.get("products", [])

        # Filter products with actual ingredient text
        valid_products = [
            p for p in products
            if p.get("ingredients_text") and len(p.get("ingredients_text", "")) > 20
        ]

        if not valid_products:
            print("No valid products found, trying again...", file=sys.stderr)
            return fetch_random_product()

        return random.choice(valid_products)

    except requests.RequestException as e:
        print(f"Error fetching product: {e}", file=sys.stderr)
        return None

def fetch_protein_product():
    """Fetch a product likely to contain protein (better for training)"""
    search_url = f"{OPENFOODFACTS_API}/search"

    # Categories likely to have protein
    protein_categories = [
        "protein bars", "protein powder", "milk", "cheese", "yogurt",
        "meat", "chicken", "fish", "eggs", "tofu", "legumes", "nuts",
        "protein shake", "sports nutrition"
    ]

    category = random.choice(protein_categories)
    page = random.randint(1, 20)

    params = {
        "fields": "code,product_name,brands,ingredients_text,nutriments",
        "page_size": 20,
        "page": page,
        "categories_tags_en": category
    }

    try:
        response = requests.get(search_url, params=params, timeout=10)
        response.raise_for_status()
        data = response.json()

        products = data.get("products", [])
        valid_products = [
            p for p in products
            if p.get("ingredients_text") and len(p.get("ingredients_text", "")) > 20
        ]

        if valid_products:
            return random.choice(valid_products)
        else:
            # Fallback to random product
            return fetch_random_product()

    except requests.RequestException:
        return fetch_random_product()

def format_test_case(product, expected_detected=None, expected_not_detected=None):
    """Format a product as a test case"""
    barcode = product.get("code", "unknown")
    name = product.get("product_name", "Unknown Product")
    brand = product.get("brands", "")
    ingredients = product.get("ingredients_text", "")
    protein = product.get("nutriments", {}).get("proteins_100g")

    # Clean up ingredients
    ingredients = ingredients.replace("\n", " ").replace("\r", " ")
    ingredients = " ".join(ingredients.split())  # Normalize whitespace

    test_case = {
        "id": f"off_{barcode}",
        "name": f"{brand} - {name}" if brand else name,
        "source": "openfoodfacts",
        "barcode": barcode,
        "ingredients": ingredients,
        "expected_detected": expected_detected or [],
        "expected_not_detected": expected_not_detected or [],
        "notes": f"Protein: {protein}g/100g" if protein else "No protein data"
    }

    return test_case

def load_test_cases():
    """Load existing test cases"""
    if TEST_CASES_FILE.exists():
        with open(TEST_CASES_FILE, "r", encoding="utf-8") as f:
            return json.load(f)
    return {"description": "Test cases for protein detection", "version": "1.0", "test_cases": []}

def save_test_cases(data):
    """Save test cases to file"""
    with open(TEST_CASES_FILE, "w", encoding="utf-8") as f:
        json.dump(data, f, indent=2, ensure_ascii=False)

def add_test_case(test_case):
    """Add a test case to the JSON file"""
    data = load_test_cases()

    # Check if barcode already exists
    existing_ids = [tc["id"] for tc in data["test_cases"]]
    if test_case["id"] in existing_ids:
        print(f"Test case {test_case['id']} already exists, skipping", file=sys.stderr)
        return False

    data["test_cases"].append(test_case)
    save_test_cases(data)
    return True

def main():
    parser = argparse.ArgumentParser(description="Fetch random products for protein detection training")
    parser.add_argument("--count", type=int, default=1, help="Number of products to fetch")
    parser.add_argument("--protein", action="store_true", help="Focus on protein-rich products")
    parser.add_argument("--add", action="store_true", help="Add to test cases file (requires manual expected values)")
    parser.add_argument("--barcode", type=str, help="Fetch specific product by barcode")
    args = parser.parse_args()

    if args.barcode:
        # Fetch specific product
        url = f"{OPENFOODFACTS_API}/product/{args.barcode}"
        try:
            response = requests.get(url, timeout=10)
            data = response.json()
            if data.get("status") == 1:
                product = data.get("product", {})
                test_case = format_test_case(product)
                print(json.dumps(test_case, indent=2, ensure_ascii=False))
            else:
                print(f"Product {args.barcode} not found", file=sys.stderr)
        except requests.RequestException as e:
            print(f"Error: {e}", file=sys.stderr)
        return

    # Fetch random products
    for i in range(args.count):
        if args.protein:
            product = fetch_protein_product()
        else:
            product = fetch_random_product()

        if product:
            test_case = format_test_case(product)

            print(f"\n{'='*80}")
            print(f"PRODUCT {i+1}/{args.count}")
            print(f"{'='*80}")
            print(json.dumps(test_case, indent=2, ensure_ascii=False))

            if args.add:
                if add_test_case(test_case):
                    print(f"\nAdded to test cases (needs expected_detected values!)")
                else:
                    print(f"\nSkipped (already exists)")

if __name__ == "__main__":
    main()
