package com.predic8.membrane.core.interceptor.oauth2server;

import com.predic8.membrane.annot.MCElement;

@MCElement(name="claim", topLevel=false, id="supportedClaims-claim")
public class Claim {

    String claimName;

    public Claim(){

    }

    public Claim(String claim) {

    }

    public String getClaimName() {
        return claimName;
    }

    public void setClaimName(String claimName) {
        this.claimName = claimName;
    }
}
