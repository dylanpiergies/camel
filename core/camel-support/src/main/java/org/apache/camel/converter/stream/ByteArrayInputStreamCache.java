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
package org.apache.camel.converter.stream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.camel.Exchange;
import org.apache.camel.StreamCache;
import org.apache.camel.util.IOHelper;

/**
 * A {@link StreamCache} for {@link java.io.ByteArrayInputStream}.
 * <p/>
 * <b>Important:</b> All the classes from the Camel release that implements {@link StreamCache} is NOT intended for end
 * users to create as instances, but they are part of Camels
 * <a href="https://camel.apache.org/manual/stream-caching.html">stream-caching</a> functionality.
 */
public class ByteArrayInputStreamCache extends FilterInputStream implements StreamCache {

    private final Lock lock = new ReentrantLock();
    private final ByteArrayInputStream bais;
    private final int length;
    private byte[] byteArrayForCopy;

    public ByteArrayInputStreamCache(ByteArrayInputStream in) {
        super(in);
        this.bais = in;
        this.length = in.available();
    }

    @Override
    public void reset() {
        lock.lock();
        try {
            super.reset();
        } catch (IOException e) {
            // ignore
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void writeTo(OutputStream os) throws IOException {
        IOHelper.copyAndCloseInput(in, os);
    }

    @Override
    public StreamCache copy(Exchange exchange) throws IOException {
        if (byteArrayForCopy == null) {
            ByteArrayOutputStream baos = new ByteArrayOutputStream(in.available());
            IOHelper.copy(in, baos);
            // reset so that the stream can be reused
            reset();
            // cache the byte array, in order not to copy the byte array in the next call again
            byteArrayForCopy = baos.toByteArray();
        }
        return new InputStreamCache(byteArrayForCopy);
    }

    @Override
    public boolean inMemory() {
        return true;
    }

    @Override
    public long length() {
        return length;
    }

    @Override
    public long position() {
        return length - bais.available();
    }
}
