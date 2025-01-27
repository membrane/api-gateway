#!/bin/bash

DB_NAME="postgres"
DB_USER="user"
DB_PASSWORD="password"
DB_HOST="localhost"
USERS=10

insert_values() {
    echo "Inserting values..."
    local batch_size=1000
    local start_time=$(date +%s)
    local first_apikey=""

    for ((i = 0; i < USERS; i += batch_size)); do
        local apikeys=()
        local scope_data=()

        for ((j = 0; j < batch_size; j++)); do
            apikey=$(uuidgen)
            if [[ -z "$first_apikey" ]]; then
                first_apikey=$apikey
            fi
            apikeys+=("('$apikey')")
            scope_data+=("('$apikey', 'scope-$((j + 1))')")
        done

        local key_values=$(IFS=, ; echo "${apikeys[*]}")
        docker exec postgres psql -U $DB_USER -d $DB_NAME -c "
        INSERT INTO key (apikey) VALUES $key_values;
        "

        local scope_values=$(IFS=, ; echo "${scope_data[*]}")
        docker exec postgres psql -U $DB_USER -d $DB_NAME -c "
        INSERT INTO scope (apikey, scope) VALUES $scope_values;
        "
    done

    local end_time=$(date +%s)
    echo "$USERS rows inserted successfully in $((end_time - start_time)) seconds."

    echo "First API Key: $first_apikey"
}

main() {
    insert_values
}

main
