package com.predic8.contactservice;

import javax.xml.ws.Endpoint;

import com.predic8.contactservice.v20.ContactService20;

public class Launcher {
    public static void main(String[] args) {
          Endpoint.publish("http://localhost:8080/ContactService/v20", new ContactService20());
          System.out.println("ContactService v11 and v20 up.");
    }
}
