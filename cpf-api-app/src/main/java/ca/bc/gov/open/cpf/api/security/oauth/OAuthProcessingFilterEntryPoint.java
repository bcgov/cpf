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

 $URL: https://secure.revolsys.com/svn/open.revolsys.com/ca.bc.gov.open.cpf/trunk/ca.bc.gov.open.cpf.api/src/main/java/ca/bc/gov/open/cpf/security/oauth/OAuthProcessingFilterEntryPoint.java $

 $Date: 2010-01-06 14:31:17 -0800 (Wed, 06 Jan 2010) $
 $Revision: 2169 $
 */
package ca.bc.gov.open.cpf.api.security.oauth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.util.Assert;

public class OAuthProcessingFilterEntryPoint implements AuthenticationEntryPoint, InitializingBean {
  private String realmName;

  @Override
  public void afterPropertiesSet() throws Exception {
    Assert.hasText(this.realmName, "realmName must be specified");
  }

  @Override
  public void commence(final HttpServletRequest request, final HttpServletResponse response,
    final AuthenticationException authException) throws IOException, ServletException {
    final HttpServletResponse httpResponse = response;
    httpResponse.addHeader("WWW-Authenticate", "OAuth realm=\"" + this.realmName + "\"");
    // httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED,
    // authException.getMessage());
  }

  public String getRealmName() {
    return this.realmName;
  }

  public void setRealmName(final String realmName) {
    this.realmName = realmName;
  }

}
