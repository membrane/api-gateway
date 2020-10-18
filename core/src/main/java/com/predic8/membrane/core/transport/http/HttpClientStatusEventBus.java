/* Copyright 2015 Fabian Kessler, Optimaize

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.http;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Event bus for outcomes of the HttpClient.
 *
 * <p>Because the HttpClient is smart (does internal auto-retry and destination switching), just looking
 * at the result is not always enough. To be informed about what's going on with each individual destination,
 * this event bus can be used.</p>
 *
 * <p>How to use:
 * HttpClientStatusEventBus.getService().registerListener(new HttpClientStatusEventListener() {});</p>
 *
 * <p>The HttpClient then calls methods in here, including {@link #reportResponse}, which goes through to your
 * event listener as {@link HttpClientStatusEventListener#onResponse}.</p>
 */
public class HttpClientStatusEventBus {

    static final Log log = LogFactory.getLog(HttpClientStatusEventBus.class.getName());


    private static final HttpClientStatusEventBus INSTANCE = new HttpClientStatusEventBus();
    /**
     * Returns the singleton instance.
     * This should probably be some kind of injectable service instead.
     */
    public static HttpClientStatusEventBus getService() {
        return INSTANCE;
    }


    /**
     * Using the CopyOnWriteArrayList list is appropriate. Modifications are only expected initially, from then on
     * it's just iterations. This way we don't have to take shallow copies before iterating.
     */
    private final List<HttpClientStatusEventListener> listeners = new CopyOnWriteArrayList<HttpClientStatusEventListener>();

    private HttpClientStatusEventBus() {
    }


    /**
     * Adds a listener to be informed about events.
     * The listener is added at the end of the list, thus being called after already registered ones.
     * @throws IllegalStateException if the method detects that the listener is registered already.
     *         Except for a tiny small time frame because the operations are not atomic, it will be detected.
     *         If not, you end up having it registered twice.
     */
    public void registerListener(HttpClientStatusEventListener listener) {
        if (listeners.contains(listener)) throw new IllegalStateException("Already registered: "+listener);
        listeners.add(listener);
    }


//    public void reportSuccess(String destination) {
//        long timestamp = System.currentTimeMillis();
//        for (HttpClientStatusEventListener listener : listeners) {
//            try {
//                listener.onSuccess(timestamp, destination);
//            } catch (Exception e) {
//                log.warn("Listener "+listener+" threw exception (it is logged and ignored)", e);
//            }
//        }
//    }
//
//    public void report5xx(String destination, int responseCode) {
//        long timestamp = System.currentTimeMillis();
//        for (HttpClientStatusEventListener listener : listeners) {
//            try {
//                listener.on5xx(timestamp, destination, responseCode);
//            } catch (Exception e) {
//                log.warn("Listener "+listener+" threw exception (it is logged and ignored)", e);
//            }
//        }
//    }

    public void reportResponse(String destination, int responseCode) {
        long timestamp = System.currentTimeMillis();
        for (HttpClientStatusEventListener listener : listeners) {
            try {
                listener.onResponse(timestamp, destination, responseCode);
            } catch (Exception e) {
                log.warn("Listener "+listener+" threw exception (it is logged and ignored)", e);
            }
        }
    }

    /**
     *
     * @param destination
     */
    public void reportException(String destination, Exception exception) {
        long timestamp = System.currentTimeMillis();
        for (HttpClientStatusEventListener listener : listeners) {
            try {
                listener.onException(timestamp, destination, exception);
            } catch (Exception e) {
                log.warn("Listener "+listener+" threw exception (it is logged and ignored)", e);
            }
        }
    }

}
