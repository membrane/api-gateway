/*
 *  Copyright 2026 predic8 GmbH, www.predic8.com
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */

package com.predic8.membrane.core.openapi.validators;

/**
 * A validation message that is rendered lazily so submitted (potentially sensitive) values can be
 * masked independently per output sink. The raw value is captured by the lambda and only turned
 * into text when {@link #render(boolean)} is called - with {@code maskValues=true} it is replaced
 * with {@link ValidationError#MASK}, otherwise it is shown in clear.
 */
@FunctionalInterface
public interface MaskableMessage {
    String render(boolean maskValues);
}
