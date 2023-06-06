/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.http.BodyCollectingMessageObserver;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.util.functionalInterfaces.ExceptionThrowingConsumer;

import java.io.IOException;

public class ResponseSnapshot extends MessageSnapshot {

    int statusCode;
    String statusMessage;

    /**
     * @param response the response to snapshot
     * @param bodyCopiedCallback will be called once the body has been filled. if null, the body stream will be read
     *                 into memory immediately.
     * @param aes parameter for the callback
     * @param strategy how to handle body lengths exceeding the {@code limit}.
     * @param limit maximum length of the body.
     */
    public ResponseSnapshot(Response response, ExceptionThrowingConsumer<AbstractExchangeSnapshot> bodyCopiedCallback, AbstractExchangeSnapshot aes, BodyCollectingMessageObserver.Strategy strategy, long limit) throws IOException {
        super(response, bodyCopiedCallback, aes, strategy, limit);
        this.statusCode = response.getStatusCode();
        this.statusMessage = response.getStatusMessage();
    }

    public ResponseSnapshot() {
        super();
    }

    public int getStatusCode() {
        return statusCode;
    }

    public void setStatusCode(int statusCode) {
        this.statusCode = statusCode;
    }

    public String getStatusMessage() {
        return statusMessage;
    }

    public void setStatusMessage(String statusMessage) {
        this.statusMessage = statusMessage;
    }

    public Response toResponse() {
        Response result = new Response();
        result.setHeader(convertHeader());
        result.setBody(convertBody());
        result.setStatusCode(getStatusCode());
        result.setStatusMessage(getStatusMessage());
        return result;
    }
}
