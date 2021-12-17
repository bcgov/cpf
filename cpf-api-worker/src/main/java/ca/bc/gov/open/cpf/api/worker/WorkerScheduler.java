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
package ca.bc.gov.open.cpf.api.worker;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.net.ConnectException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.Future;
import java.util.concurrent.FutureTask;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.SynchronousQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import javax.annotation.PreDestroy;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import javax.management.Query;
import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.servlet.annotation.WebListener;

import org.glassfish.tyrus.client.ClientManager;
import org.jeometry.common.exception.Exceptions;
import org.jeometry.common.logging.Logs;

import ca.bc.gov.open.cpf.client.httpclient.HttpStatusCodeException;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.module.ClassLoaderModule;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.Maps;
import com.revolsys.log.LogbackUtil;
import com.revolsys.parallel.NamedThreadFactory;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.spring.resource.ClassPathResource;
import com.revolsys.spring.resource.Resource;
import com.revolsys.ui.web.servlet.listener.DriverManagerCleanupListener;
import com.revolsys.util.Property;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;

@WebListener
public class WorkerScheduler extends ThreadPoolExecutor
  implements Runnable, ServletContextListener {

  private static Integer getPortNumber() {
    try {
      final MBeanServer mbs = ManagementFactory.getPlatformMBeanServer();
      for (final String protocol : Arrays.asList("HTTP/1.1", "AJP/1.3")) {

        final Set<ObjectName> objs = mbs.queryNames(new ObjectName("*:type=Connector,*"),
          Query.match(Query.attr("protocol"), Query.value(protocol)));
        int protocolPort = Integer.MAX_VALUE;
        for (final ObjectName obj : objs) {
          final int port = Integer.parseInt(obj.getKeyProperty("port"));
          if (port < protocolPort) {
            protocolPort = port;
          }
        }
        if (protocolPort < Integer.MAX_VALUE) {
          return protocolPort;
        }
      }

    } catch (final Throwable e) {
      e.printStackTrace();
    }
    return 80;
  }

  private String beanName;

  private BusinessApplicationRegistry businessApplicationRegistry = new BusinessApplicationRegistry(
    false);

  private String environmentName = "default";

  private final Set<String> executingGroupIds = new LinkedHashSet<>();

  private WorkerHttpClient httpClient;

  private String id;

  private long lastPingTime;

  private final long maxTimeBetweenPings = 60 * 1000;

  private final int maxTimeout = 60;

  private final Object monitor = new Object();

  private final AtomicInteger taskCount = new AtomicInteger();

  private String password = "DUMMY_VALUE_MUST_BE_SET_IN_CONFIG";

  private WorkerSecurityServiceFactory securityServiceFactory;

  private int timeout = 0;

  private final int timeoutStep = 10;

  private String username = "cpf_worker";

  private String webServiceUrl = "http://localhost:8080/cpf";

  private final long startTime = System.currentTimeMillis();

  private final Map<String, Future<?>> futureTaskByGroupId = new HashMap<>();

  private final Map<Future<?>, String> groupIdByFutureTask = new HashMap<>();

  private ClientManager client;

  private String nextIdPath;

  private File appLogDirectory;

  private WorkerMessageHandler messageHandler = new WorkerMessageHandler(this);

  public WorkerScheduler() {
    super(0, 100, 60, TimeUnit.SECONDS, new SynchronousQueue<Runnable>(),
      new NamedThreadFactory().setNamePrefix("cpfWorker-pool"));
    String hostName;
    try {
      hostName = InetAddress.getLocalHost().getCanonicalHostName();
    } catch (final UnknownHostException e) {
      hostName = "localhost";
    }

    this.id = this.environmentName + ":" + hostName + ":" + getPortNumber();
    this.securityServiceFactory = new WorkerSecurityServiceFactory(this.messageHandler);
  }

  protected void addExecutingGroupId(final String groupId) {
    synchronized (this.executingGroupIds) {
      this.executingGroupIds.add(groupId);
    }
  }

  public void addExecutingGroupsMessage() {
    final MapEx message = newExecutingGroupsMessage();
    final WorkerMessageHandler messageHandler = this.messageHandler;
    if (messageHandler != null) {
      messageHandler.sendMessage(message);
    }
  }

  public void addFailedGroup(final String groupId) {
    final MapEx message = new LinkedHashMapEx();
    message.put("type", "failedGroupId");
    message.put("groupId", groupId);
    final WorkerMessageHandler messageHandler = this.messageHandler;
    if (messageHandler != null) {
      messageHandler.sendMessage(message);
    }
  }

  @SuppressWarnings("rawtypes")
  @Override
  protected void afterExecute(final Runnable runnable, final Throwable e) {
    if (runnable instanceof FutureTask) {
      final FutureTask futureTask = (FutureTask)runnable;
      final String groupId = this.groupIdByFutureTask.remove(futureTask);
      this.futureTaskByGroupId.remove(groupId);
    }
    this.taskCount.decrementAndGet();
    synchronized (this.monitor) {
      this.monitor.notifyAll();
    }
  }

  public void cancelGroup(final MapEx message) {
    final String groupId = (String)message.get("groupId");
    removeExecutingGroupId(groupId);
    final Future<?> future = this.futureTaskByGroupId.get(groupId);
    if (future != null) {
      future.cancel(true);
    }
  }

  @Override
  public void contextDestroyed(final ServletContextEvent sce) {
    destroy();
  }

  @Override
  public void contextInitialized(final ServletContextEvent servletContextEvent) {
    final ServletContext servletContext = servletContextEvent.getServletContext();
    final String contextPath = servletContext.getContextPath();
    this.id += ":" + contextPath.replaceFirst("^/", "").replaceAll("/", "-");

    this.businessApplicationRegistry.setEnvironmentId("worker_" + this.id.replaceAll(":", "_"));
    this.businessApplicationRegistry.setModuleLoaders(null);

    initConfig();
    initLogging();
    try {
      this.httpClient = new WorkerHttpClient(this.webServiceUrl, this.username, this.password,
        getMaximumPoolSize() + 1);

      final String workerPath = getWorkerPath();
      this.nextIdPath = workerPath + "/jobs/groups/nextId";
    } catch (final Exception e) {
      Logs.error(this, "Error initializing worker", e);
    }
    final WorkerMessageHandler messageHandler = this.messageHandler;
    if (messageHandler != null) {
      execute(messageHandler::connect);
    }
    execute(this);
  }

  @PreDestroy
  public void destroy() {
    WorkerRunning.stop();
    shutdown();
    final WorkerMessageHandler messageHandler = this.messageHandler;
    this.messageHandler = null;
    if (messageHandler != null) {
      messageHandler.close();
    }
    if (this.securityServiceFactory != null) {
      this.securityServiceFactory.close();
      this.securityServiceFactory = null;
    }
    if (this.businessApplicationRegistry != null) {
      this.businessApplicationRegistry.destroy();
    }
    this.businessApplicationRegistry = null;
    this.futureTaskByGroupId.clear();
    this.groupIdByFutureTask.clear();

    if (this.client != null) {
      this.client.shutdown();
      this.client = null;
    }
    if (this.httpClient != null) {
      this.httpClient.close();
    }
    this.httpClient = null;
    shutdownNow();
    try {
      awaitTermination(30, TimeUnit.SECONDS);
    } catch (final InterruptedException e) {
    }
    DriverManagerCleanupListener.cleanupDrivers();
  }

  @Override
  public void execute(final Runnable command) {
    if (command != null) {
      while (!isShutdown()) {
        this.taskCount.incrementAndGet();
        try {
          super.execute(command);
          return;
        } catch (final RejectedExecutionException e) {
          this.taskCount.decrementAndGet();
        } catch (final RuntimeException e) {
          this.taskCount.decrementAndGet();
          throw e;
        } catch (final Error e) {
          this.taskCount.decrementAndGet();
          throw e;
        }
      }
    }
  }

  public File getAppLogDirectory() {
    return this.appLogDirectory;
  }

  public BusinessApplication getBusinessApplication(final AppLog log, final String moduleName,
    final Long moduleTime, final String businessApplicationName) {
    final BusinessApplication businessApplication;
    if (Property.hasValue(moduleName)) {
      final ClassLoaderModule module = (ClassLoaderModule)this.businessApplicationRegistry
        .getModule(moduleName);
      if (module == null) {
        return null;
      } else if (module.isStarted()) {
        businessApplication = module.getBusinessApplication(businessApplicationName);
      } else {
        businessApplication = null;
      }
    } else {
      businessApplication = this.businessApplicationRegistry
        .getBusinessApplication(businessApplicationName);
    }
    return businessApplication;
  }

  public BusinessApplicationRegistry getBusinessApplicationRegistry() {
    return this.businessApplicationRegistry;
  }

  public String getEnvironmentName() {
    return this.environmentName;
  }

  public WorkerHttpClient getHttpClient() {
    return this.httpClient;
  }

  public String getId() {
    return this.id;
  }

  public String getPassword() {
    return this.password;
  }

  public int getPriority() {
    return ((NamedThreadFactory)getThreadFactory()).getPriority();
  }

  public WorkerSecurityServiceFactory getSecurityServiceFactory() {
    return this.securityServiceFactory;
  }

  public String getUsername() {
    return this.username;
  }

  public String getWebServiceUrl() {
    return this.webServiceUrl;
  }

  public String getWorkerPath() {
    return "/worker/workers/" + this.id + "/" + this.startTime;
  }

  private void initConfig() {
    final Resource configResource = new ClassPathResource("/cpfWorker.json");
    try {
      final MapEx config = Json.toMap(configResource);
      String importName = (String)config.get("j:import");
      if (Property.hasValue(importName)) {
        if (importName.startsWith("${cpfConfigDirectory}")) {
          importName = importName.replace("${cpfConfigDirectory}",
            System.getProperty("cpfConfigDirectory", "config"));
        }
        final Resource importResource = Resource.getResource(importName);
        try {
          if (importResource.exists()) {
            final String importExtension = importResource.getFileNameExtension();
            if ("json".equals(importExtension)) {
              final MapEx importConfig = Json.toMap(importResource);
              config.putAll(importConfig);
            } else if ("properties".equals(importExtension)) {
              final Properties properties = new Properties();
              properties.load(importResource.newBufferedReader());
              Maps.putAll(config, properties);
            }
          }
        } catch (final Throwable e) {
          Logs.error(this, "Error reading config:" + importResource, e);
        }
      }
      for (final Entry<String, Object> entry : config.entrySet()) {
        String key = entry.getKey();
        Object value = entry.getValue();
        if (value instanceof String) {
          final String string = (String)value;
          if (string.contains("${cpfLogDirectory}")) {
            value = importName.replace("${cpfLogDirectory}",
              System.getProperty("cpfLogDirectory", "log"));
          }
        }
        if (key.startsWith("cpfWorker.")) {
          key = key.substring(10);
          if ("appLogDirectory".equals(key)) {
            setAppLogDirectory(new File(value.toString()));
          } else {
            Property.setSimple(this, key, value);
          }
        }
      }
    } catch (final Throwable e) {
      Logs.error(this, "Error reading config:" + configResource, e);
    }
  }

  private void initLogging() {
    final Logger logger = LogbackUtil.getRootLogger();
    logger.setLevel(Level.ERROR);
    LogbackUtil.setLevel("ca.bc.gov.cpf", Level.INFO);
    LogbackUtil.setLevel("ca.bc.gov.open.cpf", Level.INFO);
    LogbackUtil.setLevel("com.revolsys.maven.MavenRepositoryCache", Level.INFO);
    LogbackUtil.setLevel("ca.bc.gov.open.cpf.client.api.httpclient", Level.ERROR);

    LogbackUtil.removeAllAppenders(logger);
    final File rootDirectory = this.appLogDirectory;
    if (rootDirectory == null || !(rootDirectory.exists() || rootDirectory.mkdirs())) {
      LogbackUtil.addRootAppender("%d\\t%p\\t%c\\t%m%n");
    } else {
      final String id = this.id.replaceAll(":", "-");
      ClassLoaderModule.addAppender(logger, rootDirectory + "/worker-" + id, "cpf-worker-all");
      ClassLoaderModule.addAppender(logger, rootDirectory + "/worker-app-" + id, "cpf-worker");
    }
  }

  public boolean isRunning() {
    return WorkerRunning.isRunning();
  }

  public void logError(final String message) {
    Logs.error(this, message);
  }

  protected void logError(final String message, final Throwable e) {
    if (isRunning()) {
      Logs.error(this, message, e);
    }
  }

  protected MapEx newExecutingGroupsMessage() {
    final MapEx message = new LinkedHashMapEx();
    message.put("type", "executingGroupIds");
    message.put("workerId", this.id);
    synchronized (this.executingGroupIds) {
      message.put("executingGroupIds", new ArrayList<>(this.executingGroupIds));
    }
    this.lastPingTime = System.currentTimeMillis();
    return message;
  }

  public boolean processNextTask() {
    final long time = System.currentTimeMillis();
    final long nextPingTime = this.lastPingTime + this.maxTimeBetweenPings;
    if (time > nextPingTime) {
      addExecutingGroupsMessage();
    }
    if (!isRunning()) {
      return false;
    }
    if (this.taskCount.get() >= getMaximumPoolSize()) {
      addExecutingGroupsMessage();
      return false;
    } else {
      try {
        final MapEx parameters = new LinkedHashMapEx();
        MapEx response = null;
        final WorkerMessageHandler messageHandler = this.messageHandler;
        if (messageHandler == null) {
          return false;
        }
        final Set<String> loadedModuleNames = messageHandler.getLoadedModuleNames();
        parameters.put("moduleName", loadedModuleNames);

        if (isRunning()) {
          response = this.httpClient.postGetJsonResource(this.nextIdPath, parameters);
        }
        if (!isRunning()) {
          return false;
        } else {
          if (response != null && !response.isEmpty()) {
            if (response.get("batchJobId") != null) {
              Logs.debug(this, "Scheduling group " + response);
              final String groupId = (String)response.get("groupId");
              addExecutingGroupId(groupId);
              try {
                final Runnable runnable = new WorkerGroupRunnable(this, response);
                final Future<?> future = submit(runnable);
                this.futureTaskByGroupId.put(groupId, future);
                this.groupIdByFutureTask.put(future, groupId);
              } catch (final Throwable e) {
                if (isRunning()) {
                  Logs.error(this, "Unable to get execute group " + groupId, e);
                }
                removeExecutingGroupId(groupId);
                addExecutingGroupsMessage();
              }
            }
          } else {
            Logs.debug(this, "No group available");
          }
        }
        return true;
      } catch (final HttpStatusCodeException t) {
        addExecutingGroupsMessage();
        if (t.getStatusCode() == 404) {

        } else {
          if (isRunning()) {
            Logs.error(this, "Unable to get group", t);
          }
        }
      } catch (final Throwable t) {
        if (Exceptions.isException(t, ConnectException.class)) {
          return false;
        } else {
          addExecutingGroupsMessage();
          logError("Unable to get group", t);
        }
      }
    }
    return false;
  }

  protected void removeExecutingGroupId(final String groupId) {
    synchronized (this.executingGroupIds) {
      this.executingGroupIds.remove(groupId);
    }
  }

  @Override
  public void run() {
    Logs.info(this, "Started");
    try {
      while (isRunning()) {
        try {
          if (processNextTask()) {
            this.timeout = 0;
          } else {
            if (this.timeout < this.maxTimeout) {
              this.timeout += this.timeoutStep;
            }
          }
          if (isRunning() && this.timeout != 0) {
            synchronized (this.monitor) {
              Logs.debug(this, "Waiting " + this.timeout + " seconds before getting next task");
              this.monitor.wait(this.timeout * 1000);
            }
          }
        } catch (final InterruptedException e) {
          if (!isRunning()) {
            return;
          }
        }
      }

    } catch (final Throwable e) {
      Logs.error(this, e);
    } finally {
      Logs.info(this, "Stopped");
    }
  }

  public boolean scheduleGroup(final MapEx group) {
    if (isRunning()) {
      if (group != null && !group.isEmpty()) {
        if (group.get("batchJobId") != null) {
          Logs.debug(this, "Scheduling group " + group);
          final String groupId = group.getString("groupId");
          this.addExecutingGroupId(groupId);
          try {
            final Runnable runnable = new WorkerGroupRunnable(this, group);
            final Future<?> future = this.submit(runnable);
            this.futureTaskByGroupId.put(groupId, future);
            this.groupIdByFutureTask.put(future, groupId);
            return true;
          } catch (final Throwable e) {
            if (isRunning()) {
              Logs.error(this, "Unable to get execute group " + groupId, e);
            }
            removeExecutingGroupId(groupId);
            addExecutingGroupsMessage();
          }
        }
      } else {
        Logs.debug(this, "No group available");
      }
    }
    return false;
  }

  public void setAppLogDirectory(final File appLogDirectory) {
    this.appLogDirectory = appLogDirectory;
    this.businessApplicationRegistry.setAppLogDirectory(appLogDirectory);
  }

  public void setEnvironmentName(final String environmentName) {
    this.environmentName = environmentName;
  }

  @Override
  public void setMaximumPoolSize(final int maximumPoolSize) {
    super.setMaximumPoolSize(maximumPoolSize);
  }

  public void setModuleNames(final List<String> moduleNames) {
    final WorkerMessageHandler messageHandler = this.messageHandler;
    if (messageHandler != null) {
      messageHandler.setModuleNames(moduleNames);
    }
  }

  public void setPassword(final String password) {
    this.password = password;
  }

  public void setPriority(final int priority) {
    ((NamedThreadFactory)getThreadFactory()).setPriority(priority);
  }

  public void setUsername(final String username) {
    this.username = username;
  }

  public void setWebServiceUrl(final String webServiceUrl) {
    this.webServiceUrl = webServiceUrl;
  }

  @Override
  public String toString() {
    return this.beanName;
  }

}
