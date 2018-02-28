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
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.utils.HttpClientUtils;
import org.apache.log4j.Logger;
import org.springframework.util.StopWatch;

import ca.bc.gov.open.cpf.plugin.api.RecoverableException;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.PluginAdaptor;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.security.SecurityServiceFactory;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.Maps;
import com.revolsys.collection.range.RangeSet;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.io.FileUtil;
import com.revolsys.io.LazyHttpPostOutputStream;
import com.revolsys.io.map.MapReader;
import com.revolsys.logging.Logs;
import com.revolsys.parallel.ThreadUtil;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.record.io.format.tsv.Tsv;
import com.revolsys.record.io.format.tsv.TsvWriter;
import com.revolsys.record.property.FieldProperties;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.util.MathUtil;
import com.revolsys.util.Property;

public class WorkerGroupRunnable implements Runnable {
  private long applicationExecutionTime = 0;

  private final long batchJobId;

  private BusinessApplication businessApplication;

  private final String businessApplicationName;

  private File errorFile;

  private final RangeSet errorRequests = new RangeSet();

  private TsvWriter errorWriter;

  private final String groupId;

  private final MapEx groupIdMap;

  private final WorkerHttpClient httpClient;

  private final AppLog log;

  private final String logLevel;

  private Module module;

  private final String moduleName;

  private final WorkerScheduler scheduler;

  private SecurityService securityService;

  private final RangeSet successRequests = new RangeSet();

  private final String userId;

  private final String workerId;

  public WorkerGroupRunnable(final WorkerScheduler scheduler, final MapEx groupIdMap) {
    this.scheduler = scheduler;
    this.httpClient = scheduler.getHttpClient();
    this.workerId = scheduler.getId();
    this.groupIdMap = groupIdMap;
    this.groupId = groupIdMap.getString("groupId");
    this.moduleName = groupIdMap.getString("moduleName");
    this.businessApplicationName = groupIdMap.getString("businessApplicationName");
    this.batchJobId = groupIdMap.getLong("batchJobId");
    this.userId = groupIdMap.getString("consumerKey");
    this.logLevel = groupIdMap.getString("logLevel");
    this.log = new AppLog(this.moduleName, this.businessApplicationName, this.groupId,
      this.logLevel);
  }

  public void addError(final Integer sequenceNumber, final String logPrefix, final String errorCode,
    final Throwable e) {
    this.log.error(logPrefix + errorCode, e);
    if (this.errorWriter == null) {
      this.errorFile = FileUtil.newTempFile("group-" + this.groupId, "tsv");
      this.errorWriter = Tsv.plainWriter(this.errorFile);
      this.errorWriter.write("sequenceNumber", "errorCode", "message", "trace");
    }
    String message;
    String trace = null;
    if (e == null) {
      message = logPrefix;
    } else {
      message = e.getMessage();
      final StringWriter errorOut = new StringWriter();
      e.printStackTrace(new PrintWriter(errorOut));
      trace = errorOut.toString();
    }
    this.errorWriter.write(sequenceNumber, errorCode, message, trace);
    this.errorWriter.flush();
  }

  @SuppressWarnings("unchecked")
  private void execute(final TsvWriter resultWriter, final AppLog appLog,
    final Integer requestSequenceNumber, final Object plugin, final MapEx parameters) {
    final String resultListProperty = this.businessApplication.getResultListProperty();

    final Map<String, Object> testParameters = null;
    final boolean testMode = Maps.getBool(parameters, "cpfPluginTest");
    if (testMode) {
      double testMinTime = Maps.getDouble(parameters, "cpfMinExecutionTime", -1.0);
      double testMaxTime = Maps.getDouble(parameters, "cpfMaxExecutionTime", -1.0);
      final double testMeanTime = Maps.getDouble(parameters, "cpfMeanExecutionTime", -1.0);
      final double testStandardDeviation = Maps.getDouble(parameters, "cpfStandardDeviation", -1.0);
      double executionTime;
      if (testStandardDeviation <= 0) {
        if (testMinTime < 0) {
          testMinTime = 0.0;
        }
        if (testMaxTime < testMinTime) {
          testMaxTime = testMinTime + 10;
        }
        executionTime = MathUtil.randomRange(testMinTime, testMaxTime);
      } else {
        executionTime = MathUtil.randomGaussian(testMeanTime, testStandardDeviation);
      }
      if (testMinTime >= 0 && executionTime < testMinTime) {
        executionTime = testMinTime;
      }
      if (testMaxTime > 0 && testMaxTime > testMinTime && executionTime > testMaxTime) {
        executionTime = testMaxTime;
      }
      final long milliSeconds = (long)(executionTime * 1000);
      if (milliSeconds > 1) {
        ThreadUtil.pause(milliSeconds);
      }
      this.businessApplication.pluginTestExecute(plugin);
    } else {
      this.businessApplication.pluginExecute(plugin);
    }
    Map<String, Object> customizationProperties = null;
    if (this.businessApplication.isHasCustomizationProperties()) {
      try {
        customizationProperties = (Map<String, Object>)Property.get(plugin,
          "customizationProperties");
      } catch (final Throwable e) {
        appLog.error("Unable to get customization properties", e);
      }
    }
    if (resultListProperty == null) {
      writeResult(resultWriter, plugin, parameters, customizationProperties, requestSequenceNumber,
        0, testMode);
    } else {
      final List<Object> resultObjects = Property.getSimple(plugin, resultListProperty);
      if (resultObjects == null || resultObjects.isEmpty()) {
        if (testMode) {
          final double meanNumResults = Maps.getDouble(testParameters, "cpfMeanNumResults", 3.0);
          final int numResults = (int)Math
            .round(MathUtil.randomGaussian(meanNumResults, meanNumResults / 5));
          for (int i = 0; i < numResults; i++) {
            writeResult(resultWriter, plugin, parameters, customizationProperties,
              requestSequenceNumber, i, testMode);

          }
        }
      } else {
        int i = 1;
        for (final Object resultObject : resultObjects) {
          writeResult(resultWriter, resultObject, parameters, customizationProperties,
            requestSequenceNumber, i++, false);
        }
      }
    }
  }

  /**
   * <h2>Fields</h2>
   *
   * groupId long
   * sequenceNumber long
   * perRequestResultData boolean
   *
   * errorCode String
   * errorMessage String
   * errorDebugMessage String
   *
   * pluginExecutionTime long
   * results List<MapEx>
   * logRecords List<MapEx>
   * @param resultWriter
   *
   * @param requestRecordDefinition
   * @param applicationParameters
   * @param requestParameters
   * @return The request map
   */
  protected void executeRequest(final TsvWriter resultWriter,
    final RecordDefinition requestRecordDefinition, final MapEx applicationParameters,
    final MapEx requestParameters) {
    final StopWatch requestStopWatch = new StopWatch("Request");
    requestStopWatch.start();

    final int requestSequenceNumber = requestParameters
      .getInteger(BusinessApplication.SEQUENCE_NUMBER, -1);

    if (requestSequenceNumber > -1) {
      boolean hasError = true;
      try {
        final MapEx parameters = getParameters(this.businessApplication, requestRecordDefinition,
          applicationParameters, requestParameters);
        final Object plugin = this.module.getBusinessApplicationPlugin(this.businessApplicationName,
          this.groupId, this.logLevel);
        if (plugin == null) {
          addError(requestSequenceNumber,
            "Unable to create plugin " + this.businessApplicationName + " ",
            "ERROR_PROCESSING_REQUEST", null);
        } else {
          final AppLog appLog = new AppLog(this.moduleName, this.businessApplicationName,
            this.groupId, this.logLevel);
          File resultFile = null;
          OutputStream resultData = null;
          if (this.businessApplication.isPerRequestInputData()) {
            final String inputDataUrl = this.httpClient.getUrl("/worker/workers/" + this.workerId
              + "/jobs/" + this.batchJobId + "/groups/" + this.groupId + "/inputData", null);
            parameters.put("inputDataUrl", inputDataUrl);
          }
          if (this.businessApplication.isPerRequestResultData()) {
            resultFile = FileUtil.newTempFile("app-" + this.businessApplicationName, ".bin");
            resultData = new FileOutputStream(resultFile);
            parameters.put("resultData", resultData);
          }
          try {
            setParameters(plugin, parameters);

            if (appLog.isDebugEnabled()) {
              appLog
                .debug("Start\tRequest Execution\t" + this.groupId + "\t" + requestSequenceNumber);
            }
            try {
              execute(resultWriter, appLog, requestSequenceNumber, plugin, parameters);
            } finally {
              if (appLog.isDebugEnabled()) {
                appLog
                  .debug("End\tRequest Execution\t" + this.groupId + "\t" + requestSequenceNumber);
              }
            }
            sendResultData(requestSequenceNumber, parameters, resultFile, resultData);

          } catch (final IllegalArgumentException e) {
            addError(requestSequenceNumber, "Invalid value " + e.getMessage(),
              "BAD_INPUT_DATA_VALUE", e);
          } catch (final NullPointerException e) {
            addError(requestSequenceNumber, "Invalid value, null not allowed",
              "BAD_INPUT_DATA_VALUE", e);
          } catch (final RecoverableException e) {
            addError(requestSequenceNumber, "Error processing request " + e.getMessage(),
              "RECOVERABLE_EXCEPTION", e);
          } finally {
            FileUtil.closeSilent(resultData);
            FileUtil.deleteDirectory(resultFile);
          }
          try {
            if (requestStopWatch.isRunning()) {
              requestStopWatch.stop();
            }
          } catch (final IllegalStateException e) {
          }
        }
        hasError = false;
      } catch (final Throwable e) {
        addError(requestSequenceNumber, "Error processing request", "ERROR_PROCESSING_REQUEST", e);
      }

      if (hasError) {
        this.errorRequests.add(requestSequenceNumber);
      } else {
        this.successRequests.add(requestSequenceNumber);
      }
    }
    this.applicationExecutionTime = requestStopWatch.getTotalTimeMillis();
  }

  public String getGroupId() {
    return this.groupId;
  }

  protected MapEx getParameters(final BusinessApplication businessApplication,
    final RecordDefinition requestRecordDefinition, final Map<String, Object> applicationParameters,
    final MapEx requestParameters) {
    final MapEx parameters = new LinkedHashMapEx(applicationParameters);
    parameters.putAll(requestParameters);
    if (!businessApplication.isPerRequestInputData()) {

      for (final Entry<String, Object> entry : parameters.entrySet()) {
        final String name = entry.getKey();
        final Object value = entry.getValue();
        if (value != null) {
          final FieldDefinition fieldDefinition = requestRecordDefinition.getField(name);
          if (fieldDefinition != null) {
            final Object convertedValue = fieldDefinition.toFieldValue(value);
            entry.setValue(convertedValue);
          }
        }
      }
    }
    return parameters;
  }

  /**
   * <h2>Fields</h2>
   * batchJobId long
   * groupId long
  
   * errorCode String
   * errorMessage String
   * errorDebugMessage String
  
   * results List<MapEx>
   * logRecords List<MapEx>
   * groupExecutionTime long
   * applicationExecutionTime long
   * errorCount long
   * successCount long
   */
  @Override
  public void run() {
    this.log.info("Start\tGroup Execution\t" + this.groupId);
    try {
      final File resultFile = FileUtil.newTempFile("group-" + this.groupId, ".tsv");
      final StopWatch groupStopWatch = new StopWatch("Group");
      groupStopWatch.start();
      final Long moduleTime = this.groupIdMap.getLong("moduleTime");
      this.businessApplication = this.scheduler.getBusinessApplication(this.log, this.moduleName,
        moduleTime, this.businessApplicationName);
      if (this.businessApplication == null) {
        Logs.error(this, "Business application " + this.moduleName + "."
          + this.businessApplicationName + " is not loaded groupId=" + this.groupId);
        this.scheduler.addFailedGroup(this.groupId);
        return;
      } else {
        try (
          FileOutputStream resultOut = new FileOutputStream(resultFile);
          final TsvWriter resultWriter = Tsv.plainWriter(resultOut);) {
          resultWriter.write(this.businessApplication.getResultFieldNames());
          this.businessApplication.setLogLevel(this.logLevel);
          this.module = this.businessApplication.getModule();
          if (this.businessApplication.isSecurityServiceRequired()) {
            final SecurityServiceFactory securityServiceFactory = this.scheduler
              .getSecurityServiceFactory();
            this.securityService = securityServiceFactory.getSecurityService(this.module,
              this.userId);
          }

          final RecordDefinition requestRecordDefinition = this.businessApplication
            .getRequestRecordDefinition();
          final MapEx applicationParameters = this.groupIdMap.getValue("applicationParameters");

          for (final String name : requestRecordDefinition.getFieldNames()) {
            final Object value = applicationParameters.get(name);
            if (value != null) {
              try {
                final DataType dataType = requestRecordDefinition.getFieldType(name);
                final Object convertedValue = dataType.toObject(value);
                applicationParameters.put(name, convertedValue);
              } catch (final Throwable e) {
                addError(0, "Invalid application parameter " + name + "=" + value,
                  "BAD_INPUT_DATA_VALUE", e);
              }
            }
          }
          final String groupPath = "/worker/workers/" + this.workerId + "/jobs/" + this.batchJobId
            + "/groups/" + this.groupId;
          try (
            CloseableHttpResponse groupInputResponse = this.httpClient.execute(groupPath)) {
            final HttpEntity entity = groupInputResponse.getEntity();
            try (
              InputStream requestIn = entity.getContent()) {
              try (
                MapReader requestReader = Tsv.mapReader(requestIn)) {
                for (final MapEx requestParameters : requestReader) {
                  if (ThreadUtil.isInterrupted() || !this.module.isStarted()) {
                    this.scheduler.addFailedGroup(this.groupId);
                    return;
                  }
                  executeRequest(resultWriter, requestRecordDefinition, applicationParameters,
                    requestParameters);
                }
              }
            }
          }
        }
        if (ThreadUtil.isInterrupted() || !this.module.isStarted()) {
          this.scheduler.addFailedGroup(this.groupId);
          return;
        }
        try {
          if (groupStopWatch.isRunning()) {
            groupStopWatch.stop();
          }
        } catch (final IllegalStateException e) {
        }
        final long groupExecutionTime = groupStopWatch.getTotalTimeMillis();

        final TsvWriter errorWriter = this.errorWriter;
        this.errorWriter = null;
        if (errorWriter != null) {
          errorWriter.close();
          final String errorPath = "/worker/workers/" + this.workerId + "/jobs/" + this.batchJobId
            + "/groups/" + this.groupId + "/error";
          final HttpResponse errorResponse = this.httpClient.postResource(errorPath, Tsv.MIME_TYPE,
            this.errorFile);
          if (errorResponse.getStatusLine().getStatusCode() != 200) {
            this.log.error("Error writing errors:\n" + FileUtil.getString(this.errorFile));
            this.scheduler.addFailedGroup(this.groupId);
          }
          HttpClientUtils.closeQuietly(errorResponse);
          this.errorFile = null;
        }
        if (resultFile.exists()) {
          final Map<String, Object> parameters = new HashMap<>();
          parameters.put("groupExecutedTime", groupExecutionTime);
          parameters.put("applicationExecutedTime", this.applicationExecutionTime);
          parameters.put("completedRequestRange", this.successRequests.toString());
          parameters.put("failedRequestRange", this.errorRequests.toString());
          final String path = "/worker/workers/" + this.workerId + "/jobs/" + this.batchJobId
            + "/groups/" + this.groupId + "/results";
          try (
            InputStream inputStream = new FileInputStream(resultFile)) {
            final HttpResponse response = this.httpClient.postResource(path, Tsv.MIME_TYPE,
              inputStream, parameters);
            HttpClientUtils.closeQuietly(response);

          }
        }
      }
    } catch (

    final Throwable e) {
      Logs.error(this, "Error processing group " + this.moduleName + "."
        + this.businessApplicationName + " is not loaded groupId=" + this.groupId, e);
      this.log.error("Unable to process group " + this.groupId, e);
      this.scheduler.addFailedGroup(this.groupId);
    } finally {
      this.scheduler.removeExecutingGroupId(this.groupId);
      this.log.info("End\tGroup execution\t" + this.groupId);
      FileUtil.delete(this.errorFile);
    }
  }

  protected void sendResultData(final Integer requestSequenceNumber, final MapEx parameters,
    final File resultFile, final OutputStream resultData) {
    if (resultData != null) {
      try {
        resultData.flush();
        FileUtil.closeSilent(resultData);
        final String resultDataContentType = parameters.getString("resultDataContentType");
        final String resultDataPath = "/worker/workers/" + this.workerId + "/jobs/"
          + this.batchJobId + "/groups/" + this.groupId + "/requests/" + requestSequenceNumber
          + "/resultData";

        final HttpResponse response = this.httpClient.postResource(resultDataPath,
          resultDataContentType, resultFile);

        final StatusLine status = response.getStatusLine();
        final int statusCode = status.getStatusCode();
        if (statusCode != HttpURLConnection.HTTP_OK) {
          throw new RecoverableException(
            "Result data not accepted by server " + statusCode + " " + status.getReasonPhrase());
        }
      } catch (final Throwable e) {
        this.log.error("Error sending result data", e);
        addError(requestSequenceNumber, "Unable to send result data", "RECOVERABLE_EXCEPTION",
          null);
      }
    }
  }

  public void setParameters(final Object plugin, final MapEx parameters) {
    this.businessApplication.pluginSetParameters(plugin, parameters);
    if (this.businessApplication.isPerRequestInputData()) {
      for (final String parameterName : PluginAdaptor.INPUT_DATA_PARAMETER_NAMES) {
        final Object parameterValue = parameters.get(parameterName);
        setPluginProperty(plugin, parameterName, parameterValue);
      }
    }
    if (this.businessApplication.isPerRequestResultData()) {
      final String resultDataContentType = parameters.getString("resultDataContentType");
      final String resultDataUrl = parameters.getString("resultDataUrl");
      OutputStream resultData;
      if (resultDataUrl == null) {
        resultData = (OutputStream)parameters.get("resultData");
        if (resultData == null) {
          try {
            final File file = File.createTempFile("cpf", ".out");
            resultData = new FileOutputStream(file);
            Logger.getLogger(getClass()).info("Writing result to " + file);
          } catch (final IOException e) {
            resultData = System.out;
          }
        }
      } else {
        resultData = new LazyHttpPostOutputStream(resultDataUrl, resultDataContentType);
      }
      setPluginProperty(plugin, "resultData", resultData);
      setPluginProperty(plugin, "resultDataContentType", resultDataContentType);
    }
    if (this.businessApplication.isSecurityServiceRequired()) {
      if (this.securityService == null) {
        throw new IllegalArgumentException("Security service is required");
      } else {
        setPluginProperty(plugin, "securityService", this.securityService);
      }
    }
  }

  public void setPluginProperty(final Object plugin, final String parameterName,
    Object parameterValue) {
    try {
      final RecordDefinitionImpl requestRecordDefinition = this.businessApplication
        .getRequestRecordDefinition();
      final FieldDefinition field = requestRecordDefinition.getField(parameterName);
      if (field != null) {
        parameterValue = field.toFieldValue(parameterValue);
      }
      if (parameterValue != null) {
        BeanUtils.setProperty(plugin, parameterName, parameterValue);
      }
    } catch (final Throwable t) {
      throw new IllegalArgumentException(
        this.businessApplication.getName() + "." + parameterName + " could not be set", t);
    }
  }

  private void writeResult(final TsvWriter resultWriter, final Object plugin,
    final MapEx parameters, Map<String, Object> customizationProperties,
    final Integer requestSequenceNumber, final int resultIndex, final boolean test) {
    final RecordDefinition resultRecordDefinition = this.businessApplication
      .getResultRecordDefinition();
    final List<Object> result = new ArrayList<>();
    result.add(requestSequenceNumber);
    if (resultIndex != 0) {
      result.add(resultIndex);
    }
    for (final FieldDefinition field : resultRecordDefinition.getFields()) {
      final String fieldName = field.getName();
      Object value = null;
      if (!PluginAdaptor.INTERNAL_PROPERTY_NAMES.contains(fieldName)) {
        value = this.businessApplication.pluginGetResultFieldValue(plugin, fieldName);
        if (value == null && test) {
          value = PluginAdaptor.getTestValue(field);
        } else {
          if (value instanceof com.vividsolutions.jts.geom.Geometry) {
            final com.vividsolutions.jts.geom.Geometry jtsGeometry = (com.vividsolutions.jts.geom.Geometry)value;
            final String wkt = DataTypes.toString(jtsGeometry);
            value = DataTypes.GEOMETRY.toObject(wkt);
          }
          if (value instanceof Geometry) {
            Geometry geometry = (Geometry)value;
            GeometryFactory geometryFactory = field.getProperty(FieldProperties.GEOMETRY_FACTORY);
            if (geometryFactory == GeometryFactory.DEFAULT_3D) {
              geometryFactory = geometry.getGeometryFactory();
            }
            final int srid = parameters.getInteger("resultSrid",
              geometryFactory.getCoordinateSystemId());
            final int axisCount = parameters.getInteger("resultNumAxis",
              geometryFactory.getAxisCount());
            final double scaleXY = Maps.getDouble(parameters, "resultScaleFactorXy",
              geometryFactory.getScaleXY());
            final double scaleZ = Maps.getDouble(parameters, "resultScaleFactorZ",
              geometryFactory.getScaleZ());

            geometryFactory = GeometryFactory.fixed(srid, axisCount, scaleXY, scaleXY, scaleZ);
            geometry = geometryFactory.geometry(geometry);
            if (geometry.getCoordinateSystemId() == 0) {
              throw new IllegalArgumentException(
                "Geometry does not have a coordinate system (SRID) specified");
            }
            final Boolean validateGeometry = field.getProperty(FieldProperties.VALIDATE_GEOMETRY);
            if (validateGeometry == true) {
              if (!geometry.isValid()) {
                throw new IllegalArgumentException("Geometry is not valid for"
                  + this.businessApplication.getName() + "." + fieldName);
              }
            }
            value = geometry;
          }
        }
        result.add(value);
      }
    }
    if (resultIndex != 0 && this.businessApplication.isHasResultListCustomizationProperties()) {
      final Map<String, Object> resultListProperties = Property.get(plugin,
        "customizationProperties");
      if (resultListProperties != null) {
        customizationProperties = Maps.newLinkedHash(customizationProperties);
        customizationProperties.putAll(resultListProperties);
      }
    }
    if (Property.hasValue(customizationProperties)) {
      result.add(Json.toString(customizationProperties));
    }
    resultWriter.write(result);
  }
}
