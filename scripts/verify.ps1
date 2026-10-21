$ErrorActionPreference = 'Stop'

$repositoryRoot = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Push-Location $repositoryRoot
try {
    Write-Host '[1/3] Locating JDK 21 or newer'
    if (-not $env:JAVA_HOME -and $env:OS -eq 'Windows_NT') {
        $jdkRegistry = Get-ItemProperty 'HKLM:\SOFTWARE\JavaSoft\JDK' -ErrorAction SilentlyContinue
        if ($jdkRegistry -and $jdkRegistry.CurrentVersion) {
            $registeredJdk = Get-ItemProperty "HKLM:\SOFTWARE\JavaSoft\JDK\$($jdkRegistry.CurrentVersion)" -ErrorAction SilentlyContinue
            if ($registeredJdk -and (Test-Path (Join-Path $registeredJdk.JavaHome 'bin\javac.exe'))) {
                $env:JAVA_HOME = $registeredJdk.JavaHome
            }
        }
    }

    $compiler = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME 'bin\javac.exe'
    } else {
        (Get-Command javac -ErrorAction SilentlyContinue).Source
    }
    if (-not $compiler -or -not (Test-Path $compiler)) {
        throw 'JDK 21 or newer is required (javac was not found).'
    }
    $compilerVersion = & $compiler -version 2>&1
    if ($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch 'javac (\d+)') {
        throw 'Unable to determine the JDK version.'
    }
    if ([int]$Matches[1] -lt 21) {
        throw 'JDK 21 or newer is required.'
    }
    Write-Host '[2/3] Bootstrapping Maven, compiling, and running project tests'
    & (Join-Path $repositoryRoot 'mvnw.cmd') --batch-mode --no-transfer-progress clean verify
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed with exit code $LASTEXITCODE."
    }

    Write-Host '[3/3] Checking the executable JAR'
    $java = Join-Path (Split-Path $compiler -Parent) 'java.exe'
    $jar = Join-Path $repositoryRoot 'target\minisearch-0.1.0-SNAPSHOT.jar'
    if (-not (Test-Path $java) -or -not (Test-Path $jar)) {
        throw 'The Java runtime or executable JAR was not found.'
    }
    & $java -jar $jar --help | Out-Host
    if ($LASTEXITCODE -ne 0) {
        throw "Executable JAR check failed with exit code $LASTEXITCODE."
    }

    Write-Host 'Verification passed.'
}
finally {
    Pop-Location
}
