package com.predic8.membrane.core.config.security.acme;

import com.predic8.membrane.annot.MCElement;

import java.util.Objects;

@MCElement(topLevel = false, name = "dnsOperator")
public class DnsOperatorAcmeValidation extends AcmeValidation {

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        DnsOperatorAcmeValidation that = (DnsOperatorAcmeValidation) o;
        return true;
    }

    @Override
    public int hashCode() {
        return 435258429;
    }
}
