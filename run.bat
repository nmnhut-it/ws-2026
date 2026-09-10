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
if not exist out mkdir out
echo Compiling...
javac -encoding UTF-8 -cp "lib\*" -d out src\*.java
if errorlevel 1 (
  echo.
  echo !! COMPILE ERROR -- read the "GameServer.java:<line>" line above, fix it, run again.
  pause
  exit /b 1
)
set PORT=%1
if "%PORT%"=="" set PORT=9000
echo Starting. Ctrl-C to stop.
echo.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dbeast.threshold=40 -cp "out;lib\*" GameServer %PORT%
pause
