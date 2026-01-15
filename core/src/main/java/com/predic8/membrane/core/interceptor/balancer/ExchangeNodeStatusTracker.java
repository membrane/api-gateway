/* Copyright 2026 predic8 GmbH, www.predic8.com

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

import static com.predic8.membrane.core.exchange.Exchange.TRACK_NODE_STATUS;
import static java.lang.Boolean.TRUE;

/**
 * Used by the {@link LoadBalancingInterceptor} to track the status of nodes during load balancing.
 *
 * The order of the elements in the arrays correspond to exchange.getDestinations(). Each destination either results
 * in a status code or an exception.
 */
public class ExchangeNodeStatusTracker {
    private final int destinationCount;
    private int[] nodeStatusCodes;
    private Exception[] nodeExceptions;

    public ExchangeNodeStatusTracker(int destinationCount) {
        this.destinationCount = destinationCount;
    }

    public void setNodeStatusCode(int tryCounter, int code) {
        if (nodeStatusCodes == null) {
            nodeStatusCodes = new int[destinationCount];
        }
        nodeStatusCodes[tryCounter % destinationCount] = code;
    }

    public void trackNodeException(int tryCounter, Exception e) {
        if (nodeExceptions == null) {
            nodeExceptions = new Exception[destinationCount];
        }
        nodeExceptions[tryCounter % destinationCount] = e;
    }

    void setNodeStatusCodes(int[] nodeStatusCodes) {
        this.nodeStatusCodes = nodeStatusCodes;
    }

    public int[] getNodeStatusCodes() {
        return nodeStatusCodes;
    }

    void setNodeExceptions(Exception[] nodeExceptions) {
        this.nodeExceptions = nodeExceptions;
    }

    public Exception[] getNodeExceptions() {
        return nodeExceptions;
    }

    public ExchangeNodeStatusTracker clone() {
        ExchangeNodeStatusTracker copy = new ExchangeNodeStatusTracker(destinationCount);
        int[] nodeStatusCodes = getNodeStatusCodes();
        if (nodeStatusCodes != null)
            copy.setNodeStatusCodes(nodeStatusCodes.clone());
        Exception[] nodeExceptions = getNodeExceptions();
        if (nodeExceptions != null)
            copy.setNodeExceptions(nodeExceptions.clone());
        return copy;
    }

    public int estimateHeapSize() {
        return 50 + destinationCount * 4096;
    }
}
