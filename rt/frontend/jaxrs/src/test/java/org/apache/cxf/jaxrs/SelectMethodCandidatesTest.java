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

package org.apache.cxf.jaxrs;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;

import javax.ws.rs.Consumes;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.apache.cxf.endpoint.Endpoint;
import org.apache.cxf.jaxrs.fortest.BookEntity;
import org.apache.cxf.jaxrs.fortest.BookEntity2;
import org.apache.cxf.jaxrs.fortest.ConcreteRestController;
import org.apache.cxf.jaxrs.fortest.ConcreteRestResource;
import org.apache.cxf.jaxrs.fortest.GenericEntityImpl;
import org.apache.cxf.jaxrs.fortest.GenericEntityImpl2;
import org.apache.cxf.jaxrs.fortest.GenericEntityImpl3;
import org.apache.cxf.jaxrs.fortest.GenericEntityImpl4;
import org.apache.cxf.jaxrs.impl.MetadataMap;
import org.apache.cxf.jaxrs.model.ClassResourceInfo;
import org.apache.cxf.jaxrs.model.OperationResourceInfo;
import org.apache.cxf.jaxrs.provider.ProviderFactory;
import org.apache.cxf.jaxrs.resources.Book;
import org.apache.cxf.jaxrs.resources.Chapter;
import org.apache.cxf.jaxrs.utils.JAXRSUtils;
import org.apache.cxf.message.Exchange;
import org.apache.cxf.message.ExchangeImpl;
import org.apache.cxf.message.Message;
import org.apache.cxf.message.MessageImpl;
import org.easymock.EasyMock;

import org.junit.Assert;
import org.junit.Test;

public class SelectMethodCandidatesTest extends Assert {
    
    @Test
    public void testFindFromAbstractGenericClass() throws Exception {
        doTestGenericSuperType(BookEntity.class, "POST");
    }
    
    @Test
    public void testFindFromAbstractGenericClass2() throws Exception {
        doTestGenericSuperType(BookEntity2.class, "POST");
    }
    
    @Test
    public void testFindFromAbstractGenericInterface() throws Exception {
        doTestGenericSuperType(GenericEntityImpl.class, "POST");
    }
    
    @Test
    public void testFindFromAbstractGenericInterface2() throws Exception {
        doTestGenericSuperType(GenericEntityImpl2.class, "POST");
    }
    
    @Test
    public void testFindFromAbstractGenericImpl3() throws Exception {
        doTestGenericSuperType(GenericEntityImpl3.class, "POST");
    }
    
    @Test
    public void testFindFromAbstractGenericImpl4() throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(GenericEntityImpl4.class);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentTypes = "text/xml";
        String acceptContentTypes = "text/xml";
        
        Message m = new MessageImpl();
        m.put(Message.CONTENT_TYPE, "text/xml");
        Exchange ex = new ExchangeImpl();
        ex.setInMessage(m);
        m.setExchange(ex);
        Endpoint e = EasyMock.createMock(Endpoint.class);
        e.isEmpty();
        EasyMock.expectLastCall().andReturn(true).anyTimes();
        e.size();
        EasyMock.expectLastCall().andReturn(0).anyTimes();
        e.getEndpointInfo();
        EasyMock.expectLastCall().andReturn(null).anyTimes();
        e.get(ProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(ProviderFactory.getInstance()).times(2);
        e.get("org.apache.cxf.jaxrs.comparator");
        EasyMock.expectLastCall().andReturn(null);
        EasyMock.replay(e);
        ex.put(Endpoint.class, e);
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/books", values,
                                                                    m);
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                                                m, 
                                    "POST", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("resourceMethod needs to be selected", "postEntity",
                     ori.getMethodToInvoke().getName());
        
        String value = "<Books><Book><name>The Book</name><id>2</id></Book></Books>";
        m.setContent(InputStream.class, new ByteArrayInputStream(value.getBytes()));
        List<Object> params = JAXRSUtils.processParameters(ori, values, m);
        assertEquals(1, params.size());
        List<?> books = (List<?>)params.get(0);
        assertEquals(1, books.size());
        Book book = (Book)books.get(0);
        assertNotNull(book);
        assertEquals(2L, book.getId());
        assertEquals("The Book", book.getName());
    }
    @Test
    public void testFindFromAbstractGenericImpl5() throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(ConcreteRestController.class);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        Message m = createMessage();
        m.put(Message.CONTENT_TYPE, "text/xml");
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/", values,
                                                                    m);
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                                                m, 
                                    "POST", values, "text/xml", 
                                    JAXRSUtils.sortMediaTypes("*/*", "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("resourceMethod needs to be selected", "add",
                     ori.getMethodToInvoke().getName());
        
        String value = "<concreteRestResource><name>The Book</name></concreteRestResource>";
        m.setContent(InputStream.class, new ByteArrayInputStream(value.getBytes()));
        List<Object> params = JAXRSUtils.processParameters(ori, values, m);
        assertEquals(1, params.size());
        ConcreteRestResource book = (ConcreteRestResource)params.get(0);
        assertNotNull(book);
        assertEquals("The Book", book.getName());
    }
    
    @Test
    public void testFindFromAbstractGenericClass3() throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(BookEntity.class);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentTypes = "text/xml";
        String acceptContentTypes = "text/xml";
        
        Message m = new MessageImpl();
        m.put(Message.CONTENT_TYPE, "text/xml");
        Exchange ex = new ExchangeImpl();
        ex.setInMessage(m);
        m.setExchange(ex);
        Endpoint e = EasyMock.createMock(Endpoint.class);
        e.isEmpty();
        EasyMock.expectLastCall().andReturn(true).anyTimes();
        e.size();
        EasyMock.expectLastCall().andReturn(0).anyTimes();
        e.getEndpointInfo();
        EasyMock.expectLastCall().andReturn(null).anyTimes();
        e.get(ProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(ProviderFactory.getInstance()).times(3);
        e.get("org.apache.cxf.jaxrs.comparator");
        EasyMock.expectLastCall().andReturn(null);
        EasyMock.replay(e);
        ex.put(Endpoint.class, e);
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/books", values,
                                                                    m);
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                                                m, 
                                    "PUT", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("resourceMethod needs to be selected", "putEntity",
                     ori.getMethodToInvoke().getName());
        
        String value = "<Chapter><title>The Book</title><id>2</id></Chapter>";
        m.setContent(InputStream.class, new ByteArrayInputStream(value.getBytes()));
        List<Object> params = JAXRSUtils.processParameters(ori, values, m);
        assertEquals(1, params.size());
        Chapter c = (Chapter)params.get(0);
        assertNotNull(c);
        assertEquals(2L, c.getId());
        assertEquals("The Book", c.getTitle());
    }
    
    @Test
    public void testConsumesResource1() throws Exception {
        doTestConsumesResource(ConsumesResource1.class, "text/xml", "m2");
    }
    @Test
    public void testConsumesResource2() throws Exception {
        doTestConsumesResource(ConsumesResource2.class, "m1");
    }
    @Test
    public void testConsumesResource3() throws Exception {
        doTestConsumesResource(ConsumesResource3.class, "m1");
    }
    @Test
    public void testConsumesResource4() throws Exception {
        doTestConsumesResource(ConsumesResource4.class, "application/xml+bar", "m2");
    }
    
    private void doTestConsumesResource(Class<?> resourceClass, String expectedMethodName) throws Exception {
        doTestConsumesResource(resourceClass, null, expectedMethodName);
    }
    private void doTestConsumesResource(Class<?> resourceClass, String ct, 
                                        String expectedMethodName) throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(resourceClass);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentType = ct == null ? "application/xml" : ct;
        String acceptContentTypes = "*/*";
        
        Message m = new MessageImpl();
        m.put(Message.CONTENT_TYPE, contentType);
        Exchange ex = new ExchangeImpl();
        ex.setInMessage(m);
        m.setExchange(ex);
        Endpoint e = EasyMock.createMock(Endpoint.class);
        e.isEmpty();
        EasyMock.expectLastCall().andReturn(true).anyTimes();
        e.size();
        EasyMock.expectLastCall().andReturn(0).anyTimes();
        e.getEndpointInfo();
        EasyMock.expectLastCall().andReturn(null).anyTimes();
        e.get(ProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(ProviderFactory.getInstance()).times(2);
        e.get("org.apache.cxf.jaxrs.comparator");
        EasyMock.expectLastCall().andReturn(null);
        EasyMock.replay(e);
        ex.put(Endpoint.class, e);
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/", values,
                                                                    m);
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                                                m, 
                                    "POST", values, contentType, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals(expectedMethodName,  ori.getMethodToInvoke().getName());
    }
    
    @Test
    public void testResponseType1() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m1", 
                               "application/xml", "application/xml", "m1");
    }
    
    @Test
    public void testResponseType2() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m1", 
                               "application/*,application/json", 
                               "application/json", "m1");
    }
    @Test
    public void testResponseType3() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m1", 
                               "text/xml, application/xml;q=0.8,application/json;q=0.4", 
                               "application/xml", "m1");
    }
    
    @Test
    public void testResponseType4() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m1", 
                               "text/xml, application/xml;q=0.8,application/json;q=0.4", 
                               "application/xml", "m1");
    }
    
    @Test
    public void testResponseType5() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m2", 
                               "text/xml, application/xml;q=0.3,application/json;q=0.4", 
                               "application/json", "m2");
    }
    
    @Test
    public void testResponseType6() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m3", 
                               "text/*,application/json;q=0.4", 
                               "text/xml", "m3");
    }
    
    @Test
    public void testResponseType7() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m3", 
                               "text/*,application/json", 
                               "application/json", "m3");
    }
    
    @Test
    public void testResponseType8() throws Exception {
        doTestProducesResource(ResponseMediaTypeResource.class, "/m4", 
                               "application/*", "application/xml", "m4");
    }
    
    @Test
    public void testProducesResource1() throws Exception {
        doTestProducesResource(ProducesResource1.class, "/", 
                               "text/plain, application/xml", 
                               "application/xml", "m2");
    }
    
    @Test
    public void testProducesResource2() throws Exception {
        doTestProducesResource(ProducesResource1.class, "/", 
                               "text/plain, application/xml;q=0.8", 
                               "text/plain", "m1");
    }
    
    @Test
    public void testProducesResource3() throws Exception {
        doTestProducesResource(ProducesResource2.class, "/", 
                               "application/*", 
                               "application/json", "m1");
    }
    
    @Test
    public void testProducesResource4() throws Exception {
        doTestProducesResource(ProducesResource2.class, "/", 
                               "application/xml,application/json;q=0.9", 
                               "application/xml", "m2");
    }
    
    @Test
    public void testProducesResource5() throws Exception {
        doTestProducesResource(ProducesResource2.class, "/", 
                               "application/xml;q=0.3,application/json;q=0.5", 
                               "application/json", "m1");
    }

    @Test
    public void testProducesResource6() throws Exception {
        doTestProducesResource(ProducesResource3.class, "/", 
                               "application/xml,application/json", 
                               "application/xml", "m2");
    }

    @Test
    public void testProducesResource7() throws Exception {
        doTestProducesResource(ProducesResource4.class, "/", 
                               "application/xml,", 
                               "application/xml", "m1");
    }

    @Test
    public void testProducesResource8() throws Exception {
        doTestProducesResource(ProducesResource5.class, "/", 
                               "application/*,text/html", 
                               "text/html", "m1");
    }

    @Test
    public void testProducesResource9() throws Exception {
        doTestProducesResource(ProducesResource5.class, "/", 
                               "application/*,text/html;q=0.3", 
                               "text/html", "m1");
    }

    @Test
    public void testProducesResource10() throws Exception {
        doTestProducesResource(ProducesResource6.class, "/", 
                               "application/*,text/html", 
                               "text/html", "m1");
    }

    private void doTestProducesResource(Class<?> resourceClass, 
                                        String path,
                                        String acceptContentTypes,
                                        String expectedResponseType,
                                        String expectedMethodName) throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(resourceClass);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentType = "*/*";
        
        Message m = new MessageImpl();
        m.put(Message.CONTENT_TYPE, contentType);
        Exchange ex = new ExchangeImpl();
        ex.setInMessage(m);
        m.setExchange(ex);
        Endpoint e = EasyMock.createMock(Endpoint.class);
        e.isEmpty();
        EasyMock.expectLastCall().andReturn(true).anyTimes();
        e.size();
        EasyMock.expectLastCall().andReturn(0).anyTimes();
        e.getEndpointInfo();
        EasyMock.expectLastCall().andReturn(null).anyTimes();
        e.get(ProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(ProviderFactory.getInstance()).times(2);
        e.get("org.apache.cxf.jaxrs.comparator");
        EasyMock.expectLastCall().andReturn(null);
        EasyMock.replay(e);
        ex.put(Endpoint.class, e);
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, path, values,
                                                                    m);
     
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, m, "GET", 
                                                values, contentType, 
                                                JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                                true);

        assertNotNull(ori);
        assertEquals(expectedMethodName,  ori.getMethodToInvoke().getName());
        assertEquals(expectedResponseType, m.getExchange().get(Message.CONTENT_TYPE));
    }
    
    private void doTestGenericSuperType(Class<?> serviceClass, String methodName) throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(serviceClass);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentTypes = "text/xml";
        String acceptContentTypes = "text/xml";
        
        Message m = new MessageImpl();
        m.put(Message.CONTENT_TYPE, "text/xml");
        Exchange ex = new ExchangeImpl();
        ex.setInMessage(m);
        m.setExchange(ex);
        Endpoint e = EasyMock.createMock(Endpoint.class);
        e.isEmpty();
        EasyMock.expectLastCall().andReturn(true).anyTimes();
        e.size();
        EasyMock.expectLastCall().andReturn(0).anyTimes();
        e.getEndpointInfo();
        EasyMock.expectLastCall().andReturn(null).anyTimes();
        e.get(ProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(ProviderFactory.getInstance()).times(3);
        e.get("org.apache.cxf.jaxrs.comparator");
        EasyMock.expectLastCall().andReturn(null);
        EasyMock.replay(e);
        ex.put(Endpoint.class, e);
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/books", values,
                                                                    m);
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                                                m, 
                                    methodName, values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("resourceMethod needs to be selected", methodName.toLowerCase() + "Entity",
                     ori.getMethodToInvoke().getName());
        
        String value = "<Book><name>The Book</name><id>2</id></Book>";
        m.setContent(InputStream.class, new ByteArrayInputStream(value.getBytes()));
        List<Object> params = JAXRSUtils.processParameters(ori, values, m);
        assertEquals(1, params.size());
        Book book = (Book)params.get(0);
        assertNotNull(book);
        assertEquals(2L, book.getId());
        assertEquals("The Book", book.getName());
    }
    
    @Test
    public void testFindTargetSubResource() throws Exception {
        doTestFindTargetSubResource("/1/2/3/d/resource", "resourceMethod");
    }
    
    @Test
    public void testFindTargetSubResource2() throws Exception {
        doTestFindTargetSubResource("/1/2/3/d/resource/sub", "subresource");
    }
    
    @Test
    public void testFindTargetSubResource3() throws Exception {
        doTestFindTargetSubResource("/1/2/3/d/resource2/2/2", "resourceMethod2");
    }
    
    @Test
    public void testFindTargetSubResource4() throws Exception {
        doTestFindTargetSubResource("/1/2/3/d/resource2/1/2", "subresource2");
    }
    
    public void doTestFindTargetSubResource(String path, String method) throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(org.apache.cxf.jaxrs.resources.TestResource.class);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentTypes = "*/*";
        String acceptContentTypes = "text/xml,*/*";
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, path, values,
                                                                    new MessageImpl());
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("resourceMethod needs to be selected", method,
                     ori.getMethodToInvoke().getName());
    }
    
    @Test
    public void testSelectUsingQualityFactors() throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(org.apache.cxf.jaxrs.resources.TestResource.class);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        String contentTypes = "*/*";
        String acceptContentTypes = "application/xml;q=0.5,application/json";
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/resource1", values,
                                                                    new MessageImpl());
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("jsonResource needs to be selected", "jsonResource",
                     ori.getMethodToInvoke().getName());
    }
    
    @Test
    public void testFindTargetResourceClassWithTemplates() throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(org.apache.cxf.jaxrs.resources.TestResource.class);
        sf.create();
        
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();

        String contentTypes = "*/*";
        String acceptContentTypes = "application/xml";

        //If acceptContentTypes does not specify a specific Mime type, the  
        //method is declared with a most specific ProduceMime type is selected.
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d", values,
                                                                    new MessageImpl());
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource,
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    Collections.singletonList(MediaType.valueOf(acceptContentTypes)), true);
        assertNotNull(ori);
        assertEquals("listMethod needs to be selected", "listMethod", 
                     ori.getMethodToInvoke().getName());
        
        
        acceptContentTypes = "application/xml,application/json;q=0.8";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/1", values,
                                                  new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                        createMessage(), 
                                        "GET", values, contentTypes, 
                                        JAXRSUtils.parseMediaTypes(acceptContentTypes), true);
        assertNotNull(ori);
        assertEquals("readMethod needs to be selected", "readMethod", 
                     ori.getMethodToInvoke().getName());
        
        
        contentTypes = "application/xml";
        acceptContentTypes = "application/xml";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/1", values, new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                        createMessage(), 
                                        "GET", values, contentTypes, 
                                        Collections.singletonList(MediaType.valueOf(acceptContentTypes))
                                        , true);
        assertNotNull(ori);
        assertEquals("readMethod needs to be selected", "readMethod", 
                     ori.getMethodToInvoke().getName());
        
        contentTypes = "application/json";
        acceptContentTypes = "application/json";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/1/bar/baz/baz", values,
                                                  new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                        createMessage(), 
                                        "GET", values, contentTypes, 
                                        Collections.singletonList(MediaType.valueOf(acceptContentTypes)),
                                        true);
        assertNotNull(ori);
        assertEquals("readMethod2 needs to be selected", "readMethod2", 
                     ori.getMethodToInvoke().getName());
        
        contentTypes = "application/json";
        acceptContentTypes = "application/json";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/1", values, new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                        createMessage(), 
                                        "GET", values, contentTypes, 
                                        Collections.singletonList(MediaType.valueOf(acceptContentTypes)),
                                        true);
        assertNotNull(ori);
        assertEquals("unlimitedPath needs to be selected", "unlimitedPath", 
                     ori.getMethodToInvoke().getName());
        
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/1/2", values, new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                        createMessage(), 
                                        "GET", values, contentTypes, 
                                        Collections.singletonList(MediaType.valueOf(acceptContentTypes)),
                                        true);
        assertNotNull(ori);
        assertEquals("limitedPath needs to be selected", "limitedPath", 
                     ori.getMethodToInvoke().getName());
        
    }
    
    @Test
    public void testSelectBar() throws Exception {
        JAXRSServiceFactoryBean sf = new JAXRSServiceFactoryBean();
        sf.setResourceClasses(org.apache.cxf.jaxrs.resources.TestResource.class);
        sf.create();
        List<ClassResourceInfo> resources = ((JAXRSServiceImpl)sf.getService()).getClassResourceInfos();
        
        MetadataMap<String, String> values = new MetadataMap<String, String>();
        ClassResourceInfo resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/custom", values,
                                                                    new MessageImpl());
        
        String contentTypes = "*/*";
        String acceptContentTypes = "application/bar,application/foo;q=0.8";
        OperationResourceInfo ori = JAXRSUtils.findTargetMethod(resource, 
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("readBar", ori.getMethodToInvoke().getName());
        acceptContentTypes = "application/foo,application/bar;q=0.8";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/custom", values, new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("readFoo", ori.getMethodToInvoke().getName());
        
        acceptContentTypes = "application/foo;q=0.5,application/bar";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/custom", values, new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("readBar", ori.getMethodToInvoke().getName());
        
        acceptContentTypes = "application/foo,application/bar;q=0.5";
        resource = JAXRSUtils.selectResourceClass(resources, "/1/2/3/d/custom", values, new MessageImpl());
        ori = JAXRSUtils.findTargetMethod(resource, 
                                    createMessage(), 
                                    "GET", values, contentTypes, 
                                    JAXRSUtils.sortMediaTypes(acceptContentTypes, "q"),
                                    true);
        assertNotNull(ori);
        assertEquals("readFoo", ori.getMethodToInvoke().getName());
        
    }
    
    private Message createMessage() {
        ProviderFactory factory = ProviderFactory.getInstance();
        Message m = new MessageImpl();
        m.put("org.apache.cxf.http.case_insensitive_queries", false);
        Exchange e = new ExchangeImpl();
        m.setExchange(e);
        e.setInMessage(m);
        Endpoint endpoint = EasyMock.createMock(Endpoint.class);
        endpoint.getEndpointInfo();
        EasyMock.expectLastCall().andReturn(null).anyTimes();
        endpoint.get("org.apache.cxf.jaxrs.comparator");
        EasyMock.expectLastCall().andReturn(null);
        endpoint.size();
        EasyMock.expectLastCall().andReturn(0).anyTimes();
        endpoint.isEmpty();
        EasyMock.expectLastCall().andReturn(true).anyTimes();
        endpoint.get(ProviderFactory.class.getName());
        EasyMock.expectLastCall().andReturn(factory).anyTimes();
        EasyMock.replay(endpoint);
        e.put(Endpoint.class, endpoint);
        return m;
    }
    

    
    public static class ConsumesResource1 {
        @POST
        @Consumes({"application/xml", "text/*" })
        public void m1() {
            
        }
        @POST
        @Consumes({"application/xml", "text/xml" })
        public void m2() {
            
        }
    }
    public static class ConsumesResource2 {
        @POST
        @Consumes({"application/*", "text/xml" })
        public void m2() {
            
        }
        @POST
        @Consumes({"text/*", "application/xml" })
        public void m1() {
            
        }
    }
    public static class ConsumesResource3 {
        @POST
        @Consumes({"application/*" })
        public void m2() {
            
        }
        @POST
        @Consumes({"application/xml" })
        public void m1() {
            
        }
    }
    public static class ConsumesResource4 {
        @POST
        @Consumes({"application/xml", "application/xml+*" })
        public void m1() {
            
        }
        @POST
        @Consumes({"application/*", "application/xml+bar" })
        public void m2() {
            
        }
    }
    
    public static class ResponseMediaTypeResource {
        @GET
        @Path("m1")
        @Produces({"application/json", "application/xml" })
        public Response m1() {
            return null;
        }
        @GET
        @Path("m2")
        @Produces({"application/*" })
        public Response m2() {
            return null;
        }
        @GET
        @Path("m3")
        @Produces({"application/json", "text/xml", "application/xml" })
        public Response m3() {
            return null;
        }
        @GET
        @Path("m4")
        @Produces({"application/json;qs=0.9", "application/xml" })
        public Response m4() {
            return null;
        }
    }
    
    public static class ProducesResource1 {
        @GET
        @Produces({"text/*" })
        public Response m1() {
            return null;
        }
        @GET
        @Produces({"application/xml" })
        public Response m2() {
            return null;
        }
    }
    public static class ProducesResource2 {
        @GET
        @Produces({"application/json" })
        public Response m1() {
            return null;
        }
        @GET
        @Produces({"application/xml;qs=0.9" })
        public Response m2() {
            return null;
        }
    }

    public static class ProducesResource3 {
        @GET
        @Produces({"application/json;qs=0.2" })
        public Response m1() {
            return null;
        }
        @GET
        @Produces({"application/xml;qs=0.9" })
        public Response m2() {
            return null;
        }
    }

    public static class ProducesResource4 {
        @GET
        @Produces({"application/*" })
        public Response m1() {
            return null;
        }
        @GET
        @Produces({"application/xml;qs=0.9" })
        public Response m2() {
            return null;
        }
    }

    public static class ProducesResource5 {
        @GET
        @Produces({"text/*" })
        public Response m1() {
            return null;
        }
        @GET
        @Produces({"application/*" })
        public Response m2() {
            return null;
        }
    }

    public static class ProducesResource6 {
        @GET
        @Produces({"text/*;qs=0.9" })
        public Response m1() {
            return null;
        }
        @GET
        @Produces({"application/*" })
        public Response m2() {
            return null;
        }
    }
}
