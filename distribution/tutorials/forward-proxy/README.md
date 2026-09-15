# Forward Proxy Tutorial

Learn how to use Membrane as a classic HTTP forward proxy with the `proxy` element -
distinct from `api`/`serviceProxy`, which are reverse proxies in front of one configured
backend. A forward proxy has no target: the client's own request (an absolute-URI request
or a `CONNECT` tunnel) chooses the destination.

Before you start, make sure you have completed the
[Getting Started](../getting-started) tutorial.

To begin, open [10-Forward-Proxy.yaml](10-Forward-Proxy.yaml) and follow the instructions
in the file.
