@echo off
chcp 65001 >nul
setlocal

set "BACKEND_DIR=E:\rental-backend"
set "JAVA17_HOME=C:\Program Files\Java\jdk-17"
set "MAVEN_CMD=%USERPROFILE%\.m2\wrapper\dists\apache-maven-3.9.15-bin\4rlcemksed9vjmkvgss0jpc4po\apache-maven-3.9.15\bin\mvn.cmd"

if not exist "%JAVA17_HOME%\bin\java.exe" (
  echo [ERROR] Java 17 was not found at: %JAVA17_HOME%
  echo Please install JDK 17 or update JAVA17_HOME in this script.
  pause
  exit /b 1
)

if not exist "%MAVEN_CMD%" (
  echo [ERROR] Maven was not found at: %MAVEN_CMD%
  echo Please check the Maven wrapper cache or update MAVEN_CMD in this script.
  pause
  exit /b 1
)

set "JAVA_HOME=%JAVA17_HOME%"
set "Path=%JAVA_HOME%\bin;%Path%"

:: Load .env environment variables
if exist "%BACKEND_DIR%\.env" (
  echo Loading environment variables from .env ...
  for /f "usebackq tokens=1,* delims==" %%a in ("%BACKEND_DIR%\.env") do (
    if not "%%a"=="" if not "%%a"=="#" if not "%%a"==";" set "%%a=%%b"
  )
)

echo Using JAVA_HOME=%JAVA_HOME%
java -version
echo.
echo Starting rental-platform on http://127.0.0.1:8080 ...
cd /d "%BACKEND_DIR%"
if errorlevel 1 (
  echo [ERROR] Cannot cd into backend dir: %BACKEND_DIR%
  pause
  exit /b 1
)
"%MAVEN_CMD%" -DskipTests spring-boot:run

endlocal
