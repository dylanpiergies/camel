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
package org.apache.camel.processor;

import org.apache.camel.ContextTestSupport;
import org.apache.camel.Endpoint;
import org.apache.camel.Exchange;
import org.apache.camel.Message;
import org.apache.camel.Processor;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.apache.camel.spi.IdempotentRepository;
import org.apache.camel.support.processor.idempotent.MemoryIdempotentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class IdempotentConsumerEagerTest extends ContextTestSupport {
    protected Endpoint startEndpoint;
    protected MockEndpoint resultEndpoint;

    @Override
    public boolean isUseRouteBuilder() {
        return false;
    }

    @Test
    public void testDuplicateMessagesAreFilteredOut() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .eager(false).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedExchangesNotAddedDeadLetterChannel() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                errorHandler(deadLetterChannel("mock:error").maximumRedeliveries(2).redeliveryDelay(0).logStackTrace(false));

                from("direct:start")
                        .idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .eager(false).process(new Processor() {
                            public void process(Exchange exchange) {
                                String id = exchange.getIn().getHeader("messageId", String.class);
                                if (id.equals("2")) {
                                    throw new IllegalArgumentException("Damm I cannot handle id 2");
                                }
                            }
                        }).to("mock:result");
            }
        });
        context.start();

        // we send in 2 messages with id 2 that fails
        getMockEndpoint("mock:error").expectedMessageCount(2);
        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testFailedExchangesNotAdded() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                from("direct:start")
                        .idempotentConsumer(header("messageId"), MemoryIdempotentRepository.memoryIdempotentRepository(200))
                        .eager(false).process(new Processor() {
                            public void process(Exchange exchange) {
                                String id = exchange.getIn().getHeader("messageId", String.class);
                                if (id.equals("2")) {
                                    throw new IllegalArgumentException("Damm I cannot handle id 2");
                                }
                            }
                        }).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("1", "one");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testNotEager() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                final IdempotentRepository repo = MemoryIdempotentRepository.memoryIdempotentRepository(200);

                from("direct:start").idempotentConsumer(header("messageId"), repo).eager(false).process(new Processor() {
                    public void process(Exchange exchange) {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        // should not contain
                        assertFalse(repo.contains(id), "Should not eager add to repo");
                    }
                }).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    @Test
    public void testEager() throws Exception {
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() {
                final IdempotentRepository repo = MemoryIdempotentRepository.memoryIdempotentRepository(200);

                from("direct:start").idempotentConsumer(header("messageId"), repo).eager(true).process(new Processor() {
                    public void process(Exchange exchange) {
                        String id = exchange.getIn().getHeader("messageId", String.class);
                        // should contain
                        assertTrue(repo.contains(id), "Should eager add to repo");
                    }
                }).to("mock:result");
            }
        });
        context.start();

        resultEndpoint.expectedBodiesReceived("one", "two", "three");

        sendMessage("1", "one");
        sendMessage("2", "two");
        sendMessage("3", "three");

        assertMockEndpointsSatisfied();
    }

    protected void sendMessage(final Object messageId, final Object body) {
        template.send(startEndpoint, new Processor() {
            public void process(Exchange exchange) {
                // now lets fire in a message
                Message in = exchange.getIn();
                in.setBody(body);
                in.setHeader("messageId", messageId);
            }
        });
    }

    @Override
    @BeforeEach
    public void setUp() throws Exception {
        super.setUp();

        startEndpoint = resolveMandatoryEndpoint("direct:start");
        resultEndpoint = getMockEndpoint("mock:result");
    }

}
