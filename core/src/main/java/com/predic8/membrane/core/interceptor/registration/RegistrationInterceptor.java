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
