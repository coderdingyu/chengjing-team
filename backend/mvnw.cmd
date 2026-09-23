@ECHO OFF
SETLOCAL
SET "MAVEN_HOME=%~dp0.mvn\apache-maven-3.9.9"
IF NOT EXIST "%MAVEN_HOME%\bin\mvn.cmd" (
  ECHO Downloading Maven 3.9.9...
  POWERSHELL -NoProfile -Command "Invoke-WebRequest -Uri 'https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/3.9.9/apache-maven-3.9.9-bin.zip' -OutFile '%TEMP%\maven399.zip'; Expand-Archive -Force '%TEMP%\maven399.zip' '%~dp0.mvn'"
)
CALL "%MAVEN_HOME%\bin\mvn.cmd" %*
