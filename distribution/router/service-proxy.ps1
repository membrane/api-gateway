$required_version = "21"

function Start-MembraneRouter {
    param(
        [string]$membrane_home,
        [Parameter(ValueFromRemainingArguments=$true)]
        [string[]]$remainingArgs
    )

    $CLASSPATH = "$membrane_home\conf;$membrane_home\lib\*"
    Write-Host "Membrane Router running..."
    & java $env:JAVA_OPTS -classpath "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI @remainingArgs
}

function Resolve-MembraneHome {
    param([string]$PRG)

    while ((Get-Item -LiteralPath $PRG -ErrorAction SilentlyContinue).LinkType -eq "SymbolicLink") {
        $PRG = (Get-Item -LiteralPath $PRG).Target
    }

    $saveddir = Get-Location
    $MEMBRANE_HOME = Split-Path -Parent $PRG
    Set-Location -Path $MEMBRANE_HOME
    $MEMBRANE_HOME = (Get-Location).Path
    Set-Location -Path $saveddir

    return $MEMBRANE_HOME
}

if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    Write-Host "Java is not installed. Membrane needs at least Java $required_version."
    exit 1
}

$version_line = $null
java -version 2>&1 | ForEach-Object {
    if ($_ -match "version") {
        $version_line = $_
    }
}

if (-not $version_line) {
    Write-Host "WARNING: Could not determine Java version. Make sure Java version is at least $required_version. Proceeding anyway..."
    if (-not $env:MEMBRANE_HOME) {
        $env:MEMBRANE_HOME = Resolve-MembraneHome $PSCommandPath
    }
    Start-MembraneRouter $env:MEMBRANE_HOME $args
    exit 0
}

$full_version = [regex]::Match($version_line, '"([^"]+)"').Groups[1].Value
$current_version = [int]($full_version.Split('.')[0])

if ($current_version -ge $required_version) {
    Write-Host $env:MEMBRANE_HOME
    if (-not $env:MEMBRANE_HOME) {
        $env:MEMBRANE_HOME = Resolve-MembraneHome $PSCommandPath
    }
    Start-MembraneRouter $env:MEMBRANE_HOME $args
    exit 0
} else {
    Write-Host "Java version mismatch: Required=$required_version, Installed=$full_version"
    exit 1
}
