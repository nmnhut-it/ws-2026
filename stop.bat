@echo off
chcp 65001 >nul
cd /d "%~dp0"
set PORT=%1
if "%PORT%"=="" set PORT=9000
set KILLED=
for /f "tokens=5" %%p in ('netstat -ano -p tcp ^| findstr ":%PORT% "') do call :kill %%p
if defined KILLED (
  echo Server on port %PORT% stopped.
) else (
  echo Nothing to stop -- no java server is using port %PORT%.
)
pause
exit /b 0

:kill
tasklist /nh /fi "PID eq %1" 2>nul | findstr /i "java" >nul || goto :eof
echo Stopping java PID %1 on port %PORT%
taskkill /PID %1 /F >nul 2>nul
set KILLED=1
goto :eof
