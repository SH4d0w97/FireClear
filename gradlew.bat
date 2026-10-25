@rem Gradle wrapper script for Windows
@rem https://gradle.org
@if "%DEBUG%"=="" @echo off
@rem Set local scope for the variables with windows NT shell
setlocal
set DIRNAME=%~dp0
if "%DIRNAME%"=="" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%

@rem Execute Gradle
"%JAVA_HOME%\bin\java.exe" -classpath "%APP_HOME%\gradle\wrapper\gradle-wrapper.jar" org.gradle.wrapper.GradleWrapperMain %*

