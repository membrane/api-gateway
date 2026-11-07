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

package com.predic8.membrane.core.http;

import com.predic8.membrane.core.exceptions.ProblemDetails;

import java.util.Optional;

import static org.apache.commons.lang3.exception.ExceptionUtils.getThrowableList;

/**
 * Cause of a {@link ReadingBodyException} that describes, for the sender, what is wrong with its
 * message. What to tell the sender then stays with the code that detects the failure, and
 * {@link ProblemDetails#bodyFailure} needs no branch of its own for each kind of failure.
 * <p>
 * The exception message of an implementation explains the failure completely, so it is logged
 * without a stack trace ({@link ReadingBodyException#exceptionMessageIsSufficient()}).
 */
public interface MessageProblem {

    /**
     * Describes the failure of a request to its sender. Called only when the failure belongs to the
     * request, never for a backend response.
     *
     * @param problem a user error, already attributed to the component and the request flow
     * @return the problem with a subtype, a title, a detail and any fields that stay visible in
     * production. Use {@link ProblemDetails#detail(String, boolean)} with {@code true} only for text
     * written for the sender, and put exception messages into {@link ProblemDetails#internal}.
     */
    ProblemDetails describe(ProblemDetails problem);

    /**
     * @return the first {@link MessageProblem} in the cause chain of the throwable, the throwable
     * itself included
     */
    static Optional<MessageProblem> in(Throwable throwable) {
        return getThrowableList(throwable).stream()
                .filter(MessageProblem.class::isInstance)
                .map(MessageProblem.class::cast)
                .findFirst();
    }
}
