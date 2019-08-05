package org.apache.http.impl.auth;

import static org.apache.http.impl.auth.NTLMEngineImpl.getType1Message;
import static org.apache.http.impl.auth.NTLMEngineImpl.getType3Message;

public class NTLMEngineTrampoline {

    public static String getResponseFor(final String message, final String username, final String password, final String host, final String domain) throws NTLMEngineException {
        final String response;
        if (message == null || message.trim().equals("")) {
            response = getType1Message(host, domain);
        } else {
            final NTLMEngineImpl.Type2Message t2m = new NTLMEngineImpl.Type2Message(message);
            response = getType3Message(username, password, host, domain, t2m.getChallenge(), t2m
                    .getFlags(), t2m.getTarget(), t2m.getTargetInfo());
        }
        return response;
    }
}
