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

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.io.map.MapReader;
import com.revolsys.parallel.channel.Channel;
import com.revolsys.parallel.channel.store.Buffer;
import com.revolsys.spring.resource.PathResource;
import com.revolsys.spring.resource.Resource;

public class ThreadTest {
  public static void main(final String[] args) {
    final ThreadTest test = new ThreadTest();
    test.run();
  }

  private String businessApplicationName = "TestAllDataTypes";

  private final int iterationCount = 1;

  private final int numThreads = 10;

  private final Resource inputDataResource = new PathResource("src/data/NTS-250000-by-name.csv");

  private BusinessApplicationPluginExecutor executor = new BusinessApplicationPluginExecutor();

  private final Channel<Boolean> startChannel = new Channel<>(new Buffer<Boolean>(this.numThreads));

  private final Object startSync = new Object();

  private final Channel<Boolean> stopChannel = new Channel<>(new Buffer<Boolean>(this.numThreads));

  private List<MapEx> testData = new ArrayList<>();

  public ThreadTest() {
    this.executor.setTestModeEnabled(this.businessApplicationName, Boolean.TRUE);
  }

  public String getBusinessApplicationName() {
    return this.businessApplicationName;
  }

  public BusinessApplicationPluginExecutor getExecutor() {
    return this.executor;
  }

  public int getIterationCount() {
    return this.iterationCount;
  }

  public Map<String, Object> getTestData(final int index) {
    final int i = index % this.testData.size();
    return this.testData.get(i);
  }

  public void run() {
    if (this.inputDataResource.exists()) {
      this.testData = MapReader.newMapReader(this.inputDataResource).toList();
    } else {
      this.testData.add(new LinkedHashMapEx());
    }
    for (int i = 0; i < this.numThreads; i++) {
      final ThreadTestRunnable runnable = new ThreadTestRunnable(this, i);
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

  public void setBusinessApplicationName(final String businessApplicationName) {
    this.businessApplicationName = businessApplicationName;
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
    for (int i = 0; i < this.numThreads; i++) {
      this.startChannel.read();
    }
    synchronized (this.startSync) {
      this.startSync.notifyAll();
    }
  }

  protected void waitForAllThreadsToStop() {
    for (int i = 0; i < this.numThreads; i++) {
      this.stopChannel.read();
    }
  }
}
