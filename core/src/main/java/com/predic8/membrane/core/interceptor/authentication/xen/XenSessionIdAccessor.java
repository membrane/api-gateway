/* Copyright 2019 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.interceptor.authentication.xen;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;

import javax.xml.xpath.*;

public class XenSessionIdAccessor implements SessionIdAccessor {
    private XPathFactory xPathFactory = XPathFactory.newInstance();

    @Override
    public String getSessionId(Exchange exchange, Interceptor.Flow flow) {
        try {
            XenMessageContext xmc = XenMessageContext.get(exchange, flow);

            XPath xp;
            synchronized (xPathFactory) {
                xp = xPathFactory.newXPath();
            }
            XPathExpression xp1 = xp.compile(getPath(flow));
            String sessionId = (String) xp1.evaluate(xmc.getDocument(), XPathConstants.STRING);

            return sessionId;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void replaceSessionId(Exchange exchange, String newSessionId, Interceptor.Flow flow) {
        try {
            XenMessageContext xmc = XenMessageContext.get(exchange, flow);

            XPath xp;
            synchronized (xPathFactory) {
                xp = xPathFactory.newXPath();
            }
            XPathExpression xp1 = xp.compile(getPath(flow));
            String sessionId = (String) xp1.evaluate(xmc.getDocument(), XPathConstants.STRING);

            xmc.setX(xp, getPath(flow), newSessionId);

            // the session id occurs twice in the 'session.get_is_local_superuser' call
            String path2 = getPath2(flow);
            if (path2 != null) {
                XPathExpression xp2 = xp.compile(path2);
                String sessionId2 = (String) xp2.evaluate(xmc.getDocument(), XPathConstants.STRING);
                if (sessionId.equals(sessionId2))
                    xmc.setX(xp, path2, newSessionId);
            }

            xmc.writeBack();
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }

    }

    private String getPath(Interceptor.Flow flow) {
        switch (flow) {
            case REQUEST:
                return "/methodCall/params/param[1]/value/string/text()";
            case RESPONSE:
                return "/methodResponse/params/param[1]/value/struct/member[./name/text()='Value']/value/text()";
            default:
                throw new RuntimeException("not implemented");
        }
    }

    private String getPath2(Interceptor.Flow flow) {
        switch (flow) {
            case REQUEST:
                return "/methodCall/params/param[2]/value/string/text()";
            case RESPONSE:
                return null;
            default:
                throw new RuntimeException("not implemented");
        }
    }
}
