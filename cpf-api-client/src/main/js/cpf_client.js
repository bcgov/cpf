/**
 * <p>Construct a new CpfClient connected to the specified server. The user
 * will prompted to authenticate</p>
 * 
 * <p>The following code fragment shows an example of using the API.</p>
 * 
 * <pre class="prettyprint language-javascript">  var cpfServerUrl = 'https://apps.gov.bc.ca/pub/cpf/secure/ws/';
  var client = new CpfClient(cpfServerUrl);</pre>
<script type="text/javascript">
$(document).ready(function() {
  var url = String(document.location);
  var docsIndex = url.indexOf('/docs/client');
  if (docsIndex > 0) {
    cpfServerUrl = url.substring(0, docsIndex) + '/ws';
  } else {
    cpfServerUrl = 'https://apps.gov.bc.ca/pub/cpf/secure/ws/';
  }
});
</script>
 * 
 * @param {string} url The full URL of the CPF Web Services, including the
 * domain, port number and path e.g. http://apps.gov.bc.ca/cpf/ws/
 * @constructor
 */
function CpfClient(url) {
  this.cpfLoginWindow = null;
  if (url.lastIndexOf('/') == url.length - 1) {
    this.url = url.substring(0, url.length - 1);
  } else {
    this.url = url;
  }
};

/**
 * <p>Get the specification of the instant execution service for a business application
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsInstant">Get Business Applications Instant</a> REST API.</p>
 * 
<div class="htmlExample"><button id="getBusinessApplicationInstantSpecification" type="button">Get Business Application's Instant Specification</button>

<div id="businessApplicationInstantSpecification"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getBusinessApplicationInstantSpecification').click(function() {
    var client = new CpfClient(cpfServerUrl);
    client.getBusinessApplicationInstantSpecification('MapTileByTileId', function(specification) {
      var div = $('#businessApplicationInstantSpecification');
      client.toHtml(div, specification);
    });
  });
});
</script>
</div>
 * @param {string}   businessApplicationName The name of the business application.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getBusinessApplicationInstantSpecification = function(
    businessApplicationName,
    callback) {
  var path = '/apps/' + businessApplicationName + '/instant.json';
  this.getJsonIfLoggedIn(path, {'specification': 'true'}, callback);
};


/**
 * <p>Get the specification of the instant execution service for a business application
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsMultiple">Get Business Applications Multiple</a> REST API.</p>
 * 
<div class="htmlExample"><button id="getBusinessApplicationMultipleSpecification" type="button">Get Business Application's Multiple Specification</button>

<div id="businessApplicationMultipleSpecification"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getBusinessApplicationMultipleSpecification').click(function() {
    var client = new CpfClient(cpfServerUrl);
    client.getBusinessApplicationMultipleSpecification('MapTileByTileId', function(specification) {
      var div = $('#businessApplicationMultipleSpecification');
      client.toHtml(div, specification);
    });
  });
});
</script>
</div>
 * @param {string}   businessApplicationName The name of the business application.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getBusinessApplicationMultipleSpecification = function(
    businessApplicationName,
    callback) {
  var path = '/apps/' + businessApplicationName + '/multiple.json';
  this.getJsonIfLoggedIn(path, {'specification': 'true'}, callback);
};

/**
 * <p>Get the list of business application names a user has access to
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplications">Get Business Applications</a> REST API.</p>
 * 
<div class="htmlExample"><button id="getBusinessApplicationNames" type="button">Get Business Application Names</button>

<div id="businessApplicationNames"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getBusinessApplicationNames').click(function() {
    var client = new CpfClient(cpfServerUrl);
    client.getBusinessApplicationNames(function(businessApplicationNames) {
      var div = $('#businessApplicationNames');
      div.empty();
      div.append('<p><b>Business Application Names</b></p>');
      var ul = $('<ul>').appendTo(div);
      $(businessApplicationNames).each(function() {
        var businessApplicationName = String(this);
        $('<li>').text(businessApplicationName).appendTo(ul);
      });
    });
  });
});
</script>
</div>
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getBusinessApplicationNames = function(
    callback) {
  this.getResourcesValues(
    '/apps.json',
    'businessApplicationName',
    callback);
};

/**
 * <p>Get the specification of the instant execution service for a business application
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsSingle">Get Business Applications Single</a> REST API.</p>
 * 
<div class="htmlExample"><button id="getBusinessApplicationSingleSpecification" type="button">Get Business Application's Single Specification</button>

<div id="businessApplicationSingleSpecification"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getBusinessApplicationSingleSpecification').click(function() {
    var client = new CpfClient(cpfServerUrl);
    client.getBusinessApplicationSingleSpecification('MapTileByTileId', function(specification) {
      var div = $('#businessApplicationSingleSpecification');
      client.toHtml(div, specification);
    });
  });
});
</script>
</div>
 * @param {string}   businessApplicationName The name of the business application.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getBusinessApplicationSingleSpecification = function(
    businessApplicationName,
    callback) {
  var path = '/apps/' + businessApplicationName + '/single.json';
  this.getJsonIfLoggedIn(path, {'specification': 'true'}, callback);
};

/**
 * <p>Get the URL to the error results file for a job using the
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a> REST API.</p>
 *
 * <p><b>NOTE: due to cross domain constraints and the CSV format used for errors it is not possible to parse the contents of the CSV file in JavaScript.</b></p>
 *
<div class="htmlExample"><button id="getJobErrorResultsUrl" type="button">Get Job Error Results URL</button>

<div><b>Job Status URL</b> <input id="jobErrorResultsUrlUrl" size="80" /></div>

<div id="jobErrorResultsUrl"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getJobErrorResultsUrl').click(function() {
    var client = new CpfClient(cpfServerUrl);
    var jobStatusUrl = $('#jobErrorResultsUrlUrl').val();
    client.getJobErrorResultUrl(jobStatusUrl, function(results) {
      var div = $('#jobErrorResultsUrl');
      client.toHtml(div, results);
    });
  });
});
</script>
</div>
 * @param {string}   jobIdUrl The URL to the job status page.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getJobErrorResultUrl = function(
    jobIdUrl,
    callback) {
  this.getJobResultFileList(jobIdUrl, function(resultFiles) {
    var len = resultFiles.length;
    for ( var i = 0; i < len; i++) {
      var resultFile = resultFiles[i];
      if (resultFile['batchJobResultType'] == 'errorResultData') {
        callback(resultFile['resourceUri']);
        return;
      }
    }
    callback(null);
  });
};

/**
 * <p>Get the list of result file descriptions for a job using the
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a> REST API.</p>
 * 
 * <p>Each result file description contains the following fields.</p>
 * 
 * <div class="simpleDataTable">
 *   <table>
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
<div class="htmlExample"><button id="getJobResultFileList" type="button">Get User Job Result File List</button>

<div><b>Job Status URL</b> <input id="jobResultFileListUrl" size="80" /></div>

<div id="jobResultFileList"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getJobResultFileList').click(function() {
    var client = new CpfClient(cpfServerUrl);
    var jobStatusUrl = $('#jobResultFileListUrl').val();
    client.getJobResultFileList(jobStatusUrl, function(results) {
      var div = $('#jobResultFileList');
      client.toHtml(div, results);
    });
  });
});
</script>
</div>
 * @param {string}   jobIdUrl The URL to the job status page.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getJobResultFileList = function(
    jobIdUrl,
    callback) {
  var path = jobIdUrl.substring(this.url.length) + '/results/';
  this.getJsonIfLoggedIn(path, {}, function(results) {
    var resources = results['resources'];
    if (resources) {
      callback(resources);
    } else {
      callback(new Array());
    }
  });
};

/**
 * <p>Get the list of structured data results for a job using the
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a>  and
 * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResult">Get Users Job Result</a> REST API.</p>
 *
 *
 * <p>Each structured data record is a map containing the following fields.</p>
 *
 * <div class="simpleDataTable">
 *   <table>
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
 * results were generated get the result URL and process manually.</b></p>
 * 
<div class="htmlExample"><button id="getJobStructuredResults" type="button">Get Job Structured Results</button>

<div><b>Job Status URL</b> <input id="jobStructuredResultsUrl" size="80" /></div>

<div id="jobStructuredResults"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getJobStructuredResults').click(function() {
    var client = new CpfClient(cpfServerUrl);
    var jobStatusUrl = $('#jobStructuredResultsUrl').val();
    client.getJobStructuredResults(jobStatusUrl, function(results) {
      var div = $('#jobStructuredResults');
      client.toHtml(div, results);
    });
  });
});
</script>
</div>
 * @param {string}   jobIdUrl The URL to the job status page.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getJobStructuredResults = function(
    jobIdUrl,
    callback) {
  var self = this;
  this.getJobResultFileList(jobIdUrl, function(resultFiles) {
    var len = resultFiles.length;
    for ( var i = 0; i < len; i++) {
      var resultFile = resultFiles[i];
      if (resultFile['batchJobResultType'] == 'structuredResultData') {
        var resultUri = resultFile['resourceUri'];
        var resultContentType = resultFile['batchJobResultContentType'];

        var path = resultUri.substring(self.url.length);
        if (resultContentType == 'application/json') {
          self.getJsonIfLoggedIn(path, {}, function(results) {
            if (results) {
              var items = results['items'];
              if (items) {
                callback(items);
                return;
              }
            }
            callback('');
          });
        } else {
          self.getTextIfLoggedIn(path, {}, function(results) {
            if (results) {
              callback(results);
            } else {
              callback('');
            }
          });
        }
        return;
      }
    }
    callback(new Array());
  });
};

/**
 * <p>Get the URL to the structured data results file for a job using the
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsResults">Get Users Job Results</a> REST API.</p>
 *
<div class="htmlExample"><button id="getJobStructuredResultsUrl" type="button">Get Job Structured Results URL</button>

<div><b>Job Status URL</b> <input id="jobStructuredResultsUrlUrl" size="80" /></div>

<div id="jobStructuredResultsUrlResult"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getJobStructuredResultsUrl').click(function() {
    var client = new CpfClient(cpfServerUrl);
    var jobStatusUrl = $('#jobStructuredResultsUrlUrl').val();
    client.getJobStructuredResultUrl(jobStatusUrl, function(results) {
      var div = $('#jobStructuredResultsUrlResult');
      client.toHtml(div, results);
    });
  });
});
</script>
</div>
 * @param {string}   jobIdUrl The URL to the job status page.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getJobStructuredResultUrl = function(
    jobIdUrl,
    callback) {
  this.getJobResultFileList(jobIdUrl, function(resultFiles) {
    var len = resultFiles.length;
    for ( var i = 0; i < len; i++) {
      var resultFile = resultFiles[i];
      if (resultFile['batchJobResultType'] == 'structuredResultData') {
        callback(resultFile['resourceUri']);
        return;
      }
    }
    callback(null);
  });
};

/**
 * <p>Get the <a href="../../jobStatus.html">job status</a> using the
 * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobsInfo">Get Users Jobs Info</a> REST API.</p>
 * 
<div class="htmlExample"><button id="getJobStatus" type="button">Get Job Status</button>

<div><b>Job Status URL</b> <input id="jobStatusUrl" size="80" /></div>

<div id="jobStatus"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getJobStatus').click(function() {
    var client = new CpfClient(cpfServerUrl);
    var jobStatusUrl = $('#jobStatusUrl').val();
    client.getJobStatus(jobStatusUrl, function(status) {
      var div = $('#jobStatus');
      client.toHtml(div, status);
    });
  });
});
</script>
</div>
 * @param {string}   jobIdUrl The URL to the job status page.
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getJobStatus = function(
    jobUrl,
    callback) {
  var path = jobUrl.substring(this.url.length);
  this.getJsonIfLoggedIn(path, {}, callback);
};

/**
 * <p>Submit a form to Construct a new new job on the CPF server for a business application that
 * accepts <a href="../../structuredData.html">structured input data</a>
 * using the <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithSingleRequest">Create Job With Single Request</a> REST API.</p>
 * 
 * <p>The job and request parameters for the single request in the job are specified using a
 * multipart/form-data form comtainin a field for each parameter.</p>
 * 
<div class="htmlExample"><button id="submitSingleRequestJobForm" type="button">Submit Single Request Job</button>

<form id="submitSingleRequestJobFormForm" enctype="multipart/form-data" method="post">
  <div>
    <label for="mapGridName">Map Grid Name</label>
    <select name="mapGridName">
      <option>NTS 1:1 000 000</option>
      <option>NTS 1:500 000</option>
      <option selected="true">NTS 1:250 000</option>
      <option>NTS 1:125 000</option>
      <option>NTS 1:50 000</option>
      <option>NTS 1:25 000</option>
      <option>BCGS 1:20 000</option>
      <option>BCGS 1:10 000</option>
      <option>BCGS 1:5000</option>
      <option>BCGS 1:2500</option>
      <option>BCGS 1:2000</option>
      <option>BCGS 1:1250</option>
      <option>BCGS 1:1000</option>
      <option>BCGS 1:500</option>
      <option>MTO</option>
    </select>
  </div>
  <div>
    <label for="mapTileId">Map Tile Id</label>
    <input name="mapTileId" type="text" size="70" value="92j">
  </div>
  <div>
    <label for="numBoundaryPoints">Num Boundary Points</label>
    <input name="numBoundaryPoints" type="text" size="10" maxlength="10" value="20" class="short">
  </div>
  <div>
    <label>Result Coordinate System</label>
    <select name="resultSrid">
    <option value="4326">WGS 84</option>
      <option value="4269">NAD83</option>
      <option selected="true" value="3005">NAD83 / BC Albers</option>
      <option value="26907">NAD83 / UTM zone 7N</option>
      <option value="26908">NAD83 / UTM zone 8N</option>
      <option value="26909">NAD83 / UTM zone 9N</option>
      <option value="26910">NAD83 / UTM zone 10N</option>
      <option value="26911">NAD83 / UTM zone 11N</option>
    </select>
  </div>
  <div>
    <label for="resultDataContentType">Result Data Content Type</label>
    <select name="resultDataContentType">
      <option value="text/csv">Comma-Separated Values (text/csv)</option>
      <option value="application/x-shp+zip">Shapefile (ESRI) inside a ZIP archive (application/x-shp+zip)</option>
      <option value="application/vnd.geo+json">GeoJSON (application/vnd.geo+json)</option>
      <option value="application/gml+xml">Geography Markup Language (application/gml+xml)</option>
      <option selected="true" value="application/json">JavaScript Object Notation (application/json)</option>
      <option value="application/vnd.google-earth.kml+xml">KML (application/vnd.google-earth.kml+xml)</option>
      <option value="application/xhtml+xml">XHMTL (application/xhtml+xml)</option>
      <option value="text/html">XHMTL (text/html)</option>
      <option value="text/xml">XML (text/xml)</option>
    </select>
  </div>
</form>

<script type="text/javascript">
$(document).ready(function() {
  $('#submitSingleRequestJobForm').click(function() {
    var client = new CpfClient(cpfServerUrl);
    var form = $('#submitSingleRequestJobFormForm');
    client.submitSingleRequestJobForm(form, 'MapTileByTileId');
  });
});
</script>
</div>
 * @param {form}   form The form containing the parameters to submit to the web service.
 * @param {string} applicationName The name of the business application.
 */
CpfClient.prototype.submitSingleRequestJobForm = function(
    form,
    applicationName) {
  var path = '/apps/' + applicationName + '/single/';
  this.submitIfLoggedIn(form, path);
};

/**
 * 
 */
CpfClient.prototype.submitMultiple = function(
    form,
    applicationName) {
  var path = '/apps/' + applicationName + '/multiple/';
  this.submitIfLoggedIn(form, path);
};

/**
 * <p>Get the job id URLs for all the user's jobs using the
 * <a href="../rest-api/#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getUsersJobs">Get Users Jobs</a> REST API.</p>
 * 
<div class="htmlExample"><button id="getUserJobIdUrls" type="button">Get Users Jobs</button>

<div id="jobIdUrls"></div>

<script type="text/javascript">
$(document).ready(function() {
  $('#getUserJobIdUrls').click(function() {
    var client = new CpfClient(cpfServerUrl);
    client.getUserJobIdUrls(function(jobIdUrls) {
      var div = $('#jobIdUrls');
      client.toHtml(div, jobIdUrls);
    });
  });
});
</script>
</div>
 * @param {function} callback The callback function that will be called with the result object on success.
 */
CpfClient.prototype.getUserJobIdUrls = function(callback) {
  this.getResourcesValues(
    '/jobs',
    'batchJobUrl',
    callback);
};
/**
 * <p>Utility method to set the contents parent HTML element to the HTML representation of
 * the object.</p>
 * 
 * <ul>
 * <li>If the object is a simple data type (string, number, boolean) then the content will
 * be the string representation.</li>
 * <li>If the object is an array then the array will be wrapped in a div,
 * each element in the array will be wrapped in a div and this method called to convert each element
 * in the array to HTML.</li>
 * <li>If the object is a JavaScript object then the object will be converted to
 * a HTML table. The first column will contain the names of the properties. The second column will
 * contain the HTML representation of the property value.</li>
 * </ul>
 * 
 * @param {element} parentElement The HTML element to clear and set to the HTML for the object.
 * @param {object} object The object to convert to HTML.
 */
CpfClient.prototype.toHtml = function(parentElement, object) {
  var client = this;
  parentElement.empty();
  if (!object) {
    parentElement.text('-');
  } else if (jQuery.type(object) == "object") {
    var div = $('<div class="simpleDataTable">').appendTo(parentElement);
    var table = $('<table>').appendTo(div);
    var thead = $('<thead>').appendTo(table);
    var htr = $('<tr>').appendTo(thead);
    $('<th>').text('Property').appendTo(htr);
    $('<th>').text('Description').appendTo(htr);
    var tbody = $('<tbody>').appendTo(table);
    $.each(object, function(key, element) {
      var tr = $('<tr>').appendTo(tbody);
      $('<th>').text(String(key)).appendTo(tr);
      var td = $('<td>').appendTo(tr);
      client.toHtml(td, element);
    });
    table.dataTable({
      "bInfo" : false,
      "bJQueryUI" : true,
      "bPaginate" : false,
      "bSort" : false,
      "bFilter" : false,
      "bAutoWidth": false
    });
  } else if (jQuery.type(object) == "array") {
    var list = $('<div>').appendTo(parentElement);
    $(object).each(function() {
      var listItem = $('<div>').appendTo(list);
      client.toHtml(listItem, this);
    });
  } else {
    var string =String(object);
    if (!string || /^\s*$/.test(string)) {
      string = '-';
    }
    parentElement.text(string);
  }
}


/**
 * @private
 */
CpfClient.prototype.getJson = function(path, data, callback) {
  var url = this.url + path + '?format=json';
  $.ajax({
    url : url,
    dataType : 'jsonp',
    data : data,
    success : function(result) {
      callback(result);
    },
    error : function(xhr, ajaxOptions, thrownError) {
      alert(thrownError);
    }
  });
};

/**
 * @private
 */
CpfClient.prototype.getJsonIfLoggedIn = function(path, data, callback) {
  var self = this;
  this.checkLogin(function() {
    self.getJson(path, data, callback);
  }, function() {
    self.openLoginWindow();
    var cpfLoginCheckInterval = 0;
    cpfLoginCheckInterval = setInterval(function() {
      if (!self.isLoginWindowOpen()) {
        clearInterval(cpfLoginCheckInterval);
        self.checkLogin(function() {
          getJson(path, data, callback);
        });
      }
    }, 1000);
  });
};

/**
 * @private
 */
CpfClient.prototype.getText = function(path, data, callback) {
  var url = this.url + path;
  $.ajax({
    url : url,
    dataType : 'text',
    data : data,
    success : function(result) {
      callback(result);
    },
    error : function(xhr, ajaxOptions, thrownError) {
      alert(thrownError);
    }
  });
};

/**
 * @private
 */
CpfClient.prototype.getTextIfLoggedIn = function(path, data, callback) {
  var self = this;
  this.checkLogin(function() {
    self.getText(path, data, callback);
  }, function() {
    self.openLoginWindow();
    var cpfLoginCheckInterval = 0;
    cpfLoginCheckInterval = setInterval(function() {
      if (!self.isLoginWindowOpen()) {
        clearInterval(cpfLoginCheckInterval);
        self.checkLogin(function() {
          self.getText(path, data, callback);
        });
      }
    }, 1000);
  });
};

/**
 * @private
 */
CpfClient.prototype.getResourcesValues = function(
    path,
    resourceField,
    callback) {
  this.getJsonIfLoggedIn(path, {}, function(result) {
    var resourceValues = new Array();
    var resources = result['resources'];
    if (resources) {
      var len = resources.length;
      for ( var i = 0; i < len; i++) {
        var resource = resources[i];
        var resourceValue = resource[resourceField];
        if (resourceValue) {
          resourceValues.push(resourceValue);
        }
      }
    }
    callback(resourceValues);
  });
};

/**
 * @private
 */
CpfClient.prototype.submitIfLoggedIn = function(form, path) {
  var self = this;
  $(form).attr('action', this.url + path);
  this.checkLogin(function() {
    form.submit();
  }, function() {
    self.openLoginWindow();
    var cpfLoginCheckInterval = 0;
    cpfLoginCheckInterval = setInterval(function() {
      if (!self.isLoginWindowOpen()) {
        clearInterval(cpfLoginCheckInterval);
        self.checkLogin(function() {
          form.submit();
        });
      }
    }, 1000);
  });
};

/**
 * @private
 */
CpfClient.prototype.checkLogin = function(loggedInCallback, notLoggedInCallback) {
  if (!notLoggedInCallback) {
    notLoggedInCallback = function(jqXHR, textStatus, errorThrown) {
      alert('Login failed: Submit the form to login again');
    };
  }
  var url = this.url + '/authenticated.json';
  var request = $.ajax({
    url : url,
    dataType : 'jsonp',
    timeout : 10000
  });
  request.success(function(authenticated) {
    if (authenticated['authenticated'] == true) {
      loggedInCallback();
    } else {
      notLoggedInCallback();
    }
  });
  request.error(notLoggedInCallback);
};

/**
 * @private
 */
CpfClient.prototype.openLoginWindow = function() {
  if (this.isLoginWindowOpen()) {
    this.cpfLoginWindow.focus();
  } else {
    this.cpfLoginWindow = window.open(this.url + '/js/closeWindow', 'cpfLoginWindow');
    var client = this;
    var cpfLoginCheckInterval = setInterval(function() {
      if (!client.isLoginWindowOpen()) {
        clearInterval(cpfLoginCheckInterval);
      }
    }, 1000);
  }
};

/**
 * @private
 */
CpfClient.prototype.isLoginWindowOpen = function() {
  if (!this.cpfLoginWindow || this.cpfLoginWindow.closed) {
    return false;
  } else {
    return true;
  }
};
