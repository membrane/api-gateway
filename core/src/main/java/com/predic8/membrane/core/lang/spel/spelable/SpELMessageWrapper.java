/* Copyright 2023 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */


package com.predic8.membrane.core.lang.spel.spelable;

import com.predic8.membrane.core.http.AbstractBody;
import com.predic8.membrane.core.http.Message;

public class SpELMessageWrapper {

    private SpELHeader headers;
    private AbstractBody body;
    private String version;
    private String errorMessage;

    public SpELMessageWrapper(Message message) {
        if (message == null) {
            return;
        }

        this.headers = new SpELHeader(message.getHeader());
        this.body = message.getBody();
        this.version = message.getVersion();
        this.errorMessage = message.getErrorMessage();
    }

    public SpELHeader getHeaders() {
        return headers;
    }

    public AbstractBody getBody() {
        return body;
    }

    public String getVersion() {
        return version;
    }

    @SuppressWarnings("unused")
    public String getErrorMessage() {
        return errorMessage;
    }
}
