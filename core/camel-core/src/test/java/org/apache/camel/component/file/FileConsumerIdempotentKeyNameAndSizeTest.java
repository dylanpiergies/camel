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
package org.apache.camel.component.file;

import org.apache.camel.Exchange;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.component.mock.MockEndpoint;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.DisabledIfSystemProperty;

/**
 * Unit test for the idempotentKey option.
 */
@DisabledIfSystemProperty(named = "ci.env.name", matches = ".*", disabledReason = "Flaky on Github CI")
public class FileConsumerIdempotentKeyNameAndSizeTest extends FileConsumerIdempotentTest {

    @Override
    protected RouteBuilder createRouteBuilder() {
        return new RouteBuilder() {
            public void configure() {
                from(fileUri(
                        "?idempotent=true&idempotentKey=${file:onlyname}-${file:size}&move=done/${file:name}&initialDelay=0&delay=10"))
                        .convertBodyTo(String.class).to("mock:result");
            }
        };
    }

    @Test
    public void testIdempotentDiffSize() throws Exception {
        // consume the file the first time
        MockEndpoint mock = getMockEndpoint("mock:result");
        mock.expectedBodiesReceived("Hello World");

        template.sendBodyAndHeader(fileUri(), "Hello World", Exchange.FILE_NAME, "report.txt");

        assertMockEndpointsSatisfied();

        oneExchangeDone.matchesWaitTime();

        // reset mock and set new expectations
        mock.reset();
        mock.expectedBodiesReceived("Bye World");

        // create new file which has different length
        template.sendBodyAndHeader(fileUri(), "Bye World", Exchange.FILE_NAME, "report.txt");

        assertMockEndpointsSatisfied();
    }

}
