/* Copyright 2017 predic8 GmbH, www.predic8.com

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

import tools.jackson.core.JacksonException;
import tools.jackson.databind.*;
import com.predic8.membrane.annot.*;
import com.predic8.membrane.core.exchange.*;
import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.interceptor.*;
import com.predic8.membrane.core.interceptor.authentication.session.*;
import com.predic8.membrane.core.interceptor.registration.entity.*;
import org.slf4j.*;

import java.io.*;
import java.sql.*;

import static com.predic8.membrane.core.exceptions.ProblemDetails.*;
import static com.predic8.membrane.core.interceptor.Outcome.*;

/**
 * @description Allows account registration (!Experimental!)
 */
@MCElement(name = "accountRegistration")
public class RegistrationInterceptor extends AbstractInterceptor {

    private static final Logger log = LoggerFactory.getLogger(RegistrationInterceptor.class);


    private JdbcUserDataProvider userDataProvider;

    @Override
    public void init() {
        super.init();
        userDataProvider = router.getBeanFactory().getBean(JdbcUserDataProvider.class);
        userDataProvider.init(router);
    }

    @Override
    public Outcome handleRequest(Exchange exc) {
        Request request = exc.getRequest();
        if (!request.isPOSTRequest()) return ErrorMessages.returnErrorBadRequest(exc);

        User user;
        try {
            user = new ObjectMapper().readValue(request.getBodyAsStringDecoded(), User.class);
        } catch (JacksonException e) {
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
        } catch (SQLException e) {
            log.error("",e);
            internal(router.isProduction(),getDisplayName())
                    .detail("Could not access database")
                    .exception(e)
                    .buildAndSetResponse(exc);
            return ABORT;
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
