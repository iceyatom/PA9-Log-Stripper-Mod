@echo off
rem Builds the release jar for whichever Minecraft version this checkout targets.
rem Versions come from gradle.properties, so this works unchanged on every branch
rem (main / 26.3-Release / 26.1 ...). Double-click it, or run "build-jar.bat --no-pause".
rem
rem The finished jar is also copied into releases\ (ignored by git, like build\).

setlocal EnableDelayedExpansion
cd /d "%~dp0"

set "BASE_NAME=logstripper"
for /f "usebackq tokens=1,* delims==" %%A in ("gradle.properties") do (
	if "%%A"=="minecraft_version" set "MC_VERSION=%%B"
	if "%%A"=="mod_version" set "MOD_VERSION=%%B"
)

if not defined MC_VERSION (
	echo Could not read minecraft_version from gradle.properties.
	goto :fail
)

set "JAR_NAME=%BASE_NAME% - %MC_VERSION% - %MOD_VERSION%.jar"
echo Building %JAR_NAME%
echo   Minecraft %MC_VERSION%, mod version %MOD_VERSION%
echo.

rem Full path: some shells (NoDefaultCurrentDirectoryInExePath) won't run programs from the current folder by bare name.
call "%~dp0gradlew.bat" build
if errorlevel 1 (
	echo.
	echo BUILD FAILED - see the errors above.
	goto :fail
)

if not exist "build\libs\%JAR_NAME%" (
	echo.
	echo Build finished but "build\libs\%JAR_NAME%" was not found.
	goto :fail
)

if not exist "releases" mkdir "releases"
copy /y "build\libs\%JAR_NAME%" "releases\" >nul
if errorlevel 1 (
	echo.
	echo Could not copy the jar into releases\.
	goto :fail
)

echo.
echo Done. Jar:
for %%F in ("releases\%JAR_NAME%") do echo   %%~fF
echo.
echo Copy "%JAR_NAME%" into .minecraft\mods.
call :maybe_pause %1
exit /b 0

:fail
call :maybe_pause %1
exit /b 1

:maybe_pause
if /i not "%~1"=="--no-pause" pause
exit /b 0
