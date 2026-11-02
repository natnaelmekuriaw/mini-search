[CmdletBinding()]
param([switch]$Release)

$ErrorActionPreference = 'Stop'

function Test-Distribution {
    param([string]$Java, [string]$Root, [string]$Name, [string]$Version)

    $target = (Resolve-Path (Join-Path $Root 'target')).Path
    $stem = "$Name-$Version"
    $artifacts = @("$stem.jar", "$stem-bin.zip", "$stem-bin.tar.gz")
    foreach ($artifact in $artifacts) {
        if (-not (Test-Path -LiteralPath (Join-Path $target $artifact) -PathType Leaf)) {
            throw "Release artifact is missing: $artifact"
        }
    }
    $tar = Get-Command tar -CommandType Application -ErrorAction SilentlyContinue
    if (-not $tar) { throw 'tar is required to verify the release archives.' }
    $scratch = Join-Path $target ("release-smoke-" + [guid]::NewGuid().ToString('N'))
    New-Item -ItemType Directory -Path $scratch | Out-Null
    try {
        $zipRoot = Join-Path $scratch 'zip'
        Expand-Archive -LiteralPath (Join-Path $target "$stem-bin.zip") -DestinationPath $zipRoot
        $tarRoot = Join-Path $scratch 'tar'
        New-Item -ItemType Directory -Path $tarRoot | Out-Null
        & $tar.Source -xzf (Join-Path $target "$stem-bin.tar.gz") -C $tarRoot
        if ($LASTEXITCODE -ne 0) { throw 'Unable to extract the tar distribution.' }
        foreach ($extracted in @($zipRoot, $tarRoot)) {
            $distribution = Join-Path $extracted $stem
            foreach ($required in @('LICENSE', 'README.md', 'docs/openapi.yaml', 'examples/documents/web.txt', 'lib/minisearch.jar')) {
                if (-not (Test-Path -LiteralPath (Join-Path $distribution $required) -PathType Leaf)) {
                    throw "Distribution file is missing: $required"
                }
            }
            if ($env:OS -eq 'Windows_NT') {
                $versionText = & (Join-Path $distribution 'bin/minisearch.cmd') --version
            } else {
                $launcher = Join-Path $distribution 'bin/minisearch'
                if ($extracted -eq $tarRoot) { $versionText = & $launcher --version }
                else { $versionText = & sh $launcher --version }
            }
            if ($LASTEXITCODE -ne 0 -or $versionText -ne "MiniSearch $Version") {
                throw 'Extracted distribution launcher check failed.'
            }
            Test-PackagedJar -Java $Java -Jar (Join-Path $distribution 'lib/minisearch.jar') -Root $Root
        }
        $checksums = foreach ($artifact in $artifacts) {
            $hash = (Get-FileHash -LiteralPath (Join-Path $target $artifact) -Algorithm SHA256).Hash.ToLowerInvariant()
            "$hash  $artifact"
        }
        [IO.File]::WriteAllText((Join-Path $target 'SHA256SUMS'),
            (($checksums -join [char]10) + [char]10), [Text.UTF8Encoding]::new($false))
    }
    finally {
        $resolved = (Resolve-Path -LiteralPath $scratch).Path
        if ((Split-Path $resolved -Parent) -ne $target) { throw 'Unexpected release workspace path.' }
        Remove-Item -LiteralPath $resolved -Recurse -Force
    }
}

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
    $stepCount = if ($Release) { 4 } else { 3 }
    Write-Host "[1/$stepCount] Locating JDK 21 or newer"
    $tools = Get-JavaTools -RequireCompiler
    Write-Host "Using Java $($tools.MajorVersion) from $($tools.Java)"

    Write-Host "[2/$stepCount] Bootstrapping Maven, compiling, and running project tests"
    $mavenArguments = @('--batch-mode', '--no-transfer-progress', 'clean', 'verify')
    if ($Release) { $mavenArguments += '-Prelease' }
    if ($env:OS -eq 'Windows_NT') {
        & (Join-Path $repositoryRoot 'mvnw.cmd') @mavenArguments
    } else {
        & sh (Join-Path $repositoryRoot 'mvnw') @mavenArguments
    }
    if ($LASTEXITCODE -ne 0) {
        throw "Maven verification failed with exit code $LASTEXITCODE."
    }

    Write-Host "[3/$stepCount] Checking the executable JAR"
    [xml]$project = Get-Content -LiteralPath (Join-Path $repositoryRoot 'pom.xml') -Raw
    $jar = Join-Path $repositoryRoot "target/$($project.project.artifactId)-$($project.project.version).jar"
    if (-not (Test-Path -LiteralPath $jar -PathType Leaf)) { throw 'Executable JAR was not found.' }
    & $tools.Java -jar $jar --help | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Executable JAR help check failed.' }
    & $tools.Java -jar $jar --version | Out-Host
    if ($LASTEXITCODE -ne 0) { throw 'Executable JAR version check failed.' }
    Test-PackagedJar -Java $tools.Java -Jar $jar -Root $repositoryRoot
    if ($Release) {
        Write-Host '[4/4] Verifying distributions and writing SHA-256 checksums'
        Test-Distribution -Java $tools.Java -Root $repositoryRoot -Name $project.project.artifactId -Version $project.project.version
    }
    Write-Host 'Verification passed.'
}
finally {
    Pop-Location
}
