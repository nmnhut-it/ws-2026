@echo off
chcp 65001 >nul
cd /d "%~dp0"
if not exist tests\out mkdir tests\out
echo Bien dich test / compiling tests...
javac -encoding UTF-8 -cp lib\gamelogic.jar -d tests\out src\*.java tests\src\Tests.java
if errorlevel 1 ( pause & exit /b 1 )
echo.
java -Dstdout.encoding=UTF-8 -cp tests\out;lib\gamelogic.jar Tests
pause
