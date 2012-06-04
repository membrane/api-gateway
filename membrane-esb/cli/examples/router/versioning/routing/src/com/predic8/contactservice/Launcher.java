package com.predic8.contactservice;

import javax.xml.ws.Endpoint;

import com.predic8.contactservice.v11.ContactService11;
import com.predic8.contactservice.v20.ContactService20;

public class Launcher {
    public static void main(String[] args) {
          Endpoint.publish("http://localhost:8080/ContactService/v11", new ContactService11());
          Endpoint.publish("http://localhost:8080/ContactService/v20", new ContactService20());
    }
}
