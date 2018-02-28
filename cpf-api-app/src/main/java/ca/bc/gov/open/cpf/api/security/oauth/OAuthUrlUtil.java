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

 $URL: https://secure.revolsys.com/svn/open.revolsys.com/ca.bc.gov.open.cpf/trunk/ca.bc.gov.open.cpf.api/src/main/java/ca/bc/gov/open/cpf/security/oauth/OAuthUrlUtil.java $
 $Author: paul.austin@revolsys.com $
 $Date: 2009-06-08 09:59:13 -0700 (Mon, 08 Jun 2009) $
 $Revision: 1866 $
 */
package ca.bc.gov.open.cpf.api.security.oauth;

import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLEncoder;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.List;
import java.util.Map.Entry;

import org.apache.http.NameValuePair;
import org.apache.http.client.utils.URLEncodedUtils;

import net.oauth.OAuth;
import net.oauth.OAuthAccessor;
import net.oauth.OAuthConsumer;
import net.oauth.OAuthMessage;

public class OAuthUrlUtil {

  public static String addAuthenticationToUrl(final String method, final String url,
    final String consumerKey, final String consumerSecret) {
    try {
      final String baseUrl = getUriWithoutQuery(url);
      final Collection<Entry<String, String>> parameters = getParameters(url);

      final OAuthMessage message = new OAuthMessage(method, url, parameters);
      final OAuthAccessor accessor = getOAuthAccessor(consumerKey, consumerSecret);

      message.addRequiredParameters(accessor);
      final List<Entry<String, String>> authenticatedParameters = message.getParameters();
      return getUrl(baseUrl, authenticatedParameters);
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to Authenticate URL", e);
    }
  }

  private static void addParameter(final StringBuilder url, final Entry<String, String> parameter) {
    final String key = parameter.getKey();
    final String value = parameter.getValue();

    try {
      url.append(URLEncoder.encode(key, "UTF-8"));
      url.append("=");
      url.append(URLEncoder.encode(value, "UTF-8"));
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot encode parameters", e);
    }
  }

  private static OAuthAccessor getOAuthAccessor(final String consumerKey,
    final String consumerSecret) {
    final OAuthAccessor accessor = new OAuthAccessor(
      new OAuthConsumer("", consumerKey, consumerSecret, null));
    return accessor;
  }

  public static Collection<Entry<String, String>> getParameters(final String uri)
    throws URISyntaxException {
    final List<NameValuePair> parameters = URLEncodedUtils.parse(new URI(uri), (Charset)null);
    final List<Entry<String, String>> parameterEntries = new ArrayList<>();
    for (final NameValuePair parameter : parameters) {
      final String name = parameter.getName();
      final String value = parameter.getValue();
      parameterEntries.add(new OAuth.Parameter(name, value));
    }
    return parameterEntries;
  }

  public static String getUriWithoutQuery(final String uri) {
    String url = uri;
    final int queryIndex = url.indexOf('?');
    if (queryIndex != -1) {
      url = url.substring(0, queryIndex);
    }
    return url;
  }

  public static String getUrl(final String baseUrl,
    final Collection<Entry<String, String>> parameters) {
    final Iterator<Entry<String, String>> parameterIter = parameters.iterator();
    if (parameterIter.hasNext()) {
      final StringBuilder url = new StringBuilder(baseUrl);
      url.append('?');
      Entry<String, String> parameter = parameterIter.next();
      addParameter(url, parameter);
      while (parameterIter.hasNext()) {
        url.append('&');
        parameter = parameterIter.next();
        addParameter(url, parameter);
      }
      return url.toString();
    } else {
      return baseUrl;
    }
  }

}
