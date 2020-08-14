/*
 * Copyright 2016 predic8 GmbH, www.predic8.com
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *    http://www.apache.org/licenses/LICENSE-2.0
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.interceptor.oauth2;

import com.predic8.membrane.core.http.Message;

public class TokenAuthorizationHeader {

    protected String value;

    public TokenAuthorizationHeader(Message msg) {
        value = msg.getHeader().getAuthorization();
    }

    public String getAuthorizationHeader() {
        return value;
    }

    public boolean isSet() {
        return getAuthorizationHeader() != null;
    }

    public String getToken(){
        return getAuthorizationHeader().split(" ")[1];
    }

    public String getTokenType(){
        return getAuthorizationHeader().split(" ")[0];
    }

    public boolean isValid() {
        String[] split = getAuthorizationHeader().split(" ");
        if(split.length < 2 || split[0].isEmpty() || split[1].isEmpty())
            return false;
        return true;
    }
}
