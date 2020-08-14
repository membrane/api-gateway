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

import com.predic8.membrane.core.http.Message;
import com.predic8.membrane.core.http.Response;

public class ResponseSnapshot extends MessageSnapshot {

    int statusCode;
    String statusMessage;

    public ResponseSnapshot(Response response) {
        super(response);
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
