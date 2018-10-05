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
import com.google.common.base.Preconditions;

import java.util.HashMap;
import java.util.Map;


public class DatadogEmitterConfig {
  private final static int DEFAULT_BATCH_SIZE = 100;
  private static final Long DEFAULT_FLUSH_PERIOD = (long) (60 * 1000); // flush every one minute

  @JsonProperty
  final private String apiUrl;
  @JsonProperty
  final private String apiKey;
  @JsonProperty
  final private int batchSize;
  @JsonProperty
  final private Long flushPeriod;
  @JsonProperty
  final private Integer maxQueueSize;
  @JsonProperty
  final private String tags;
  @JsonProperty
  final private String namespace;

  @JsonCreator
  public DatadogEmitterConfig(
          @JsonProperty("batchSize") Integer batchSize,
          @JsonProperty("flushPeriod") Long flushPeriod,
          @JsonProperty("maxQueueSize") Integer maxQueueSize,
          @JsonProperty("apiKey") String apiKey,
          @JsonProperty("namespace") String namespace,
          @JsonProperty("tags") String tags,
          @JsonProperty("apiUrl") String apiUrl
  ) {
    this.flushPeriod = flushPeriod == null ? DEFAULT_FLUSH_PERIOD : flushPeriod;
    this.namespace = namespace;
    this.maxQueueSize = maxQueueSize == null ? Integer.MAX_VALUE : maxQueueSize;
    this.apiKey = Preconditions.checkNotNull(apiKey, "apiKey can not be null");
    this.batchSize = (batchSize == null) ? DEFAULT_BATCH_SIZE : batchSize;
    this.tags = tags;
    this.apiUrl = apiUrl == null ? "https://app.datadoghq.com/api" : apiUrl;
  }

  @JsonProperty
  public int getBatchSize() {
    return batchSize;
  }

  @JsonProperty
  public String getTags() {
    return tags;
  }

  public Map<String, String> getTagsMap() {
    HashMap<String, String> map = new HashMap<>();
    String tags = this.getTags();
    if (tags == null) {
      return map;
    }
    String[] split = tags.split(",");
    for (String keyValue : split) {
      String[] keyValueSplitted = keyValue.split(":");
      if (keyValueSplitted.length == 2) {
        map.put(keyValueSplitted[0], keyValueSplitted[1]);
      }
    }
    return map;
  }

  @JsonProperty
  public Integer getMaxQueueSize() {
    return maxQueueSize;
  }

  @JsonProperty
  public Long getFlushPeriod() {
    return flushPeriod;
  }

  @JsonProperty
  public String getApiKey() {
    return apiKey;
  }

  @JsonProperty
  public String getApiUrl() {
    return apiUrl;
  }

  @JsonProperty
  public String getNamespace() {
    return namespace;
  }
}
