@echo off
rem Builds the app if needed (or with --rebuild), starts it on localhost and opens the browser.
rem Extra JVM options can be passed via JAVA_OPTS, e.g. set JAVA_OPTS=-Drmm.configDir=C:\temp\rmm
setlocal
cd /d "%~dp0"
set JAR=server\build\libs\rimworld-mod-manager.jar
if "%~1"=="--rebuild" goto build
if exist "%JAR%" goto run
:build
call gradlew.bat :server:shadowJar -q || exit /b 1
:run
java %JAVA_OPTS% -Drmm.openBrowser=true -jar "%JAR%"
