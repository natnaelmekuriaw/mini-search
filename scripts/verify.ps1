$ErrorActionPreference = 'Stop'

function Test-PackagedJar {
    param([string]$Java, [string]$Jar, [string]$Root)

    $target = (Resolve-Path (Join-Path $Root 'target')).Path
    $scratch = Join-Path $target ("smoke-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $scratch | Out-Null
    try {
        Copy-Item -LiteralPath (Join-Path $Root 'examples/documents') -Destination $scratch -Recurse
        $source = Join-Path $scratch 'documents'
        $snapshot = Join-Path $scratch 'index.bin'
        $created = & $Java -jar $Jar index --source $source --index $snapshot
        if ($LASTEXITCODE -ne 0 -or $created -notmatch '5 added') { throw 'Index creation smoke check failed.' }
        $results = (& $Java -jar $Jar search --index $snapshot --query '(java OR kotlin) -draft type:md path:guides/') -join [Environment]::NewLine
        if ($LASTEXITCODE -ne 0 -or $results -notmatch '2 results' -or $results -notmatch 'guides/java.md' -or $results -notmatch 'guides/kotlin.md') {
            throw 'Search smoke check failed.'
        }
        $suggestions = (& $Java -jar $Jar suggest --index $snapshot --prefix jav) -join ','
        if ($LASTEXITCODE -ne 0 -or $suggestions -ne 'java,javascript') { throw 'Suggestion smoke check failed.' }
        'Rust search engine tutorial.' | Set-Content -LiteralPath (Join-Path $source 'rust.txt') -Encoding utf8
        $refreshed = & $Java -jar $Jar index --source $source --index $snapshot
        if ($LASTEXITCODE -ne 0 -or $refreshed -notmatch '1 added, 0 changed, 0 removed, 5 unchanged') {
            throw 'Incremental refresh smoke check failed.'
        }
        $rust = (& $Java -jar $Jar search --index $snapshot --query rust) -join [Environment]::NewLine
        if ($LASTEXITCODE -ne 0 -or $rust -notmatch 'rust.txt') { throw 'Saved index smoke check failed.' }
    }
    finally {
        $resolved = (Resolve-Path -LiteralPath $scratch).Path
        if ((Split-Path $resolved -Parent) -ne $target) { throw 'Unexpected smoke workspace path.' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

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
    Test-PackagedJar -Java $tools.Java -Jar $jar -Root $repositoryRoot
    Write-Host 'Verification passed.'
}
finally {
    Pop-Location
}
