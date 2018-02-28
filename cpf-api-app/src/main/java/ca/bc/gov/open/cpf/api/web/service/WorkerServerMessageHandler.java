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
package ca.bc.gov.open.cpf.api.web.service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.BiConsumer;

import javax.websocket.OnClose;
import javax.websocket.OnError;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.server.PathParam;
import javax.websocket.server.ServerEndpoint;

import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.context.ContextLoader;
import org.springframework.web.context.WebApplicationContext;

import ca.bc.gov.open.cpf.api.domain.ConfigProperty;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.api.scheduler.Worker;
import ca.bc.gov.open.cpf.api.scheduler.WorkerModuleState;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEvent;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEventListener;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.Maps;
import com.revolsys.logging.Logs;
import com.revolsys.record.Record;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.websocket.json.JsonDecoder;
import com.revolsys.websocket.json.JsonEncoder;

@ServerEndpoint(value = "/worker/workers/{workerId}/{startTime}/message",
    encoders = JsonEncoder.class, decoders = JsonDecoder.class)
public class WorkerServerMessageHandler implements ModuleEventListener {

  private BusinessApplicationRegistry businessApplicationRegistry;

  private BatchJobService batchJobService;

  private final Map<String, BiConsumer<MapEx, Worker>> messageHandlers = new LinkedHashMap<>();

  public WorkerServerMessageHandler() {
    this.messageHandlers.put("executingGroupIds", this::executingGroupIds);
    this.messageHandlers.put("failedGroupId", this::failedGroupId);
    this.messageHandlers.put("moduleConfigLoad", this::moduleConfigLoad);
    this.messageHandlers.put("moduleDisabled", this::moduleDisabled);
    this.messageHandlers.put("moduleStarted", this::moduleStarted);
    this.messageHandlers.put("moduleStartFailed", this::moduleStartFailed);
    this.messageHandlers.put("moduleStopped", this::moduleStopped);
    this.messageHandlers.put("securityUserAttributes", this::securityUserAttributes);
  }

  private void addConfigProperties(final Map<String, MapEx> configProperties,
    final String environmentName, final String moduleName, final String componentName) {
    final CpfDataAccessObject dataAccessObject = this.batchJobService.getDataAccessObject();
    final List<Record> properties = dataAccessObject.getConfigPropertiesForModule(environmentName,
      moduleName, componentName);
    for (final Record configProperty : properties) {
      final String propertyName = configProperty.getValue(ConfigProperty.PROPERTY_NAME);
      configProperties.put(propertyName, configProperty);
    }
  }

  private void executingGroupIds(final MapEx message, final Worker worker) {
    @SuppressWarnings("unchecked")
    final List<String> executingGroupIds = (List<String>)message.get("executingGroupIds");
    this.batchJobService.updateWorkerExecutingGroups(worker, executingGroupIds);
  }

  private void failedGroupId(final MapEx message, final Worker worker) {
    final String groupId = (String)message.get("groupId");
    this.batchJobService.cancelGroup(worker, groupId);
  }

  private Collection<MapEx> getConfigProperties(final String environmentName,
    final String moduleName, final String componentName) {
    final Map<String, MapEx> configProperiesByName = new TreeMap<>();
    addConfigProperties(configProperiesByName, ConfigProperty.DEFAULT, moduleName, componentName);
    addConfigProperties(configProperiesByName, environmentName, moduleName, componentName);
    return configProperiesByName.values();
  }

  public boolean isModuleEnabled(final String moduleName) {
    final Module module = this.batchJobService.getModule(moduleName);
    final boolean enabled = module != null && module.isEnabled();
    return enabled;
  }

  @Override
  public void moduleChanged(final ModuleEvent event) {
    final String type = event.getAction();
    final MapEx message = new LinkedHashMapEx("type", type);

    final String moduleName = event.getModuleName();
    message.put("moduleName", moduleName);

    final long moduleTime = event.getModuleTime();
    message.put("moduleTime", moduleTime);

    final Module module = event.getModule();
    final int jarCount = module.getJarCount();
    message.put("moduleJarCount", jarCount);

    for (final Worker worker : this.batchJobService.getWorkers()) {
      worker.sendMessage(message);
    }
  }

  private void moduleConfigLoad(final MapEx message, final Worker worker) {
    final String moduleName = message.getString("moduleName");
    final String environmentName = message.getString("environmentName");
    final String componentName = message.getString("componentName");
    final Collection<MapEx> configProperties = getConfigProperties(environmentName, moduleName,
      componentName);
    final MapEx resultMessage = newResultMessage(message);
    resultMessage.put("properties", configProperties);
    worker.sendMessage(resultMessage);
  }

  private void moduleDisabled(final MapEx message, final Worker worker) {
    final String moduleName = (String)message.get("moduleName");
    final boolean enabled = isModuleEnabled(moduleName);
    final WorkerModuleState moduleState = worker.getModuleState(moduleName);
    moduleState.setEnabled(enabled);
    moduleState.setStatus("Disabled");
    moduleState.setStartedTime(0);
  }

  private void moduleStarted(final MapEx message, final Worker worker) {
    final String moduleName = message.getString("moduleName");
    final boolean enabled = isModuleEnabled(moduleName);
    final WorkerModuleState moduleState = worker.getModuleState(moduleName);
    moduleState.setEnabled(enabled);
    moduleState.setStatus("Started");
    final long moduleTime = Maps.getLong(message, "moduleTime");
    moduleState.setStartedTime(moduleTime);
  }

  private void moduleStartFailed(final MapEx message, final Worker worker) {
    final String moduleName = (String)message.get("moduleName");
    final boolean enabled = isModuleEnabled(moduleName);
    final WorkerModuleState moduleState = worker.getModuleState(moduleName);
    moduleState.setEnabled(enabled);
    moduleState.setStatus("Start Failed");
    final String moduleError = (String)message.get("moduleError");
    moduleState.setModuleError(moduleError);
    moduleState.setStartedTime(0);
  }

  private void moduleStopped(final MapEx message, final Worker worker) {
    final String moduleName = (String)message.get("moduleName");
    final boolean enabled = isModuleEnabled(moduleName);
    final WorkerModuleState moduleState = worker.getModuleState(moduleName);
    moduleState.setEnabled(enabled);
    if (enabled) {
      moduleState.setStatus("Stopped");
    } else {
      moduleState.setStatus("Disabled");
    }
    moduleState.setStartedTime(0);
  }

  private MapEx newResultMessage(final MapEx message) {
    final String messageId = message.getString("messageId");
    final MapEx resultMessage = new LinkedHashMapEx("messageId", messageId);
    return resultMessage;
  }

  @OnClose
  public void onClose(@PathParam("workerId") final String workerId,
    @PathParam("startTime") final long workerStartTime, final Session session) {
    this.batchJobService.setWorkerDisconnected(workerId, workerStartTime, session);
  }

  @OnError
  public void onError(final Session session, final Throwable e) {
    if (e instanceof SocketTimeoutException) {
      Logs.warn(this, "Websocket timeout: " + session);
      try {
        session.close();
      } catch (final IOException ioe) {
        Logs.error(this, "Error closing: " + session, e);

      }
    } else {
      Logs.error(this, "Websocket error: " + session, e);
    }
  }

  @OnMessage
  public void onMessage(@PathParam("workerId") final String workerId, final MapEx message) {
    final Worker worker = this.batchJobService.getWorker(workerId);
    if (worker != null) {
      final String type = message.getString("type");
      final BiConsumer<MapEx, Worker> messageHandler = this.messageHandlers.get(type);
      if (messageHandler == null) {
        Logs.error(this, "Invalid message type: " + message);
      } else {
        try {
          messageHandler.accept(message, worker);
        } catch (final Throwable e) {
          Logs.error(this, "Unable to handle message: " + message, e);
        }
      }
    }
  }

  @OnOpen
  public void onOpen(@PathParam("workerId") final String workerId,
    @PathParam("startTime") final long workerStartTime, final Session session) {
    if (this.businessApplicationRegistry == null) {
      final WebApplicationContext wac = (WebApplicationContext)ContextLoader
        .getCurrentWebApplicationContext()
        .getServletContext()
        .getAttribute("org.springframework.web.servlet.FrameworkServlet.CONTEXT.cpf");
      this.businessApplicationRegistry = wac.getBean(BusinessApplicationRegistry.class);
      this.businessApplicationRegistry.addModuleEventListener(this);
      this.batchJobService = wac.getBean(BatchJobService.class);
    }
    this.batchJobService.setWorkerConnected(workerId, workerStartTime, session);
  }

  @RequestMapping(value = "/worker/modules/{moduleName}/users/{consumerKey}/resourcePermission")
  @ResponseBody
  public void securityCanAccessResource(final MapEx message, final Worker worker) {
    final String moduleName = message.getString("moduleName");
    final String consumerKey = message.getString("consumerKey");
    final String resourceClass = message.getString("resourceClass");
    final String resourceId = message.getString("resourceId");
    final String actionName = message.getString("actionName");

    final MapEx resultMessage = newResultMessage(message);
    final Module module = this.batchJobService.getModule(moduleName);
    if (module != null) {
      final SecurityService securityService = this.batchJobService.getSecurityService(module,
        consumerKey);
      final boolean hasAccess = securityService.canAccessResource(resourceClass, resourceId,
        actionName);
      resultMessage.put("hasAccess", hasAccess);
    }
    worker.sendMessage(resultMessage);
  }

  @RequestMapping(
      value = "/worker/modules/{moduleName}/users/{consumerKey}/actions/{actionName}/hasAccess")
  @ResponseBody
  public void securityCanPerformAction(final MapEx message, final Worker worker) {
    final String moduleName = message.getString("moduleName");
    final String consumerKey = message.getString("consumerKey");
    final String actionName = message.getString("actionName");

    final MapEx resultMessage = newResultMessage(message);
    final Module module = this.batchJobService.getModule(moduleName);
    if (module != null) {
      final SecurityService securityService = this.batchJobService.getSecurityService(module,
        consumerKey);
      final boolean hasAccess = securityService.canPerformAction(actionName);
      resultMessage.put("hasAccess", hasAccess);
    }
    worker.sendMessage(resultMessage);
  }

  @RequestMapping(
      value = "/worker/modules/{moduleName}/users/{consumerKey}/groups/{groupName}/memberOf")
  @ResponseBody
  public void securityIsMemberOfGroup(final MapEx message, final Worker worker) {
    final String moduleName = message.getString("moduleName");
    final String consumerKey = message.getString("consumerKey");
    final String groupName = message.getString("groupName");

    final MapEx resultMessage = newResultMessage(message);
    final Module module = this.batchJobService.getModule(moduleName);
    if (module != null) {
      final SecurityService securityService = this.batchJobService.getSecurityService(module,
        consumerKey);
      final boolean inGroup = securityService.isInGroup(groupName);
      resultMessage.put("memberOfGroup", inGroup);
    }
    worker.sendMessage(resultMessage);
  }

  private void securityUserAttributes(final MapEx message, final Worker worker) {
    final String moduleName = message.getString("moduleName");
    final String consumerKey = message.getString("consumerKey");

    final MapEx resultMessage = newResultMessage(message);
    final Module module = this.batchJobService.getModule(moduleName);
    if (module != null) {
      final SecurityService securityService = this.batchJobService.getSecurityService(module,
        consumerKey);
      resultMessage.put("attributes", securityService.getUserAttributes());
    }
    worker.sendMessage(resultMessage);
  }
}
