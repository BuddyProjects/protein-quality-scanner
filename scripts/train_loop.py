#!/usr/bin/env python3
"""
Protein Detection Training Loop

Automatically trains the protein detection algorithm by:
1. Fetching random products from OpenFoodFacts
2. Running Claude Code to evaluate and add test cases
3. Running tests and fixing issues
4. Repeating until target test count reached

Usage:
    python train_loop.py                    # Run training loop
    python train_loop.py --iterations 10   # Run 10 iterations
    python train_loop.py --target-tests 30 # Run until 30 test cases
"""

import subprocess
import json
import sys
import time
import argparse
from pathlib import Path

PROJECT_DIR = Path(__file__).parent.parent
TEST_CASES_FILE = PROJECT_DIR / "app/src/test/resources/protein_test_cases.json"

def count_test_cases():
    """Count current number of test cases"""
    if TEST_CASES_FILE.exists():
        with open(TEST_CASES_FILE, "r", encoding="utf-8") as f:
            data = json.load(f)
            return len(data.get("test_cases", []))
    return 0

def run_tests():
    """Run Gradle tests and return True if all pass"""
    print("\n--- Running Tests ---")
    result = subprocess.run(
        ["gradlew.bat", ":app:testDebugUnitTest"],
        cwd=PROJECT_DIR,
        capture_output=True,
        text=True,
        shell=True
    )

    passed = result.returncode == 0
    if passed:
        print("All tests PASSED")
    else:
        print("Tests FAILED")
        # Extract failure info
        if "FAILED" in result.stdout:
            for line in result.stdout.split("\n"):
                if "FAILED" in line or "AssertionError" in line:
                    print(f"  {line.strip()}")

    return passed

def run_claude_iteration(iteration_num):
    """Run one Claude Code training iteration"""
    print(f"\n{'='*60}")
    print(f"ITERATION {iteration_num}")
    print(f"{'='*60}")

    prompt = """Execute ONE protein detection training iteration:

1. First, fetch a random product:
   python scripts/fetch_random_product.py --protein

2. Analyze the ingredients from the output and determine:
   - expected_detected: proteins that ARE actual ingredients
   - expected_not_detected: proteins from trace warnings, allergens, or emulsifiers (like soya lecithin)

3. Add the test case to app/src/test/resources/protein_test_cases.json
   - Use a unique id like "training_<barcode>"
   - Fill in expected_detected and expected_not_detected correctly

4. Run tests:
   gradlew.bat :app:testDebugUnitTest

5. If tests fail:
   - Read the test report to see what failed
   - Fix ProteinDatabase.kt (add keywords, fix trace warning detection, etc.)
   - Run tests again until they pass

6. When done, output: ITERATION_COMPLETE

Remember:
- "May contain" / "traces of" / "produced in a facility" = trace warnings, don't detect
- "soya lecithin" = emulsifier, not protein
- Only detect actual protein ingredients"""

    try:
        result = subprocess.run(
            [
                "claude",
                "-p", prompt,
                "--allowedTools", "Bash,Read,Write,Edit,Glob,Grep"
            ],
            cwd=PROJECT_DIR,
            timeout=600,  # 10 minute timeout per iteration
            capture_output=True,
            text=True
        )

        print(result.stdout[-2000:] if len(result.stdout) > 2000 else result.stdout)

        return "ITERATION_COMPLETE" in result.stdout or result.returncode == 0

    except subprocess.TimeoutExpired:
        print("Iteration timed out!")
        return False
    except FileNotFoundError:
        print("Error: 'claude' command not found. Make sure Claude Code CLI is installed.")
        return False

def main():
    parser = argparse.ArgumentParser(description="Protein Detection Training Loop")
    parser.add_argument("--iterations", type=int, default=10, help="Number of iterations to run")
    parser.add_argument("--target-tests", type=int, default=0, help="Target number of test cases (0 = use iterations)")
    parser.add_argument("--dry-run", action="store_true", help="Just show what would be done")
    args = parser.parse_args()

    initial_count = count_test_cases()
    print(f"""
{'='*60}
PROTEIN DETECTION TRAINING LOOP
{'='*60}
Project: {PROJECT_DIR}
Current test cases: {initial_count}
Target iterations: {args.iterations}
Target test cases: {args.target_tests if args.target_tests > 0 else 'N/A'}
{'='*60}
""")

    if args.dry_run:
        print("DRY RUN - would execute training loop")
        return

    # Initial test run
    print("Running initial tests...")
    if not run_tests():
        print("\nWARNING: Initial tests failing. Fix them before training.")
        response = input("Continue anyway? (y/n): ")
        if response.lower() != 'y':
            return

    successful_iterations = 0

    for i in range(1, args.iterations + 1):
        # Check if we've reached target test count
        if args.target_tests > 0:
            current_count = count_test_cases()
            if current_count >= args.target_tests:
                print(f"\nReached target of {args.target_tests} test cases!")
                break

        success = run_claude_iteration(i)

        if success:
            successful_iterations += 1
        else:
            print(f"Iteration {i} had issues, continuing...")

        # Brief pause between iterations
        time.sleep(2)

    # Final summary
    final_count = count_test_cases()
    print(f"""
{'='*60}
TRAINING COMPLETE
{'='*60}
Iterations run: {args.iterations}
Successful iterations: {successful_iterations}
Test cases: {initial_count} -> {final_count} (+{final_count - initial_count})
{'='*60}
""")

    # Final test verification
    print("Running final test verification...")
    run_tests()

if __name__ == "__main__":
    main()
