/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.nageoffer.ai.ragent.ingestion.util;

import com.nageoffer.ai.ragent.framework.exception.ServiceException;
import com.nageoffer.ai.ragent.ingestion.config.RemoteFetchProperties;
import okhttp3.OkHttpClient;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class HttpClientHelperSecurityTest {

    private MockWebServer server;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
    }

    @AfterEach
    void tearDown() throws Exception {
        server.shutdown();
    }

    @Test
    void explicitPrivateHostAllowlistSupportsTrustedIntranetSources() {
        server.enqueue(new MockResponse().setBody("document"));
        HttpClientHelper helper = helper(Set.of(server.getHostName()));

        HttpClientHelper.HttpFetchResponse response = helper.get(server.url("/document").toString(), Map.of());

        assertEquals("document", new String(response.body()));
    }

    @Test
    void blocksPrivateAddressesWithoutAnExplicitAllowlist() {
        HttpClientHelper helper = helper(Set.of());

        assertThrows(ServiceException.class,
                () -> helper.get(server.url("/internal").toString(), Map.of()));
    }

    @Test
    void revalidatesRedirectTargetsBeforeConnecting() {
        server.enqueue(new MockResponse()
                .setResponseCode(302)
                .setHeader("Location", "http://169.254.169.254/latest/meta-data"));
        HttpClientHelper helper = helper(Set.of(server.getHostName()));

        assertThrows(ServiceException.class,
                () -> helper.get(server.url("/redirect").toString(), Map.of()));
    }

    private HttpClientHelper helper(Set<String> allowedPrivateHosts) {
        RemoteFetchProperties properties = new RemoteFetchProperties();
        properties.setAllowedPrivateHosts(allowedPrivateHosts);
        return new HttpClientHelper(new OkHttpClient(), new RemoteUrlPolicy(properties));
    }
}
