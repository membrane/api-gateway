#!/bin/bash

echo -e "--------- Valid Request ---------"
curl -d @requests/valid.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "-------- Too Many Tokens --------"
curl -d @requests/max_tokens.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "------- Document Too Large ------"
curl -d @requests/max_size.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "-------- Nesting Too Deep -------"
curl -d @requests/max_depth.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "-------- String Too Large -------"
curl -d @requests/max_string_length.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "--------- Key Too Large ---------"
curl -d @requests/max_key_length.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "-------- Object Too Large -------"
curl -d @requests/max_object_size.json localhost:2000
echo -e "\n---------------------------------\n\n"

echo -e "-------- Array Too Large --------"
curl -d @requests/max_array_size.json localhost:2000
echo -e "\n---------------------------------\n\n"
