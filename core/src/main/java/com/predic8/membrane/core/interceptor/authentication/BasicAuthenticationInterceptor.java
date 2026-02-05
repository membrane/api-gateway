/* Copyright 2009, 2012 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.authentication;

import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.util.*;
import com.predic8.membrane.core.util.security.*;

import java.util.*;

import static com.predic8.membrane.core.Constants.*;
import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.exchange.Exchange.*;
import static com.predic8.membrane.core.http.Header.*;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;
import static com.predic8.membrane.core.security.HttpSecurityScheme.*;
import static org.apache.commons.text.StringEscapeUtils.*;

/**
 * @description Blocks requests which do not have the correct RFC 1945 basic authentication credentials (HTTP header "Authentication: Basic ....").
 * @topic 3. Security and Validation
 */
@MCElement(name = "basicAuthentication")
public class BasicAuthenticationInterceptor extends AbstractInterceptor {

    private UserDataProvider userDataProvider = new StaticUserDataProvider();

    private boolean removeAuthorizationHeader = true;

    public BasicAuthenticationInterceptor() {
        name = "basic authenticator";
        setAppliedFlow(REQUEST_FLOW);
    }

    @Override
    public void init() {
        super.init();
        //to not alter the interface of "BasicAuthenticationInterceptor" in the config file the "name" attribute is renamed to "username" in code
        if (userDataProvider instanceof StaticUserDataProvider)
            for (User user : getUsers()) {
                if (user.getAttributes().containsKey("name")) {
                    String username = user.getAttributes().get("name");
                    user.getAttributes().remove("name");
                    user.getAttributes().put("username", username);
                }
            }

        userDataProvider.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        if (hasNoAuthorizationHeader(exc) || !validUser(exc)) {
            removeAuthenticationHeader(exc);
            return deny(exc);
        }
        removeAuthenticationHeader(exc);
        return CONTINUE;
    }

    private void removeAuthenticationHeader(Exchange exchange) {
        if (!removeAuthorizationHeader)
            return;
        exchange.getRequest().getHeader().removeFields(AUTHORIZATION);
    }

    private boolean validUser(Exchange exc) {
        try {
            var credentials = BasicAuthenticationUtil.getCredentials(exc);
            userDataProvider.verify(credentials.toMap());
            exc.setProperty(SECURITY_SCHEMES, List.of(BASIC().username(credentials.username())));
            return true;
        } catch (NoSuchElementException e) {
            return false;
        }
    }

    private Outcome deny(Exchange exc) {
        security(router.getConfiguration().isProduction(), getDisplayName())
                .status(401)
                .title("Unauthorized")
                .buildAndSetResponse(exc);
        exc.getResponse().setHeader(HttpUtil.createHeaders(null, "WWW-Authenticate", "Basic realm=\"%s Authentication\"".formatted(PRODUCT_NAME)));
        return ABORT;
    }

    private boolean hasNoAuthorizationHeader(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(AUTHORIZATION) == null;
    }

    public List<User> getUsers() {
        if (userDataProvider instanceof StaticUserDataProvider sud) {
            return sud.getUsers();
        }
        throw new UnsupportedOperationException("getUsers is not implemented for this userDataProvider.");
    }

    /**
     * @description A list of username/password combinations to accept.
     */
    @MCChildElement(order = 20)
    public void setUsers(List<User> users) {
        if (userDataProvider instanceof StaticUserDataProvider sud) {
            sud.setUsers(users);
            return;
        }
        throw new UnsupportedOperationException("setUsers is not implemented for this userDataProvider.");
    }

    public UserDataProvider getUserDataProvider() {
        return userDataProvider;
    }

    /**
     * @description The <i>user data provider</i> verifying a combination of a username with a password.
     */
    @MCChildElement(order = 10)
    public void setUserDataProvider(UserDataProvider userDataProvider) {
        this.userDataProvider = userDataProvider;
    }

    @Override
    public String getShortDescription() {
        return "Authenticates incoming requests based on a fixed user list.";
    }

    @Override
    public String getLongDescription() {
        StringBuilder sb = new StringBuilder();
        sb.append(getShortDescription());
        sb.append("<br/>");
        if (userDataProvider instanceof StaticUserDataProvider) {
            sb.append("Users: ");
            for (User user : ((StaticUserDataProvider) userDataProvider).getUsers()) {
                sb.append(escapeHtml4(user.getUsername()));
                sb.append(", ");
            }
            sb.delete(sb.length() - 2, sb.length());
            sb.append("<br/>Passwords are not shown.");
        }
        return sb.toString();
    }

    public boolean isRemoveAuthorizationHeader() {
        return removeAuthorizationHeader;
    }

    /**
     * @description Removes the Authorization header after successful authentication.
     * <p>
     * Default is {@code true} to prevent credentials from being forwarded to backends.
     * Set to {@code false} if both gateway and backend need to validate credentials.
     *
     * @param removeAuthorizationHeader {@code true} to remove (default), {@code false} to forward
     * @default true
     */
    @MCAttribute()
    public void setRemoveAuthorizationHeader(boolean removeAuthorizationHeader) {
        this.removeAuthorizationHeader = removeAuthorizationHeader;
    }
}
