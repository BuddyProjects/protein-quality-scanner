"""Test matching the refined Kotlin logic"""
import re

PROTEIN_KEYWORDS = {
    "Rice Protein": ["rice protein", "reisprotein", "reiseiweiß", "reiseiweiss", "brown rice protein"],
    "Whey Concentrate": ["whey protein concentrate", "whey concentrate", "whey powder", "whey", "molkenproteinkonzentrat", "molkenpulver", "molkeneiweiß", "molkeneiweiss"],
    "Whey Isolate": ["whey protein isolate", "whey isolate", "molkenproteinisolat", "molkenisolat"],
    "Soy Protein": ["soy protein", "soja", "soya", "sojaeiweiß", "sojaeiweiss", "tofu", "tempeh"],
    "Soy Protein Isolate": ["soy protein isolate", "soy isolate", "sojaproteinisolat", "sojaisolat"],
    "Pea Protein": ["pea protein", "pea", "erbsen", "peas", "protéine de pois"],
    "Pea Protein Isolate": ["pea protein isolate", "pea isolate", "erbsenproteinisolat", "erbsenisolat"],
    "Wheat Protein": ["wheat protein", "wheat", "weizen", "gluten", "wheat flour", "weizenmehl"],
}

PROTEIN_BASE_KEYWORDS = {
    "soja", "soya", "erbsen", "peas", "pea", "reis", "rice", "whey", "molke", "molken"
}

def get_full_word(text, start, end):
    boundaries = set(' ,.:;()[]\t\n')
    word_start = 0
    for i in range(start - 1, -1, -1):
        if text[i] in boundaries:
            word_start = i + 1
            break
    word_end = len(text)
    for i in range(end, len(text)):
        if text[i] in boundaries:
            word_end = i
            break
    return text[word_start:word_end]

def find_matches(ingredients: str) -> list:
    ingredients_lower = ingredients.lower()
    matches = []
    
    for protein_name, keywords in PROTEIN_KEYWORDS.items():
        for keyword in keywords:
            found = False
            
            if len(keyword) <= 3:
                pattern = r'\b' + re.escape(keyword) + r'\b'
                if re.search(pattern, ingredients_lower):
                    matches.append((protein_name, keyword))
                    found = True
            else:
                pattern = r'\b' + re.escape(keyword) + r'\b'
                if re.search(pattern, ingredients_lower):
                    matches.append((protein_name, keyword))
                    found = True
                else:
                    idx = ingredients_lower.find(keyword)
                    if idx != -1:
                        if keyword.lower() in PROTEIN_BASE_KEYWORDS:
                            full_word = get_full_word(ingredients_lower, idx, idx + len(keyword))
                            if "isolat" not in full_word and "konzentrat" not in full_word:
                                matches.append((protein_name, keyword))
                                found = True
                        else:
                            matches.append((protein_name, keyword))
                            found = True
            
            if found:
                break
    return matches

def test():
    tests = [
        ("Reismehl, Zucker", [], "Rice flour no match"),
        ("Reisprotein, Wasser", ["Rice Protein"], "Rice protein matches"),
        ("Molkeneiweiß, Zucker", ["Whey Concentrate"], "Generic whey -> Concentrate"),
        ("Molkenproteinisolat", ["Whey Isolate"], "Whey isolat -> Isolate ONLY"),
        ("Soja, Wasser", ["Soy Protein"], "Plain soja -> Soy Protein"),
        ("Sojaproteinisolat", ["Soy Protein Isolate"], "Soy isolat -> Isolate ONLY"),
        ("Erbsen, Salz", ["Pea Protein"], "Plain erbsen -> Pea Protein"),  
        ("Erbsenproteinisolat", ["Pea Protein Isolate"], "Pea isolat -> Isolate ONLY"),
        ("Whey protein, wheat flour", ["Whey Concentrate", "Wheat Protein"], "Mixed detection"),
        ("Weizenvollkornmehl", ["Wheat Protein"], "German wheat compound"),
        ("Tofu, Salz", ["Soy Protein"], "Tofu matches"),
        ("Molkenproteinisolat, Weizen", ["Whey Isolate", "Wheat Protein"], "Isolate + wheat"),
    ]
    
    print("Testing with refined logic...\n")
    passed = failed = 0
    
    for ingredients, expected, desc in tests:
        matches = find_matches(ingredients)
        got = [m[0] for m in matches]
        ok = (set(got) == set(expected)) if expected else (len(got) == 0)
        
        status = "✅" if ok else "❌"
        print(f"{status} {desc}")
        if not ok:
            print(f"   Input: '{ingredients}'")
            print(f"   Expected: {expected}")
            print(f"   Got: {got}")
        
        passed += ok
        failed += not ok
    
    print(f"\n{'='*40}")
    print(f"Results: {passed} passed, {failed} failed")
    return failed == 0

if __name__ == "__main__":
    import sys
    sys.exit(0 if test() else 1)
