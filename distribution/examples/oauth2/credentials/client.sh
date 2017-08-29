clientId="abc"
clientSecret="def"
tokenEndpoint="http://localhost:7000/oauth2/token"
target="http://localhost:2000"

parseResponse(){

    IFS='"' read -ra ADDR <<< "$1"    
    authHeader="Authorization: ${ADDR[7]} ${ADDR[3]}"
}

getToken(){
    call=$(curl --data "grant_type=client_credentials&client_id=${clientId}&client_secret=${clientSecret}" $tokenEndpoint)
    parseResponse $call
}

sendRequestToTarget(){
    targetResult=$(curl -v -s -H "$authHeader" $target 1> /dev/null)
}

getToken
sendRequestToTarget
echo $targetResult