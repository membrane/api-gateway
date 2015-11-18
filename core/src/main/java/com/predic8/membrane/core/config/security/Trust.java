package com.predic8.membrane.core.config.security;

import com.google.common.base.Objects;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;

import java.util.ArrayList;
import java.util.List;

/**
 * @description Experimental.
 * <p>Allows to insert one or more PEM blocks containing the certificates to be trusted directly into the proxies.xml
 * file.</p>
 * <p>This is an alternative for {@link TrustStore}.</p>
 */
@MCElement(name="trust")
public class Trust {
    List<Certificate> certificateList = new ArrayList<Certificate>();

    @Override
    public boolean equals(Object obj) {
        if (!(obj instanceof Trust))
            return false;
        Trust other = (Trust)obj;
        return Objects.equal(certificateList, other.certificateList);
    }

    public List<Certificate> getCertificateList() {
        return certificateList;
    }

    @MCChildElement
    public void setCertificateList(List<Certificate> certificateList) {
        this.certificateList = certificateList;
    }
}
