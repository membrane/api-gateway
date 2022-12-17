echo "------------------- Valid request => 200 Ok -------------------\n"

curl http://localhost:2000/persons\
  -H 'content-type: application/json' \
  -d '{"name": "Johannes Gutenberg","age": 78}' -v


echo "\n\n------------------- Invalid => 400 Bad Content ----------------\n"

curl http://localhost:2000/persons \
  -H 'content-type: application/json' \
  -d '{"name": "Bo","email": "mailatme","age": -1}' -v