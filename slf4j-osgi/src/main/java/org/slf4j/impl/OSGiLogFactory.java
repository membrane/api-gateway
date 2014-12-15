package org.slf4j.impl;

import org.osgi.framework.BundleContext;
import org.osgi.framework.InvalidSyntaxException;
import org.osgi.framework.ServiceEvent;
import org.osgi.framework.ServiceListener;
import org.osgi.framework.ServiceReference;
import org.osgi.service.log.LogService;
import org.slf4j.ILoggerFactory;
import org.slf4j.Logger;

/**
 * OSGiLogFactory
 *
 * @author Florian Frankenberger
 */
public class OSGiLogFactory implements ILoggerFactory {

    private static volatile BundleContext bundleContext;
    private static final ServiceListener SERVICE_LISTENER = new ServiceListener() {

        @Override
        public void serviceChanged(ServiceEvent se) {
            switch (se.getType()) {
                case ServiceEvent.REGISTERED:
                    onServiceAvailable((LogService) bundleContext.getService(se.getServiceReference()));
                    break;
                case ServiceEvent.UNREGISTERING:
                    onServiceLost();
                    break;
                default:
                    //doesn't matter - but checkstyle wants it
                    break;
            }
        }

    };

    private static volatile Logger logger = new EmptyLogger();

    @Override
    public Logger getLogger(String name) {
        return logger;
    }

    public static void initOSGi(BundleContext context) {
        bundleContext = context;
        final String serviceName = LogService.class.getName();
        ServiceReference controlReference = context.getServiceReference(serviceName);
        if (controlReference != null) {
            onServiceAvailable((LogService) context.getService(controlReference));
        }

        try {
            context.addServiceListener(SERVICE_LISTENER, "(objectclass=" + serviceName + ")");
        } catch (InvalidSyntaxException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private static void onServiceAvailable(LogService logService) {
        logger = new OSGiLogger(logService);
    }

    private static void onServiceLost() {
        logger = new EmptyLogger();
    }

}
