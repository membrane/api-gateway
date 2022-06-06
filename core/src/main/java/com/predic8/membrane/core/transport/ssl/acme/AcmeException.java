package com.predic8.membrane.core.transport.ssl.acme;

import java.util.List;
import java.util.Map;

public class AcmeException extends Exception {
    public static final String TYPE_BAD_NONCE = "urn:ietf:params:acme:error:badNonce";

    private final String type;
    private final String detail;
    private final List<SubProblem> subproblems;
    private final String nonce;

    public AcmeException(String type, String detail, List<SubProblem> subproblems, String nonce) {
        super(type + " " + detail + (subproblems != null ? " " + subproblems : ""));
        this.type = type;
        this.detail = detail;
        this.subproblems = subproblems;
        this.nonce = nonce;
    }

    public String getType() {
        return type;
    }

    String getNonce() {
        return nonce;
    }

    public static class SubProblem {
        String type;
        String detail;
        Map identifier;

        public SubProblem(String type, String detail, Map identifier) {
            this.type = type;
            this.detail = detail;
            this.identifier = identifier;
        }
    }
}
