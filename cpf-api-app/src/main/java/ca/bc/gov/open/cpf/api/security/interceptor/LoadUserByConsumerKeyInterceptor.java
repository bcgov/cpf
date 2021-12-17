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
/*
 Copyright 2009 Revolution Systems Inc.

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.

 $URL: https://secure.revolsys.com/svn/open.revolsys.com/ca.bc.gov.open.cpf/trunk/ca.bc.gov.open.cpf.api/src/main/java/ca/bc/gov/open/cpf/security/interceptor/LoadUserByConsumerKeyInterceptor.java $

 $Date: 2009-06-08 09:59:13 -0700 (Mon, 08 Jun 2009) $
 $Revision: 1866 $
 */
package ca.bc.gov.open.cpf.api.security.interceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.servlet.handler.HandlerInterceptorAdapter;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserAccount;

import com.revolsys.record.Record;

/**
 * The LoadUserByConsumerKeyInterceptor intercepts HTTP requests and adds the
 * userAccount attribute containing an {@link UserAccount} object for the
 * external user name identified in the HTTP request Principal within the
 * specified userAccountClass.
 */
public class LoadUserByConsumerKeyInterceptor extends HandlerInterceptorAdapter {

  private CpfDataAccessObject dataAccessObject;

  /**
   * Load the {@link UserAccount} for the logged in user and store it in the
   * userAccount request attribute.
   *
   * @param request The HTTP request.
   * @param response The HTTP response.
   * @param handler The handler which is being invoked.
   * @return True
   * @throws Exception If there was an exception loading the user.
   */
  @Override
  public boolean preHandle(final HttpServletRequest request, final HttpServletResponse response,
    final Object handler) throws Exception {

    final SecurityContext context = SecurityContextHolder.getContext();
    final Authentication authentication = context.getAuthentication();
    if (authentication != null) {
      final String userAccountName = authentication.getName();
      final Record userAccount = this.dataAccessObject.getUserAccount(userAccountName);
      if (userAccount != null) {
        request.setAttribute("userAccount", userAccount);
      } else {
        return true;
      }
    }
    return true;
  }

  public void setDataAccessObject(final CpfDataAccessObject dataAccessObject) {
    this.dataAccessObject = dataAccessObject;
  }

}
