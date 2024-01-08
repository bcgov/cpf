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
import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.date.Dates;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobResult;
import ca.bc.gov.open.cpf.api.domain.Common;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;

import com.revolsys.collection.list.Lists;
import com.revolsys.record.Record;
import com.revolsys.record.io.format.xml.XmlWriter;
import com.revolsys.record.query.And;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.DateFormatKeySerializer;
import com.revolsys.ui.html.serializer.key.KeySerializer;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.config.Page;
import com.revolsys.ui.web.exception.PageNotFoundException;

@Controller
public class BatchJobResultUiBuilder extends CpfUiBuilder {

  public BatchJobResultUiBuilder() {
    super("batchJobResult", BatchJobResult.BATCH_JOB_RESULT, BatchJobResult.SEQUENCE_NUMBER,
      "Batch Job Result", "Batch Job Results");
    setIdParameterName("sequenceNumber");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/results/{sequenceNumber}/download"
  }, title = "Download", method = {
    RequestMethod.GET, RequestMethod.POST
  })
  @ResponseBody
  public void download(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Long batchJobId,
    @PathVariable("sequenceNumber") final Integer sequenceNumber) throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    getModuleBusinessApplication(moduleName, businessApplicationName);
    getBatchJob(businessApplicationName, batchJobId);
    final Identifier jobId = Identifier.newIdentifier(batchJobId);

    final Record batchJobResult = getBatchJobResult(jobId, sequenceNumber);
    final BatchJobService batchJobService = getBatchJobService();
    if (batchJobResult == null) {
      throw new PageNotFoundException("Batch Job result " + sequenceNumber + " does not exist.");
    } else {
      batchJobService.downloadBatchJobResult(request, response, jobId, sequenceNumber,
        batchJobResult);
    }
  }

  public void expiryDate(final XmlWriter out, final Object object) throws IOException {
    final Record batchJobResult = (Record)object;
    final Date completionTimestamp = batchJobResult.getValue(Common.WHEN_CREATED);
    if (completionTimestamp == null) {
      out.append('-');
    } else {
      final java.sql.Date expiryDate = getBatchJobService().getExpiryDate(completionTimestamp);
      out.append(Dates.format("yyyy-MM-dd", expiryDate));
    }
  }

  public Record getBatchJobResult(final Identifier batchJobId, final Integer sequenceNumber) {
    final And where = Q.and(Q.equal(BatchJobResult.BATCH_JOB_ID, batchJobId),
      Q.equal(BatchJobResult.SEQUENCE_NUMBER, sequenceNumber));
    final Query query = new Query(BatchJobResult.BATCH_JOB_RESULT, where);
    final Record batchJobResult = getRecordStore().getRecords(query).getFirst();

    if (batchJobResult != null) {
      return batchJobResult;
    }
    throw new PageNotFoundException(
      "Job " + batchJobId + " or result " + sequenceNumber + " does not exist");
  }

  @Override
  protected void initLabels() {
    addLabel("SEQUENCE_NUMBER", "#");
    addLabel("BATCH_JOB_RESULT_TYPE", "Result Type");
    addLabel("RESULT_DATA_CONTENT_TYPE", "Content Type");
  }

  @Override
  protected void initPages() {
    super.initPages();
    addPage(new Page("clientList", "Results", "/ws/jobs/{batchJobId}/results/"));
    addPage(
      new Page("clientDownload", "Download", "/ws/jobs/{batchJobId}/results/{sequenceNumber}/"));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    addKeySerializer(new DateFormatKeySerializer("DOWNLOAD_TIMESTAMP", "Download Time"));
    addKeySerializer(new DateFormatKeySerializer("WHEN_CREATED", "Creation Time"));
    addKeySerializer(new ActionFormKeySerializer("download", "Download", "fa fa-download")//
      .setTarget("_top"));

    newView("clientList",
      Lists.newArray("SEQUENCE_NUMBER", "BATCH_JOB_RESULT_TYPE", "RESULT_DATA_CONTENT_TYPE",
        "expiryDate", new ActionFormKeySerializer("clientDownload", "Download", "fa fa-download") //
          .setTarget("_top")));
    final KeySerializer expiryDateSerializer = getKeySerializer("expiryDate");
    expiryDateSerializer.setProperty("sortable", false);
    expiryDateSerializer.setProperty("searchable", false);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/results"
  }, title = "Result Files", method = RequestMethod.GET, fieldNames = {
    "SEQUENCE_NUMBER", "download", "BATCH_JOB_RESULT_TYPE", "RESULT_DATA_CONTENT_TYPE",
    "expiryDate", "WHEN_CREATED", "DOWNLOAD_TIMESTAMP"
  })
  @ResponseBody
  public Object moduleAppJobList(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable final String moduleName,
    @PathVariable final String businessApplicationName, @PathVariable final Long batchJobId)
    throws IOException {
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
