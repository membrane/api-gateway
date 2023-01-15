clientId="abc"
clientSecret="def"
tokenEndpoint="http://localhost:7007/oauth2/token"
target="http://localhost:2000"

username=$1
password=$2

parseResponse(){
    IFS='"' read -ra ADDR <<< "$1"    
    authHeader="Authorization: ${ADDR[7]} ${ADDR[3]}"
}

getToken(){
    call=$(curl -o - -v --data "grant_type=password&username=${username}&password=${password}&client_id=${clientId}&client_secret=${clientSecret}" $tokenEndpoint)
    parseResponse $call
}

sendRequestToTarget(){
    targetResult=$(curl -v -o - -H "$authHeader" $target)
}

getToken
sendRequestToTarget
echo $targetResult