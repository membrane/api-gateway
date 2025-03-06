package com.predic8.membrane.core.interceptor.soap;

import com.predic8.soamodel.*;
import com.predic8.wsdl.*;

public class AbstractWsdlCreator extends AbstractCreator implements WsdlCreator<WsdlCreatorContext> {


    @Override
    public void createDefinitions(Definitions definitions, WsdlCreatorContext ctx) {
        System.out.println("WsdlCreator.createDefinitions");
        
        definitions.getPortTypes().forEach(portType -> {
            portType.create(this, ctx);
        });
    }

    @Override
    public void createImport(Import imp, WsdlCreatorContext context) {

    }

    @Override
    public void createTypes(Types types, WsdlCreatorContext context) {

    }

    @Override
    public void createMessage(Message message, WsdlCreatorContext context) {
        System.out.println("message = " + message);

    }

    @Override
    public void createPart(Part part, WsdlCreatorContext context) {

    }

    @Override
    public void createPortType(PortType portType, WsdlCreatorContext context) {
        System.out.println("PortType: " + portType.getName());
        
        portType.getOperations().forEach(operation -> {
            operation.create(this, context);
        });

    }

    @Override
    public void createOperation(Operation operation, WsdlCreatorContext context) {
        System.out.println("operation.getName() = " + operation.getName());

        operation.getInput().create(this, context);
        if (operation.getOutput() != null) {
            operation.getOutput().create(this, context);
        }
    }

    @Override
    public void createBinding(Binding binding, WsdlCreatorContext context) {

    }

    @Override
    public void createSoapBinding(AbstractSOAPBinding binding, WsdlCreatorContext context) {

    }

    @Override
    public void createBindingOperation(BindingOperation operation, WsdlCreatorContext context) {

    }

    @Override
    public void createService(Service service, WsdlCreatorContext context) {

    }

    @Override
    public void createPort(Port port, WsdlCreatorContext context) {

    }
}
