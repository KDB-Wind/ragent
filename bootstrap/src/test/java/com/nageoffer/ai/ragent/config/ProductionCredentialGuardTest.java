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

package com.nageoffer.ai.ragent.config;

import org.junit.jupiter.api.Test;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.mock.env.MockEnvironment;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ProductionCredentialGuardTest {

    private final ProductionCredentialGuard guard = new ProductionCredentialGuard();

    @Test
    void noProfileAllowsDevDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "postgres")
                .withProperty("spring.data.redis.password", "123456");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void devProfileAllowsDevDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.password", "postgres");
        environment.setActiveProfiles("dev");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void localProfileAllowsDevDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("rag.storage.s3.secret-key", "rustfsadmin");
        environment.setActiveProfiles("local");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithDevCredentialFails() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("spring.datasource.password", "postgres");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertFalse(ex.getMessage().contains("postgres"));
    }

    @Test
    void prodProfileWithEnvOverridePasses() {
        MockEnvironment environment = validProdEnvironment("s3");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void programmaticProdProfileIsGuarded() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("spring.datasource.password", "postgres");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void mixedProdAndDevProfilesAreGuarded() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("spring.datasource.password", "postgres");
        environment.setActiveProfiles("prod", "dev");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithUnresolvablePlaceholderFails() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertFalse(ex.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void prodProfileWithResolvablePlaceholderPasses() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("DB_PASSWORD", "S3cret-Override!")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithWeakPlaceholderDefaultFails() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("spring.datasource.password", "${DB_PASSWORD:postgres}");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertFalse(ex.getMessage().contains("postgres"));
    }

    @Test
    void prodProfileWithEmptyPlaceholderDefaultFails() {
        MockEnvironment environment = validProdEnvironment("s3")
                .withProperty("spring.datasource.password", "${DB_PASSWORD:}");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithMissingCredentialFails() {
        MockEnvironment environment = validProdEnvironment("s3");
        environment.setProperty("spring.data.redis.password", "");
        assertThrows(IllegalStateException.class, () -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void ossStorageRequiresOssCredentialsButNotS3Credentials() {
        MockEnvironment environment = validProdEnvironment("oss");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void ossStorageWithMissingSecretFails() {
        MockEnvironment environment = validProdEnvironment("oss")
                .withProperty("rag.storage.oss.secret-key", "");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("rag.storage.oss.secret-key"));
        assertFalse(ex.getMessage().contains("oss-secret"));
    }

    @Test
    void registeredGuardRejectsProdDefaultsDuringSpringStartup() {
        SpringApplication application = new SpringApplication(EmptyApplication.class);
        application.setWebApplicationType(WebApplicationType.NONE);
        application.setDefaultProperties(Map.of("spring.profiles.active", "prod", "spring.main.banner-mode", "off"));

        RuntimeException ex = assertThrows(RuntimeException.class, application::run);
        assertTrue(hasMessageInChain(ex, "配置键 ["));
    }

    private MockEnvironment validProdEnvironment(String storageType) {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.datasource.username", "ragent_prod")
                .withProperty("spring.datasource.password", "db-secret")
                .withProperty("spring.data.redis.password", "redis-secret")
                .withProperty("rag.storage.type", storageType);
        environment.setActiveProfiles("prod");
        if ("oss".equals(storageType)) {
            environment.withProperty("rag.storage.oss.access-key", "oss-access")
                    .withProperty("rag.storage.oss.secret-key", "oss-secret");
        } else {
            environment.withProperty("rag.storage.s3.access-key", "s3-access")
                    .withProperty("rag.storage.s3.secret-key", "s3-secret");
        }
        return environment;
    }

    private boolean hasMessageInChain(Throwable error, String expected) {
        for (Throwable current = error; current != null; current = current.getCause()) {
            if (current.getMessage() != null && current.getMessage().contains(expected)) {
                return true;
            }
        }
        return false;
    }

    private static final class EmptyApplication {
    }
}
