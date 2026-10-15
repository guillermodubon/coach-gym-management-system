@echo off
setlocal

pushd "%~dp0backend"
if errorlevel 1 exit /b 1

call "%~dp0backend\gradlew.bat" %*
set "TASK_GRADLE_EXIT_CODE=%ERRORLEVEL%"
popd
exit /b %TASK_GRADLE_EXIT_CODE%
