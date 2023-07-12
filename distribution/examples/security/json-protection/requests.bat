@echo off

echo --------- Valid Request ---------
echo.
curl -d @requests/valid.json localhost:2000 

echo -------- Too Many Tokens --------
echo.
curl -d @requests/max_tokens.json localhost:2000 

echo ------- Document Too Large ------
echo.
curl -d @requests/max_size.json localhost:2000 

echo -------- Nesting Too Deep -------
echo.
curl -d @requests/max_depth.json localhost:2000 

echo -------- String Too Large -------
echo.
curl -d @requests/max_string_length.json localhost:2000 

echo --------- Key Too Large ---------
echo.
curl -d @requests/max_key_length.json localhost:2000 

echo -------- Object Too Large -------
echo.
curl -d @requests/max_object_size.json localhost:2000 


echo -------- Array Too Large --------
echo.
curl -d @requests/max_array_size.json localhost:2000 

