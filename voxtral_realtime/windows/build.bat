@echo off
echo === Building Voxtral Realtime Windows App ===
cd /d "%~dp0VoxtralRealtime"

echo.
echo [1/2] Restoring NuGet packages...
dotnet restore
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: dotnet restore failed
    exit /b 1
)

echo.
echo [2/2] Building Release...
dotnet build --configuration Release
if %ERRORLEVEL% NEQ 0 (
    echo ERROR: Build failed
    exit /b 1
)

echo.
echo === Build successful! ===
echo.
echo Run the app with:
echo   dotnet run --project VoxtralRealtime --configuration Release
echo.
echo Or directly:
echo   VoxtralRealtime\bin\Release\net8.0-windows\VoxtralRealtime.exe
