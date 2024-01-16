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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.io.PathName;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.web.controller.JobController;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;

import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.BooleanImageKeySerializer;
import com.revolsys.ui.html.serializer.key.KeySerializer;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.util.Property;

@Controller
public class ExecutionGroupUiBuilder extends CpfUiBuilder {

  public ExecutionGroupUiBuilder() {
    super("executionGroup", PathName.newPathName("ExecutionGroup"), "sequenceNumber",
      "Execution Group", "Execution Groups");
    setIdParameterName("sequenceNumber");
  }

  @Override
  protected void initLabels() {
    super.initLabels();
    addLabel("sequenceNumber", "#");
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();
    addKeySerializer(new BooleanImageKeySerializer("completed"));

    final ActionFormKeySerializer actionDownload = new ActionFormKeySerializer("inputData",
      "Download", "fa fa-download");
    actionDownload.setTarget("_top");
    addKeySerializer(actionDownload);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/groups/{sequenceNumber}/inputData"
  }, method = {
    RequestMethod.GET, RequestMethod.POST
  })
  @ResponseBody
  public void inputData(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Long batchJobId,
    @PathVariable("sequenceNumber") final Integer sequenceNumber) throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    final BusinessApplication businessApplication = getModuleBusinessApplication(moduleName,
      businessApplicationName);
    getBatchJob(businessApplicationName, batchJobId);
    // final Record batchJobExecutionGroup =
    // getBatchJobExecutionGroup(batchJobId, sequenceNumber);
    if (businessApplication.isPerRequestInputData()) {
      // final String dataUrl =
      // batchJobExecutionGroup.getValue(BatchJobExecutionGroup.INPUT_DATA_URL);
      // if (dataUrl != null) {
      // response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      // response.setHeader("Location", dataUrl);
      // } else {
      // try {
      // final String contentType =
      // batchJobExecutionGroup.getValue(BatchJobExecutionGroup.INPUT_DATA_CONTENT_TYPE);
      // final Blob inputData =
      // batchJobExecutionGroup.getValue(BatchJobExecutionGroup.INPUT_DATA);
      // writeOpaqueData(response, contentType, baseName, inputData);
      // } catch (final SQLException e) {
      // final String message = "Unable to get data for " + baseName;
      // Logs.error(this,message, e);
      // throw new HttpMessageNotWritableException(message, e);
      // }
      // }
    } else {
      final JobController jobController = getJobController();
      jobController.writeGroupInput(response, Identifier.newIdentifier(batchJobId), sequenceNumber);
    }
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/groups"
  }, method = RequestMethod.GET, title = "Group Input", fieldNames = {
    "sequenceNumber", "inputData", "completed", "completedRequests", "failedRequests"
  })
  @ResponseBody
  public Object moduleAppJobList(final HttpServletRequest request,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Long batchJobId) throws IOException {

    if (isDataTableCallback(request)) {
      checkAdminOrModuleAdmin(moduleName);

      final BusinessApplication businessApplication = getModuleBusinessApplication(moduleName,
        businessApplicationName);
      final BatchJob batchJob = getBatchJob(businessApplicationName, batchJobId);
      final int numRecords = batchJob.getNumSubmittedGroups();

      final Map<String, Object> response = new LinkedHashMap<>();
      response.put("draw", HttpServletUtils.getIntegerParameter(request, "draw"));
      response.put("recordsTotal", numRecords);
      response.put("recordsFiltered", numRecords);
      final List<List<String>> rows = new ArrayList<>();
      int recordCount = 50;
      final String lengthString = request.getParameter("length");
      if (Property.hasValue(lengthString)) {
        if (!"NAN".equalsIgnoreCase(lengthString)) {
          try {
            recordCount = Integer.valueOf(lengthString);
          } catch (final Throwable e) {
          }
        }
      }
      if (recordCount > 0) {
        int offset = HttpServletUtils.getIntegerParameter(request, "start");
        if (offset < 0) {
          offset = 0;
        }
        offset++;
        if (businessApplication.isPerRequestInputData()) {
        } else {
          int maxRow = offset + recordCount;
          if (maxRow > numRecords) {
            maxRow = numRecords;
          }

          final List<KeySerializer> serializers = getSerializers("moduleAppJobList", "list");
          final Map<String, Object> data = new HashMap<>();
          data.put("inputData", "");

          for (int sequenceNumber = offset; sequenceNumber <= maxRow; sequenceNumber++) {
            data.put("sequenceNumber", sequenceNumber);
            final boolean completed = batchJob.isCompleted(sequenceNumber);
            data.put("completed", completed);
            final List<String> row = new ArrayList<>();
            for (final KeySerializer serializer : serializers) {
              final String key = serializer.getKey();
              final Object value = data.get(key);
              if (value == null) {
                row.add("-");
              } else {
                final String html = serializer.toString(data);
                row.add(html);
              }
            }
            rows.add(row);
          }
        }
      }
      response.put("data", rows);
      return response;

    } else {
      return redirectToTab(BatchJob.BATCH_JOB, "moduleAppView", "moduleAppJobList");
    }
  }
}
