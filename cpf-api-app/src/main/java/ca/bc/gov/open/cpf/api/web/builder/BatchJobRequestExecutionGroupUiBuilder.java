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

import java.util.Collection;
import java.util.List;
import java.util.concurrent.Callable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.scheduler.BatchJobRequestExecutionGroup;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.api.scheduler.Worker;

import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.DateFormatKeySerializer;
import com.revolsys.ui.html.serializer.key.MultipleKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.utils.HttpServletUtils;

@Controller
public class BatchJobRequestExecutionGroupUiBuilder extends CpfUiBuilder {
  private final Callable<Collection<? extends Object>> workerExecutionGroupsCallable = this::getWorkerExecutionGroups;

  public BatchJobRequestExecutionGroupUiBuilder() {
    super("executionGroup", "Execution Group", "Execution Groups");
  }

  public List<BatchJobRequestExecutionGroup> getWorkerExecutionGroups() {
    final String workerKey = HttpServletUtils.getPathVariable("workerKey");
    final BatchJobService batchJobService = getBatchJobService();
    final Worker worker = batchJobService.getWorkerByKey(workerKey);
    if (worker == null) {
      throw new PageNotFoundException(
        "The worker " + workerKey + " could not be found. It may no longer be connected.");
    } else {

      final List<BatchJobRequestExecutionGroup> executingGroups = worker.getExecutingGroups();
      return executingGroups;
    }
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    addKeySerializer(new PageLinkKeySerializer("module.name", "module", "Module", "view"));
    addKeySerializer(new PageLinkKeySerializer("businessApplication.name", "businessApplication",
      "BusinessApplication", "moduleView"));
    addKeySerializer(new DateFormatKeySerializer("scheduleTimestamp"));

    final MultipleKeySerializer actions = new MultipleKeySerializer("actions");
    actions.addSerializer(new ActionFormKeySerializer("workerRestart", "Restart", "fa fa-repeat")
      .addParameterName("executionGroupId", "id"));
    addKeySerializer(actions);
  }

  @RequestMapping(value = {
    "/admin/workers/{workerKey}/executingGroups"
  }, title = "Executing Groups", method = RequestMethod.GET, fieldNames = {
    "id", "module.name", "businessApplication.name", "scheduleTimestamp", "actions"
  }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public Object workerList(final HttpServletRequest request, @PathVariable final String workerKey) {
    checkHasAnyRole(ADMIN);
    final BatchJobService batchJobService = getBatchJobService();
    final Worker worker = batchJobService.getWorkerByKey(workerKey);
    if (worker == null) {
      throw new PageNotFoundException(
        "The worker " + workerKey + " could not be found. It may no longer be connected.");
    } else {
      return newDataTableHandler(request, "workerList", this.workerExecutionGroupsCallable);
    }
  }

  @RequestMapping(value = {
    "/admin/workers/{workerKey}/executingGroups/{executionGroupId}/restart"
  }, title = "Restart Group", method = RequestMethod.POST, permission = "hasRole('ROLE_ADMIN')")
  public void workerRestart(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable final String workerKey, @PathVariable final String executionGroupId) {
    checkHasAnyRole(ADMIN);
    final BatchJobService batchJobService = getBatchJobService();
    final Worker worker = batchJobService.getWorkerByKey(workerKey);
    if (worker != null) {
      batchJobService.cancelGroup(worker, executionGroupId);
    }
    referrerRedirect(request);
  }
}
