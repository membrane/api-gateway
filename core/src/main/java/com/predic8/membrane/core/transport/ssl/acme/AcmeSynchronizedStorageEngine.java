/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.transport.ssl.acme;

public interface AcmeSynchronizedStorageEngine {
    String getAccountKey();
    void setAccountKey(String key);

    void setKeyPair(String[] hosts, AcmeKeyPair key);
    String getPublicKey(String[] hosts);
    String getPrivateKey(String[] hosts);

    void setCertChain(String[] hosts, String caChain);
    String getCertChain(String[] hosts);

    void setToken(String host, String token);
    String getToken(String host);

    String getOAL(String[] hosts);
    void setOAL(String[] hosts, String oal);

    String getAccountURL();

    void setAccountURL(String url);

    String getAccountContacts();

    void setAccountContacts(String contacts);

    String getOALError(String[] hosts);
    void setOALError(String[] hosts, String oalError);

    String getOALKey(String[] hosts);
    void setOALKey(String[] hosts, String oalKey);

    void archiveOAL(String[] hosts);

    boolean acquireLease(long durationMillis);

    boolean prolongLease(long durationMillis);

    void releaseLease();
}
