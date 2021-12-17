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

 $URL: https://secure.revolsys.com/svn/open.revolsys.com/ca.bc.gov.open.cpf/trunk/ca.bc.gov.open.cpf.api/src/main/java/ca/bc/gov/open/cpf/security/oauth/OAuthOrDigestProcessingFilterEntryPoint.java $

 $Date: 2010-01-06 14:31:17 -0800 (Wed, 06 Jan 2010) $
 $Revision: 2169 $
 */
package ca.bc.gov.open.cpf.api.security.oauth;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.codec.binary.Base64;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.www.DigestAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.NonceExpiredException;

import com.revolsys.util.Md5;

public class OAuthOrDigestProcessingFilterEntryPoint extends DigestAuthenticationEntryPoint {

  private static final Log logger = LogFactory.getLog(DigestAuthenticationEntryPoint.class);

  public void commence(final ServletRequest request, final ServletResponse response,
    final AuthenticationException authException) throws IOException, ServletException {
    final HttpServletResponse httpResponse = (HttpServletResponse)response;
    final String key = getKey();
    final String realmName = getRealmName();

    // compute a nonce (do not use remote IP address due to proxy farms)
    // format of nonce is:
    // base64(expirationTime + ":" + md5Hex(expirationTime + ":" + key))
    final long expiryTime = System.currentTimeMillis() + getNonceValiditySeconds() * 1000;
    final String signatureValue = new String(Md5.md5Hex(expiryTime + ":" + key));
    final String nonceValue = expiryTime + ":" + signatureValue;
    final String nonceValueBase64 = new String(Base64.encodeBase64(nonceValue.getBytes()));

    String authenticateHeader = "Digest realm=\"" + realmName + "\", " + "qop=\"auth\", nonce=\""
      + nonceValueBase64 + "\"";

    if (authException instanceof NonceExpiredException) {
      authenticateHeader = authenticateHeader + ", stale=\"true\"";
    }

    if (logger.isDebugEnabled()) {
      logger.debug("WWW-Authenticate header sent to user agent: " + authenticateHeader);
    }

    httpResponse.addHeader("WWW-Authenticate", "OAuth realm=\"" + realmName + "\"");
    httpResponse.addHeader("WWW-Authenticate", authenticateHeader);
    httpResponse.sendError(HttpServletResponse.SC_UNAUTHORIZED, authException.getMessage());
  }
}
