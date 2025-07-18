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

package org.apache.camel.test.infra.opensearch.services;

import java.time.Duration;

import org.apache.camel.test.infra.common.LocalPropertyResolver;
import org.apache.camel.test.infra.common.services.ContainerEnvironmentUtil;
import org.apache.camel.test.infra.common.services.ContainerService;
import org.apache.camel.test.infra.opensearch.common.OpenSearchProperties;
import org.opensearch.testcontainers.OpensearchContainer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.containers.output.Slf4jLogConsumer;
import org.testcontainers.containers.wait.strategy.LogMessageWaitStrategy;
import org.testcontainers.utility.DockerImageName;

public class OpenSearchLocalContainerInfraService implements OpenSearchInfraService, ContainerService<OpensearchContainer> {
    private static final Logger LOG = LoggerFactory.getLogger(OpenSearchLocalContainerInfraService.class);
    private static final int OPEN_SEARCH_PORT = 9200;
    private static final String USER_NAME = "admin";
    private static final String PASSWORD = "admin";
    private final OpensearchContainer container;

    public OpenSearchLocalContainerInfraService() {
        this(LocalPropertyResolver.getProperty(OpenSearchLocalContainerInfraService.class,
                OpenSearchProperties.OPEN_SEARCH_CONTAINER));
    }

    public OpenSearchLocalContainerInfraService(String imageName) {
        container = initContainer(imageName);
    }

    public OpenSearchLocalContainerInfraService(OpensearchContainer container) {
        this.container = container;
    }

    protected OpensearchContainer initContainer(String imageName) {
        class TestInfraOpensearchContainer extends OpensearchContainer {
            public TestInfraOpensearchContainer(boolean fixedPort) {
                super(DockerImageName.parse(imageName)
                        .asCompatibleSubstituteFor("opensearchproject/opensearch"));

                // Increase the timeout from 60 seconds to 90 seconds to ensure that it will be long enough
                // on the build pipeline
                setWaitStrategy(
                        new LogMessageWaitStrategy()
                                .withRegEx(".*(\"message\":\\s?\"started[\\s?|\"].*|] started\n$)")
                                .withStartupTimeout(Duration.ofSeconds(90)));

                withLogConsumer(new Slf4jLogConsumer(LOG));

                if (fixedPort) {
                    addFixedExposedPort(OPEN_SEARCH_PORT, OPEN_SEARCH_PORT);
                }
            }
        }

        return new TestInfraOpensearchContainer(ContainerEnvironmentUtil.isFixedPort(this.getClass()));
    }

    @Override
    public int getPort() {
        return container.getMappedPort(OPEN_SEARCH_PORT);
    }

    @Override
    public String getOpenSearchHost() {
        return container.getHost();
    }

    @Override
    public String getHttpHostAddress() {
        return container.getHttpHostAddress();
    }

    @Override
    public void registerProperties() {
        System.setProperty(OpenSearchProperties.OPEN_SEARCH_HOST, getOpenSearchHost());
        System.setProperty(OpenSearchProperties.OPEN_SEARCH_PORT, String.valueOf(getPort()));
    }

    @Override
    public void initialize() {
        LOG.info("Trying to start the OpenSearch container");
        ContainerEnvironmentUtil.configureContainerStartup(container, OpenSearchProperties.OPEN_SEARCH_CONTAINER_STARTUP,
                2);

        container.start();

        registerProperties();
        LOG.info("OpenSearch instance running at {}", getHttpHostAddress());
    }

    @Override
    public void shutdown() {
        LOG.info("Stopping the OpenSearch container");
        container.stop();
    }

    @Override
    public OpensearchContainer getContainer() {
        return container;
    }

    @Override
    public String getUsername() {
        return USER_NAME;
    }

    @Override
    public String getPassword() {
        return PASSWORD;
    }
}
