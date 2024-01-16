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
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import jakarta.websocket.ClientEndpoint;
import jakarta.websocket.CloseReason;
import jakarta.websocket.CloseReason.CloseCode;
import jakarta.websocket.CloseReason.CloseCodes;
import jakarta.websocket.OnClose;
import jakarta.websocket.OnError;
import jakarta.websocket.OnMessage;
import jakarta.websocket.OnOpen;
import jakarta.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientManager.ReconnectHandler;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.auth.AuthConfig;
import org.glassfish.tyrus.client.auth.AuthConfig.Builder;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.glassfish.tyrus.client.auth.Authenticator;
import org.glassfish.tyrus.client.auth.Credentials;
import org.jeometry.common.exception.Exceptions;
import org.jeometry.common.logging.Logs;

import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;
import ca.bc.gov.open.cpf.plugin.impl.module.ClassLoaderModule;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEvent;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEventListener;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleLoader;
import ca.bc.gov.open.cpf.plugin.impl.security.SignatureUtil;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.io.BaseCloseable;
import com.revolsys.io.FileUtil;
import com.revolsys.spring.ClassLoaderFactoryBean;
import com.revolsys.util.Property;
import com.revolsys.websocket.json.JsonAsyncSender;
import com.revolsys.websocket.json.JsonDecoder;
import com.revolsys.websocket.json.JsonEncoder;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WorkerMessageHandler implements ModuleEventListener, BaseCloseable {
  private final WorkerScheduler scheduler;

  private final Set<String> loadedModuleNames = new HashSet<>();

  private final JsonAsyncSender messageSender = new JsonAsyncSender();

  private final List<String> includedModuleNames = new ArrayList<>();

  private final List<String> excludedModuleNames = new ArrayList<>();

  private File tempDir = FileUtil.newTempDirectory("cpf", "jars");

  private boolean running = true;

  private final ConfigPropertyLoader configPropertyLoader;

  private ClientManager client;

  private long lastConnectTimestamp = 0;

  private long lastErrorTimestamp = 0;

  private int reconnectDelay = 0;

  private String webSocketUrl;

  private final long startTime = System.currentTimeMillis();

  private boolean connected;

  private final Object reconnectSync = new Object();

  private final Thread reconnectThread = new Thread(this::reconnectRunnable,
    "cpf-worker-reconnect");

  private boolean reconnect;

  public WorkerMessageHandler(final WorkerScheduler scheduler) {
    this.scheduler = scheduler;
    this.configPropertyLoader = new WorkerConfigPropertyLoader(scheduler, this);
    final BusinessApplicationRegistry businessApplicationRegistry = scheduler
      .getBusinessApplicationRegistry();
    businessApplicationRegistry.addModuleEventListener(this);
    businessApplicationRegistry.setConfigPropertyLoader(this.configPropertyLoader);
    initConnection();
    this.reconnectThread.start();
  }

  @Override
  public void close() {
    this.running = false;
    this.messageSender.close();
    synchronized (this.reconnectSync) {
      this.reconnectSync.notifyAll();
    }

    if (this.client != null) {
      this.client.shutdown();
      this.client = null;
    }
    if (this.tempDir != null) {
      if (!FileUtil.deleteDirectory(this.tempDir)) {
        Logs.error(this, "Unable to delete jar cache " + this.tempDir);
      }
      this.tempDir = null;
    }
  }

  public void connect() {
    boolean first = true;
    int waitTime = 5;
    while (isRunning()) {
      final String workerPath = this.scheduler.getWorkerPath();
      final String webServiceUrl = this.scheduler.getWebServiceUrl();
      this.webSocketUrl = webServiceUrl.replaceFirst("http", "ws") + workerPath + "/message";
      try {
        final URI webSocketUri = new URI(this.webSocketUrl);
        this.client.connectToServer(this, webSocketUri);
        this.connected = true;
        return;
      } catch (final URISyntaxException e) {
        Logs.error(this, "cpfClient.webServiceUrl not valid", e);
        return;
      } catch (final Throwable e) {
        // Don't log within 1 minute of the server starting up
        if (first && System.currentTimeMillis() > this.startTime + 60 * 1000) {
          first = false;
          if (isRunning()) {
            waitTime = 30;
            Logs.error(this, "Cannot connect to server: " + this.webSocketUrl, e);
          }
        }
      }
      try {
        synchronized (this) {
          // Wait 1 minutes before trying again
          wait(1000 * waitTime);
        }
      } catch (final InterruptedException e) {
      }
    }
  }

  public BusinessApplicationRegistry getBusinessApplicationRegistry() {
    return this.scheduler.getBusinessApplicationRegistry();
  }

  public Set<String> getLoadedModuleNames() {
    return this.loadedModuleNames;
  }

  public JsonAsyncSender getMessageSender() {
    return this.messageSender;
  }

  private void initConnection() {
    this.client = ClientManager.createClient();
    final Map<String, Object> config = this.client.getProperties();

    final AuthConfig authConfig = Builder.create() //
      .disableProvidedBasicAuth() //
      .disableProvidedDigestAuth() //
      .registerAuthProvider("CPF_WORKER", new Authenticator() {
        @Override
        public String generateAuthorizationHeader(final URI uri, final String wwwAuthenticateHeader,
          final Credentials credentials) throws AuthenticationException {
          final String username = WorkerMessageHandler.this.scheduler.getUsername();
          final String password = WorkerMessageHandler.this.scheduler.getPassword();
          final String fullPath = uri.getPath();
          final long time = System.currentTimeMillis();
          final String signature = SignatureUtil.sign(password, fullPath, time);
          final StringBuilder authorization = new StringBuilder();
          authorization.append(username);
          authorization.append(',');
          authorization.append(time);
          authorization.append(',');
          authorization.append(signature);
          return authorization.toString();
        }
      })
      .build();
    config.put(ClientProperties.AUTH_CONFIG, authConfig);
    config.put(ClientProperties.RETRY_AFTER_SERVICE_UNAVAILABLE, true);
    config.put(ClientProperties.RECONNECT_HANDLER, new ReconnectHandler() {

      private boolean handleMessage(final String message, final Exception exception) {
        if (WorkerMessageHandler.this.running && WorkerMessageHandler.this.connected) {
          final long time = System.currentTimeMillis();
          if (WorkerMessageHandler.this.reconnectDelay < 60) {
            WorkerMessageHandler.this.reconnectDelay += 10;
          }
          final int oneHour = 60 * 60 * 1000;
          if (WorkerMessageHandler.this.lastErrorTimestamp < WorkerMessageHandler.this.lastConnectTimestamp
            || WorkerMessageHandler.this.lastErrorTimestamp + oneHour < time) {
            if (exception == null) {
              Logs.info(WorkerMessageHandler.class, message);
            } else {
              Logs.error(WorkerMessageHandler.class, message, exception);
            }
          }
          WorkerMessageHandler.this.lastErrorTimestamp = time;
          synchronized (WorkerMessageHandler.this.reconnectSync) {
            WorkerMessageHandler.this.reconnect = true;
            WorkerMessageHandler.this.reconnectSync.notifyAll();
          }
        }
        return false;
      }

      @Override
      public boolean onConnectFailure(final Exception exception) {
        final String message = "Master error " + exception.getMessage();
        return handleMessage(message, exception);
      }

      // @Override
      // public boolean onDisconnect(final CloseReason closeReason) {
      //   final int code = closeReason.getCloseCode().getCode();
      //   final CloseCode closeCode = CloseCodes.getCloseCode(code);
      //   final String reason = closeCode + " " + closeReason.getReasonPhrase();
      //   return handleMessage("Master disconnected " + reason, null);
      // }
    });
  }

  public boolean isRunning() {
    if (this.running) {
      if (!WorkerRunning.isRunning()) {
        close();
      }
    }
    return this.running;
  }

  @Override
  public void moduleChanged(final ModuleEvent event) {
    final String action = event.getAction();
    final Module module = event.getModule();
    final String moduleName = module.getName();
    MapEx message = null;
    if (action.equals(ModuleEvent.START)) {

    } else if (action.equals(ModuleEvent.START_FAILED)) {
      final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
      businessApplicationRegistry.unloadModule(module);

      final String moduleError = module.getModuleError();
      message = newModuleMessage(module, "moduleExcluded");
      message.put("moduleError", moduleError);
    } else if (action.equals(ModuleEvent.STOP)) {
      message = newModuleMessage(module, "moduleStopped");
      this.loadedModuleNames.remove(moduleName);
    }
    if (message != null) {
      sendMessage(message);
    }
  }

  private void moduleSecurityChanged(final MapEx message) {
    final String moduleName = message.getString("moduleName");
    final ClassLoaderModule module = (ClassLoaderModule)getBusinessApplicationRegistry()
      .getModule(moduleName);
    if (module != null) {
      final ModuleEvent moduleEvent = new ModuleEvent(module, ModuleEvent.SECURITY_CHANGED);
      final WorkerSecurityServiceFactory securityServiceFactory = this.scheduler
        .getSecurityServiceFactory();
      securityServiceFactory.moduleChanged(moduleEvent);
    }
  }

  protected void moduleStart(final MapEx message) {
    final String moduleName = (String)message.get("moduleName");
    final Long moduleTime = message.getLong("moduleTime");
    final int moduleJarCount = message.getInteger("moduleJarCount", 0);
    if ((this.includedModuleNames.isEmpty() || this.includedModuleNames.contains(moduleName))
      && !this.excludedModuleNames.contains(moduleName)) {
      final AppLog log = new AppLog(moduleName);

      final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
      ClassLoaderModule module = (ClassLoaderModule)businessApplicationRegistry
        .getModule(moduleName);
      if (module != null) {
        final long lastStartedTime = module.getStartedTime();
        if (lastStartedTime < moduleTime) {
          log.info("Unloading older module version\tmoduleName=" + moduleName + "\tmoduleTime="
            + lastStartedTime);
          unloadModule(module);
          module = null;
        } else if (lastStartedTime == moduleTime) {
          return;
        }
      }
      try {
        final File moduleDir = new File(this.tempDir, moduleName + "-" + moduleTime);
        moduleDir.mkdir();
        moduleDir.deleteOnExit();
        final List<URL> urls = new ArrayList<>();
        for (int jarIndex = 0; jarIndex < moduleJarCount; jarIndex++) {
          final String jarPath = "/worker/modules/" + moduleName + "/" + moduleTime + "/jar/"
            + jarIndex;
          try {
            final File jarFile = new File(moduleDir, jarIndex + ".jar");
            jarFile.deleteOnExit();
            final WorkerHttpClient httpClient = this.scheduler.getHttpClient();
            httpClient.getResource(jarPath, jarFile);
            if (jarFile.length() > 0) {
              urls.add(FileUtil.toUrl(jarFile));
            } else {
              log.error("Empty jar file " + jarPath);
            }
          } catch (final Throwable e) {
            log.error("Unable to download jar file " + jarPath, e);
          }
        }
        final ClassLoader parentClassLoader = getClass().getClassLoader();
        final ClassLoader classLoader = ClassLoaderFactoryBean.newClassLoader(parentClassLoader,
          urls);
        final List<URL> configUrls = ModuleLoader.getConfigUrls(classLoader, false);
        if (configUrls.isEmpty()) {
          final String urlsMessage = "Cannot load classes for module " + moduleName;
          log.error(urlsMessage);
          final MapEx responseMessage = new LinkedHashMapEx();
          responseMessage.put("type", "moduleStartFailed");
          responseMessage.put("moduleName", moduleName);
          responseMessage.put("moduleTime", moduleTime);
          responseMessage.put("moduleError", urlsMessage);
          sendMessage(responseMessage);
          if (module != null) {
            businessApplicationRegistry.unloadModule(module);
          }
        } else {
          module = new ClassLoaderModule(businessApplicationRegistry, moduleName, classLoader,
            this.configPropertyLoader, configUrls.get(0), "INFO");
          businessApplicationRegistry.addModule(module);
          final Module startModule = module;
          this.scheduler.execute(() -> startApplications(startModule));
        }
      } catch (final Throwable e) {
        log.error("Unable to load module " + moduleName, e);
        final MapEx responseMessage = new LinkedHashMapEx();
        responseMessage.put("type", "moduleStartFailed");
        responseMessage.put("moduleName", moduleName);
        responseMessage.put("moduleTime", moduleTime);
        responseMessage.put("moduleError", Exceptions.toString(e));
        sendMessage(responseMessage);
        if (module != null) {
          businessApplicationRegistry.unloadModule(module);
        }
      }
    } else {
      final MapEx responseMessage = newModuleMessage(moduleName, moduleTime, "moduleDisabled");
      sendMessage(responseMessage);
    }
  }

  protected void moduleStop(final MapEx message) {
    final String moduleName = message.getString("moduleName");
    final ClassLoaderModule module = (ClassLoaderModule)this.getBusinessApplicationRegistry()
      .getModule(moduleName);
    if (module != null) {
      unloadModule(module);
    }
  }

  protected MapEx newModuleMessage(final Module module, final String action) {
    final String moduleName = module.getName();
    final long moduleTime = module.getStartedTime();
    return newModuleMessage(moduleName, moduleTime, action);
  }

  protected MapEx newModuleMessage(final String moduleName, final long moduleTime,
    final String action) {
    final MapEx message = new LinkedHashMapEx();
    message.put("type", action);
    message.put("moduleName", moduleName);
    message.put("moduleTime", moduleTime);
    return message;
  }

  @OnClose
  public void onClose(final Session session) {
    this.messageSender.clearSession();
  }

  @OnError
  public void onError(final Session session, final Throwable e) {
    Logs.error(this, "Websocket error: " + session, e);
  }

  @OnMessage
  public void onMessage(final MapEx message) {
    final String type = message.getString("type");
    if (ModuleEvent.STOP.equals(type)) {
      moduleStop(message);
    } else if (ModuleEvent.START.equals(type)) {
      moduleStart(message);
    } else if (ModuleEvent.SECURITY_CHANGED.equals(type)) {
      moduleSecurityChanged(message);
    } else if ("cancelGroup".equals(type)) {
      this.scheduler.cancelGroup(message);
    } else {
      final JsonAsyncSender messageSender = getMessageSender();
      messageSender.setResult(message);
    }
  }

  @OnOpen
  public void onOpen(final Session session) {
    this.lastConnectTimestamp = System.currentTimeMillis();
    this.reconnectDelay = 0;
    this.messageSender.setSession(session);
    Logs.info(this, "Master connected " + this.webSocketUrl);
  }

  private void reconnectRunnable() {
    while (isRunning()) {
      synchronized (this.reconnectSync) {
        try {
          this.reconnectSync.wait();
        } catch (final InterruptedException e) {
        }
        if (this.reconnect && isRunning()) {
          synchronized (this.reconnectSync) {
            try {
              this.reconnectSync.wait(this.reconnectDelay * 1000);
            } catch (final InterruptedException e) {
            }
          }
          if (isRunning()) {
            connect();
          }
        }
        this.reconnect = false;
      }
    }
  }

  public void sendMessage(final MapEx message) {
    this.messageSender.sendMessage(message);
  }

  public void setModuleNames(final List<String> moduleNames) {
    this.includedModuleNames.clear();
    this.excludedModuleNames.clear();
    if (moduleNames != null) {
      for (final String moduleName : moduleNames) {
        if (moduleName.startsWith("-")) {
          this.excludedModuleNames.add(moduleName.substring(1));
        } else {
          this.includedModuleNames.add(moduleName);
        }
      }
    }
  }

  public void startApplications(final Module module) {
    final String moduleName = module.getName();
    MapEx message;
    try {
      module.enable();
      module.loadApplications();
      final String moduleError = module.getModuleError();
      if (Property.hasValue(moduleError)) {
        message = newModuleMessage(module, "moduleStartFailed");
        message.put("moduleError", moduleError);
        getBusinessApplicationRegistry().unloadModule(module);
      } else {
        this.loadedModuleNames.add(moduleName);
        message = newModuleMessage(module, "moduleStarted");
      }
    } catch (final Throwable e) {
      final AppLog log = new AppLog(moduleName);
      log.error("Unable to load module " + moduleName, e);
      message = newModuleMessage(module, "moduleStartFailed");
      message.put("moduleError", Exceptions.toString(e));
      getBusinessApplicationRegistry().unloadModule(module);
    }
    if (message != null) {
      sendMessage(message);
    }
  }

  public void unloadModule(final ClassLoaderModule module) {
    final long time = module.getStartedTime();
    getBusinessApplicationRegistry().unloadModule(module);
    final File lastModuleDir = new File(this.tempDir, module.getName() + "-" + time);
    FileUtil.deleteDirectory(lastModuleDir, true);
  }

}
