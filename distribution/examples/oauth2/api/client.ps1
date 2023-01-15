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
    
    $postParams = @{grant_type="password";username=$username;password=$password;client_id=$clientId;client_secret=$clientSecret}
    return Invoke-WebRequest -Uri $tokenEndpoint -Method POST -Body $postParams | ConvertFrom-Json
}

function sendRequestToTarget($tokenResult){
    $headers = @{"Authorization"=$tokenResult.token_type + " " + $tokenResult.access_token}
    return Invoke-WebRequest -Uri $target -Headers $headers
}

$tokenEndpointResult = getToken
$result = sendRequestToTarget  $tokenEndpointResult
$result.StatusDescription