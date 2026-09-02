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

    @Test
    void rejectsIpv6AddressesCarryingAnEmbeddedIpv4Host() throws Exception {
        RemoteUrlPolicy policy = policy(Set.of());

        // The JDK normalizes the IPv4-mapped form ::ffff:a.b.c.d to an
        // Inet4Address even when built by raw bytes (getByAddress included), so
        // those land in the IPv4 checks — which must refuse them. The deprecated
        // IPv4-compatible form ::a.b.c.d stays an Inet6Address and is what the
        // 0000::/8 rejection exists for. All three must be refused, and a real
        // global IPv6 address must keep flowing.
        assertThrows(UnknownHostException.class, () -> policy.validateResolvedHost("mapped-metadata",
                List.of(InetAddress.getByAddress(mappedIpv6(new byte[]{(byte) 169, (byte) 254, (byte) 169, (byte) 254})))));
        assertThrows(UnknownHostException.class, () -> policy.validateResolvedHost("mapped-cgnat",
                List.of(InetAddress.getByAddress(mappedIpv6(new byte[]{100, 64, 0, 1})))));
        assertThrows(UnknownHostException.class, () -> policy.validateResolvedHost("compatible-loopback",
                List.of(InetAddress.getByAddress(compatibleIpv6(new byte[]{127, 0, 0, 1})))));
        // A real global IPv6 address must keep flowing.
        policy.validateResolvedHost("public-v6", List.of(InetAddress.getByName("2606:4700:4700::1111")));
    }

    /** ::ffff:a.b.c.d — the IPv4-mapped form (bytes 0-9 zero, 10-11 = 0xff). */
    private static byte[] mappedIpv6(byte[] ipv4) {
        byte[] result = new byte[16];
        result[10] = (byte) 0xff;
        result[11] = (byte) 0xff;
        System.arraycopy(ipv4, 0, result, 12, 4);
        return result;
    }

    /** ::a.b.c.d — the deprecated IPv4-compatible form (bytes 0-11 zero). */
    private static byte[] compatibleIpv6(byte[] ipv4) {
        byte[] result = new byte[16];
        System.arraycopy(ipv4, 0, result, 12, 4);
        return result;
    }

    private RemoteUrlPolicy policy(Set<String> allowedPrivateHosts) {
        RemoteFetchProperties properties = new RemoteFetchProperties();
        properties.setAllowedPrivateHosts(allowedPrivateHosts);
        return new RemoteUrlPolicy(properties);
    }
}
