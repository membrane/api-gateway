/* Copyright 2017 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.transport.ws.interceptors;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.transport.ws.WebSocketFrame;
import com.predic8.membrane.core.transport.ws.WebSocketInterceptorInterface;
import com.predic8.membrane.core.transport.ws.WebSocketSender;

import static com.predic8.membrane.core.transport.ws.interceptors.WebSocketLogInterceptor.Encoding.HEX;

@MCElement(name = "wsLog")
public class WebSocketLogInterceptor implements WebSocketInterceptorInterface {
    public enum Encoding {
        RAW,
        HEX
    }

    private Encoding encoding = Encoding.RAW;

    @Override
    public void init(Router router) throws Exception {

    }

    public Encoding getEncoding() {
        return encoding;
    }

    @MCAttribute
    public void setEncoding(Encoding encoding) {
        this.encoding = encoding;
    }

    @Override
    public void handleFrame(WebSocketFrame frame, boolean frameTravelsToRight, WebSocketSender sender) throws Exception {
        System.out.println("Frame travels from " + (frameTravelsToRight ? "client to server" : "server to client"));
        System.out.println(encoding == HEX ? frame.toStringHex() : frame.toString());
        sender.handleFrame(frame);
    }
}
