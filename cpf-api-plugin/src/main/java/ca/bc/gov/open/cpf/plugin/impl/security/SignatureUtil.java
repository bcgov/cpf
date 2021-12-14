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
package ca.bc.gov.open.cpf.plugin.impl.security;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import javax.crypto.Mac;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

public interface SignatureUtil {
  static String getQueryString(final Map<String, ? extends Object> parameters) throws Error {
    if (parameters == null || parameters.isEmpty()) {
      return "";
    } else {
      final StringBuilder query = new StringBuilder();
      boolean firstParameter = true;
      for (final Entry<String, ? extends Object> parameter : parameters.entrySet()) {
        final String name = parameter.getKey();
        final Object value = parameter.getValue();
        if (name != null && value != null) {
          if (!firstParameter) {
            query.append('&');
          } else {
            firstParameter = false;
          }
          boolean first = true;
          if (value instanceof Iterable) {
            final Iterable<?> values = (Iterable<?>)value;
            for (final Object paramValue : values) {
              if (first) {
                first = false;
              } else {
                query.append('&');
              }
              query.append(name).append('=').append(urlEncode(paramValue));
            }
          } else if (value instanceof String[]) {
            final String[] values = (String[])value;
            for (int i = 0; i < values.length; i++) {
              query.append(name).append('=').append(urlEncode(values[i]));
              if (i < values.length - 1) {
                query.append('&');
              }
            }
          } else if (value instanceof List) {
            @SuppressWarnings("rawtypes")
            final List values = (List)value;
            for (int i = 0; i < values.size(); i++) {
              query.append(name).append('=').append(urlEncode(values.get(i)));
              if (i < values.size() - 1) {
                query.append('&');
              }
            }
          } else {
            query.append(name).append('=').append(urlEncode(value));
          }

        }
      }
      return query.toString();
    }
  }

  static String sign(final String secretKey, final String data) {
    try {
      final SecretKey key = new SecretKeySpec(secretKey.getBytes("UTF-8"), "HmacSHA1");
      final Mac mac = Mac.getInstance("HmacSHA1");
      mac.init(key);
      final byte[] dataBytes = data.getBytes("UTF-8");
      final byte[] digestBytes = mac.doFinal(dataBytes);
      return java.util.Base64.getEncoder().encodeToString(digestBytes);
    } catch (final Throwable e) {
      throw new IllegalArgumentException("Unable to encrypt data " + data, e);
    }
  }

  static String sign(final String key, final String path, final Object time) {
    final String dataToSign = path + ":" + time;
    final String signature = sign(key, dataToSign);
    return signature;
  }

  static String sign(final String key, final String path, final Object time,
    final Map<String, ? extends Object> parameters) {
    final String data = getQueryString(new TreeMap<String, Object>(parameters));
    final String dataToSign = path + ":" + time + ":" + data;
    final String signature = sign(key, dataToSign);
    return signature;
  }

  static String urlEncode(final Object value) {
    try {
      return URLEncoder.encode(value.toString(), "US-ASCII");
    } catch (final UnsupportedEncodingException e) {
      throw new RuntimeException("Cannot find US-ASCII encoding", e);
    }
  }
}
