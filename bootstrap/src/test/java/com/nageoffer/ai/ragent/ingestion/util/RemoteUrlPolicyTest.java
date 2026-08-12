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

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.ingestion.config.RemoteFetchProperties;
import okhttp3.HttpUrl;
import org.junit.jupiter.api.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoteUrlPolicyTest {

    @Test
    void acceptsOnlyHttpUrlsWithoutUserInfo() {
        RemoteUrlPolicy policy = policy(Set.of());

        HttpUrl result = policy.parseAndValidate("https://example.com/document.pdf");

        assertEquals("example.com", result.host());
        assertThrows(ClientException.class, () -> policy.parseAndValidate("file:///etc/passwd"));
        assertThrows(ClientException.class, () -> policy.parseAndValidate("https://user:secret@example.com/file"));
    }

    @Test
    void rejectsNonPublicResolvedAddresses() throws Exception {
        RemoteUrlPolicy policy = policy(Set.of());

        assertThrows(UnknownHostException.class,
                () -> policy.validateResolvedHost("metadata", List.of(InetAddress.getByName("169.254.169.254"))));
        assertThrows(UnknownHostException.class,
                () -> policy.validateResolvedHost("internal", List.of(InetAddress.getByName("10.0.0.1"))));
        assertThrows(UnknownHostException.class,
                () -> policy.validateResolvedHost("loopback", List.of(InetAddress.getByName("::1"))));
        assertThrows(UnknownHostException.class,
                () -> policy.validateResolvedHost("unique-local", List.of(InetAddress.getByName("fc00::1"))));
    }

    @Test
    void allowsPublicAddressesAndExplicitPrivateHosts() throws Exception {
        RemoteUrlPolicy policy = policy(Set.of("lightrag.internal"));

        policy.validateResolvedHost("example.com", List.of(InetAddress.getByName("8.8.8.8")));
        policy.validateResolvedHost("LIGHTRAG.INTERNAL", List.of(InetAddress.getByName("10.0.0.8")));
    }

    private RemoteUrlPolicy policy(Set<String> allowedPrivateHosts) {
        RemoteFetchProperties properties = new RemoteFetchProperties();
        properties.setAllowedPrivateHosts(allowedPrivateHosts);
        return new RemoteUrlPolicy(properties);
    }
}
