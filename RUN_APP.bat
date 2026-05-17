@echo off
setlocal
cd /d "%~dp0"
powershell -NoProfile -ExecutionPolicy Bypass -File "%~dp0run_app.ps1"
set EXIT_CODE=%ERRORLEVEL%
echo.
if not "%EXIT_CODE%"=="0" (
    echo Chuong trinh chua chay duoc. Xem canh bao/loi o tren.
) else (
    echo Chuong trinh da dong.
)
echo.
pause
endlocal
exit /b %EXIT_CODE%
