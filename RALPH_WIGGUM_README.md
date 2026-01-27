# Ralph Wiggum Training Loop

Automated protein detection training using the Ralph Wiggum iterative technique.

## Quick Start

### Step 1: Create a New Run
```bash
python ralph_loop.py
```
This automatically:
1. **Archives completed runs** to `runs_archived/` (prevents AI confusion)
2. Fetches 100 products from OpenFoodFacts
3. Creates new run folder with:
   - `runs/run_YYYYMMDD_NN/products.json` - Products to process
   - `runs/run_YYYYMMDD_NN/HISTORY.md` - Progress tracking
4. Updates `PROMPT.md` - Instructions for the AI

### Step 2: Start the Loop
```
/ralph-loop:ralph-loop "Read PROMPT.md and follow all instructions." --max-iterations 150 --completion-promise "TRAINING COMPLETE"
```

### Step 3: Monitor Progress
Check `runs/run_YYYYMMDD_NN/HISTORY.md` for:
- Products processed
- Algorithm fixes made
- Test pass/fail status

### Step 4: Cancel if Needed
```
/cancel-ralph
```

## How It Works

```
┌─────────────────────────────────────────────────────┐
│  1. Read next product from products.json            │
│  2. Analyze ingredients (proteins vs trace/oil)     │
│  3. Add test case to protein_test_cases.json        │
│  4. Run tests: gradlew.bat :app:testDebugUnitTest   │
│  5. If fail → fix ProteinDatabase.kt → re-test      │
│  6. Log to HISTORY.md                               │
│  7. Repeat until all 100 done                       │
└─────────────────────────────────────────────────────┘
```

## Files

| File | Purpose |
|------|---------|
| `ralph_loop.py` | Creates new runs, fetches products |
| `evaluator.py` | Runs tests, reports pass/fail |
| `PROMPT.md` | AI instructions (auto-generated) |
| `runs/*/products.json` | Pre-fetched products |
| `runs/*/HISTORY.md` | Iteration log |

## Key Rules for Detection

| Detect | Don't Detect |
|--------|--------------|
| Actual ingredients (milk, eggs, soy) | Trace warnings ("may contain") |
| Protein sources | Lecithin (emulsifier) |
| Meat, fish, legumes | Oils (soybean oil) |
| Nuts, grains | Starch (amidon, stärke) |

## Manual Test Run
```bash
gradlew.bat :app:testDebugUnitTest
```

## Completion

Loop ends when:
- All 100 products processed + tests pass → `<promise>TRAINING COMPLETE</promise>`
- Max iterations (150) reached
- Manual cancel with `/cancel-ralph`

## Tips

1. **Language consistency**: When adding keywords, add EN/DE/FR equivalents
2. **Check HISTORY.md**: See what's been tried before
3. **Run evaluator**: `python evaluator.py` to check test status
4. **New run**: Just run `python ralph_loop.py` again for fresh products
