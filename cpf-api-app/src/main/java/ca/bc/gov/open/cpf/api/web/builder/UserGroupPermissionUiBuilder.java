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

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserGroup;
import ca.bc.gov.open.cpf.api.domain.UserGroupPermission;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEvent;

import com.revolsys.record.Record;
import com.revolsys.record.io.format.xml.XmlWriter;
import com.revolsys.ui.html.fields.TextField;
import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.MultipleKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.web.annotation.RequestMapping;

@Controller
public class UserGroupPermissionUiBuilder extends CpfUiBuilder {

  protected UserGroupPermissionUiBuilder() {
    super("userGroupPermission", UserGroupPermission.USER_GROUP_PERMISSION,
      UserGroupPermission.USER_GROUP_PERMISSION_ID, "User Group Permission",
      "User Group Permissions");
  }

  public void adminUserGroupLink(final XmlWriter out, final Object object) {
    final Record userGroup = (Record)object;

    final Map<String, String> parameterNames = new HashMap<>();
    parameterNames.put("userGroupName", "userGroupName");

    final Map<String, Object> linkObject = new HashMap<>();
    final Object userGroupName = userGroup.getValue(UserGroup.USER_GROUP_NAME);
    linkObject.put(UserGroup.USER_GROUP_NAME, userGroupName);
    linkObject.put("userGroupName", userGroupName);

    final String moduleName = userGroup.getValue(UserGroup.MODULE_NAME);
    String pageName;
    if (UserGroupUiBuilder.GLOBAL_GROUP_NAMES.contains(moduleName)) {
      pageName = "groupView";
    } else if (moduleName.startsWith("ADMIN_MODULE_")) {
      pageName = "moduleAdminView";
      linkObject.put("moduleName", moduleName.substring(13));
      parameterNames.put("moduleName", "moduleName");
    } else {
      pageName = "moduleView";
      linkObject.put("moduleName", moduleName);
      parameterNames.put("moduleName", "moduleName");
    }

    serializeLink(out, linkObject, UserGroup.USER_GROUP_NAME, pageName, parameterNames);
  }

  @Override
  protected void initFields() {
    super.initFields();
    addField(new TextField("RESOURCE_CLASS", 70, 255, true));
    addField(new TextField("RESOURCE_ID", 70, 255, true));
    addField(new TextField("ACTION_NAME", 70, 255, true));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    addKeySerializer(
      new PageLinkKeySerializer("ID_LINK", "USER_GROUP_PERMISSION_ID", "ID", "moduleView"));
    addKeySerializer(new MultipleKeySerializer("moduleActions", "Actions") //
      .addSerializer(new ActionFormKeySerializer("moduleDelete", "Delete", "fa fa-trash") //
        .addParameterName("userGroupPermissionId", "USER_GROUP_PERMISSION_ID")));
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/permissions/add"
  }, title = "Add User Group Permission", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "RESOURCE_CLASS", "RESOURCE_ID", "ACTION_NAME", "ACTIVE_IND"
  })
  @ResponseBody
  public Element moduleAdd(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName)
    throws ServletException, IOException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);
    final Record group = getUserGroup(userGroupName);
    if (group != null) {
      final Long groupId = group.getLong(UserGroup.USER_GROUP_ID);
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put(UserGroupPermission.MODULE_NAME, moduleName);
      parameters.put(UserGroupPermission.USER_GROUP_ID, groupId);
      parameters.put(UserGroupPermission.ACTIVE_IND, 1);
      return newObjectAddPage(parameters, "module", "preInsert");
    }
    notFound(response, "User group " + userGroupName + " does not exist");
    return null;
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/permissions/{userGroupPermissionId}/delete"
  }, title = "Delete User Group Permission {userGroupPermissionId}", method = RequestMethod.POST)
  public void moduleDelete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("userGroupPermissionId") final Long userGroupPermissionId)
    throws ServletException, IOException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);
    final Record group = getUserGroup(userGroupName);
    if (group != null) {
      final Record permission = loadObject(userGroupPermissionId);
      if (permission != null) {
        if (permission.getValue(UserGroupPermission.MODULE_NAME).equals(moduleName)) {
          final Identifier userGroupId = group.getIdentifier();
          if (permission.getValue(UserGroupPermission.USER_GROUP_ID).equals(userGroupId)) {
            final CpfDataAccessObject dataAccessObject = getDataAccessObject();
            dataAccessObject.delete(permission);
            redirectToTab(UserGroup.USER_GROUP, "moduleView", "moduleList");
            return;
          }
        }
      }
    }
    notFound(response, "User group " + userGroupName + " does not exist");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/permissions/{userGroupPermissionId}/edit"
  }, title = "Edit User Group Permission {userGroupPermissionId}", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "RESOURCE_CLASS", "RESOURCE_ID", "ACTION_NAME", "ACTIVE_IND"
  })
  @ResponseBody
  public Element moduleEdit(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("userGroupPermissionId") final Long userGroupPermissionId)
    throws ServletException, IOException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);
    final Record group = getUserGroup(userGroupName);
    if (group != null) {
      final Record permission = loadObject(userGroupPermissionId);
      if (permission != null) {
        if (permission.getValue(UserGroupPermission.MODULE_NAME).equals(moduleName)) {
          final Identifier userGroupId = group.getIdentifier();
          if (permission.getValue(UserGroupPermission.USER_GROUP_ID).equals(userGroupId)) {
            return newObjectEditPage(permission, "module");
          }
        }
      }
    }
    notFound(response, "User group " + userGroupName + " does not exist");
    return null;
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/permissions"
  }, title = "User Group Permissions", method = RequestMethod.GET, fieldNames = {
    "ID_LINK", "RESOURCE_CLASS", "RESOURCE_ID", "ACTION_NAME", "ACTIVE_IND", "moduleActions"
  })
  @ResponseBody
  public Object moduleList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName)
    throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);
    final Record group = getUserGroup(userGroupName);
    if (group != null) {
      final Map<String, Object> parameters = new HashMap<>();

      final Map<String, Object> filter = new HashMap<>();
      filter.put(UserGroupPermission.MODULE_NAME, moduleName);
      final Identifier userGroupId = group.getIdentifier();
      filter.put(UserGroupPermission.USER_GROUP_ID, userGroupId);
      parameters.put("filter", filter);

      return newDataTableHandlerOrRedirect(request, response, "moduleList", UserGroup.USER_GROUP,
        "moduleView", parameters);
    }
    notFound(response, "User group " + userGroupName + " does not exist");
    return null;
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/permissions/{userGroupPermissionId}"
  }, title = "User Group Permission {userGroupPermissionId}", method = RequestMethod.GET,
      fieldNames = {
        "USER_GROUP_PERMISSION_ID", "MODULE_NAME", "adminUserGroupLink", "RESOURCE_CLASS",
        "RESOURCE_ID", "ACTION_NAME", "ACTIVE_IND", "WHO_CREATED", "WHEN_CREATED", "WHO_UPDATED",
        "WHEN_UPDATED", "moduleActions"
      })
  @ResponseBody
  public Element moduleView(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("userGroupPermissionId") final Long userGroupPermissionId)
    throws ServletException, IOException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);
    final Record group = getUserGroup(userGroupName);
    if (group != null) {
      final Record permission = loadObject(userGroupPermissionId);
      if (permission != null) {
        if (permission.getValue(UserGroupPermission.MODULE_NAME).equals(moduleName)) {
          final Identifier userGroupId = group.getIdentifier();
          if (permission.getValue(UserGroupPermission.USER_GROUP_ID).equals(userGroupId)) {
            final TabElementContainer tabs = new TabElementContainer();
            addObjectViewPage(tabs, permission, "module");
            return tabs;
          }
        }
      }
    }
    notFound(response, "User group " + userGroupName + " does not exist");
    return null;
  }

  @Override
  public void postInsert(final Record permission) {
    postUpdate(permission);
  }

  @Override
  public void postUpdate(final Record permission) {
    final String moduleName = permission.getValue(UserGroupPermission.MODULE_NAME);
    final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
    final Module module = businessApplicationRegistry.getModule(moduleName);
    if (module != null) {
      businessApplicationRegistry.moduleEvent(module, ModuleEvent.SECURITY_CHANGED);
    }
  }

}
