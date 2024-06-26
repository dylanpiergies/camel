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
package org.apache.camel.builder;

import java.lang.reflect.Field;

import org.apache.camel.CamelContext;
import org.apache.camel.Message;
import org.apache.camel.TestSupport;
import org.apache.camel.impl.DefaultCamelContext;
import org.apache.camel.processor.SendProcessor;
import org.apache.camel.processor.transformer.ProcessorTransformer;
import org.apache.camel.spi.DataType;
import org.apache.camel.spi.Transformer;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class TransformerBuilderTest extends TestSupport {

    @Test
    public void testEndpointTransformer() throws Exception {
        CamelContext ctx = new DefaultCamelContext();
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() {
                transformer().fromType("json:foo").toType("xml:bar").withUri("direct:transformer");
                from("direct:transformer").log("test");
            }
        };
        ctx.addRoutes(builder);
        ctx.start();
        Transformer transformer = ctx.resolveTransformer(new DataType("json:foo"), new DataType("xml:bar"));
        assertNotNull(transformer);
        assertEquals(ProcessorTransformer.class, transformer.getClass());
        ProcessorTransformer pt = (ProcessorTransformer) transformer;
        Field f = ProcessorTransformer.class.getDeclaredField("processor");
        f.setAccessible(true);
        Object processor = f.get(pt);
        assertEquals(SendProcessor.class, processor.getClass());
        SendProcessor sp = (SendProcessor) processor;
        assertEquals("direct://transformer", sp.getEndpoint().getEndpointUri());
    }

    @Test
    public void testCustomTransformer() throws Exception {
        CamelContext ctx = new DefaultCamelContext();
        RouteBuilder builder = new RouteBuilder() {
            @Override
            public void configure() {
                transformer().name("other").withJava(MyTransformer.class);
                from("direct:input").log("test");
            }
        };
        ctx.addRoutes(builder);
        ctx.start();
        Transformer transformer = ctx.resolveTransformer("other");
        assertNotNull(transformer);
        assertEquals(MyTransformer.class, transformer.getClass());
    }

    public static class MyTransformer extends Transformer {
        @Override
        public void transform(Message message, DataType from, DataType to) {
            message.getBody();
        }
    }
}
