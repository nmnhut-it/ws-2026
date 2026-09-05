@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist out mkdir out
echo Bien dich / compiling...
javac -encoding UTF-8 -cp lib\gamelogic.jar -d out src\*.java
if errorlevel 1 (
  echo.
  echo !! LOI BIEN DICH -- doc dong "GameServer.java:<so dong>" o tren, sua, roi chay lai.
  pause
  exit /b 1
)
set PORT=%1
if "%PORT%"=="" set PORT=9000
echo Chay / starting. Ctrl-C de dung.
echo.
java -Dstdout.encoding=UTF-8 -Dstderr.encoding=UTF-8 -Dbeast.threshold=40 -cp out;lib\gamelogic.jar GameServer %PORT%
pause
