package com.predic8.membrane.core.interceptor.authentication.xen;

import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.Interceptor;

import javax.xml.xpath.*;

public class XenCredentialAccessor implements CredentialAccessor<XenCredentialAccessor.XenLoginData>  {

    public class XenLoginData {
        String username;
        String password;
    }

    private XPathFactory xPathFactory = XPathFactory.newInstance();

    @Override
    public XenLoginData getLogin(Exchange exchange) {
        try {
            XenMessageContext xmc = XenMessageContext.get(exchange, Interceptor.Flow.REQUEST);

            XPath xp;
            synchronized (xPathFactory) {
                xp = xPathFactory.newXPath();
            }
            XPathExpression xp1 = xp.compile("/methodCall/methodName/text()");
            String methodName = (String) xp1.evaluate(xmc.getDocument(), XPathConstants.STRING);

            XenLoginData loginData = new XenLoginData();

            XPathExpression xp2 = xp.compile("/methodCall/params/param[1]/value/string/text()");
            loginData.username = (String) xp2.evaluate(xmc.getDocument(), XPathConstants.STRING);

            XPathExpression xp3 = xp.compile("/methodCall/params/param[2]/value/string/text()");
            loginData.password = (String) xp3.evaluate(xmc.getDocument(), XPathConstants.STRING);


            if (!"session.login_with_password".equals(methodName))
                return null;

            return loginData;
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public void replaceLogin(Exchange exchange, XenLoginData newLoginData) {
        try {
            XenMessageContext xmc = XenMessageContext.get(exchange, Interceptor.Flow.REQUEST);

            XPath xp;
            synchronized (xPathFactory) {
                xp = xPathFactory.newXPath();
            }

            xmc.setX(xp, "/methodCall/params/param[1]/value/string/text()", newLoginData.username);

            xmc.setX(xp, "/methodCall/params/param[2]/value/string/text()", newLoginData.password);

            xmc.writeBack();
        } catch (XPathExpressionException e) {
            throw new RuntimeException(e);
        }
    }

}
