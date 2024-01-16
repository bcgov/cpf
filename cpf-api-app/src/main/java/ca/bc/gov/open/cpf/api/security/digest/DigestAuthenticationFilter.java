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
package ca.bc.gov.open.cpf.api.security.digest;

import java.io.IOException;
import java.util.Map;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.ServletRequest;
import jakarta.servlet.ServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.springframework.context.MessageSource;
import org.springframework.context.MessageSourceAware;
import org.springframework.context.support.MessageSourceAccessor;
import org.springframework.security.authentication.AuthenticationDetailsSource;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.SpringSecurityMessageSource;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserCache;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.core.userdetails.cache.NullUserCache;
import org.springframework.security.crypto.codec.Base64;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.security.web.authentication.www.DigestAuthenticationEntryPoint;
import org.springframework.security.web.authentication.www.NonceExpiredException;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.GenericFilterBean;

import ca.bc.gov.open.cpf.api.security.UsernamePasswordAuthenticationToken;

/**
 * Processes a HTTP request's Digest authorization headers, putting the result into the
 * <code>SecurityContextHolder</code>.
 * <p>
 * For a detailed background on what this filter is designed to process, refer to
 * <a href="http://www.ietf.org/rfc/rfc2617.txt">RFC 2617</a> (which superseded RFC 2069, although this
 * filter support clients that implement either RFC 2617 or RFC 2069).
 * <p>
 * This filter can be used to provide Digest authentication services to both remoting protocol clients (such as
 * Hessian and SOAP) as well as standard user agents (such as Internet Explorer and FireFox).
 * <p>
 * This Digest implementation has been designed to avoid needing to store session state between invocations.
 * All session management information is stored in the "nonce" that is sent to the client by the {@link
 * DigestAuthenticationEntryPoint}.
 * <p>
 * If authentication is successful, the resulting {@link org.springframework.security.core.Authentication Authentication}
 * object will be placed into the <code>SecurityContextHolder</code>.
 * <p>
 * If authentication fails, an {@link org.springframework.security.web.AuthenticationEntryPoint AuthenticationEntryPoint}
 * implementation is called. This must always be {@link DigestAuthenticationEntryPoint}, which will prompt the user
 * to authenticate again via Digest authentication.
 * <p>
 * Note there are limitations to Digest authentication, although it is a more comprehensive and secure solution
 * than Basic authentication. Please see RFC 2617 section 4 for a full discussion on the advantages of Digest
 * authentication over Basic authentication, including commentary on the limitations that it still imposes.
 */
public class DigestAuthenticationFilter extends GenericFilterBean implements MessageSourceAware {
  // ~ Static fields/initializers
  // =====================================================================================

  private static final Log logger = LogFactory.getLog(DigestAuthenticationFilter.class);

  // ~ Instance fields
  // ================================================================================================

  private AuthenticationDetailsSource authenticationDetailsSource = new WebAuthenticationDetailsSource();

  private DigestAuthenticationEntryPoint authenticationEntryPoint;

  protected MessageSourceAccessor messages = SpringSecurityMessageSource.getAccessor();

  private UserCache userCache = new NullUserCache();

  private UserDetailsService userDetailsService;

  private boolean passwordAlreadyEncoded = false;

  // ~ Methods
  // ========================================================================================================

  @Override
  public void afterPropertiesSet() {
    Assert.notNull(this.userDetailsService, "A UserDetailsService is required");
    Assert.notNull(this.authenticationEntryPoint, "A DigestAuthenticationEntryPoint is required");
  }

  @Override
  public void doFilter(final ServletRequest req, final ServletResponse res, final FilterChain chain)
    throws IOException, ServletException {
    final HttpServletRequest request = (HttpServletRequest)req;
    final HttpServletResponse response = (HttpServletResponse)res;

    final String header = request.getHeader("Authorization");

    if (logger.isDebugEnabled()) {
      logger.debug("Authorization header received from user agent: " + header);
    }

    if (header != null && header.startsWith("Digest ")) {
      final String section212response = header.substring(7);

      final String[] headerEntries = DigestAuthUtils.splitIgnoringQuotes(section212response, ',');
      final Map<String, String> headerMap = DigestAuthUtils
        .splitEachArrayElementAndCreateMap(headerEntries, "=", "\"");

      final String username = headerMap.get("username");
      final String realm = headerMap.get("realm");
      final String nonce = headerMap.get("nonce");
      final String uri = headerMap.get("uri");
      final String responseDigest = headerMap.get("response");
      final String qop = headerMap.get("qop"); // RFC 2617 extension
      final String nc = headerMap.get("nc"); // RFC 2617 extension
      final String cnonce = headerMap.get("cnonce"); // RFC 2617 extension

      // Check all required parameters were supplied (ie RFC 2069)
      if (username == null || realm == null || nonce == null || uri == null || response == null) {
        if (logger.isDebugEnabled()) {
          logger.debug("extracted username: '" + username + "'; realm: '" + realm + "'; nonce: '"
            + nonce + "'; uri: '" + uri + "'; response: '" + responseDigest + "'");
        }

        fail(request, response,
          new BadCredentialsException(
            this.messages.getMessage("DigestAuthenticationFilter.missingMandatory", new Object[] {
              section212response
            }, "Missing mandatory digest value; received header {0}")));

        return;
      }

      // Check all required parameters for an "auth" qop were supplied (ie RFC
      // 2617)
      if ("auth".equals(qop)) {
        if (nc == null || cnonce == null) {
          if (logger.isDebugEnabled()) {
            logger.debug("extracted nc: '" + nc + "'; cnonce: '" + cnonce + "'");
          }

          fail(request, response,
            new BadCredentialsException(
              this.messages.getMessage("DigestAuthenticationFilter.missingAuth", new Object[] {
                section212response
              }, "Missing mandatory digest value; received header {0}")));

          return;
        }
      }

      // Check realm name equals what we expected
      if (!this.getAuthenticationEntryPoint().getRealmName().equals(realm)) {
        fail(request, response,
          new BadCredentialsException(
            this.messages.getMessage("DigestAuthenticationFilter.incorrectRealm", new Object[] {
              realm, this.getAuthenticationEntryPoint().getRealmName()
            }, "Response realm name '{0}' does not match system realm name of '{1}'")));

        return;
      }

      // Check nonce was a Base64 encoded (as sent by
      // DigestAuthenticationEntryPoint)
      if (!Base64.isBase64(nonce.getBytes())) {
        fail(request, response,
          new BadCredentialsException(
            this.messages.getMessage("DigestAuthenticationFilter.nonceEncoding", new Object[] {
              nonce
            }, "Nonce is not encoded in Base64; received nonce {0}")));

        return;
      }

      // Decode nonce from Base64
      // format of nonce is:
      // base64(expirationTime + ":" + md5Hex(expirationTime + ":" + key))
      final String nonceAsPlainText = new String(Base64.decode(nonce.getBytes()));
      final String[] nonceTokens = StringUtils.delimitedListToStringArray(nonceAsPlainText, ":");

      if (nonceTokens.length != 2) {
        fail(request, response,
          new BadCredentialsException(
            this.messages.getMessage("DigestAuthenticationFilter.nonceNotTwoTokens", new Object[] {
              nonceAsPlainText
            }, "Nonce should have yielded two tokens but was {0}")));

        return;
      }

      // Extract expiry time from nonce
      long nonceExpiryTime;

      try {
        nonceExpiryTime = new Long(nonceTokens[0]).longValue();
      } catch (final NumberFormatException nfe) {
        fail(request, response,
          new BadCredentialsException(
            this.messages.getMessage("DigestAuthenticationFilter.nonceNotNumeric", new Object[] {
              nonceAsPlainText
            }, "Nonce token should have yielded a numeric first token, but was {0}")));

        return;
      }

      // Check signature of nonce matches this expiry time
      final String expectedNonceSignature = DigestAuthUtils
        .md5Hex(nonceExpiryTime + ":" + this.getAuthenticationEntryPoint().getKey());

      if (!expectedNonceSignature.equals(nonceTokens[1])) {
        fail(request, response,
          new BadCredentialsException(
            this.messages.getMessage("DigestAuthenticationFilter.nonceCompromised", new Object[] {
              nonceAsPlainText
            }, "Nonce token compromised {0}")));

        return;
      }

      // Lookup password for presented username
      // NB: DAO-provided password MUST be clear text - not encoded/salted
      // (unless this instance's passwordAlreadyEncoded property is 'false')
      boolean loadedFromDao = false;
      UserDetails user = this.userCache.getUserFromCache(username);

      if (user == null) {
        loadedFromDao = true;

        try {
          user = this.userDetailsService.loadUserByUsername(username);
        } catch (final AuthenticationException notFound) {
          fail(request, response,
            new BadCredentialsException(
              this.messages.getMessage("DigestAuthenticationFilter.usernameNotFound", new Object[] {
                username
              }, "Username {0} not found")));

          return;
        }

        if (user == null) {
          throw new AuthenticationServiceException(
            "AuthenticationDao returned null, which is an interface contract violation");
        }

        this.userCache.putUserInCache(user);
      }

      // Compute the expected response-digest (will be in hex form)
      String serverDigestMd5;

      // Don't catch IllegalArgumentException (already checked validity)
      serverDigestMd5 = DigestAuthUtils.generateDigest(this.passwordAlreadyEncoded, username, realm,
        user.getPassword(), request.getMethod(), uri, qop, nonce, nc, cnonce);

      // If digest is incorrect, try refreshing from backend and recomputing
      if (!serverDigestMd5.equals(responseDigest) && !loadedFromDao) {
        if (logger.isDebugEnabled()) {
          logger.debug(
            "Digest comparison failure; trying to refresh user from DAO in case password had changed");
        }

        try {
          user = this.userDetailsService.loadUserByUsername(username);
        } catch (final UsernameNotFoundException notFound) {
          // Would very rarely happen, as user existed earlier
          fail(request, response,
            new BadCredentialsException(
              this.messages.getMessage("DigestAuthenticationFilter.usernameNotFound", new Object[] {
                username
              }, "Username {0} not found")));
        }

        this.userCache.putUserInCache(user);

        // Don't catch IllegalArgumentException (already checked validity)
        serverDigestMd5 = DigestAuthUtils.generateDigest(this.passwordAlreadyEncoded, username,
          realm, user.getPassword(), request.getMethod(), uri, qop, nonce, nc, cnonce);
      }

      // If digest is still incorrect, definitely reject authentication attempt
      if (!serverDigestMd5.equals(responseDigest)) {
        if (logger.isDebugEnabled()) {
          logger.debug("Expected response: '" + serverDigestMd5 + "' but received: '"
            + responseDigest + "'; is AuthenticationDao returning clear text passwords?");
        }

        fail(request, response, new BadCredentialsException(this.messages
          .getMessage("DigestAuthenticationFilter.incorrectResponse", "Incorrect response")));
        return;
      }

      // To get this far, the digest must have been valid
      // Check the nonce has not expired
      // We do this last so we can direct the user agent its nonce is stale
      // but the request was otherwise appearing to be valid
      if (nonceExpiryTime < System.currentTimeMillis()) {
        fail(request, response, new NonceExpiredException(this.messages
          .getMessage("DigestAuthenticationFilter.nonceExpired", "Nonce has expired/timed out")));

        return;
      }

      if (logger.isDebugEnabled()) {
        logger.debug("Authentication success for user: '" + username + "' with response: '"
          + responseDigest + "'");
      }

      final UsernamePasswordAuthenticationToken authRequest = new UsernamePasswordAuthenticationToken(
        user, user.getPassword(), user.getAuthorities());

      authRequest.setDetails(this.authenticationDetailsSource.buildDetails(request));

      SecurityContextHolder.getContext().setAuthentication(authRequest);
    }

    chain.doFilter(request, response);
  }

  private void fail(final HttpServletRequest request, final HttpServletResponse response,
    final AuthenticationException failed) throws IOException, ServletException {
    SecurityContextHolder.getContext().setAuthentication(null);

    if (logger.isDebugEnabled()) {
      logger.debug(failed);
    }

    this.authenticationEntryPoint.commence(request, response, failed);
  }

  public DigestAuthenticationEntryPoint getAuthenticationEntryPoint() {
    return this.authenticationEntryPoint;
  }

  public UserCache getUserCache() {
    return this.userCache;
  }

  public UserDetailsService getUserDetailsService() {
    return this.userDetailsService;
  }

  public void setAuthenticationDetailsSource(
    final AuthenticationDetailsSource authenticationDetailsSource) {
    Assert.notNull(authenticationDetailsSource, "AuthenticationDetailsSource required");
    this.authenticationDetailsSource = authenticationDetailsSource;
  }

  public void setAuthenticationEntryPoint(
    final DigestAuthenticationEntryPoint authenticationEntryPoint) {
    this.authenticationEntryPoint = authenticationEntryPoint;
  }

  /**
   * If you set this property, the Authentication object, which is
   * created after the successful digest authentication will be marked
   * as <b>authenticated</b> and filled with the authorities loaded by
   * the UserDetailsService. It therefore will not be re-authenticated
   * by your AuthenticationProvider. This means, that only the password
   * of the user is checked, but not the flags like isEnabled() or
   * isAccountNonExpired(). You will save some time by enabling this flag,
   * as otherwise your UserDetailsService will be called twice. A more secure
   * option would be to introduce a cache around your UserDetailsService, but
   * if you don't use these flags, you can also safely enable this option.
   *
   * @param createAuthenticatedToken default is false
   */
  public void setCreateAuthenticatedToken(final boolean createAuthenticatedToken) {
  }

  @Override
  public void setMessageSource(final MessageSource messageSource) {
    this.messages = new MessageSourceAccessor(messageSource);
  }

  public void setPasswordAlreadyEncoded(final boolean passwordAlreadyEncoded) {
    this.passwordAlreadyEncoded = passwordAlreadyEncoded;
  }

  public void setUserCache(final UserCache userCache) {
    this.userCache = userCache;
  }

  public void setUserDetailsService(final UserDetailsService userDetailsService) {
    this.userDetailsService = userDetailsService;
  }
}
