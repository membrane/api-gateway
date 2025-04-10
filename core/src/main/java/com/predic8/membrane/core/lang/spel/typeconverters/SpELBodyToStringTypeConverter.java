/* Copyright 2025 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.lang.spel.typeconverters;

import com.predic8.membrane.core.http.*;
import com.predic8.membrane.core.lang.spel.spelable.*;
import com.predic8.membrane.core.util.*;
import org.slf4j.*;
import org.springframework.core.convert.converter.*;

public class SpELBodyToStringTypeConverter implements Converter<SpELBody, String> {

    private static final Logger log = LoggerFactory.getLogger(SpELBodyToStringTypeConverter.class.getName());

    @Override
    public String convert(SpELBody body) {
        try {
            Message message = body.getMessage();
            message.readBody();
            return new String(MessageUtil.getContent(message));
        } catch (Exception e) {
            log.warn("Cannot log body content", e);
            throw new RuntimeException(e);
        }

    }
}
