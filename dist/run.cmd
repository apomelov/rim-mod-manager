@echo off
rem Starts RimWorld Mod Manager on localhost and opens it in the browser (Windows). Needs Java 21 or newer.
cd /d "%~dp0"
where java >nul 2>nul
if errorlevel 1 (
  echo Java not found. Install Java 21 or newer, e.g. from https://adoptium.net, and run this again.
  pause
  exit /b 1
)
java %JAVA_OPTS% -Drmm.openBrowser=true -jar rimworld-mod-manager.jar %*
if errorlevel 1 pause
