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

 $URL: https://secure.revolsys.com/svn/open.revolsys.com/ca.bc.gov.open.cpf/trunk/ca.bc.gov.open.cpf.api/src/main/java/ca/bc/gov/open/cpf/security/oauth/OAuthProcessingFilter.java $

 $Date: 2010-01-06 14:31:17 -0800 (Wed, 06 Jan 2010) $
 $Revision: 2169 $
 */
package ca.bc.gov.open.cpf.api.security.oauth;

import java.io.IOException;
import java.util.Collection;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.util.Assert;

import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;
import net.oauth.OAuthServiceProvider;
import net.oauth.SimpleOAuthValidator;

public class OAuthProcessingFilter extends AbstractAuthenticationProcessingFilter
  implements InitializingBean {
  /**
   * Extract the parts of the given request that are relevant to OAuth.
   * Parameters include OAuth Authorization headers and the usual request
   * parameters in the query string and/or form encoded body. The header
   * parameters come first, followed by the rest in the order they came from
   * request.getParameterMap().
   *
   * @param URL the official URL of this service; that is the URL a legitimate
   *          client would use to compute the digital signature. If this
   *          parameter is null, this method will try to reconstruct the URL
   *          from the HTTP request; which may be wrong in some cases.
   */
  public static OAuthMessage getMessage(final HttpServletRequest request, String URL) {
    if (URL == null) {
      URL = request.getRequestURL().toString();
    }
    final int q = URL.indexOf('?');
    if (q >= 0) {
      URL = URL.substring(0, q);
      // The query string parameters will be included in
      // the result from getParameters(request).
    }
    return new HttpRequestMessage(request, URL);
  }

  /** Reconstruct the requested URL, complete with query string (if any). */
  public static String getRequestURL(final HttpServletRequest request) {
    final StringBuffer url = request.getRequestURL();
    final String queryString = request.getQueryString();
    if (queryString != null) {
      url.append("?").append(queryString);
    }
    return url.toString();
  }

  private AuthenticationEntryPoint authenticationEntryPoint;

  private UserDetailsService consumerDetailsService;

  private boolean ignoreFailure = false;

  private final SimpleOAuthValidator oauthMessageValidator = new SimpleOAuthValidator();

  public OAuthProcessingFilter() {
    super("/j_spring_security_check");
  }

  @Override
  public void afterPropertiesSet() {
    if (!isIgnoreFailure()) {
      Assert.notNull(this.authenticationEntryPoint, "An AuthenticationEntryPoint is required");
    }
  }

  @Override
  public Authentication attemptAuthentication(final HttpServletRequest request,
    final HttpServletResponse response)
    throws AuthenticationException, IOException, ServletException {
    final String requestUrl = getRequestURL(request);
    final OAuthMessage message = getMessage(request, requestUrl);
    final String consumerKey = message.getConsumerKey();
    try {
      final UserDetails consumerDetails = getConsumerDetails(consumerKey);
      if (consumerDetails != null) {
        final OAuthAccessor accessor = getOAuthAccessor(consumerDetails);

        this.oauthMessageValidator.validateMessage(message, accessor);
        @SuppressWarnings("unchecked")
        final Collection<GrantedAuthority> authorities = (Collection<GrantedAuthority>)consumerDetails
          .getAuthorities();
        final Authentication authentication = new OAuthAuthenticationToken(consumerKey,
          authorities);
        authentication.setAuthenticated(true);
        return authentication;
      }
    } catch (final AuthenticationException e) {
      throw e;
    } catch (final Exception e) {
      final AuthenticationException authE = new BadCredentialsException(
        "Signature validation failed", e);
      if (!this.ignoreFailure) {
        this.authenticationEntryPoint.commence(request, response, authE);
      }
    }
    return null;
  }

  protected AuthenticationEntryPoint getAuthenticationEntryPoint() {
    return this.authenticationEntryPoint;
  }

  private UserDetails getConsumerDetails(final String consumerKey) {
    if (consumerKey == null) {
      return null;
    } else {
      final UserDetails consumerDetails = this.consumerDetailsService
        .loadUserByUsername(consumerKey);
      return consumerDetails;
    }
  }

  public UserDetailsService getConsumerDetailsService() {
    return this.consumerDetailsService;
  }

  private OAuthAccessor getOAuthAccessor(final UserDetails consumerDetails) {
    final String consumerKey = consumerDetails.getUsername();
    final String consumerSecret = consumerDetails.getPassword();
    final OAuthServiceProvider serviceProvider = new OAuthServiceProvider("", "", "");
    final OAuthConsumer consumer = new OAuthConsumer("", consumerKey, consumerSecret,
      serviceProvider);
    return new OAuthAccessor(consumer);
  }

  protected boolean isIgnoreFailure() {
    return this.ignoreFailure;
  }

  public void setAuthenticationEntryPoint(final AuthenticationEntryPoint authenticationEntryPoint) {
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  public void setConsumerDetailsService(final UserDetailsService consumerDetailsService) {
    this.consumerDetailsService = consumerDetailsService;
  }

  public void setIgnoreFailure(final boolean ignoreFailure) {
    this.ignoreFailure = ignoreFailure;
  }

}
