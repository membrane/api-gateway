$MEMBRANE_HOME = "$pwd\..\..\.."
if (-not $env:MEMBRANE_HOME) {
    $env:MEMBRANE_HOME = $MEMBRANE_HOME
}
Write-Output $env:MEMBRANE_HOME
if (Test-Path "$env:MEMBRANE_HOME\service-proxy.ps1") {
    goto homeOk
}

:homeSet
if (Test-Path "$env:MEMBRANE_HOME\service-proxy.ps1") {
    goto homeOk
}
Write-Output "Please set the MEMBRANE_HOME environment variable to point to"
Write-Output "the directory where you have extracted the Membrane software."
exit

:javaHomeMissing
Write-Output "Please set the JAVA_HOME environment variable."
exit

:homeOk
if (-not (Test-Path $env:JAVA_HOME)) {
    goto javaHomeMissing
}
& java -cp "$env:JAVA_HOME\jre\lib\ext\*;$env:MEMBRANE_HOME\lib\*;$env:MEMBRANE_HOME\conf\*" com.predic8.membrane.balancer.client.LBNotificationClient $args