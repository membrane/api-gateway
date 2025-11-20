/* Copyright 2022 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.membrane.core.config.spring.k8s;

import com.predic8.membrane.annot.yaml.BeanRegistry;
import org.jetbrains.annotations.*;
import org.yaml.snakeyaml.*;
import org.yaml.snakeyaml.events.*;

import java.io.*;
import java.util.*;

public class YamlLoader {

    public Envelope load(Reader reader, BeanRegistry registry) throws IOException {
        Envelope e = new Envelope();
        e.parse(new Yaml().parse(reader).iterator(), registry);
        return e;
    }

}
