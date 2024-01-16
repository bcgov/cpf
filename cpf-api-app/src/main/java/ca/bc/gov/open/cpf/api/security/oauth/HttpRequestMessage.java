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
package ca.bc.gov.open.cpf.api.security.oauth;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.List;
import java.util.Map;

import jakarta.servlet.http.HttpServletRequest;

import net.oauth.OAuth;
import net.oauth.OAuthMessage;

/**
 * An HttpServletRequest, encapsulated as an OAuthMessage.
 *
 * @author John Kristian
 */
public class HttpRequestMessage extends OAuthMessage {
  private static void copyHeaders(final HttpServletRequest request,
    final Collection<Map.Entry<String, String>> into) {
    final Enumeration<String> names = request.getHeaderNames();
    if (names != null) {
      while (names.hasMoreElements()) {
        final String name = names.nextElement();
        final Enumeration<String> values = request.getHeaders(name);
        if (values != null) {
          while (values.hasMoreElements()) {
            into.add(new OAuth.Parameter(name, values.nextElement()));
          }
        }
      }
    }
  }

  @SuppressWarnings("unchecked")
  public static List<OAuth.Parameter> getParameters(final HttpServletRequest request) {
    final List<OAuth.Parameter> list = new ArrayList<>();
    for (final Enumeration<String> headers = request.getHeaders("Authorization"); headers != null
      && headers.hasMoreElements();) {
      final String header = headers.nextElement();
      for (final OAuth.Parameter parameter : OAuthMessage.decodeAuthorization(header)) {
        if (!"realm".equalsIgnoreCase(parameter.getKey())) {
          list.add(parameter);
        }
      }
    }
    for (final Object e : request.getParameterMap().entrySet()) {
      final Map.Entry<String, String[]> entry = (Map.Entry<String, String[]>)e;
      final String name = entry.getKey();
      for (final String value : entry.getValue()) {
        list.add(new OAuth.Parameter(name, value));
      }
    }
    return list;
  }

  private final HttpServletRequest request;

  public HttpRequestMessage(final HttpServletRequest request, final String URL) {
    super(request.getMethod(), URL, getParameters(request));
    this.request = request;
    copyHeaders(request, getHeaders());
  }

  @Override
  public InputStream getBodyAsStream() throws IOException {
    return this.request.getInputStream();
  }

  @Override
  public String getBodyEncoding() {
    return this.request.getCharacterEncoding();
  }

}
