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

package org.apache.cxf.tools.wsdlto.databinding.jaxb;

import javax.xml.namespace.QName;

import com.sun.tools.xjc.api.ClassNameAllocator;

import org.apache.cxf.service.model.InterfaceInfo;
import org.apache.cxf.tools.util.ClassCollector;
import org.apache.cxf.tools.util.NameUtil;
import org.apache.cxf.tools.util.URIParserUtil;

public class ClassNameAllocatorImpl implements ClassNameAllocator {
    private static final String TYPE_SUFFIX = "_Type";
    private ClassCollector collector;
    public ClassNameAllocatorImpl(ClassCollector classCollector) {
        collector = classCollector;
    }

    private boolean isNameCollision(String packageName, String className) {
        return collector.containSeiClass(packageName, className);
    }

    public String assignClassName(String packageName, String className) {
        String fullClzName = className;
        if (isNameCollision(packageName, className)) {
            fullClzName = className + TYPE_SUFFIX;
        }
        collector.addTypesClassName(packageName, className, packageName + "." + fullClzName);
        return fullClzName;
    }

    public void setInterface(InterfaceInfo seiInfo, String packageName) {
        QName portType = seiInfo.getName();
        String ns = portType.getNamespaceURI();
        String type = portType.getLocalPart();
        String pkgName = URIParserUtil.parsePackageName(ns, packageName);
        String className = NameUtil.mangleNameToClassName(type);
        String fullClassName = pkgName + "." + className;
        if (packageName == null) {
            packageName = pkgName;
        }
        collector.addSeiClassName(packageName, className, fullClassName);
        
    }

   
}
