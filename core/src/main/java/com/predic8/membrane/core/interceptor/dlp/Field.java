package com.predic8.membrane.core.interceptor.dlp;

import com.predic8.membrane.annot.MCAttribute;
import com.predic8.membrane.annot.MCElement;
import com.predic8.membrane.core.http.Message;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@MCElement(name = "field")
public class Field {

    private static final Logger log = LoggerFactory.getLogger(Field.class);

    private String name;
    private String action;

    @MCAttribute
    public void setName(String name) {
        this.name = name;
    }

    @MCAttribute
    public void setAction(String action) {
        this.action = action.toLowerCase();
    }

    public String getName() {
        return name;
    }

    public String getAction() {
        return action;
    }

    public void handleAction(Message msg) {
        String json = msg.getBodyAsStringDecoded();
        String modified = json;

        switch (action) {
            case "filter" -> modified = filter(json);
            case "mask" -> modified = mask(json);
            case "report" -> modified = "";
            default -> log.warn("Unknown DLP action: {}", action);
        }

        msg.setBodyContent(modified.getBytes());
    }

    private String filter(String json) {
        return json.replaceAll("\"(" + name + ")\"\\s*:\\s*\".*?\"\\s*,?", "");
    }

    private String mask(String json) {
        return json.replaceAll("\"(" + name + ")\"\\s*:\\s*(\".*?\"|-?\\d+(\\.\\d+)?|true|false|null)", "\"$1\":\"****\"");
    }
}
