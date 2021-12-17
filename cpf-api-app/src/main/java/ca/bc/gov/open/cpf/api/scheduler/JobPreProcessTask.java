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
import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.logging.Logs;
import org.springframework.util.StopWatch;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobResult;
import ca.bc.gov.open.cpf.api.domain.BatchJobStatus;
import ca.bc.gov.open.cpf.api.domain.Common;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.web.controller.JobController;
import ca.bc.gov.open.cpf.client.api.ErrorCode;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.log.AppLogUtil;

import com.revolsys.io.FileUtil;
import com.revolsys.io.IoFactory;
import com.revolsys.io.Reader;
import com.revolsys.io.map.MapReader;
import com.revolsys.io.map.MapReaderFactory;
import com.revolsys.io.map.MapWriter;
import com.revolsys.record.FieldValueInvalidException;
import com.revolsys.record.Record;
import com.revolsys.record.io.MapReaderRecordReader;
import com.revolsys.record.io.format.csv.Csv;
import com.revolsys.record.io.format.csv.CsvMapWriter;
import com.revolsys.record.io.format.tsv.Tsv;
import com.revolsys.record.io.format.tsv.TsvWriter;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.spring.resource.InputStreamResource;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Property;

public class JobPreProcessTask {

  private final Identifier batchJobId;

  private final long time;

  private final long lastChangedTime;

  private final BatchJobService batchJobService;

  private final JobController jobController;

  private final CpfDataAccessObject dataAccessObject;

  private TsvWriter errorWriter;

  private File errorFile;

  public JobPreProcessTask(final BatchJobService batchJobService, final Identifier batchJobId,
    final long time, final long lastChangedTime) {
    this.batchJobService = batchJobService;
    this.jobController = batchJobService.getJobController();
    this.dataAccessObject = batchJobService.getDataAccessObject();
    this.batchJobId = batchJobId;
    this.time = time;
    this.lastChangedTime = lastChangedTime;
  }

  /**
  * Generate an error result for the job, update the job counts and status, and
  * back out any add job requests that have already been added.
  *
  * @param validationErrorCode The failure error code.
  */
  private boolean addJobValidationError(final BatchJob batchJob,
    final ErrorCode validationErrorCode, final String validationErrorMessage) {
    final Identifier batchJobId = batchJob.getIdentifier();
    final CpfDataAccessObject dataAccessObject = this.batchJobService.getDataAccessObject();
    if (dataAccessObject != null) {

      final StringWriter errorWriter = new StringWriter();

      String newErrorMessage = validationErrorMessage;
      if (Property.isEmpty(validationErrorMessage)) {
        newErrorMessage = validationErrorCode.getDescription();
      }
      try (
        final MapWriter errorMapWriter = new CsvMapWriter(errorWriter)) {
        final Map<String, String> errorResultMap = new HashMap<>();
        errorResultMap.put("Code", validationErrorCode.name());
        errorResultMap.put("Message", newErrorMessage);
        errorMapWriter.write(errorResultMap);
      }
      final byte[] errorBytes = errorWriter.toString().getBytes(StandardCharsets.UTF_8);
      this.batchJobService.newBatchJobResult(batchJobId, BatchJobResult.ERROR_RESULT_DATA,
        Csv.MIME_TYPE, errorBytes, 0);
      dataAccessObject.setBatchJobFailed(batchJob);
    }
    return false;
  }

  private boolean addJobValidationError(final BatchJob batchJob,
    final ErrorCode validationErrorCode, final Throwable exception) {
    Logs.debug(BatchJobService.class, exception);
    return addJobValidationError(batchJob, validationErrorCode, exception.getMessage());
  }

  public void addRequestError(final int sequenceNumber, final Object errorCode,
    final String message, final CharSequence trace) {
    if (this.errorWriter == null) {
      this.errorFile = FileUtil.newTempFile("job-" + this.batchJobId.toString(), "tsv");
      this.errorWriter = Tsv.plainWriter(this.errorFile);
      this.errorWriter.write("sequenceNumber", "errorCode", "message", "trace");
    }

    this.errorWriter.write(sequenceNumber, errorCode, message, trace);
    this.errorWriter.flush();
  }

  /**
   * Get a buffered reader for the job's input data. The input Data may be a
   * remote URL or a CLOB field.
   * @param batchJobId
   *
   * @return BufferedReader or null if unable to connect to data
   */
  private InputStream getJobInputDataStream(final Identifier batchJobId, final Record batchJob) {
    final String inputDataUrlString = batchJob.getString(BatchJob.STRUCTURED_INPUT_DATA_URL);
    if (Property.hasValue(inputDataUrlString)) {
      try {
        final URL inputDataUrl = new URL(inputDataUrlString);
        return inputDataUrl.openStream();
      } catch (final IOException e) {
        Logs.error(BatchJobService.class, "Unable to open stream: " + inputDataUrlString, e);
      }
    } else {
      return this.jobController.getJobInputStream(batchJobId);
    }

    return null;
  }

  public boolean process() {
    this.batchJobService.addPreProcessedJobId(this.batchJobId);
    AppLog log = null;
    BatchJob batchJob = null;
    int maxRequests = Integer.MAX_VALUE;

    try (
      final Transaction transaction = this.dataAccessObject
        .newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        batchJob = this.dataAccessObject.getBatchJob(this.batchJobId);
        if (batchJob != null && batchJob.isStatus(BatchJobStatus.CREATING_REQUESTS)) {
          int numSubmittedRequests = 0;
          final String businessApplicationName = batchJob
            .getValue(BatchJob.BUSINESS_APPLICATION_NAME);
          final BusinessApplication businessApplication = this.batchJobService
            .getBusinessApplication(businessApplicationName);
          if (businessApplication == null) {
            throw new IllegalArgumentException(
              "Cannot find business application: " + businessApplicationName);
          }
          log = businessApplication.getLog();
          if (log.isInfoEnabled()) {
            log.info("Start\tJob pre-process\tbatchJobId=" + this.batchJobId);
          }
          try {
            final int maxGroupSize = businessApplication.getNumRequestsPerWorker();
            int numGroups = 0;
            boolean valid = true;
            final Map<String, Object> preProcessScheduledStatistics = new HashMap<>();
            preProcessScheduledStatistics.put("preProcessScheduledJobsCount", 1);
            preProcessScheduledStatistics.put("preProcessScheduledJobsTime",
              this.time - this.lastChangedTime);
            final StatisticsService statisticsService = this.batchJobService.getStatisticsService();

            Transaction.afterCommit(() -> {
              statisticsService.addStatistics(businessApplication, preProcessScheduledStatistics);
            });

            int numFailedRequests = batchJob.getNumFailedRequests();
            try (
              final InputStream inputDataStream = getJobInputDataStream(this.batchJobId,
                batchJob)) {
              final Map<String, String> jobParameters = batchJob.getBusinessApplicationParameters();

              final String inputContentType = batchJob.getValue(BatchJob.INPUT_DATA_CONTENT_TYPE);
              final String resultContentType = batchJob.getValue(BatchJob.RESULT_DATA_CONTENT_TYPE);
              if (!businessApplication.isInputContentTypeSupported(inputContentType)) {
                valid = addJobValidationError(batchJob, ErrorCode.BAD_INPUT_DATA_TYPE, "");
              } else if (!businessApplication.isResultContentTypeSupported(resultContentType)) {
                valid = addJobValidationError(batchJob, ErrorCode.BAD_RESULT_DATA_TYPE, "");
              } else if (inputDataStream == null) {
                valid = addJobValidationError(batchJob, ErrorCode.INPUT_DATA_UNREADABLE, "");
              } else {
                final RecordDefinition requestRecordDefinition = businessApplication
                  .getInternalRequestRecordDefinition();
                try {
                  final MapReaderFactory factory = IoFactory
                    .factoryByMediaType(MapReaderFactory.class, inputContentType);
                  if (factory == null) {
                    valid = addJobValidationError(batchJob, ErrorCode.INPUT_DATA_UNREADABLE,
                      "Media type not supported:" + inputContentType);
                  } else {
                    PreProcessGroup group = null;
                    final InputStreamResource resource = new InputStreamResource("input",
                      inputDataStream);
                    try (
                      final MapReader mapReader = factory.newMapReader(resource)) {
                      if (mapReader == null) {
                        valid = addJobValidationError(batchJob, ErrorCode.INPUT_DATA_UNREADABLE,
                          "Media type not supported: " + inputContentType);
                      } else {
                        try (
                          final Reader<Record> inputDataReader = new MapReaderRecordReader(
                            requestRecordDefinition, mapReader)) {

                          for (final Iterator<Record> iterator = inputDataReader
                            .iterator(); iterator.hasNext();) {
                            numSubmittedRequests++;
                            try {
                              final Record inputDataRecord = iterator.next();
                              if (!this.batchJobService
                                .containsPreProcessedJobId(this.batchJobId)) {
                                if (group != null) {
                                  group.cancel();
                                }
                                return true;
                              }
                              if (group == null) {
                                group = this.jobController.newPreProcessGroup(this,
                                  businessApplication, batchJob, jobParameters, numGroups + 1);
                                numGroups++;
                              }
                              if (!group.addRequest(inputDataRecord, numSubmittedRequests)) {
                                numFailedRequests++;
                              }
                              if (group.getGroupSize() == maxGroupSize) {
                                if (this.batchJobService
                                  .containsPreProcessedJobId(this.batchJobId)) {
                                  group.commit();
                                } else {
                                  group.cancel();
                                  return true;
                                }
                                group = null;
                              }
                            } catch (final FieldValueInvalidException e) {
                              numFailedRequests++;
                              addRequestError(numFailedRequests, ErrorCode.BAD_INPUT_DATA_VALUE,
                                e.getMessage(), "");
                            }
                          }
                          if (group != null) {
                            if (this.batchJobService.containsPreProcessedJobId(this.batchJobId)) {
                              group.commit();
                            } else {
                              group.cancel();
                              return true;
                            }
                          }
                        }

                        maxRequests = businessApplication.getMaxRequestsPerJob();
                        if (numSubmittedRequests == 0) {
                          valid = addJobValidationError(batchJob, ErrorCode.INPUT_DATA_UNREADABLE,
                            "No records specified");
                        } else if (numSubmittedRequests > maxRequests) {
                          valid = addJobValidationError(batchJob, ErrorCode.TOO_MANY_REQUESTS,
                            String.valueOf(numSubmittedRequests));
                        }
                      }
                    } catch (final Throwable e) {
                      if (group != null) {
                        group.cancel();
                      }
                      Logs.error(this, "Error pre-processing job " + this.batchJobId, e);
                      valid = addJobValidationError(batchJob, ErrorCode.ERROR_PROCESSING_REQUEST,
                        e);
                    }
                  }
                } catch (final Throwable e) {
                  if (BatchJobService.isDatabaseResourcesException(e)) {
                    Logs.error(this, "Tablespace error pre-processing job " + this.batchJobId, e);
                    return false;
                  } else {
                    Logs.error(this, "Error pre-processing job " + this.batchJobId, e);
                    valid = addJobValidationError(batchJob, ErrorCode.ERROR_PROCESSING_REQUEST, e);
                  }
                }
              }
            } catch (final IOException e) {
              Logs.error(this, "Error reading input data or writing groups for " + this.batchJobId,
                e);
              transaction.setRollbackOnly(e);
              return false;
            } finally {
              if (this.errorWriter != null) {
                this.errorWriter.close();
                this.jobController.setGroupError(this.batchJobId, 0, this.errorFile);
              }
            }

            if (numSubmittedRequests == 0 || numSubmittedRequests > maxRequests) {

            } else if (!valid || numSubmittedRequests == numFailedRequests) {
              valid = false;
              if (this.dataAccessObject.setBatchJobRequestsFailed(this.batchJobId,
                numSubmittedRequests, numFailedRequests, maxGroupSize, numGroups)) {
                batchJob.setStatus(this.batchJobService, BatchJobStatus.CREATING_REQUESTS,
                  BatchJobStatus.PROCESSED);
                this.batchJobService.postProcess(this.batchJobId);
              } else {
                batchJob.setStatus(this.batchJobService, BatchJobStatus.CREATING_REQUESTS,
                  BatchJobStatus.SUBMITTED);
              }
            } else {
              if (batchJob.setStatus(this.batchJobService, BatchJobStatus.CREATING_REQUESTS,
                BatchJobStatus.PROCESSING)) {
                final Timestamp now = batchJob.getValue(BatchJob.LAST_SCHEDULED_TIMESTAMP);
                batchJob.setValue(BatchJob.LAST_SCHEDULED_TIMESTAMP, now);
                batchJob.setValue(BatchJob.NUM_SUBMITTED_REQUESTS, numSubmittedRequests);
                batchJob.setValue(BatchJob.GROUP_SIZE, maxGroupSize);
                batchJob.setGroupCount(numGroups);
                batchJob.update();
                this.batchJobService.scheduleJob(batchJob);
              }
            }
            final Map<String, Object> preProcessStatistics = new HashMap<>();
            preProcessStatistics.put("preProcessedTime", stopWatch);
            preProcessStatistics.put("preProcessedJobsCount", 1);
            preProcessStatistics.put("preProcessedRequestsCount", numSubmittedRequests);

            Transaction.afterCommit(
              () -> statisticsService.addStatistics(businessApplication, preProcessStatistics));

            if (!valid) {
              final Timestamp whenCreated = batchJob.getValue(Common.WHEN_CREATED);

              final Map<String, Object> jobCompletedStatistics = new HashMap<>();

              jobCompletedStatistics.put("completedJobsCount", 1);
              jobCompletedStatistics.put("completedRequestsCount", numFailedRequests);
              jobCompletedStatistics.put("completedFailedRequestsCount", numFailedRequests);
              jobCompletedStatistics.put("completedTime",
                System.currentTimeMillis() - whenCreated.getTime());

              Transaction.afterCommit(() -> {
                statisticsService.addStatistics(businessApplication, jobCompletedStatistics);
                if (this.errorFile != null) {
                  FileUtil.delete(this.errorFile);
                }
              });
            }
          } finally {
            if (log.isInfoEnabled()) {
              AppLogUtil.infoAfterCommit(log,
                "End\tJob pre-process\tbatchJobId=" + this.batchJobId);
            }
          }
        }
      } catch (final Throwable e) {
        if (batchJob != null) {
          batchJob.setStatus(this.batchJobService, BatchJobStatus.CREATING_REQUESTS,
            BatchJobStatus.SUBMITTED);
        }
        BatchJobService.error(log, "Error\tJob pre-process\tbatchJobId=" + this.batchJobId, e);
        throw transaction.setRollbackOnly(e);
      } finally {
        this.batchJobService.removePreProcessedJobId(this.batchJobId);
      }
    }
    return true;
  }
}
