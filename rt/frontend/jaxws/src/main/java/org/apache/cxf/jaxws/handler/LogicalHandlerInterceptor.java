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

package org.apache.cxf.jaxws.handler;

import javax.xml.ws.Binding;

import org.apache.cxf.message.Message;
import org.apache.cxf.phase.Phase;

public class LogicalHandlerInterceptor<T extends Message> extends AbstractJAXWSHandlerInterceptor<T> {
    
    public LogicalHandlerInterceptor(Binding binding) {
        super(binding);
        setPhase(Phase.USER_LOGICAL);
    }
    
    public void handleMessage(T message) {
        HandlerChainInvoker invoker = getInvoker(message);
        if (!invoker.getLogicalHandlers().isEmpty()) {
            LogicalMessageContextImpl lctx = new LogicalMessageContextImpl(message);
            if (!invoker.invokeLogicalHandlers(isRequestor(message), lctx)) {
                // need to abort - not sure how to do this:
                // we have access to the interceptor chain via the message but 
                // there is no support for terminating the chain yet
            }
        }
    }
    
    public void handleFault(T message) {
        // TODO
    }
    
    
    public void onCompletion(T message) {
        if (isRequestor(message) && (isOneway(message) || !isOutbound(message))) {
            getInvoker(message).mepComplete(message);
        }
    }
   
}
