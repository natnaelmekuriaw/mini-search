function Get-JavaTools {
    param([switch]$RequireCompiler)

    $windowsPlatform = $env:OS -eq 'Windows_NT'
    $suffix = if ($windowsPlatform) { '.exe' } else { '' }
    if (-not $env:JAVA_HOME -and $windowsPlatform) {
        $registry = Get-ItemProperty 'HKLM:\SOFTWARE\JavaSoft\JDK' -ErrorAction SilentlyContinue
        if ($registry -and $registry.CurrentVersion) {
            $jdk = Get-ItemProperty "HKLM:\SOFTWARE\JavaSoft\JDK\$($registry.CurrentVersion)" -ErrorAction SilentlyContinue
            if ($jdk -and (Test-Path -LiteralPath (Join-Path $jdk.JavaHome 'bin/javac.exe'))) {
                $env:JAVA_HOME = $jdk.JavaHome
            }
        }
    }

    $java = if ($env:JAVA_HOME) {
        Join-Path $env:JAVA_HOME "bin/java$suffix"
    } else {
        (Get-Command "java$suffix" -CommandType Application -ErrorAction SilentlyContinue).Source
    }
    if (-not $java -or -not (Test-Path -LiteralPath $java -PathType Leaf)) {
        throw 'Java 21 or newer was not found. Set JAVA_HOME to a Java installation.'
    }
    $runtimeVersion = (& $java --version 2>&1) -join [Environment]::NewLine
    if ($LASTEXITCODE -ne 0 -or $runtimeVersion -notmatch '(?m)^(?:openjdk|java) (\d+)') {
        throw 'Unable to determine the Java runtime version.'
    }
    $major = [int]$Matches[1]
    if ($major -lt 21) { throw 'Java 21 or newer is required.' }

    $compiler = $null
    if ($RequireCompiler) {
        $compiler = if ($env:JAVA_HOME) {
            Join-Path $env:JAVA_HOME "bin/javac$suffix"
        } else {
            (Get-Command "javac$suffix" -CommandType Application -ErrorAction SilentlyContinue).Source
        }
        if (-not $compiler -or -not (Test-Path -LiteralPath $compiler -PathType Leaf)) {
            throw 'A JDK is required to build MiniSearch. Set JAVA_HOME to a JDK installation.'
        }
        $compilerVersion = (& $compiler -version 2>&1) -join [Environment]::NewLine
        if ($LASTEXITCODE -ne 0 -or $compilerVersion -notmatch 'javac (\d+)') {
            throw 'Unable to determine the Java compiler version.'
        }
        if ([int]$Matches[1] -ne $major) {
            throw 'java and javac must use the same JDK version. Set JAVA_HOME explicitly.'
        }
    }
    [pscustomobject]@{ Java = $java; Compiler = $compiler; MajorVersion = $major }
}
