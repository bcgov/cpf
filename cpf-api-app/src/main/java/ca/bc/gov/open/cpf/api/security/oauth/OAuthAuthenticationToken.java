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

 $URL: https://secure.revolsys.com/svn/open.revolsys.com/ca.bc.gov.open.cpf/trunk/ca.bc.gov.open.cpf.api/src/main/java/ca/bc/gov/open/cpf/security/oauth/OAuthAuthenticationToken.java $

 $Date: 2010-01-06 14:31:17 -0800 (Wed, 06 Jan 2010) $
 $Revision: 2169 $
 */
package ca.bc.gov.open.cpf.api.security.oauth;

import java.util.Collection;
import java.util.Collections;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

public class OAuthAuthenticationToken extends AbstractAuthenticationToken {
  private static final long serialVersionUID = -7694264939286470744L;

  private final String consumerKey;

  public OAuthAuthenticationToken(final String consumerKey) {
    super(Collections.singleton((GrantedAuthority)new SimpleGrantedAuthority("ROLE_USER")));
    this.consumerKey = consumerKey;
  }

  public OAuthAuthenticationToken(final String consumerKey,
    final Collection<GrantedAuthority> authorities) {
    super(authorities);
    this.consumerKey = consumerKey;
  }

  @Override
  public Object getCredentials() {
    return null;
  }

  @Override
  public Object getPrincipal() {
    return this.consumerKey;
  }
}
