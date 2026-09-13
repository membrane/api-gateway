/* Copyright 2026 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.interceptor.schemavalidation;

import com.predic8.membrane.core.exceptions.ProblemDetails;

import java.util.function.Consumer;

import static com.predic8.membrane.core.exceptions.ProblemDetails.user;

/**
 * How a validation failure is reported to the client.
 * <p>
 * Validation errors are not confidential - the schema a message is validated against is public -
 * so {@code production} does not suppress them; only {@code validationDetails} does. The
 * production flag is carried here purely so the
 * {@link com.predic8.membrane.core.exceptions.ProblemDetails} envelope (title masking,
 * development-mode warning) is built correctly. Validators that render their own body rather than
 * a ProblemDetails document ignore it.
 *
 * @param validationDetails {@code <validator validationDetails="...">}
 * @param production        {@code router.getConfiguration().isProduction()}
 */
public record ErrorDetailsPolicy(boolean validationDetails, boolean production) {

    /**
     * Full disclosure, development mode. The default for validators constructed directly rather
     * than through {@link ValidatorInterceptor}.
     */
    public static final ErrorDetailsPolicy FULL = new ErrorDetailsPolicy(true, false);

    /**
     * The {@link ProblemDetails} envelope reporting a rejected message: a user error naming the
     * validator and carrying {@code title}, with {@code details} applied only when the
     * configuration allows the validation details to be disclosed. Every validator that answers
     * with a ProblemDetails document builds its response here, so that the disclosure decision is
     * made in one place.
     *
     * @param component the validator's name
     * @param title     what went wrong in general terms - never a message-specific detail, since
     *                  the title is reported whether the details are disclosed or not
     * @param details   adds the top-level members describing what was wrong with the message
     */
    public ProblemDetails problemDetails(String component, String title, Consumer<ProblemDetails> details) {
        var pd = user(production, component)
                .title(title)
                .addSubType("validation");
        if (validationDetails)
            details.accept(pd);
        return pd;
    }
}
