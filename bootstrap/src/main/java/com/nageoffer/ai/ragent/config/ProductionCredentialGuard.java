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

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;

import java.util.Arrays;
import java.util.Map;
import java.util.Set;

/**
 * 生产 profile 下的开发默认凭据 fail-fast 守卫。
 *
 * <p>application.yaml 携带一组本地开发默认凭据（postgres / 123456 / rustfsadmin 等）。未显式激活
 * profile 或激活集合含 local / dev / test 时完全放行，本地直接启动行为不变；其他 profile（如 prod）
 * 下检查敏感键的生效值：命中已知开发默认值、或占位符无默认值且无法解析时抛
 * {@link IllegalStateException} 中断启动，避免弱凭据静默上线。</p>
 *
 * <p>通过 {@code META-INF/spring.factories} 注册（EnvironmentPostProcessor 在 Spring Boot 3 只从
 * spring.factories 加载，不走 auto-configuration 的 .imports 机制），排到
 * {@link Ordered#LOWEST_PRECEDENCE} 保证晚于 ConfigDataEnvironmentPostProcessor，能读到
 * application.yaml 与 profile 解析结果。</p>
 *
 * <p>异常消息只含键名与提示，绝不包含配置值。</p>
 */
public class ProductionCredentialGuard implements EnvironmentPostProcessor, Ordered {

    private static final Set<String> DEV_PROFILES = Set.of("local", "dev", "test");

    private static final Map<String, Set<String>> DEV_DEFAULT_CREDENTIALS = Map.of(
            "spring.datasource.username", Set.of("postgres", "root"),
            "spring.datasource.password", Set.of("postgres", "123456", "root", "password"),
            "spring.data.redis.password", Set.of("123456", "password", "redis"),
            "rag.storage.s3.access-key", Set.of("rustfsadmin", "minioadmin"),
            "rag.storage.s3.secret-key", Set.of("rustfsadmin", "minioadmin"));

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (isDevLaunch(environment)) {
            return;
        }
        DEV_DEFAULT_CREDENTIALS.forEach((key, defaults) -> check(environment, key, defaults));
    }

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    private boolean isDevLaunch(ConfigurableEnvironment environment) {
        String active = environment.getProperty("spring.profiles.active");
        if (active == null || active.isBlank()) {
            return true;
        }
        return Arrays.stream(active.split(","))
                .map(String::trim)
                .filter(profile -> !profile.isEmpty())
                .anyMatch(DEV_PROFILES::contains);
    }

    private void check(ConfigurableEnvironment environment, String key, Set<String> devDefaults) {
        String value;
        try {
            value = environment.getProperty(key);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "配置键 [" + key + "] 的占位符未配置且无默认值，请通过环境变量或 secret 覆盖后重启");
        }
        if (value != null && devDefaults.contains(value)) {
            throw new IllegalStateException(
                    "配置键 [" + key + "] 检测到开发默认值，请通过环境变量或 secret 覆盖后重启");
        }
    }
}
