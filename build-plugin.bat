@echo off
set "JAVA_HOME=%~dp0tools\jdk17\jdk-17.0.12+7"
"%~dp0tools\maven\bin\mvn.cmd" clean package %*
