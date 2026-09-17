Starting Membrane With Docker
-----------------------------

No local Java installation is required. Open a terminal in this folder and run:

* On Linux/Mac:
  ./run-docker.sh

* On Windows:
  run-docker.cmd

Or, using Docker Compose:

docker compose up

By default, Membrane starts with the configuration from the file conf/apis.yaml. This directory is mounted into the container, so that configuration changes take effect immediately without restarting the container..

Use the -c option to start Membrane with a different configuration file, including a tutorial:

./run-docker.sh -c tutorials/getting-started/10-First-API.yaml

Ports 2000-2010 and 9000 (the admin console) are published. A configuration listening on a
different port needs that port published too:

MEMBRANE_DOCKER_OPTS="-p 3128:3128"
./run-docker.sh -c tutorials/forward-proxy/10-Forward-Proxy.yaml

On Windows, set the variable first:  set "MEMBRANE_DOCKER_OPTS=-p 3128:3128"
With Docker Compose, add the port to the ports list in docker-compose.yml.

To stop: press Ctrl+C. When started with 'docker compose up', also run 'docker compose down'
afterwards to remove the container.


Find the latest version at:
https://github.com/membrane/api-gateway


Starting Membrane With Java
---------------------------

You can also start Membrane without Docker. Make sure Java 21 or higher is installed. Then open a terminal and execute in this folder:

* On Linux/Mac:
  ./membrane.sh

* On Windows:
  ./membrane.cmd

By default, the gateway uses the apis.yaml file in the conf folder as configuration.

If you want to use a different configuration file or to use the old XML configuration, use the -c option:

./membrane.sh -c conf/proxies.xml

Changes to apis.yaml take effect immediately without restarting the gateway.


Documentation
=============

- Complete the getting started tutorial in the 'tutorial' folder
- Run the samples in the 'examples' folder
- Download the free API Gateway eBook
  https://www.membrane-api.io/api-gateway-ebook.html
- Look at the documentation at https://www.membrane-api.io/docs/


Support
=======
Your questions and ideas are welcome at the discussions:
https://github.com/membrane/api-gateway/discussions

If you find a bug, you can report it at:
https://github.com/membrane/api-gateway/issues
Please include the version of the Membrane and any details that may be necessary to reproduce the problem.

For support see: https://www.membrane-api.io/api-gateway-pricing.html
Or contact us at info@predic8.de


Enjoy using Membrane!
The Membrane Team

