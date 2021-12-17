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

import java.sql.Timestamp;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.jeometry.common.data.identifier.Identifier;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

public class BatchJobRequestExecutionGroup {

  private final BusinessApplication businessApplication;

  private final Map<String, String> businessApplicationParameterMap;

  private long executionStartTime;

  private String id;

  private final AtomicInteger attempt = new AtomicInteger(0);

  private final String resultDataContentType;

  private final Timestamp scheduleTimestamp;

  private final String consumerKey;

  private final String moduleName;

  private long moduleStartTime;

  private int numCompletedRequests;

  private int numFailedRequests;

  private boolean cancelled = false;

  private final int sequenceNumber;

  private final String baseId;

  private final BatchJob batchJob;

  public BatchJobRequestExecutionGroup(final String consumerKey, final BatchJob batchJob,
    final BusinessApplication businessApplication,
    final Map<String, String> businessApplicationParameterMap, final String resultDataContentType,
    final Timestamp scheduleTimestamp, final int sequenceNumber) {
    this.consumerKey = consumerKey;
    this.batchJob = batchJob;
    this.businessApplication = businessApplication;
    this.moduleName = businessApplication.getModule().getName();
    this.businessApplicationParameterMap = businessApplicationParameterMap;
    this.resultDataContentType = resultDataContentType;
    this.scheduleTimestamp = scheduleTimestamp;
    this.sequenceNumber = sequenceNumber;
    this.baseId = getBatchJobId() + "-" + sequenceNumber;
    resetId();
  }

  public void cancel() {
    final BusinessApplication businessApplication = this.businessApplication;
    if (businessApplication != null) {
      final AppLog log = businessApplication.getLog();
      if (log.isInfoEnabled()) {
        log.info("Cancel group: " + this.batchJob.getIdentifier() + " " + this.id);
      }
    }
    this.batchJob.removeGroup(this);
    cancelInternal();
  }

  public void cancelInternal() {
    this.batchJob.cancelScheduledGroup(this.sequenceNumber);
    this.cancelled = true;
  }

  public String getBaseId() {
    return this.baseId;
  }

  public BatchJob getBatchJob() {
    return this.batchJob;
  }

  public Identifier getBatchJobId() {
    return this.batchJob.getIdentifier();
  }

  public BusinessApplication getBusinessApplication() {
    return this.businessApplication;
  }

  public String getBusinessApplicationName() {
    return this.businessApplication.getName();
  }

  public Map<String, String> getBusinessApplicationParameterMap() {
    return this.businessApplicationParameterMap;
  }

  public String getconsumerKey() {
    return this.consumerKey;
  }

  public long getExecutionStartTime() {
    return this.executionStartTime;
  }

  public String getId() {
    return this.id;
  }

  public Module getModule() {
    return this.businessApplication.getModule();
  }

  public String getModuleName() {
    return this.moduleName;
  }

  public long getModuleStartTime() {
    return this.moduleStartTime;
  }

  public int getNumCompletedRequests() {
    return this.numCompletedRequests;
  }

  public int getNumFailedRequests() {
    return this.numFailedRequests;
  }

  public String getResultDataContentType() {
    return this.resultDataContentType;
  }

  public Timestamp getScheduleTimestamp() {
    return this.scheduleTimestamp;
  }

  public int getSequenceNumber() {
    return this.sequenceNumber;
  }

  public Timestamp getStartedTimestamp() {
    return this.scheduleTimestamp;
  }

  @Override
  public int hashCode() {
    return this.id.hashCode();
  }

  public boolean isCancelled() {
    return this.cancelled || this.batchJob.isCancelled();
  }

  public void resetId() {
    this.id = getBatchJobId() + "-" + this.sequenceNumber + "-" + this.attempt.incrementAndGet();
  }

  public void setExecutionStartTime(final long executionStartTime) {
    this.executionStartTime = executionStartTime;
  }

  public void setModuleStartTime(final long moduleStartTime) {
    this.moduleStartTime = moduleStartTime;
  }

  public void setNumCompletedRequests(final int numCompletedRequests) {
    this.numCompletedRequests = numCompletedRequests;
  }

  public void setNumFailedRequests(final int numFailedRequests) {
    this.numFailedRequests = numFailedRequests;
  }

  @Override
  public String toString() {
    return this.id;
  }
}
