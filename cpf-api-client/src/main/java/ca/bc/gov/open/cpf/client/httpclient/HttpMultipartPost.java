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
package ca.bc.gov.open.cpf.client.httpclient;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URL;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.entity.mime.MultipartEntity;
import org.apache.http.entity.mime.content.FileBody;
import org.apache.http.entity.mime.content.InputStreamBody;
import org.apache.http.entity.mime.content.StringBody;
import org.apache.http.protocol.HttpContext;

import com.revolsys.spring.resource.Resource;

@SuppressWarnings("javadoc")
public class HttpMultipartPost {
  private CpfHttpClient httpclient = null;

  private HttpPost httppost = new HttpPost();

  private final MultipartEntity requestEntity = new MultipartEntity() {
    @Override
    public boolean isRepeatable() {
      return true;
    }
  };

  private HttpResponse response = null;

  private HttpEntity responseEntity = null;

  private String url;

  public HttpMultipartPost(final CpfHttpClient httpclient, final String url) {
    this.httppost = new HttpPost(url);
    this.url = url;
    this.httpclient = httpclient;
  }

  public HttpMultipartPost(final CpfHttpClient httpclient, final URL url) {
    this.httppost = new HttpPost(url.toString());
    this.httppost.setHeader("Accept", "text/csv");
  }

  public void addHeader(final String name, final String value) {
    this.httppost.addHeader(name, value);
  }

  public void addParameter(final String parameterName, final File file) {
    this.requestEntity.addPart(parameterName, new FileBody(file));
  }

  public void addParameter(final String parameterName, final Object parameterValue) {
    if (parameterValue != null) {
      try {
        this.requestEntity.addPart(parameterName, new StringBody(parameterValue.toString()));
      } catch (final UnsupportedEncodingException e) {
        e.printStackTrace();
      }
    }
  }

  public void addParameter(final String parameterName, final Resource resource,
    final String cotentType) {
    final ResourceBody body = new ResourceBody(resource, cotentType);
    this.requestEntity.addPart(parameterName, body);
  }

  public void addParameter(final String parameterName, final String filename,
    final InputStream inputStream) throws IOException {
    this.requestEntity.addPart(parameterName, new InputStreamBody(inputStream, filename));
  }

  @SuppressWarnings("deprecation")
  public void close() throws IOException {
    if (this.responseEntity != null) {
      this.responseEntity.consumeContent();
    }
  }

  public HttpResponse getResponse() {
    return this.response;
  }

  public InputStream getResponseContentStream() throws IOException {
    return this.responseEntity.getContent();
  }

  public HttpEntity getResponseEntity() {
    return this.responseEntity;
  }

  public String getUrl() {
    return this.url;
  }

  public int postRequest(final HttpContext context) throws IOException {
    this.response = null;
    int statusCode = HttpStatus.SC_BAD_REQUEST; // 400
    this.httppost.setEntity(this.requestEntity);

    try {
      this.response = this.httpclient.getHttpClient().execute(this.httppost, context);
      statusCode = this.response.getStatusLine().getStatusCode();
      if (statusCode < HttpStatus.SC_BAD_REQUEST) {
        this.responseEntity = this.response.getEntity();
      }
    } catch (final ClientProtocolException e) {
      e.printStackTrace();
    } catch (final IOException e) {
      e.printStackTrace();
    }
    return statusCode;
  }
}
