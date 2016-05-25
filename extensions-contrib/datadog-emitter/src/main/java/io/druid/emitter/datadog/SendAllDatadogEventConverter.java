/*
 *  Licensed to Metamarkets Group Inc. (Metamarkets) under one
 *  or more contributor license agreements. See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership. Metamarkets licenses this file
 *  to you under the Apache License, Version 2.0 (the
 *  "License"); you may not use this file except in compliance
 *  with the License. You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied. See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.druid.emitter.datadog;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonTypeName;
import com.google.common.base.Joiner;
import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSortedSet;
import com.metamx.emitter.service.ServiceMetricEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Emits all the events instance of {@link com.metamx.emitter.service.ServiceMetricEvent}.
 * <p>
 * All the dimensions will be retained and lexicographically order using dimensions name.
 * <p>
 * The metric path of the graphite event is:
 * <namespacePrefix>.[<druid service name>].[<druid hostname>].<dimensions values ordered by dimension's name>.<metric>
 * <p>
 * Note that this path will be sanitized by replacing all the `.` or `space` to `_` {@link DatadogEmitter#sanitize(String)}
 */

public class SendAllDatadogEventConverter implements DruidToDatadogEventConverter
{

  private final DatadogEmitterConfig datadogEmitterConfig;

  public SendAllDatadogEventConverter(DatadogEmitterConfig datadogEmitterConfig)
  {
    this.datadogEmitterConfig = datadogEmitterConfig;
  }

  @Override
  public DatadogEvent druidEventToDatadog(ServiceMetricEvent serviceMetricEvent)
  {
    final Long[][] points = new Long[1][2];
    points[0][0] = TimeUnit.MILLISECONDS.toSeconds(serviceMetricEvent.getCreatedTime()
                                                                     .getMillis());
    points[0][1] = serviceMetricEvent.getValue().longValue();

    String metric = DatadogEmitter.sanitize(serviceMetricEvent.getMetric());
    String service = DatadogEmitter.sanitize(serviceMetricEvent.getService());


    Map<String, Object> userDims = serviceMetricEvent.getUserDims();
    List<String> tags = new ArrayList<>();
    for (Map.Entry<String, Object> values : userDims.entrySet()) {
      if(!values.getKey().contains("context") && !values.getKey().contains("id"))
        tags.add(String.format("%s:%s", values.getKey(), values.getValue()));
    }
    for (Map.Entry<String, String> values : this.datadogEmitterConfig.getTagsMap().entrySet()) {
      tags.add(String.format("%s:%s", values.getKey(), values.getValue()));
    }
    String host = serviceMetricEvent.getHost();
    int endIndex = host.lastIndexOf(":");
    if (endIndex != -1) {
      host = host.substring(0, endIndex);
    }
    tags.add(String.format("service:%s", service));
    if (this.datadogEmitterConfig.getNamespace() != null) {
      metric = this.datadogEmitterConfig.getNamespace() + "." + metric;
    }
    return new DatadogEvent(metric, points, "gauge", host, tags);
  }
}
