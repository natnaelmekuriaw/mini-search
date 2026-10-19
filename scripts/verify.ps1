$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repositoryRoot
try {
    Write-Host '[1/2] Checking required tools'
    if (-not (Get-Command javac -ErrorAction SilentlyContinue)) {
        throw 'JDK 21 or newer is required (javac was not found).'
    }
    if (-not (Get-Command mvn -ErrorAction SilentlyContinue)) {
        throw 'Maven 3.6.3 or newer is required (mvn was not found).'
    }

    Write-Host '[2/2] Compiling and running project tests'
    & mvn --batch-mode --no-transfer-progress clean verify
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed with exit code $LASTEXITCODE."
    }

    Write-Host 'Verification passed.'
}
finally {
    Pop-Location
}
