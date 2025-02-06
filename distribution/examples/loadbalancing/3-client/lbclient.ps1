$MEMBRANE_HOME = Join-Path $PWD "..\..\.."
if (-not $env:MEMBRANE_HOME) {
    $env:MEMBRANE_HOME = $MEMBRANE_HOME
}
Write-Output $env:MEMBRANE_HOME

function Check-MembraneHome {
    if (Test-Path "$env:MEMBRANE_HOME\membrane.cmd") {
        return $true
    }
    Write-Output "Please set the MEMBRANE_HOME environment variable to point to"
    Write-Output "the directory where you have extracted the Membrane software."
    exit
}

function Check-JavaHome {
    if (-not (Test-Path $env:JAVA_HOME)) {
        Write-Output "Please set the JAVA_HOME environment variable."
        exit
    }
}

if (-not (Check-MembraneHome)) {
    exit
}

Check-JavaHome

$classpath = "$env:JAVA_HOME\jre\lib\ext\*;$env:MEMBRANE_HOME\lib\*;$env:MEMBRANE_HOME\conf\*"
& java -cp $classpath com.predic8.membrane.balancer.client.LBNotificationClient $args