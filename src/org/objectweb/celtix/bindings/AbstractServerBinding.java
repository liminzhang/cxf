package org.objectweb.celtix.bindings;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.wsdl.WSDLException;
import javax.xml.namespace.QName;
import javax.xml.ws.Endpoint;
import javax.xml.ws.Holder;
import javax.xml.ws.ServiceMode;
import javax.xml.ws.handler.MessageContext;

import org.objectweb.celtix.Bus;
import org.objectweb.celtix.addressing.EndpointReferenceType;
import org.objectweb.celtix.bus.EndpointUtils;
import org.objectweb.celtix.context.GenericMessageContext;
import org.objectweb.celtix.context.InputStreamMessageContext;
import org.objectweb.celtix.context.ObjectMessageContext;
import org.objectweb.celtix.context.OutputStreamMessageContext;
import org.objectweb.celtix.transports.ServerTransport;
import org.objectweb.celtix.transports.ServerTransportCallback;


public abstract class AbstractServerBinding implements ServerBinding {

    private static final Logger LOG = Logger.getLogger(AbstractServerBinding.class.getName());

    protected final Bus bus;
    protected final EndpointReferenceType reference;
    protected ServerTransport transport;
    protected Endpoint endpoint;

    public AbstractServerBinding(Bus b, EndpointReferenceType ref, Endpoint ep) {
        bus = b;
        reference = ref;
        endpoint = ep;
    }
    
    public Endpoint getEndpoint() {
        return endpoint;
    }

    public void activate() throws WSDLException, IOException {
        transport = createTransport(reference);
        
        ServerTransportCallback tc = new ServerTransportCallback() {

            public void dispatch(InputStreamMessageContext ctx, ServerTransport t) {
                AbstractServerBinding.this.dispatch(ctx, t);               
            }

            public Executor getExecutor() {
                return AbstractServerBinding.this.getEndpoint().getExecutor();
            }
            
        };
        
        transport.activate(tc);
    }

    public void deactivate() throws IOException {
        transport.deactivate();
    }

    public ObjectMessageContext createObjectContext() {
        return new ObjectMessageContextImpl();
    }

    protected abstract ServerTransport createTransport(EndpointReferenceType ref) 
        throws WSDLException, IOException;

    protected abstract MessageContext createBindingMessageContext(MessageContext orig);

    protected abstract void marshal(ObjectMessageContext objCtx, MessageContext replyCtx);

    protected void unmarshal(MessageContext requestCtx, ObjectMessageContext objCtx) {
        QName operationName = getOperationName(requestCtx);
        objCtx.put(MessageContext.WSDL_OPERATION, operationName);
    }

    protected abstract void write(MessageContext replyCtx, OutputStreamMessageContext outCtx)
        throws IOException;

    protected abstract void read(InputStreamMessageContext inCtx, MessageContext context) throws IOException;

    protected abstract MessageContext invokeOnProvider(MessageContext requestCtx, ServiceMode mode)
        throws RemoteException;

    protected void dispatch(InputStreamMessageContext inCtx, ServerTransport t) {
        LOG.info("Dispatched to binding on thread : " + Thread.currentThread());
        MessageContext requestCtx = createBindingMessageContext(inCtx);

        //Input Message
        requestCtx.put(ObjectMessageContext.MESSAGE_INPUT, Boolean.FALSE);
        // use ServerBinding to read the SAAJ model and insert it into a
        // SOAPMessageContext
        
        try {
            read(inCtx, requestCtx);
        } catch (IOException ex) {
            ex.printStackTrace();
            return;
        }

        // invoke handlers

        MessageContext replyCtx = null;

        ServiceMode mode = EndpointUtils.getServiceMode(endpoint);
        try {
            if (null != mode) {
                replyCtx = invokeOnProvider(requestCtx, mode);
            } else {
                replyCtx = invokeOnMethod(requestCtx);
            }
        } catch (RemoteException ex) {
            LOG.log(Level.SEVERE, "Failed to invoke on provider.", ex);
        }

        try {
            OutputStreamMessageContext outCtx = t.createOutputStreamContext(inCtx);
            // TODO - invoke output stream handlers
            t.finalPrepareOutputStreamContext(outCtx);

            write(replyCtx, outCtx);
        } catch (IOException ex) {
            LOG.log(Level.SEVERE, "Failed to write response.", ex);
        }
        
        LOG.info("Dispatch complete on thread : " + Thread.currentThread());
    }

    private MessageContext invokeOnMethod(MessageContext requestCtx) {

        // get operation name from message context and identify method
        // in implementor
        //REVISIT replyCtx should be created once the method is invoked
        MessageContext replyCtx = createBindingMessageContext(new GenericMessageContext());
        
        QName operationName = getOperationName(requestCtx);

        if (null == operationName) {
            LOG.severe("Request Context does not include operation name.");
            return replyCtx;
        }
        
        // get implementing method
        Method method = EndpointUtils.getMethod(endpoint, operationName);
        if (method == null) {
            LOG.severe("Web method: " + operationName + " not found in implementor.");
            return replyCtx;
        }

        // unmarshal arguments for method call - includes transferring the
        // operationName from the message context into the object context

        ObjectMessageContext objContext = createObjectContext();
        objContext.setMethod(method);
       
        unmarshal(requestCtx, objContext);
        
        // get parameters from object context and invoke on implementor

        Object result = null;
        Object params[] = (Object[])objContext.getMessageObjects();

        try {
            result = method.invoke(getEndpoint().getImplementor(), params);
        } catch (IllegalAccessException ex) {
            LOG.log(Level.SEVERE, "Failed to invoke method " + method.getName() + " on implementor.", ex);
        } catch (InvocationTargetException ex) {
            LOG.log(Level.SEVERE, "Failed to invoke method " + method.getName() + " on implementor.", ex);
        }
        objContext.setReturn(result);        
        
        // marshal objects into response object context: parameters whose type
        // is a
        // parametrized javax.xml.ws.Holder<T> are classified as in/out or out.
        List<Object> replyParamList = new ArrayList<Object>();
        if (params != null) {
            for (Object p : params) {
                if (p instanceof Holder) {
                    replyParamList.add(p);
                }
            }
        }

        if (replyParamList.size() > 0) {
            Object[] replyParams = replyParamList.toArray();
            objContext.setMessageObjects(replyParams);
        }
        
        replyCtx.put(ObjectMessageContext.MESSAGE_INPUT, Boolean.TRUE);                
        if (null != replyCtx) {
            marshal(objContext, replyCtx);
        }

        return replyCtx;
    }
    
    protected abstract QName getOperationName(MessageContext ctx);

}
