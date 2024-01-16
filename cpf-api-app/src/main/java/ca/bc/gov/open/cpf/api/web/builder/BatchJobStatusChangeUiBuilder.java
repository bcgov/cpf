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
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobStatusChange;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;

import com.revolsys.record.Record;
import com.revolsys.ui.web.annotation.ColumnSortOrder;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;

@Controller
public class BatchJobStatusChangeUiBuilder extends CpfUiBuilder {

  public BatchJobStatusChangeUiBuilder() {
    super("batchJobStatusChange", BatchJobStatusChange.BATCH_JOB_STATUS_CHANGE,
      BatchJobStatusChange.BATCH_JOB_STATUS_CHANGE_ID, "Batch Job Status Change",
      "Batch Job Status Changes");
  }

  @RequestMapping(value = "/ws/jobs/{batchJobId}/statusChanges", //
      method = RequestMethod.GET, //
      title = "Status Changes", fieldNames = {
        BatchJobStatusChange.JOB_STATUS, //
        BatchJobStatusChange.WHEN_CREATED
      }, //
      columnSortOrder = @ColumnSortOrder(value = BatchJobStatusChange.WHEN_CREATED,
          ascending = false))
  @ResponseBody
  public Object clientList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("batchJobId") final Long batchJobId) throws IOException {
    final String consumerKey = getConsumerKey();
    final Identifier batchJobIdentifier = Identifier.newIdentifier(batchJobId);
    final BatchJobService batchJobService = getBatchJobService();
    final Record batchJob = batchJobService.getBatchJob(batchJobIdentifier, consumerKey);
    if (batchJob == null) {
      throw new PageNotFoundException("Batch Job " + batchJobIdentifier + " does not exist.");
    } else {

      final Map<String, Object> parameters = new HashMap<>();

      final Map<String, Object> filter = new HashMap<>();
      filter.put("BATCH_JOB_ID", batchJobId);
      parameters.put("filter", filter);

      return newDataTableHandlerOrRedirect(request, response, "clientList", BatchJob.BATCH_JOB,
        "clientList", parameters);
    }
  }

  @RequestMapping(
      value = "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/statusChanges",
      method = RequestMethod.GET, title = "Status Changes", fieldNames = {
        BatchJobStatusChange.JOB_STATUS, //
        BatchJobStatusChange.WHEN_CREATED, //
        BatchJobStatusChange.WHO_CREATED
      }, columnSortOrder = @ColumnSortOrder(value = BatchJobStatusChange.WHEN_CREATED,
          ascending = false))
  @ResponseBody
  public Object moduleAppJobList(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Long batchJobId) throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    getModuleBusinessApplication(moduleName, businessApplicationName);
    getBatchJob(businessApplicationName, batchJobId);

    final Map<String, Object> parameters = new HashMap<>();

    final Map<String, Object> filter = new HashMap<>();
    filter.put("BATCH_JOB_ID", batchJobId);
    parameters.put("filter", filter);

    return newDataTableHandlerOrRedirect(request, response, "moduleAppJobList", BatchJob.BATCH_JOB,
      "moduleAppView", parameters);
  }
}
