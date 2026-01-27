@echo off
REM Protein Algorithm Training Script for Windows
REM Usage: run_training.bat [command]
REM Commands:
REM   fetch  - Fetch new random product for training
REM   test   - Run protein detection tests
REM   status - Show training status
REM   auto   - Fetch product and run tests

cd /d "%~dp0"

if "%1"=="" (
    python train_protein_algorithm.py --status
    goto :eof
)

if "%1"=="fetch" (
    python train_protein_algorithm.py --fetch
    goto :eof
)

if "%1"=="test" (
    python train_protein_algorithm.py --test
    goto :eof
)

if "%1"=="status" (
    python train_protein_algorithm.py --status
    goto :eof
)

if "%1"=="auto" (
    python train_protein_algorithm.py --auto
    goto :eof
)

echo Unknown command: %1
echo Usage: run_training.bat [fetch^|test^|status^|auto]
