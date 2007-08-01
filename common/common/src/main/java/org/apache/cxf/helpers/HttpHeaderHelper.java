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

package org.apache.cxf.helpers;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class HttpHeaderHelper {
    
    public static final String CONTENT_TYPE = "Content-Type";
    public static final String CONTENT_ID = "Content-ID";
    public static final String CONTENT_TRANSFER_ENCODING = "Content-Transfer-Encoding";
    public static final String COOKIE = "Cookie";
    public static final String TRANSFER_ENCODING = "Transfer-Encoding";
    public static final String CHUNKED = "chunked";
    public static final String CONNECTION = "Connection";
    public static final String CLOSE = "close";

    
    private static Map<String, String> internalHeaders = new HashMap<String, String>();
    
    static {
        internalHeaders.put("Content-Type", "content-type");
        internalHeaders.put("Content-ID", "content-id");
        internalHeaders.put("Content-Transfer-Encoding", "content-transfer-encoding"); 
        internalHeaders.put("Transfer-Encoding", "transfer-encoding");
        internalHeaders.put("Connection", "connection");
    }
    
    private HttpHeaderHelper() {
        
    }
    
    public static List getHeader(Map<String, List<String>> headerMap, String key) {
        return headerMap.get(getHeaderKey(key));
    }
    
    public static String getHeaderKey(String key) {
        if (internalHeaders.containsKey(key)) {
            return internalHeaders.get(key);
        } else {
            return key;
        }
    }
}
