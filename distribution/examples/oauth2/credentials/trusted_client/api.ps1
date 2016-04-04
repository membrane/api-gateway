$clientId = "abc"
$clientSecret = "def"
$tokenEndpoint = "http://localhost:2000/oauth2/token"
$target = "http://localhost:2002"

function getToken{ 
    
    $postParams = @{grant_type="client_credentials";client_id=$clientId;client_secret=$clientSecret}
    return Invoke-WebRequest -Uri $tokenEndpoint -Method POST -Body $postParams | ConvertFrom-Json
}

function sendRequestToTarget($tokenResult){
    $headers = @{"Authorization"=$tokenResult.token_type + " " + $tokenResult.access_token}
    return Invoke-WebRequest -Uri $target -Headers $headers
}

$tokenEndpointResult = getToken
$result = sendRequestToTarget  $tokenEndpointResult
$result.StatusDescription