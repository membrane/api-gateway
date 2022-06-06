package com.predic8.membrane.core.transport.ssl.acme;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import org.joda.time.DateTime;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Order {
    public static final String ORDER_STATUS_PENDING = "pending";
    public static final String ORDER_STATUS_READY = "ready";
    public static final String ORDER_STATUS_PROCESSING = "processing";
    public static final String ORDER_STATUS_VALID = "valid";
    public static final String ORDER_STATUS_INVALID = "invalid";

    List<Identifier> identifiers = new ArrayList<>();
    List<String> authorizations = new ArrayList<>();
    DateTime notBefore;
    DateTime notAfter;
    String finalize;
    String status;
    DateTime expires;
    Map error;
    String certificate;

    public List<Identifier> getIdentifiers() {
        return identifiers;
    }

    public void setIdentifiers(List<Identifier> identifiers) {
        this.identifiers = identifiers;
    }

    public List<String> getAuthorizations() {
        return authorizations;
    }

    public void setAuthorizations(List<String> authorizations) {
        this.authorizations = authorizations;
    }

    public DateTime getNotBefore() {
        return notBefore;
    }

    public void setNotBefore(DateTime notBefore) {
        this.notBefore = notBefore;
    }

    public DateTime getNotAfter() {
        return notAfter;
    }

    public void setNotAfter(DateTime notAfter) {
        this.notAfter = notAfter;
    }

    public String getFinalize() {
        return finalize;
    }

    public void setFinalize(String finalize) {
        this.finalize = finalize;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public DateTime getExpires() {
        return expires;
    }

    public void setExpires(DateTime expires) {
        this.expires = expires;
    }

    public Map getError() {
        return error;
    }

    public void setError(Map error) {
        this.error = error;
    }

    public String getCertificate() {
        return certificate;
    }

    public void setCertificate(String certificate) {
        this.certificate = certificate;
    }
}
