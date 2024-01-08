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
package ca.bc.gov.open.cpf.api.web.controller;

import java.util.UUID;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.web.servlet.ModelAndView;
import org.springframework.web.servlet.mvc.Controller;

import ca.bc.gov.open.cpf.api.domain.UserAccount;

import com.revolsys.record.Record;

public class ResetOAuthCredentialsByUserAccountController implements Controller {
  /** The user type to use when finding the user by their external name. */
  private String userAccountClass;

  private String successViewName;

  private String viewName;

  public String getSuccessViewName() {
    return this.successViewName;
  }

  /**
   * Get the user type to use when finding the user by their external name.
   *
   * @return The user type to use when finding the user by their external name.
   */
  public String getUserAccountClass() {
    return this.userAccountClass;
  }

  public String getViewName() {
    return this.viewName;
  }

  @Override
  public ModelAndView handleRequest(final HttpServletRequest request,
    final HttpServletResponse response) throws Exception {
    if (request.getMethod().equals("POST")) {
      final Record user = (Record)request.getAttribute("userAccount");
      if (user != null) {
        user.setValue(UserAccount.CONSUMER_SECRET, UUID.randomUUID().toString());
        return new ModelAndView(this.successViewName);
      }
    }
    return new ModelAndView(this.viewName);
  }

  public void setSuccessViewName(final String successViewName) {
    this.successViewName = successViewName;
  }

  /**
   * Set the user type to use when finding the user by their external name.
   *
   * @param userType The user type to use when finding the user by their
   *          external name.
   */
  public void setUserAccountClass(final String userType) {
    this.userAccountClass = userType;
  }

  public void setViewName(final String viewName) {
    this.viewName = viewName;
  }

}
