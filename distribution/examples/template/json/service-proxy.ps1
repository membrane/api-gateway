function Find-MembraneDirectory {
    param ([string]$currentPath)

    while ($currentPath -ne (Get-Item -Path "/").FullName) {
        if ((Test-Path "$currentPath\conf") -and (Test-Path "$currentPath\lib")) {
            return $currentPath
        }
        $currentPath = (Get-Item -Path $currentPath).Parent.FullName
    }
    return $null
}

$currentPath = (Get-Location).Path
$membraneHome = Find-MembraneDirectory -currentPath $currentPath

if (-not $membraneHome) {
    Write-Output "Could not start Membrane. Ensure the directory structure is correct."
    exit
}

$env:CLASSPATH = "$membraneHome\conf;$membraneHome\lib\*"

Write-Output "Membrane Router running..."

java -classpath "$env:CLASSPATH" com.predic8.membrane.core.cli.RouterCLI -c proxies.xml
