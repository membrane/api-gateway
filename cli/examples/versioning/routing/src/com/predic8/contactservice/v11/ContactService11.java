package com.predic8.contactservice.v11;

import javax.jws.WebService;
import javax.jws.WebMethod;
import javax.jws.WebParam;

@WebService(serviceName = "ContactService11", targetNamespace="http://predic8.com/contactService/v11")
public class ContactService11 {

    @WebMethod(operationName = "addContact")
    public String addContact(
            @WebParam(name = "firstname") String firstname,
            @WebParam(name = "lastname") String lastname) {
    	
        return "Hello " + firstname + " " + lastname + " from ContactService version 1.1 !";
    }

}
