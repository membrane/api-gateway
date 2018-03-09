/* Copyright 2018 predic8 GmbH, www.predic8.com

   Licensed under the Apache License, Version 2.0 (the "License");
   you may not use this file except in compliance with the License.
   You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

   Unless required by applicable law or agreed to in writing, software
   distributed under the License is distributed on an "AS IS" BASIS,
   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
   See the License for the specific language governing permissions and
   limitations under the License. */

package com.predic8.membrane.core.exchange.snapshots;

import com.predic8.membrane.core.http.*;

import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MessageSnapshot {

    Map<String,String> header;
    String body = null;

    public MessageSnapshot(Message msg){
        header = Stream.of(msg.getHeader().getAllHeaderFields()).collect(Collectors.toMap(headerField -> headerField.getHeaderName().toString(), headerField -> headerField.getValue()));
        if(!msg.getHeader().isBinaryContentType())
            body = msg.getBodyAsStringDecoded();
    }

    public MessageSnapshot() {
    }

    public Map<String, String> getHeader() {
        return header;
    }

    public void setHeader(Map<String, String> header) {
        this.header = header;
    }

    public String getBody() {
        return body;
    }

    public void setBody(String body) {
        this.body = body;
    }

    public Header convertHeader(){
        Header result = new Header();
        header.keySet().stream().forEach(key -> result.add(key,header.get(key)));
        return result;
    }

    public AbstractBody convertBody(){
        if(body == null)
            return new EmptyBody();
        return new Body(body.getBytes());
    }
}
