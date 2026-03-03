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

package com.predic8.membrane.core.util.text;

import com.predic8.membrane.core.lang.*;

import java.util.function.*;

/**
 * Represents a specialized function that serializes an object into its string representation.m.
 * <p>
 * Implementations of this interface are expected to define the logic for serialization
 * of objects, translating various object types into a standardized or application-specific
 * string representation.
 */
public interface SerializationFunction extends Function<Object, String> {

    SerializationFunction JSON_SERIALIZATION = CommonBuiltInFunctions::toJSON;
    SerializationFunction XML_SERIALIZATION = ToXMLSerializer::toXML;
    SerializationFunction TEXT_SERIALIZATION = ToTextSerializer::toText;
    SerializationFunction URL_SERIALIZATION = ToURLSerializer::toURL;
    SerializationFunction IDENTITY_SERIALIZATION = SerializationUtil::identity;
    SerializationFunction SEGMENT_SERIALIZATION = SerializationUtil::pathEncode;

}
