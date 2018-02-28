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
package ca.bc.gov.open.cpf.client.test;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ca.bc.gov.open.cpf.client.api.Callback;
import ca.bc.gov.open.cpf.client.api.CpfClient;

import com.revolsys.spring.resource.PathResource;
import com.revolsys.spring.resource.Resource;

@SuppressWarnings("javadoc")
public class CpfClientTest {
  private static final String consumerKey = "cpftest";

  private static final String consumerSecret = "cpftest";

  private static final String url = "http://localhost/pub/cpf";

  public static void main(final String[] args) throws IOException {
    testConstructor();
    testCloseConnection();

    testGetBusinessApplicationNames();
    testGetBusinessApplicationSingleSpecification();
    testGetBusinessApplicationInstantSpecification();
    testGetBusinessApplicationMultipleSpecification();

    testDeleteBatchJob();

    // testCreateOpaqueResource();
    // testCreateOpaqueResourceCollection();
    // testCreateOpaqueUrl();
    // testCreateOpaqueUrlCollection();

    testCreateStructuredSingle();
    testCreateStructuredMultipleList();
    testCreateStructuredMultipleResource();
    testCreateStructuredMultipleUrl();

    testGetBatchJobStatus();
    testIsBatchJobCompleted();
    testGetBatchJobResultsError();
    testProcessBatchJobResultsError();
    testGetBatchJobResultsList();
    testGetBatchJobStructuredResults();
    testProcessBatchJobStructuredResults();

    testUserGetBatchJobIds();
    testUserAppGetBatchJobIds();
    testProcessBatchJobResultFile();
  }

  private static void testCloseConnection() {
    System.out.println("Close Connection");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      // Use the client
    }
  }

  private static void testConstructor() {
    System.out.println("Constructor");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
    }
  }

  private static void testCreateStructuredMultipleList() {
    System.out.println("Create Batch Job Structured Multiple List");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> jobParameters = new HashMap<>();
      jobParameters.put("mapGridName", "BCGS 1:20 000");

      final List<Map<String, ? extends Object>> requests = new ArrayList<>();
      requests.add(Collections.singletonMap("mapTileId", "92j025"));
      requests.add(Collections.singletonMap("mapTileId", "92j016"));

      final String batchJobId = client.createJobWithStructuredMultipleRequestsList(
        "MapTileByTileId", jobParameters, requests, "application/json");
      try {
        final List<Map<String, Object>> results = client.getJobStructuredResults(batchJobId, 20000);
        for (final Map<String, Object> result : results) {
          System.out.println(result);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testCreateStructuredMultipleResource() {
    System.out.println("Create Batch Job Structured Multiple Resource");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> jobParameters = new HashMap<>();
      jobParameters.put("mapGridName", "NTS 1:500 000");

      final int numRequests = 48;
      final Resource inputData = new PathResource(
        "../plugins/map-tile/src/data/NTS-500000-by-name.csv");

      final String batchJobId = client.createJobWithStructuredMultipleRequestsResource(
        "MapTileByTileId", jobParameters, numRequests, inputData, "text/csv", "application/json");
      try {
        final List<Map<String, Object>> results = client.getJobStructuredResults(batchJobId, 30000);
        for (final Map<String, Object> result : results) {
          System.out.println(result);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testCreateStructuredMultipleUrl() {
    System.out.println("Create Batch Job Structured Multiple URL");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> jobParameters = new HashMap<>();
      jobParameters.put("mapGridName", "NTS 1:500 000");

      final int numRequests = 48;

      final String inputDataUrl = "https://bcgov.github.io/cpf/sample/NTS-500000-by-name.csv";
      final String batchJobId = client.createJobWithStructuredMultipleRequestsUrl("MapTileByTileId",
        jobParameters, numRequests, inputDataUrl, "text/csv", "application/json");
      try {
        final List<Map<String, Object>> results = client.getJobStructuredResults(batchJobId, 30000);
        for (final Map<String, Object> result : results) {
          System.out.println(result);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testCreateStructuredSingle() {
    System.out.println("Create Batch Job Structured Single");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "92j025");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        try {
          final List<Map<String, Object>> results = client.getJobStructuredResults(batchJobId,
            10000);
          for (final Map<String, Object> result : results) {
            System.out.println(result);
          }
        } catch (final IllegalStateException e) {
          final List<Map<String, Object>> jobErrorResults = client.getJobErrorResults(batchJobId,
            1000);
          for (final Map<String, Object> result : jobErrorResults) {
            System.err.println(result);
          }
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testDeleteBatchJob() {
    System.out.println("Close Batch Job");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "92g025");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      // Download the results of the job
      client.deleteJob(batchJobId);
    }
  }

  private static void testGetBatchJobResultsError() {
    System.out.println("Get Batch Job Results Error");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final List<Map<String, Object>> results = client.getJobErrorResults(batchJobId, 10000);
        for (final Map<String, Object> error : results) {
          System.out.println(error);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testGetBatchJobResultsList() {
    System.out.println("Get Batch Job Results File List");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final List<Map<String, Object>> files = client.getJobResultFileList(batchJobId, 10000);
        for (final Map<String, Object> file : files) {
          System.out.println(file);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testGetBatchJobStatus() {
    System.out.println("Get Batch Job Status");
    final String url = "http://localhost/pub/cpf";
    final String consumerKey = "cpftest";
    final String consumerSecret = "cpftest";
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final Map<String, Object> status = client.getJobStatus(batchJobId);
        System.out.println(status);
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testGetBatchJobStructuredResults() {
    System.out.println("Get Batch Job Structured Results");
    final String url = "http://localhost/pub/cpf";
    final String consumerKey = "cpftest";
    final String consumerSecret = "cpftest";
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "92j025");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final List<Map<String, Object>> results = client.getJobStructuredResults(batchJobId, 10000);
        for (final Map<String, Object> result : results) {
          System.out.println(result);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testGetBusinessApplicationInstantSpecification() {
    System.out.println("Get Business Application Specification");
    final String url = "http://localhost/pub/cpf";
    final String consumerKey = "cpftest";
    final String consumerSecret = "cpftest";
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> specification = client
        .getBusinessApplicationInstantSpecification("MapTileByTileId");
      System.out.println(specification);
    }
  }

  private static void testGetBusinessApplicationMultipleSpecification() {
    System.out.println("Get Business Application Specification");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> specification = client
        .getBusinessApplicationMultipleSpecification("MapTileByTileId");
      System.out.println(specification);
    }
  }

  private static void testGetBusinessApplicationNames() {
    System.out.println("Get Business Application Names");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final List<String> businessApplicationNames = client.getBusinessApplicationNames();
      for (final String businessApplicationName : businessApplicationNames) {
        System.out.println(businessApplicationName);
      }
    }
  }

  private static void testGetBusinessApplicationSingleSpecification() {
    System.out.println("Get Business Application Specification");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> specification = client
        .getBusinessApplicationSingleSpecification("MapTileByTileId");
      System.out.println(specification);
    }
  }

  private static void testIsBatchJobCompleted() {
    System.out.println("Is Batch Job Completed");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final boolean completed = client.isJobCompleted(batchJobId, 2000);
        if (completed) {
          System.out.println("Job Completed");
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testProcessBatchJobResultFile() {
    System.out.println("Process Batch Job Results File");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "92j016");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final List<Map<String, Object>> files = client.getJobResultFileList(batchJobId, 10000);
        for (final Map<String, Object> file : files) {
          final String jobResultUrl = (String)file.get("resourceUri");
          client.processResultFile(jobResultUrl, new Callback<InputStream>() {

            @Override
            public void process(final InputStream in) {
              try {
                final BufferedReader reader = new BufferedReader(new InputStreamReader(in));
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                  System.out.println(line);
                }
              } catch (final IOException e) {
                throw new RuntimeException(e);
              }
            }
          });
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testProcessBatchJobResultsError() {
    System.out.println("Process Batch Job Results Error");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final int numErrors = client.processJobErrorResults(batchJobId, 10000,
          new Callback<Map<String, Object>>() {
            @Override
            public void process(final Map<String, Object> error) {
              System.out.println(error);
            }
          });
        System.out.println(numErrors);
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testProcessBatchJobStructuredResults() {
    System.out.println("Process Batch Job Structured Results");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "92j025");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final int numResults = client.processJobStructuredResults(batchJobId, 10000,
          new Callback<Map<String, Object>>() {
            @Override
            public void process(final Map<String, Object> result) {
              System.out.println(result);
            }
          });
        System.out.println(numResults);
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testUserAppGetBatchJobIds() {
    System.out.println("User Get App Batch Job Ids");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final List<String> batchJobIds = client.getUserJobIdUrls("MapTileByTileId");
        for (final String batchJobIdUrl : batchJobIds) {
          System.out.println(batchJobIdUrl);
        }
        if (!batchJobIds.contains(batchJobId)) {
          System.err.println("Missing job " + batchJobId);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }

  private static void testUserGetBatchJobIds() {
    System.out.println("User Get Batch Job Ids");
    try (
      final CpfClient client = new CpfClient(url, consumerKey, consumerSecret)) {
      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("mapGridName", "BCGS 1:20 000");
      parameters.put("mapTileId", "INVALID");
      final String batchJobId = client.createJobWithStructuredSingleRequest("MapTileByTileId",
        parameters, "application/json");
      try {
        final List<String> batchJobIds = client.getUserJobIdUrls();
        for (final String batchJobIdUrl : batchJobIds) {
          System.out.println(batchJobIdUrl);
        }
        if (!batchJobIds.contains(batchJobId)) {
          System.err.println("Missing job " + batchJobId);
        }
      } finally {
        client.deleteJob(batchJobId);
      }
    }
  }
}
