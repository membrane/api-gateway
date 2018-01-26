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
