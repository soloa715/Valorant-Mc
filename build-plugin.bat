@echo off
set "JAVA_HOME=%~dp0tools\jdk25\jdk-25.0.4.1+1"
"%~dp0tools\maven\bin\mvn.cmd" clean package %*
