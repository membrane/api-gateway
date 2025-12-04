# Configration 

This folder contains the Membrane configuration. More configuration samples could be found in the [tutorial](../tutorials) and [examples](../examples) folders.

## Starting Membrane    

Run Membrane with the configuration from this folder:

```
cd membrane-api-gateway-<VERSION>
./membrane.sh or ./membrane.cmd
```


## YAML Configuration

Use the 'apis.yaml' file to configure your APIs. You can remove the inactive XML file.

rm proxies.xml.inactive


## XML  Configuration

To switch back to XML:

rm apis.yaml
mv proxies.xml.inactive proxies.xml

