/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.balancer;

import java.io.IOException;
import java.net.*;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import com.predic8.membrane.annot.MCAttribute;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import org.joda.time.DateTime;

@MCElement(name = "nodeOnlineChecker")
public class NodeOnlineChecker {


    public int getPingTimeoutInSeconds() {
        return pingTimeoutInSeconds;
    }

    public void setPingTimeoutInSeconds(int pingTimeoutInSeconds) {
        this.pingTimeoutInSeconds = pingTimeoutInSeconds;
    }

    private class BadNode {
        private Node node;
        private AtomicInteger failsOn5XX = new AtomicInteger(0);
        private HashSet<Cluster> nodeClusters = new HashSet<Cluster>();

        public BadNode(Node node) {
            this.node = node;
        }

        public Node getNode() {
            return node;
        }

        public void setNode(Node node) {
            this.node = node;
        }

        public AtomicInteger getFailsOn5XX() {
            return failsOn5XX;
        }

        public void setFailsOn5XX(AtomicInteger failsOn5XX) {
            this.failsOn5XX = failsOn5XX;
        }

        public HashSet<Cluster> getNodeClusters() {
            return nodeClusters;
        }

        public void setNodeClusters(HashSet<Cluster> nodeClusters) {
            this.nodeClusters = nodeClusters;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;

            BadNode badNode = (BadNode) o;

            return node.equals(badNode.node);

        }

        @Override
        public int hashCode() {
            return node.hashCode();
        }
    }

    private static Log log = LogFactory.getLog(NodeOnlineChecker.class.getName());
    LoadBalancingInterceptor lbi;
    ConcurrentHashMap<String, BadNode> badNodesForDestinations = new ConcurrentHashMap<String, BadNode>();
    HashSet<BadNode> offlineNodes = new HashSet<BadNode>();
    private int retryTimeInSeconds = -1;
    private int nodeCounterLimit5XX = 10;
    private int pingTimeoutInSeconds = 1;
    private DateTime lastCheck = DateTime.now();


    public NodeOnlineChecker() {
    }

    public void handle(Exchange exc) {
        if (exc.getNodeExceptions() != null) {
            for (int i = 0; i < exc.getDestinations().size(); i++) {
                if (exc.getNodeExceptions()[i] != null) {
                    //setNodeDown(exc, i);
                    handleNodeException(exc, i);
                }
            }
        }
        if (exc.getNodeStatusCodes() != null) {
            for (int i = 0; i < exc.getDestinations().size(); i++) {
                if (exc.getNodeStatusCodes()[i] != 0) {
                    int status = exc.getNodeStatusCodes()[i];
                    if (status >= 400 && status < 600) {
                        //setNodeDown(exc, i);
                        handleNodeBadStatusCode(exc, i);
                    }
                }
            }
        }
    }

    public void handleNodeBadStatusCode(Exchange exc, int destination) {
        int statuscode = exc.getNodeStatusCodes()[destination];
        String destinationString = getDestinationAsString(exc, destination);
        if (statuscode < 500)
            badNodesForDestinations.remove(destinationString);
        else if (statuscode >= 500) {
            if (!badNodesForDestinations.containsKey(destinationString))
                badNodesForDestinations.put(destinationString, new BadNode(getNodeFromExchange(exc, destination)));
            int currentFails = badNodesForDestinations.get(destinationString).getFailsOn5XX().incrementAndGet();
            if(currentFails > nodeCounterLimit5XX){
                setNodeDown(exc,destination);
            }
        }
    }

    public void handleNodeException(Exchange exc, int destination) {
        badNodesForDestinations.put(getDestinationAsString(exc, destination), new BadNode(getNodeFromExchange(exc, destination)));
        setNodeDown(exc, destination);
    }

    public Node getNodeFromExchange(Exchange exc, int destination) {
        URL destUrl = getUrlObjectFromDestination(exc, destination);
        return new Node(destUrl.getProtocol() + "://" +destUrl.getHost(), destUrl.getPort());
    }

    public String getDestinationAsString(Exchange exc, int destination) {
        return exc.getDestinations().get(destination);
    }

    public void setNodeDown(Exchange exc, int destination) {
        String destinationAsString = getDestinationAsString(exc, destination);
        BadNode bad = badNodesForDestinations.get(destinationAsString);
        synchronized (offlineNodes) {
            for (Cluster cl : lbi.getClusterManager().getClusters()) {
                Node node = bad.getNode();
                if (cl.getNodes().contains(node)) {
                    cl.nodeDown(node);
                    bad.getNodeClusters().add(cl);
                }
            }
            offlineNodes.add(bad);
        }
        log.info("Node down: " + destinationAsString);
    }

    private URL getUrlObjectFromDestination(Exchange exc, int destination) {
        String url = getDestinationAsString(exc, destination);
        URL u = null;
        try {
            u = new URL(url);
        } catch (MalformedURLException e) {
        }
        return u;
    }


    public void putNodesBackUp() {
        if(retryTimeInSeconds < 0) {
            return;
        }
        if(retryTimeInSeconds > 0) {
            if (DateTime.now().isBefore(lastCheck.plusSeconds(retryTimeInSeconds))) {
                return;
            }
        }
        List<BadNode> onlineNodes = pingOfflineNodes();
        for(BadNode node : onlineNodes){
            putNodeUp(node);
        }
    }

    private void putNodeUp(BadNode node) {
        for(Cluster cl : node.getNodeClusters()){
            cl.nodeUp(node.getNode());
        }
        offlineNodes.remove(node);
        log.info("Node down: " + node.getNode().getHost() + ":" + node.getNode().getPort());
    }

    private List<BadNode> pingOfflineNodes() {
        ArrayList<BadNode> onlineNodes = new ArrayList<BadNode>();

        for(BadNode node : offlineNodes){
            URL url = null;
            try {
                url = new URL(node.getNode().getHost());
            } catch (MalformedURLException ignored) {
                continue;
            }
            try {
                HttpURLConnection urlConn = (HttpURLConnection) url.openConnection();
                urlConn.setConnectTimeout(pingTimeoutInSeconds*1000);
                urlConn.connect();
                if(urlConn.getResponseCode() == HttpURLConnection.HTTP_OK)
                    onlineNodes.add(node);
            } catch (IOException ignored) {
                continue;
            }
        }

        return onlineNodes;
    }

    public LoadBalancingInterceptor getLbi() {
        return lbi;
    }

    public void setLbi(LoadBalancingInterceptor lbi) {
        this.lbi = lbi;
    }

    public int getRetryTimeInSeconds() {
        return retryTimeInSeconds;
    }

    /**
     * @description the time in seconds until offline nodes are checked again. -1 to disable
     * @default -1
     */
    @MCAttribute
    public void setRetryTimeInSeconds(int retryTimeInSeconds) {
        this.retryTimeInSeconds = retryTimeInSeconds;
    }

    public int getNodeCounterLimit5XX() {
        return nodeCounterLimit5XX;
    }

    /**
     * @description the number of times a node has to fail with a 5XX statuscode until it is taken down
     * @default 10
     */
    @MCAttribute
    public void setNodeCounterLimit5XX(int nodeCounterLimit5XX) {
        this.nodeCounterLimit5XX = nodeCounterLimit5XX;
    }
}
