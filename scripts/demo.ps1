param([string]$Directory, [string]$Jar)

$ErrorActionPreference = 'Stop'
$root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
. (Join-Path $PSScriptRoot 'JavaTools.ps1')
$java = (Get-JavaTools).Java
if (-not $Jar) {
    [xml]$project = Get-Content -LiteralPath (Join-Path $root 'pom.xml') -Raw
    $Jar = Join-Path $root "target/$($project.project.artifactId)-$($project.project.version).jar"
}
if (-not (Test-Path -LiteralPath $Jar -PathType Leaf)) {
    throw 'Build the executable JAR first with ./scripts/verify.ps1.'
}
$Jar = (Resolve-Path -LiteralPath $Jar).Path
if (-not $Directory) {
    $Directory = Join-Path $root ("target/demo-" + [guid]::NewGuid().ToString('N'))
}
if (Test-Path -LiteralPath $Directory) { throw 'Choose a new demo directory; it must not already exist.' }
New-Item -ItemType Directory -Path $Directory -Force | Out-Null
$Directory = (Resolve-Path -LiteralPath $Directory).Path
Copy-Item -LiteralPath (Join-Path $root 'examples/documents') -Destination $Directory -Recurse
$source = Join-Path $Directory 'documents'
$snapshot = Join-Path $Directory 'index.bin'

function Invoke-DemoCommand {
    param([string[]]$Arguments)
    & $java -jar $Jar @Arguments | Out-Host
    if ($LASTEXITCODE -ne 0) { throw "Demo command failed: $($Arguments -join ' ')" }
}

Write-Host '[1/5] Index five sample documents'
Invoke-DemoCommand @('index', '--source', $source, '--index', $snapshot)
Write-Host '[2/5] Search Java documents with relevance scores and snippets'
Invoke-DemoCommand @('search', '--index', $snapshot, '--query', 'java')
Write-Host '[3/5] Combine alternatives, exclusion, and path filters'
Invoke-DemoCommand @('search', '--index', $snapshot, '--query', '(java OR kotlin) -draft path:guides/')
Write-Host '[4/5] Search a prefix and suggest matching indexed words'
Invoke-DemoCommand @('search', '--index', $snapshot, '--query', 'jav* type:txt')
Invoke-DemoCommand @('suggest', '--index', $snapshot, '--prefix', 'jav')
Write-Host '[5/5] Add one document and refresh the saved index'
'Rust search engine tutorial.' | Set-Content -LiteralPath (Join-Path $source 'rust.txt') -Encoding utf8
Invoke-DemoCommand @('index', '--source', $source, '--index', $snapshot)
Invoke-DemoCommand @('search', '--index', $snapshot, '--query', 'rust')
Write-Host "Demo files: $Directory"
Write-Host 'Start the API with:'
Write-Host ('& "' + $java + '" -jar "' + $Jar + '" serve --index "' + $snapshot + '"')
