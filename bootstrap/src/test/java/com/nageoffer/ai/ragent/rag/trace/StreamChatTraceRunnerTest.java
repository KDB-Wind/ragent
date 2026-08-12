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

package com.nageoffer.ai.ragent.rag.trace;

import com.nageoffer.ai.ragent.framework.trace.RagTraceContext;
import com.nageoffer.ai.ragent.infra.chat.StreamCallback;
import com.nageoffer.ai.ragent.rag.config.RagTraceProperties;
import com.nageoffer.ai.ragent.rag.dao.entity.RagTraceRunDO;
import com.nageoffer.ai.ragent.rag.service.RagTraceRecordService;
import com.nageoffer.ai.ragent.rag.service.handler.StreamTaskManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Date;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StreamChatTraceRunnerTest {

    @Mock
    private RagTraceProperties traceProperties;

    @Mock
    private RagTraceRecordService traceRecordService;

    @Mock
    private StreamTaskManager taskManager;

    @Mock
    private StreamCallback callback;

    @Captor
    private ArgumentCaptor<RagTraceRunDO> runCaptor;

    @Captor
    private ArgumentCaptor<Runnable> cancellationObserverCaptor;

    @InjectMocks
    private StreamChatTraceRunner traceRunner;

    @AfterEach
    void clearTraceContext() {
        RagTraceContext.clear();
    }

    @Test
    void shouldExposeSameTraceIdAndFinishCancelledRunExactlyOnce() {
        when(traceProperties.isEnabled()).thenReturn(true);
        AtomicReference<StreamCallback> traceAware = new AtomicReference<>();

        traceRunner.run("question", "conversation-1", "task-1", callback, traceAware::set);

        verify(traceRecordService).startRun(runCaptor.capture());
        String traceId = runCaptor.getValue().getTraceId();
        verify(taskManager).bindCancellationObserver(eq("task-1"), cancellationObserverCaptor.capture());
        verify(callback).onTraceStarted(traceId);
        assertEquals("task-1", runCaptor.getValue().getTaskId());
        assertNull(RagTraceContext.getTraceId());
        assertNull(RagTraceContext.getTaskId());

        cancellationObserverCaptor.getValue().run();
        traceAware.get().onComplete();

        verify(traceRecordService, times(1))
                .finishRun(eq(traceId), eq("CANCELLED"), isNull(), any(Date.class), anyLong());
        verify(callback).onComplete();
    }

    @Test
    void shouldPropagateTraceContextDuringBusinessLogicAndClearAfterwards() {
        when(traceProperties.isEnabled()).thenReturn(true);
        AtomicReference<String> observedTraceId = new AtomicReference<>();
        AtomicReference<String> observedTaskId = new AtomicReference<>();
        AtomicReference<StreamCallback> observedCallback = new AtomicReference<>();

        traceRunner.run("question", "conversation-1", "task-1", callback, enhanced -> {
            observedTraceId.set(RagTraceContext.getTraceId());
            observedTaskId.set(RagTraceContext.getTaskId());
            observedCallback.set(enhanced);
        });

        verify(traceRecordService).startRun(runCaptor.capture());
        assertEquals(runCaptor.getValue().getTraceId(), observedTraceId.get());
        assertEquals("task-1", observedTaskId.get());
        observedCallback.get().onComplete();
        verify(callback).onComplete();
        assertNull(RagTraceContext.getTraceId());
        assertNull(RagTraceContext.getTaskId());
        verify(taskManager).bindCancellationObserver(eq("task-1"), any(Runnable.class));
    }
}
