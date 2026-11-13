$ErrorActionPreference = 'Stop'
$creatorRoot = Split-Path -Parent $PSScriptRoot
$workspaceRoot = Split-Path -Parent $creatorRoot
$creatorBackend = Join-Path $creatorRoot 'backend'
$platformBackend = Join-Path $workspaceRoot 'sinx-platform/backend'
$localRoot = Join-Path $creatorRoot '.local'
New-Item -ItemType Directory -Path $localRoot -Force | Out-Null
$classpathFile = Join-Path $localRoot 'test-classpath.txt'

# Both test services use disposable H2 databases and public fixture credentials.
Push-Location $creatorBackend
try {
    & mvn -q '-DskipTests' package dependency:build-classpath '-Dmdep.includeScope=test' "-Dmdep.outputFile=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Creator test artifact preparation failed.' }
} finally { Pop-Location }

Push-Location $platformBackend
try {
    & mvn -q test '-Dtest=SsoHttpIntegrationTest' "-Dcreator.http.classpath=$classpathFile"
    if ($LASTEXITCODE -ne 0) { throw 'Real HTTP SSO integration test failed.' }
} finally { Pop-Location }
