/**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.apache.cxf.jaxrs.interceptor;

import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.ResourceBundle;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.servlet.http.HttpServletResponse;
import javax.ws.rs.InternalServerErrorException;
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.core.HttpHeaders;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.MultivaluedMap;
import javax.ws.rs.core.Response;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.WriterInterceptor;
import javax.xml.stream.XMLStreamWriter;
import javax.xml.stream.events.XMLEvent;

import org.apache.cxf.common.i18n.BundleUtils;
import org.apache.cxf.common.logging.LogUtils;
import org.apache.cxf.interceptor.AbstractOutDatabindingInterceptor;
import org.apache.cxf.io.CachedOutputStream;
import org.apache.cxf.jaxrs.impl.AsyncResponseImpl;
import org.apache.cxf.jaxrs.impl.ResponseImpl;
import org.apache.cxf.jaxrs.impl.WriterInterceptorMBW;
import org.apache.cxf.jaxrs.lifecycle.ResourceProvider;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.provider.AbstractConfigurableProvider;
import org.apache.cxf.jaxrs.provider.ServerProviderFactory;
import org.apache.cxf.jaxrs.utils.HttpUtils;
import org.apache.cxf.jaxrs.utils.InjectionUtils;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageContentsList;
import org.apache.cxf.message.MessageUtils;
import org.apache.cxf.phase.Phase;
import org.apache.cxf.staxutils.CachingXmlEventWriter;
import org.apache.cxf.staxutils.StaxUtils;
import org.apache.cxf.transport.http.AbstractHTTPDestination;

public class JAXRSOutInterceptor extends AbstractOutDatabindingInterceptor {
    private static final Logger LOG = LogUtils.getL7dLogger(JAXRSOutInterceptor.class);
    private static final ResourceBundle BUNDLE = BundleUtils.getBundle(JAXRSOutInterceptor.class);

    public JAXRSOutInterceptor() {
        super(Phase.MARSHAL);
    }

    public void handleMessage(Message message) {
        ServerProviderFactory providerFactory = ServerProviderFactory.getInstance(message);
        try {
            processResponse(providerFactory, message);
        } finally {
            Object rootInstance = message.getExchange().remove(JAXRSUtils.ROOT_INSTANCE);
            Object rootProvider = message.getExchange().remove(JAXRSUtils.ROOT_PROVIDER);
            if (rootInstance != null && rootProvider != null) {
                try {
                    ((ResourceProvider)rootProvider).releaseInstance(message, rootInstance);
                } catch (Throwable tex) {
                    LOG.warning("Exception occurred during releasing the service instance, "
                                + tex.getMessage());
                }
            }
            providerFactory.clearThreadLocalProxies();
            ClassResourceInfo cri =
                (ClassResourceInfo)message.getExchange().get(JAXRSUtils.ROOT_RESOURCE_CLASS);
            if (cri != null) {
                cri.clearThreadLocalProxies();
            }
            AsyncResponse ar = (AsyncResponse)message.getExchange().get(AsyncResponse.class);
            if (ar != null) {
                ((AsyncResponseImpl)ar).reset();
            }
        }
            

    }
    
    private void processResponse(ServerProviderFactory providerFactory, Message message) {
        
        if (isResponseAlreadyHandled(message)) {
            return;
        }
        MessageContentsList objs = MessageContentsList.getContentsList(message);
        if (objs == null || objs.size() == 0) {
            return;
        }
        
        Object responseObj = objs.get(0);
        
        Response response = null;
        if (responseObj instanceof Response) {
            response = (Response)responseObj;
        } else {
            int status = getStatus(message, responseObj != null ? 200 : 204);
            response = Response.status(status).entity(responseObj).build();
        }
        
        Exchange exchange = message.getExchange();
        OperationResourceInfo ori = (OperationResourceInfo)exchange.get(OperationResourceInfo.class
            .getName());
        
        serializeMessage(providerFactory, message, response, ori, true);        
    }

    
    
    private int getStatus(Message message, int defaultValue) {
        Object customStatus = message.getExchange().get(Message.RESPONSE_CODE);
        return customStatus == null ? defaultValue : (Integer)customStatus;
    }
    
    @SuppressWarnings("unchecked")
    private void serializeMessage(ServerProviderFactory providerFactory,
                                  Message message, 
                                  Response response, 
                                  OperationResourceInfo ori,
                                  boolean firstTry) {
        
        response = JAXRSUtils.copyResponseIfNeeded(response);
        
        final Exchange exchange = message.getExchange();
        
        Object entity = response.getEntity();
        if (response.getStatus() == 200 && entity != null && firstTry 
            && ori != null && JAXRSUtils.headMethodPossible(ori.getHttpMethod(), 
                (String)exchange.getInMessage().get(Message.HTTP_REQUEST_METHOD))) {
            LOG.info(new org.apache.cxf.common.i18n.Message("HEAD_WITHOUT_ENTITY", BUNDLE).toString());
            entity = null;
        }
               
               
        Method invoked = ori == null ? null : ori.getAnnotatedMethod() != null
            ? ori.getAnnotatedMethod() : ori.getMethodToInvoke();
        
        Annotation[] annotations = null;
        Annotation[] staticAnns = invoked != null ? invoked.getAnnotations() : new Annotation[]{};
        Annotation[] responseAnns = ((ResponseImpl)response).getEntityAnnotations();
        if (responseAnns != null) {
            annotations = new Annotation[staticAnns.length + responseAnns.length];
            System.arraycopy(staticAnns, 0, annotations, 0, staticAnns.length);
            System.arraycopy(responseAnns, 0, annotations, staticAnns.length, responseAnns.length);
        } else {
            annotations = staticAnns;
        }
        
        ((ResponseImpl)response).setStatus(
            getActualStatus(response.getStatus(), entity));
        ((ResponseImpl)response).setEntity(entity, annotations);
        
        // Prepare the headers
        MultivaluedMap<String, Object> responseHeaders = response.getMetadata();
        Map<String, List<Object>> userHeaders = 
            (Map<String, List<Object>>)message.get(Message.PROTOCOL_HEADERS);
        if (firstTry && userHeaders != null) {
            responseHeaders.putAll(userHeaders);
        }

        String responseContentType = (String)message.get(Message.CONTENT_TYPE);
        if (responseContentType != null && !responseHeaders.containsKey(HttpHeaders.CONTENT_TYPE)) {
            responseHeaders.putSingle(HttpHeaders.CONTENT_TYPE, responseContentType);
        }
        
        message.put(Message.PROTOCOL_HEADERS, responseHeaders);
        
        setResponseDate(responseHeaders, firstTry);
               
        // Run the filters
        try {
            JAXRSUtils.runContainerResponseFilters(providerFactory, response, message, ori, invoked);
        } catch (IOException ex) {
            handleWriteException(providerFactory, message, ex, firstTry);
            return;
        } catch (Throwable ex) {
            handleWriteException(providerFactory, message, ex, firstTry);
            return;
        }
   
        // Write the entity
        entity = InjectionUtils.getEntity(response.getEntity());
        setResponseStatus(message, getActualStatus(response.getStatus(), entity));
        if (entity == null) {
            responseHeaders.putSingle(HttpHeaders.CONTENT_LENGTH, "0");
            responseHeaders.remove(HttpHeaders.CONTENT_TYPE);
            message.remove(Message.CONTENT_TYPE);
            return;
        }
        
        Object ignoreWritersProp = exchange.get(JAXRSUtils.IGNORE_MESSAGE_WRITERS);
        boolean ignoreWriters = 
            ignoreWritersProp == null ? false : Boolean.valueOf(ignoreWritersProp.toString());
        if (ignoreWriters) {
            writeResponseToStream(message.getContent(OutputStream.class), entity);
            return;
        }
        
        responseContentType = (String)responseHeaders.getFirst(HttpHeaders.CONTENT_TYPE);
        MediaType responseMediaType = responseContentType == null ? MediaType.WILDCARD_TYPE 
            : JAXRSUtils.toMediaType(responseContentType);
        
        Class<?> targetType = InjectionUtils.getRawResponseClass(entity);
        Type genericType = 
            InjectionUtils.getGenericResponseType(invoked, response.getEntity(), targetType, exchange);
        annotations = ((ResponseImpl)response).getEntityAnnotations();        
        
        List<WriterInterceptor> writers = providerFactory
            .createMessageBodyWriterInterceptor(targetType, genericType, annotations, responseMediaType, message);
        
        responseMediaType = checkFinalContentType(responseMediaType);
        responseContentType = responseMediaType.toString();
        if (LOG.isLoggable(Level.FINE)) {
            LOG.fine("Response content type is: " + responseContentType);
        }
        responseHeaders.putSingle(HttpHeaders.CONTENT_TYPE, responseContentType);
        message.put(Message.CONTENT_TYPE, responseContentType);
        
        OutputStream outOriginal = message.getContent(OutputStream.class);
        if (writers == null) {
            message.put(Message.CONTENT_TYPE, "text/plain");
            message.put(Message.RESPONSE_CODE, 500);
            writeResponseErrorMessage(outOriginal, "NO_MSG_WRITER", targetType.getSimpleName());
            return;
        }
        boolean enabled = checkBufferingMode(message, writers, firstTry);
        
        try {
            
            try {
                JAXRSUtils.writeMessageBody(writers, 
                        entity, 
                        targetType, 
                        genericType, 
                        annotations, 
                        responseMediaType,
                        responseHeaders,
                        message);
                
                if (isResponseRedirected(message)) {
                    return;
                }
                checkCachedStream(message, outOriginal, enabled);
            } finally {
                if (enabled) {
                    message.setContent(OutputStream.class, outOriginal);
                    message.put(XMLStreamWriter.class.getName(), null);
                }
            }
            
        } catch (IOException ex) {
            handleWriteException(providerFactory, message, ex, firstTry);
        } catch (Throwable ex) {
            handleWriteException(providerFactory, message, ex, firstTry);
        }
    }
    
    private int getActualStatus(int status, Object responseObj) {
        if (status == -1) {
            return responseObj == null ? 204 : 200;
        } else {
            return status;
        }
    }

    private boolean checkBufferingMode(Message m, List<WriterInterceptor> writers, boolean firstTry) {
        if (!firstTry) {
            return false;
        }
        WriterInterceptor last = writers.get(writers.size() - 1);
        MessageBodyWriter<Object> w = ((WriterInterceptorMBW)last).getMBW();
        Object outBuf = m.getContextualProperty(OUT_BUFFERING);
        boolean enabled = MessageUtils.isTrue(outBuf);
        boolean configurableProvider = w instanceof AbstractConfigurableProvider;
        if (!enabled && outBuf == null && configurableProvider) {
            enabled = ((AbstractConfigurableProvider)w).getEnableBuffering();
        }
        if (enabled) {
            boolean streamingOn = configurableProvider 
                ? ((AbstractConfigurableProvider)w).getEnableStreaming() : false;
            if (streamingOn) {
                m.setContent(XMLStreamWriter.class, new CachingXmlEventWriter());
            } else {
                m.setContent(OutputStream.class, new CachedOutputStream());
            }
        }
        return enabled;
    }
    
    private void checkCachedStream(Message m, OutputStream osOriginal, boolean enabled) throws Exception {
        XMLStreamWriter writer = null;
        if (enabled) {
            writer = m.getContent(XMLStreamWriter.class);
        } else {
            writer = (XMLStreamWriter)m.get(XMLStreamWriter.class.getName());
        }
        if (writer instanceof CachingXmlEventWriter) {
            CachingXmlEventWriter cache = (CachingXmlEventWriter)writer;
            if (cache.getEvents().size() != 0) {
                XMLStreamWriter origWriter = null;
                try {
                    origWriter = StaxUtils.createXMLStreamWriter(osOriginal);
                    for (XMLEvent event : cache.getEvents()) {
                        StaxUtils.writeEvent(event, origWriter);
                    }
                } finally {
                    StaxUtils.close(origWriter);
                }
            }
            m.setContent(XMLStreamWriter.class, null);
            return;
        }
        if (enabled) {
            OutputStream os = m.getContent(OutputStream.class);
            if (os != osOriginal && os instanceof CachedOutputStream) {
                CachedOutputStream cos = (CachedOutputStream)os;
                if (cos.size() != 0) {
                    cos.writeCacheTo(osOriginal);
                }
            }
        }
    }
    
    
    private void handleWriteException(ServerProviderFactory pf,
                                      Message message, 
                                      Throwable ex,
                                      boolean firstTry) {
        Response excResponse = null;
        if (firstTry) {
            excResponse = JAXRSUtils.convertFaultToResponse(ex, message);
        }
        if (excResponse == null) {
            setResponseStatus(message, 500);
            throw new InternalServerErrorException(ex);
        } else {
            serializeMessage(pf, message, excResponse, null, false);
        } 
            
    }
    
    
    private void writeResponseErrorMessage(OutputStream out, String errorString, 
                                           String parameter) {
        try {
            org.apache.cxf.common.i18n.Message message = 
                new org.apache.cxf.common.i18n.Message(errorString,
                                                   BUNDLE,
                                                   parameter);
            LOG.warning(message.toString());
            if (out != null) {
                out.write(message.toString().getBytes("UTF-8"));
            }
        } catch (IOException another) {
            // ignore
        }
    }
    
    
    private MediaType checkFinalContentType(MediaType mt) {
        if (mt.isWildcardType() || mt.isWildcardSubtype() && mt.getType().equals("application")) {
            return MediaType.APPLICATION_OCTET_STREAM_TYPE;
        } else if (mt.getParameters().containsKey("q")) {
            return JAXRSUtils.toMediaType(JAXRSUtils.removeMediaTypeParameter(mt, "q"));
        } else {
            return mt;
        }
        
    }
    
    private void setResponseDate(MultivaluedMap<String, Object> headers, boolean firstTry) {
        if (!firstTry) {
            return;
        }
        SimpleDateFormat format = HttpUtils.getHttpDateFormat();
        headers.putSingle(HttpHeaders.DATE, format.format(new Date()));
    }
    
    private boolean isResponseAlreadyHandled(Message m) {
        return isResponseAlreadyCommited(m) || isResponseRedirected(m);
    }
    
    private boolean isResponseAlreadyCommited(Message m) {
        return Boolean.TRUE.equals(m.getExchange().get(AbstractHTTPDestination.RESPONSE_COMMITED));
    }

    private boolean isResponseRedirected(Message outMessage) {
        return Boolean.TRUE.equals(outMessage.get(AbstractHTTPDestination.REQUEST_REDIRECTED));
    }
    
    private void writeResponseToStream(OutputStream os, Object responseObj) {
        try {
            byte[] bytes = responseObj.toString().getBytes("UTF-8");
            os.write(bytes, 0, bytes.length);
        } catch (Exception ex) {
            LOG.severe("Problem with writing the data to the output stream");
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
    }
   
    private void setResponseStatus(Message message, int status) {
        message.put(Message.RESPONSE_CODE, status);   
        boolean responseHeadersCopied = isResponseHeadersCopied(message);
        if (responseHeadersCopied) {
            HttpServletResponse response = 
                (HttpServletResponse)message.get(AbstractHTTPDestination.HTTP_RESPONSE);
            response.setStatus(status);
        }
    }
    
    // Some CXF interceptors such as FIStaxOutInterceptor will indirectly initiate
    // an early copying of response code and headers into the HttpServletResponse
    // TODO : Pushing the filter processing and copying response headers into say
    // PRE-LOGICAl and PREPARE_SEND interceptors will most likely be a good thing
    // however JAX-RS MessageBodyWriters are also allowed to add response headers
    // which is reason why a MultipartMap parameter in MessageBodyWriter.writeTo 
    // method is modifiable. Thus we do need to know if the initial copy has already
    // occurred: for now we will just use to ensure the correct status is set
    private boolean isResponseHeadersCopied(Message message) {
        return MessageUtils.isTrue(message.get(AbstractHTTPDestination.RESPONSE_HEADERS_COPIED));
    }
}
