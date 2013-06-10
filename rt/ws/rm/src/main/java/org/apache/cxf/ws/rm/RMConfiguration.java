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

package org.apache.cxf.ws.rm;

import org.apache.cxf.ws.rm.manager.DeliveryAssuranceType;
import org.apache.cxf.ws.rm.manager.RM10AddressingNamespaceType;

/**
 * Configuration parameters for reliable messaging. These may be defined by a combination of Spring/Blueprint
 * configuration with default values and WS-ReliableMessagingPolicy overrides.
 */
public class RMConfiguration {
    private Long inactivityTimeout;
    private Long acknowledgementInterval;
    private Long baseRetransmissionInterval;
    private boolean exponentialBackoff;
    private boolean sequenceSTRRequired;
    private boolean sequenceTransportSecurityRequired;
    private boolean exactlyOnce;
    private DeliveryAssuranceType deliveryAssurance;
    private String rmNamespace;
    private RM10AddressingNamespaceType rm10AddressingNamespace;
    
    /**
     * Constructor.
     */
    public RMConfiguration() {
    }
    
    /**
     * Copy constructor.
     * 
     * @param base
     */
    public RMConfiguration(RMConfiguration base) {
        inactivityTimeout = base.inactivityTimeout;
        acknowledgementInterval = base.acknowledgementInterval;
        baseRetransmissionInterval = base.baseRetransmissionInterval;
        exponentialBackoff = base.exponentialBackoff;
        sequenceSTRRequired = base.sequenceSTRRequired;
        sequenceTransportSecurityRequired = base.sequenceTransportSecurityRequired;
        deliveryAssurance = base.deliveryAssurance;
        rmNamespace = base.rmNamespace;
        rm10AddressingNamespace = base.rm10AddressingNamespace;
    }
    
    /**
     *  @return Returns the exactlyOnce.
     */
    public boolean isExactlyOnce() {
        return exactlyOnce;
    }

    /**
     * @param exactlyOnce The exactlyOnce to set.
     */
    public void setExactlyOnce(boolean exactlyOnce) {
        this.exactlyOnce = exactlyOnce;
    }

    /**  
     * @return Returns the deliveryAssurance.
     */
    public DeliveryAssuranceType getDeliveryAssurance() {
        return deliveryAssurance;
    }

    /**
     * @param deliveryAssurance The deliveryAssurance to set.
     */
    public void setDeliveryAssurance(DeliveryAssuranceType deliveryAssurance) {
        this.deliveryAssurance = deliveryAssurance;
    }

    /**
     * @return inactivityTimeout
     */
    public Long getInactivityTimeout() {
        return inactivityTimeout;
    }
    
    /**
     * Get the number of milliseconds for the inactivity timeout.
     * 
     * @return milliseconds, 0 if not set
     */
    public long getInactivityTimeoutTime() {
        return inactivityTimeout == null ? 0 : inactivityTimeout.longValue();
    }

    /**
     * @param inactivityTimeout
     */
    public void setInactivityTimeout(Long inactivityTimeout) {
        this.inactivityTimeout = inactivityTimeout;
    }

    /**
     * @return acknowledgementInterval
     */
    public Long getAcknowledgementInterval() {
        return acknowledgementInterval;
    }
    
    /**
     * Get the number of milliseconds for the acknowledgment interval.
     * 
     * @return milliseconds, 0 if not set
     */
    public long getAcknowledgementIntervalTime() {
        return acknowledgementInterval == null ? 0 : acknowledgementInterval.longValue();
    }

    /**
     * @param acknowledgementInterval
     */
    public void setAcknowledgementInterval(Long acknowledgementInterval) {
        this.acknowledgementInterval = acknowledgementInterval;
    }

    /**
     * @return baseRetransmissionInterval
     */
    public Long getBaseRetransmissionInterval() {
        return baseRetransmissionInterval;
    }

    /**
     * @param baseRetransmissionInterval
     */
    public void setBaseRetransmissionInterval(Long baseRetransmissionInterval) {
        this.baseRetransmissionInterval = baseRetransmissionInterval;
    }

    /**
     * @return exponentialBackoff
     */
    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    /**
     * @param exponentialBackoff
     */
    public void setExponentialBackoff(boolean exponentialBackoff) {
        this.exponentialBackoff = exponentialBackoff;
    }

    /**
     * @return sequenceSTRRequired
     */
    public boolean isSequenceSTRRequired() {
        return sequenceSTRRequired;
    }

    /**
     * @param sequenceSTRRequired
     */
    public void setSequenceSTRRequired(boolean sequenceSTRRequired) {
        this.sequenceSTRRequired = sequenceSTRRequired;
    }

    /**
     * @return sequenceTransportSecurityRequired
     */
    public boolean isSequenceTransportSecurityRequired() {
        return sequenceTransportSecurityRequired;
    }

    /**
     * @param sequenceTransportSecurityRequired
     */
    public void setSequenceTransportSecurityRequired(boolean sequenceTransportSecurityRequired) {
        this.sequenceTransportSecurityRequired = sequenceTransportSecurityRequired;
    }

    public String getRMNamespace() {
        return rmNamespace;
    }

    public void setRMNamespace(String uri) {
        rmNamespace = uri;
    }

    public RM10AddressingNamespaceType getRM10AddressingNamespace() {
        return rm10AddressingNamespace;
    }

    public void setRM10AddressingNamespace(RM10AddressingNamespaceType addrns) {
        rm10AddressingNamespace = addrns;
    }

    /**
     * @return protocol variation
     */
    public ProtocolVariation getConfiguredProtocol() {
        String addrns = rm10AddressingNamespace == null ? null : rm10AddressingNamespace.getUri();
        return ProtocolVariation.findVariant(getRMNamespace(), addrns);
    }
}
