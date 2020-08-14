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
import com.predic8.membrane.core.http.Request;

public class RequestSnapshot extends MessageSnapshot {

    String method;
    String uri;

    public RequestSnapshot(Request request) {
        super(request);
        this.method = request.getMethod();
        this.uri = request.getUri();
    }

    public RequestSnapshot() {
        super();
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUri() {
        return uri;
    }

    public void setUri(String uri) {
        this.uri = uri;
    }

    public Request toRequest() {
        Request request = new Request();
        request.setHeader(convertHeader());
        request.setBody(convertBody());
        request.setMethod(getMethod());
        request.setUri(getUri());
        return request;
    }
}
