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

/**
 *
 * Implementations are supposed to not block! If they have time-consuming work to do then they shall put that
 * work into a background task/queue to be processed separately, otherwise the end user has to wait for his response.
 *
 * Implementations are not supposed to throw. If they do, the caller ({@link HttpClientStatusEventBus} catches
 * and logs the Exception, and goes on as if nothing happened.
 *
 */
public interface HttpClientStatusEventListener {

//    void onSuccess(long timestamp, String destination);
//
//    void on5xx(long timestamp, String destination, int responseCode);

    /**
     * This is called when the destination replied correctly by HTTP.
     * It acn be a success (2xx), server error (5xx), or any other kind of HTTP response.
     *
     * @param timestamp When it happened.
     * @param destination What the target was: URL with host, port, service etc.
     * @param responseCode for example 200 or 500.
     */
    void onResponse(long timestamp, String destination, int responseCode);

    /**
     * This is called when there was no valid HTTP response from the destination.
     *
     * @param timestamp When it happened.
     * @param destination What the target was: URL with host, port, service etc.
     * @param exception Expect one of these: ConnectException, SocketException, UnknownHostException, EOFWhileReadingFirstLineException, NoResponseException, or else any Exception.
     */
    void onException(long timestamp, String destination, Exception exception);

}
