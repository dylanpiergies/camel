/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.camel.component.snmp;

import org.apache.camel.Exchange;
import org.apache.camel.Processor;
import org.apache.camel.support.DefaultConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.TransportMapping;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.smi.Address;
import org.snmp4j.smi.GenericAddress;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;

public class SnmpTrapConsumer extends DefaultConsumer implements CommandResponder {

    private static final Logger LOG = LoggerFactory.getLogger(SnmpTrapConsumer.class);

    private final SnmpEndpoint endpoint;
    private TransportMapping<? extends Address> transport;

    public SnmpTrapConsumer(SnmpEndpoint endpoint, Processor processor) {
        super(endpoint, processor);
        this.endpoint = endpoint;
    }

    @Override
    protected void doStart() throws Exception {
        super.doStart();

        // load connection data only if the endpoint is enabled
        if (LOG.isInfoEnabled()) {
            LOG.info("Starting trap consumer on {}", this.endpoint.getServerAddress());
        }

        Address listenGenericAddress = GenericAddress.parse(this.endpoint.getServerAddress());

        // either tcp or udp
        if ("tcp".equals(endpoint.getProtocol())) {
            this.transport = new DefaultTcpTransportMapping((TcpAddress) listenGenericAddress);
        } else if ("udp".equals(endpoint.getProtocol())) {
            this.transport = new DefaultUdpTransportMapping((UdpAddress) listenGenericAddress);
        } else {
            throw new IllegalArgumentException("Unknown protocol: " + endpoint.getProtocol());
        }

        Snmp snmp = new Snmp(transport);
        snmp.addCommandResponder(this);

        // listen to the transport
        if (LOG.isDebugEnabled()) {
            LOG.debug("Starting trap consumer on {} using {} protocol", endpoint.getServerAddress(), endpoint.getProtocol());
        }
        this.transport.listen();
        if (LOG.isInfoEnabled()) {
            LOG.info("Started trap consumer on {} using {} protocol", endpoint.getServerAddress(), endpoint.getProtocol());
        }
    }

    @Override
    protected void doStop() throws Exception {
        // stop listening to the transport
        if (this.transport != null && this.transport.isListening()) {
            if (LOG.isDebugEnabled()) {
                LOG.debug("Stopping trap consumer on {}", this.endpoint.getServerAddress());
            }
            this.transport.close();
            LOG.info("Stopped trap consumer on {}", this.endpoint.getServerAddress());
        }

        super.doStop();
    }

    @Override
    public void processPdu(CommandResponderEvent event) {
        PDU pdu = event.getPDU();
        // check PDU not null
        if (pdu != null) {
            // check for INFORM
            // code take from the book "Essential SNMP"
            if (pdu.getType() != PDU.TRAP && pdu.getType() != PDU.V1TRAP && pdu.getType() != PDU.REPORT
                    && pdu.getType() != PDU.RESPONSE) {
                // first response the inform-message and then process the
                // message
                pdu.setErrorIndex(0);
                pdu.setErrorStatus(0);
                pdu.setType(PDU.RESPONSE);
                StatusInformation statusInformation = new StatusInformation();
                StateReference<?> ref = event.getStateReference();
                try {
                    event.getMessageDispatcher().returnResponsePdu(event.getMessageProcessingModel(),
                            event.getSecurityModel(),
                            event.getSecurityName(),
                            event.getSecurityLevel(), pdu,
                            event.getMaxSizeResponsePDU(), ref,
                            statusInformation);
                    if (LOG.isDebugEnabled()) {
                        LOG.debug("response to INFORM sent");
                    }
                } catch (MessageException ex) {
                    getExceptionHandler().handleException(ex);
                }
            }
            processPDU(pdu, event);
        } else {
            LOG.debug("Received invalid trap PDU");
        }
    }

    public void processPDU(PDU pdu, CommandResponderEvent event) {
        if (LOG.isDebugEnabled()) {
            LOG.debug("Received trap event for {} : {}", this.endpoint.getServerAddress(), pdu);
        }
        Exchange exchange = createExchange(pdu, event);
        try {
            getProcessor().process(exchange);
        } catch (Exception e) {
            exchange.setException(e);
        }
        if (exchange.getException() != null) {
            getExceptionHandler().handleException(exchange.getException());
        }
        releaseExchange(exchange, false);
    }

    /**
     * creates an exchange for the given message
     *
     * @param  pdu   the pdu
     * @param  event a snmp4j CommandResponderEvent
     * @return       an exchange
     */
    public Exchange createExchange(PDU pdu, CommandResponderEvent event) {
        Exchange exchange = createExchange(false);
        exchange.setIn(new SnmpMessage(getEndpoint().getCamelContext(), pdu, event));
        return exchange;
    }

}
