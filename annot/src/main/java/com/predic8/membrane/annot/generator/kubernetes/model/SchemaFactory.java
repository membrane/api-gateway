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

package com.predic8.membrane.annot.generator.kubernetes.model;

import java.util.*;

public class SchemaFactory {

    public static final String OBJECT = "object";
    public static final String ARRAY = "array";

    public static SchemaObject object() {
        return object(null);
    }

    public static SchemaObject object(String name) {
        return new SchemaObject(name);
    }

    public static BasicSchema basic(String name) {
        return new BasicSchema(name);
    }

    public static AbstractSchema from(String type) {
        if ("object".equals(type)) {
            return object();
        }
        if ("array".equals(type)) {
            return array();
        }
        BasicSchema bs = new BasicSchema();
        bs.type = type;
        return bs;
    }

    public static SchemaArray array() {
        return array(null);
    }

    public static SchemaArray array(String name) {
        return new SchemaArray(name);
    }

    public static SchemaString string(String name) {
        return new SchemaString(name);
    }

    public static SchemaRef ref(String ref) {
        return new SchemaRef(ref);
    }

    public static AnyOf anyOf(List<SchemaObject> anyOfs) {
        var anyOf = new AnyOf(anyOfs);
        anyOf.name = "anyOf";
        return anyOf;
    }

    public static Schema schema(String name) {
        return new Schema(name);
    }

}
