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

import java.util.List;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.api.scheduler.Worker;
import ca.bc.gov.open.cpf.api.scheduler.WorkerModuleState;

import com.revolsys.ui.html.serializer.key.BooleanImageKeySerializer;
import com.revolsys.ui.html.serializer.key.DateFormatKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;

@Controller
public class WorkerModuleStateUiBuilder extends CpfUiBuilder {

  public WorkerModuleStateUiBuilder() {
    super("workerModule", "Worker Module", "Worker Modules");
    setIdParameterName("moduleName");
    setIdPropertyName("name");
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();
    addKeySerializer(new PageLinkKeySerializer("name_link", "name", "Name", "workerView"));

    addKeySerializer(new BooleanImageKeySerializer("enabled"));

    addKeySerializer(new BooleanImageKeySerializer("started"));

    addKeySerializer(new DateFormatKeySerializer("startedDate", "Start Time"));
  }

  @RequestMapping(value = {
    "/admin/workers/{workerKey}/modules"
  }, title = "Modules", method = RequestMethod.GET, fieldNames = {
    "name_link", "status", "enabled", "started", "startedDate"
  })
  @ResponseBody
  public Object workerList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable final String workerKey) {
    checkHasAnyRole(ADMIN);
    final BatchJobService batchJobService = getBatchJobService();
    final Worker worker = batchJobService.getWorkerByKey(workerKey);
    if (worker == null) {
      throw new PageNotFoundException(
        "The worker " + workerKey + " could not be found. It may no longer be connected.");
    } else {
      final List<WorkerModuleState> modules = worker.getModules();
      return newDataTableHandlerOrRedirect(request, response, "workerList", modules, Worker.class,
        "view");
    }
  }

  @RequestMapping(value = {
    "/admin/workers/{workerKey}/modules/{moduleName}"
  }, title = "Module {moduleName}", method = RequestMethod.GET, fieldNames = {
    "name", "status", "enabled", "started", "startedDate", "moduleError"
  })
  @ResponseBody
  public Element workerView(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable final String workerKey, @PathVariable final String moduleName)
    throws ServletException {
    checkHasAnyRole(ADMIN);
    final BatchJobService batchJobService = getBatchJobService();
    final Worker worker = batchJobService.getWorkerByKey(workerKey);
    checkAdminOrModuleAdmin(moduleName);
    if (worker == null) {
      throw new PageNotFoundException(
        "The worker " + workerKey + " could not be found. It may no longer be connected.");
    } else {
      final WorkerModuleState module = worker.getModuleState(moduleName);
      if (module == null) {
        throw new PageNotFoundException(
          "The module " + moduleName + " is not running on worker " + workerKey);
      } else {
        final TabElementContainer tabs = new TabElementContainer();
        addObjectViewPage(tabs, module, "worker");

        return tabs;
      }
    }
  }
}
