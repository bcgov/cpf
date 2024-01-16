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
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.data.type.DataType;
import org.springframework.stereotype.Controller;
import org.springframework.ui.ModelMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.servlet.ModelAndView;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;
import ca.bc.gov.open.cpf.api.domain.UserGroup;
import ca.bc.gov.open.cpf.api.domain.UserGroupAccountXref;

import com.revolsys.collection.ArrayListOfMap;
import com.revolsys.io.IoConstants;
import com.revolsys.record.Record;
import com.revolsys.record.query.Condition;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.ui.html.decorator.FormGroupInstructionsDecorator;
import com.revolsys.ui.html.decorator.InputGroup;
import com.revolsys.ui.html.fields.AutoCompleteTextField;
import com.revolsys.ui.html.fields.Button;
import com.revolsys.ui.html.fields.CheckBox01Field;
import com.revolsys.ui.html.fields.Field;
import com.revolsys.ui.html.fields.TextField;
import com.revolsys.ui.html.form.Form;
import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.MultipleKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.view.ButtonsToolbarElement;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.ElementContainer;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.model.Menu;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.config.Page;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.util.Property;

@Controller
public class UserAccountUiBuilder extends CpfUiBuilder implements UserAccount {

  private static final String CONSUMER_KEY_VIEW = "CONSUMER_KEY_VIEW";

  public UserAccountUiBuilder() {
    super("userAccount", USER_ACCOUNT, CONSUMER_KEY, "User Account", "User Accounts");
    setIdParameterName("consumerKey");
    newKeyList("activeEdit", Collections.singletonList(ACTIVE_IND));
    addPage(new Page("searchLikeName", "/admin/userAccounts/searchLikeName.json"));
  }

  @RequestMapping(value = "/admin/userAccounts/add", method = {
    RequestMethod.GET, RequestMethod.POST
  }, title = "Add User Account", fieldNames = {
    CONSUMER_KEY, CONSUMER_SECRET, ACTIVE_IND
  }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public Element add(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    final Map<String, Object> defaultValues = new HashMap<>();
    defaultValues.put(USER_ACCOUNT_CLASS, USER_ACCOUNT_CLASS_CPF);
    defaultValues.put(CONSUMER_SECRET, UUID.randomUUID().toString().replaceAll("-", ""));
    defaultValues.put(ACTIVE_IND, "1");
    final TabElementContainer tabs = (TabElementContainer)super.newObjectAddPage(defaultValues,
      null, "preInsert");
    if (!tabs.isEmpty()) {
      final ElementContainer page = (ElementContainer)tabs.getElements().get(0);
      final List<Element> elements = page.getElements();
      final ButtonsToolbarElement menuView = (ButtonsToolbarElement)elements
        .get(elements.size() - 1);
      final Menu menu = menuView.getMenu();
      menu.addMenuItem("Generate Consumer Secret", "javascript:generateConsumerSecret()");
    }
    return tabs;
  }

  public void addMembersDataTable(final TabElementContainer container, final String prefix) {
    final Map<String, Object> parameters = new HashMap<>();
    parameters.put("serverSide", Boolean.TRUE);
    parameters.put("deferLoading", 0);
    parameters.put("tabbed", true);

    final String pageName = prefix + "List";
    final HttpServletRequest request = getRequest();
    final ElementContainer element = newDataTable(request, pageName, parameters);
    if (element != null) {

      final String addUrl = getPageUrl(prefix + "MemberAdd");
      final Form form = new Form(prefix + "MemberAdd", addUrl);

      final String searchUrl = getPageUrl("searchLikeName");
      final AutoCompleteTextField searchField = new AutoCompleteTextField("consumerKey", searchUrl,
        true);

      final InputGroup group = new InputGroup(searchField) //
        .addButton(Button.submit("add", "Add"));

      FormGroupInstructionsDecorator.decorate(form, group,
        "Search for a user by typing 3 or more consecutive characters from any part of the Consumer Key or User Account Name. The matching users will be displayed in a pop-up list. Select the required user and click the Add button.");

      element.add(0, form);

      final String tabId = getTypeName() + "_" + pageName;
      final String title = getPageTitle(pageName);
      container.add(tabId, title, element);
    }
  }

  public ModelAndView addUserGroupMembership(final HttpServletRequest request,
    final HttpServletResponse response, final String moduleName, final String userGroupName,
    final String moduleGroupName, final String consumerKey, final String parentPageName,
    final String tabName) {
    if (Property.hasValue(consumerKey)) {
      if (moduleName != null) {
        hasModule(request, moduleName);
      }
      final Record userGroup = getUserGroup(userGroupName);
      if (userGroup != null && (moduleGroupName == null
        || userGroup.getValue(UserGroup.MODULE_NAME).equals(moduleGroupName))) {

        final Record userAccount = getUserAccount(consumerKey);
        if (userAccount == null) {
          final ModelMap model = new ModelMap();
          model.put("body", "/WEB-INF/jsp/admin/groupMemberNotFound.jsp");
          model.put("consumerKey", consumerKey);
          return new ModelAndView("/jsp/template/page", model);
        } else {
          final CpfDataAccessObject dataAccessObject = getDataAccessObject();
          dataAccessObject.newUserGroupAccountXref(userGroup, userAccount);
          redirectToTab(UserGroup.USER_GROUP, parentPageName, tabName);
          return null;
        }
      }
      throw new PageNotFoundException();
    } else {
      final ModelMap model = new ModelMap();
      model.put("body", "/WEB-INF/jsp/admin/groupMemberNull.jsp");
      return new ModelAndView("/jsp/template/page", model);
    }
  }

  @RequestMapping(value = {
    "/admin/userAccounts/searchLikeName"
  }, method = RequestMethod.GET)
  @ResponseBody
  public ArrayListOfMap<Object> autocompleteUserName(final HttpServletRequest request,
    final HttpServletResponse response, @RequestParam("term") final String term)
    throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin();
    request.setAttribute(IoConstants.JSON_LIST_ROOT_PROPERTY, Boolean.TRUE);
    final CpfDataAccessObject cpfDataAccessObject = getDataAccessObject();
    final List<Record> userAccounts = cpfDataAccessObject.getUserAccountsLikeName(term);

    final ArrayListOfMap<Object> results = new ArrayListOfMap<>();
    for (final Record userAccount : userAccounts) {
      final String accountClass = userAccount.getValue(USER_ACCOUNT_CLASS);
      final String accountName = userAccount.getValue(USER_NAME);
      final String username = userAccount.getValue(CONSUMER_KEY);
      final String label = username + " (" + accountName + " - " + accountClass + ")";
      final Map<String, Object> autoComplete = new LinkedHashMap<>();
      autoComplete.put("label", label);
      autoComplete.put("value", username);
      results.add(autoComplete);
    }
    return results;
  }

  @RequestMapping(value = "/admin/userAccounts/{consumerKey}/delete", method = RequestMethod.POST,
      title = "Delete User Account {consumerKey}", permission = "hasRole('ROLE_ADMIN')")
  public void delete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("consumerKey") final String consumerKey) throws IOException, ServletException {
    checkHasAnyRole(ADMIN);

    final Record userAccount = getUserAccount(consumerKey);
    if (userAccount != null) {

      getDataAccessObject().deleteUserAccount(userAccount);
      redirectPage("list");
    }
  }

  @RequestMapping(value = "/admin/userAccounts/{consumerKey}/edit", method = {
    RequestMethod.GET, RequestMethod.POST
  }, title = "Edit User Account {consumerKey}", fieldNames = {
    CONSUMER_KEY, CONSUMER_SECRET, ACTIVE_IND
  }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public Element edit(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("consumerKey") String consumerKey) throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    final Record userAccount = getUserAccount(consumerKey);
    if (USER_ACCOUNT_CLASS_CPF.equals(userAccount.getValue(USER_ACCOUNT_CLASS))) {
      final TabElementContainer tabs = (TabElementContainer)super.newObjectEditPage(userAccount,
        null);
      if (!tabs.isEmpty()) {
        final ElementContainer page = (ElementContainer)tabs.getElements().get(0);
        final List<Element> elements = page.getElements();
        final ButtonsToolbarElement menuView = (ButtonsToolbarElement)elements
          .get(elements.size() - 1);

        final Menu menu = menuView.getMenu();
        menu.addMenuItem("Generate Consumer Secret", "javascript:generateConsumerSecret()");
      }
      return tabs;
    } else {
      return super.newObjectEditPage(userAccount, "active");
    }
  }

  @RequestMapping(value = "/admin/userGroups/{userGroupName}/members", method = RequestMethod.GET,
      title = "User Accounts for Group", fieldNames = {
        CONSUMER_KEY_VIEW, USER_ACCOUNT_CLASS, USER_NAME, ACTIVE_IND, GROUP_XREF_WHEN_CREATED,
        "groupActions"
      })
  @ResponseBody
  public Object groupList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("userGroupName") final String userGroupName)
    throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin();
    return newUserGroupMembersList(request, response, "group", null, userGroupName, null);
  }

  @RequestMapping(value = "/admin/userGroups/{userGroupName}/members/add",
      method = RequestMethod.POST)
  public ModelAndView groupMemberAdd(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("userGroupName") final String userGroupName,
    @RequestParam("consumerKey") final String consumerKey) throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    return addUserGroupMembership(request, response, null, userGroupName, null, consumerKey,
      "groupView", "groupList");
  }

  @RequestMapping(value = "/admin/userGroups/{userGroupName}/members/{consumerKey}/delete",
      method = RequestMethod.POST)
  public void groupMemberDelete(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("consumerKey") final String consumerKey) throws ServletException {
    checkHasAnyRole(ADMIN);
    removeUserGroupMembership(request, response, null, userGroupName, null, consumerKey,
      "groupView", "groupList");

  }

  @Override
  protected void initFields() {
    super.initFields();

    addField(new TextField(CONSUMER_KEY, 70, 255, true));
    addField(new TextField(CONSUMER_SECRET, 70, 255, true));
    addField(new TextField(USER_NAME, true));
    addField(new CheckBox01Field(ACTIVE_IND).setLabel("Active"));
    addField(
      new AutoCompleteTextField(USER_ACCOUNT_CLASS, "/users/properties/userAccountClass", true));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    addKeySerializer(
      new PageLinkKeySerializer(CONSUMER_KEY_VIEW, CONSUMER_KEY, "Consumer Key", "view"));

    final MultipleKeySerializer actions = new MultipleKeySerializer("actions", "Actions");
    actions.addSerializer(new ActionFormKeySerializer("delete", "Delete", "fa fa-trash") //
      .addParameterName("CONSUMER_KEY", "CONSUMER_KEY") //
      .addParameterName("userAccountClass", "USER_ACCOUNT_CLASS"));
    addKeySerializer(actions);

    final MultipleKeySerializer groupActions = new MultipleKeySerializer("groupActions", "Actions");
    groupActions
      .addSerializer(new ActionFormKeySerializer("groupMemberDelete", "Delete", "fa fa-trash")
        .addParameterName("CONSUMER_KEY", "CONSUMER_KEY"));
    addKeySerializer(groupActions);

    final MultipleKeySerializer moduleGroupActions = new MultipleKeySerializer("moduleGroupActions",
      "Actions");
    moduleGroupActions
      .addSerializer(new ActionFormKeySerializer("moduleGroupMemberDelete", "Delete", "fa fa-trash")
        .addParameterName("CONSUMER_KEY", "CONSUMER_KEY"));
    addKeySerializer(moduleGroupActions);

    final MultipleKeySerializer moduleGroupAdminActions = new MultipleKeySerializer(
      "moduleGroupAdminActions", "Actions");
    moduleGroupAdminActions.addSerializer(
      new ActionFormKeySerializer("moduleGroupAdminMemberDelete", "Delete", "fa fa-trash")
        .addParameterName("CONSUMER_KEY", "CONSUMER_KEY"));
    addKeySerializer(moduleGroupAdminActions);
  }

  @RequestMapping(value = "/admin/userAccounts", method = RequestMethod.GET,
      title = "User Accounts", fieldNames = {
        CONSUMER_KEY_VIEW, USER_ACCOUNT_CLASS, USER_NAME, ACTIVE_IND, "actions"
      }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public Object list(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException {
    checkHasAnyRole(ADMIN);
    HttpServletUtils.setAttribute("title", "User Accounts");
    return newDataTableHandler(request, "list");
  }

  @RequestMapping(value = "/admin/modules/{moduleName}/adminUserGroups/{userGroupName}/members",
      method = RequestMethod.GET, title = "User Accounts for Group", fieldNames = {
        CONSUMER_KEY_VIEW, USER_ACCOUNT_CLASS, USER_NAME, ACTIVE_IND, GROUP_XREF_WHEN_CREATED,
        "moduleGroupAdminActions"
      })
  @ResponseBody
  public Object moduleGroupAdminList(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    return newUserGroupMembersList(request, response, "moduleGroupAdmin", moduleName, userGroupName,
      "ADMIN_MODULE_" + moduleName);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/adminUserGroups/{userGroupName}/members/add"
  }, method = RequestMethod.POST)
  public ModelAndView moduleGroupAdminMemberAdd(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @RequestParam("consumerKey") final String consumerKey) throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    return addUserGroupMembership(request, response, moduleName, userGroupName,
      "ADMIN_MODULE_" + moduleName, consumerKey, "moduleAdminView", "moduleGroupAdminList");
  }

  @RequestMapping(
      value = "/admin/modules/{moduleName}/adminUserGroups/{userGroupName}/members/{consumerKey}/delete",
      method = RequestMethod.POST)
  public void moduleGroupAdminMemberDelete(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("consumerKey") final String consumerKey) throws ServletException {
    checkAdminOrModuleAdmin(moduleName);
    removeUserGroupMembership(request, response, moduleName, userGroupName,
      "ADMIN_MODULE_" + moduleName, consumerKey, "moduleAdminView", "moduleGroupAdminList");

  }

  @RequestMapping(value = "/admin/modules/{moduleName}/userGroups/{userGroupName}/members",
      method = RequestMethod.GET, title = "User Accounts for Group", fieldNames = {
        CONSUMER_KEY_VIEW, USER_ACCOUNT_CLASS, USER_NAME, ACTIVE_IND, GROUP_XREF_WHEN_CREATED,
        "moduleGroupActions"
      }, permission = "#userGroupName.startsWith(#moduleName)")
  @ResponseBody
  public Object moduleGroupList(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName)
    throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    return newUserGroupMembersList(request, response, "moduleGroup", moduleName, userGroupName,
      moduleName);
  }

  @RequestMapping(value = "/admin/modules/{moduleName}/userGroups/{userGroupName}/members/add",
      method = RequestMethod.POST, permission = "#userGroupName.startsWith(#moduleName)")
  public ModelAndView moduleGroupMemberAdd(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @RequestParam("consumerKey") final String consumerKey) throws IOException, ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    return addUserGroupMembership(request, response, moduleName, userGroupName, moduleName,
      consumerKey, "moduleView", "moduleGroupList");
  }

  @RequestMapping(
      value = "/admin/modules/{moduleName}/userGroups/{userGroupName}/members/{consumerKey}/delete",
      method = RequestMethod.POST, permission = "#userGroupName.startsWith(#moduleName)")
  public void moduleGroupMemberDelete(final HttpServletRequest request,
    final HttpServletResponse response, @PathVariable("moduleName") final String moduleName,
    @PathVariable("userGroupName") final String userGroupName,
    @PathVariable("consumerKey") final String consumerKey) throws ServletException {
    checkAdminOrAnyModuleAdmin(moduleName);
    removeUserGroupMembership(request, response, moduleName, userGroupName, moduleName, consumerKey,
      "moduleView", "moduleGroupList");
  }

  public Object newUserGroupMembersList(final HttpServletRequest request,
    final HttpServletResponse response, final String prefix, final String moduleName,
    final String userGroupName, final String groupModuleName) {
    final String pageName = prefix + "List";
    if (isDataTableCallback(request)) {
      if (moduleName != null) {
        hasModule(request, moduleName);
      }
      final Record userGroup = getUserGroup(userGroupName);
      if (userGroup != null && (moduleName == null
        || userGroup.getValue(UserGroup.MODULE_NAME).equals(groupModuleName))) {

        final Identifier userGroupId = userGroup.getIdentifier();
        final Condition equal = Q.equal(UserGroupAccountXref.USER_GROUP_ID, userGroupId);
        final Query query = new Query();
        query.setFieldNames(CONSUMER_KEY, USER_ACCOUNT_CLASS, USER_NAME, ACTIVE_IND,
          "X.WHEN_CREATED as \"GROUP_XREF_WHEN_CREATED\"");
        query.setFromClause("CPF.CPF_USER_ACCOUNTS T"
          + " JOIN CPF.CPF_USER_GROUP_ACCOUNT_XREF X ON T.USER_ACCOUNT_ID = X.USER_ACCOUNT_ID");
        query.setWhereCondition(equal);
        final RecordStore recordStore = getRecordStore();
        return newDataTableMap(request, recordStore, query, pageName);
      }
      throw new PageNotFoundException();
    } else {
      return redirectToTab(UserGroup.USER_GROUP, prefix + "View", pageName);
    }
  }

  @Override
  public void postUpdate(final Record userAccount) {
    super.postUpdate(userAccount);
    final String consumerKey = userAccount.getString(CONSUMER_KEY);
    final String oldConsumerKey = HttpServletUtils.getAttribute("oldConsumerKey");
    if (!DataType.equal(consumerKey, oldConsumerKey)) {
      final CpfDataAccessObject dataAccessObject = getDataAccessObject();
      dataAccessObject.updateJobUserId(oldConsumerKey, consumerKey);
    }
  }

  @Override
  public boolean preInsert(final Form form, final Record userAccount) {
    final Field consumerKeyField = form.getField(CONSUMER_KEY);
    String consumerKey = consumerKeyField.getValue();
    consumerKey = consumerKey.toLowerCase();
    consumerKeyField.setValue(consumerKey);
    userAccount.setValue(CONSUMER_KEY, consumerKey);
    userAccount.setValue(USER_NAME, consumerKey);

    if (!consumerKey.matches("[a-z0-9_]+")) {
      consumerKeyField.addValidationError("Can only contain the characters a-z, 0-9, and _.");
      return false;
    } else {
      final Record account = getUserAccount(consumerKey);
      if (account == null) {
        return true;
      } else {
        consumerKeyField.addValidationError("Consumer key is already used.");
        return false;
      }
    }
  }

  @Override
  public boolean preUpdate(final Form form, final Record userAccount) {
    if (USER_ACCOUNT_CLASS_CPF.equals(userAccount.getValue(USER_ACCOUNT_CLASS))) {
      final String oldConsumerKey = userAccount.getString(CONSUMER_KEY);
      final Long userAccountId = userAccount.getLong(USER_ACCOUNT_ID);
      final Field consumerKeyField = form.getField(CONSUMER_KEY);
      String consumerKey = consumerKeyField.getValue();
      consumerKey = consumerKey.toLowerCase();
      consumerKeyField.setValue(consumerKey);
      userAccount.setValue(CONSUMER_KEY, consumerKey);
      userAccount.setValue(USER_NAME, consumerKey);
      if (!consumerKey.matches("[a-z0-9_]+")) {
        consumerKeyField.addValidationError("Can only contain the characters a-z, 0-9, and _.");
        return false;
      } else {
        final Record account = getUserAccount(consumerKey);
        if (account == null || account.getLong(USER_ACCOUNT_ID).equals(userAccountId)) {
          HttpServletUtils.setAttribute("oldConsumerKey", oldConsumerKey);
          HttpServletUtils.setPathVariable("consumerKey", consumerKey);
          return true;
        } else {
          consumerKeyField.addValidationError("Consumer key is already used");
          return false;
        }
      }
    } else {
      return true;
    }
  }

  protected void removeUserGroupMembership(final HttpServletRequest request,
    final HttpServletResponse response, final String moduleName, final String userGroupName,
    final String moduleGroupName, final String consumerKey, final String parentPageName,
    final String tabName) throws ServletException {
    if (moduleName != null) {
      hasModule(request, moduleName);
    }
    final Record userGroup = getUserGroup(userGroupName);
    if (userGroup != null && (moduleGroupName == null
      || userGroup.getValue(UserGroup.MODULE_NAME).equals(moduleGroupName))) {

      final Record userAccount = getUserAccount(consumerKey);
      if (userAccount != null) {
        final CpfDataAccessObject dataAccessObject = getDataAccessObject();
        dataAccessObject.deleteUserGroupAccountXref(userGroup, userAccount);
        redirectToTab(UserGroup.USER_GROUP, parentPageName, tabName);
        return;
      }
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = "/admin/userAccounts/{consumerKey}", method = RequestMethod.GET,
      title = "User Account {consumerKey}", fieldNames = {
        USER_ACCOUNT_ID, CONSUMER_KEY, USER_ACCOUNT_CLASS, USER_NAME, ACTIVE_IND, WHO_CREATED,
        WHEN_CREATED, WHO_UPDATED, WHEN_UPDATED, "actions"
      }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public ElementContainer view(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("consumerKey") final String consumerKey) throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    final Record userAccount = getUserAccount(consumerKey);
    if (userAccount != null) {
      final String userAccountClass = userAccount.getValue(USER_ACCOUNT_CLASS);
      HttpServletUtils.setPathVariable("userAccountClass", userAccountClass);
      final TabElementContainer tabs = new TabElementContainer();
      addObjectViewPage(tabs, userAccount, null);

      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.TRUE);
      addTabDataTable(tabs, UserGroup.USER_GROUP, "userAccountList", parameters);
      return tabs;
    }
    throw new PageNotFoundException();
  }

}
