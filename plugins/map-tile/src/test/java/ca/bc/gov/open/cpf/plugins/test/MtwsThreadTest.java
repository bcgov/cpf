/*
 * Copyright © 2008-2016, Province of British Columbia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.bc.gov.open.cpf.plugins.test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationPluginExecutor;

import com.revolsys.collection.map.MapEx;
import com.revolsys.io.map.MapReader;
import com.revolsys.parallel.channel.Channel;
import com.revolsys.parallel.channel.store.Buffer;
import com.revolsys.spring.resource.PathResource;
import com.revolsys.spring.resource.Resource;

public class MtwsThreadTest {
  private static final String BUSINESS_APPLICATION_NAME = "MapTileByTileId";

  private static final int ITERATION_COUNT = 10;

  private static final int NUM_THREADS = 10;

  private static final Resource inputDataResource = new PathResource(
    "src/data/NTS-250000-by-name.csv");

  public static void main(final String[] args) {
    final MtwsThreadTest test = new MtwsThreadTest();
    test.run();
  }

  private BusinessApplicationPluginExecutor executor = new BusinessApplicationPluginExecutor();

  private final Channel<Boolean> startChannel = new Channel<>(new Buffer<Boolean>(NUM_THREADS));

  private final Object startSync = new Object();

  private final Channel<Boolean> stopChannel = new Channel<>(new Buffer<Boolean>(NUM_THREADS));

  private List<MapEx> testData = new ArrayList<>();

  public MtwsThreadTest() {
    this.executor.setTestModeEnabled("MapTileByTileId", Boolean.TRUE);
  }

  public String getBusinessApplicationName() {
    return BUSINESS_APPLICATION_NAME;
  }

  public BusinessApplicationPluginExecutor getExecutor() {
    return this.executor;
  }

  public int getIterationCount() {
    return ITERATION_COUNT;
  }

  public Map<String, Object> getTestData(final int index) {
    final int i = index % this.testData.size();
    return this.testData.get(i);
  }

  public void run() {
    this.testData = MapReader.newMapReader(inputDataResource).toList();
    for (int i = 0; i < NUM_THREADS; i++) {
      final MtwsThreadTestRunnable runnable = new MtwsThreadTestRunnable(this, i);
      final Thread thread = new Thread(runnable, "Runner " + i);
      thread.start();
    }

    waitForAllThreadsToStart();
    waitForAllThreadsToStop();
    this.executor.close();
    this.executor = null;
    this.testData.clear();
    System.gc();
    System.gc();
    System.gc();
    System.gc();
    System.gc();
    synchronized (this) {
      try {
        wait();
      } catch (final InterruptedException e) {
      }
    }
  }

  public void threadStarted() {
    this.startChannel.write(true);
    synchronized (this.startSync) {
      try {
        this.startSync.wait();
      } catch (final InterruptedException e) {
      }
    }
  }

  public void threadStopped() {
    this.stopChannel.write(true);
  }

  protected void waitForAllThreadsToStart() {
    for (int i = 0; i < NUM_THREADS; i++) {
      this.startChannel.read();
    }
    synchronized (this.startSync) {
      this.startSync.notifyAll();
    }
  }

  protected void waitForAllThreadsToStop() {
    for (int i = 0; i < NUM_THREADS; i++) {
      this.stopChannel.read();
    }
  }
}
