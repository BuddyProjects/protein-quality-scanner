@echo off
setlocal enabledelayedexpansion

REM ============================================
REM Protein Detection Training Loop
REM Runs Claude Code repeatedly to train the algorithm
REM ============================================

set PROJECT_DIR=%~dp0..
set MAX_ITERATIONS=20
set CURRENT_ITERATION=0

echo ============================================
echo PROTEIN DETECTION TRAINING LOOP
echo ============================================
echo Project: %PROJECT_DIR%
echo Max iterations: %MAX_ITERATIONS%
echo.

cd /d "%PROJECT_DIR%"

:LOOP
set /a CURRENT_ITERATION+=1
echo.
echo ============================================
echo ITERATION %CURRENT_ITERATION% of %MAX_ITERATIONS%
echo ============================================

REM Run Claude Code with the training prompt
claude -p "Execute ONE training iteration: 1) Run: python scripts/fetch_random_product.py --protein 2) Analyze the ingredients and determine expected_detected and expected_not_detected 3) Add the test case to app/src/test/resources/protein_test_cases.json 4) Run: gradlew.bat :app:testDebugUnitTest 5) If tests fail, fix ProteinDatabase.kt and rerun tests 6) Report: ITERATION_COMPLETE when done, or NEEDS_FIX if stuck" --allowedTools "Bash,Read,Write,Edit,Glob,Grep"

REM Check if we should continue
if %CURRENT_ITERATION% geq %MAX_ITERATIONS% (
    echo.
    echo ============================================
    echo TRAINING COMPLETE - Reached max iterations
    echo ============================================
    goto END
)

REM Small pause between iterations
timeout /t 2 /nobreak > nul

goto LOOP

:END
echo.
echo Running final test to verify all tests pass...
call gradlew.bat :app:testDebugUnitTest

echo.
echo ============================================
echo TRAINING SESSION FINISHED
echo Iterations completed: %CURRENT_ITERATION%
echo ============================================

endlocal
