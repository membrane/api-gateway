


echo "------------------- Valid request, should work => 200 Ok -------------------\n"

curl 'http://localhost:2000/demo-api/v2/persons?limit=10' -v

echo "\n\n"
echo "------------------------ Wrong path => 404 Not Found -----------------------\n"

curl http://localhost:2000/demo-api/v2/wrong -v

echo "\n\n"
echo "----------------- Limit greater than 100 => 400 Bad Request ----------------\n"

curl 'http://localhost:2000/demo-api/v2/persons?limit=200' -v

echo "\n\n"
echo "----------------------------- Valid => 200 Ok ------------------------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer"}'

echo "\n\n"
echo "------------- Invalid UUID, email and enum => 400 Bad Request --------------\n"

curl -X PUT 'http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2+DDFC3112CE89D1' \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","email": "jan(at)schilderei.nl","type": "ARTIST"}' -v

echo "\n\n"
echo "-------------- Wrong Content-Type => 415 Unsupported Mediatype -------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/xml' \
  -d '<name>Jan</name>' -v

echo "\n\n"
echo "-------------- Required property is missing => 400 Bad Request -------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"email": "jan@predic8.de"}' -v

echo "\n\n"
echo "----------------- Additional property role => 400 Bad Request --------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","role": "admin"}' -v

echo "\n\n"
echo "------------------ Wrong regex pattern => 400 Bad Request ------------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","countryCode": "Germany"}' -v

echo "\n\n"
echo "---------------------- Nested Object => 201 Created ------------------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","countryCode": "DE","address": {"city": "Bonn","street": "Koblenzer Straße 65","zip": "D-53173"}}' -v

echo "\n\n"
echo "------------- OneOf with wrong string pattern => 400 Bad Request -----------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","countryCode": "DE","address": {"city": "Bonn","street": "Koblenzer Straße 65","zip": "D-5317"}}' -v

echo "\n\n"
echo "------------------- OneOf with right integer => 201 Created ----------------\n"

curl -X PUT http://localhost:2000/demo-api/v2/persons/4077C19D-2C1D-427B-B2DD-FC3112CE89D1 \
  -H 'content-type: application/json' \
  -d '{"name": "Jan Vermeer","countryCode": "DE","address": {"city": "Bonn","street": "Koblenzer Straße 65","zip": 53173}}' -v