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
package org.apache.cxf.transport.jms.util;

import javax.jms.Destination;
import javax.jms.JMSException;
import javax.jms.Message;
import javax.jms.MessageConsumer;
import javax.jms.Session;

public final class JMSUtil {
    private static final char[] CORRELATTION_ID_PADDING = {
        '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0', '0'
    };
    
    private JMSUtil() {
    }
    
    public static Message receive(Session session,
                                  Destination replyToDestination,
                                  String correlationId,
                                  long receiveTimeout,
                                  boolean pubSubNoLocal) {
        ResourceCloser closer = new ResourceCloser();
        try {
            final String messageSelector = "JMSCorrelationID = '" + correlationId + "'";
            MessageConsumer consumer = closer.register(session.createConsumer(replyToDestination, messageSelector,
                                                 pubSubNoLocal));
            javax.jms.Message replyMessage = consumer.receive(receiveTimeout);
            if (replyMessage == null) {
                throw new RuntimeException("Timeout receiving message with correlationId "
                                           + correlationId);
            }
            return replyMessage;
        } catch (JMSException e) {
            throw convertJmsException(e);
        } finally {
            closer.close();
        }
    }

    public static RuntimeException convertJmsException(JMSException e) {
        return new RuntimeException(e.getMessage(), e);
    }

    public static String createCorrelationId(final String prefix, long i) {
        String index = Long.toHexString(i);
        StringBuilder id = new StringBuilder(prefix);
        id.append(CORRELATTION_ID_PADDING, 0, 16 - index.length());
        id.append(index);
        return id.toString();
    }
    

}
