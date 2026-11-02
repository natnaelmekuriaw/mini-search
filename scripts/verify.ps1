$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'JavaTools.ps1')
Push-Location $repositoryRoot
try {
    Write-Host '[1/3] Locating JDK 21 or newer'
    $tools = Get-JavaTools -RequireCompiler
    Write-Host "Using Java $($tools.MajorVersion) from $($tools.Java)"

    Write-Host '[2/3] Bootstrapping Maven, compiling, and running project tests'
    $mavenArguments = @('--batch-mode', '--no-transfer-progress', 'clean', 'verify')
    if ($env:OS -eq 'Windows_NT') {
        & (Join-Path $repositoryRoot 'mvnw.cmd') @mavenArguments
    } else {
        & sh (Join-Path $repositoryRoot 'mvnw') @mavenArguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed with exit code $LASTEXITCODE."
    }

    Write-Host '[3/3] Checking the executable JAR'
    [xml]$project = Get-Content -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -Raw
    $jar = Join-Path $repositoryRoot "target/$($project.project.artifactId)-$($project.project.version).jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Executable JAR was not found.' }
    & $tools.Java -jar $jar --help | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Executable JAR help check failed.' }
    & $tools.Java -jar $jar --version | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Executable JAR version check failed.' }
    Write-Host 'Verification passed.'
}
finally {
    Pop-Location
}
