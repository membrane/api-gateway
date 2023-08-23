clientId="abc"
clientSecret="def"
tokenEndpoint="http://localhost:7007/oauth2/token"
target="http://localhost:2000"

username=$1
password=$2

parseResponse(){
    IFS='"' read -ra ADDR <<< "$1" 
    echo "Got Token: ${ADDR[3]}"
    authHeader="Authorization: ${ADDR[7]} ${ADDR[3]}"
}

getToken(){
    body="grant_type=password&username=${username}&password=${password}&client_id=${clientId}&client_secret=${clientSecret}"
    echo "1.) Requesting Token"
    echo "POST $tokenEndpoint"
    echo $body
    echo
    call=$(curl -s -d $body $tokenEndpoint)
    parseResponse $call
}

sendRequestToTarget(){
    echo
    echo "2.) Calling API"
    echo "GET $target"
    echo "$authHeader"
    targetResult=$(curl -s -H "$authHeader" $target)
    echo
    echo Got: $targetResult
}

getToken
sendRequestToTarget