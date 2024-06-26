/* Copyright 2023, 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.util;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.transport.http.FakeHttpHandler;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.UnknownHostException;

public class TestUtil {

    public static InputStream getResourceAsStream(Object thisObj,String filename) {
        return thisObj.getClass().getClassLoader().getResourceAsStream(filename);
    }

    public static Exchange assembleExchange(String hostHeader, String method, String path, String proxyVersion, int port, String localIp) throws UnknownHostException {
        return new Exchange(new FakeHttpHandler(port, Inet4Address.getByName(localIp))) {{
            setRequest(new Request() {{
                getHeader().setHost(hostHeader);
                setMethod(method);
                setUri(path);
                setVersion(proxyVersion);
            }});
        }};
    }
}
