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

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.io.Closeables;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.metamx.common.ISE;
import com.metamx.common.logger.Logger;
import com.metamx.emitter.core.Emitter;
import com.metamx.emitter.core.Event;
import com.metamx.emitter.service.AlertEvent;
import com.metamx.emitter.service.ServiceMetricEvent;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.ContentType;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClientBuilder;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Pattern;


public class DatadogEmitter implements Emitter
{
  private static Logger log = new Logger(DatadogEmitter.class);

  private final DruidToDatadogEventConverter datadogEventConverter;
  private final DatadogEmitterConfig datadogEmitterConfig;
  private final List<Emitter> emitterList;
  private ObjectMapper mapper = new ObjectMapper();
  private final AtomicBoolean started = new AtomicBoolean(false);
  private final LinkedBlockingQueue<DatadogEvent> eventsQueue;
  private static final long FLUSH_TIMEOUT = 60000; // default flush wait 1 min
  private final ScheduledExecutorService exec = Executors.newScheduledThreadPool(2, new ThreadFactoryBuilder()
      .setDaemon(true)
      .setNameFormat("DatadogEmitter-%s")
      .build()); // Thread pool of two in order to schedule flush runnable
  private AtomicLong countLostEvents = new AtomicLong(0);

  public DatadogEmitter(
      DatadogEmitterConfig datadogEmitterConfig,
      List<Emitter> emitterList
  )
  {
    this.emitterList = emitterList;
    this.datadogEmitterConfig = datadogEmitterConfig;
    this.datadogEventConverter = new SendAllDatadogEventConverter(datadogEmitterConfig);
    this.eventsQueue = new LinkedBlockingQueue<>(datadogEmitterConfig.getMaxQueueSize());
  }

  @Override
  public void start()
  {
    log.info("Starting Datadog Emitter.");
    synchronized (started) {
      if (!started.get()) {
        exec.scheduleAtFixedRate(
            new ConsumerRunnable(),
            datadogEmitterConfig.getFlushPeriod(),
            datadogEmitterConfig.getFlushPeriod(),
            TimeUnit.MILLISECONDS
        );
        started.set(true);
      }
    }
  }


  @Override
  public void emit(Event event)
  {
    if (!started.get()) {
      throw new ISE("WTF emit was called while service is not started yet");
    }
    if (event instanceof ServiceMetricEvent) {
      final DatadogEvent datadogEvent = datadogEventConverter.druidEventToDatadog((ServiceMetricEvent) event);
      if (datadogEvent == null) {
        return;
      }
      final boolean isSuccessful = eventsQueue.offer(
          datadogEvent
      );
      if (!isSuccessful) {
        if (countLostEvents.getAndIncrement() % 1000 == 0) {
          log.error(
              "Lost total of [%s] events because of emitter queue is full. Please increase the capacity or/and the consumer frequency",
              countLostEvents.get()
          );
        }
      }
    } else if (!emitterList.isEmpty() && event instanceof AlertEvent) {
      for (Emitter emitter : emitterList) {
        emitter.emit(event);
      }
    }
    //Just ingore for now
  }

  private class ConsumerRunnable implements Runnable
  {

    @Override
    public void run()
    {
      //Send events to API
      DatadogContainer datadogContainer = new DatadogContainer();
      while (eventsQueue.size() > 0 && !exec.isShutdown()) {
        final DatadogEvent datadogEvent = eventsQueue.poll();
        if (datadogEvent != null) {
          datadogContainer.add(datadogEvent);
        }
      }
      if (datadogContainer.getSeries().size() > 0) {
        try {
          CloseableHttpClient client = HttpClientBuilder.create().build();
          StringBuilder url = new StringBuilder();
          url.append(datadogEmitterConfig.getApiUrl())
             .append("/v1/series?api_key=")
             .append(datadogEmitterConfig.getApiKey());
          String json = mapper.writeValueAsString(datadogContainer);
          HttpPost post = new HttpPost(url.toString());
          log.info("Flusing to datadog %s", json);
          post.setEntity(new StringEntity(json, ContentType.APPLICATION_JSON));
          HttpResponse response = client.execute(post);
          BufferedReader rd = new BufferedReader(new InputStreamReader(response.getEntity().getContent()));
          String responseText = "";
          String line;
          while ((line = rd.readLine()) != null) {
            responseText += line;
          }
          log.info("Response %s", responseText);
          post.reset();
          Closeables.close(client, true);
        }
        catch (IOException e) {
          e.printStackTrace();
        }
      }
    }
  }

  private class DatadogContainer
  {
    private final List<DatadogEvent> series = new ArrayList<>();

    void add(DatadogEvent event)
    {
      series.add(event);
    }

    public List<DatadogEvent> getSeries()
    {
      return series;
    }

  }

  @Override
  public void flush() throws IOException
  {
    if (started.get()) {
      Future future = exec.schedule(new ConsumerRunnable(), 0, TimeUnit.MILLISECONDS);
      try {
        future.get(FLUSH_TIMEOUT, TimeUnit.MILLISECONDS);
      }
      catch (InterruptedException | ExecutionException | TimeoutException e) {
        if (e instanceof InterruptedException) {
          throw new RuntimeException("interrupted flushing elements from queue", e);
        }
        log.error(e, e.getMessage());
      }
    }

  }

  @Override
  public void close() throws IOException
  {
    flush();
    started.set(false);
    exec.shutdown();
  }

  protected static String sanitize(String namespace)
  {
    Pattern WHITE_SPACE = Pattern.compile("[\\s]+");
    Pattern SLASHES = Pattern.compile("[/]+");
    String s = WHITE_SPACE.matcher(namespace).replaceAll("_");
    return SLASHES.matcher(s).replaceAll(".");
  }
}
