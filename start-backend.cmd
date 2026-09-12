@echo off
setlocal
cd /d "%~dp0"
title Campus Platform - Backend

set "CAMPUS_JAVA="
if defined JAVA_HOME if exist "%JAVA_HOME%\bin\java.exe" set "CAMPUS_JAVA=%JAVA_HOME%\bin\java.exe"
if not defined CAMPUS_JAVA if exist "C:\Program Files\Java\jdk-17\bin\java.exe" set "CAMPUS_JAVA=C:\Program Files\Java\jdk-17\bin\java.exe"
if not defined CAMPUS_JAVA for /f "delims=" %%J in ('where java 2^>nul') do if not defined CAMPUS_JAVA set "CAMPUS_JAVA=%%J"

if not defined CAMPUS_JAVA (
  echo [ERROR] Java was not found. Install JDK 17 or set JAVA_HOME.
  pause
  exit /b 1
)
if not exist "backend\release\campus-platform.jar" (
  echo [ERROR] backend\release\campus-platform.jar was not found.
  pause
  exit /b 1
)
if not exist "backend\src\main\resources\application.properties" (
  echo [ERROR] Database configuration file was not found.
  pause
  exit /b 1
)

echo Starting backend at http://127.0.0.1:8080 ...
"%CAMPUS_JAVA%" -Dfile.encoding=UTF-8 -jar "backend\release\campus-platform.jar" --server.address=127.0.0.1 --spring.config.additional-location=optional:file:./backend/src/main/resources/application.properties
pause
