package com.predic8.contactservice.v20;

import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;

@WebService(serviceName = "ContactService20", targetNamespace="http://predic8.com/contactService/v20")
public class ContactService20 {

    @WebMethod(operationName = "addContact")
    public String addContact(
            @WebParam(name = "firstname") String firstname,
            @WebParam(name = "lastname") String lastname,
            @WebParam(name = "email") String email) {
    	
        return "Hello " + firstname + " " + lastname + " " + email + " from ContactService version 2.0 !";
    }
}
