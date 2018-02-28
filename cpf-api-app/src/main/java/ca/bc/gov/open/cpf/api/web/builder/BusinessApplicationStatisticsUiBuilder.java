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
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.scheduler.BusinessApplicationStatistics;
import ca.bc.gov.open.cpf.api.scheduler.DurationType;
import ca.bc.gov.open.cpf.api.scheduler.StatisticsService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;

import com.revolsys.collection.list.Lists;
import com.revolsys.record.Record;
import com.revolsys.record.io.format.xml.XmlWriter;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.web.annotation.ColumnSortOrder;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.config.Page;
import com.revolsys.ui.web.exception.PageNotFoundException;

@Controller
public class BusinessApplicationStatisticsUiBuilder extends CpfUiBuilder {

  public BusinessApplicationStatisticsUiBuilder() {
    super("statistic", "Business Application Statistic", "Business Application LabelCountMap");
    setIdParameterName("statisticId");
    setIdPropertyName("id");
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
    "/admin/dashboard"
  }, title = "Dashboard", method = RequestMethod.GET,
      permission = "hasRole('ROLE_ADMIN') or hasRoleRegex('ROLE_ADMIN_MODULE_.*_ADMIN')")
  @ResponseBody
  public Object dashboard(final HttpServletRequest request) {
    checkAdminOrAnyModuleAdminExceptSecurity();
    setPageTitle(request, "summary");

    final TabElementContainer tabs = new TabElementContainer();

    final Map<String, Object> parameters = new HashMap<>();
    parameters.put("serverSide", Boolean.FALSE);

    addTabDataTable(tabs, this, "hourList", parameters);

    addTabDataTable(tabs, this, "dayList", parameters);

    addTabDataTable(tabs, this, "monthList", parameters);

    addTabDataTable(tabs, this, "yearList", parameters);

    return tabs;
  }

  @Override
  public Object getProperty(final Object object, final String keyName) {
    if (object instanceof BusinessApplicationStatistics) {
      final BusinessApplicationStatistics statistics = (BusinessApplicationStatistics)object;
      if (keyName.equals("businessApplication")) {
        final String businessApplicationName = statistics.getBusinessApplicationName();

        final BusinessApplication businessApplication = getBusinessApplication(
          businessApplicationName);
        return businessApplication;
      } else if (keyName.equals("module")) {
        final String businessApplicationName = statistics.getBusinessApplicationName();

        final BusinessApplication businessApplication = getBusinessApplication(
          businessApplicationName);
        if (businessApplication == null) {
          return null;
        } else {
          return businessApplication.getModule();
        }
      } else if (keyName.equals("moduleName")) {
        final String businessApplicationName = statistics.getBusinessApplicationName();

        final BusinessApplication businessApplication = getBusinessApplication(
          businessApplicationName);
        if (businessApplication == null) {
          return null;
        } else {
          return businessApplication.getModule().getName();
        }
      }
    }
    return super.getProperty(object, keyName);
  }

  public List<BusinessApplicationStatistics> getStatistics(
    final BusinessApplication businessApplication) {
    final String businessApplicationName = businessApplication.getName();
    final StatisticsService statisticsService = getStatisticsService();
    final List<BusinessApplicationStatistics> statistics = statisticsService
      .getStatisticsList(businessApplicationName);
    Collections.reverse(statistics);
    return statistics;
  }

  public List<BusinessApplicationStatistics> getSummaryStatistics(final DurationType durationType) {
    final String statisticId = durationType.getId();
    final List<BusinessApplication> apps = getBusinessApplications();
    final List<BusinessApplicationStatistics> statistics = new ArrayList<>();
    for (final BusinessApplication businessApplication : apps) {
      final String businessApplicationName = businessApplication.getName();
      final StatisticsService statisticsService = getStatisticsService();
      final BusinessApplicationStatistics statistic = statisticsService
        .getStatistics(businessApplicationName, statisticId);
      statistics.add(statistic);
    }
    return statistics;
  }

  @Override
  protected void initLabels() {
    super.initLabels();
    addLabel("module.started", "Module Started");
    addLabel("submittedJobsCount", "# Jobs Submitted");
    addLabel("completedJobsCount", "# Jobs Completed");
    addLabel("completedRequestsCount", "# Requests Completed");
    addLabel("applicationExecutedGroupsCount", "# App Groups Executed");
    addLabel("applicationExecutedRequestsCount", "# App Requests Completed");
    addLabel("applicationExecutedFailedRequestsCount", "# App Requests Failed");
  }

  @Override
  protected void initPages() {
    super.initPages();
    for (final String key : Lists.newArray("hourList", "dayList", "monthList", "yearList")) {
      newView(key,
        Lists.newArray("moduleAppViewLink", "module.name_link", "module.started",
          "businessApplication.name_link", "submittedJobsCount", "completedJobsCount",
          "applicationExecutedGroupsCount", "applicationExecutedRequestsCount",
          "applicationExecutedFailedRequestsCount"));
    }

    addPage(new Page("hourList", "Hour", "/admin/dashboard/hour/"));
    addPage(new Page("dayList", "Today", "/admin/dashboard/day/"));
    addPage(new Page("monthList", "Month", "/admin/dashboard/month/"));
    addPage(new Page("yearList", "Year", "/admin/dashboard/year/"));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();
    addKeySerializer(new PageLinkKeySerializer("moduleAppViewLink", "id", "ID", "moduleAppView")
      .addParameterKey("moduleName", "moduleName") //
      .addParameterKey("businessApplicationName", "businessApplicationName") //
      .addParameterKey("statisticId", "id"));
    addKeySerializer(
      new PageLinkKeySerializer("listModuleAppViewLink", "id", "ID", "moduleAppView"));
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/dashboard"
  }, title = "Dashboard", method = RequestMethod.GET, fieldNames = {
    "listModuleAppViewLink", "durationType", "submittedJobsCount", "completedJobsCount",
    "applicationExecutedGroupsCount", "applicationExecutedRequestsCount",
    "applicationExecutedFailedRequestsCount"
  }, columnSortOrder = @ColumnSortOrder("listModuleAppViewLink"))
  @ResponseBody
  public Object moduleAppList(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("moduleName") String moduleName,
    final @PathVariable("businessApplicationName") String businessApplicationName)
    throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    final BusinessApplication businessApplication = getBusinessApplicationRegistry()
      .getModuleBusinessApplication(moduleName, businessApplicationName);
    if (businessApplication != null) {
      return newDataTableHandlerOrRedirect(request, "moduleAppList", () -> {
        return getStatistics(businessApplication);
      }, BusinessApplication.class, "moduleView");
    }
    throw new PageNotFoundException("Business Application " + businessApplicationName
      + " does not exist for module " + moduleName);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/dashboard/{statisticId}"
  }, title = "Dashboard for {businessApplicationName}", method = RequestMethod.GET)
  public ModelAndView moduleAppView(final HttpServletRequest request,
    final HttpServletResponse response, final @PathVariable("moduleName") String moduleName,
    final @PathVariable("businessApplicationName") String businessApplicationName,
    final @PathVariable("statisticId") String statisticId) throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    try {
      final BusinessApplication businessApplication = getBusinessApplicationRegistry()
        .getModuleBusinessApplication(moduleName, businessApplicationName);
      if (businessApplication != null) {
        final StatisticsService statisticsService = getStatisticsService();
        final BusinessApplicationStatistics statistics = statisticsService
          .getStatistics(businessApplicationName, statisticId);

        if (statistics != null) {
          final ModelAndView viewPage = newStatsViewPage(businessApplicationName, statistics);

          return viewPage;
        }
      }
    } catch (final IllegalArgumentException e) {
    }
    throw new PageNotFoundException();
  }

  public ModelAndView newStatsViewPage(final String businessApplicationName,
    final BusinessApplicationStatistics stats) {
    final ModelMap model = new ModelMap();
    model.put("title", businessApplicationName + " LabelCountMap " + stats.getId());
    model.put("statisitcs", stats);
    model.put("body", "/WEB-INF/jsp/builder/businessApplicationStatisticsView.jsp");
    return new ModelAndView("/jsp/template/page", model);
  }

  @RequestMapping(value = {
    "/admin/dashboard/{durationType}"
  }, method = RequestMethod.GET, fieldNames = {
    "moduleAppViewLink", "module.name_link", "module.started", "businessApplication.name_link",
    "submittedJobsCount", "completedJobsCount", "applicationExecutedGroupsCount",
    "applicationExecutedRequestsCount", "applicationExecutedFailedRequestsCount"
  })
  @ResponseBody
  public Object summaryList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("durationType") final String durationType) throws IOException {
    checkAdminOrAnyModuleAdminExceptSecurity();
    final DurationType type = DurationType.getDurationType(durationType);
    return newDataTableHandlerOrRedirect(request, durationType + "List", () -> {
      return getSummaryStatistics(type);
    }, this, "summary");
  }

}
