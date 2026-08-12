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

package com.nageoffer.ai.ragent.user.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHashServiceTest {

    private final PasswordHashService passwordHashService = new PasswordHashService();

    @Test
    void encodesAndVerifiesBcryptPasswords() {
        String encoded = passwordHashService.encode("correct horse battery staple");

        assertNotEquals("correct horse battery staple", encoded);
        assertTrue(encoded.startsWith("$2"));
        assertTrue(passwordHashService.matches("correct horse battery staple", encoded));
        assertFalse(passwordHashService.matches("wrong", encoded));
        assertFalse(passwordHashService.needsUpgrade(encoded));
    }

    @Test
    void acceptsLegacyPlaintextOnlyForOneTimeMigration() {
        assertTrue(passwordHashService.matches("legacy-password", "legacy-password"));
        assertFalse(passwordHashService.matches("wrong", "legacy-password"));
        assertTrue(passwordHashService.needsUpgrade("legacy-password"));
    }

    @Test
    void rejectsMissingCredentials() {
        assertFalse(passwordHashService.matches(null, "hash"));
        assertFalse(passwordHashService.matches("password", null));
    }
}
