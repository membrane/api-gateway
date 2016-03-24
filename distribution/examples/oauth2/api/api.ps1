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
$tokenEndpoint = "http://localhost:2000/oauth2/token"
$target = "http://localhost:2002"

# http://stackoverflow.com/questions/27951561/use-invoke-webrequest-with-a-username-and-password-for-basic-authentication-on-t
# for basic auth

function getCmdLine{

    param (
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]$username,
        [Parameter(Mandatory=$true)]
        [ValidateNotNullOrEmpty()]
        [string]$password
    )
    $global:username = $username
    $global:password = $password
}

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