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
package ca.bc.gov.open.cpf.api.security.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.springframework.beans.factory.annotation.Autowired;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;
import ca.bc.gov.open.cpf.plugin.impl.module.ResourcePermission;

import com.revolsys.record.Record;
import com.revolsys.record.Records;

public class BaseAuthorizationService implements AuthorizationService {

  private CpfDataAccessObject dataAccessObject;

  private UserAccountSecurityService userAccountSecurityService;

  @Override
  public boolean canAccessResource(final String moduleName, final String consumerKey,
    final String resourceClass, final String resourceId) {
    return canAccessResource(moduleName, consumerKey, resourceClass, resourceId,
      ResourcePermission.ALL);
  }

  @Override
  public boolean canAccessResource(final String moduleName, final String consumerKey,
    final String resourceClass, final String resourceId, final String actionName) {

    final Record userAccount = this.dataAccessObject.getUserAccount(consumerKey);
    if (userAccount == null || !Records.getBoolean(userAccount, UserAccount.ACTIVE_IND)) {
      return false;
    } else {
      final List<String> groupNames = new ArrayList<>();
      for (final String groupName : this.userAccountSecurityService.getGroupNames(userAccount)) {
        groupNames.add(groupName);
      }
      final Record permission = this.dataAccessObject.getUserGroupPermission(groupNames, moduleName,
        resourceClass, resourceId, actionName);
      return permission != null;
    }
  }

  @Override
  public boolean canPerformAction(final String moduleName, final String consumerKey,
    final String actionName) {
    return canAccessResource(moduleName, consumerKey, ResourcePermission.ALL,
      ResourcePermission.ALL, ResourcePermission.ALL);
  }

  @PreDestroy
  public void close() {
    this.dataAccessObject = null;
    this.userAccountSecurityService = null;
  }

  public UserAccountSecurityService getUserAccountSecurityService() {
    return this.userAccountSecurityService;
  }

  @Override
  public Map<String, Object> getUserAttributes(final String moduleName, final String consumerKey) {
    return Collections.emptyMap();
  }

  @Override
  public String getUserClass(final String consumerKey) {
    final Record userAccount = this.dataAccessObject.getUserAccount(consumerKey);
    if (userAccount == null) {
      return null;
    } else {
      return userAccount.getValue(UserAccount.USER_ACCOUNT_CLASS);
    }
  }

  @Override
  public String getUsername(final String consumerKey) {
    final Record userAccount = this.dataAccessObject.getUserAccount(consumerKey);
    if (userAccount == null) {
      return null;
    } else {
      return userAccount.getValue(UserAccount.USER_NAME);
    }
  }

  @Override
  public boolean isInGroup(final String moduleName, final String consumerKey,
    final String groupName) {
    final Record userAccount = this.dataAccessObject.getUserAccount(consumerKey);
    if (userAccount == null) {
      return false;
    } else {
      for (final String name : this.userAccountSecurityService.getGroupNames(userAccount)) {
        if (groupName.equals(name) || groupName.equals("ROLE_" + name)) {
          return true;
        }
      }
      return false;
    }
  }

  public void setDataAccessObject(final CpfDataAccessObject dataAccessObject) {
    this.dataAccessObject = dataAccessObject;
  }

  @Autowired
  public void setUserAccountSecurityService(
    final UserAccountSecurityService userAccountSecurityService) {
    this.userAccountSecurityService = userAccountSecurityService;
  }

}
