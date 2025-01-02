/* Copyright 2024 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.proxies;

import java.util.*;

public class SOAPProxyMultipleServicesException extends RuntimeException  {

    private final SOAPProxy soapProxy;
    private final List<String> services;

    public SOAPProxyMultipleServicesException(SOAPProxy soapProxy, List<String> services) {
        this.soapProxy = soapProxy;
        this.services = services;
    }

    public SOAPProxy getSoapProxy() {
        return soapProxy;
    }

    public List<String> getServices() {
        return services;
    }
}
