$ErrorActionPreference = 'Stop'

if (-not (Test-Path -LiteralPath 'pom.xml') -or
    -not (Test-Path -LiteralPath 'src\test\java\com\defermentsystem\context\WebApplicationContextDiagnosticTest.java')) {
    Write-Host 'FAIL: Run this checker from the Deferment project root.'
    exit 1
}

if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
    Write-Host 'FAIL: Maven was not found on PATH.'
    exit 1
}

Write-Host 'DEFERMENT CONFIGURATION CHECKER'
Write-Host 'Static XML checks and runtime Root/Child Spring context diagnostic'
Write-Host 'WARNING: Maven writes build output under target; application data is not changed.'

& mvn '-Dtest=StaticConfigurationTest,WebApplicationContextDiagnosticTest' '-DfailIfNoTests=true' '-DskipTests=false' '-Dmaven.test.skip=false' test
$mavenExit = $LASTEXITCODE

if ($mavenExit -eq 0) {
    Write-Host 'OVERALL RESULT: PASS'
    exit 0
}

Write-Host "OVERALL RESULT: FAIL (Maven exit code $mavenExit; see the checks and test errors above)"
exit 1
