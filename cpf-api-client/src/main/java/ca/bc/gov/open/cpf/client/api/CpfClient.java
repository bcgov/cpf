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
package ca.bc.gov.open.cpf.client.api;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import javax.annotation.PreDestroy;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.StatusLine;
import org.apache.http.util.EntityUtils;

import ca.bc.gov.open.cpf.client.httpclient.CpfHttpClient;
import ca.bc.gov.open.cpf.client.httpclient.CpfHttpClientPool;
import ca.bc.gov.open.cpf.client.httpclient.HttpMultipartPost;
import ca.bc.gov.open.cpf.client.httpclient.HttpStatusCodeException;

import com.revolsys.collection.map.MapEx;
import com.revolsys.io.BaseCloseable;
import com.revolsys.io.IoFactory;
import com.revolsys.io.map.MapReader;
import com.revolsys.io.map.MapWriter;
import com.revolsys.io.map.MapWriterFactory;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.spring.resource.ByteArrayResource;
import com.revolsys.spring.resource.Resource;
import com.revolsys.util.Property;

/**
 * <p>
 * The CPF Java (6+) client allows applications to use the <a
 * href="../rest-api/">CPF Web ArcGisRestService REST API</a> to query the available
 * business applications, create jobs and download the results of
 * jobs on behalf of their users.
 * </p>
 *
 * <p><b>NOTE: The CpfClient is not thread safe. A separate instance must be
 * created for each thread that uses the API.</b></p>
 *
 * <p>The following code fragment shows an example of using the API for an application that
 * accepts and returns <a href="../../structuredData.html">structured data</a>. Additional methods
 * are provided for opaque data. The documentation of each method in this API provides an example
 * of use.</p>
 * <p><b>NOTE:</b> The examples are simplified versions of using the API. <b>They should not be considered
 * the correct way to write an application.</b>. In the following example it waits a maximum of 2
 * seconds (2000 milliseconds) to see if the structured result file is available. In the real world
 * jobs will take a longer undetermined time to execute. Applications should use the
 * <a href="../../notificationCallback.html">notification callback</a> mechanism rather than polling the
 * server or if that is not possible something like the Java
 * <a href="http://docs.oracle.com/javase/7/docs/api/java/util/concurrent/ScheduledExecutorService.html">ScheduledExecutorService</a>.</p>
 *
 * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92j025");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobStructuredResults(jobId, 2000);
      for (Map&lt;String, Object&gt; result : results) {
        System.out.println(result);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
 * <p class="note">The CPF requires that <a href="http://en.wikipedia.org/wiki/UTF-8">UTF-8</a>
 * encoding be used for all text files. This includes the text in a .dbf file for a .shpz archive, unless
 * a .cpf file is provided in the shpz archive.</p>
 */
public class CpfClient implements BaseCloseable {
  /** DigestHttpClient using OAuth credentials */
  private CpfHttpClientPool httpClientPool;

  /**
   * <p>Construct a new CpfClient connected to the specified server using the
   * consumerKey and consumerSecret for authentication.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);</pre>
   *
   * @param url The full URL of the CPF Web Services, including the domain,
   * port number and path e.g. https://apps.gov.bc.ca/cpf/ws/
   * @param consumerKey The application's OAuth Consumer Key (user name) to
   * connect to the service.
   * @param consumerSecret The OAuth Consumer Secret (encryption key) used to
   * sign the requests for the Consumer Key.
   */
  public CpfClient(String url, final String consumerKey, final String consumerSecret) {
    url = url.replaceAll("(/ws)?/*$", "");
    this.httpClientPool = new CpfHttpClientPool(url, consumerKey, consumerSecret, 1);
  }

  /**
   * Add the job parameters to the request.
   *
   * @param request The request.
   * @param jobParameters The parameters.s
   */
  private void addJobParameters(final HttpMultipartPost request,
    final Map<String, ? extends Object> jobParameters) {
    request.addHeader("Accept", "application/json");
    if (jobParameters != null && !jobParameters.isEmpty()) {
      for (final String parameterName : jobParameters.keySet()) {
        final Object value = jobParameters.get(parameterName);
        request.addParameter(parameterName, value);
      }
    }
  }

  /**
   * <p>Cancel the job
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.cancelJob">Cancel Job</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  try (CpfClient client = new CpfClient(url, consumerKey, consumerSecret){
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92g025");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    // Download the results of the job
    client.closeJob(jobId);
  }</pre>
   *
   * @param jobUrl The URL of the job to be closed.
   */
  public void cancelJob(final String jobUrl) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      httpClient.postResource(jobUrl + "/cancel");
    } catch (final IOException e) {
      throw new RuntimeException("Unable to cancel job " + jobUrl, e);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Close the connection to the CPF service. Once this method has been called
   * the client can no longer be used and a new instance must be created. This should be called
   * when the client is no longer needed to clean up resources.<p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";

  try (CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
    // Use the client
  }</pre>
   */
  @Override
  @PreDestroy
  public void close() {
    this.httpClientPool.close();
    this.httpClientPool = null;
  }

  /**
   * <p>Close the connection to the CPF service. Once this method has been called
   * the client can no longer be used and a new instance must be created. This should be called
   * when the client is no longer needed to clean up resources.<p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    // Use the client
  } finally {
    client.closeConnection();
  }</pre>
   */
  @Deprecated
  public void closeConnection() {
    close();
  }

  /**
   * Replace by {@link #cancelJob(String)}. Or use {@link #deleteJob(String)} to actually delete the job.
   *
   * @param jobUrl The URL of the job to be closed.
   * @see #cancelJob(String)
   * @see #deleteJob(String)
   */
  @Deprecated
  public void closeJob(final String jobUrl) {
    deleteJob(jobUrl);
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../opaqueData.html">opaque input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the opaque data for each request is specified using
   * a spring framework <a href="http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/core/io/Resource.html">Resource</a> object.</p>
   *
   * <p><b>NOTE: There is a limit of 20MB of data for a multipart/form-data HTTP request. For
   * jobs with large volumes of data make the data available on a HTTP server use the URL
   * versions of this method instead.</b></p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("algorithmName", "MD5");

    List&lt;Resource&gt; requests = new ArrayList&lt;Resource&gt;();
    requests.add(new ByteArrayResource("Test string".getBytes()));
    // requests.add(new FileSystemResource(pathToFile));

    String jobId = client.createJobWithOpaqueResourceRequests("Digest",
      "1.0.0", parameters, "text/plain", "application/json", requests);
    // Download the results of the job
    client.closeJob(jobId);
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param jobParameters The global job parameters.
   * @param inputDataContentType The <a href="../../fileFormats.html">media type</a> used for all the
   * requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data
   * using.
   * @param requests The resource for the requests.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithOpaqueResourceRequests(final String businessApplicationName,
    final Map<String, Object> jobParameters, final String inputDataContentType,
    final String resultContentType, final Collection<Resource> requests) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/multiple/");

      final HttpMultipartPost request = new HttpMultipartPost(httpClient, url);
      addJobParameters(request, jobParameters);

      request.addParameter("numRequests", requests.size());
      for (final Resource inputData : requests) {
        request.addParameter("inputData", inputData, inputDataContentType);
        request.addParameter("inputDataContentType", inputDataContentType);
      }
      request.addParameter("resultDataContentType", resultContentType);

      request.addParameter("media", "application/json");
      return httpClient.postResourceRedirect(request);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../opaqueData.html">opaque input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the opaque data for each request is specified using
   * a spring framework <a href="http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/core/io/Resource.html">Resource</a> object.</p>
   *
   * <p><b>NOTE: There is a limit of 20MB of data for a multipart/form-data HTTP request. For
   * jobs with large volumes of data make the data available on a HTTP server use the URL
   * versions of this method instead.</b></p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("algorithmName", "MD5");

    List&lt;Resource&gt; requests = new ArrayList&lt;Resource&gt;();
    requests.add(new ByteArrayResource("Test string".getBytes()));
    // requests.add(Resource resource = new FileSystemResource(pathToFile));

    String jobId = client.createJobWithOpaqueResourceRequests("Digest",
      "1.0.0", parameters, "text/plain", "application/json", requests);
    // Download the results of the job
    client.closeJob(jobId);
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param jobParameters The global job parameters.
   * @param inputDataContentType The <a href="../../fileFormats.html">media type</a> used for all the
   * requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data
   * using.
   * @param requests The resource for the requests.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithOpaqueResourceRequests(final String businessApplicationName,
    final Map<String, Object> jobParameters, final String inputDataContentType,
    final String resultContentType, final Resource... requests) {
    return createJobWithOpaqueResourceRequests(businessApplicationName, jobParameters,
      inputDataContentType, resultContentType, Arrays.asList(requests));
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../opaqueData.html">opaque input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the opaque data for each request is specified using
   * a URL to the data on a publicly accessible HTTP server.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("algorithmName", "MD5");

    &lt;Resource&gt; inputDataUrls = new Array&lt;Resource&gt;();
    inputDataUrls.add("https://apps.gov.bc.ca/pub/cpf/css/cpf.css");

    String jobId = client.createJobWithOpaqueUrlRequests("Digest",
      "1.0.0", parameters, "text/plain", "application/json", inputDataUrls);
    // Download the results of the job
    client.closeJob(jobId);
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param jobParameters The global job parameters.
   * @param inputDataUrls The collection of URLs for the requests.
   * @param inputDataContentType The <a href="../../fileFormats.html">media type</a> used for all the
   * requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data
   * using.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithOpaqueUrlRequests(final String businessApplicationName,
    final Map<String, ? extends Object> jobParameters, final String inputDataContentType,
    final String resultContentType, final Collection<String> inputDataUrls) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/multiple/");

      final HttpMultipartPost request = new HttpMultipartPost(httpClient, url);
      addJobParameters(request, jobParameters);

      request.addParameter("numRequests", inputDataUrls.size());
      request.addParameter("resultDataContentType", resultContentType);
      for (final String inputDataUrl : inputDataUrls) {
        request.addParameter("inputDataUrl", inputDataUrl);
        request.addParameter("inputDataContentType", inputDataContentType);
      }
      request.addParameter("media", "application/json");
      return httpClient.postResourceRedirect(request);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../opaqueData.html">opaque input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the opaque data for each request is specified using
   * a URL to the data on a publicly accessible HTTP server.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("algorithmName", "MD5");

    String inputDataUrl = "https://apps.gov.bc.ca/pub/cpf/css/cpf.css";

    String jobId = client.createJobWithOpaqueUrlRequests("Digest",
      "1.0.0", parameters, "text/plain", "application/json", inputDataUrl);
    // Download the results of the job
    client.closeJob(jobId);
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param jobParameters The global job parameters.
   * @param inputDataUrls The collection of resources for the requests.
   * @param inputDataContentType The <a href="../../fileFormats.html">media type</a> used for all the
   * requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data
   * using.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithOpaqueUrlRequests(final String businessApplicationName,
    final Map<String, ? extends Object> jobParameters, final String inputDataContentType,
    final String resultContentType, final String... inputDataUrls) {
    return createJobWithOpaqueUrlRequests(businessApplicationName, jobParameters,
      inputDataContentType, resultContentType, Arrays.asList(inputDataUrls));
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../structuredData.html">structured input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the structured data is specified as a list of requests. Each request is specified
   * as a map containing the request parameters.</p>
   *
   * <p><b>NOTE: There is a limit of 20MB of data for a multipart/form-data HTTP request. For
   * jobs with large volumes of data make the data available on a HTTP server use the URL
   * version of this method instead.</b></p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; jobParameters = new HashMap&lt;String, Object&gt;();
    jobParameters.put("mapGridName", "BCGS 1:20 000");

    List&lt;Map&lt;String,?extends Object&gt;&gt; requests = new ArrayList&lt;Map&lt;String,?extends Object&gt;&gt;();
    requests.add(Collections.singletonMap("mapTileId", "92j025"));
    requests.add(Collections.singletonMap("mapTileId", "92j016"));

    String jobId = client.createJobWithStructuredMultipleRequestsList(
      "MapTileByTileId", jobParameters, requests,"application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobStructuredResults(
        jobId, 5000);
      for (Map&lt;String, Object&gt; result : results) {
        System.out.println(result);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the web services business
   * application.
   * @param jobParameters A map of additional parameters specific to the
   * requested Business Application.
   * @param requests A list of data Maps of the requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> of the result data.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithStructuredMultipleRequestsList(final String businessApplicationName,
    final Map<String, ? extends Object> jobParameters,
    final List<? extends Map<String, ? extends Object>> requests, final String resultContentType) {
    final String inputDataType = Json.MIME_TYPE;
    final int numRequests = requests.size();

    final MapWriterFactory factory = IoFactory.factoryByMediaType(MapWriterFactory.class,
      inputDataType);
    final ByteArrayOutputStream out = new ByteArrayOutputStream();
    final MapWriter mapWriter = factory.newMapWriter(out);

    for (final Map<String, ? extends Object> requestRecord : requests) {
      mapWriter.write(requestRecord);
    }
    mapWriter.close();
    final Resource inputData = new ByteArrayResource("data.json", out.toByteArray());

    return createJobWithStructuredMultipleRequestsResource(businessApplicationName, jobParameters,
      numRequests, inputData, inputDataType, resultContentType);
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../structuredData.html">structured input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the structured in data for each request is specified using
   * a spring framework <a href="http://static.springsource.org/spring/docs/3.0.x/javadoc-api/org/springframework/core/io/Resource.html">Resource</a> object.
   * The resource must be encoded using the <a href="../../fileFormats.html">file format</a> specified by
   * the inputDataContentType. The resource must contain one record containing the request parameters
   * for each request to be processed by the business application.</p>
   *
   * <p><b>NOTE: There is a limit of 20MB of data for a multipart/form-data HTTP request. For
   * jobs with large volumes of data make the data available on a HTTP server use the URL
   * version of this method instead.</b></p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; jobParameters = new HashMap&lt;String, Object&gt;();
    jobParameters.put("mapGridName", "NTS 1:500 000");

    int numRequests = 48;
    Resource inputData = new FileSystemResource(
      "../cpf-war-app/src/main/webapp/docs/sample/NTS-500000-by-name.csv");

    String jobId = client.createJobWithStructuredMultipleRequestsResource(
      "MapTileByTileId", jobParameters, numRequests, inputData,
      "text/csv", "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobStructuredResults(
        jobId, 30000);
      for (Map&lt;String, Object&gt; result : results) {
        System.out.println(result);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param jobParameters The global job parameters.
   * @param numRequests The number of requests in the input data.
   * @param inputData The resource containing the request input data.
   * @param inputDataContentType The <a href="../../fileFormats.html">media type</a> used for all the
   * requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data
   * using.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithStructuredMultipleRequestsResource(
    final String businessApplicationName, final Map<String, ? extends Object> jobParameters,
    final int numRequests, final Resource inputData, final String inputDataContentType,
    final String resultContentType) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/multiple/");

      final HttpMultipartPost request = new HttpMultipartPost(httpClient, url);
      addJobParameters(request, jobParameters);

      request.addParameter("numRequests", numRequests);
      request.addParameter("resultDataContentType", resultContentType);
      request.addParameter("inputData", inputData, inputDataContentType);
      request.addParameter("inputDataContentType", inputDataContentType);
      request.addParameter("media", "application/json");
      return httpClient.postResourceRedirect(request);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../structuredData.html">structured input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The content of the structured in data for each request is specified using
   * a URL to the data on a publicly accessible HTTP server.
   * The data must be encoded using the <a href="../../fileFormats.html">file format</a> specified by
   * the inputDataContentType. The data must contain one record containing the request parameters
   * for each request to be processed by the business application.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; jobParameters = new HashMap&lt;String, Object&gt;();
    jobParameters.put("mapGridName", "NTS 1:500 000");

    int numRequests = 48;

    String inputDataUrl = "https://apps.gov.bc.ca/pub/cpf/docs/sample/NTS-500000-by-name.csv";
    String jobId = client.createJobWithStructuredMultipleRequestsUrl(
      "MapTileByTileId", jobParameters, numRequests, inputDataUrl,
      "text/csv", "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobStructuredResults(
        jobId, 30000);
      for (Map&lt;String, Object&gt; result : results) {
        System.out.println(result);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param jobParameters The global job parameters.
   * @param numRequests The number of requests in the input data.
   * @param inputDataUrl The URL containing the request input data.
   * @param inputDataContentType The <a href="../../fileFormats.html">media type</a> used for all the
   * requests.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data
   * using.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithStructuredMultipleRequestsUrl(final String businessApplicationName,
    final Map<String, ? extends Object> jobParameters, final int numRequests,
    final String inputDataUrl, final String inputDataContentType, final String resultContentType) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/multiple/");

      final HttpMultipartPost request = new HttpMultipartPost(httpClient, url);
      addJobParameters(request, jobParameters);

      request.addParameter("numRequests", numRequests);
      request.addParameter("resultDataContentType", resultContentType);
      request.addParameter("inputDataUrl", inputDataUrl);
      request.addParameter("inputDataContentType", inputDataContentType);
      request.addParameter("media", "application/json");
      return httpClient.postResourceRedirect(request);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Construct a new new job on the CPF server for a business application that
   * accepts <a href="../../structuredData.html">structured input data</a>
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> REST API.</p>
   *
   * <p>The job and request parameters for the single request in the job are specified using a
   * map of parameter values.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92j025");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobStructuredResults(
        jobId, 5000);
      for (Map&lt;String, Object&gt; result : results) {
        System.out.println(result);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @param parameters The job and request parameters.
   * @param resultContentType The <a href="../../fileFormats.html">media type</a> to return the result data using.
   * @return The job id (URL) of the created job.
   */
  public String createJobWithStructuredSingleRequest(final String businessApplicationName,
    final Map<String, ? extends Object> parameters, final String resultContentType) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/single/");

      final HttpMultipartPost request = new HttpMultipartPost(httpClient, url);
      addJobParameters(request, parameters);

      request.addParameter("resultDataContentType", resultContentType);

      return httpClient.postResourceRedirect(request);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Delete the job
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.deleteJob">Cancel Job</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  try (CpfClient client = new CpfClient(url, consumerKey, consumerSecret){
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92g025");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    // Download the results of the job
    client.deleteJob(jobId);
  }</pre>
   *
   * @param jobUrl The URL of the job to be closed.
   */
  public void deleteJob(final String jobUrl) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      httpClient.deleteUrl(jobUrl);
    } catch (final IOException e) {
      throw new RuntimeException("Unable to close job " + jobUrl, e);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the specification of the instant execution service for a business application
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsInstant">Get Business Applications Instant</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; specification = client.getBusinessApplicationInstantSpecification(
      "MapTileByTileId");
    System.out.println(specification);
   } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @return The map containing the business application specification.
   */
  public Map<String, Object> getBusinessApplicationInstantSpecification(
    final String businessApplicationName) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient
        .getUrl("/ws/apps/" + businessApplicationName + "/instant/?format=json&specification=true");
      final Map<String, Object> result = httpClient.getJsonResource(url);
      return result;
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the specification of the create job with multiple requests service for a business application
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsMultiple">Get Business Applications Multiple</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; specification = client.getBusinessApplicationMultipleSpecification(
      "MapTileByTileId");
    System.out.println(specification);
   } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @return The map containing the business application specification.
   */
  public Map<String, Object> getBusinessApplicationMultipleSpecification(
    final String businessApplicationName) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/multiple/");
      final Map<String, Object> result = httpClient.getJsonResource(url);
      return result;
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the list of business application names a user has access to
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplications">Get Business Applications</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    &lt;Resource&gt; businessApplicationNames = client.getBusinessApplicationNames();
    for (String businessApplicationName : businessApplicationNames) {
      System.out.println(businessApplicationName);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @return The list of business application names.
   */
  public List<String> getBusinessApplicationNames() {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    final String url = httpClient.getUrl("/ws/apps/");
    try {
      final Map<String, Object> result = httpClient.getJsonResource(url);
      @SuppressWarnings("unchecked")
      final List<Map<String, Object>> items = (List<Map<String, Object>>)result.get("resources");
      if (items != null && !items.isEmpty()) {
        final List<String> businessApplicationNames = new ArrayList<>();
        for (final Map<String, Object> item : items) {
          final String businessApplicationName = (String)item.get("businessApplicationName");
          if (Property.hasValue(businessApplicationName)) {
            businessApplicationNames.add(businessApplicationName);
          }
        }
        return businessApplicationNames;
      }
      return Collections.emptyList();
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the specification of the create job with a single request service for a business application
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsSingle">Get Business Applications Single</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; specification = client.getBusinessApplicationSingleSpecification(
      "MapTileByTileId");
    System.out.println(specification);
   } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @return The map containing the business application specification.
   */
  public Map<String, Object> getBusinessApplicationSingleSpecification(
    final String businessApplicationName) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl("/ws/apps/" + businessApplicationName + "/single/");
      final Map<String, Object> result = httpClient.getJsonResource(url);
      return result;
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the list of error results for a job using the
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a>  and
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResult">Get Users Job Result</a> REST API.</p>
   *
   * <p>Each error record is a map containing the following fields.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-striped table-bordered table-condensed">
   *     <caption>Error Result Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Field Name</th>
   *         <th>Description</th>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <th>sequenceNumber</th>
   *         <td>The sequence number of the request that caused the error.</td>
   *       </tr>
   *       <tr>
   *         <th>errorCode</th>
   *         <td>The <a href="#ca.bc.gov.open.cpf.client.api.ErrorCode">Error code</a>.</td>
   *       </tr>
   *       <tr>
   *         <th>errorMessage</th>
   *         <td>A more detailed message describing the error code.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * <p><b>NOTE: This method loads all the error results into memory. If a larger number of
   * errors were generated use the <a href="#ca.bc.gov.open.cpf.client.api.CpfClient.processJobErrorResults(String,long,ca.bc.gov.open.cpf.client.api.Callback)">processJobErrorResults</a>
   * method.</b></p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobErrorResults(
        jobId, 2000);
      for (Map&lt;String, Object&gt; error : results) {
        System.out.println(error);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobIdUrl The job id URL.
   * @param maxWait The maximum number of milliseconds to wait for the job to be
   * completed.
   * @return The reader maps containing the result fields.
   */
  @SuppressWarnings({
    "unchecked", "rawtypes"
  })
  public List<Map<String, Object>> getJobErrorResults(final String jobIdUrl, final long maxWait) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      for (final Map<String, Object> resultFile : getJobResultFileList(jobIdUrl, maxWait)) {
        final String resultType = (String)resultFile.get("batchJobResultType");
        if ("errorResultData".equals(resultType)) {
          final String resultUrl = (String)resultFile.get("resourceUri");
          final MapReader reader = httpClient.getMapReader("error", resultUrl);
          try {
            return (List)reader.toList();
          } finally {
            reader.close();
          }
        }
      }
      return Collections.emptyList();
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }

  }

  /**
   * Get the list if job id Urls for the path.
   *
   * @param path The path to get the job id URLs from.
   * @return The list of job id URLs.
   */
  private List<String> getJobIdUrls(final String path) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final String url = httpClient.getUrl(path);
      final Map<String, Object> jobs = httpClient.getJsonResource(url);
      @SuppressWarnings("unchecked")
      final List<Map<String, Object>> resources = (List<Map<String, Object>>)jobs.get("resources");
      final List<String> jobIdUrls = new ArrayList<>();
      if (resources != null) {
        for (final Map<String, Object> jobResource : resources) {
          final String jobIdUrl = (String)jobResource.get("batchJobUrl");
          if (jobIdUrl != null) {
            jobIdUrls.add(jobIdUrl);
          }
        }
      }
      return jobIdUrls;
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the list of result file descriptions for a job using the
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a> REST API.</p>
   *
   * <p>Each result file description contains the following fields.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-striped table-bordered table-condensed">
   *     <caption>Result Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Field Name</th>
   *         <th>Description</th>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <th>resourceUri</th>
   *         <td>The URL the result file can be downloaded from.</td>
   *       </tr>
   *       <tr>
   *         <th>title</th>
   *         <td>A title of the result file (e.g. Batch Job 913 result 384).</td>
   *       </tr>
   *       <tr>
   *         <th>batchJobResultType</th>
   *         <td>The type of result file structuredResultData, opaqueResultData, or errorResultData.</td>
   *       </tr>
   *       <tr>
   *         <th>batchJobResultContentType</th>
   *         <td>The <a href="../../fileFormats.html">media type</a> of the data in the result file.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; files = client.getJobResultFileList(jobId, 2000);
      for (Map&lt;String, Object&gt; file : files) {
        System.out.println(file);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobIdUrl The job id URL.
   * @param maxWait The maximum number of milliseconds to wait for the job to be
   * completed.
   * @return The list of maps that describe each of the result files.
   */
  @SuppressWarnings("unchecked")
  public List<Map<String, Object>> getJobResultFileList(final String jobIdUrl, final long maxWait) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      if (isJobCompleted(jobIdUrl, maxWait)) {
        final String resultsUrl = jobIdUrl + "results/";
        final Map<String, Object> jobResults = httpClient.getJsonResource(resultsUrl);
        return (List<Map<String, Object>>)jobResults.get("resources");
      } else {
        throw new IllegalStateException("Job results have not yet been created");
      }
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the <a href="../../jobStatus.html">job status</a> using the
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsInfo">Get Users Jobs Info</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      Map&lt;String, Object&gt; status = client.getJobStatus(jobId);
      System.out.println(status);
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobUrl WS URL of the job.
   * @return A map containing the <a href="../../jobStatus.html">job status</a>.
   */
  public Map<String, Object> getJobStatus(final String jobUrl) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      return httpClient.getJsonResource(jobUrl);
    } catch (final HttpStatusCodeException e) {
      if (e.getStatusCode() == 404) {
        return MapEx.EMPTY;
      } else {
        throw e;
      }
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Get the list of structured data results for a job using the
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a>  and
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResult">Get Users Job Result</a> REST API.</p>
   *
   *
   * <p>Each structured data record is a map containing the following fields.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-striped table-bordered table-condensed">
   *     <caption>Result Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Field Name</th>
   *         <th>Description</th>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <th>sequenceNumber</th>
   *         <td>The sequence number of the request that caused the error.</td>
   *       </tr>
   *       <tr>
   *         <th>resultNumber</th>
   *         <td>If the business application returned multiple results for a single request there
   *         will be one record per result with an incrementing result number.</td>
   *       </tr>
   *       <tr>
   *         <th><i>resultFieldName</i></th>
   *         <td>One field for each of the business application specific result fields.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * <p><b>NOTE: This method loads all the structured data results into memory. If a larger number of
   * results were generated use the <a href="#ca.bc.gov.open.cpf.client.api.CpfClient.processJobStructuredResults(String,long,ca.bc.gov.open.cpf.client.api.Callback)">processJobErrorResults</a>
   * method.</b></p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92j025");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; results = client.getJobStructuredResults(jobId, 2000);
      for (Map&lt;String, Object&gt; result : results) {
        System.out.println(result);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobIdUrl The job id (URL) of the job.
   * @param maxWait The maximum number of milliseconds to wait for the job to be completed.
   * @return The list of results.
   */
  @SuppressWarnings({
    "unchecked", "rawtypes"
  })
  public List<Map<String, Object>> getJobStructuredResults(final String jobIdUrl,
    final long maxWait) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      for (final Map<String, Object> resultFile : getJobResultFileList(jobIdUrl, maxWait)) {
        final String resultType = (String)resultFile.get("batchJobResultType");
        if ("structuredResultData".equals(resultType)) {
          final String resultUrl = (String)resultFile.get("resourceUri");
          try (
            final MapReader reader = httpClient.getMapReader(resultUrl)) {
            return (List)reader.toList();
          }
        }
      }
      throw new IllegalStateException("Cannot find structured result file for " + jobIdUrl);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }

  }

  /**
   * <p>Get the job id URLs for all the user's jobs using the
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobs">Get Users Jobs</a> REST API.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      &lt;Resource&gt; jobIds = client.getUserJobIdUrls();
      for (String jobIdUrl : jobIds) {
        System.out.println(jobIdUrl);
      }
      if (!jobIds.contains(jobId)) {
        System.err.println("Missing job " + jobId);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @return The list of job id URLs.
   */
  public List<String> getUserJobIdUrls() {
    final String path = "/ws/jobs/";
    return getJobIdUrls(path);
  }

  /**
   * <p>Get the job id URLs to the user's jobs for a business application using the
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersBusinessApplicationsJobs">Get Business Applications Users Jobs</a> REST API.</p>
   * Get the list of all job id URLs the user created.
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      &lt;Resource&gt; jobIds = client.getUserJobIdUrls("MapTileByTileId");
      for (String jobIdUrl : jobIds) {
        System.out.println(jobIdUrl);
      }
      if (!jobIds.contains(jobId)) {
        System.err.println("Missing job " + jobId);
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param businessApplicationName The name of the business application.
   * @return The list of job id URLs.
   */
  public List<String> getUserJobIdUrls(final String businessApplicationName) {
    final String path = "/ws/apps/" + businessApplicationName + "/jobs/";
    return getJobIdUrls(path);
  }

  /**
   * <p>Check the <a href="../../jobStatus.html">job</a> status to see if it has been completed using the
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsInfo">Get Users Jobs Info</a> REST API.</p>
   *
   * <p>If the job has not been completed within the maxWait number of milliseconds false will be
   * returned.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      boolean completed = client.isJobCompleted(jobId, 2000);
      if (completed) {
        System.out.println("Job Completed");
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobIdUrl The job id URL.
   * @param maxWait The maximum number of milliseconds to wait.
   * @return True if the job was completed, false otherwise.
   */
  public boolean isJobCompleted(final String jobIdUrl, final long maxWait) {
    if (maxWait > 0) {
      final long startTime = System.currentTimeMillis();
      final long maxEnd = startTime + maxWait;
      long currentTime = startTime;
      while (currentTime < maxEnd) {
        final Map<String, Object> jobStatusMap = getJobStatus(jobIdUrl);
        final String jobStatus = (String)jobStatusMap.get("jobStatus");
        if ("resultsCreated".equals(jobStatus) || "downloadInitiated".equals(jobStatus)) {
          return true;
        }
        long sleepTime = ((Number)jobStatusMap.get("secondsToWaitForStatusCheck")).intValue()
          * 1000L;
        if (sleepTime == 0) {
          sleepTime = 1000;
        }
        sleepTime = Math.min(sleepTime, maxEnd - System.currentTimeMillis());
        if (sleepTime > 0) {
          synchronized (this) {
            try {
              wait(sleepTime);
            } catch (final InterruptedException e) {
              throw new RuntimeException(e);
            }
          }
        }
        currentTime = System.currentTimeMillis();
      }
    }
    final Map<String, Object> jobStatusMap = getJobStatus(jobIdUrl);
    final String jobStatus = (String)jobStatusMap.get("jobStatus");
    return "resultsCreated".equals(jobStatus);
  }

  /**
   * <p>Process the list of error results for a job using the
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a>  and
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResult">Get Users Job Result</a> REST API.</p>
   *
   * <p>Each error record is a map containing the following fields.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-striped table-bordered table-condensed">
   *     <caption>Error Result Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Field Name</th>
   *         <th>Description</th>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <th>sequenceNumber</th>
   *         <td>The sequence number of the request that caused the error.</td>
   *       </tr>
   *       <tr>
   *         <th>errorCode</th>
   *         <td>The <a href="#ca.bc.gov.open.cpf.client.api.ErrorCode">Error code</a>.</td>
   *       </tr>
   *       <tr>
   *         <th>errorMessage</th>
   *         <td>A more detailed message describing the error code.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * <p>The callback method will be invoked for each record in the error file.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "INVALID");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      int numErrors = client.processJobErrorResults(jobId, 10000,
        new Callback&lt;Map&lt;String, Object&gt;&gt;() {
          public void process(Map&lt;String, Object&gt; error) {
            System.out.println(error);
          }
        });
      System.out.println(numErrors);
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobIdUrl The job id (URL) of the job.
   * @param maxWait The maximum number of milliseconds to wait for the job to be completed.
   * @param callback The call back in the client application that will be called for each error record.
   * @return The number of error results processed.
   */
  public int processJobErrorResults(final String jobIdUrl, final long maxWait,
    final Callback<Map<String, Object>> callback) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final List<Map<String, Object>> jobResultFileList = getJobResultFileList(jobIdUrl, maxWait);
      if (jobResultFileList.isEmpty()) {
        throw new IllegalStateException("Cannot find error result file for " + jobIdUrl);
      } else {
        int i = 0;
        for (final Map<String, Object> resultFile : jobResultFileList) {
          final String resultType = (String)resultFile.get("batchJobResultType");
          if ("errorResultData".equals(resultType)) {
            final String resultUrl = (String)resultFile.get("resourceUri");
            try (
              final MapReader reader = httpClient.getMapReader(resultUrl)) {
              for (final Map<String, Object> object : reader) {
                callback.process(object);
                i++;
              }
            }
          }
        }
        return i;
      }
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Process the list of structured data results for a job using the
   * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a>  and
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResult">Get Users Job Result</a> REST API.</p>
   *
   * <p>Each structured record is a map with the fields sequenceNumber (to map back
   * to the input request), resultNumber (if multiple results were returned for a single request
   * and the business application specific result fields.</p>
   *
   * <p>The callback method will be invoked for each record in the structured result data file.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92j025");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      int numResults = client.processJobStructuredResults(jobId, 10000,
        new Callback&lt;Map&lt;String, Object&gt;&gt;() {
          public void process(Map&lt;String, Object&gt; result) {
            System.out.println(result);
          }
        });
      System.out.println(numResults);
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobIdUrl The job id (URL) of the job.
   * @param maxWait The maximum number of milliseconds to wait for the job to be completed.
   * @param callback The call back in the client application that will be called for each result record.
   * @return The number of results processed.
   */
  public int processJobStructuredResults(final String jobIdUrl, final long maxWait,
    final Callback<Map<String, Object>> callback) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final List<Map<String, Object>> jobResultFileList = getJobResultFileList(jobIdUrl, maxWait);
      if (jobResultFileList.isEmpty()) {
        throw new IllegalStateException("Cannot find structured result file for " + jobIdUrl);
      } else {
        int i = 0;
        for (final Map<String, Object> resultFile : jobResultFileList) {
          final String resultType = (String)resultFile.get("batchJobResultType");
          if ("structuredResultData".equals(resultType)) {
            final String resultUrl = (String)resultFile.get("resourceUri");
            try (
              final MapReader reader = httpClient.getMapReader(resultUrl)) {
              for (final Map<String, Object> object : reader) {
                callback.process(object);
                i++;
              }
            }
          }
        }
        return i;
      }
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

  /**
   * <p>Process the result file for a job using the
   * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResult">Get Users Job Result</a> REST API.</p>
   *
   * <p>The callback method will be invoked with the input stream to the file. This method ensures
   * that the input stream is closed correctly after being processed.</p>
   *
   * <p>The following code fragment shows an example of using the API.</p>
   *
   * <pre class="prettyprint language-java">  String url = "https://apps.gov.bc.ca/pub/cpf";
  String consumerKey = "cpftest";
  String consumerSecret = "cpftest";
  CpfClient client = new CpfClient(url, consumerKey, consumerSecret);
  try {
    Map&lt;String, Object&gt; parameters = new HashMap&lt;String, Object&gt;();
    parameters.put("mapGridName", "BCGS 1:20 000");
    parameters.put("mapTileId", "92j016");
    String jobId = client.createJobWithStructuredSingleRequest(
      "MapTileByTileId", parameters, "application/json");
    try {
      List&lt;Map&lt;String, Object&gt;&gt; files = client.getJobResultFileList(
        jobId, 5000);
      for (Map&lt;String, Object&gt; file : files) {
       String jobResultUrl = (String)file.get("resourceUri");
      client.processResultFile(jobResultUrl , new Callback&lt;Base64InputStream&gt;() {
        public void process(Base64InputStream in) {
          // Read file from input stream
        }
      });
      }
    } finally {
      client.closeJob(jobId);
    }
  } finally {
    client.closeConnection();
  }</pre>
   *
   * @param jobResultUrl The URL to the job result file.
   * @param resultProcessor The processor to process the input stream returned
   * from the web service.
   */
  public void processResultFile(final String jobResultUrl,
    final Callback<InputStream> resultProcessor) {
    final CpfHttpClient httpClient = this.httpClientPool.getClient();
    try {
      final HttpResponse response = httpClient.getResource(jobResultUrl);
      final HttpEntity entity = response.getEntity();
      try {
        final StatusLine statusLine = response.getStatusLine();
        final int httpStatusCode = statusLine.getStatusCode();
        final String httpStatusMessage = statusLine.getReasonPhrase();

        if (httpStatusCode >= HttpStatus.SC_BAD_REQUEST) {
          throw new RuntimeException(httpStatusCode + " " + httpStatusMessage);
        }
        final InputStream in = entity.getContent();
        resultProcessor.process(in);
      } finally {
        EntityUtils.consume(entity);
      }

    } catch (final IOException e) {
      throw new RuntimeException("Unable to get file " + jobResultUrl, e);
    } finally {
      this.httpClientPool.releaseClient(httpClient);
    }
  }

}
