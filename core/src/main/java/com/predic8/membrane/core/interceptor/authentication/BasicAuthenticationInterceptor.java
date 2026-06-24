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

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCChildElement;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider;
import com.predic8.membrane.core.interceptor.authentication.session.StaticUserDataProvider.UserConfig;
import com.predic8.membrane.core.interceptor.authentication.session.User;
import com.predic8.membrane.core.interceptor.authentication.session.UserDataProvider;
import com.predic8.membrane.core.util.security.BasicAuthenticationUtil;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.NoSuchElementException;

import static com.predic8.membrane.core.exceptions.ProblemDetails.security;
import static com.predic8.membrane.core.exchange.Exchange.SECURITY_SCHEMES;
import static com.predic8.membrane.core.http.Header.AUTHORIZATION;
import static com.predic8.membrane.core.http.Header.CLOSE;
import static com.predic8.membrane.core.interceptor.Interceptor.Flow.Set.REQUEST_FLOW;
import static com.predic8.membrane.core.interceptor.Outcome.ABORT;
import static com.predic8.membrane.core.interceptor.Outcome.CONTINUE;
import static com.predic8.membrane.core.security.HttpSecurityScheme.BASIC;

/**
 * @description Requires HTTP Basic credentials (the <code>Authorization: Basic</code> header) on each request and
 * verifies them against the configured users or user data provider. On success the request continues and an HTTP Basic
 * security scheme carrying the username is attached to the exchange; the <code>Authorization</code> header is removed
 * before the request is forwarded. A missing or invalid credential is rejected with 401 and a
 * <code>WWW-Authenticate</code> challenge. See the examples under
 * examples/security/basic-auth and the tutorial tutorials/security/30-Basic-Authentication.yaml.
 * <pre>
 * basicAuthentication:
 *   [ removeAuthorizationHeader: true | false ]   # default: true
 *   users:                                        # built-in store
 *     - username: alice
 *       password: secret
 *     ...
 *   # or, instead of users, a pluggable provider:
 *   [ staticUserDataProvider | htpasswdFileProvider | jdbcUserDataProvider | ldapUserDataProvider ]
 * </pre>
 * @yaml <pre><code>
 * api:
 *   port: 2000
 *   flow:
 *     - basicAuthentication:
 *         users:
 *           - username: user
 *             password: user123
 *           - username: admin
 *             password: admin456
 *   target:
 *     url: https://api.predic8.de
 * </code></pre>
 * @topic 3. Security and Validation
 */
@MCElement(name = "basicAuthentication")
public class BasicAuthenticationInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(BasicAuthenticationInterceptor.class);

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
        } catch (Exception e) {
            log.warn("", e);
            return false;
        }
    }

    Outcome deny(Exchange exc) {
        security(router.getConfiguration().isProduction(), getDisplayName())
                .status(401)
                .title("Unauthorized")
                .buildAndSetResponse(exc);
        var header = exc.getResponse().getHeader();
        header.setConnection(CLOSE); // Stay compliant with old implementations.
        header.setWwwAuthenticate("membrane");
        return ABORT;
    }

    private boolean hasNoAuthorizationHeader(Exchange exc) {
        return exc.getRequest().getHeader().getFirstValue(AUTHORIZATION) == null;
    }

    public List<UserConfig> getUsers() {
        if (userDataProvider instanceof StaticUserDataProvider sud) {
            return sud.getUsers();
        }
        throw new UnsupportedOperationException("getUsers is not implemented for this userDataProvider.");
    }

    /**
     * @description Username/password pairs accepted by the built-in user store. Used when no userDataProvider is
     * configured; each entry takes a <code>username</code> and a <code>password</code>.
     */
    @MCChildElement(order = 20)
    public void setUsers(List<UserConfig> users) {
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
     * @description Source that verifies a username/password pair, such as a file, JDBC database, or LDAP directory.
     * Defaults to an in-memory provider populated from the users list.
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
        return "%s<br/>Number of users: %d".formatted(getShortDescription(), getUsers().size());
    }

    public boolean isRemoveAuthorizationHeader() {
        return removeAuthorizationHeader;
    }

    /**
     * @description Whether to strip the <code>Authorization</code> header after successful authentication so the
     * credentials are not forwarded to the backend. Set to <code>false</code> when the backend also validates them.
     * @default true
     * @example false
     */
    @MCAttribute()
    public void setRemoveAuthorizationHeader(boolean removeAuthorizationHeader) {
        this.removeAuthorizationHeader = removeAuthorizationHeader;
    }
}
