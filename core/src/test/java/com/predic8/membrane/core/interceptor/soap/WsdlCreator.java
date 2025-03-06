package com.predic8.membrane.core.interceptor.soap;

import com.predic8.wsdl.*;

public interface WsdlCreator<Context extends WsdlCreatorContext> {

    void createDefinitions(Definitions definitions, Context context);

    void createImport(Import imp, Context context);

    void createTypes(Types types, Context context);

    void createMessage(Message message, Context context);

    void createPart(Part part, Context context);

    void createPortType(PortType portType, Context context);

    void createOperation(Operation operation, Context context);

    void createBinding(Binding binding, Context context);

    void createSoapBinding(AbstractSOAPBinding binding, Context context);

    void createBindingOperation(BindingOperation operation, Context context);

    void createService(Service service, Context context);

    void createPort(Port port, Context context);
}
