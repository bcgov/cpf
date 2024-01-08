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
package ca.bc.gov.open.cpf.api.web.builder;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobFile;
import ca.bc.gov.open.cpf.api.web.controller.JobController;

import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.web.annotation.RequestMapping;

@Controller
public class BatchJobFileUiBuilder extends CpfUiBuilder {

  public BatchJobFileUiBuilder() {
    super("batchJobFile", BatchJobFile.BATCH_JOB_FILE, "sequenceNumber", "Job File", "Job Files");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/{filePath}/{sequenceNumber}"
  }, method = {
    RequestMethod.GET, RequestMethod.POST
  })
  @ResponseBody
  public void download(final HttpServletRequest request, //
    final HttpServletResponse response, //
    @PathVariable final String moduleName, //
    @PathVariable final String businessApplicationName, //
    @PathVariable final Identifier batchJobId, //
    @PathVariable final String filePath, //
    @PathVariable final int sequenceNumber) throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    final JobController jobController = getJobController();
    jobController.writeFile(response, batchJobId, filePath, sequenceNumber);
  }

  @Override
  protected void initLabels() {
    super.initLabels();
    addLabel("sequenceNumber", "#");
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    final ActionFormKeySerializer actionDownload = new ActionFormKeySerializer("download",
      "Download", "fa fa-download");
    actionDownload.addParameterName("filePath", "filePath");
    actionDownload.addParameterName("sequenceNumber", "sequenceNumber");
    actionDownload.setTarget("_top");
    addKeySerializer(actionDownload);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/groupErrors"
  }, method = RequestMethod.GET, title = "Group Errors", fieldNames = {
    "sequenceNumber", "contentType", "size", "download"
  })
  @ResponseBody
  public Object moduleAppJobGroupErrorList(final HttpServletRequest request, //
    @PathVariable final String moduleName, //
    @PathVariable final String businessApplicationName, //
    @PathVariable final Identifier batchJobId) throws IOException {
    return newHandler(request, moduleName, businessApplicationName, batchJobId,
      JobController.GROUP_ERRORS, "moduleAppJobGroupErrorList");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/groupResults"
  }, method = RequestMethod.GET, title = "Group Results", fieldNames = {
    "sequenceNumber", "download", "contentType", "size"
  })
  @ResponseBody
  public Object moduleAppJobGroupResultList(final HttpServletRequest request, //
    @PathVariable final String moduleName, //
    @PathVariable final String businessApplicationName, //
    @PathVariable final Identifier batchJobId) throws IOException {
    return newHandler(request, moduleName, businessApplicationName, batchJobId,
      JobController.GROUP_RESULTS, "moduleAppJobGroupResultList");
  }

  private Object newHandler(final HttpServletRequest request, final String moduleName,
    final String businessApplicationName, final Identifier batchJobId, final String filePath,
    final String pageName) {
    return newDataTableHandlerOrRedirect(request, pageName, () -> {
      checkAdminOrModuleAdmin(moduleName);
      getModuleBusinessApplication(moduleName, businessApplicationName);
      getBatchJob(businessApplicationName, batchJobId);
      final JobController jobController = getJobController();
      return jobController.getFiles(batchJobId, filePath);
    }, BatchJob.BATCH_JOB, "moduleAppView");
  }
}
