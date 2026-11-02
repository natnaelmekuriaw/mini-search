@echo off
setlocal
set "MINISEARCH_JAVA=java.exe"
if defined JAVA_HOME set "MINISEARCH_JAVA=%JAVA_HOME%\bin\java.exe"
"%MINISEARCH_JAVA%" -jar "%~dp0..\lib\minisearch.jar" %*
exit /b %ERRORLEVEL%
