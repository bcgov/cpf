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
import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.websocket.ClientEndpoint;
import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;

import org.glassfish.tyrus.client.ClientManager;
import org.glassfish.tyrus.client.ClientProperties;
import org.glassfish.tyrus.client.auth.AuthConfig;
import org.glassfish.tyrus.client.auth.AuthConfig.Builder;
import org.glassfish.tyrus.client.auth.AuthenticationException;
import org.glassfish.tyrus.client.auth.Authenticator;
import org.glassfish.tyrus.client.auth.Credentials;

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
import com.revolsys.logging.Logs;
import com.revolsys.spring.ClassLoaderFactoryBean;
import com.revolsys.util.Exceptions;
import com.revolsys.util.Property;
import com.revolsys.websocket.json.JsonAsyncSender;
import com.revolsys.websocket.json.JsonDecoder;
import com.revolsys.websocket.json.JsonEncoder;

@ClientEndpoint(encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WorkerMessageHandler implements ModuleEventListener, BaseCloseable {
  private final WorkerScheduler scheduler;

  private final Set<String> loadedModuleNames = new HashSet<>();

  private JsonAsyncSender messageSender;

  private final List<String> includedModuleNames = new ArrayList<>();

  private final List<String> excludedModuleNames = new ArrayList<>();

  private File tempDir = FileUtil.newTempDirectory("cpf", "jars");

  private boolean running = true;

  private final ConfigPropertyLoader configPropertyLoader;

  private ClientManager client;

  private Session session;

  public WorkerMessageHandler(final WorkerScheduler scheduler) {
    super();
    this.scheduler = scheduler;
    this.configPropertyLoader = new WorkerConfigPropertyLoader(scheduler, this);
    final BusinessApplicationRegistry businessApplicationRegistry = scheduler
      .getBusinessApplicationRegistry();
    businessApplicationRegistry.addModuleEventListener(this);
    businessApplicationRegistry.setConfigPropertyLoader(this.configPropertyLoader);
    initConnection();
  }

  @Override
  public void close() {
    this.running = false;
    if (this.session != null) {
      try {
        this.session.close();
      } catch (final IOException e) {
      }
      this.session = null;
    }
    if (this.client != null) {
      this.client.shutdown();
      this.client = null;
    }
    this.messageSender = null;
    if (this.tempDir != null) {
      if (!FileUtil.deleteDirectory(this.tempDir)) {
        Logs.error(this, "Unable to delete jar cache " + this.tempDir);
      }
      this.tempDir = null;
    }
  }

  public void connect() {
    if (this.session == null) {
      while (this.running) {
        final String workerPath = this.scheduler.getWorkerPath();
        final String webServiceUrl = this.scheduler.getWebServiceUrl();
        final String webSocketUrl = webServiceUrl.replaceFirst("http", "ws") + workerPath
          + "/message";
        try {
          final URI webSocketUri = new URI(webSocketUrl);
          this.session = this.client.connectToServer(this, webSocketUri);
          Logs.info(this, "Connected to server: " + webSocketUri);
          return;
        } catch (final URISyntaxException e) {
          Logs.error(this, "cpfClient.webServiceUrl not valid", e);
        } catch (final Throwable e) {
          Logs.error(this, "Cannot connect to server: " + webSocketUrl, e);
        }
        try {
          synchronized (this) {
            // Wait 1 minutes before trying again
            wait(1000 * 60);
          }
        } catch (final InterruptedException e) {
        }
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

            urls.add(FileUtil.toUrl(jarFile));
          } catch (final Throwable e) {
            throw new RuntimeException("Unable to download jar file " + jarPath, e);
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
    this.messageSender = null;
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
      if (messageSender != null) {
        messageSender.setResult(message);
      }
    }
  }

  @OnOpen
  public void onOpen(final Session session) {
    this.messageSender = new JsonAsyncSender(session);
  }

  public void sendMessage(final MapEx message) {
    final JsonAsyncSender messageSender = getMessageSender();
    if (messageSender != null) {
      messageSender.sendMessage(message);
    }
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
