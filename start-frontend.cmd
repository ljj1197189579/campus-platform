@echo off
setlocal
cd /d "%~dp0frontend"
title Campus Platform - Frontend

if not exist "package.json" (
  echo [ERROR] frontend/package.json was not found.
  pause
  exit /b 1
)
where npm >nul 2>nul
if errorlevel 1 (
  echo [ERROR] npm was not found. Install Node.js LTS first.
  pause
  exit /b 1
)
if not exist "node_modules\vite\bin\vite.js" (
  echo Installing frontend dependencies, please wait...
  call npm ci
  if errorlevel 1 (
    echo [ERROR] Frontend dependency installation failed.
    pause
    exit /b 1
  )
)

echo Starting frontend at http://127.0.0.1:5173 ...
call npm run dev -- --host 127.0.0.1 --port 5173 --strictPort
pause
