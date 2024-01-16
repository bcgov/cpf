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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;
import ca.bc.gov.open.cpf.api.domain.UserGroup;
import ca.bc.gov.open.cpf.api.domain.UserGroupAccountXref;
import ca.bc.gov.open.cpf.api.domain.UserGroupPermission;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

import com.revolsys.record.Record;
import com.revolsys.record.io.format.xml.XmlWriter;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.ui.html.fields.Field;
import com.revolsys.ui.html.fields.TextAreaField;
import com.revolsys.ui.html.fields.TextField;
import com.revolsys.ui.html.form.Form;
import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.KeySerializer;
import com.revolsys.ui.html.serializer.key.MultipleKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.utils.HttpServletUtils;

@Controller
public class UserGroupUiBuilder extends CpfUiBuilder implements UserGroup {
  public static final List<String> GLOBAL_GROUP_NAMES = Arrays.asList("ADMIN", "USER_TYPE",
    "GLOBAL", "WORKER");

  public UserGroupUiBuilder() {
    super("userGroup", UserGroup.USER_GROUP, UserGroup.USER_GROUP_NAME, "User Group",
      "User Groups");
    setIdParameterName("userGroupName");
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
    if (GLOBAL_GROUP_NAMES.contains(moduleName)) {
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

  public List<String> getUserGroupModuleNames(final String moduleName) {
    return Arrays.asList(moduleName, "USER_TYPE", "GLOBAL");
  }

  @RequestMapping(value = {
    "/admin/userGroups/add"
  }, title = "Add User Group", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND",
  })
  @ResponseBody
  public Element groupAdd(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException, ServletException {
    final Map<String, Object> defaultValues = new HashMap<>();
    defaultValues.put(UserGroup.ACTIVE_IND, 1);
    defaultValues.put(UserGroup.MODULE_NAME, "GLOBAL");
    defaultValues.put(UserGroup.USER_GROUP_NAME, "GLOBAL_");
    return super.newObjectAddPage(defaultValues, "group", "preInsert");
  }

  @RequestMapping(value = {
    "/admin/userGroups/{userGroupName}/delete"
  }, title = "Delete User Group", method = RequestMethod.POST,
      permission = "#userGroupName.startsWith('GLOBAL_')")
  public void groupDelete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("userGroupName") final String userGroupName)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);

    final Record userGroup = getUserGroup(userGroupName);
    if (userGroup != null && userGroup.getValue(UserGroup.MODULE_NAME).equals("GLOBAL")) {
      final CpfDataAccessObject dataAccessObject = getDataAccessObject();
      dataAccessObject.deleteUserGroup(userGroup);
      redirectPage("groupList");
    }
  }

  @RequestMapping(value = {
    "/admin/userGroups/{userGroupName}/edit"
  }, title = "Edit User Group {userGroupName}", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND",
  }, permission = "#userGroupName.startsWith('GLOBAL_')")
  @ResponseBody
  public Element groupEdit(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("userGroupName") String userGroupName)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    final Record userGroup = getUserGroup(userGroupName);
    return super.newObjectEditPage(userGroup, "group");
  }

  @RequestMapping(value = {
    "/admin/userGroups"
  }, title = "User Groups", method = RequestMethod.GET, fieldNames = {
    "adminUserGroupLink", "MODULE_NAME", "DESCRIPTION", "ACTIVE_IND", "globalActions",
  }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public Object groupList(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException {
    checkHasAnyRole(ADMIN);
    HttpServletUtils.setAttribute("title", "User Groups");
    return newDataTableHandler(request, "groupList");
  }

  @RequestMapping(value = {
    "/admin/userGroups/{userGroupName}"
  }, title = "User Group {userGroupName}", method = RequestMethod.GET, fieldNames = {
    "USER_GROUP_ID", "MODULE_NAME", "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND", "WHO_CREATED",
    "WHEN_CREATED", "WHO_UPDATED", "WHEN_UPDATED", "globalActions",
  })
  @ResponseBody
  public Element groupView(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("userGroupName") String userGroupName)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    return newUserGroupView(request, response, "group", "group", null, userGroupName, null);
  }

  @Override
  protected void initFields() {
    super.initFields();
    addField(new TextField("USER_GROUP_NAME", true));
    addField(new TextAreaField("DESCRIPTION", false));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    final MultipleKeySerializer globalActions = new MultipleKeySerializer("globalActions",
      "Actions");
    globalActions.addSerializer(new ActionFormKeySerializer("groupDelete", "Delete", "fa fa-trash")
      .addParameterName("userGroupName", "USER_GROUP_NAME"));
    addKeySerializer(globalActions);

    addKeySerializer(
      new PageLinkKeySerializer("adminGroupView", USER_GROUP_NAME, "Group", "groupView"));

    addKeySerializer(new PageLinkKeySerializer("moduleAdminViewLink", USER_GROUP_NAME,
      "User Group Name", "moduleAdminView"));

    addKeySerializer(new PageLinkKeySerializer("moduleViewLink", USER_GROUP_NAME, "User Group Name",
      "moduleView"));

    final MultipleKeySerializer userAccountActions = new MultipleKeySerializer("userAccountActions",
      "Actions");
    userAccountActions
      .addSerializer(new ActionFormKeySerializer("userAccountMemberDelete", "Delete", "fa fa-trash")
        .addParameterName("userGroupName", "USER_GROUP_NAME"));
    addKeySerializer(userAccountActions);

    final MultipleKeySerializer moduleActions = new MultipleKeySerializer("moduleViewActions",
      "Actions");
    moduleActions.addSerializer(new ActionFormKeySerializer("moduleDelete", "Delete", "fa fa-trash")
      .addParameterName("userGroupName", "USER_GROUP_NAME"));
    addKeySerializer(moduleActions);

    final KeySerializer keySerializer = getKeySerializer("adminUserGroupLink");
    keySerializer.setKey(UserGroup.USER_GROUP_NAME);
    keySerializer.setLabel("Name");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/add"
  }, title = "User Group Add", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND",
  })
  @ResponseBody
  public Element moduleAdd(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName) throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Map<String, Object> parameters = new HashMap<>();
    parameters.put(UserGroup.ACTIVE_IND, 1);
    parameters.put(UserGroup.MODULE_NAME, moduleName);
    parameters.put(UserGroup.USER_GROUP_NAME, moduleName + "_");
    return newObjectAddPage(parameters, "module", "preInsert");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/adminUserGroups"
  }, title = "Module Admin User Groups", method = RequestMethod.GET, fieldNames = {
    "moduleAdminViewLink", "DESCRIPTION", "ACTIVE_IND",
  }, permission = "hasRole('ROLE_ADMIN')  or hasRole('ROLE_ADMIN_MODULE_' + #moduleName + '_ADMIN')")
  @ResponseBody
  public Object moduleAdminList(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Map<String, Object> parameters = new HashMap<>();

    final Map<String, Object> filter = new HashMap<>();
    filter.put(UserGroup.MODULE_NAME, "ADMIN_MODULE_" + moduleName);

    parameters.put("filter", filter);

    return newDataTableHandlerOrRedirect(request, response, "moduleAdminList", Module.class, "view",
      parameters);

  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/adminUserGroups/{userGroupName}"
  }, method = RequestMethod.GET, fieldNames = {
    "USER_GROUP_ID", "MODULE_NAME", "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND", "WHO_CREATED",
    "WHEN_CREATED", "WHO_UPDATED", "WHEN_UPDATED",
  }, title = "Admin User Group {userGroupName}")
  @ResponseBody
  public Element moduleAdminView(final HttpServletRequest request,
    final HttpServletResponse response, final @PathVariable("userGroupName") String userGroupName,
    @PathVariable("moduleName") final String moduleName) throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    return newUserGroupView(request, response, "moduleAdmin", "moduleGroupAdmin", moduleName,
      userGroupName, Arrays.asList("ADMIN_MODULE_" + moduleName));

  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/delete"
  }, title = "User Group Delete", method = RequestMethod.POST,
      permission = "#userGroupName.startsWith(#moduleName) and (hasRole('ROLE_ADMIN') or hasRole('ROLE_ADMIN_MODULE_' + #moduleName + '.*'))")
  public void moduleDelete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName)
    throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Record userGroup = getUserGroup(userGroupName);
    if (userGroup != null && userGroup.getValue(UserGroup.MODULE_NAME).equals(moduleName)) {
      final CpfDataAccessObject dataAccessObject = getDataAccessObject();
      dataAccessObject.deleteUserGroup(userGroup);
      redirectPage("moduleList");
    }
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}/edit"
  }, title = "User Group {userGroupName} Edit", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND",
  }, permission = "#userGroupName.startsWith(#moduleName)")
  @ResponseBody
  public Element moduleEdit(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("userGroupName") String userGroupName,
    @PathVariable("moduleName") final String moduleName) throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Map<String, Object> parameters = new HashMap<>();
    parameters.put("moduleName", moduleName);

    final Record userGroup = getUserGroup(userGroupName);
    if (userGroup != null && userGroup.getValue(UserGroup.MODULE_NAME).equals(moduleName)) {
      return newObjectEditPage(userGroup, "module");
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups"
  }, title = "User Groups", method = RequestMethod.GET, fieldNames = {
    "moduleViewLink", "DESCRIPTION", "ACTIVE_IND", "moduleViewActions"
  })
  @ResponseBody
  public Object moduleList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName) throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Map<String, Object> parameters = new HashMap<>();

    final Map<String, Object> filter = new HashMap<>();
    final List<String> moduleNames = getUserGroupModuleNames(moduleName);
    filter.put("MODULE_NAME", moduleNames);

    parameters.put("filter", filter);

    return newDataTableHandlerOrRedirect(request, response, "moduleList", Module.class, "view",
      parameters);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/userGroups/{userGroupName}"
  }, title = "User Group {userGroupName}", method = RequestMethod.GET, fieldNames = {
    "USER_GROUP_ID", "MODULE_NAME", "USER_GROUP_NAME", "DESCRIPTION", "ACTIVE_IND", "WHO_CREATED",
    "WHEN_CREATED", "WHO_UPDATED", "WHEN_UPDATED", "moduleViewActions"
  })
  @ResponseBody
  public Element moduleView(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("userGroupName") String userGroupName,
    @PathVariable("moduleName") final String moduleName) throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    return newUserGroupView(request, response, "module", "moduleGroup", moduleName, userGroupName,
      getUserGroupModuleNames(moduleName));
  }

  public Element newUserGroupView(final HttpServletRequest request,
    final HttpServletResponse response, final String prefix, final String membersPrefix,
    final String moduleName, final String userGroupName, final List<String> moduleNames) {
    if (moduleName != null) {
      hasModule(request, moduleName);
    }

    final Record userGroup = getUserGroup(userGroupName);
    if (userGroup != null
      && (moduleName == null || moduleNames.contains(userGroup.getValue(UserGroup.MODULE_NAME)))) {
      final TabElementContainer tabs = new TabElementContainer();
      addObjectViewPage(tabs, userGroup, prefix);

      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.TRUE);

      if (!userGroup.getValue(UserGroup.MODULE_NAME).equals("USER_TYPE")) {
        final UserAccountUiBuilder userAccountUiBuilder = getBuilder(UserAccount.USER_ACCOUNT);
        userAccountUiBuilder.addMembersDataTable(tabs, membersPrefix);
      }

      addTabDataTable(tabs, UserGroupPermission.USER_GROUP_PERMISSION, prefix + "List", parameters);

      return tabs;
    }
    throw new PageNotFoundException();
  }

  @Override
  public boolean preInsert(final Form form, final Record userGroup) {
    final Field nameField = form.getField(UserGroup.USER_GROUP_NAME);
    String groupName = nameField.getValue();
    groupName = groupName.toUpperCase();
    nameField.setValue(groupName);
    userGroup.setValue(UserGroup.USER_GROUP_NAME, groupName);

    final Record group = getUserGroup(groupName);
    if (group == null) {
      final String moduleName = userGroup.getValue(UserGroup.MODULE_NAME);
      if (!groupName.startsWith(moduleName + "_")) {
        nameField.addValidationError("Must start with " + moduleName + "_");
        return false;
      } else if (!groupName.matches("[A-Z0-9_]+")) {
        nameField.addValidationError("Can only contain the characters A-Z, 0-9, and _.");
        return false;
      } else {
        final int groupNameLength = groupName.length();
        final int minLength = moduleName.length() + 2;
        if (groupNameLength < minLength) {

          nameField.addValidationError("Group name must have a suffix");
          return false;
        }
      }
      return true;
    } else {
      nameField.addValidationError("Group name is already used");
      return false;
    }
  }

  @Override
  public boolean preUpdate(final Form form, final Record userGroup) {
    final Long userGroupId = userGroup.getLong(UserGroup.USER_GROUP_ID);
    final Field nameField = form.getField(UserGroup.USER_GROUP_NAME);
    String groupName = nameField.getValue();
    groupName = groupName.toUpperCase();
    nameField.setValue(groupName);
    userGroup.setValue(UserGroup.USER_GROUP_NAME, groupName);

    final Record group = getUserGroup(groupName);
    if (group == null || group.getLong(UserGroup.USER_GROUP_ID).equals(userGroupId)) {
      final String moduleName = userGroup.getValue(UserGroup.MODULE_NAME);
      if (!groupName.startsWith(moduleName + "_")) {
        nameField.addValidationError("Group name must start with " + moduleName + "_");
        return false;
      } else if (!groupName.matches("[A-Z0-9_]+")) {
        nameField.addValidationError("Can only contain the characters A-Z, 0-9, and _.");
        return false;
      } else {
        final int groupNameLength = groupName.length();
        final int minLength = moduleName.length() + 2;
        if (groupNameLength < minLength) {

          nameField.addValidationError("Group name must have a suffix");
          return false;
        }
      }
      HttpServletUtils.setPathVariable("userGroupName", groupName);
      return true;
    } else {
      nameField.addValidationError("Group name is already used");
      return false;
    }
  }

  @RequestMapping(value = "/admin/userAccounts/{consumerKey}/userGroups",
      method = RequestMethod.GET, title = "User Groups", fieldNames = {
        "adminGroupView", MODULE_NAME, DESCRIPTION, ACTIVE_IND, GROUP_XREF_WHEN_CREATED,
        "userAccountActions"
      })
  @ResponseBody
  public Object userAccountList(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("consumerKey") final String consumerKey)
    throws IOException {
    checkHasAnyRole(ADMIN);
    final Record userAccount = getUserAccount(consumerKey);
    if (userAccount != null) {

      final Map<String, Object> parameters = new HashMap<>();

      final Query query = new Query();
      query.setFieldNames(USER_GROUP_NAME, MODULE_NAME, DESCRIPTION, ACTIVE_IND,
        "X.WHEN_CREATED as \"GROUP_XREF_WHEN_CREATED\"");
      query.setFromClause("CPF.CPF_USER_GROUPS T"
        + " JOIN CPF.CPF_USER_GROUP_ACCOUNT_XREF X ON T.USER_GROUP_ID = X.USER_GROUP_ID");
      query.setWhereCondition(
        Q.equal(UserGroupAccountXref.USER_ACCOUNT_ID, userAccount.getIdentifier()));

      parameters.put("query", query);

      return newDataTableHandlerOrRedirect(request, response, "userAccountList",
        UserAccount.USER_ACCOUNT, "view", parameters);

    }
    throw new PageNotFoundException("User Account " + consumerKey + " does not exist");
  }

  @RequestMapping(value = {
    "/admin/userAccounts/{consumerKey}/userGroups/{userGroupName}/delete"
  }, method = RequestMethod.POST)
  public void userAccountMemberDelete(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("consumerKey") final String consumerKey) throws ServletException {
    checkHasAnyRole(ADMIN);
    final Record userGroup = getUserGroup(userGroupName);
    if (userGroup != null) {

      final Record userAccount = getUserAccount(consumerKey);
      if (userAccount != null) {
        final CpfDataAccessObject dataAccessObject = getDataAccessObject();
        dataAccessObject.deleteUserGroupAccountXref(userGroup, userAccount);
        redirectToTab(UserAccount.USER_ACCOUNT, "view", "userAccountList");
        return;
      }
    }
  }

  public void userGroupName(final XmlWriter out, final Object object) {
    final Record record = (Record)object;
    final long userGroupId = record.getLong(UserGroup.USER_GROUP_ID);

    final CpfDataAccessObject dataAccessObject = getDataAccessObject();
    final Record userGroup = dataAccessObject.getUserGroup(userGroupId);

    final String name = userGroup.getValue(UserGroup.USER_GROUP_NAME);
    out.text(name);
  }
}
