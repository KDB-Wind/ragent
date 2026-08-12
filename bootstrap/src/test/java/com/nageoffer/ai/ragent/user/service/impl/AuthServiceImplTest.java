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

package com.nageoffer.ai.ragent.user.service.impl;

import com.nageoffer.ai.ragent.framework.exception.ClientException;
import com.nageoffer.ai.ragent.user.controller.request.LoginRequest;
import com.nageoffer.ai.ragent.user.controller.vo.LoginVO;
import com.nageoffer.ai.ragent.user.dao.entity.UserDO;
import com.nageoffer.ai.ragent.user.dao.mapper.UserMapper;
import com.nageoffer.ai.ragent.user.service.PasswordHashService;
import com.nageoffer.ai.ragent.user.service.UserSessionService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthServiceImplTest {

    private final PasswordHashService passwordHashService = spy(new PasswordHashService());
    private final UserMapper userMapper = mock(UserMapper.class);
    private final UserSessionService userSessionService = mock(UserSessionService.class);
    private final AuthServiceImpl authService = new AuthServiceImpl(userMapper, passwordHashService, userSessionService);

    @Test
    void successfulLegacyLoginMigratesPasswordBeforeCreatingSession() {
        UserDO user = user("legacy-password");
        when(userMapper.selectOne(any())).thenReturn(user);
        when(userMapper.update(any(), any())).thenReturn(1);
        when(userSessionService.create("42")).thenReturn("token");

        LoginVO result = authService.login(request("legacy-password"));

        assertEquals("42", result.getUserId());
        assertEquals("legacy-password", user.getPassword());
        verify(userMapper).update(any(), any());
        verify(userSessionService).create("42");
    }

    @Test
    void invalidPasswordCannotCreateSessionOrMigrateCredential() {
        UserDO user = user(passwordHashService.encode("correct-password"));
        when(userMapper.selectOne(any())).thenReturn(user);

        assertThrows(ClientException.class, () -> authService.login(request("wrong-password")));

        verify(userMapper, never()).update(any(), any());
        verify(userSessionService, never()).create(any());
    }

    @Test
    void concurrentCredentialChangeAbortsLegacyLogin() {
        when(userMapper.selectOne(any())).thenReturn(user("legacy-password"));
        when(userMapper.update(any(), any())).thenReturn(0);

        assertThrows(ClientException.class, () -> authService.login(request("legacy-password")));

        verify(userSessionService, never()).create(any());
    }

    @Test
    void missingUserStillPerformsPasswordVerification() {
        when(userMapper.selectOne(any())).thenReturn(null);

        assertThrows(ClientException.class, () -> authService.login(request("wrong-password")));

        verify(passwordHashService).matches(anyString(), anyString());
        verify(userSessionService, never()).create(any());
    }

    private LoginRequest request(String password) {
        LoginRequest request = new LoginRequest();
        request.setUsername("alice");
        request.setPassword(password);
        return request;
    }

    private UserDO user(String password) {
        return UserDO.builder()
                .id("42")
                .username("alice")
                .password(password)
                .role("user")
                .deleted(0)
                .build();
    }
}
