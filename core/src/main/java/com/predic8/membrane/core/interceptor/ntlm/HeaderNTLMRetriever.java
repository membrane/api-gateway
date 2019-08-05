package com.predic8.membrane.core.interceptor.ntlm;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;

@MCElement(name = "headerRetriever", topLevel = false)
public class HeaderNTLMRetriever implements NTLMRetriever {

    String userHeaderName;
    String passwordHeaderName;
    String domainHeaderName;
    String workstationHeaderName;

    public HeaderNTLMRetriever() {
    }

    public HeaderNTLMRetriever(String userHeaderName, String passwordHeaderName, String domainHeaderName, String workstationHeaderName) {
        if(userHeaderName == null || userHeaderName.isEmpty())
            throw new RuntimeException("userHeaderName attribute cannot be null or empty");
        if(passwordHeaderName == null || passwordHeaderName.isEmpty())
            throw new RuntimeException("passwordHeaderName attribute cannot be null or empty");
        /*if(domainHeaderName == null || domainHeaderName.isEmpty())
            throw new RuntimeException("domainHeaderName attribute cannot be null or empty");
        if(workstationHeaderName == null || workstationHeaderName.isEmpty())
            throw new RuntimeException("workstationHeaderName attribute cannot be null or empty");*/

        this.userHeaderName = userHeaderName;
        this.passwordHeaderName = passwordHeaderName;
        this.domainHeaderName = domainHeaderName;
        this.workstationHeaderName = workstationHeaderName;
    }

    @Override
    public String fetchUsername(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(userHeaderName);
    }

    @Override
    public String fetchPassword(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(passwordHeaderName);
    }

    @Override
    public String fetchDomain(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(domainHeaderName);
    }

    @Override
    public String fetchWorkstation(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(workstationHeaderName);
    }

    public String getUserHeaderName() {
        return userHeaderName;
    }

    @MCAttribute(attributeName = "user")
    public void setUserHeaderName(String userHeaderName) {
        this.userHeaderName = userHeaderName;
    }

    public String getPasswordHeaderName() {
        return passwordHeaderName;
    }

    @MCAttribute(attributeName = "pass")
    public void setPasswordHeaderName(String passwordHeaderName) {
        this.passwordHeaderName = passwordHeaderName;
    }

    public String getDomainHeaderName() {
        return domainHeaderName;
    }

    @MCAttribute(attributeName = "domain")
    public void setDomainHeaderName(String domainHeaderName) {
        this.domainHeaderName = domainHeaderName;
    }

    public String getWorkstationHeaderName() {
        return workstationHeaderName;
    }

    @MCAttribute(attributeName = "workstation")
    public void setWorkstationHeaderName(String workstationHeaderName) {
        this.workstationHeaderName = workstationHeaderName;
    }
}
