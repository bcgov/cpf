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
import java.io.InputStream;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.logging.Logs;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.controller.CpfConfig;
import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobRequestExecutionGroup;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.api.scheduler.StatisticsService;
import ca.bc.gov.open.cpf.api.scheduler.Worker;
import ca.bc.gov.open.cpf.api.web.controller.JobController;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.NamedLinkedHashMapEx;
import com.revolsys.collection.range.RangeSet;
import com.revolsys.io.FileUtil;
import com.revolsys.record.Record;
import com.revolsys.record.io.format.csv.Csv;
import com.revolsys.spring.resource.InputStreamResource;
import com.revolsys.transaction.Transaction;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.util.Property;
import com.revolsys.util.UrlUtil;

@Controller
public class WorkerWebService {
  private BatchJobService batchJobService;

  private CpfDataAccessObject dataAccessObject;

  @Resource
  private CpfConfig cpfConfig;

  private JobController jobController;

  @Resource
  private StatisticsService statisticsService;

  private void checkRunning() {
    if (!this.batchJobService.isRunning()) {
      throw new IllegalStateException("Application is not running");
    }
  }

  @PreDestroy
  public void close() {
    this.batchJobService = null;
    this.dataAccessObject = null;
    this.jobController = null;
  }

  @RequestMapping("/worker/workers/{workerId}/jobs/{batchJobId}/groups/{groupId}/inputData")
  public void getBatchJobExecutionGroupOpaqueInputData(
    @PathVariable("workerId") final String workerId, final HttpServletResponse response,
    @PathVariable("groupId") final String groupId) throws IOException {
    checkRunning();
    final BatchJobRequestExecutionGroup group = this.batchJobService
      .getBatchJobRequestExecutionGroup(workerId, groupId);
    if (group != null && !group.isCancelled()) {
      final Identifier batchJobId = group.getBatchJobId();
      final BusinessApplication businessApplication = group.getBusinessApplication();
      final Module module = businessApplication.getModule();
      if (module == null || !module.isStarted()) {
        this.batchJobService.rescheduleGroup(group);
      } else if (businessApplication.isPerRequestInputData()) {
        final int groupSequenceNumber = group.getSequenceNumber();
        try (
          Transaction transaction = this.dataAccessObject.newTransaction()) {
          final String contentType = this.jobController.getGroupInputContentType(batchJobId,
            groupSequenceNumber);
          if (contentType != null) {
            if (contentType.startsWith("url:")) {
              final String inputDataUrl = this.jobController.getGroupInputString(batchJobId,
                groupSequenceNumber);
              if (Property.hasValue(inputDataUrl)) {
                response.setStatus(HttpServletResponse.SC_SEE_OTHER);
                response.setHeader("Location", inputDataUrl);
                return;
              }
            } else {
              try (
                InputStream in = this.jobController.getGroupInputStream(batchJobId,
                  groupSequenceNumber)) {
                if (in != null) {
                  response.setContentType(contentType);
                  try (
                    final OutputStream out = response.getOutputStream()) {
                    FileUtil.copy(in, out);
                  }
                  return;
                }
              }
            }
          }
        }
      }
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = "/worker/workers/{workerId}/jobs/{batchJobId}/groups/{groupId}")
  public void getBatchJobRequestExecutionGroup(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("workerId") final String workerId,
    @PathVariable("groupId") final String groupId) throws IOException {
    checkRunning();
    final BatchJobRequestExecutionGroup group = this.batchJobService
      .getBatchJobRequestExecutionGroup(workerId, groupId);
    if (group != null && !group.isCancelled()) {
      final Identifier batchJobId = group.getBatchJobId();
      final BusinessApplication businessApplication = group.getBusinessApplication();
      final Module module = businessApplication.getModule();
      if (module == null || !module.isStarted()) {
        this.batchJobService.rescheduleGroup(group);
      } else {
        final int groupSequenceNumber = group.getSequenceNumber();
        response.setContentType(Csv.MIME_TYPE);
        try (
          final OutputStream out = response.getOutputStream()) {
          if (businessApplication.isPerRequestInputData()) {
            try (
              PrintWriter printWriter = new PrintWriter(out)) {
              printWriter.println(BusinessApplication.SEQUENCE_NUMBER);
              printWriter.println(groupSequenceNumber);
            }
            return;
          } else {
            try (
              Transaction transaction = this.dataAccessObject.newTransaction();
              InputStream in = this.jobController.getGroupInputStream(batchJobId,
                groupSequenceNumber)) {
              if (in != null) {
                FileUtil.copy(in, out);
                return;
              }
            }
          }
        }
      }
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/worker/modules/{moduleName}/{moduleTime}/jar/{jarIndex}"
  }, method = RequestMethod.GET)
  @ResponseBody
  public void getModuleJar(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("moduleTime") final Long moduleTime, @PathVariable("jarIndex") final int jarIndex)
    throws IOException {
    checkRunning();
    final Module module = this.batchJobService.getModule(moduleName);
    if (module == null || !module.isStarted() || module.getStartedTime() != moduleTime) {
      throw new PageNotFoundException();
    } else {
      final URL url = module.getJarUrl(jarIndex);
      if (url != null) {
        try (
          final InputStream in = UrlUtil.getInputStream(url);
          final OutputStream out = response.getOutputStream()) {
          FileUtil.copy(in, out);
        } catch (final IllegalArgumentException e) {
          module.addModuleError("Error loading jar " + jarIndex + ": " + url, e);
        }
      }
    }

  }

  @RequestMapping(value = {
    "/worker/modules/{moduleName}/{moduleTime}/urls"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Map<String, Object> getModuleUrls(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("moduleTime") final Long moduleTime) throws IOException {
    checkRunning();
    final BusinessApplicationRegistry businessApplicationRegistry = this.batchJobService
      .getBusinessApplicationRegistry();
    final Module module = businessApplicationRegistry.getModule(moduleName);
    if (module == null || !module.isStarted() || module.getStartedTime() != moduleTime) {
      throw new PageNotFoundException();
    } else {
      final List<URL> jarUrls = module.getJarUrls();
      final String path = "/worker/modules/" + moduleName + "/" + moduleTime + "/urls/";
      final List<String> jarPaths = new ArrayList<>();
      for (int i = 0; i < jarUrls.size(); i++) {
        jarPaths.add(path + i);
      }
      final MapEx result = new NamedLinkedHashMapEx("ModuleUrls");
      result.put("jarPaths", jarPaths);
      return result;
    }
  }

  public String getWebServiceUrl() {
    return this.cpfConfig.getInternalWebServiceUrl();
  }

  @RequestMapping("/worker/workers/{workerId}/jobs/{batchJobId}/groups/{groupId}/error")
  public void postBatchJobExecutionGroupError(@PathVariable final String workerId, //
    @PathVariable final Identifier batchJobId, //
    @PathVariable final String groupId, //
    final InputStream in) {
    checkRunning();
    final BatchJobRequestExecutionGroup group = this.batchJobService
      .getBatchJobRequestExecutionGroup(workerId, groupId);

    if (group != null) {
      synchronized (group) {
        if (!group.isCancelled()) {
          final int sequenceNumber = group.getSequenceNumber();
          this.jobController.setGroupError(batchJobId, sequenceNumber, in);
        }
      }
    }
  }

  @RequestMapping("/worker/workers/{workerId}/jobs/{batchJobId}/groups/{groupId}/requests/{sequenceNumber}/resultData")
  @ResponseBody
  public Map<String, ? extends Object> postBatchJobExecutionGroupOpaqueOutputData(
    final HttpServletRequest request, @PathVariable("workerId") final String workerId,
    @PathVariable("batchJobId") final Identifier batchJobId,
    @PathVariable("groupId") final String groupId,
    @PathVariable("sequenceNumber") final int sequenceNumber) throws IOException {
    checkRunning();
    final BatchJobRequestExecutionGroup group = this.batchJobService
      .getBatchJobRequestExecutionGroup(workerId, groupId);

    if (group != null) {
      synchronized (group) {
        if (!group.isCancelled()) {
          final Record batchJob = this.dataAccessObject.getBatchJob(batchJobId);
          if (batchJob != null) {
            final String businessApplicationName = batchJob
              .getValue(BatchJob.BUSINESS_APPLICATION_NAME);
            final BusinessApplication businessApplication = this.batchJobService
              .getBusinessApplication(businessApplicationName);
            if (businessApplication != null && businessApplication.isPerRequestResultData()) {
              final String resultDataContentType = batchJob
                .getValue(BatchJob.RESULT_DATA_CONTENT_TYPE);

              try (
                InputStream in = request.getInputStream()) {
                final int conentLegth = request.getContentLength();
                final com.revolsys.spring.resource.Resource resource = new InputStreamResource(in,
                  conentLegth);
                if (!group.isCancelled()) {
                  this.batchJobService.newBatchJobResultOpaque(batchJobId, sequenceNumber,
                    resultDataContentType, resource);
                }
              } catch (final IOException e) {
                this.batchJobService.rescheduleGroup(group);
              }
            }
          }
        }
      }
    }
    final MapEx map = new NamedLinkedHashMapEx("OpaqueOutputDataResults");
    return map;
  }

  @RequestMapping(value = "/worker/workers/{workerId}/jobs/{batchJobId}/groups/{groupId}/results",
      method = RequestMethod.POST)
  public void postBatchJobRequestExecutionGroupResults(
    @PathVariable("workerId") final String workerId, //
    @PathVariable("batchJobId") final String batchJobId, //
    @PathVariable("groupId") final String groupId, //
    @RequestParam(value = "completedRequestRange",
        defaultValue = "") final String completedRequestRange, //
    @RequestParam(value = "failedRequestRange", defaultValue = "") final String failedRequestRange, //
    @RequestParam(value = "groupExecutedTime", defaultValue = "0") final Long groupExecutedTime, //
    @RequestParam(value = "applicationExecutedTime",
        defaultValue = "0") final Long applicationExecutedTime, //
    final InputStream in) {
    checkRunning();
    final Worker worker = this.batchJobService.getWorker(workerId);
    if (worker != null) {
      final BatchJobRequestExecutionGroup group = worker.removeExecutingGroup(groupId);
      if (group != null && !group.isCancelled()) {
        synchronized (group) {
          final BatchJob batchJob = group.getBatchJob();
          if (!batchJob.isCompleted()) {
            final RangeSet completedRequests = batchJob.addCompletedRequests(completedRequestRange);
            final RangeSet failedRequests = batchJob.addFailedRequests(failedRequestRange);
            if (in != null) {
              this.batchJobService.updateBatchJobExecutionGroupFromResponse(worker, batchJob, group,
                in);
            }
            batchJob.removeGroup(group);
            this.batchJobService.updateBatchJob(batchJob);
            final BusinessApplication businessApplication = group.getBusinessApplication();
            final String moduleName = businessApplication.getModuleName();
            final long executionTime = this.statisticsService.updateGroupStatistics(group,
              businessApplication, moduleName, applicationExecutedTime, groupExecutedTime,
              completedRequests.size(), failedRequests.size());
            final AppLog appLog = businessApplication.getLog();
            appLog.info("End\tGroup execution\tgroupId=" + groupId + "\tworkerId=" + workerId
              + "\ttime=" + executionTime / 1000.0);
          }
        }
      }
    }
  }

  @RequestMapping(value = "/worker/workers/{workerId}/{workerStartTime}/jobs/groups/nextId",
      method = RequestMethod.POST)
  @ResponseBody
  public Map<String, Object> postNextBatchJobExecutionGroupId(
    @PathVariable("workerId") final String workerId,
    @PathVariable("workerStartTime") final long workerStartTime, //
    @RequestParam(value = "moduleName", required = false) final List<String> moduleNames,
    @RequestParam(value = "maxMessageId", required = false,
        defaultValue = "0") final int maxMessageId) {
    Map<String, Object> response = Collections.emptyMap();
    final BatchJobService batchJobService = this.batchJobService;
    if (batchJobService != null) {
      checkRunning();
      try {
        batchJobService.setWorkerConnectTime(workerId, workerStartTime);
        response = batchJobService.getNextBatchJobRequestExecutionGroup(workerId, maxMessageId,
          moduleNames);
      } catch (final Throwable e) {
        Logs.error(WorkerWebService.class, e.getMessage(), e);
        throw new HttpMessageNotWritableException("Unable to get execution group id", e);
      } finally {
        batchJobService.setWorkerConnectTime(workerId, workerStartTime);
      }
    }
    return response;
  }

  @Resource
  public void setBatchJobService(final BatchJobService batchJobService) {
    this.batchJobService = batchJobService;
    this.dataAccessObject = batchJobService.getDataAccessObject();
    this.jobController = batchJobService.getJobController();
  }
}
