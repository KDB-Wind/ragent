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

package com.nageoffer.ai.ragent.rag.core.rewrite;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class QueryTermMappingUtilTest {

    @Test
    void replacesSourceWithoutDuplicatingExistingTarget() {
        assertEquals("平安保司理赔", QueryTermMappingUtil.applyMapping("平安保险理赔", "平安保险", "平安保司"));
        assertEquals("平安保司理赔", QueryTermMappingUtil.applyMapping("平安保司理赔", "平安", "平安保司"));
    }

    @Test
    void ignoresIncompleteMappingInsteadOfDereferencingNullTarget() {
        assertEquals("原始问题", QueryTermMappingUtil.applyMapping("原始问题", "原始", null));
        assertEquals("原始问题", QueryTermMappingUtil.applyMapping("原始问题", "原始", ""));
    }
}
