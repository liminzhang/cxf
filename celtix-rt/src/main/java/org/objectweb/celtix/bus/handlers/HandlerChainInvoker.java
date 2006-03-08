package org.objectweb.celtix.bus.handlers;


import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.xml.ws.ProtocolException;
import javax.xml.ws.handler.Handler;
import javax.xml.ws.handler.LogicalHandler;
import javax.xml.ws.handler.MessageContext;

import org.objectweb.celtix.bus.context.LogicalMessageContextImpl;
import org.objectweb.celtix.bus.context.StreamMessageContextImpl;
import org.objectweb.celtix.common.logging.LogUtils;
import org.objectweb.celtix.context.InputStreamMessageContext;
import org.objectweb.celtix.context.ObjectMessageContext;
import org.objectweb.celtix.context.OutputStreamMessageContext;
import org.objectweb.celtix.context.WebServiceContextImpl;
import org.objectweb.celtix.handlers.HandlerInvoker;
import org.objectweb.celtix.handlers.StreamHandler;

/**
 * invoke invoke the handlers in a registered handler chain
 *
 */
public class HandlerChainInvoker implements HandlerInvoker {

    private static final Logger LOG = LogUtils.getL7dLogger(HandlerChainInvoker.class);

    private final List<Handler> protocolHandlers = new ArrayList<Handler>(); 
    private final List<LogicalHandler> logicalHandlers  = new ArrayList<LogicalHandler>(); 
    private final List<StreamHandler> streamHandlers  = new ArrayList<StreamHandler>(); 
    private final List<Handler> invokedHandlers  = new ArrayList<Handler>(); 
    private final List<Handler> closeHandlers  = new ArrayList<Handler>(); 

    private boolean outbound; 
    private boolean responseExpected = true; 
    private boolean faultExpected;
    private boolean handlerProcessingAborted; 
    private boolean closed;  

    public HandlerChainInvoker(List<Handler> hc) {
        this(hc, true);
    } 

   

    public HandlerChainInvoker(List<Handler> hc, boolean isOutbound) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "invoker for chain size: " + (hc != null ? hc.size() : 0));
        }
        
        if (hc != null) { 
            for (Handler h : hc) { 
                if (h instanceof LogicalHandler) {                    
                    logicalHandlers.add((LogicalHandler)h);
                } else if (h instanceof StreamHandler) {
                    streamHandlers.add((StreamHandler)h); 
                } else { 
                    protocolHandlers.add(h);
                }
            }
        }
        outbound = isOutbound;
    }

    public boolean invokeLogicalHandlers(boolean requestor, ObjectMessageContext objectCtx) {        
        objectCtx.setRequestorRole(requestor);
        objectCtx.put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, isOutbound()); 
        LogicalMessageContextImpl logicalContext = new LogicalMessageContextImpl(objectCtx);
        return invokeHandlerChain(logicalHandlers, logicalContext); 

    }
        
    public boolean invokeProtocolHandlers(boolean requestor, MessageContext bindingContext) { 
        bindingContext.put(ObjectMessageContext.REQUESTOR_ROLE_PROPERTY, requestor);
        bindingContext.put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, isOutbound()); 

        return invokeHandlerChain(protocolHandlers, bindingContext);
    }    
    
    public boolean invokeStreamHandlers(InputStreamMessageContext ctx) {
        StreamMessageContextImpl sctx = new StreamMessageContextImpl(ctx);
        sctx.put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, this.outbound);
        // return invokeHandlerChain(streamHandlers, new StreamMessageContextImpl(ctx));
        return invokeHandlerChain(streamHandlers, sctx);
    }
    
    public boolean invokeStreamHandlers(OutputStreamMessageContext ctx) {
        StreamMessageContextImpl sctx = new StreamMessageContextImpl(ctx);
        sctx.put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, this.outbound);
        return invokeHandlerChain(streamHandlers, sctx);
    }
        
    public void closeHandlers() {
        //nothing to do
    }
    
    public void setResponseExpected(boolean expected) {
        responseExpected = expected; 
    }

    public boolean isResponseExpected() {
        return responseExpected;
    }

    public boolean isOutbound() {
        return outbound;
    }    
    
    public boolean isInbound() { 
        return !outbound;
    }
    
    public void setInbound() {
        outbound = false;
    }

    public void setOutbound() {
        outbound = true;
    }


    public boolean faultRaised(MessageContext context) {
        return (null != context && null != context.get(ObjectMessageContext.METHOD_FAULT)) 
            || faultExpected; 
    }

    public void setFault(boolean fe) { 
        faultExpected = fe;
    }

    /** Invoke handlers at the end of an MEP calling close on each.
     * The handlers must be invoked in the reverse order that they
     * appear in the handler chain.  On the server side this will not
     * be the reverse order in which they were invoked so use the
     * handler chain directly and not simply the invokedHandler list.
     */
    public void mepComplete(MessageContext context) {
        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "closing protocol handlers - handler count:" + invokedHandlers.size());
        }
        invokeClose(protocolHandlers, context);
        invokeClose(logicalHandlers, context);
        invokeClose(streamHandlers, context);
    }


    /** Indicates that the invoker is closed.  When closed, only @see
     * #mepComplete may be called.  The invoker will become closed if
     * during a invocation of handlers, a handler throws a runtime
     * exception that is not a protocol exception and no futher
     * handler or message processing is possible.
     *
     */
    public boolean isClosed() {
        return closed; 
    }

    List getInvokedHandlers() { 
        return Collections.unmodifiableList(invokedHandlers);
    }

    public List<LogicalHandler> getLogicalHandlers() { 
        return logicalHandlers;
    } 

    List<Handler> getProtocolHandlers() { 
        return protocolHandlers;
    }

    List<? extends Handler> getStreamHandlers() { 
        return streamHandlers;
    }
    
    private <T extends Handler> void invokeClose(List<T> handlers, MessageContext context) {
        handlers = reverseHandlerChain(handlers); 
        for (Handler h : handlers) {
            if (closeHandlers.contains(h)) {
                h.close(context);
            }
        }
    }

    private boolean invokeHandlerChain(List<? extends Handler> handlerChain, MessageContext ctx) { 
        if (handlerChain.isEmpty()) {
            LOG.log(Level.FINEST, "no handlers registered");        
            return true;
        }

        if (isClosed()) {
            return false;
        }

        if (LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "invoking handlers, direction: " + (outbound ? "outbound" : "inbound"));  
        }
        setMessageOutboundProperty(ctx);

        if (!outbound) {
            handlerChain = reverseHandlerChain(handlerChain);
        }
        
        boolean continueProcessing = true; 
        
        WebServiceContextImpl.setMessageContext(ctx); 

        if (!faultRaised(ctx)) {
            continueProcessing = invokeHandleMessage(handlerChain, ctx);
        } else {
            continueProcessing = invokeHandleFault(handlerChain, ctx);
        }

        if (!continueProcessing) {
            // stop processing handlers, change direction and return
            // control to the bindng.  Then the binding will invoke on
            // the next set on handlers and they will be processing in
            // the correct direction.  It would be good refactor it
            // and control all of the processing here.
            changeMessageDirection(ctx); 
            handlerProcessingAborted = true;
        }
        return continueProcessing;        
    }    

    @SuppressWarnings("unchecked")
    private boolean invokeHandleFault(List<? extends Handler> handlerChain, MessageContext ctx) {
        
        boolean continueProcessing = true; 

        try {
            for (Handler<MessageContext> h : handlerChain) {
                if (invokeThisHandler(h)) {
                    closeHandlers.add(h);
                    continueProcessing = h.handleFault(ctx);
                }
                if (!continueProcessing) {
                    break;
                }
                markHandlerInvoked(h); 
            }
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "HANDLER_RAISED_RUNTIME_EXCEPTION", e);
            continueProcessing = false; 
            closed = true;
        }
        return continueProcessing;
    } 


    @SuppressWarnings("unchecked")
    private boolean invokeHandleMessage(List<? extends Handler> handlerChain, MessageContext ctx) { 

        boolean continueProcessing = true; 
        try {
            for (Handler h : handlerChain) {
                if (invokeThisHandler(h)) {
                    closeHandlers.add(h);
                    continueProcessing = h.handleMessage(ctx);
                }
                if (!continueProcessing) {
                    break;
                }
                markHandlerInvoked(h); 
            }
        } catch (ProtocolException e) {
            LOG.log(Level.FINE, "handleMessage raised exception", e);
            continueProcessing = false;
            setFault(ctx, e);
        } catch (RuntimeException e) {
            LOG.log(Level.WARNING, "HANDLER_RAISED_RUNTIME_EXCEPTION", e);
            continueProcessing = false; 
            closed = true;
        }
        return continueProcessing;
    } 

    
    private boolean invokeThisHandler(Handler h) {
        boolean ret = true;
        // when handler processing has been aborted, only invoked on
        // previously invoked handlers
        //
        if (handlerProcessingAborted) {
            ret = invokedHandlers.contains(h);
        }
        if (ret && LOG.isLoggable(Level.FINE)) {
            LOG.log(Level.FINE, "invoking handler of type " + h.getClass().getName());
        }
        return ret; 
    }


    private void markHandlerInvoked(Handler h) {
        if (!invokedHandlers.contains(h)) { 
            invokedHandlers.add(h);
        }
    }

    private void changeMessageDirection(MessageContext context) { 
        outbound = !outbound;
        setMessageOutboundProperty(context);
        context.put(ObjectMessageContext.MESSAGE_INPUT, Boolean.TRUE);   
    }
    
    private void setMessageOutboundProperty(MessageContext context) {
        context.put(MessageContext.MESSAGE_OUTBOUND_PROPERTY, this.outbound);
    }
    
    private <T extends Handler> List<T> reverseHandlerChain(List<T> handlerChain) {
        List<T> reversedHandlerChain = new ArrayList<T>();
        reversedHandlerChain.addAll(handlerChain);
        Collections.reverse(reversedHandlerChain);
        return reversedHandlerChain;
    }
    
    protected final void setFault(MessageContext context, Exception ex) { 
        context.put(ObjectMessageContext.METHOD_FAULT, ex);
        context.setScope(ObjectMessageContext.METHOD_FAULT, MessageContext.Scope.HANDLER);
    }
}

