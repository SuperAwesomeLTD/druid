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


import javax.validation.constraints.NotNull;
import java.util.List;


public class DatadogEvent {
  private String metric;
  private Long[][] points;
  private String type;
  private String host;
  private List<String> tags;

  /**
   * A graphite event must be in the following format: <metric path> <metric value> <metric timestamp>
   * ex:  PRODUCTION.host.graphite-tutorial.responseTime.p95 0.10 1400509112
   */
  DatadogEvent(
          @NotNull String metric,
          @NotNull Long[][] points,
          @NotNull String type,
          @NotNull String host,
          @NotNull List<String> tags
  ) {

    this.metric = metric;
    this.points = points;
    this.type = type;
    this.host = host;
    this.tags = tags;
  }

  public String getMetric() {
    return metric;
  }

  public Long[][] getPoints() {
    return points;
  }

  public String getType() {
    return type;
  }

  public String getHost() {
    return host;
  }

  public List<String> getTags() {
    return tags;
  }
}
