"""
Evaluator for Ralph Wiggum Loop - Protein Detection Training

Runs the gradle test suite and reports pass/fail status.
"""

import subprocess
import sys
import re
from pathlib import Path

PROJECT_ROOT = Path(__file__).parent


def run_tests() -> tuple[bool, str]:
    """Run gradle tests and return (success, output)."""
    try:
        result = subprocess.run(
            ["powershell.exe", "-Command", ".\\gradlew.bat :app:testDebugUnitTest 2>&1"],
            cwd=PROJECT_ROOT,
            capture_output=True,
            text=True,
            timeout=300
        )

        output = result.stdout + result.stderr

        # Check for success
        if "BUILD SUCCESSFUL" in output:
            return True, output
        else:
            return False, output

    except subprocess.TimeoutExpired:
        return False, "ERROR: Test timeout (5 minutes exceeded)"
    except Exception as e:
        return False, f"ERROR: {str(e)}"


def extract_test_count(output: str) -> tuple[int, int]:
    """Extract passed/failed counts from test output."""
    # Look for patterns like "165 passed, 0 failed"
    match = re.search(r'(\d+)\s+passed,\s+(\d+)\s+failed', output)
    if match:
        return int(match.group(1)), int(match.group(2))
    return 0, 0


def evaluate():
    """Run evaluation and print results."""
    print("Running protein detection tests...")
    print("-" * 50)

    success, output = run_tests()
    passed, failed = extract_test_count(output)

    print(f"\nResults:")
    print(f"  Tests passed: {passed}")
    print(f"  Tests failed: {failed}")
    print(f"  Overall: {'SUCCESS' if success else 'FAIL'}")

    if not success:
        # Print relevant error lines
        print("\nError details:")
        for line in output.split('\n'):
            if 'FAILED' in line or 'AssertionError' in line or 'expected' in line.lower():
                print(f"  {line.strip()}")

    return success


if __name__ == "__main__":
    success = evaluate()
    sys.exit(0 if success else 1)
