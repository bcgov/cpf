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
package net.oauth;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

/**
 * Miscellaneous constants, methods and types.
 *
 * @author John Kristian
 */
@SuppressWarnings("javadoc")
public class OAuth {

  /** A name/value pair. */
  public static class Parameter implements Map.Entry<String, String> {

    private final String key;

    private String value;

    public Parameter(final String key, final String value) {
      this.key = key;
      this.value = value;
    }

    @Override
    public boolean equals(final Object obj) {
      if (this == obj) {
        return true;
      }
      if (obj == null) {
        return false;
      }
      if (getClass() != obj.getClass()) {
        return false;
      }
      final Parameter that = (Parameter)obj;
      if (this.key == null) {
        if (that.key != null) {
          return false;
        }
      } else if (!this.key.equals(that.key)) {
        return false;
      }
      if (this.value == null) {
        if (that.value != null) {
          return false;
        }
      } else if (!this.value.equals(that.value)) {
        return false;
      }
      return true;
    }

    @Override
    public String getKey() {
      return this.key;
    }

    @Override
    public String getValue() {
      return this.value;
    }

    @Override
    public int hashCode() {
      final int prime = 31;
      int result = 1;
      result = prime * result + (this.key == null ? 0 : this.key.hashCode());
      result = prime * result + (this.value == null ? 0 : this.value.hashCode());
      return result;
    }

    @Override
    public String setValue(final String value) {
      try {
        return this.value;
      } finally {
        this.value = value;
      }
    }

    @Override
    public String toString() {
      return percentEncode(getKey()) + '=' + percentEncode(getValue());
    }
  }

  /**
   * Strings used for <a href="http://wiki.oauth.net/ProblemReporting">problem
   * reporting</a>.
   */
  public static class Problems {
    public static final String ADDITIONAL_AUTHORIZATION_REQUIRED = "additional_authorization_required";

    public static final String CONSUMER_KEY_REFUSED = "consumer_key_refused";

    public static final String CONSUMER_KEY_REJECTED = "consumer_key_rejected";

    public static final String CONSUMER_KEY_UNKNOWN = "consumer_key_unknown";

    public static final String NONCE_USED = "nonce_used";

    public static final String OAUTH_ACCEPTABLE_TIMESTAMPS = "oauth_acceptable_timestamps";

    public static final String OAUTH_ACCEPTABLE_VERSIONS = "oauth_acceptable_versions";

    public static final String OAUTH_PARAMETERS_ABSENT = "oauth_parameters_absent";

    public static final String OAUTH_PARAMETERS_REJECTED = "oauth_parameters_rejected";

    public static final String OAUTH_PROBLEM_ADVICE = "oauth_problem_advice";

    public static final String PARAMETER_ABSENT = "parameter_absent";

    public static final String PARAMETER_REJECTED = "parameter_rejected";

    public static final String PERMISSION_DENIED = "permission_denied";

    public static final String PERMISSION_UNKNOWN = "permission_unknown";

    public static final String SIGNATURE_INVALID = "signature_invalid";

    public static final String SIGNATURE_METHOD_REJECTED = "signature_method_rejected";

    public static final String TIMESTAMP_REFUSED = "timestamp_refused";

    /**
     * A map from an <a
     * href="http://wiki.oauth.net/ProblemReporting">oauth_problem</a> value to
     * the appropriate HTTP response code.
     */
    public static final Map<String, Integer> TO_HTTP_CODE = mapToHttpCode();

    public static final String TOKEN_EXPIRED = "token_expired";

    public static final String TOKEN_REJECTED = "token_rejected";

    public static final String TOKEN_REVOKED = "token_revoked";

    public static final String TOKEN_USED = "token_used";

    public static final String USER_REFUSED = "user_refused";

    public static final String VERSION_REJECTED = "version_rejected";

    private static Map<String, Integer> mapToHttpCode() {
      final Integer badRequest = 400;
      final Integer unauthorized = 401;
      final Integer serviceUnavailable = 503;
      final Map<String, Integer> map = new HashMap<>();

      map.put(Problems.VERSION_REJECTED, badRequest);
      map.put(Problems.PARAMETER_ABSENT, badRequest);
      map.put(Problems.PARAMETER_REJECTED, badRequest);
      map.put(Problems.TIMESTAMP_REFUSED, badRequest);
      map.put(Problems.SIGNATURE_METHOD_REJECTED, badRequest);

      map.put(Problems.NONCE_USED, unauthorized);
      map.put(Problems.TOKEN_USED, unauthorized);
      map.put(Problems.TOKEN_EXPIRED, unauthorized);
      map.put(Problems.TOKEN_REVOKED, unauthorized);
      map.put(Problems.TOKEN_REJECTED, unauthorized);
      map.put("token_not_authorized", unauthorized);
      map.put(Problems.SIGNATURE_INVALID, unauthorized);
      map.put(Problems.CONSUMER_KEY_UNKNOWN, unauthorized);
      map.put(Problems.CONSUMER_KEY_REJECTED, unauthorized);
      map.put(Problems.ADDITIONAL_AUTHORIZATION_REQUIRED, unauthorized);
      map.put(Problems.PERMISSION_UNKNOWN, unauthorized);
      map.put(Problems.PERMISSION_DENIED, unauthorized);

      map.put(Problems.USER_REFUSED, serviceUnavailable);
      map.put(Problems.CONSUMER_KEY_REFUSED, serviceUnavailable);
      return Collections.unmodifiableMap(map);
    }

  }

  /** The encoding used to represent characters as bytes. */
  public static final String ENCODING = "UTF-8";

  private static String characterEncoding = ENCODING;

  /** The MIME type for a sequence of OAuth parameters. */
  public static final String FORM_ENCODED = "application/x-www-form-urlencoded";

  public static final String HMAC_SHA1 = "HMAC-SHA1";

  public static final String OAUTH_CALLBACK = "oauth_callback";

  public static final String OAUTH_CALLBACK_CONFIRMED = "oauth_callback_confirmed";

  public static final String OAUTH_CONSUMER_KEY = "oauth_consumer_key";

  public static final String OAUTH_NONCE = "oauth_nonce";

  public static final String OAUTH_SIGNATURE = "oauth_signature";

  public static final String OAUTH_SIGNATURE_METHOD = "oauth_signature_method";

  public static final String OAUTH_TIMESTAMP = "oauth_timestamp";

  public static final String OAUTH_TOKEN = "oauth_token";

  public static final String OAUTH_TOKEN_SECRET = "oauth_token_secret";

  public static final String OAUTH_VERIFIER = "oauth_verifier";

  public static final String OAUTH_VERSION = "oauth_version";

  public static final String RSA_SHA1 = "RSA-SHA1";

  public static final String VERSION_1_0 = "1.0";

  public static String addParameters(final String url,
    final Iterable<? extends Map.Entry<String, String>> parameters) throws IOException {
    final String form = formEncode(parameters);
    if (form == null || form.length() <= 0) {
      return url;
    } else {
      return url + (url.indexOf("?") < 0 ? '?' : '&') + form;
    }
  }

  /**
   * Construct a URL like the given one, but with the given parameters added to
   * its query string.
   */
  public static String addParameters(final String url, final String... parameters)
    throws IOException {
    return addParameters(url, newList(parameters));
  }

  public static String decodeCharacters(final byte[] from) {
    if (characterEncoding != null) {
      try {
        return new String(from, characterEncoding);
      } catch (final UnsupportedEncodingException e) {
        System.err.println(e + "");
      }
    }
    return new String(from);
  }

  /** Parse a form-urlencoded document. */
  public static List<Parameter> decodeForm(final String form) {
    final List<Parameter> list = new ArrayList<>();
    if (!isEmpty(form)) {
      for (final String nvp : form.split("\\&")) {
        final int equals = nvp.indexOf('=');
        String name;
        String value;
        if (equals < 0) {
          name = decodePercent(nvp);
          value = null;
        } else {
          name = decodePercent(nvp.substring(0, equals));
          value = decodePercent(nvp.substring(equals + 1));
        }
        list.add(new Parameter(name, value));
      }
    }
    return list;
  }

  public static String decodePercent(final String s) {
    try {
      return URLDecoder.decode(s, ENCODING);
      // This implements http://oauth.pbwiki.com/FlexibleDecoding
    } catch (final java.io.UnsupportedEncodingException wow) {
      throw new RuntimeException(wow.getMessage(), wow);
    }
  }

  public static byte[] encodeCharacters(final String from) {
    if (characterEncoding != null) {
      try {
        return from.getBytes(characterEncoding);
      } catch (final UnsupportedEncodingException e) {
        System.err.println(e + "");
      }
    }
    return from.getBytes();
  }

  /**
   * Construct a form-urlencoded document containing the given sequence of
   * name/value pairs. Use OAuth percent encoding (not exactly the encoding
   * mandated by HTTP).
   */
  public static String formEncode(final Iterable<? extends Entry<String, String>> parameters)
    throws IOException {
    final ByteArrayOutputStream b = new ByteArrayOutputStream();
    formEncode(parameters, b);
    return decodeCharacters(b.toByteArray());
  }

  /**
   * Write a form-urlencoded document into the given stream, containing the
   * given sequence of name/value pairs.
   */
  public static void formEncode(final Iterable<? extends Entry<String, String>> parameters,
    final OutputStream into) throws IOException {
    if (parameters != null) {
      boolean first = true;
      for (final Entry<String, String> parameter : parameters) {
        if (first) {
          first = false;
        } else {
          into.write('&');
        }
        into.write(encodeCharacters(percentEncode(toString(parameter.getKey()))));
        into.write('=');
        into.write(encodeCharacters(percentEncode(toString(parameter.getValue()))));
      }
    }
  }

  public static boolean isEmpty(final String str) {
    return str == null || str.length() == 0;
  }

  /** Return true if the given Content-Type header means FORM_ENCODED. */
  public static boolean isFormEncoded(String contentType) {
    if (contentType == null) {
      return false;
    }
    final int semi = contentType.indexOf(";");
    if (semi >= 0) {
      contentType = contentType.substring(0, semi);
    }
    return FORM_ENCODED.equalsIgnoreCase(contentType.trim());
  }

  /** Construct a list of Parameters from name, value, name, value... */
  public static List<Parameter> newList(final String... parameters) {
    final List<Parameter> list = new ArrayList<>(parameters.length / 2);
    for (int p = 0; p + 1 < parameters.length; p += 2) {
      list.add(new Parameter(parameters[p], parameters[p + 1]));
    }
    return list;
  }

  /**
   * Construct a MapService containing a copy of the given parameters. If
   * several parameters have the same name, the MapService will contain the
   * first value, only.
   */
  public static Map<String, String> newMap(final Iterable<? extends Entry<String, String>> from) {
    final Map<String, String> map = new HashMap<>();
    if (from != null) {
      for (final Entry<String, String> f : from) {
        final String key = toString(f.getKey());
        if (!map.containsKey(key)) {
          map.put(key, toString(f.getValue()));
        }
      }
    }
    return map;
  }

  /** Construct a &-separated list of the given values, percentEncoded. */
  public static String percentEncode(final Iterable<String> values) {
    final StringBuilder p = new StringBuilder();
    for (final String v : values) {
      if (p.length() > 0) {
        p.append("&");
      }
      p.append(OAuth.percentEncode(toString(v)));
    }
    return p.toString();
  }

  public static String percentEncode(final String s) {
    if (s == null) {
      return "";
    }
    try {
      return URLEncoder.encode(s, ENCODING)
        // OAuth encodes some characters differently:
        .replace("+", "%20")
        .replace("*", "%2A")
        .replace("%7E", "~");
      // This could be done faster with more hand-crafted code.
    } catch (final UnsupportedEncodingException wow) {
      throw new RuntimeException(wow.getMessage(), wow);
    }
  }

  public static void setCharacterEncoding(final String encoding) {
    OAuth.characterEncoding = encoding;
  }

  private static final String toString(final Object from) {
    return from == null ? null : from.toString();
  }
}
