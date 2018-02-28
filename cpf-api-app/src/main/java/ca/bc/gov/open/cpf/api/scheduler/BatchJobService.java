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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.net.URI;
import java.net.URISyntaxException;
import java.sql.BatchUpdateException;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.mail.internet.MimeMessage;
import javax.servlet.ServletOutputStream;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.websocket.Session;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.StatusLine;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.StringEntity;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.DefaultHttpClient;
import org.springframework.http.MediaType;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.util.StopWatch;

import ca.bc.gov.open.cpf.api.controller.CpfConfig;
import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobResult;
import ca.bc.gov.open.cpf.api.domain.BatchJobStatus;
import ca.bc.gov.open.cpf.api.domain.Common;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;
import ca.bc.gov.open.cpf.api.security.service.AuthorizationService;
import ca.bc.gov.open.cpf.api.security.service.AuthorizationServiceUserSecurityServiceFactory;
import ca.bc.gov.open.cpf.api.web.controller.DatabaseJobController;
import ca.bc.gov.open.cpf.api.web.controller.JobController;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;
import ca.bc.gov.open.cpf.plugin.impl.PluginAdaptor;
import ca.bc.gov.open.cpf.plugin.impl.log.AppLogUtil;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEvent;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEventListener;
import ca.bc.gov.open.cpf.plugin.impl.security.SecurityServiceFactory;

import com.revolsys.collection.list.Lists;
import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.Maps;
import com.revolsys.collection.map.NamedLinkedHashMapEx;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.geometry.operation.valid.IsValidOp;
import com.revolsys.identifier.Identifier;
import com.revolsys.io.FileUtil;
import com.revolsys.io.IoConstants;
import com.revolsys.io.IoFactory;
import com.revolsys.io.map.MapReader;
import com.revolsys.io.map.MapWriter;
import com.revolsys.io.map.MapWriterFactory;
import com.revolsys.logging.Logs;
import com.revolsys.parallel.ThreadUtil;
import com.revolsys.parallel.channel.ClosedException;
import com.revolsys.parallel.channel.NamedChannelBundle;
import com.revolsys.record.Record;
import com.revolsys.record.Records;
import com.revolsys.record.io.RecordWriter;
import com.revolsys.record.io.RecordWriterFactory;
import com.revolsys.record.io.format.csv.Csv;
import com.revolsys.record.io.format.html.XhtmlMapWriter;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.record.io.format.kml.Kml22Constants;
import com.revolsys.record.io.format.tsv.Tsv;
import com.revolsys.record.io.format.tsv.TsvWriter;
import com.revolsys.record.property.FieldProperties;
import com.revolsys.record.query.Condition;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.spring.resource.PathResource;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.SendToChannelAfterCommit;
import com.revolsys.transaction.Transaction;
import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.util.Dates;
import com.revolsys.util.Property;
import com.revolsys.util.UrlUtil;

public class BatchJobService implements ModuleEventListener {
  private static final String CONTENT_TYPE_JSON = MediaType.APPLICATION_JSON.toString();

  private static final String DATE_TIME_FORMAT = "yyyy-MM-dd HH:mm:ss";

  private static final ArrayList<String> JOB_TSV_FIELD_NAMES = Lists.newArray(
    BatchJob.BUSINESS_APPLICATION_NAME, BatchJob.BATCH_JOB_ID, BatchJob.USER_ID,
    BatchJob.WHEN_CREATED, BatchJob.COMPLETED_TIMESTAMP, BatchJob.NUM_SUBMITTED_REQUESTS,
    BatchJob.FAILED_REQUEST_RANGE, BatchJob.INPUT_DATA_CONTENT_TYPE,
    BatchJob.RESULT_DATA_CONTENT_TYPE);

  private static final Pattern RANGE_PATTERN = Pattern.compile("bytes=(\\d+)?-(\\d+)?");

  private static long capacityErrorTime;

  public static void error(final AppLog log, final String message, final Throwable e) {
    if (log == null) {
      Logs.error(BatchJobService.class, message, e);
    } else {
      log.error(message, e);
    }
  }

  public static Map<String, Object> getGroupLogData(final BatchJobRequestExecutionGroup group) {
    final String groupId = group.getId();
    final Identifier batchJobId = group.getBatchJobId();
    final String businessApplicationName = group.getBusinessApplicationName();
    final Map<String, Object> appLogData = new LinkedHashMap<>();
    appLogData.put("businessApplicationName", businessApplicationName);
    appLogData.put("batchJobId", batchJobId);
    appLogData.put("groupId", groupId);
    return appLogData;
  }

  public static boolean isDatabaseResourcesException(final Throwable e) {
    if (e instanceof BatchUpdateException) {
      final BatchUpdateException batchException = (BatchUpdateException)e;
      for (SQLException sqlException = batchException
        .getNextException(); sqlException != null; sqlException = batchException
          .getNextException()) {
        if (isDatabaseResourcesException(sqlException)) {
          return true;
        }
      }
    } else if (e instanceof SQLException) {
      final SQLException sqlException = (SQLException)e;
      final int errorCode = sqlException.getErrorCode();
      boolean isCapacityError = false;
      if (errorCode == 1653) {
        isCapacityError = true;
      } else if (errorCode == 1688) {
        isCapacityError = true;
      } else if (errorCode == 1691) {
        isCapacityError = true;
      } else {
        final String sqlState = ((SQLException)e).getSQLState();
        if (sqlState != null && sqlState.startsWith("53")) {
          isCapacityError = true;
        }
      }
      if (isCapacityError) {
        capacityErrorTime = System.currentTimeMillis();
      }
      return isCapacityError;
    } else {
      final Throwable cause = e.getCause();
      if (cause != null) {
        return isDatabaseResourcesException(cause);
      }
    }
    return false;
  }

  public static void setStructuredInputDataValue(final String sridString,
    final Map<String, Object> requestParemeters, final FieldDefinition field, Object parameterValue,
    final boolean setValue) {
    final DataType dataType = field.getDataType();
    final Class<?> dataClass = dataType.getJavaClass();
    if (Geometry.class.isAssignableFrom(dataClass)) {
      if (parameterValue != null) {
        final GeometryFactory geometryFactory = field.getProperty(FieldProperties.GEOMETRY_FACTORY);
        Geometry geometry;
        if (parameterValue instanceof Geometry) {

          geometry = (Geometry)parameterValue;
          if (geometry.getCoordinateSystemId() == 0 && Property.hasValue(sridString)) {
            final int srid = Integer.parseInt(sridString);
            final GeometryFactory sourceGeometryFactory = GeometryFactory.floating3d(srid);
            geometry = sourceGeometryFactory.geometry(geometry);
          }
        } else {
          String wkt = parameterValue.toString();
          if (Property.hasValue(wkt)) {
            wkt = wkt.trim();
          }
          if (wkt.startsWith("http")) {
            wkt = UrlUtil.getContent(wkt + "/feature.wkt?srid=3005");
            if (!wkt.startsWith("SRID")) {
              wkt = "SRID=3005;" + wkt;
            }
          }
          try {
            if (Property.hasValue(sridString)) {
              final int srid = Integer.parseInt(sridString);
              final GeometryFactory sourceGeometryFactory = GeometryFactory.floating3d(srid);
              geometry = sourceGeometryFactory.geometry(wkt, false);
            } else {
              geometry = geometryFactory.geometry(wkt, false);
            }
          } catch (final Throwable t) {
            throw new IllegalArgumentException("invalid WKT geometry", t);
          }
        }
        if (geometryFactory != GeometryFactory.DEFAULT_3D) {
          geometry = geometryFactory.geometry(geometry);
        }
        final Boolean validateGeometry = field.getProperty(FieldProperties.VALIDATE_GEOMETRY);
        if (geometry.getCoordinateSystemId() == 0) {
          throw new IllegalArgumentException("does not have a coordinate system (SRID) specified");
        }
        if (validateGeometry == true) {
          final IsValidOp validOp = new IsValidOp(geometry);
          if (!validOp.isValid()) {
            throw new IllegalArgumentException(validOp.getValidationError().getMessage());
          }
        }
        parameterValue = geometry;
      }
    } else if (Number.class.isAssignableFrom(dataClass)) {
      if (!(parameterValue instanceof Number)) {
        parameterValue = new BigDecimal(parameterValue.toString().trim());
      }
    }
    if (setValue) {
      requestParemeters.put(field.getName(), parameterValue);
    }
  }

  @Resource(name = "configPropertyLoader")
  private ConfigPropertyLoader configPropertyLoader;

  private final int largestGroupResultCount = 0;

  @Resource(name = "cpfConfig")
  private CpfConfig config;

  private final AtomicInteger groupResultCount = new AtomicInteger(0);

  private int daysToKeepOldJobs = 7;

  private boolean jsonpEnabled = true;

  private JobController jobController;

  private boolean compressData = false;

  private AuthorizationService authorizationService;

  private BusinessApplicationRegistry businessApplicationRegistry;

  /** The email address messages are sent from. */
  private String fromEmail;

  private NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = new NamedChannelBundle<>();

  /** The class used to send email. */
  private JavaMailSender mailSender;

  private long maxWorkerWaitTime = 60 * 1000;

  private BatchJobPostProcess postProcess;

  private BatchJobPreProcess preProcess;

  private boolean running;

  private BatchJobScheduler scheduler;

  private SecurityServiceFactory securityServiceFactory;

  private Map<String, String> userClassBaseUrls;

  private final Map<String, Integer> connectedWorkerCounts = new HashMap<>();

  private CpfDataAccessObject dataAccessObject;

  private final Map<String, Worker> workersById = new TreeMap<>();

  private final Map<String, Worker> workersByKey = new TreeMap<>();

  private final Set<Identifier> preprocesedJobIds = new HashSet<>();

  private long timeoutForCapacityErrors = 5 * 60 * 1000;

  @Resource
  private StatisticsService statisticsService;

  private RecordStore recordStore;

  private final Object jobTsvFileSync = new Object();

  private File appLogDirectory;

  protected void addPreProcessedJobId(final Identifier batchJobId) {
    synchronized (this.preprocesedJobIds) {
      this.preprocesedJobIds.add(batchJobId);
    }
  }

  public boolean cancelBatchJob(final Identifier batchJobId) {
    final boolean cancelled = false;
    synchronized (this.preprocesedJobIds) {
      this.preprocesedJobIds.remove(batchJobId);
    }
    final BatchJob batchJob = getBatchJob(batchJobId);
    if (batchJob != null) {
      if (batchJob.cancelJob(this, this.scheduler)) {
        synchronized (this.workersById) {
          for (final Worker worker : this.workersById.values()) {
            worker.cancelBatchJob(batchJobId);
          }
          final NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = this.groupsToSchedule;
          if (groupsToSchedule != null) {
            groupsToSchedule.notifyReaders();
          }
        }
        return true;
      }
    }
    return cancelled;
  }

  public void cancelGroup(final Worker worker, final String groupId) {
    if (groupId != null) {
      final BatchJobRequestExecutionGroup group = worker.removeExecutingGroup(groupId);
      if (group != null) {
        final BusinessApplication businessApplication = group.getBusinessApplication();
        final AppLog log = businessApplication.getLog();
        log.info("Reschedule\tGroup execution\tgroupId=" + groupId);

        group.resetId();
        rescheduleGroup(group);
      }
    }
  }

  protected boolean containsPreProcessedJobId(final Identifier batchJobId) {
    synchronized (this.preprocesedJobIds) {
      return this.preprocesedJobIds.contains(batchJobId);
    }
  }

  public void deleteJob(final Identifier batchJobId) {
    cancelBatchJob(batchJobId);
    this.jobController.deleteJob(batchJobId);
  }

  @PreDestroy
  public void destory() {
    this.running = false;
    this.authorizationService = null;
    this.businessApplicationRegistry = null;
    this.connectedWorkerCounts.clear();
    this.dataAccessObject = null;
    this.recordStore = null;
    if (this.groupsToSchedule != null) {
      this.groupsToSchedule.close();
      this.groupsToSchedule = null;
    }
    this.mailSender = null;
    if (this.postProcess != null) {
      this.postProcess.getIn().writeDisconnect();
      this.postProcess = null;
    }
    this.preprocesedJobIds.clear();
    if (this.preProcess != null) {
      this.preProcess.getIn().writeDisconnect();
      this.preProcess = null;
    }
    if (this.scheduler != null) {
      this.scheduler.getIn().writeDisconnect();
      this.scheduler = null;
    }
    this.securityServiceFactory = null;
    this.userClassBaseUrls.clear();
    this.workersById.clear();
    this.jobController = null;
  }

  public void downloadBatchJobResult(final HttpServletRequest request,
    final HttpServletResponse response, final Identifier batchJobIdentifier, final int resultId,
    final Record batchJobResult) throws IOException {
    final String resultDataUrl = batchJobResult.getValue(BatchJobResult.RESULT_DATA_URL);
    if (resultDataUrl != null) {
      response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      response.setHeader("Location", resultDataUrl);
    } else {
      final String etag = Integer.toString(resultId);
      long size = getBatchJobResultSize(batchJobIdentifier, resultId);
      long fromIndex = 0;
      long toIndex = size - 1;
      boolean hasRange = false;

      String jsonCallback = null;
      final String resultDataContentType = batchJobResult
        .getValue(BatchJobResult.RESULT_DATA_CONTENT_TYPE);
      if (CONTENT_TYPE_JSON.equals(resultDataContentType)) {
        jsonCallback = HttpServletUtils.getParameter("callback");
        if (Property.hasValue(jsonCallback)) {
          size += 3 + jsonCallback.length();
        }
      }

      final String range = request.getHeader("Range");
      if (jsonCallback == null && range != null) {
        final Matcher matcher = RANGE_PATTERN.matcher(range);
        if (matcher.matches()) {
          hasRange = true;
          response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);
          final String from = matcher.group(1);
          if (Property.hasValue(from)) {
            fromIndex = Integer.parseInt(from);
          }
          final String to = matcher.group(2);
          if (Property.hasValue(to)) {
            toIndex = Integer.parseInt(to);
          }
        }
      }

      if (jsonCallback == null) {
        response.setHeader("Accept-Ranges", "bytes");
        response.setHeader("Content-Length", Long.toString(size));
        final java.util.Date lastModified = batchJobResult.getValue(Common.WHEN_CREATED);

        final String lastModifiedString = Dates.format("EEE, dd MMM yyyy HH:mm:ss z", lastModified);
        response.setHeader("Last-Modified", lastModifiedString);
        response.setHeader("ETag", etag);
        response.setHeader("Connection", "keep-alive");
        response.setHeader("Content-Range", "bytes " + fromIndex + "-" + toIndex + "/" + size);
      }
      try (
        final InputStream in = getBatchJobResultData(batchJobIdentifier, resultId, batchJobResult,
          hasRange, fromIndex, toIndex)) {
        response.setContentType(resultDataContentType);
        final RecordWriterFactory writerFactory = IoFactory
          .factoryByMediaType(RecordWriterFactory.class, resultDataContentType);
        if (writerFactory != null) {
          final String fileExtension = writerFactory.getFileExtension(resultDataContentType);
          final String fileName = "job-" + batchJobIdentifier + "-result-" + resultId + "."
            + fileExtension;
          response.setHeader("Content-Disposition",
            "attachment; filename=" + fileName + ";size=" + size);
        }
        final ServletOutputStream out = response.getOutputStream();
        if (Property.hasValue(jsonCallback)) {
          out.write(jsonCallback.getBytes());
          out.write("(".getBytes());
          FileUtil.copy(in, out);
          out.write(");".getBytes());
        } else if (hasRange) {
          FileUtil.copy(in, out, toIndex - fromIndex + 1);
        } else {
          FileUtil.copy(in, out);
        }
      }
    }
  }

  public AppLog getAppLog(final String businessApplicationName) {
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName);
    if (businessApplication == null) {
      return new AppLog(businessApplicationName);
    } else {
      return businessApplication.getLog();
    }
  }

  public File getAppLogDirectory() {
    return this.appLogDirectory;
  }

  public AuthorizationService getAuthorizationService() {
    return this.authorizationService;
  }

  public String getBaseUrl() {
    return this.config.getBaseUrl();
  }

  public synchronized BatchJob getBatchJob(final Identifier batchJobId) {
    return this.dataAccessObject.getBatchJob(batchJobId);
  }

  public BatchJob getBatchJob(final Identifier batchJobId, final String consumerKey) {
    final BatchJob batchJob = getBatchJob(batchJobId);
    if (batchJob == null) {
      return null;
    } else {
      final String userId = batchJob.getValue(Common.WHO_CREATED);
      if (consumerKey.equals(userId)) {
        return batchJob;
      } else {
        return null;
      }
    }
  }

  public BatchJob getBatchJob(final Record batchJob) {
    return this.dataAccessObject.getBatchJob(batchJob);
  }

  public BatchJobRequestExecutionGroup getBatchJobRequestExecutionGroup(final String workerId,
    final String groupId) {
    final Worker worker = getWorker(workerId);
    if (worker == null) {
      return null;
    } else {
      return worker.getExecutingGroup(groupId);
    }
  }

  public InputStream getBatchJobResultData(final Identifier batchJobId, final int sequenceNumber,
    final Record batchJobResult, final boolean hasRange, final long fromIndex, final long toIndex) {
    if (hasRange) {
      return this.jobController.getJobResultStream(batchJobId, sequenceNumber, fromIndex, toIndex);
    } else {
      return this.jobController.getJobResultStream(batchJobId, sequenceNumber);
    }
  }

  public long getBatchJobResultSize(final Identifier batchJobId, final int sequenceNumber) {
    return this.jobController.getJobResultSize(batchJobId, sequenceNumber);
  }

  public BusinessApplication getBusinessApplication(final String name) {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getBusinessApplication(name);
    }
  }

  public BusinessApplication getBusinessApplication(final String name, final String version) {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getBusinessApplication(name);
    }
  }

  public List<String> getBusinessApplicationNames() {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getBusinessApplicationNames();
    }
  }

  public PluginAdaptor getBusinessApplicationPlugin(final BusinessApplication businessApplication) {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getBusinessApplicationPlugin(businessApplication);
    }
  }

  public PluginAdaptor getBusinessApplicationPlugin(final String businessApplicationName,
    final String businessApplicationVersion) {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getBusinessApplicationPlugin(businessApplicationName,
        businessApplicationVersion);
    }
  }

  public BusinessApplicationRegistry getBusinessApplicationRegistry() {
    return this.businessApplicationRegistry;
  }

  public List<BusinessApplication> getBusinessApplications() {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getBusinessApplications();
    }
  }

  public ConfigPropertyLoader getConfigPropertyLoader() {
    return this.configPropertyLoader;
  }

  public CpfDataAccessObject getDataAccessObject() {
    return this.dataAccessObject;
  }

  public int getDaysToKeepOldJobs() {
    return this.daysToKeepOldJobs;
  }

  public java.sql.Date getExpiryDate(final Date completionTimestamp) {
    if (completionTimestamp == null) {
      return null;
    } else {
      final int dayInMilliseconds = 1000 * 60 * 60 * 24;
      final long timeXDaysAgo = completionTimestamp.getTime()
        + this.daysToKeepOldJobs * dayInMilliseconds;
      return new java.sql.Date(timeXDaysAgo);
    }
  }

  /**
   * Get the email address messages are sent from.
   *
   * @return The email address messages are sent from.
   */
  public String getFromEmail() {
    return this.fromEmail;
  }

  public GeometryFactory getGeometryFactory(final GeometryFactory geometryFactory,
    final Map<String, ? extends Object> parameters) {
    if (geometryFactory == null) {
      return null;
    } else {
      final int srid = Maps.getInteger(parameters, "resultSrid",
        geometryFactory.getCoordinateSystemId());
      final int axisCount = Maps.getInteger(parameters, "resultNumAxis",
        geometryFactory.getAxisCount());
      final double scaleXY = Maps.getDouble(parameters, "resultScaleFactorXy",
        geometryFactory.getScaleXY());
      final double scaleZ = Maps.getDouble(parameters, "resultScaleFactorZ",
        geometryFactory.getScaleZ());
      return GeometryFactory.fixed(srid, axisCount, scaleXY, scaleXY, scaleZ);
    }
  }

  public int getGroupResultCount() {
    return this.groupResultCount.get();
  }

  public JobController getJobController() {
    return this.jobController;
  }

  public JobController getJobController(final Identifier batchJobId) {
    return this.jobController;
  }

  public int getLargestGroupResultCount() {
    return this.largestGroupResultCount;
  }

  /**
   * Get the class used to send email.
   *
   * @return The class used to send email.
   */
  public JavaMailSender getMailSender() {
    return this.mailSender;
  }

  public Module getModule(final String moduleName) {
    if (this.businessApplicationRegistry == null) {
      return null;
    } else {
      return this.businessApplicationRegistry.getModule(moduleName);
    }
  }

  public Map<String, Object> getNextBatchJobRequestExecutionGroup(final String workerId,
    final int maxMessageId, final List<String> moduleNames) {
    final long startTime = System.currentTimeMillis();
    final long endTime = startTime + this.maxWorkerWaitTime;
    final Map<String, Object> response = new HashMap<>();
    if (this.running) {
      BatchJobRequestExecutionGroup group = null;
      try {
        final long waitTime = Math.min(10000, endTime - startTime);
        if (waitTime > 0) {
          final NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = this.groupsToSchedule;
          if (groupsToSchedule == null) {
            return Collections.emptyMap();
          } else {
            group = groupsToSchedule.read(waitTime, moduleNames);
          }
        }
      } catch (final ClosedException e) {
      }
      if (this.running && group != null && !group.isCancelled()) {
        final BusinessApplication businessApplication = group.getBusinessApplication();
        if (businessApplication == null) {
          rescheduleGroup(group);
        } else {
          final String businessApplicationName = businessApplication.getName();
          final Module module = businessApplication.getModule();
          if (module == null || !module.isStarted()) {
            scheduleGroup(group);
          } else {
            final String moduleName = group.getModuleName();
            final long moduleStartTime = module.getStartedTime();
            final Worker worker = getWorker(workerId);
            if (worker == null || moduleStartTime == -1 || !module.isStarted()) {
              scheduleGroup(group);
            } else {
              response.put("workerId", workerId);
              response.put("moduleName", moduleName);
              response.put("moduleTime", moduleStartTime);
              response.put("businessApplicationName", businessApplicationName);
              response.put("logLevel", businessApplication.getLogLevel());

              group.setExecutionStartTime(System.currentTimeMillis());
              final String groupId = group.getId();
              final Identifier batchJobId = group.getBatchJobId();
              final String baseId = group.getBaseId();

              response.put("batchJobId", batchJobId);
              response.put("baseId", baseId);
              response.put("groupId", groupId);
              response.put("applicationParameters", group.getBusinessApplicationParameterMap());
              if (businessApplication.isPerRequestResultData()) {
                response.put("resultDataContentType", group.getResultDataContentType());
              }
              final AppLog log = businessApplication.getLog();
              log.info("Start\tGroup execution\tgroupId=" + groupId + "\tworkerId=" + workerId);
              response.put("consumerKey", group.getconsumerKey());
              worker.addExecutingGroup(moduleName, moduleStartTime, group);
            }
          }
        }
      }
    }
    return response;
  }

  public BatchJobPostProcess getPostProcess() {
    return this.postProcess;
  }

  public BatchJobPreProcess getPreProcess() {
    return this.preProcess;
  }

  public RecordStore getRecordStore() {
    return this.recordStore;
  }

  public SecurityService getSecurityService(final Module module, final String consumerKey) {
    return this.securityServiceFactory.getSecurityService(module, consumerKey);
  }

  public StatisticsService getStatisticsService() {
    return this.statisticsService;
  }

  public long getTimeoutForCapacityErrors() {
    return this.timeoutForCapacityErrors;
  }

  public Map<String, String> getUserClassBaseUrls() {
    return this.userClassBaseUrls;
  }

  public Worker getWorker(final String workerId) {
    synchronized (this.workersById) {
      final Worker worker = this.workersById.get(workerId);
      return worker;
    }
  }

  public Worker getWorkerByKey(final String workerKey) {
    synchronized (this.workersByKey) {
      final Worker worker = this.workersByKey.get(workerKey);
      return worker;
    }
  }

  public List<Worker> getWorkers() {
    return new ArrayList<>(this.workersByKey.values());
  }

  @PostConstruct
  public void init() {
    this.running = true;
    this.securityServiceFactory = new AuthorizationServiceUserSecurityServiceFactory(
      this.authorizationService);
    this.businessApplicationRegistry.addModuleEventListener(this.securityServiceFactory);
    this.recordStore = this.dataAccessObject.getRecordStore();
    Logs.info(this, "Started");
  }

  public boolean isCompressData() {
    return this.compressData;
  }

  public boolean isHasTablespaceError() {
    if (System.currentTimeMillis() < capacityErrorTime + this.timeoutForCapacityErrors) {
      return true;
    } else {
      return false;
    }
  }

  public boolean isJsonpEnabled() {
    return this.jsonpEnabled;
  }

  public boolean isRunning() {
    return this.running;
  }

  @Override
  public void moduleChanged(final ModuleEvent event) {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        final String action = event.getAction();
        final Module module = event.getModule();
        final String moduleName = module.getName();
        synchronized (module) {
          final List<String> businessApplicationNames = module.getBusinessApplicationNames();
          for (final String businessApplicationName : businessApplicationNames) {
            synchronized (businessApplicationName.intern()) {
              if (action.equals(ModuleEvent.STOP)) {
                final NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = this.groupsToSchedule;
                if (groupsToSchedule != null) {
                  groupsToSchedule.remove(businessApplicationName);
                }
                this.dataAccessObject.clearBatchJobs(businessApplicationName);
              } else if (action.equals(ModuleEvent.START)) {
                this.dataAccessObject.clearBatchJobs(businessApplicationName);
                resetProcessingBatchJobs(moduleName, businessApplicationName);
                resetCreatingRequestsBatchJobs(moduleName, businessApplicationName);
                resetCreatingResultsBatchJobs(moduleName, businessApplicationName);
                scheduleFromDatabase(moduleName, businessApplicationName);
                if (this.preProcess != null) {
                  this.preProcess.scheduleFromDatabase(moduleName, businessApplicationName);
                }
                if (this.postProcess != null) {
                  this.postProcess.scheduleFromDatabase(moduleName, businessApplicationName);
                }
              }
            }
          }
        }
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    }
  }

  public void newBatchJobResult(final Identifier batchJobId, final String resultDataType,
    final String contentType, final Object data, final int sequenceNumber) {
    if (data != null) {
      if (data instanceof File) {
        final File file = (File)data;
        if (file.length() == 0) {
          return;
        }
      }
      try {
        final Record result = this.dataAccessObject.newRecord(BatchJobResult.BATCH_JOB_RESULT);
        result.setValue(BatchJobResult.SEQUENCE_NUMBER, sequenceNumber);
        result.setValue(BatchJobResult.BATCH_JOB_ID, batchJobId);
        result.setValue(BatchJobResult.BATCH_JOB_RESULT_TYPE, resultDataType);
        result.setValue(BatchJobResult.RESULT_DATA_CONTENT_TYPE, contentType);
        this.jobController.setJobResult(batchJobId, sequenceNumber, contentType, data);
        this.dataAccessObject.write(result);
      } catch (final Throwable e) {
        throw new RuntimeException("Unable to save result data", e);
      }
    }
  }

  public void newBatchJobResultOpaque(final Identifier batchJobId, final int sequenceNumber,
    final String contentType, final Object data) {
    final Record result = this.dataAccessObject.newRecord(BatchJobResult.BATCH_JOB_RESULT);
    result.setValue(BatchJobResult.SEQUENCE_NUMBER, sequenceNumber);
    result.setValue(BatchJobResult.BATCH_JOB_ID, batchJobId);
    result.setValue(BatchJobResult.BATCH_JOB_RESULT_TYPE, BatchJobResult.OPAQUE_RESULT_DATA);
    result.setValue(BatchJobResult.RESULT_DATA_CONTENT_TYPE, contentType);
    this.jobController.setJobResult(batchJobId, sequenceNumber, contentType, data);
    this.dataAccessObject.write(result);
  }

  protected com.revolsys.io.Writer<Record> newStructuredResultWriter(final BatchJob batchJob,
    final Identifier batchJobId, final BusinessApplication application,
    final File structuredResultFile, final RecordDefinition resultRecordDefinition,
    final String resultFormat) {
    final Map<String, ? extends Object> businessApplicationParameters = batchJob
      .getBusinessApplicationParameters();
    final GeometryFactory geometryFactory = getGeometryFactory(
      resultRecordDefinition.getGeometryFactory(), businessApplicationParameters);
    final String title = "Job " + batchJobId + " Result";

    final PathResource resource = new PathResource(structuredResultFile);
    return newStructuredResultWriter(resource, application, resultRecordDefinition, resultFormat,
      title, geometryFactory);
  }

  public RecordWriter newStructuredResultWriter(
    final com.revolsys.spring.resource.Resource resource, final BusinessApplication application,
    final RecordDefinition resultRecordDefinition, final String resultFormat, final String title,
    final GeometryFactory geometryFactory) {
    final RecordWriterFactory writerFactory = IoFactory
      .factoryByMediaType(RecordWriterFactory.class, resultFormat.trim());
    if (writerFactory == null) {
      throw new IllegalArgumentException("Unsupported result content type: " + resultFormat);
    } else {
      final RecordWriter recordWriter = writerFactory.newRecordWriter(resultRecordDefinition,
        resource);
      recordWriter.setProperty(Kml22Constants.STYLE_URL_PROPERTY,
        this.getBaseUrl() + "/kml/defaultStyle.kml#default");
      recordWriter.setProperty(IoConstants.TITLE_PROPERTY, title);
      recordWriter.setProperty("htmlCssStyleUrl", this.getBaseUrl() + "/css/default.css");

      recordWriter.setProperty(IoConstants.GEOMETRY_FACTORY, geometryFactory);
      recordWriter.setProperties(application.getProperties());
      return recordWriter;
    }
  }

  public Transaction newTransaction(final Propagation propagation) {
    return this.dataAccessObject.newTransaction(propagation);
  }

  public void postProcess(final Identifier batchJobId) {
    if (this.postProcess != null) {
      SendToChannelAfterCommit.send(this.postProcess.getIn(), batchJobId);
    }
  }

  public boolean postProcessBatchJob(final Identifier batchJobId, final long time,
    final long lastChangedTime) {
    AppLog log = null;
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try (
        RecordWriter writer = this.recordStore.newRecordWriter();) {
        final BatchJob batchJob = getBatchJob(batchJobId);
        if (batchJob != null && !batchJob.isCancelled()) {
          final StopWatch stopWatch = new StopWatch();
          stopWatch.start();

          final long numRequests = batchJob.getInteger(BatchJob.NUM_SUBMITTED_REQUESTS);
          final String businessApplicationName = batchJob
            .getValue(BatchJob.BUSINESS_APPLICATION_NAME);
          final BusinessApplication businessApplication = getBusinessApplication(
            businessApplicationName);
          if (businessApplication == null) {
            return false;
          } else {
            log = businessApplication.getLog();
          }
          if (log.isInfoEnabled()) {
            log.info("Start\tJob post-process\tbatchJobId=" + batchJobId);
          }
          final Map<String, Object> postProcessScheduledStatistics = new HashMap<>();
          postProcessScheduledStatistics.put("postProcessScheduledJobsTime",
            time - lastChangedTime);
          postProcessScheduledStatistics.put("postProcessScheduledJobsCount", 1);
          this.statisticsService.addStatistics(businessApplication, postProcessScheduledStatistics);

          if (!businessApplication.isPerRequestResultData()) {
            postProcessCreateStructuredResults(businessApplication, log, batchJob, batchJobId);
          }
          postProcessCreateErrorResults(log, batchJob, batchJobId);

          if (batchJob.setStatus(this, BatchJobStatus.CREATING_REQUESTS,
            BatchJobStatus.RESULTS_CREATED)
            || batchJob.setStatus(this, BatchJobStatus.CREATING_RESULTS,
              BatchJobStatus.RESULTS_CREATED)) {

            final Timestamp now = batchJob.getValue(BatchJob.WHEN_STATUS_CHANGED);
            batchJob.setValue(BatchJob.COMPLETED_TIMESTAMP, now);
            batchJob.setValue(Common.WHEN_UPDATED, now);

            final String username = CpfDataAccessObject.getUsername();
            batchJob.setValue(Common.WHO_UPDATED, username);

            synchronized (this.jobTsvFileSync) {
              final File jobsDirectory = FileUtil.getDirectory(this.appLogDirectory, "jobs");

              final String dateString = Dates.format("yyyy-MM-dd", now);
              final File jobsFile = FileUtil.getFile(jobsDirectory, "jobs-" + dateString + ".tsv");
              final boolean newFile = !jobsFile.exists();
              try (
                Writer jobsWriter = new FileWriter(jobsFile, true);
                TsvWriter jobsTsvWriter = Tsv.plainWriter(jobsWriter)) {
                if (newFile) {
                  jobsTsvWriter.write(JOB_TSV_FIELD_NAMES);
                }
                final List<Object> values = batchJob.getValues(JOB_TSV_FIELD_NAMES);
                jobsTsvWriter.write(values);
              } catch (final Throwable e) {
                Logs.error(this, "Unable to log job to:" + jobsFile, e);
              }
            }
            batchJob.update();
            sendNotification(batchJobId, batchJob);
          }
          final int numCompletedRequests = batchJob.getNumCompletedRequests();
          final int numFailedRequests = batchJob.getNumFailedRequests();
          if (log.isInfoEnabled()) {
            AppLogUtil.infoAfterCommit(log, "End\tJob post-process\tbatchJobId=" + batchJobId,
              stopWatch);
            AppLogUtil.infoAfterCommit(log, "End\tJob execution\tbatchJobId=" + batchJobId);
          }
          final Timestamp whenCreated = batchJob.getValue(Common.WHEN_CREATED);

          final Map<String, Object> postProcessStatistics = new HashMap<>();

          postProcessStatistics.put("postProcessedTime", stopWatch);
          postProcessStatistics.put("postProcessedJobsCount", 1);
          postProcessStatistics.put("postProcessedRequestsCount", numRequests);

          postProcessStatistics.put("completedJobsCount", 1);
          postProcessStatistics.put("completedRequestsCount",
            numCompletedRequests + numFailedRequests);
          postProcessStatistics.put("completedFailedRequestsCount", numFailedRequests);
          postProcessStatistics.put("completedTime",
            System.currentTimeMillis() - whenCreated.getTime());

          Transaction.afterCommit(
            () -> this.statisticsService.addStatistics(businessApplication, postProcessStatistics));
        }
      } catch (final Throwable e) {
        boolean result = true;
        if (isDatabaseResourcesException(e)) {
          error(log, "Tablespace error post-processing job " + batchJobId, e);
          result = false;
        } else {
          error(log, "Error post-processing job " + batchJobId, e);
        }
        transaction.setRollbackOnly();
        return result;
      }
    }
    return true;
  }

  /**
   * Create the error BatchJobResult for a BatchJob. This will only be created
   * if there were any errors.
   * @param batchJob
   *
   * @param batchJobId The Record identifier.
   */
  protected void postProcessCreateErrorResults(final AppLog log, final BatchJob batchJob,
    final Identifier batchJobId) {
    if (!batchJob.isCancelled()) {
      final List<MapEx> files = this.jobController.getFiles(batchJobId, JobController.GROUP_ERRORS);
      if (!files.isEmpty()) {
        final File errorFile = FileUtil.newTempFile("errors", ".csv");
        try {
          try (
            MapWriter errorWriter = MapWriter.newMapWriter(errorFile)) {
            for (final MapEx errorFileProperties : files) {
              final int sequenceNumber = errorFileProperties.getInteger("sequenceNumber");
              try (
                MapReader errorReader = this.jobController.getGroupErrorReader(batchJobId,
                  sequenceNumber)) {
                for (final MapEx error : errorReader) {
                  errorWriter.write(error);
                }
              }
            }
          }
          if (!batchJob.isCancelled()) {
            newBatchJobResult(batchJobId, BatchJobResult.ERROR_RESULT_DATA, Csv.MIME_TYPE,
              errorFile, 0);
          }
        } catch (final Throwable e) {
          throw new RuntimeException("Unable to save errors", e);
        } finally {
          Transaction.afterCommit(errorFile::delete);
        }
      }
    }
  }

  /**
   * Create the structured result files for a BatchJob.
   *
   * @param log The log to record any errors
   * @param batchJob The batch job.
   * @param batchJobId The batch job identifier.
   */
  protected void postProcessCreateStructuredResults(final BusinessApplication businessApplication,
    final AppLog log, final BatchJob batchJob, final Identifier batchJobId) {
    if (!batchJob.isCancelled()) {
      final String resultFormat = batchJob.getValue(BatchJob.RESULT_DATA_CONTENT_TYPE);

      final RecordDefinition resultRecordDefinition = businessApplication
        .getResultRecordDefinition();

      final String fileExtension = IoFactory.fileExtensionByMediaType(resultFormat);
      final File structuredResultFile = FileUtil.newTempFile("result", "." + fileExtension);

      boolean hasResults = false;
      try {
        try (
          com.revolsys.io.Writer<Record> structuredResultWriter = newStructuredResultWriter(
            batchJob, batchJobId, businessApplication, structuredResultFile, resultRecordDefinition,
            resultFormat)) {
          structuredResultWriter.open();
          final Map<String, Object> defaultProperties = new HashMap<>(
            structuredResultWriter.getProperties());
          final Integer numSubmittedGroups = batchJob.getInteger(BatchJob.NUM_SUBMITTED_GROUPS);
          if (numSubmittedGroups > 0) {
            for (int sequenceNumber = 1; sequenceNumber <= numSubmittedGroups; sequenceNumber++) {
              try (
                final MapReader resultDataReader = this.jobController
                  .getGroupResultReader(batchJobId, sequenceNumber);) {
                if (resultDataReader != null) {
                  for (final MapEx resultData : resultDataReader) {
                    if (batchJob.isCancelled()) {
                      return;
                    } else {
                      postProcessWriteStructuredResult(structuredResultWriter,
                        resultRecordDefinition, defaultProperties, resultData);
                      hasResults = true;
                    }
                  }
                }
              } catch (final Throwable e) {
                throw new RuntimeException("Unable to read results. batchJobId=" + batchJobId + "\t"
                  + " <= SEQUENCE_NUMBER = " + sequenceNumber, e);
              }
            }
          }
        }
        if (hasResults && !batchJob.isCancelled()) {
          newBatchJobResult(batchJobId, BatchJobResult.STRUCTURED_RESULT_DATA, resultFormat,
            structuredResultFile, 1);
        }
      } catch (final Throwable e) {
        throw new RuntimeException("Unable to save results", e);
      } finally {
        FileUtil.delete(structuredResultFile);
      }
    }
  }

  protected boolean postProcessWriteError(final MapWriter errorMapWriter,
    final Map<String, Object> resultMap) {
    boolean written;
    final String errorCode = (String)resultMap.get("errorCode");
    final Number requestSequenceNumber = (Number)resultMap.get(BusinessApplication.SEQUENCE_NUMBER);
    final String errorMessage = (String)resultMap.get("errorMessage");
    final Map<String, String> errorMap = new LinkedHashMap<>();
    errorMap.put("sequenceNumber", DataTypes.toString(requestSequenceNumber));
    errorMap.put("errorCode", errorCode);
    errorMap.put("errorMessage", errorMessage);
    errorMapWriter.write(errorMap);
    written = true;
    return written;
  }

  protected void postProcessWriteStructuredResult(
    final com.revolsys.io.Writer<Record> structuredDataWriter,
    final RecordDefinition resultRecordDefinition, final Map<String, Object> defaultProperties,
    final Map<String, Object> resultData) {
    final Record structuredResult = Records.newRecord(resultRecordDefinition, resultData);

    final String propertiesString = (String)resultData.get("customizationProperties");
    final boolean hasProperties = Property.hasValue(propertiesString);
    if (hasProperties) {
      final Map<String, Object> properties = Json.toObjectMap(propertiesString);
      structuredDataWriter.setProperties(properties);
    }

    structuredDataWriter.write(structuredResult);
    if (hasProperties) {
      structuredDataWriter.clearProperties();
      structuredDataWriter.setProperties(defaultProperties);
    }
  }

  public void preProcess(final Identifier batchJobId) {
    if (this.preProcess != null) {
      SendToChannelAfterCommit.send(this.preProcess.getIn(), batchJobId);
    }
  }

  public boolean preProcessBatchJob(final Identifier batchJobId, final long time,
    final long lastChangedTime) {

    final JobPreProcessTask jobPreProcessTask = new JobPreProcessTask(this, batchJobId, time,
      lastChangedTime);
    return jobPreProcessTask.process();
  }

  protected void removePreProcessedJobId(final Identifier batchJobId) {
    synchronized (this.preprocesedJobIds) {
      this.preprocesedJobIds.remove(batchJobId);
    }
  }

  public void rescheduleGroup(final BatchJobRequestExecutionGroup group) {
    if (this.running) {
      final BatchJob batchJob = group.getBatchJob();
      if (batchJob != null) {
        batchJob.rescheduleGroup(group);
        scheduleGroup(group);
      }
    }
  }

  public void resetCreatingRequestsBatchJobs(final String moduleName,
    final String businessApplicationName) {
    final AppLog log = getAppLog(businessApplicationName);
    try {
      int numCleanedJobs = 0;
      final Condition where = Q.equal(BatchJob.JOB_STATUS, BatchJobStatus.CREATING_REQUESTS) //
        .and(Q.equal(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName)) //
        .and(Q.equal(BatchJob.NUM_SUBMITTED_REQUESTS, 0)) //
      ;
      final Query query = new Query(BatchJob.BATCH_JOB, where);
      query.setFieldNames(BatchJob.BATCH_JOB_ID);
      for (final Record job : this.recordStore.getRecords(query)) {
        final Identifier jobId = job.getIdentifier();
        preProcess(jobId);
        numCleanedJobs++;
      }
      if (numCleanedJobs > 0) {
        log.info(
          "Re-schedule pre-procces\t" + businessApplicationName + "\tcount=" + numCleanedJobs);
      }
    } catch (final Throwable e) {
      log.error("Unable to re-schedule pre-procces\t" + businessApplicationName, e);
    }
  }

  public void resetCreatingResultsBatchJobs(final String moduleName,
    final String businessApplicationName) {
    final AppLog log = getAppLog(businessApplicationName);
    try {
      final String oldStatus = BatchJobStatus.CREATING_RESULTS;
      int numCleanedJobs = 0;
      final Condition where = Q.equal(BatchJob.JOB_STATUS, oldStatus) //
        .and(Q.equal(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName)) //
      ;
      final Query query = new Query(BatchJob.BATCH_JOB, where);
      query.setFieldNames(BatchJob.BATCH_JOB_ID);
      for (final Record job : this.recordStore.getRecords(query)) {
        final Identifier jobId = job.getIdentifier();
        postProcess(jobId);
        numCleanedJobs++;
      }
      if (numCleanedJobs > 0) {
        log.info(
          "Re-schedule post-procces\t" + businessApplicationName + "\tcount=" + numCleanedJobs);
      }
    } catch (final Throwable e) {
      log.error("Unable to re-schedule post-procces\t" + businessApplicationName, e);
    }
  }

  public void resetHungWorkers() {
    final Timestamp lastIdleTime = new Timestamp(
      System.currentTimeMillis() - this.maxWorkerWaitTime * 2);
    ArrayList<String> workerIds;
    synchronized (this.workersById) {
      workerIds = new ArrayList<>(this.workersById.keySet());
    }
    for (final String workerId : workerIds) {
      if (!this.connectedWorkerCounts.containsKey(workerId)) {
        Worker worker = getWorker(workerId);
        if (worker != null) {
          final Timestamp workerTimestamp = worker.getLastConnectTime();
          if (workerTimestamp == null || workerTimestamp.before(lastIdleTime)) {
            synchronized (this.workersById) {
              worker = this.workersById.remove(workerId);
              this.workersByKey.remove(worker.getKey());
            }
            final Map<String, BatchJobRequestExecutionGroup> groupsById = worker
              .getExecutingGroupsById();
            if (groupsById != null) {
              synchronized (groupsById) {
                for (final BatchJobRequestExecutionGroup group : new ArrayList<>(
                  groupsById.values())) {
                  final String groupId = group.getId();
                  Logs.debug(this, "Rescheduling group " + groupId + " from worker " + workerId);
                  worker.removeExecutingGroup(groupId);
                  group.resetId();
                  rescheduleGroup(group);
                }
              }
            }
          }
        }
      }
    }
  }

  /**
   * Reset all BatchJobs and their BatchJobExecutionGroups for the specified business
   * applications which are currently in the processing state so that they can
   * be rescheduled.
   *
   * @param moduleName The name of the module.
   * @param businessApplicationName The name of the business applications to
   *          reset the jobs for.
   */
  public void resetProcessingBatchJobs(final String moduleName,
    final String businessApplicationName) {
    final AppLog log = getAppLog(businessApplicationName);
    try {
      final NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = this.groupsToSchedule;
      if (groupsToSchedule != null) {
        final Collection<BatchJobRequestExecutionGroup> groups = groupsToSchedule
          .remove(moduleName);
        if (groups != null) {
          for (final BatchJobRequestExecutionGroup group : groups) {
            group.cancel();
          }
        }
      }
      final int numCleanedStatus = this.dataAccessObject
        .updateBatchJobProcessedStatus(businessApplicationName);
      if (numCleanedStatus > 0) {
        log.info("Jobs status for restart\tcount=" + numCleanedStatus);
      }
    } catch (final Throwable e) {
      log.error("Unable to reset jobs and groups for restart", e);
    }
  }

  public void scheduleFromDatabase() {
    if (this.dataAccessObject != null) {
      try (
        Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
        try {
          for (final Module module : this.businessApplicationRegistry.getModules()) {
            if (module.isStarted()) {
              final String moduleName = module.getName();
              for (final String businessApplicationName : module.getBusinessApplicationNames()) {
                scheduleFromDatabase(moduleName, businessApplicationName);
              }
            }
          }
        } catch (final Throwable e) {
          throw transaction.setRollbackOnly(e);
        }
      }
    }
  }

  public void scheduleFromDatabase(final String jobStatus) {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        for (final Module module : this.businessApplicationRegistry.getModules()) {
          if (module.isEnabled()) {
            final String moduleName = module.getName();
            for (final BusinessApplication businessApplication : module.getBusinessApplications()) {
              final String businessApplicationName = businessApplication.getName();
              scheduleFromDatabase(moduleName, businessApplicationName, jobStatus);
            }
          }
        }
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    }
  }

  public void scheduleFromDatabase(final String moduleName, final String businessApplicationName) {
    synchronized (businessApplicationName.intern()) {
      final List<Identifier> batchJobIds = this.dataAccessObject
        .getBatchJobIdsToSchedule(businessApplicationName);
      for (final Identifier batchJobId : batchJobIds) {
        getAppLog(businessApplicationName).info("Schedule from database\tbatchJobId=" + batchJobId);
        final BatchJob batchJob = getBatchJob(batchJobId);
        scheduleJob(batchJob);
      }
    }
  }

  public void scheduleFromDatabase(final String moduleName, final String businessApplicationName,
    final String jobStatus) {
    final AppLog log = getAppLog(businessApplicationName);
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        final List<Identifier> batchJobIds = this.dataAccessObject
          .getBatchJobIds(businessApplicationName, jobStatus);
        for (final Identifier batchJobId : batchJobIds) {
          if (jobStatus.equals(BatchJobStatus.SUBMITTED)) {
            log.info("Pre-process from database\tbatchJobId=" + batchJobId);
            this.preProcess.schedule(batchJobId);
          } else if (jobStatus.equals(BatchJobStatus.PROCESSED)) {
            log.info("Post-process from database\tbatchJobId=" + batchJobId);
            this.postProcess.schedule(batchJobId);
          }
        }
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    }
  }

  public void scheduleGroup(final BatchJobRequestExecutionGroup group) {
    if (this.running) {
      final BusinessApplication businessApplication = group.getBusinessApplication();
      final Module module = businessApplication.getModule();
      final String moduleName = module.getName();
      final NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = this.groupsToSchedule;
      if (groupsToSchedule != null) {
        groupsToSchedule.write(moduleName, group);
      }
    }
  }

  public void scheduleJob(final BatchJob batchJob) {
    if (this.running) {
      final BatchJobScheduler scheduler = this.scheduler;
      if (scheduler != null) {
        scheduler.schedule(batchJob);
      }
    }
  }

  /**
   * @param batchJobId The Record identifier.
   * @param batchJob The BatchJob.
   */
  public void sendNotification(final Identifier batchJobId, final BatchJob batchJob) {
    String notificationUrl = batchJob.getValue(BatchJob.NOTIFICATION_URL);
    if (Property.hasValue(notificationUrl)) {
      try {
        String baseUrl = this.getBaseUrl();
        if (this.userClassBaseUrls != null && !this.userClassBaseUrls.isEmpty()) {
          final String consumerKey = batchJob.getValue(BatchJob.USER_ID);
          final Record userAccount = this.dataAccessObject.getUserAccount(consumerKey);
          final String userClass = userAccount.getValue(UserAccount.USER_ACCOUNT_CLASS);
          if (this.userClassBaseUrls.containsKey(userClass)) {
            baseUrl = this.userClassBaseUrls.get(userClass);
          }
        }
        final String propertiesText = batchJob.getValue(BatchJob.PROPERTIES);
        String batchJobUrl = baseUrl;
        if (Property.hasValue(propertiesText)) {
          final String webServicePrefix = Json.toMap(propertiesText).get("webServicePrefix");
          if (Property.hasValue(webServicePrefix)) {
            batchJobUrl += webServicePrefix;
          }
        }
        batchJobUrl += "/ws/jobs/" + batchJobId;
        final Map<String, Object> jobMap = toMap(batchJob, batchJobUrl, 0);
        final String subject = "CPF Job " + batchJobId + " status";

        URI notificationUri = new URI(notificationUrl);
        final String scheme = notificationUri.getScheme();
        if (scheme != null) {
          if (scheme.equals("mailto")) {
            final StringWriter bodyOut = new StringWriter();

            final MapWriter writer = new XhtmlMapWriter(bodyOut);
            writer.setProperty("title", subject);
            writer.write(jobMap);
            writer.close();

            final MimeMessage message = this.mailSender.createMimeMessage();

            final MimeMessageHelper messageHelper = new MimeMessageHelper(message);
            messageHelper.setTo(notificationUri.getSchemeSpecificPart());
            messageHelper.setSubject(subject);
            messageHelper.setFrom(this.fromEmail);
            messageHelper.setText(bodyOut.toString(), true);
            this.mailSender.send(message);
          } else if (scheme.equals("http") || scheme.equals("https")) {
            notificationUrl = UrlUtil.getUrl(notificationUrl,
              Collections.singletonMap("batchJobUrl", batchJobUrl));
            notificationUri = new URI(notificationUrl);
            final StringWriter bodyOut = new StringWriter();
            final String contentType = batchJob.getValue(BatchJob.RESULT_DATA_CONTENT_TYPE);
            final MapWriterFactory writerFactory = IoFactory
              .factoryByMediaType(MapWriterFactory.class, contentType);
            if (writerFactory == null) {
              Logs.error(this,
                "Media type not supported for Record #" + batchJobId + " to " + contentType);
            } else {
              final MapWriter writer = writerFactory.newMapWriter(bodyOut);
              writer.setProperty("title", subject);
              writer.write(jobMap);
              writer.close();
              final HttpEntity body = new StringEntity(bodyOut.toString());
              try (
                @SuppressWarnings("deprecation")
                final CloseableHttpClient client = new DefaultHttpClient()) {
                final HttpPost request = new HttpPost(notificationUri);
                request.setHeader("Content-type", contentType);
                request.setEntity(body);
                final HttpResponse response = client.execute(request);
                final HttpEntity entity = response.getEntity();
                try {
                  final StatusLine statusLine = response.getStatusLine();
                  if (statusLine.getStatusCode() >= 400) {
                    Logs.error(this, "Unable to send notification for Record #" + batchJobId
                      + " to " + notificationUrl + " response=" + statusLine);
                  }
                } finally {
                  final InputStream content = entity.getContent();
                  FileUtil.closeSilent(content);
                }
              }
            }
          }
        }
      } catch (final Throwable e) {
        Logs.error(BatchJobService.class,
          "Unable to send notification for Record #" + batchJobId + " to " + notificationUrl, e);
      }
    }
  }

  public void sendWorkerMessage(final MapEx message) {
    synchronized (this.workersById) {
      for (final Worker worker : this.workersById.values()) {
        worker.sendMessage(message);
      }
      final NamedChannelBundle<BatchJobRequestExecutionGroup> groupsToSchedule = this.groupsToSchedule;
      if (groupsToSchedule != null) {
        groupsToSchedule.notifyReaders();
      }
    }
  }

  public void setAppLogDirectory(final File appLogDirectory) {
    this.appLogDirectory = appLogDirectory;
  }

  public void setAuthorizationService(final AuthorizationService authorizationService) {
    this.authorizationService = authorizationService;
  }

  public void setBusinessApplicationRegistry(
    final BusinessApplicationRegistry businessApplicationRegistry) {
    this.businessApplicationRegistry = businessApplicationRegistry;
  }

  public void setCompressData(final boolean compressData) {
    this.compressData = compressData;
  }

  public void setDataAccessObject(final CpfDataAccessObject dataAccessObject) {
    this.dataAccessObject = dataAccessObject;
    this.jobController = new DatabaseJobController(dataAccessObject);
    // this.jobController = new FileJobController(this, new
    // File("/apps/data/cpf"));
  }

  public void setDaysToKeepOldJobs(final int daysToKeepOldJobs) {
    this.daysToKeepOldJobs = daysToKeepOldJobs;
  }

  /**
   * Set the email address messages are sent from.
   *
   * @param fromEmail The email address messages are sent from.
   */
  public void setFromEmail(final String fromEmail) {
    this.fromEmail = fromEmail;
  }

  public void setJsonpEnabled(final boolean jsonpEnabled) {
    this.jsonpEnabled = jsonpEnabled;
  }

  /**
   * Set the class used to send email.
   *
   * @param mailSender The class used to send email.
   */
  public void setMailSender(final JavaMailSender mailSender) {
    this.mailSender = mailSender;
  }

  public void setMaxWorkerWaitTime(final long maxWorkerWaitTime) {
    this.maxWorkerWaitTime = maxWorkerWaitTime * 1000;
  }

  public void setPostProcess(final BatchJobPostProcess postProcess) {
    this.postProcess = postProcess;
    postProcess.getIn().writeConnect();
  }

  public void setPreProcess(final BatchJobPreProcess preProcess) {
    this.preProcess = preProcess;
    preProcess.getIn().writeConnect();
  }

  public void setScheduler(final BatchJobScheduler scheduler) {
    this.scheduler = scheduler;
  }

  public void setTimeoutForCapacityErrors(final long timeoutForCapacityErrors) {
    this.timeoutForCapacityErrors = timeoutForCapacityErrors * 60 * 1000;
  }

  public void setUserClassBaseUrls(final Map<String, String> userClassBaseUrls) {
    this.userClassBaseUrls = userClassBaseUrls;
  }

  public void setWorkerConnected(final String workerId, final long workerStartTime,
    final Session session) {
    synchronized (this.connectedWorkerCounts) {
      Maps.addCount(this.connectedWorkerCounts, workerId);

      synchronized (this.workersById) {
        Worker worker = this.workersById.get(workerId);
        if (worker == null || worker.getStartTime() != workerStartTime) {
          worker = new Worker(workerId, workerStartTime);
          worker.setSession(session);
          this.workersById.put(workerId, worker);
          this.workersByKey.put(worker.getKey(), worker);
          for (final Module module : this.businessApplicationRegistry.getModules()) {
            if (module.isStarted()) {
              final MapEx message = new LinkedHashMapEx();
              message.put("type", "moduleStart");

              final String name = module.getName();
              message.put("moduleName", name);

              final long startedTime = module.getStartedTime();
              message.put("moduleTime", startedTime);

              final int jarCount = module.getJarCount();
              message.put("moduleJarCount", jarCount);

              worker.sendMessage(message);
            }
          }
        }
        final long time = System.currentTimeMillis();
        final Timestamp lastConnectTime = new Timestamp(time);
        worker.setLastConnectTime(lastConnectTime);
      }
    }
  }

  public void setWorkerConnectTime(final String workerId, final long workerStartTime) {
    synchronized (this.connectedWorkerCounts) {
      synchronized (this.workersById) {
        final Worker worker = this.workersById.get(workerId);
        if (worker != null) {
          if (worker.getStartTime() == workerStartTime) {
            final long time = System.currentTimeMillis();
            final Timestamp lastConnectTime = new Timestamp(time);
            worker.setLastConnectTime(lastConnectTime);
          }

        }
      }
    }
  }

  public void setWorkerDisconnected(final String workerId, final long workerStartTime,
    final Session session) {
    synchronized (this.connectedWorkerCounts) {
      Integer count = this.connectedWorkerCounts.get(workerId);
      if (count != null) {
        count = count - 1;
        if (count <= 0) {
          this.connectedWorkerCounts.remove(workerId);
        } else {
          this.connectedWorkerCounts.put(workerId, count);
        }
      }

      synchronized (this.workersById) {
        final Worker worker = this.workersById.get(workerId);
        if (worker != null) {
          this.workersByKey.remove(worker.getKey());
          if (worker.getSession() == session) {
            worker.setSession(null);
          }
        }
      }
    }
  }

  public Map<String, Object> toMap(final BatchJob batchJob, final String jobUrl,
    final long timeUntilNextCheck) {
    try {
      final MapEx jobMap = new NamedLinkedHashMapEx("BatchJob");
      jobMap.put("id", new URI(jobUrl));
      jobMap.put("consumerKey", batchJob.getValue(BatchJob.USER_ID));
      jobMap.put("businessApplicationName", batchJob.getValue(BatchJob.BUSINESS_APPLICATION_NAME));
      jobMap.put("inputDataContentType", batchJob.getValue(BatchJob.INPUT_DATA_CONTENT_TYPE));
      jobMap.put("structuredInputDataUrl", batchJob.getValue(BatchJob.STRUCTURED_INPUT_DATA_URL));
      jobMap.put("resultDataContentType", batchJob.getValue(BatchJob.RESULT_DATA_CONTENT_TYPE));
      final Map<String, String> parameters = batchJob.getBusinessApplicationParameters();
      for (final Entry<String, String> param : parameters.entrySet()) {
        jobMap.put(param.getKey(), param.getValue());
      }
      jobMap.put("jobStatus", batchJob.getValue(BatchJob.JOB_STATUS));
      jobMap.put("jobStatusDate", Dates.format(DATE_TIME_FORMAT));
      jobMap.put("startTime",
        Dates.format(DATE_TIME_FORMAT, (Date)batchJob.getValue(Common.WHEN_CREATED)));
      jobMap.put("modificationTime",
        Dates.format(DATE_TIME_FORMAT, (Date)batchJob.getValue(Common.WHEN_UPDATED)));
      jobMap.put("lastScheduledTime",
        Dates.format(DATE_TIME_FORMAT, (Date)batchJob.getValue(BatchJob.LAST_SCHEDULED_TIMESTAMP)));
      final Date completedDate = (Date)batchJob.getValue(BatchJob.COMPLETED_TIMESTAMP);
      jobMap.put("completionTime", Dates.format(DATE_TIME_FORMAT, completedDate));
      jobMap.put("expiryDate", Dates.format("yyyy-MM-dd", getExpiryDate(completedDate)));

      jobMap.put("secondsToWaitForStatusCheck", timeUntilNextCheck);

      jobMap.put("numSubmittedRequests", batchJob.getValue(BatchJob.NUM_SUBMITTED_REQUESTS));
      jobMap.put("numCompletedRequests", batchJob.getNumCompletedRequests());
      jobMap.put("numFailedRequests", batchJob.getNumFailedRequests());
      jobMap.put("resultDataContentType", batchJob.getValue(BatchJob.RESULT_DATA_CONTENT_TYPE));
      if (batchJob.getValue(BatchJob.COMPLETED_TIMESTAMP) != null) {
        String path;
        if (jobUrl.endsWith("/")) {
          path = jobUrl + "results/";
        } else {
          path = jobUrl + "/results/";
        }

        jobMap.put("resultsUrl", new URI(path));
      }
      return jobMap;
    } catch (final URISyntaxException e) {
      throw new IllegalArgumentException(jobUrl + " is not a valid URI", e);
    }
  }

  public void updateBatchJobExecutionGroupFromResponse(final Worker worker, final BatchJob batchJob,
    final BatchJobRequestExecutionGroup group, final InputStream in) {
    final String groupId = group.getId();
    try {
      final int sequenceNumber = group.getSequenceNumber();
      final Identifier batchJobId = batchJob.getIdentifier();
      final BusinessApplication businessApplication = group.getBusinessApplication();
      if (!businessApplication.isPerRequestResultData()) {
        this.jobController.setGroupResult(batchJobId, sequenceNumber, in);
      }
      batchJob.addCompletedGroup(sequenceNumber);
      if (batchJob.isCompleted()) {
        batchJob.setStatus(this, BatchJobStatus.PROCESSING, BatchJobStatus.PROCESSED);
        postProcess(batchJobId);
      } else if (batchJob.hasAvailableGroup()) {
        scheduleJob(batchJob);
      }
      this.scheduler.groupFinished(group);
    } catch (final Throwable e) {
      if (isDatabaseResourcesException(e)) {
        Logs.error(this, "Tablespace error saving group results: " + groupId);
      } else {
        Logs.error(this, "Error saving group results: " + groupId, e);
      }
      cancelGroup(worker, groupId);
    }
  }

  public void updateWorkerExecutingGroups(final Worker worker,
    final List<String> executingGroupIds) {
    final long minStartTime = System.currentTimeMillis() - 60 * 1000;
    final List<BatchJobRequestExecutionGroup> executingGroups = worker.getExecutingGroups();
    for (final BatchJobRequestExecutionGroup executionGroup : executingGroups) {
      if (executionGroup.getExecutionStartTime() < minStartTime) {
        final String groupId = executionGroup.getId();
        if (!executingGroupIds.contains(groupId)) {
          cancelGroup(worker, groupId);
        }
      }
    }
  }

  public void waitIfTablespaceError(final Class<?> logClass) {
    final long currentTime = System.currentTimeMillis();
    final long waitTime = capacityErrorTime + this.timeoutForCapacityErrors - currentTime;
    if (waitTime > 0) {
      Logs.error(logClass, "Waiting " + waitTime / 1000 + " seconds for tablespace error");
      ThreadUtil.pause(waitTime);
    }
  }
}
