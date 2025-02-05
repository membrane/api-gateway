$required_version = "21"

function Start-MembraneService {
    param(
        [string]$membrane_home,
        [string[]]$ExtraArgs
    )

    $env:MEMBRANE_HOME = $membrane_home
    $CLASSPATH = "$membrane_home\conf;$membrane_home\lib\*"
    Write-Host "Starting: $membrane_home CL: $CLASSPATH"
    & java -cp "$CLASSPATH" com.predic8.membrane.core.cli.RouterCLI @ExtraArgs
}

function Find-MembraneDirectory {
    param([string]$current)

    while ($current -ne "") {
        if ((Test-Path "$current\conf") -and (Test-Path "$current\lib")) {
            return $current
        }
        $current = Split-Path -Parent $current
    }
    return $null
}

function Start-Membrane {
    param(
       [string[]]$ExtraArgs
    )
    $membrane_home = Find-MembraneDirectory (Get-Location).Path
    if ($membrane_home) {
        Start-MembraneService $membrane_home @ExtraArgs
    } else {
        Write-Host "Could not start Membrane. Ensure the directory structure is correct."
    }
}

try {
    $null = Get-Command java -ErrorAction Stop
} catch {
    Write-Host "Java is not installed. Membrane needs at least Java $required_version."
    exit 1
}

try {
    $version_output = & java -version 2>&1
    $version_line = $version_output | Where-Object { $_ -match "version" } | Select-Object -First 1

    if (-not $version_line) {
        Write-Host "WARNING: Could not determine Java version. Make sure Java version is at least $required_version. Proceeding anyway..."
        Start-Membrane @args
        exit 0
    }

    $full_version = $version_line -replace '.*version "([^"]+)".*', '$1'
    $current_version = $full_version -replace '\..*$', ''

    if ($current_version -match '^\d+$') {
        if ([int]$current_version -ge [int]$required_version) {
            Start-Membrane @args
            exit 0
        } else {
            Write-Host "Java version mismatch: Required=$required_version, Installed=$full_version"
            exit 1
        }
    } else {
        Write-Host "WARNING: Could not parse Java version. Make sure your Java version is at least $required_version. Proceeding anyway..."
        Start-Membrane @args
        exit 0
    }
} catch {
    Write-Host "Error checking Java version: $_"
    exit 1
}