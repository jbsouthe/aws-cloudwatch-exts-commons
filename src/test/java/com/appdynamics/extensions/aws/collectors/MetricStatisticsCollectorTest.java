/*
 * Copyright 2018. AppDynamics LLC and its affiliates.
 * All Rights Reserved.
 * This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 * The copyright notice above does not evidence any actual or intended publication of such source code.
 */

package com.appdynamics.extensions.aws.collectors;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.mockito.Matchers.any;
import static org.mockito.Mockito.when;

import com.appdynamics.extensions.aws.config.IncludeMetric;
import com.appdynamics.extensions.aws.config.MetricsTimeRange;
import com.appdynamics.extensions.aws.dto.AWSMetric;
import com.appdynamics.extensions.aws.exceptions.AwsException;
import com.appdynamics.extensions.aws.metric.MetricStatistic;
import com.appdynamics.extensions.aws.metric.StatisticType;
import com.google.common.collect.Lists;
import org.joda.time.DateTime;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.runners.MockitoJUnitRunner;
import software.amazon.awssdk.core.exception.SdkServiceException;
import software.amazon.awssdk.services.cloudwatch.CloudWatchClient;
import software.amazon.awssdk.services.cloudwatch.model.Datapoint;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsRequest;
import software.amazon.awssdk.services.cloudwatch.model.GetMetricStatisticsResponse;
import software.amazon.awssdk.services.cloudwatch.model.Metric;
import software.amazon.awssdk.services.cloudwatch.model.StandardUnit;

import java.lang.reflect.Field;
import java.util.Date;
import java.util.List;
import java.util.Random;
import java.util.concurrent.atomic.LongAdder;

@RunWith(MockitoJUnitRunner.class)
public class MetricStatisticsCollectorTest {

    private MetricStatisticCollector classUnderTest;

    @Mock
    private AWSMetric mockAWSMetric;

    @Mock
    private IncludeMetric mockIncludeMetric;

    @Mock
    private Metric mockMetric;

    @Mock
    private CloudWatchClient mockAwsCloudWatch;

    @Mock
    private GetMetricStatisticsResponse mockGetMetricStatsResult;

    @Mock
    private MetricsTimeRange mockMetricsTimeRange;

    private LongAdder requestCounter = new LongAdder();

    @Before
    public void setup() {
        when(mockAWSMetric.getIncludeMetric()).thenReturn(mockIncludeMetric);
        when(mockAWSMetric.getMetric()).thenReturn(mockMetric);
    }

    @Test(expected = AwsException.class)
    public void testInvalidTimeRangeThrowsException() throws Exception {
        MetricsTimeRange invalidTimeRange = new MetricsTimeRange();
        invalidTimeRange.setEndTimeInMinsBeforeNow(10);
        invalidTimeRange.setStartTimeInMinsBeforeNow(5);

        classUnderTest = new MetricStatisticCollector.Builder()
                .withMetricsTimeRange(invalidTimeRange)
                .withMetric(mockAWSMetric)
                .withAWSRequestCounter(requestCounter)
                .build();

        classUnderTest.call();
    }

    @Test(expected = AwsException.class)
    public void testAwsCloudwatchThrowsException() throws Exception {
        when(mockAwsCloudWatch.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenThrow(new Exception("test exception"));

        classUnderTest = new MetricStatisticCollector.Builder()
                .withMetricsTimeRange(new MetricsTimeRange())
                .withMetric(mockAWSMetric)
                .withAwsCloudWatch(mockAwsCloudWatch)
                .withAWSRequestCounter(requestCounter)
                .build();

        classUnderTest.call();
    }

    @Test
    public void testLatestDatapointIsUsed() throws Exception {
        Datapoint latestDatapoint = createTestDatapoint(
                DateTime.now().toDate());

        Datapoint fiveMinsAgoDatapoint = createTestDatapoint(
                DateTime.now().minusMinutes(5).toDate());

        Datapoint tenMinsAgoDatapoint = createTestDatapoint(
                DateTime.now().minusMinutes(10).toDate());

        List<Datapoint> testDatapoints = Lists.newArrayList(latestDatapoint,
                fiveMinsAgoDatapoint, tenMinsAgoDatapoint);

        when(mockAwsCloudWatch.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(mockGetMetricStatsResult);

        when(mockGetMetricStatsResult.datapoints()).thenReturn(testDatapoints);

        classUnderTest = new MetricStatisticCollector.Builder()
                .withMetricsTimeRange(new MetricsTimeRange())
                .withMetric(mockAWSMetric)
                .withAwsCloudWatch(mockAwsCloudWatch)
                .withStatType(StatisticType.SUM)
                .withAWSRequestCounter(requestCounter)
                .build();

        MetricStatistic result = classUnderTest.call();

        assertEquals(latestDatapoint.sum(), result.getValue());
        assertEquals(latestDatapoint.unit(), result.getUnit());
    }

    @Test
    public void testNullDatapoint() throws Exception {
        List<Datapoint> testDatapoints = Lists.newArrayList(null, null);

        when(mockAwsCloudWatch.getMetricStatistics(any(GetMetricStatisticsRequest.class)))
                .thenReturn(mockGetMetricStatsResult);

        when(mockGetMetricStatsResult.datapoints()).thenReturn(testDatapoints);

        classUnderTest = new MetricStatisticCollector.Builder()
                .withMetricsTimeRange(new MetricsTimeRange())
                .withMetric(mockAWSMetric)
                .withAwsCloudWatch(mockAwsCloudWatch)
                .withStatType(StatisticType.SUM)
                .withAWSRequestCounter(requestCounter)
                .build();

        MetricStatistic result = classUnderTest.call();

        assertNull(result.getValue());
        assertNull(result.getUnit());
    }

    @Test
    public void testIndividualMetricTimeRanges() throws NoSuchFieldException, IllegalAccessException {
        MetricsTimeRange timeRange = new MetricsTimeRange();
        timeRange.setEndTimeInMinsBeforeNow(0);
        timeRange.setStartTimeInMinsBeforeNow(5);

        int startTime = 15;
        int endTime = 10;
        when(mockAWSMetric.getIncludeMetric()).thenReturn(mockIncludeMetric);
        when(mockIncludeMetric.getMetricsTimeRange()).thenReturn(mockMetricsTimeRange);
        when(mockMetricsTimeRange.getStartTimeInMinsBeforeNow()).thenReturn(startTime);
        when(mockMetricsTimeRange.getEndTimeInMinsBeforeNow()).thenReturn(endTime);


        classUnderTest = new MetricStatisticCollector.Builder()
                .withMetricsTimeRange(timeRange)
                .withMetric(mockAWSMetric)
                .withAWSRequestCounter(requestCounter)
                .build();


        Field startTimeInMinsBeforeNow = getField(classUnderTest.getClass(), "startTimeInMinsBeforeNow");
        int startTimeInMinsBeforeNowValue = (Integer) startTimeInMinsBeforeNow.get(classUnderTest);
        assertEquals(startTime, startTimeInMinsBeforeNowValue);


        Field endTimeInMinsBeforeNow = getField(classUnderTest.getClass(), "endTimeInMinsBeforeNow");
        int endTimeInMinsBeforeNowValue = (Integer) endTimeInMinsBeforeNow.get(classUnderTest);
        assertEquals(endTime, endTimeInMinsBeforeNowValue);
    }

    private Field getField(Class<?> thisClass, String name) throws NoSuchFieldException {
        Field field = thisClass.getDeclaredField(name);
        field.setAccessible(true);

        return field;
    }

    private Datapoint createTestDatapoint(Date timestamp) {
        Random random = new Random();

        Datapoint datapoint = Datapoint.builder()
            .average(random.nextDouble())
            .maximum(random.nextDouble())
            .minimum(random.nextDouble())
            .sampleCount(random.nextDouble())
            .sum(random.nextDouble())
            .timestamp(timestamp.toInstant())
            .unit(StandardUnit.BITS)
            .build();

        return datapoint;
    }

}
