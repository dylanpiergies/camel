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
package org.apache.camel.test.spring;

import org.apache.camel.Exchange;
import org.apache.camel.NamedNode;
import org.apache.camel.Processor;
import org.apache.camel.spi.Breakpoint;
import org.apache.camel.support.BreakpointSupport;
import org.apache.camel.test.spring.junit5.ProvidesBreakpoint;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class CamelSpringProvidesBreakpointTest
        extends CamelSpringPlainTest {

    @ProvidesBreakpoint
    public static Breakpoint createBreakpoint() {
        return new TestBreakpoint();
    }

    @Test
    @Override
    public void testProvidesBreakpoint() {
        assertNotNull(camelContext.getDebugger());

        start.sendBody("David");

        assertNotNull(camelContext.getDebugger());
        assertNotNull(camelContext.getDebugger().getBreakpoints());
        assertEquals(1, camelContext.getDebugger().getBreakpoints().size());

        assertInstanceOf(TestBreakpoint.class, camelContext.getDebugger().getBreakpoints().get(0));
        assertTrue(((TestBreakpoint) camelContext.getDebugger().getBreakpoints().get(0)).isBreakpointHit());
    }

    private static final class TestBreakpoint extends BreakpointSupport {

        private boolean breakpointHit;

        @Override
        public void beforeProcess(Exchange exchange, Processor processor, NamedNode definition) {
            breakpointHit = true;
        }

        public boolean isBreakpointHit() {
            return breakpointHit;
        }
    }
}
