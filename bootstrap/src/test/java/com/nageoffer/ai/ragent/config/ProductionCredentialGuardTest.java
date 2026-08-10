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
import org.springframework.mock.env.MockEnvironment;

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
                .withProperty("spring.profiles.active", "dev")
                .withProperty("spring.datasource.password", "postgres");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void localProfileAllowsDevDefaults() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "local")
                .withProperty("rag.storage.s3.secret-key", "rustfsadmin");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithDevCredentialFails() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.password", "postgres");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertFalse(ex.getMessage().contains("postgres"));
    }

    @Test
    void prodProfileWithEnvOverridePasses() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.password", "S3cret-Override!");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithUnresolvablePlaceholderFails() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertFalse(ex.getMessage().contains("DB_PASSWORD"));
    }

    @Test
    void prodProfileWithResolvablePlaceholderPasses() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("DB_PASSWORD", "S3cret-Override!")
                .withProperty("spring.datasource.password", "${DB_PASSWORD}");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }

    @Test
    void prodProfileWithWeakPlaceholderDefaultFails() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.password", "${DB_PASSWORD:postgres}");
        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> guard.postProcessEnvironment(environment, null));
        assertTrue(ex.getMessage().contains("spring.datasource.password"));
        assertFalse(ex.getMessage().contains("postgres"));
    }

    @Test
    void prodProfileWithEmptyPlaceholderDefaultPasses() {
        MockEnvironment environment = new MockEnvironment()
                .withProperty("spring.profiles.active", "prod")
                .withProperty("spring.datasource.password", "${DB_PASSWORD:}");
        assertDoesNotThrow(() -> guard.postProcessEnvironment(environment, null));
    }
}
