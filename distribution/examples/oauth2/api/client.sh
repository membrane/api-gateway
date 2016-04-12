clientId="abc"
clientSecret="def"
tokenEndpoint="http://localhost:2000/oauth2/token"
target="http://localhost:2002"

username=$1
password=$2

parseResponse(){

    IFS='"' read -ra ADDR <<< "$1"    
    authHeader="Authorization: ${ADDR[7]} ${ADDR[3]}"
}

getToken(){
    call=$(curl --data "grant_type=password&username=${username}&password=${password}&client_id=${clientId}&client_secret=${clientSecret}" $tokenEndpoint)
    parseResponse $call
}

sendRequestToTarget(){
    targetResult=$(curl -v -s -H "$authHeader" $target 1> /dev/null)
}

getToken
sendRequestToTarget
echo $targetResult