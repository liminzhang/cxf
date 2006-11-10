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

package org.apache.cxf.bus.cxf.extension;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.cxf.common.injection.ResourceInjector;
import org.apache.cxf.configuration.Configurer;
import org.apache.cxf.extension.ExtensionManager;
import org.apache.cxf.resource.ResourceManager;
import org.apache.cxf.resource.ResourceResolver;
import org.apache.cxf.resource.SinglePropertyResolver;
import org.springframework.core.io.UrlResource;

public class ExtensionManagerImpl implements ExtensionManager {

    public static final String EXTENSIONMANAGER_PROPERTY_NAME = "extensionManager";
    public static final String ACTIVATION_NAMESPACES_PROPERTY_NAME = "activationNamespaces";
    
    private final ClassLoader loader;
    private ResourceManager resourceManager;
    private Map<String, Collection<Extension>> deferred;
    private final Map<Class, Object> activated;


    public ExtensionManagerImpl(String resource, ClassLoader cl, Map<Class, Object> initialExtensions, 
        ResourceManager rm) {

        loader = cl;
        activated = initialExtensions;
        resourceManager = rm;

        ResourceResolver extensionManagerResolver =
            new SinglePropertyResolver(EXTENSIONMANAGER_PROPERTY_NAME, this);
        resourceManager.addResourceResolver(extensionManagerResolver);

        deferred = new HashMap<String, Collection<Extension>>();

        try {
            load(resource);
        } catch (IOException ex) {
            throw new ExtensionException(ex);
        }
    }

    public void activateViaNS(String namespaceURI) {
        Collection<Extension> extensions = deferred.get(namespaceURI);
        if (null == extensions) {
            return;
        }
        for (Extension e : extensions) {
            loadAndRegister(e);
        }
        extensions.clear();
        deferred.remove(namespaceURI);
    }

    final void load(String resource) throws IOException {
        Enumeration<URL> urls = Thread.currentThread().getContextClassLoader().getResources(resource);
        while (urls.hasMoreElements()) {
            URL url = urls.nextElement();
            UrlResource urlRes = new UrlResource(url);
            InputStream is = urlRes.getInputStream();
            loadFragment(is);       
        }
        
    }

    final void loadFragment(InputStream is) {
        List<Extension> extensions = new ExtensionFragmentParser().getExtensions(is);
        for (Extension e : extensions) {
            processExtension(e);
        }
    }

    final void processExtension(Extension e) {

        if (!e.isDeferred()) {
            loadAndRegister(e);
        }

        Collection<String> namespaces = e.getNamespaces();
        for (String ns : namespaces) {
            Collection<Extension> extensions = deferred.get(ns);
            if (null == extensions) {
                extensions = new ArrayList<Extension>();
                deferred.put(ns, extensions);
            }
            extensions.add(e);
        }
    }
    
    final void loadAndRegister(Extension e) {
        
        Class<?> cls = null;
        if (null != e.getInterfaceName() && !"".equals(e.getInterfaceName())) {
            cls = e.loadInterface(loader);
        }

        if (null != activated && null != cls && null != activated.get(cls)) {
            return;
        }
 
        Object obj = e.load(loader);
        
        Configurer configurer = (Configurer)(activated.get(Configurer.class));
        if (null != configurer) {
            configurer.configureBean(obj);
        }
        
        
        
        // let the object know for which namespaces it has been activated
        ResourceResolver namespacesResolver = null;
        if (null != e.getNamespaces()) {            
            namespacesResolver = new SinglePropertyResolver(ACTIVATION_NAMESPACES_PROPERTY_NAME, 
                                                            e.getNamespaces());
            resourceManager.addResourceResolver(namespacesResolver);
        }
        
        ResourceInjector injector = new ResourceInjector(resourceManager);
        
        try {
            injector.inject(obj);
        } finally {
            if (null != namespacesResolver) {
                resourceManager.removeResourceResolver(namespacesResolver);
            }
        }
        
        if (null != activated && null != e.getInterfaceName()) {
            activated.put(cls, obj);
        }
    }

}
