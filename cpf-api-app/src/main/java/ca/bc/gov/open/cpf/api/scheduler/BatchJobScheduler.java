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
package ca.bc.gov.open.cpf.api.scheduler;

import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.util.Arrays;
import java.util.Collection;
import java.util.ConcurrentModificationException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.logging.Logs;

import ca.bc.gov.open.cpf.api.controller.CpfConfig;
import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;

import com.revolsys.collection.SetQueue;
import com.revolsys.collection.map.Maps;
import com.revolsys.parallel.NamedThreadFactory;
import com.revolsys.parallel.ThreadUtil;
import com.revolsys.parallel.channel.Channel;
import com.revolsys.parallel.channel.ClosedException;
import com.revolsys.parallel.channel.MultiInputSelector;
import com.revolsys.parallel.channel.store.Buffer;
import com.revolsys.parallel.channel.store.Overwrite;
import com.revolsys.parallel.process.Process;
import com.revolsys.parallel.process.ProcessNetwork;
import com.revolsys.util.Property;

public class BatchJobScheduler extends ThreadPoolExecutor
  implements Process, PropertyChangeListener {
  /** The batch job service used to interact with the database. */
  private BatchJobService batchJobService;

  private final long timeout = 60000;

  private final Channel<Object> awakeChannel = new Channel<>(new Overwrite<>());

  private int taskCount = 0;

  private final Object monitor = new Object();

  private long errorTime;

  private final Map<String, Set<BatchJobRequestExecutionGroup>> scheduledGroupsByBusinessApplication = new HashMap<>();

  private final Channel<BatchJob> in = new Channel<>(new Buffer<>(new SetQueue<BatchJob>()));

  private ProcessNetwork processNetwork;

  private String beanName;

  @Resource(name = "cpfConfig")
  private CpfConfig config;

  private final Map<Identifier, BatchJob> queuedJobById = new LinkedHashMap<>();

  public BatchJobScheduler() {
    super(0, 1, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(), new NamedThreadFactory());
  }

  @Override
  protected void afterExecute(final Runnable r, final Throwable t) {
    synchronized (this.monitor) {
      this.taskCount--;
      this.monitor.notifyAll();
    }
  }

  public void clearBusinessApplication(final String businessApplicationName) {
    while (true) {
      try {
        for (final Iterator<BatchJob> iterator = this.queuedJobById.values().iterator(); iterator
          .hasNext();) {
          final BatchJob batchJob = iterator.next();
          final Object appName = batchJob.getValue(BatchJob.BUSINESS_APPLICATION_NAME);
          if (businessApplicationName.equals(appName)) {
            iterator.remove();
          }
        }
        synchronized (this.scheduledGroupsByBusinessApplication) {
          this.scheduledGroupsByBusinessApplication.remove(businessApplicationName);
        }
        return;
      } catch (final ConcurrentModificationException e) {
      }
    }
  }

  public void destroy() {
    shutdown();
    try {
      awaitTermination(30, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
    }
  }

  @Override
  public void execute(final Runnable command) {
    if (command != null) {
      synchronized (this.monitor) {
        while (!isShutdown()) {
          while (this.taskCount >= getMaximumPoolSize()) {
            ThreadUtil.pause(this.monitor);
          }
          try {
            super.execute(command);
            this.taskCount++;
            return;
          } catch (final RejectedExecutionException e) {
          } catch (final RuntimeException e) {
            throw e;
          } catch (final Error e) {
            throw e;
          }
        }
      }
    }
  }

  /**
   * Get the batch job service used to interact with the database.
   *
   * @return The batch job service used to interact with the database.
   */
  public BatchJobService getBatchJobService() {
    return this.batchJobService;
  }

  @Override
  public String getBeanName() {
    return this.beanName;
  }

  public CpfConfig getConfig() {
    return this.config;
  }

  public Channel<BatchJob> getIn() {
    return this.in;
  }

  public Object getMonitor() {
    return this.monitor;
  }

  @Override
  public ProcessNetwork getProcessNetwork() {
    return this.processNetwork;
  }

  private int getScheduledGroupCount(final String businessApplicationName) {
    synchronized (this.scheduledGroupsByBusinessApplication) {
      final Collection<BatchJobRequestExecutionGroup> values = this.scheduledGroupsByBusinessApplication
        .get(businessApplicationName);
      if (values == null) {
        return 0;
      } else {
        int count = 0;
        for (final Iterator<BatchJobRequestExecutionGroup> iterator = values.iterator(); iterator
          .hasNext();) {
          final BatchJobRequestExecutionGroup group = iterator.next();
          if (group.isCancelled()) {
            iterator.remove();
          } else {
            count++;
          }
        }
        return count;
      }
    }
  }

  public void groupFinished(final BatchJobRequestExecutionGroup group) {
    removeScheduledGroup(group);
    schedule(group.getBatchJob());
  }

  private void init() {
    this.in.readConnect();
    this.awakeChannel.readConnect();
    this.awakeChannel.writeConnect();
    final CpfConfig config = getConfig();
    Property.addListener(config, "preProcessPoolSize", this);
    final int preProcessPoolSize = config.getPreProcessPoolSize();
    setMaximumPoolSize(preProcessPoolSize);
  }

  public void newExecutionGroup(final BusinessApplication businessApplication,
    final BatchJob batchJob) {
    try {
      final BatchJobRequestExecutionGroup group = batchJob.getNextGroup(businessApplication);
      if (group != null) {
        final String businessApplicationName = group.getBusinessApplicationName();
        if (!group.isCancelled()) {
          synchronized (this.scheduledGroupsByBusinessApplication) {
            Maps.addToSet(this.scheduledGroupsByBusinessApplication, businessApplicationName,
              group);
          }
        }
        this.batchJobService.scheduleGroup(group);
      }
    } catch (final Throwable t) {
      Logs.error(BatchJobScheduler.class, t.getMessage(), t);
    }
  }

  @Override
  public void propertyChange(final PropertyChangeEvent event) {
    final String propertyName = event.getPropertyName();
    if ("schedulerPoolSize".equals(propertyName)) {
      final Integer poolSize = (Integer)event.getNewValue();
      setMaximumPoolSize(poolSize);
    }
  }

  public void removeScheduledGroup(final BatchJobRequestExecutionGroup group) {
    final String businessApplicationName = group.getBusinessApplicationName();
    synchronized (this.scheduledGroupsByBusinessApplication) {
      Maps.removeFromCollection(this.scheduledGroupsByBusinessApplication, businessApplicationName,
        group);
    }
  }

  @Override
  public final void run() {
    boolean hasError = false;
    try {
      Logs.debug(this, "Start");
      run(this.in);
    } catch (final ClosedException e) {
      Logs.debug(this, "Shutdown");
    } catch (final ThreadDeath e) {
      Logs.debug(this, "Shutdown");
    } catch (final Throwable e) {
      Logs.error(e.getMessage(), e);
      hasError = true;
    } finally {
      if (this.in != null) {
        this.in.readDisconnect();
      }
    }
    if (hasError) {
      getProcessNetwork().stop();
    }
  }

  protected void run(final Channel<BatchJob> in) {
    Logs.info(this, "Started");
    init();
    final long timeout = this.timeout;
    final LoadJobIdsToScheduleFromDatabase loadJobIds = new LoadJobIdsToScheduleFromDatabase(
      this.batchJobService);
    final MultiInputSelector selector = new MultiInputSelector();
    final List<Channel<?>> channels = Arrays.asList(this.awakeChannel, in);
    while (true) {
      try {
        final int index = selector.select(timeout, channels);
        if (index == -1) {
          if (!loadJobIds.isRunning()) {
            execute(loadJobIds);
          }
        } else if (index == 0) {
          this.awakeChannel.read();
        } else {
          if (System.currentTimeMillis() - this.errorTime < 60000) {
            ThreadUtil.pause(60000);
          }
          final BatchJob batchJob = in.read();
          final Identifier batchJobId = batchJob.getIdentifier();
          this.queuedJobById.put(batchJobId, batchJob);
        }
        scheduleQueuedJobs();
      } catch (final ClosedException e) {
        Logs.info(this, "Stopped");
        return;
      } catch (final Throwable t) {
        Logs.error(this, "Error scheduling jobs", t);
      }
    }
  }

  public void schedule(final BatchJob batchJob) {
    if (batchJob.isProcessing()) {
      this.in.write(batchJob);
    }
  }

  private void scheduleQueuedJobs() {
    while (!this.queuedJobById.isEmpty()) {
      try {
        for (final Iterator<BatchJob> iterator = this.queuedJobById.values().iterator(); iterator
          .hasNext();) {
          final BatchJob batchJob = iterator.next();
          final String businessApplicationName = batchJob
            .getValue(BatchJob.BUSINESS_APPLICATION_NAME);
          final BusinessApplication businessApplication = this.batchJobService
            .getBusinessApplication(businessApplicationName);
          if (businessApplication != null && businessApplication.getModule().isStarted()) {
            final int maxCount = businessApplication.getMaxConcurrentRequests();
            final int scheduledCount = getScheduledGroupCount(businessApplicationName);
            if (scheduledCount < maxCount) {
              iterator.remove();
              newExecutionGroup(businessApplication, batchJob);
              schedule(batchJob);
            }
          } else {
            iterator.remove();
          }
        }
        return;
      } catch (final ConcurrentModificationException e) {
      }
    }
  }

  /**
   * Set the batch job service used to interact with the database.
   *
   * @param batchJobService The batch job service used to interact with the
   *          database.
   */
  @Resource(name = "batchJobService")
  public void setBatchJobService(final BatchJobService batchJobService) {
    this.batchJobService = batchJobService;
    batchJobService.setScheduler(this);
  }

  @Override
  public void setBeanName(final String beanName) {
    this.beanName = beanName;
    final ThreadFactory threadFactory = getThreadFactory();
    if (threadFactory instanceof NamedThreadFactory) {
      final NamedThreadFactory namedThreadFactory = (NamedThreadFactory)threadFactory;
      namedThreadFactory.setNamePrefix(beanName + "-pool");
    }
  }

  @Override
  public void setProcessNetwork(final ProcessNetwork processNetwork) {
    this.processNetwork = processNetwork;
  }

  @Override
  public String toString() {
    return this.beanName;
  }

}
