@echo off
chcp 65001 >nul
cd /d "%~dp0"
where javac >nul 2>nul
if errorlevel 1 (
  echo !! NO JDK -- this machine has no javac command.
  echo    Install JDK 17 or newer: https://adoptium.net  ^(Latest LTS, default options^)
  echo    After installing, REOPEN this window and run again.
  pause
  exit /b 1
)
for /f "tokens=2 delims=. " %%v in ('javac -version 2^>^&1') do set JV=%%v
if not "%JV%"=="" if %JV% LSS 17 (
  echo !! JDK too old -- 17 or newer needed: https://adoptium.net
  pause
  exit /b 1
)
if not exist tests\out mkdir tests\out
echo Compiling tests...
javac -encoding UTF-8 -cp "lib\*" -d tests\out src\*.java tests\src\Tests.java tools\Report.java
if errorlevel 1 ( pause & exit /b 1 )
echo.
set SRV=
if not "%1"=="" set SRV=-Dserver=%1
java -Dstdout.encoding=UTF-8 %SRV% -cp "tests\out;lib\*" Tests
pause
