param (
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]$username,
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]$password
    )

$clientId = "abc"
$clientSecret = "def"
$tokenEndpoint = "http://localhost:7007/oauth2/token"
$target = "http://localhost:2000"

function getToken{ 
    Write-Host "1.) Requesting Token"
    Write-Host "POST $tokenEndpoint"
    $postParams = @{grant_type="password";username=$username;password=$password;client_id=$clientId;client_secret=$clientSecret}
    Write-Host $postParams
    Write-Host
    return Invoke-WebRequest -Uri $tokenEndpoint -Method POST -Body $postParams | ConvertFrom-Json
}

function sendRequestToTarget($tokenResult){
    Write-Host
    Write-Host "2.) Calling API"
    Write-Host "GET $target"
    $headers = @{"Authorization"=$tokenResult.token_type + " " + $tokenResult.access_token}
    Write-Host Authorization: $headers["Authorization"]
    Write-Host
    return Invoke-WebRequest -Uri $target -Headers $headers
}

$tokenEndpointResult = getToken
Write-Host "Got Token:" $tokenEndpointResult.access_token
Write-Host 
$result = sendRequestToTarget  $tokenEndpointResult
Write-Host "Got": $result.Content