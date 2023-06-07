@echo off

echo --------- Valid Request ---------
echo.
curl -d @requests/valid.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo -------- Too Many Tokens --------
echo.
curl -d @requests/max_tokens.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo ------- Document Too Large ------
echo.
curl -d @requests/max_size.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo -------- Nesting Too Deep -------
echo.
curl -d @requests/max_depth.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo -------- String Too Large -------
echo.
curl -d @requests/max_string_length.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo --------- Key Too Large ---------
echo.
curl -d @requests/max_key_length.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo -------- Object Too Large -------
echo.
curl -d @requests/max_object_size.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.

echo -------- Array Too Large --------
echo.
curl -d @requests/max_array_size.json localhost:2000 | python -m json.tool
echo ---------------------------------
echo.
