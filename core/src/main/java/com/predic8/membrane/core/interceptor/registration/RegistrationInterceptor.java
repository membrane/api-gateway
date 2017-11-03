/* Copyright 2015 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.registration;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.Router;
import com.predic8.membrane.core.exchange.Exchange;
import com.predic8.membrane.core.http.Request;
import com.predic8.membrane.core.http.Response;
import com.predic8.membrane.core.interceptor.AbstractInterceptor;
import com.predic8.membrane.core.interceptor.Outcome;
import com.predic8.membrane.core.interceptor.authentication.session.JdbcUserDataProvider;
import com.predic8.membrane.core.interceptor.registration.entity.User;

import java.io.IOException;
import java.sql.Connection;
import java.sql.ResultSet;

/**
 * @description Allows account registration (!Experimental!)
 */
@MCElement(name = "accountRegistration")
public class RegistrationInterceptor extends AbstractInterceptor {
    private JdbcUserDataProvider userDataProvider;

    @Override
    public void init(Router router) throws Exception {
        super.init(router);
        userDataProvider = router.getBeanFactory().getBean(JdbcUserDataProvider.class);
        userDataProvider.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) throws Exception {
        Request request = exc.getRequest();
        if (!request.isPOSTRequest()) return ErrorMessages.returnErrorBadRequest(exc);

        User user;
        try {
            user = new ObjectMapper().readValue(request.getBodyAsStringDecoded(), User.class);
        } catch (IOException e) {
            return ErrorMessages.returnErrorBadRequest(exc);
        }
        //user.setConfirmed(false); DB setzt als Standardwert 'false' gesetzt

        try (Connection connection = userDataProvider.getDatasource().getConnection()) {
            try (ResultSet rs = connection.createStatement().executeQuery(getIsAccountNameAvailableSQL(user))) {
                if (rs.next() && rs.getInt(1) != 0) return ErrorMessages.returnErrorUserAlreadyExists(exc);
            }

            if (!SecurityUtils.isHashedPassword(user.getPassword()))
                user.setPassword(SecurityUtils.createPasswdCompatibleHash(user.getPassword()));

            connection.createStatement().executeUpdate(getInsertAccountIntoDatabaseSQL(user));
        }

        //TODO: Save user mit flag if confirmated
        //TODO: Send Confirmation Email
        //TODO: PreparedStatements gegen SQL-Injection verwenden??????
        exc.setResponse(Response.ok().build());
        return Outcome.RETURN;
    }

    private String getInsertAccountIntoDatabaseSQL(User user) {
        return String.format("INSERT INTO %s", userDataProvider.getTableName()) +
                " (" + userDataProvider.getUserColumnName() + ", " + userDataProvider.getPasswordColumnName() + ")" +
                " VALUES('" + user.getEmail() + "', '" + user.getPassword() + "')";
    }

    private String getIsAccountNameAvailableSQL(User user) {
        return "SELECT COUNT(*) FROM " + userDataProvider.getTableName() +
                " WHERE " + userDataProvider.getUserColumnName() + " = '" + user.getEmail() + "'";
    }
}

/*
* Example Proxies.xml
*
    <spring:bean id="accountDB" class="org.apache.commons.dbcp.BasicDataSource" destroy-method="close">
        <spring:property name="driverClassName" value="org.h2.Driver"/>
        <spring:property name="username" value="sa"/>
        <spring:property name="password" value=""/>
        <spring:property name="url" value="jdbc:h2:mem:myDb;DB_CLOSE_DELAY=-1"/>
    </spring:bean>

    <spring:bean id="userDataProvider"
                 class="com.predic8.membrane.core.interceptor.authentication.session.JdbcUserDataProvider">
        <spring:property name="userColumnName" value="email"/>
        <spring:property name="passwordColumnName" value="password"/>
        <spring:property name="tableName" value="useraccount"/>
    </spring:bean>

    <!--Registration Server-->
    <router>
        <serviceProxy name="localhost" port="8081">
            <path>/user-registration</path>
            <accountRegistration/>
        </serviceProxy>
    </router>

    <!--Authorization Server-->
    <router>
        <serviceProxy name="Authorization Server" port="7000">
            <oauth2authserver issuer="http://localhost:7000" location="logindialog" consentFile="consentFile.json">
                <!-- UserDataProvider is exchangeable, e.g. for a database table -->
                <jdbcUserDataProvider userColumnName="email" passwordColumnName="password" tableName="useraccount"/>

                <staticClientList>
                    <client clientId="abc" clientSecret="def2000"
                            callbackUrl="http://localhost:10000/membrane/oauth2callback"/>
                </staticClientList>

                <!-- Generates tokens in the given format -->
                <bearerToken/>

                <claims value="email">
                    <!-- Scopes are defined from the claims exposed above -->
                    <scope id="profil" claims="email"/>
                </claims>
            </oauth2authserver>
        </serviceProxy>
    </router>

    <!--Authorization Client-->
    <router>
        <serviceProxy name="Resource Service" port="2000">
            <!-- Protects a resource with OAuth2 - blocks on invalid login -->
            <oauth2Resource publicURL="http://localhost:2000/">
                <membrane subject="email" src="http://localhost:7000" clientId="abc" clientSecret="def2000"
                          scope="openid profil"/>
            </oauth2Resource>

            <!-- Use the information from the authentication server and pass it to the resource server (optional) -->
            <groovy>
                def oauth2 = exc.properties.oauth2
                <!-- Put the eMail into the header X-EMAIL and pass it to the protected server. -->
                exc.request.header.setValue('X-EMAIL',oauth2.userinfo.email)
                CONTINUE
            </groovy>

            <target host="localhost" port="3000"/>
        </serviceProxy>

        <serviceProxy port="3000">
            <groovy>
                exc.setResponse(Response.ok("You accessed the protected resource! Hello " +
                exc.request.header.getFirstValue("X-EMAIL")).build())
                RETURN
            </groovy>
        </serviceProxy>

        <serviceProxy port="10000">
            <path>/membrane</path>
            <!-- Protects a resource with OAuth2 - blocks on invalid login -->
            <oauth2Resource>
                <membrane subject="email" src="http://localhost:7000" clientId="abc" clientSecret="def2000"
                          scope="openid profil"/>
            </oauth2Resource>

            <!-- Use the information from the authentication server and pass it to the resource server (optional) -->
            <groovy>
                def oauth2 = exc.properties.oauth2
                <!-- Put the eMail into the header X-EMAIL and pass it to the protected server. -->
                exc.request.header.setValue('X-TOKEN-PROVIDER',"membrane")
                exc.request.header.setValue('Authorization',"Bearer "+oauth2.accessToken)
                CONTINUE
            </groovy>

            <log/>

            <rewriter>
                <map from="/membrane/(.*)" to="/$1"/>
            </rewriter>

            <target host="localhost" port="10000"/>
        </serviceProxy>

        <serviceProxy port="10000">
            <target host="localhost" port="8080"/>
        </serviceProxy>
    </router>
* */
