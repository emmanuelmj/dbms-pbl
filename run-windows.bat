@echo off
setlocal
cd /d "%~dp0\gcet-placement-hub-java"
if exist "run-windows.bat" (
    call run-windows.bat %*
) else (
    echo [ERROR] gcet-placement-hub-java\run-windows.bat not found.
    pause
    exit /b 1
)
endlocal
