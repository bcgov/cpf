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
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobFile;
import ca.bc.gov.open.cpf.api.domain.BatchJobResult;
import ca.bc.gov.open.cpf.api.domain.BatchJobStatusChange;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

import com.revolsys.identifier.Identifier;
import com.revolsys.record.Record;
import com.revolsys.record.io.format.xml.XmlWriter;
import com.revolsys.ui.html.builder.HtmlUiBuilder;
import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.DateFormatKeySerializer;
import com.revolsys.ui.html.serializer.key.KeySerializer;
import com.revolsys.ui.html.serializer.key.MapTableKeySerializer;
import com.revolsys.ui.html.serializer.key.MultipleKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.serializer.key.StringKeySerializer;
import com.revolsys.ui.html.view.ElementContainer;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.web.annotation.ColumnSortOrder;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.config.Page;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.rest.interceptor.MediaTypeUtil;
import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.util.Dates;
import com.revolsys.util.Property;

@Controller
public class BatchJobUiBuilder extends CpfUiBuilder {

  public BatchJobUiBuilder() {
    super("batchJob", BatchJob.BATCH_JOB, BatchJob.BATCH_JOB_ID, "Batch Job", "Batch Jobs");
  }

  public void businessApplication(final XmlWriter out, final Object object) {
    final Record batchJob = (Record)object;
    final String businessApplicationName = batchJob.getValue(BatchJob.BUSINESS_APPLICATION_NAME);
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName);
    final BusinessApplicationUiBuilder appBuilder = getBuilder(BusinessApplication.class);
    final Map<String, String> parameterKeys = new HashMap<>();
    parameterKeys.put("moduleName", "moduleName");
    parameterKeys.put("businessApplicationName", "name");
    appBuilder.serializeLink(out, businessApplication, "name", "moduleView", parameterKeys);
  }

  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}/cancelJob"
  }, method = RequestMethod.POST)
  public void clientCancel(@PathVariable("batchJobId") final Long batchJobId) {
    if (MediaTypeUtil.isHtmlPage()) {
      final Identifier jobId = Identifier.newIdentifier(batchJobId);
      final String consumerKey = getConsumerKey();
      final Record batchJob = getDataAccessObject().getBatchJob(consumerKey, jobId);
      if (batchJob == null) {
        throw new PageNotFoundException("The job " + batchJobId + " does not exist");
      } else {
        final BatchJobService batchJobService = getBatchJobService();
        batchJobService.cancelBatchJob(jobId);
        redirectPage("clientList");
      }
    } else {

    }
  }

  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}/delete"
  }, method = RequestMethod.POST)
  public void clientDelete(@PathVariable("batchJobId") final Long batchJobId) {
    final Identifier jobId = Identifier.newIdentifier(batchJobId);
    final String consumerKey = getConsumerKey();
    final Record batchJob = getDataAccessObject().getBatchJob(consumerKey, jobId);
    if (batchJob == null) {
      throw new PageNotFoundException("The job " + batchJobId + " does not exist");
    } else {
      final BatchJobService batchJobService = getBatchJobService();
      batchJobService.deleteJob(jobId);
      redirectPage("clientList");
    }
  }

  public void completedCount(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final int count = batchJob.getCompletedCount();
    out.text(count);
  }

  public void completedGroups(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final String text = batchJob.getCompletedGroups();
    out.text(text);
  }

  public void completedRequests(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final String text = batchJob.getCompletedRequests();
    if (Property.hasValue(text)) {
      boolean first = true;
      for (final String range : text.split(",")) {
        if (first) {
          first = false;
        } else {
          out.text(", ");
        }
        out.text(range);
      }
    } else {
      out.text('-');
    }
  }

  @Override
  protected Record convertRecord(final Record batchJob) {
    return getBatchJobService().getBatchJob(batchJob);
  }

  public void failedCount(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final int count = batchJob.getFailedCount();
    out.text(count);
  }

  public void failedRequests(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final String text = batchJob.getFailedRequests();
    if (Property.hasValue(text)) {
      boolean first = true;
      for (final String range : text.split(",")) {
        if (first) {
          first = false;
        } else {
          out.text(", ");
        }
        out.text(range);
      }
    } else {
      out.text('-');
    }
  }

  @Override
  public Object getProperty(final Object object, final String keyName) {
    if (keyName.startsWith("BUSINESS_APPLICATION_NAME") && object instanceof Record) {
      final Record batchJob = (Record)object;
      final String businessApplicationName = batchJob.getValue(BatchJob.BUSINESS_APPLICATION_NAME);
      BusinessApplication businessApplication = getBusinessApplicationRegistry()
        .getBusinessApplication(businessApplicationName);
      if (businessApplication == null) {
        businessApplication = new BusinessApplication(businessApplicationName);
      }
      final String subKey = Property.getSubName(keyName);
      if (Property.hasValue(subKey)) {
        final HtmlUiBuilder<?> uiBuilder = getBuilder(businessApplication);
        return uiBuilder.getProperty(businessApplication, subKey);
      } else {
        return businessApplication;
      }
    } else if (keyName.equals("jobStatusDate")) {
      return Dates.format("yyyy-MM-dd HH:mm:ss");
    } else {
      return super.getProperty(object, keyName);
    }
  }

  public void groupsToProcess(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final String text = batchJob.getGroupsToProcess();
    out.text(text);
  }

  @Override
  protected void initLabels() {
    super.initLabels();
    addLabel("jobStatusDate", "Job Status as of");
  }

  @Override
  protected void initPages() {
    super.initPages();
    newView("clientView", "BATCH_JOB_ID_CLIENT_LINK", "CLIENT_BUSINESS_APPLICATION_NAME_LINK",
      "USER_ID", "JOB_STATUS", "jobStatusDate", "businessApplicationParameterMap",
      "INPUT_DATA_CONTENT_TYPE", "STRUCTURED_INPUT_DATA_URL", "RESULT_DATA_CONTENT_TYPE",
      "WHEN_CREATED", "WHEN_UPDATED", "LAST_SCHEDULED_TIMESTAMP", "COMPLETED_TIMESTAMP",
      "expiryDate", "NOTIFICATION_URL", "NUM_SUBMITTED_REQUESTS", "completedRequests",
      "failedRequests", "GROUP_SIZE", "NUM_SUBMITTED_GROUPS", "completedGroups", "WHO_CREATED",
      "WHO_UPDATED", "clientActions");
    newView("clientAppList", "BATCH_JOB_ID_CLIENT_LINK", "WHEN_CREATED", "WHEN_UPDATED",
      "JOB_STATUS", "NUM_SUBMITTED_REQUESTS", "completedCount", "failedCount", "clientActions");

    newView("clientList", "BATCH_JOB_ID_CLIENT_LINK", "CLIENT_BUSINESS_APPLICATION_NAME_LINK",
      "WHEN_CREATED", "WHEN_UPDATED", "JOB_STATUS", "NUM_SUBMITTED_REQUESTS", "completedCount",
      "failedCount", "clientActions");

    setListSortOrder("clientList", Collections.singletonList(Arrays.asList(0, "desc")));
    setListSortOrder("clientAppList", Collections.singletonList(Arrays.asList(0, "desc")));

    addPage(new Page("clientList", "Batch Jobs", "/ws/jobs/"));
    addPage(new Page("clientView", "Batch Job {batchJobId}", "/ws/jobs/{batchJobId}/"));
    addPage(new Page("clientCancel", "Job", "/ws/jobs/{batchJobId}/cancelJob/"));
    addPage(new Page("clientDelete", "Job", "/ws/jobs/{batchJobId}/delete/"));
    addPage(new Page("clientAppList", "Batch Jobs", "/ws/apps/{businessApplicationName}/jobs/"));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    final KeySerializer completedCount = getKeySerializer("completedCount");
    completedCount.setProperty("sortable", false);
    completedCount.setProperty("searchable", false);

    final KeySerializer failedCount = getKeySerializer("failedCount");
    failedCount.setProperty("sortable", false);
    failedCount.setProperty("searchable", false);

    addKeySerializer(new PageLinkKeySerializer("listIdLink", "BATCH_JOB_ID", "ID", "moduleAppView") //
      .addParameterKey("moduleName", "BUSINESS_APPLICATION_NAME.module.name") //
      .addParameterKey("businessApplicationName", "BUSINESS_APPLICATION_NAME") //
      .addParameterKey("batchJobId", "BATCH_JOB_ID"));

    addKeySerializer(
      new PageLinkKeySerializer("BATCH_JOB_ID_CLIENT_LINK", "BATCH_JOB_ID", "ID", "clientView")
        .addParameterKey("batchJobId", "BATCH_JOB_ID"));

    addKeySerializer(new PageLinkKeySerializer("BUSINESS_APPLICATION_NAME_LINK",
      "BUSINESS_APPLICATION_NAME.name", "Business Application", "moduleView")//
        .addParameterKey("moduleName", "module.name")//
        .addParameterKey("businessApplicationName", "name") //
        .setSortFieldName("BUSINESS_APPLICATION_NAME"));

    addKeySerializer(new PageLinkKeySerializer("CLIENT_BUSINESS_APPLICATION_NAME_LINK",
      "BUSINESS_APPLICATION_NAME.title", "Business Application", "clientView") //
        .addParameterKey("businessApplicationName", "name")//
        .setSortFieldName("BUSINESS_APPLICATION_NAME"));

    final PageLinkKeySerializer moduleLinkSerializer = new PageLinkKeySerializer("MODULE_NAME_LINK",
      "BUSINESS_APPLICATION_NAME.module.name", "Module", "view") //
        .addParameterKey("moduleName", "name");
    moduleLinkSerializer.setProperty("searchable", false);
    moduleLinkSerializer.setProperty("sortable", false);
    addKeySerializer(moduleLinkSerializer);

    addKeySerializer(new DateFormatKeySerializer("LAST_SCHEDULED_TIMESTAMP",
      "Most Recent Request Scheduled Time"));
    addKeySerializer(new DateFormatKeySerializer("COMPLETED_TIMESTAMP", "Completion Time"));
    addKeySerializer(new MapTableKeySerializer(//
      "businessApplicationParameterMap", //
      "Business Application Parameter"//
    )//
      .setKeyLabel("Parameter")//
      .setValueLabel("Value")//
      .setKey("BUSINESS_APPLICATION_PARAMS"));
    addKeySerializer(new MapTableKeySerializer(//
      "propertyMap", //
      "Properties"//
    )//
      .setKeyLabel("Name")//
      .setValueLabel("Value")//
      .setKey("PROPERTIES"));
    addKeySerializer(new StringKeySerializer("NUM_SUBMITTED_GROUPS", "Group Count"));
    addKeySerializer(new StringKeySerializer("NUM_SUBMITTED_REQUESTS", "Request Count"));

    // Admin Actions
    final MultipleKeySerializer adminActions = new MultipleKeySerializer("adminActions", "Actions");
    addKeySerializer(adminActions);

    final ActionFormKeySerializer moduleAppCancel = new ActionFormKeySerializer("moduleAppCancel",
      "Cancel", "fa fa-stop");
    moduleAppCancel.setEnabledExpression(
      "!(#JOB_STATUS == 'downloadInitiated' or #JOB_STATUS == 'resultsCreated' or #JOB_STATUS == 'cancelled')");
    moduleAppCancel.addParameterName("moduleName", "BUSINESS_APPLICATION_NAME.module.name");
    moduleAppCancel.addParameterName("businessApplicationName", "BUSINESS_APPLICATION_NAME");
    moduleAppCancel.addParameterName("batchJobId", "BATCH_JOB_ID");
    adminActions.addSerializer(moduleAppCancel);

    final ActionFormKeySerializer moduleAppDelete = new ActionFormKeySerializer("moduleAppDelete",
      "Delete", "fa fa-trash-o");
    moduleAppDelete.setEnabledExpression(
      "#JOB_STATUS == 'downloadInitiated' or #JOB_STATUS == 'resultsCreated' or #JOB_STATUS == 'cancelled'");
    moduleAppDelete.addParameterName("moduleName", "BUSINESS_APPLICATION_NAME.module.name");
    moduleAppDelete.addParameterName("businessApplicationName", "BUSINESS_APPLICATION_NAME");
    moduleAppDelete.addParameterName("batchJobId", "BATCH_JOB_ID");
    adminActions.addSerializer(moduleAppDelete);

    // Client Actions
    final MultipleKeySerializer clientActions = new MultipleKeySerializer("clientActions",
      "Actions");
    addKeySerializer(clientActions);

    final ActionFormKeySerializer clientCancel = new ActionFormKeySerializer("clientCancel",
      "Cancel", "fa fa-stop");
    clientCancel.setEnabledExpression(
      "!(#JOB_STATUS == 'downloadInitiated' or #JOB_STATUS == 'resultsCreated' or #JOB_STATUS == 'cancelled')");
    clientCancel.addParameterName("batchJobId", "BATCH_JOB_ID");
    clientActions.addSerializer(clientCancel);

    final ActionFormKeySerializer clientDelete = new ActionFormKeySerializer("clientDelete",
      "Delete", "fa fa-trash-o");
    clientDelete.setEnabledExpression(
      "#JOB_STATUS == 'downloadInitiated' or #JOB_STATUS == 'resultsCreated' or #JOB_STATUS == 'cancelled'");
    clientDelete.addParameterName("batchJobId", "BATCH_JOB_ID");
    clientActions.addSerializer(clientDelete);
  }

  @RequestMapping(value = {
    "/admin/jobs"
  }, title = "Batch Jobs", method = RequestMethod.GET, fieldNames = {
    "listIdLink", "BUSINESS_APPLICATION_NAME_LINK", "WHEN_CREATED", "WHEN_UPDATED", "JOB_STATUS",
    "NUM_SUBMITTED_REQUESTS", "completedCount", "failedCount", "USER_ID", "adminActions"
  }, columnSortOrder = @ColumnSortOrder(value = "listIdLink", ascending = false),
      permission = "hasRole('ROLE_ADMIN') or hasRoleRegex('ROLE_ADMIN_MODULE_.*_ADMIN')")
  @ResponseBody
  public Object list(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException {
    checkAdminOrAnyModuleAdminExceptSecurity();
    HttpServletUtils.setAttribute("title", "Batch Jobs");
    return newDataTableHandler(request, "list");
  }

  public void module(final XmlWriter out, final Object object) {
    final Record batchJob = (Record)object;
    final String businessApplicationName = batchJob.getValue(BatchJob.BUSINESS_APPLICATION_NAME);

    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName);
    final Module module = businessApplication.getModule();
    final CpfUiBuilder appBuilder = getBuilder(Module.class);

    final Map<String, String> parameterKeys = new HashMap<>();
    parameterKeys.put("moduleName", "name");
    appBuilder.serializeLink(out, module, "name", "view", parameterKeys);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/cancel"
  }, title = "Cancel Batch Job {batchJobId}", method = RequestMethod.POST)
  public void moduleAppCancel(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Long batchJobId) throws IOException, ServletException {
    checkAdminOrAnyModuleAdminExceptSecurity();
    getModuleBusinessApplication(moduleName, businessApplicationName);
    final BatchJobService batchJobService = getBatchJobService();
    final Identifier jobId = Identifier.newIdentifier(batchJobId);
    batchJobService.cancelBatchJob(jobId);
    final String url = request.getHeader("Referer");
    if (Property.hasValue(url) && url.indexOf("/apps") != -1) {
      redirectPage("moduleAppList");
    } else {
      redirectPage("list");
    }
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}/delete"
  }, title = "Cancel Batch Job {batchJobId}", method = RequestMethod.POST)
  public void moduleAppDelete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Long batchJobId) throws IOException, ServletException {
    final Identifier jobId = Identifier.newIdentifier(batchJobId);
    checkAdminOrAnyModuleAdminExceptSecurity();
    getModuleBusinessApplication(moduleName, businessApplicationName);
    final BatchJobService batchJobService = getBatchJobService();
    batchJobService.deleteJob(jobId);
    final String url = request.getHeader("Referer");
    if (Property.hasValue(url) && url.indexOf("/apps") != -1) {
      redirectPage("moduleAppList");
    } else {
      redirectPage("list");
    }
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs"
  }, title = "Batch Jobs", method = RequestMethod.GET, fieldNames = {
    "listIdLink", "BUSINESS_APPLICATION_NAME_LINK", "WHEN_CREATED", "WHEN_UPDATED", "JOB_STATUS",
    "NUM_SUBMITTED_REQUESTS", "completedCount", "failedCount", "USER_ID", "adminActions"
  }, columnSortOrder = @ColumnSortOrder(value = "listIdLink", ascending = false))
  @ResponseBody
  public Object moduleAppList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    getModuleBusinessApplication(moduleName, businessApplicationName);

    final Map<String, Object> parameters = new HashMap<>();

    final Map<String, Object> filter = new HashMap<>();
    filter.put("BUSINESS_APPLICATION_NAME", businessApplicationName);
    parameters.put("filter", filter);

    return newDataTableHandlerOrRedirect(request, response, "moduleAppList",
      BusinessApplication.class, "moduleView", parameters);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/jobs/{batchJobId}"
  }, title = "Batch Job {batchJobId}", method = RequestMethod.GET, fieldNames = {
    "BATCH_JOB_ID", "BUSINESS_APPLICATION_NAME_LINK", "USER_ID", "JOB_STATUS", "jobStatusDate",
    "businessApplicationParameterMap", "INPUT_DATA_CONTENT_TYPE", "STRUCTURED_INPUT_DATA_URL",
    "RESULT_DATA_CONTENT_TYPE", "WHEN_CREATED", "WHEN_UPDATED", "LAST_SCHEDULED_TIMESTAMP",
    "COMPLETED_TIMESTAMP", "expiryDate", "NOTIFICATION_URL", "NUM_SUBMITTED_REQUESTS",
    "completedRequests", "failedRequests", "GROUP_SIZE", "groupsToProcess", "scheduledGroups",
    "completedGroups", "propertyMap", "WHO_CREATED", "WHO_UPDATED", "adminActions"
  })
  @ResponseBody
  public ElementContainer moduleAppView(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("batchJobId") final Integer batchJobId) throws IOException, ServletException {
    checkAdminOrAnyModuleAdminExceptSecurity();
    final BusinessApplication businessApplication = getModuleBusinessApplication(moduleName,
      businessApplicationName);
    final Record batchJob = getBatchJob(businessApplicationName, batchJobId);

    final TabElementContainer tabs = new TabElementContainer();
    addObjectViewPage(tabs, batchJob, "moduleApp");

    if (businessApplication.isPerRequestInputData()) {

    } else {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.TRUE);
      parameters.put("dom", "rtiS");
      parameters.put("ordering", false);
      addTabDataTable(tabs, "ExecutionGroup", "moduleAppJobList", parameters);
    }
    {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.TRUE);
      parameters.put("dom", "rtiS");
      parameters.put("ordering", false);
      addTabDataTable(tabs, BatchJobFile.BATCH_JOB_FILE, "moduleAppJobGroupResultList", parameters);
      addTabDataTable(tabs, BatchJobFile.BATCH_JOB_FILE, "moduleAppJobGroupErrorList", parameters);
    }
    {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.TRUE);
      addTabDataTable(tabs, BatchJobResult.BATCH_JOB_RESULT, "moduleAppJobList", parameters);
    }
    {
      final Map<String, Object> parameters = new LinkedHashMap<>();
      final List<List<Object>> sorting = Collections
        .singletonList(Arrays.<Object> asList(1, "asc"));
      parameters.put("order", sorting);
      addTabDataTable(tabs, BatchJobStatusChange.BATCH_JOB_STATUS_CHANGE, "moduleAppJobList",
        parameters);
    }
    return tabs;
  }

  public void scheduledGroups(final XmlWriter out, final Object object) {
    final BatchJob batchJob = (BatchJob)object;
    final String text = batchJob.getScheduledGroups();
    out.text(text);
  }
}
