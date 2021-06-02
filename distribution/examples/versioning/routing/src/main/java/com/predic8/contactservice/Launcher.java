/* Copyright 2013 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */
package com.predic8.contactservice;

import javax.xml.ws.Endpoint;

import com.predic8.contactservice.v11.ContactService11;
import com.predic8.contactservice.v20.ContactService20;

public class Launcher {
    public static void main(String[] args) {
          Endpoint.publish("http://localhost:8080/ContactService/v11", new ContactService11());
          Endpoint.publish("http://localhost:8080/ContactService/v20", new ContactService20());
          System.out.println("ContactService v11 and v20 up.");
    }
}
