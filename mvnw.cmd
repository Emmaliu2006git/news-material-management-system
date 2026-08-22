@echo off
setlocal
set "WRAPPER_DIR=%~dp0"
set "MAVEN_VERSION=3.9.11"
set "MAVEN_HOME=%WRAPPER_DIR%.tools\apache-maven-%MAVEN_VERSION%"
set "ARCHIVE=%WRAPPER_DIR%.tools\apache-maven-%MAVEN_VERSION%-bin.zip"
if not exist "%MAVEN_HOME%\bin\mvn.cmd" (
  if not exist "%ARCHIVE%" powershell -NoProfile -Command "Invoke-WebRequest -UseBasicParsing https://archive.apache.org/dist/maven/maven-3/%MAVEN_VERSION%/binaries/apache-maven-%MAVEN_VERSION%-bin.zip -OutFile '%ARCHIVE%'"
  powershell -NoProfile -Command "Expand-Archive -Force '%ARCHIVE%' '%WRAPPER_DIR%.tools'"
)
call "%MAVEN_HOME%\bin\mvn.cmd" %*
