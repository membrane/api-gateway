# Configuration 

This folder contains the Membrane configuration. More configuration samples could be found in the [tutorial](../tutorials) and [examples](../examples) folders.


## Starting Membrane    

Run Membrane with the configuration in this folder:

```
cd membrane-api-gateway-<VERSION>
./membrane.sh or ./membrane.cmd
```


## YAML Configuration

Use the 'apis.yaml' file to configure your APIs. The 'apis.yaml' has precedence over the 'proxies.xml' file.

You can remove the inactive XML file in case you do not want to use the YAML configuration.

```
rm proxies.xml.inactive
```


## XML  Configuration

To switch back to the XML configuration:

```
rm apis.yaml
mv proxies.xml.inactive proxies.xml
```

