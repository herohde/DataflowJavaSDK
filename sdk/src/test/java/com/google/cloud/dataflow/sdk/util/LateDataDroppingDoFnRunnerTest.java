/*
 * Copyright (C) 2016 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.google.cloud.dataflow.sdk.util;

import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertThat;
import static org.mockito.Mockito.when;

import com.google.cloud.dataflow.sdk.transforms.Aggregator;
import com.google.cloud.dataflow.sdk.transforms.Combine.CombineFn;
import com.google.cloud.dataflow.sdk.transforms.Sum;
import com.google.cloud.dataflow.sdk.transforms.windowing.FixedWindows;
import com.google.cloud.dataflow.sdk.transforms.windowing.PaneInfo;
import com.google.cloud.dataflow.sdk.util.LateDataDroppingDoFnRunner.LateDataFilter;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Iterables;

import org.joda.time.Duration;
import org.joda.time.Instant;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Arrays;

/**
 * Unit tests for {@link LateDataDroppingDoFnRunner}.
 */
@RunWith(JUnit4.class)
public class LateDataDroppingDoFnRunnerTest {
  private static final FixedWindows WINDOW_FN = FixedWindows.of(Duration.millis(10));

  @Mock private TimerInternals mockTimerInternals;

  @Before
  public void setUp() {
    MockitoAnnotations.initMocks(this);
  }

  @Test
  public void testLateDataFilter() throws Exception {
    when(mockTimerInternals.currentInputWatermarkTime()).thenReturn(new Instant(15L));

    InMemoryLongSumAggregator droppedDueToLateness =
        new InMemoryLongSumAggregator("droppedDueToLateness");
    LateDataFilter lateDataFilter = new LateDataFilter(
        WindowingStrategy.of(WINDOW_FN), mockTimerInternals, droppedDueToLateness);

    Iterable<WindowedValue<Integer>> actual = lateDataFilter.filter(
        "a",
        ImmutableList.of(
            createDatum(13, 13L),
            createDatum(5, 5L), // late element, earlier than 4L.
            createDatum(16, 16L),
            createDatum(18, 18L)));

    Iterable<WindowedValue<Integer>> expected =  ImmutableList.of(
        createDatum(13, 13L),
        createDatum(16, 16L),
        createDatum(18, 18L));
    assertThat(expected, containsInAnyOrder(Iterables.toArray(actual, WindowedValue.class)));
    assertEquals(1, droppedDueToLateness.sum);
  }

  private <T> WindowedValue<T> createDatum(T element, long timestampMillis) {
    Instant timestamp = new Instant(timestampMillis);
    return WindowedValue.of(
        element,
        timestamp,
        Arrays.asList(WINDOW_FN.assignWindow(timestamp)),
        PaneInfo.NO_FIRING);
  }

  private static class InMemoryLongSumAggregator implements Aggregator<Long, Long> {
    private final String name;
    private long sum = 0;

    public InMemoryLongSumAggregator(String name) {
      this.name = name;
    }

    @Override
    public void addValue(Long value) {
      sum += value;
    }

    @Override
    public String getName() {
      return name;
    }

    @Override
    public CombineFn<Long, ?, Long> getCombineFn() {
      return new Sum.SumLongFn();
    }
  }
}

