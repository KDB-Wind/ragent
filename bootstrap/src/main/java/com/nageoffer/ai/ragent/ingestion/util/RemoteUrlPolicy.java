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
import lombok.RequiredArgsConstructor;
import okhttp3.HttpUrl;
import org.springframework.stereotype.Component;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
public class RemoteUrlPolicy {

    private final RemoteFetchProperties properties;

    public HttpUrl parseAndValidate(String rawUrl) {
        HttpUrl candidate = rawUrl == null ? null : HttpUrl.parse(rawUrl.trim());
        if (candidate == null || !("http".equals(candidate.scheme()) || "https".equals(candidate.scheme()))) {
            throw new ClientException("远程地址只允许使用 HTTP 或 HTTPS");
        }
        validateForRequest(candidate);

        // Keep the canonical URI host comparison explicit. Besides preventing parser ambiguity
        // between URI and OkHttp, this is a sanitizer pattern understood by CodeQL's SSRF query.
        URI canonicalUri = URI.create(candidate.toString());
        if (canonicalUri.getHost() != null && canonicalUri.getHost().equals(candidate.host())) {
            HttpUrl safeUrl = HttpUrl.parse(canonicalUri.toASCIIString());
            if (safeUrl != null) {
                return safeUrl;
            }
        }
        throw new ClientException("远程地址格式不安全");
    }

    public void validateForRequest(HttpUrl url) {
        if (!("http".equals(url.scheme()) || "https".equals(url.scheme()))
                || !url.username().isEmpty()
                || !url.password().isEmpty()
                || url.host().isBlank()) {
            throw new ClientException("远程地址格式不安全");
        }
    }

    public void validateResolvedHost(String host, List<InetAddress> addresses) throws UnknownHostException {
        if (isAllowedPrivateHost(host)) {
            return;
        }
        if (addresses == null || addresses.isEmpty()) {
            throw new UnknownHostException("远程主机没有可用地址");
        }
        for (InetAddress address : addresses) {
            if (!isPublicAddress(address)) {
                throw new UnknownHostException("远程主机解析到不允许访问的地址");
            }
        }
    }

    private boolean isAllowedPrivateHost(String host) {
        Set<String> allowed = properties.getAllowedPrivateHosts();
        if (allowed == null || allowed.isEmpty() || host == null) {
            return false;
        }
        String normalized = host.toLowerCase(Locale.ROOT);
        return allowed.stream()
                .filter(value -> value != null)
                .map(value -> value.trim().toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet())
                .contains(normalized);
    }

    private boolean isPublicAddress(InetAddress address) {
        if (address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()) {
            return false;
        }
        byte[] bytes = address.getAddress();
        if (address instanceof Inet4Address) {
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            int third = Byte.toUnsignedInt(bytes[2]);
            return first != 0
                    && !(first == 100 && second >= 64 && second <= 127)
                    && !(first == 192 && second == 0 && third == 0)
                    && !(first == 192 && second == 0 && third == 2)
                    && !(first == 198 && (second == 18 || second == 19))
                    && !(first == 198 && second == 51 && third == 100)
                    && !(first == 203 && second == 0 && third == 113)
                    && first < 224;
        }
        if (address instanceof Inet6Address) {
            // 0000::/8 carries no legitimate global unicast host, yet its head
            // bits sail past the unique-local and documentation checks below.
            // The block contains the IPv4-mapped form ::ffff:a.b.c.d and the
            // deprecated IPv4-compatible form ::a.b.c.d, whose real destination
            // is the embedded IPv4 address — ::ffff:169.254.169.254 reaches a
            // cloud metadata endpoint, ::ffff:100.64.0.1 a CGNAT range. An
            // attacker-controlled DNS answer can serve such an AAAA record, so
            // reject the whole block instead of re-deriving the IPv4 rules
            // here. Accepted trade-off: NAT64/DNS64 deployments synthesizing
            // 64:ff9b::/96 AAAA records also fall in this block (64:ff9b:: is
            // global per IANA but first byte 0) and are refused fail-closed;
            // this server is not intended for IPv6-only upstreams.
            if (Byte.toUnsignedInt(bytes[0]) == 0) {
                return false;
            }
            int first = Byte.toUnsignedInt(bytes[0]);
            int second = Byte.toUnsignedInt(bytes[1]);
            boolean uniqueLocal = (first & 0xfe) == 0xfc;
            boolean documentation = first == 0x20
                    && second == 0x01
                    && Byte.toUnsignedInt(bytes[2]) == 0x0d
                    && Byte.toUnsignedInt(bytes[3]) == 0xb8;
            return !uniqueLocal && !documentation;
        }
        return false;
    }
}
