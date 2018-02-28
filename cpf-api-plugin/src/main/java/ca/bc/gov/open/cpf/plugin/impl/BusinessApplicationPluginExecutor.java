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
package ca.bc.gov.open.cpf.plugin.impl;

import java.io.OutputStream;
import java.net.URL;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.util.StopWatch;

import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;
import ca.bc.gov.open.cpf.plugin.impl.log.AppLogUtil;
import ca.bc.gov.open.cpf.plugin.impl.module.ClassLoaderModuleLoader;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleLoader;
import ca.bc.gov.open.cpf.plugin.impl.security.MockSecurityService;
import ca.bc.gov.open.cpf.plugin.impl.security.MockSecurityServiceFactory;
import ca.bc.gov.open.cpf.plugin.impl.security.SecurityServiceFactory;

import com.revolsys.record.Record;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.record.schema.RecordDefinition;

/**
 * The BusinessApplicationPluginExecutor can be used to simulate running a
 * {@link BusinessApplicationPlugin} with the CPF. This can be used to test the
 * business application without deploying it to the CPF.
 */
public class BusinessApplicationPluginExecutor {
  private BusinessApplicationRegistry businessApplicationRegistry;

  private String consumerKey = "cpftest";

  private final SecurityServiceFactory securityServiceFactory = new MockSecurityServiceFactory();

  private Map<String, Object> testParameters = new HashMap<>();

  public BusinessApplicationPluginExecutor() {
    final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
    final ModuleLoader moduleLoader = new ClassLoaderModuleLoader(classLoader);
    this.businessApplicationRegistry = new BusinessApplicationRegistry(false, moduleLoader);
  }

  public BusinessApplicationPluginExecutor(
    final BusinessApplicationRegistry businessApplicationRegistry) {
    this.businessApplicationRegistry = businessApplicationRegistry;
  }

  public void addTestParameter(final String name, final Object value) {
    this.testParameters.put(name, value);
  }

  public void close() {
    if (this.businessApplicationRegistry != null) {
      this.businessApplicationRegistry.destroy();
      this.businessApplicationRegistry = null;
    }
  }

  /**
   * Execute a {@link BusinessApplicationPlugin} which accepts and returns
   * structured data.
   *
   * @param businessApplicationName The name of the business application to
   *          execute.
   * @param inputParameters The input parameters to the business application.
   * @return The result parameters from the business application.
   */
  public Map<String, Object> execute(final String businessApplicationName,
    final Map<String, ? extends Object> inputParameters) {
    final Object sequenceNumber = inputParameters.get("sequenceNumber");
    final PluginAdaptor plugin = getPlugin(businessApplicationName);
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    final BusinessApplication businessApplication = plugin.getApplication();
    final AppLog log = businessApplication.getLog();
    log.info("Start\tExecution");

    final Record requestRecord = getRequestRecord(businessApplicationName, inputParameters);

    if (businessApplication.getResultListProperty() != null) {
      throw new IllegalArgumentException(
        "Business Application return a list of results, use executeList method");
    }

    if (businessApplication.isPerRequestInputData()) {
      throw new IllegalArgumentException("Business Application requires an input data URL");
    }

    if (businessApplication.isPerRequestResultData()) {
      throw new IllegalArgumentException(
        "Business Application requires an result data and content type");
    }

    plugin.setParameters(requestRecord);
    plugin.execute();

    final RecordDefinition resultRecordDefinition = businessApplication.getResultRecordDefinition();
    final Map<String, Object> response = plugin.getResponseFields();
    final Record result = getResultRecord(resultRecordDefinition, response);
    result.setValue("sequenceNumber", sequenceNumber);

    AppLogUtil.info(log, "End\tExecution", stopWatch);
    return result;
  }

  /**
   * Execute a {@link BusinessApplicationPlugin} which accepts structured data
   * and returns opaque data.
   *
   * @param businessApplicationName The name of the business application to
   *          execute.
   * @param inputParameters The input parameters to the business application.
   * @param resultDataContentType The content type of the result data.
   * @param resultData The output stream that the plug-in can use to write the
   *          result data to.
   */
  public void execute(final String businessApplicationName,
    final Map<String, ? extends Object> inputParameters, final String resultDataContentType,
    final OutputStream resultData) {
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    final PluginAdaptor plugin = getPlugin(businessApplicationName);
    final BusinessApplication businessApplication = plugin.getApplication();
    final AppLog log = businessApplication.getLog();
    log.info("Start\tExecution");

    final Record requestRecord = getRequestRecord(businessApplicationName, inputParameters);
    final Map<String, Object> parameters = new HashMap<>(requestRecord);

    if (businessApplication.isPerRequestResultData()) {
      parameters.put("resultData", resultData);
      parameters.put("resultDataContentType", resultDataContentType);
    } else {
      throw new IllegalArgumentException(
        "Business Application does not support opaque result data");
    }

    plugin.setParameters(parameters);
    plugin.execute();
    if (!plugin.getResponseFields().isEmpty()) {
      throw new RuntimeException("Business Application does not support response fields");
    }
    AppLogUtil.info(log, "End\tExecution", stopWatch);
  }

  /**
   * Execute a {@link BusinessApplicationPlugin} which accepts opaque data and
   * returns structured data.
   *
   * @param businessApplicationName The name of the business application to
   *          execute.
   * @param jobParameters The global job parameters to the business application.
   * @param inputDataUrl The URL to the opaque input data.
   * @return The result parameters from the business application.
   */
  public Map<String, Object> execute(final String businessApplicationName,
    final Map<String, ? extends Object> jobParameters, final URL inputDataUrl) {
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    final PluginAdaptor plugin = getPlugin(businessApplicationName);
    final BusinessApplication businessApplication = plugin.getApplication();
    final AppLog log = businessApplication.getLog();
    log.info("Start\tExecution");

    final Record requestRecord = getRequestRecord(businessApplicationName, jobParameters);

    final Map<String, Object> parameters = new HashMap<>(requestRecord);

    if (businessApplication.getResultListProperty() == null) {
      throw new IllegalArgumentException("Business Application return a list of results");
    }
    if (businessApplication.isPerRequestInputData()) {
      parameters.put("inputDataUrl", inputDataUrl);
    } else {
      throw new IllegalArgumentException("Business Application does not support an input data URL");
    }
    if (businessApplication.isPerRequestResultData()) {
      throw new IllegalArgumentException(
        "Business Application requires an result data and content type");
    }

    plugin.setParameters(parameters);
    plugin.execute();
    final Map<String, Object> response = plugin.getResponseFields();
    final RecordDefinition resultRecordDefinition = businessApplication.getResultRecordDefinition();
    final Map<String, Object> results = getResultRecord(resultRecordDefinition, response);
    AppLogUtil.info(log, "End\tExecution", stopWatch);
    return results;
  }

  /**
   * Execute a {@link BusinessApplicationPlugin} which accepts and returns
   * opaque data.
   *
   * @param businessApplicationName The name of the business application to
   *          execute.
   * @param jobParameters The global job parameters to the business application.
   * @param inputDataUrl The URL to the opaque input data.
   * @param resultDataContentType The content type of the result data.
   * @param resultData The output stream that the plug-in can use to write the
   *          result data to.
   */
  public void execute(final String businessApplicationName,
    final Map<String, ? extends Object> jobParameters, final URL inputDataUrl,
    final String resultDataContentType, final OutputStream resultData) {
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    final PluginAdaptor plugin = getPlugin(businessApplicationName);
    final BusinessApplication businessApplication = plugin.getApplication();
    final AppLog log = businessApplication.getLog();
    log.info("Start\tExecution");

    final Record requestRecord = getRequestRecord(businessApplicationName, jobParameters);

    final Map<String, Object> parameters = new HashMap<>(requestRecord);

    if (businessApplication.isPerRequestInputData()) {
      parameters.put("inputDataUrl", inputDataUrl);
    } else {
      throw new IllegalArgumentException("Business Application does not support an input data URL");
    }

    if (businessApplication.isPerRequestResultData()) {
      parameters.put("resultData", resultData);
      parameters.put("resultDataContentType", resultDataContentType);
    } else {
      throw new IllegalArgumentException(
        "Business Application does not support opaque result data");
    }

    plugin.setParameters(parameters);
    plugin.execute();
    if (!plugin.getResponseFields().isEmpty()) {
      throw new RuntimeException("Business Application does not support response fields");
    }
    AppLogUtil.info(log, "End\tExecution", stopWatch);
  }

  /**
   * Execute a {@link BusinessApplicationPlugin} which accepts and returns
   * structured data.
   *
   * @param businessApplicationName The name of the business application to
   *          execute.
   * @param inputParameters The input parameters to the business application.
   * @return The result parameters from the business application.
   */
  public List<Map<String, Object>> executeList(final String businessApplicationName,
    final Map<String, Object> inputParameters) {
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();

    final PluginAdaptor plugin = getPlugin(businessApplicationName);
    final BusinessApplication businessApplication = plugin.getApplication();
    final AppLog log = businessApplication.getLog();
    log.info("Start\tExecution");

    final Record requestRecord = getRequestRecord(businessApplicationName, inputParameters);

    if (businessApplication.getResultListProperty() == null) {
      throw new IllegalArgumentException("Business Application does not return a list of results");
    }

    if (businessApplication.isPerRequestInputData()) {
      throw new IllegalArgumentException("Business Application requires an input data URL");
    }

    if (businessApplication.isPerRequestResultData()) {
      throw new IllegalArgumentException(
        "Business Application requires an result data and content type");
    }

    plugin.setParameters(requestRecord);
    plugin.execute();
    final List<Map<String, Object>> results = plugin.getResults();
    final RecordDefinition resultRecordDefinition = businessApplication.getResultRecordDefinition();
    final List<Map<String, Object>> resultsList = getResultList(resultRecordDefinition, results);
    AppLogUtil.info(log, "End\tExecution", stopWatch);
    return resultsList;
  }

  protected BusinessApplication getBusinessApplication(final String businessApplicationName) {
    final BusinessApplication businessApplication = this.businessApplicationRegistry
      .getBusinessApplication(businessApplicationName);
    return businessApplication;
  }

  public BusinessApplicationRegistry getBusinessApplicationRegistry() {
    return this.businessApplicationRegistry;
  }

  public String getConsumerKey() {
    return this.consumerKey;
  }

  protected PluginAdaptor getPlugin(final String businessApplicationName) {
    final PluginAdaptor plugin = this.businessApplicationRegistry
      .getBusinessApplicationPlugin(businessApplicationName);
    if (plugin == null) {
      throw new IllegalArgumentException(
        "Cannot find business application " + businessApplicationName);
    } else {
      final SecurityService securityService = getSecurityService(businessApplicationName);
      plugin.setSecurityService(securityService);
      plugin.setTestParameters(this.testParameters);
      return plugin;
    }
  }

  protected Record getRequestRecord(final String businessApplicationName,
    final Map<String, ? extends Object> parameters) {
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName);
    final RecordDefinition recordDefinition = businessApplication.getRequestRecordDefinition();
    final String jsonString = Json.toString(recordDefinition, parameters);
    return Json.toRecord(recordDefinition, jsonString);
  }

  @SuppressWarnings({
    "rawtypes", "unchecked"
  })
  protected List<Map<String, Object>> getResultList(final RecordDefinition recordDefinition,
    final List<Map<String, Object>> list) {
    if (list.isEmpty()) {
      final List results = list;
      return results;
    } else {
      final String jsonString = Json.toString(recordDefinition, list);
      final List results = Json.toRecordList(recordDefinition, jsonString);
      return results;
    }
  }

  protected Record getResultRecord(final RecordDefinition recordDefinition,
    final Map<String, Object> object) {
    final String jsonString = Json.toString(recordDefinition, object);
    return Json.toRecord(recordDefinition, jsonString);
  }

  public MockSecurityService getSecurityService(final String businessApplicationName) {
    final BusinessApplication application = getBusinessApplication(businessApplicationName);
    if (application != null) {
      final Module module = application.getModule();
      return (MockSecurityService)this.securityServiceFactory.getSecurityService(module,
        this.consumerKey);
    }
    return null;
  }

  public boolean hasResultsList(final String businessApplicationName) {
    final PluginAdaptor plugin = getPlugin(businessApplicationName);
    final BusinessApplication businessApplication = plugin.getApplication();
    return businessApplication.getResultListProperty() != null;
  }

  public void setConsumerKey(final String consumerKey) {
    this.consumerKey = consumerKey;
  }

  public void setTestModeEnabled(final String businessApplicationName, final boolean enabled) {
    if (enabled) {
      this.testParameters.put("cpfPluginTest", Boolean.TRUE);
    }
    final BusinessApplication application = this.businessApplicationRegistry
      .getBusinessApplication(businessApplicationName);
    if (application != null) {
      application.setTestModeEnabled(enabled);
    }
  }

  public void setTestParameters(final Map<String, Object> testParameters) {
    this.testParameters = new HashMap<>(testParameters);
  }

}
