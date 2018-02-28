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
package ca.bc.gov.open.cpf.api.web.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.net.URL;
import java.sql.Date;
import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletRequestWrapper;
import javax.servlet.http.HttpServletResponse;

import org.springframework.expression.EvaluationContext;
import org.springframework.http.MediaType;
import org.springframework.http.converter.HttpMessageNotWritableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.expression.ExpressionUtils;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Controller;
import org.springframework.util.StopWatch;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobResult;
import ca.bc.gov.open.cpf.api.domain.BatchJobStatus;
import ca.bc.gov.open.cpf.api.domain.BatchJobStatusChange;
import ca.bc.gov.open.cpf.api.domain.Common;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.api.scheduler.StatisticsService;
import ca.bc.gov.open.cpf.api.web.builder.BatchJobResultUiBuilder;
import ca.bc.gov.open.cpf.api.web.builder.BatchJobUiBuilder;
import ca.bc.gov.open.cpf.api.web.builder.BusinessApplicationUiBuilder;
import ca.bc.gov.open.cpf.api.web.builder.CpfUiBuilder;
import ca.bc.gov.open.cpf.api.web.builder.ErrorsTableSerializer;
import ca.bc.gov.open.cpf.api.web.controller.JobController;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.PluginAdaptor;
import ca.bc.gov.open.cpf.plugin.impl.log.AppLogUtil;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.NamedLinkedHashMapEx;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.identifier.Identifier;
import com.revolsys.io.FileUtil;
import com.revolsys.io.IoConstants;
import com.revolsys.io.IoFactory;
import com.revolsys.io.StringWriter;
import com.revolsys.logging.Logs;
import com.revolsys.record.ArrayRecord;
import com.revolsys.record.Record;
import com.revolsys.record.RecordState;
import com.revolsys.record.Records;
import com.revolsys.record.io.RecordWriter;
import com.revolsys.record.io.RecordWriterFactory;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.record.io.format.tsv.Tsv;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.spring.resource.ByteArrayResource;
import com.revolsys.spring.resource.ClassPathResource;
import com.revolsys.spring.resource.InputStreamResource;
import com.revolsys.spring.resource.OutputStreamResource;
import com.revolsys.transaction.Transaction;
import com.revolsys.ui.html.builder.HtmlUiBuilder;
import com.revolsys.ui.html.decorator.FormGroupDecorator;
import com.revolsys.ui.html.fields.BigDecimalField;
import com.revolsys.ui.html.fields.BigIntegerField;
import com.revolsys.ui.html.fields.Button;
import com.revolsys.ui.html.fields.ByteField;
import com.revolsys.ui.html.fields.CheckBoxField;
import com.revolsys.ui.html.fields.DateField;
import com.revolsys.ui.html.fields.DateTimeField;
import com.revolsys.ui.html.fields.DoubleField;
import com.revolsys.ui.html.fields.EmailAddressField;
import com.revolsys.ui.html.fields.Field;
import com.revolsys.ui.html.fields.FileField;
import com.revolsys.ui.html.fields.FloatField;
import com.revolsys.ui.html.fields.IntegerField;
import com.revolsys.ui.html.fields.LongField;
import com.revolsys.ui.html.fields.NumberField;
import com.revolsys.ui.html.fields.SelectField;
import com.revolsys.ui.html.fields.ShortField;
import com.revolsys.ui.html.fields.TextAreaField;
import com.revolsys.ui.html.fields.TextField;
import com.revolsys.ui.html.fields.TimestampField;
import com.revolsys.ui.html.fields.UrlField;
import com.revolsys.ui.html.form.Form;
import com.revolsys.ui.html.serializer.ElementKeySerializer;
import com.revolsys.ui.html.serializer.KeySerializerTableSerializer;
import com.revolsys.ui.html.serializer.RowsTableSerializer;
import com.revolsys.ui.html.serializer.key.BooleanImageKeySerializer;
import com.revolsys.ui.html.serializer.key.KeySerializer;
import com.revolsys.ui.html.serializer.key.StringKeySerializer;
import com.revolsys.ui.html.view.DivElementContainer;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.ElementContainer;
import com.revolsys.ui.html.view.ErrorWithBackButton;
import com.revolsys.ui.html.view.ListElement;
import com.revolsys.ui.html.view.PanelGroup;
import com.revolsys.ui.html.view.RawContent;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.html.view.TableView;
import com.revolsys.ui.html.view.XmlTagElement;
import com.revolsys.ui.model.PageInfo;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.rest.interceptor.MediaTypeUtil;
import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.ui.web.utils.MultipartFileResource;
import com.revolsys.util.Booleans;
import com.revolsys.util.CaseConverter;
import com.revolsys.util.Dates;
import com.revolsys.util.Exceptions;
import com.revolsys.util.HtmlElem;
import com.revolsys.util.Property;
import com.revolsys.util.Strings;
import com.vividsolutions.jts.geom.Geometry;

/**
 * <p>The Concurrent Processing Framework REST API allows client applications in Java, JavaScript or other
 * programming languages to query the available business applications, create jobs and download
 * the results of jobs on behalf of their users.<p>
 *
 * <p>Most of the resources can return JSON or XML documents by appending the <code>.json</code> or <code>.xml</code>
 * file format extension to the URI Templates before any query string parameters. JSON is the preferred
 * format due to it's well defined map, list and value structure. Some resources that
 * always return a specific data type will ignore the file format extension if specified.</p>
 *
 * <p>The CPF REST API can also be accessed directly using a web browser by not specifying a file format extension.
 * If a file format extension
 * is not included in the request then the HTML user interface will be displayed instead of the resources
 * described in this API. The HTML user interface allows full access to the CPF without any programming
 * experience required. The HTML user interface will be slightly different from the responses
 * described in this API.</p>
 */
@Controller
public class ConcurrentProcessingFramework {
  private static PageInfo addPage(final PageInfo parent, final Object path, final String title) {
    final String url = MediaTypeUtil.getUrlWithExtension(path.toString());
    return parent.addPage(url, title);
  }

  private static PageInfo addPage(final PageInfo parent, final String path, final String title,
    final String... methods) {
    final String url = MediaTypeUtil.getUrlWithExtension(path);
    return parent.addPage(url, title, methods);
  }

  private static void checkPermission(final BusinessApplication businessApplication) {
    final EvaluationContext evaluationContext = CpfUiBuilder.getSecurityEvaluationContext();
    if (!hasPermission(businessApplication, evaluationContext)) {
      throw new AccessDeniedException(
        "No permission for business application " + businessApplication.getName());
    }
  }

  private static boolean hasPermission(final BusinessApplication businessApplication,
    final EvaluationContext evaluationContext) {
    if (ExpressionUtils.evaluateAsBoolean(businessApplication.getBatchModeExpression(),
      evaluationContext)) {
      return true;
    } else if (ExpressionUtils.evaluateAsBoolean(businessApplication.getInstantModeExpression(),
      evaluationContext)) {
      return true;
    } else {
      return false;
    }

  }

  private static PageInfo newRootPageInfo(final String title) {
    HttpServletUtils.setAttribute("title", title);
    final PageInfo page = new PageInfo(title);
    final String url = HttpServletUtils.getFullRequestUrl();
    page.setUrl(url);
    return page;
  }

  private BatchJobService batchJobService;

  @Resource
  private StatisticsService statisticsService;

  private BatchJobUiBuilder batchJobUiBuilder;

  private BatchJobResultUiBuilder batchJobResultUiBuilder;

  private BusinessApplicationUiBuilder businessAppBuilder;

  private CpfDataAccessObject dataAccessObject;

  private Map<String, RawContent> rawContent = new HashMap<>();

  private JobController jobController;

  /**
   * Construct a new ConcurrentProcessingFramework.
   */
  public ConcurrentProcessingFramework() {
  }

  private void addBatchJobStatusLink(final PageInfo page, final Record job) {
    final Identifier batchJobId = job.getIdentifier();
    final String batchJobUrl = HttpServletUtils.getFullUrl("/ws/jobs/" + batchJobId + "/");
    final Timestamp timestamp = job.getValue(Common.WHEN_CREATED);
    final PageInfo childPage = addPage(page, batchJobUrl, "Batch Job " + batchJobId + " Status");

    childPage.setAttribute("batchJobId", batchJobId);
    childPage.setAttribute("batchJobUrl", batchJobUrl);
    childPage.setAttribute("jobStatus", job.getValue(BatchJob.JOB_STATUS));
    childPage.setAttribute("creationTimestamp",
      Dates.format(DateFormat.DEFAULT, DateFormat.SHORT, timestamp));
  }

  private void addField(final Map<String, String> fieldSectionMap, final PanelGroup panelGroup,
    final String fieldName, final Element field, final String labelUrl, final String label,
    final String instructions) {
    String sectionName = fieldSectionMap.get(fieldName);
    if (!Property.hasValue(sectionName)) {
      sectionName = "applicationParameters";
    }

    final FormGroupDecorator decorator = new FormGroupDecorator(labelUrl, label, instructions);
    panelGroup.addElement(sectionName, field, decorator);
  }

  private void addFieldRow(final Map<String, String> fieldSectionMap, final PanelGroup panelGroup,
    final FieldDefinition attribute) {
    final Field field = getField(attribute);
    final String name = attribute.getName();
    final String label = CaseConverter.toCapitalizedWords(name);
    String instructions = attribute.getDescription();
    if (!Property.hasValue(instructions)) {
      instructions = field.getDefaultInstructions();
    }
    final String labelUrl = attribute.getProperty("descriptionUrl");
    addField(fieldSectionMap, panelGroup, name, field, labelUrl, label, instructions);
  }

  private void addFieldRow(final Map<String, String> fieldSectionMap, final PanelGroup panelGroup,
    final RecordDefinitionImpl recordDefinition, final String name) {
    if (recordDefinition.hasField(name)) {
      final FieldDefinition attribute = recordDefinition.getField(name);
      addFieldRow(fieldSectionMap, panelGroup, attribute);
    }
  }

  private void addGeometryFields(final Map<String, String> fieldSectionMap,
    final PanelGroup panelGroup, final BusinessApplication businessApplication) {
    final RecordDefinitionImpl requestRecordDefinition = businessApplication
      .getRequestRecordDefinition();
    addFieldRow(fieldSectionMap, panelGroup, requestRecordDefinition, "srid");
    if (requestRecordDefinition.hasField("resultSrid")) {
      for (final String name : Arrays.asList("resultSrid", "resultNumAxis", "resultScaleFactorXy",
        "resultScaleFactorZ")) {
        addFieldRow(fieldSectionMap, panelGroup, requestRecordDefinition, name);
      }

    }
  }

  private void addInputDataFields(final Map<String, String> fieldSectionMap,
    final PanelGroup panelGroup, final BusinessApplication businessApplication) {
    final Map<String, String> inputDataFileExtensions = businessApplication
      .getInputDataFileExetensions();
    final String defaultInputType = businessApplication.getDefaultInputDataFileExtension();
    final SelectField inputDataContentType = new SelectField("inputDataContentType",
      defaultInputType, true, inputDataFileExtensions);

    addField(fieldSectionMap, panelGroup, "inputDataContentType", inputDataContentType, null,
      "Input Data Content Type",
      "The MIME type of the input data specified by an inputData or inputDataUrl parameter.");

    final UrlField inputDataUrl = new UrlField("inputDataUrl", false);
    addField(fieldSectionMap, panelGroup, "inputDataUrl", inputDataUrl, null, "Input Data URL",
      "The http: URL to the file or resource containing input data. The CPF requires UTF-8 encoding for text files. Shapefiles may use a different encoding if a cpg file is provided.");

    final FileField inputData = new FileField("inputData", false);
    addField(fieldSectionMap, panelGroup, "inputData", inputData, null, "Input Data File",
      "The multi-part file containing the input data. The CPF requires UTF-8 encoding for text files. Shapefiles may use a different encoding if a cpg file is provided.");
  }

  private void addMultiInputDataFields(final Map<String, String> fieldSectionMap,
    final PanelGroup panelGroup, final BusinessApplication businessApplication) {
    final Map<String, String> inputDataContentTypes = businessApplication
      .getInputDataFileExetensions();
    final String defaultInputType = BusinessApplication
      .getDefaultFileExtension(inputDataContentTypes);
    final SelectField inputDataContentType = new SelectField("inputDataContentType",
      defaultInputType, true, inputDataContentTypes);

    if (businessApplication.isPerRequestInputData()) {
      final ElementContainer container = new ElementContainer();
      addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/multiInputDataPre.html");
      container.add(inputDataContentType);
      addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/multiInputDataPost.html");
      addField(fieldSectionMap, panelGroup, "inputData", container, null, "Input Data File",
        "Use the 'Add File' or 'Add URL' buttons to add one or more input data files, then select the MIME type for each file and enter the URL or select the file.");

    } else {
      addInputDataFields(fieldSectionMap, panelGroup, businessApplication);
    }
  }

  private void addNotificationFields(final Map<String, String> fieldSectionMap,
    final PanelGroup panelGroup) {
    final EmailAddressField emailField = new EmailAddressField("notificationEmail", false);
    addField(fieldSectionMap, panelGroup, "notificationEmail", emailField, null,
      "Notification Email",
      "The email address to send the job status to when the job is completed.");

    final UrlField urlField = new UrlField("notificationUrl", false);
    addField(fieldSectionMap, panelGroup, "notificationUrl", urlField, null, "Notification URL",
      "The http: URL to be notified when the job is completed. A copy of the Job status will be posted to process running at this URL.");
  }

  private void addParameter(final List<Map<String, Object>> parameters,
    final FieldDefinition attribute, final boolean perRequestInputData) {
    final String name = attribute.getName();
    final String typeDescription = attribute.getTypeDescription();
    final String description = attribute.getDescription();
    final boolean jobParameter = Booleans
      .getBoolean(attribute.getProperty(BusinessApplication.JOB_PARAMETER));
    final boolean requestParameter = Booleans
      .getBoolean(attribute.getProperty(BusinessApplication.REQUEST_PARAMETER));
    if (jobParameter || requestParameter) {
      final Collection<Object> allowedValues = attribute.getAllowedValues().keySet();
      final String descriptionUrl = attribute.getProperty("descriptionUrl");
      addParameter(parameters, name, typeDescription, descriptionUrl, description, jobParameter,
        requestParameter, perRequestInputData, allowedValues);
    }
  }

  private void addParameter(final List<Map<String, Object>> parameters, final String name,
    final String typeDescription, final String descriptionUrl, final String description,
    final boolean jobParameter, final boolean requestParameter, final boolean perRequestInputData,
    final Collection<Object> allowedValues) {
    final Map<String, Object> parameter = new LinkedHashMap<>();
    parameter.put("name", name);
    parameter.put("type", typeDescription);
    parameter.put("description", description);
    parameter.put("descriptionUrl", descriptionUrl);
    parameter.put("jobParameter", jobParameter);
    if (!perRequestInputData) {
      parameter.put("requestParameter", requestParameter);
    }
    if (allowedValues != null && !allowedValues.isEmpty()) {
      parameter.put("allowedValues", allowedValues);
    }
    parameters.add(parameter);
  }

  private void addRawContent(final ElementContainer container, final String resource) {
    RawContent content = this.rawContent.get(resource);
    if (content == null) {
      synchronized (this.rawContent) {
        content = new RawContent(new ClassPathResource(resource));
        this.rawContent.put(resource, content);
      }
    }
    container.add(content);
  }

  private void addResultDataFields(final Map<String, String> fieldSectionMap,
    final PanelGroup panelGroup, final BusinessApplication businessApplication,
    final String fieldName) {
    final Map<String, String> resultDataFileExtensions = businessApplication
      .getResultDataFileExtensions();
    final String defaultValue = businessApplication.getDefaultResultDataFileExtension();

    final SelectField resultDataContentType = new SelectField(fieldName, defaultValue, true,
      resultDataFileExtensions);
    addField(fieldSectionMap, panelGroup, fieldName, resultDataContentType, null,
      "Result Data Content Type",
      "The MIME type of the result data specified to be returned after running the request");

  }

  private void addSections(final String formName, final BusinessApplication businessApplication,
    final ElementContainer container, final PanelGroup panelGroup) {
    for (final String sectionId : getFormSectionsOpen(businessApplication, formName)) {
      panelGroup.setOpen(sectionId, true);
    }
    container.add(panelGroup);
  }

  private void addTestFields(final Map<String, String> fieldSectionMap, final PanelGroup panelGroup,
    final BusinessApplication businessApplication) {
    if (businessApplication.isTestModeEnabled()) {
      final CheckBoxField cpfPluginTest = new CheckBoxField("cpfPluginTest");

      addField(fieldSectionMap, panelGroup, "cpfPluginTest", cpfPluginTest, null, "Test Mode",
        "Enable test mode for the request.");

      final DoubleField minTime = new DoubleField("cpfMinExecutionTime", false, 0);
      addField(fieldSectionMap, panelGroup, "cpfMinExecutionTime", minTime, null,
        "Min Execution Time (s)", "The minimum execution time.");

      final DoubleField meanTime = new DoubleField("cpfMeanExecutionTime", false, 0);
      addField(fieldSectionMap, panelGroup, "cpfMeanExecutionTime", meanTime, null,
        "Mean Execution Time (s)", "The mean execution time using a gaussian distribution.");

      final DoubleField standardDeviation = new DoubleField("cpfStandardDeviation", false, 2);
      addField(fieldSectionMap, panelGroup, "cpfStandardDeviation", standardDeviation, null,
        "Standard Deviation (s)", "The standard deviation for a gaussian distribution.");

      final DoubleField maxTime = new DoubleField("cpfMaxExecutionTime", false, 10);
      addField(fieldSectionMap, panelGroup, "cpfMaxExecutionTime", maxTime, null,
        "Max Execution Time (s)", "The maximum execution time.");

      if (businessApplication.isHasResultListProperty()) {
        final DoubleField meanNumResults = new DoubleField("cpfMeanNumResults", false, 3);
        addField(fieldSectionMap, panelGroup, "cpfMeanNumResults", meanNumResults, null,
          "Mean Num Results",
          "The mean number of results for each request using a gaussian distribution.");
      }
    }
  }

  @SuppressWarnings({
    "unchecked", "rawtypes"
  })
  public void addTestParameters(final BusinessApplication businessApplication,
    final Map parameters) {
    if (businessApplication.isTestModeEnabled()) {
      final HttpServletRequest request = HttpServletUtils.getRequest();
      for (final Enumeration<String> parameterNames = request.getParameterNames(); parameterNames
        .hasMoreElements();) {
        final String name = parameterNames.nextElement();
        if (name.startsWith("cpf")) {
          Object value = request.getParameter(name);
          if ("on".equals(value)) {
            value = true;
          }
          parameters.put(name, value);
        }
      }
    }
  }

  /**
   * <p>
   * Cancel the user's job. This will mark the job as cancelled and remove all requests
   * and results from the job. The job will be removed after a few days.
   * </p>
   * <p>
   * This service should be invoked after the results from the job are
   * downloaded. If this method is not called the job will automatically
   * be deleted 7 days after the result download was started.
   * </p>
   * <p>
   * This method can also be used to cancel a job before it was finished.
   * If a job was submitted in error or no longer required use this method
   * to cancel the job to help free resources on the system to process
   * other jobs.
   * </p>
   *
   * @param batchJobId The job identifier.
   * @web.response.status 200
   * <p>
   * <b>OK</b>
   * </p>
   * <p>The method always returns the JSON {"cancelled":true}</p>
   */
  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}/cancel"
  }, method = RequestMethod.POST)
  public void cancelJob(@PathVariable("batchJobId") final Long batchJobId) throws IOException {
    final String consumerKey = getConsumerKey();
    final Identifier batchJobIdentifier = Identifier.newIdentifier(batchJobId);
    final Record batchJob = this.batchJobService.getBatchJob(batchJobIdentifier, consumerKey);
    if (batchJob != null) {
      this.batchJobService.cancelBatchJob(batchJobIdentifier);
    }
    final HttpServletResponse response = HttpServletUtils.getResponse();
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(Json.MIME_TYPE);
    try (
      Writer writer = response.getWriter()) {
      writer.write("{\"cancelled\":true}");
    }
  }

  private List<MultipartFile> cleanInputDataFiles(List<MultipartFile> inputDataFiles) {
    if (inputDataFiles == null) {
      inputDataFiles = new ArrayList<>();
    } else {
      for (final Iterator<MultipartFile> iterator = inputDataFiles.iterator(); iterator
        .hasNext();) {
        final MultipartFile multipartFile = iterator.next();
        if (multipartFile.getSize() == 0) {
          iterator.remove();
        }
      }
    }
    return inputDataFiles;
  }

  private List<String> cleanInputDataUrls(List<String> inputDataUrls) {
    if (inputDataUrls == null) {
      inputDataUrls = new ArrayList<>();
    } else {
      for (final Iterator<String> iterator = inputDataUrls.iterator(); iterator.hasNext();) {
        final String inputDataUrl = iterator.next();
        if (!Property.hasValue(inputDataUrl)) {
          iterator.remove();
        }
      }
    }
    return inputDataUrls;
  }

  @PreDestroy
  public void close() {
    this.batchJobService = null;
    this.batchJobUiBuilder = null;
    this.batchJobResultUiBuilder = null;
    this.businessAppBuilder = null;
    this.dataAccessObject = null;
    this.jobController = null;
    this.rawContent = null;
  }

  /**
   * <p>Construct a new new job containing multiple requests to be processed by the
   * business application.</p>
   *
   * <p>The service parameters must be passed
   *  using the multipart/form-data encoding in the body of a HTTP POST request (e.g. a HTML form).</p>
   *
   * <p>In addition to the standard parameters listed in the API each business
   * application has additional job and request parameters. The
   * <a href= "#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsMultiple">Get Business Applications Multiple</a>
   * resource should be consulted to get the full list of supported parameters. </p>
   *
   * <h4>Structured Input Data</h4>
   *
   * <p>For structured input data business applications the requests are specified
   * using either a single inputData file or a single inputDataUrl can be
   * specified. The file must be in the <a href="../../fileFormats.html">file
   * format</a> specified by a single inputDataContentType. The contents of the
   * file must contain one record for each request to be processed in the batch
   * job. The fields of each record must contain the request parameters
   * supported by the business application. The names of the parameters are case
   * sensitive.</p>
   *
   * <h4>Opaque input data</h4>
   *
   * <p>For opaque input data (e.g. JPEG image, ESRI Shapefile) the requests can be
   * specified as one or more inputData files or one or more inputDataUrl
   * parameters. It is not possible to mix inputData and inputDataUrl parameters
   * in the same job. If all the requests have the same content type a
   * single inputDataContentType can be specified. Otherwise an
   * inputDataContentType must be specified for each inputData or inputDataUrl
   * in the same order.</p>
   *
   * <p class="note">NOTE: The maximum size including all parameters and protocol overhead of a
   * multi-part request is 20MB. Therefore inputDataUrl should be used instead
   * of inputData where possible.</p>
   *
   *
   * @param businessApplicationName The name of the business application.
   * @param inputDataContentTypes The MIME type of the input data specified by
   * an inputData or inputDataUrl parameter.
   * @param inputDataFiles The multi-part file containing the input data.
   * @param inputDataUrls The http: URL to the file or resource containing input
   * data.
   * @param srid The coordinate system code of the projection for the input geometry.
   * @param resultDataContentType The MIME type of the result data specified to
   * be returned after running the request.
   * @param resultSrid The coordinate system code of the projection for the
   * result geometry.
   * @param resultNumAxis The number of coordinate axis in the result geometry
   * (e.g. 2 for 2D or 3 for 3D).
   * @param resultScaleFactorXy The scale factor to apply the x, y coordinates.
   * The scale factor is 1 / minimum unit. For example if the minimum unit was
   * 1mm (0.001) the scale factor is 1000 (1 / 0.001).
   * @param resultScaleFactorZ The scale factor to apply the z coordinate. The
   * scale factor is 1 / minimum unit. For example if the minimum unit was 1mm
   * (0.001) the scale factor is 1000 (1 / 0.001).
   * @param notificationUrl The http: URL to be notified when the job is
   * completed. A copy of the Job status will be posted to process running at
   * this URL.
   * @param notificationEmail The email address to send the job status to when
   * the job is completed.
   * @return The resource.
   *
   * @web.response.status 200 <p>In a client application the resource will be returned in the body of the HTTP response in the requested format. See  {@link #getJobsInfo(long)} for the details returned.</p>
   * @web.response.status 302 <p>In a web browser the user will be redirected to the {@link #getJobsInfo(long)} page.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}/multiple"
  }, method = RequestMethod.POST)
  @ResponseBody
  public Object createJobWithMultipleRequests(
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @RequestParam(value = "inputDataContentType",
        required = false) final String[] inputDataContentTypes,
    @RequestParam(value = "inputData", required = false) List<MultipartFile> inputDataFiles,
    @RequestParam(value = "inputDataUrl", required = false) List<String> inputDataUrls,
    @RequestParam(value = "srid", required = false) final String srid, //
    @RequestParam(value = "resultDataContentType",
        required = false) final String resultDataContentType,
    @RequestParam(value = "resultSrid", required = false,
        defaultValue = "3005") final int resultSrid, //
    @RequestParam(value = "resultNumAxis", required = false,
        defaultValue = "2") final int resultNumAxis, //
    @RequestParam(value = "resultScaleFactorXy", required = false,
        defaultValue = "-1") final int resultScaleFactorXy, //
    @RequestParam(value = "resultScaleFactorZ", required = false,
        defaultValue = "-1") final int resultScaleFactorZ, //
    @RequestParam(value = "notificationUrl", required = false) String notificationUrl,
    @RequestParam(value = "notificationEmail", required = false) final String notificationEmail) {
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName,
      "clientMultiple");
    if (businessApplication != null) {
      CpfUiBuilder.checkPermission(businessApplication.getBatchModeExpression(),
        "No batch mode permission for " + businessApplication.getName());

      final List<List<String>> parameterErrors = new ArrayList<>();

      final String consumerKey = getConsumerKey();

      final String defaultInputDataContentType = businessApplication
        .getDefaultInputDataContentType();
      final String defaultResultDataContentType = businessApplication
        .getDefaultResultDataContentType();

      final Map<String, String> businessApplicationParameters = new HashMap<>();
      addTestParameters(businessApplication, businessApplicationParameters);

      final BatchJob batchJob = this.dataAccessObject.newBatchJob();
      final Identifier batchJobId = batchJob.getIdentifier(BatchJob.BATCH_JOB_ID);

      final AppLog log = businessApplication.getLog();
      log.info("Start\tJob submit multiple\tbatchJobId=" + batchJobId);

      batchJob.setValue(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName);
      batchJob.setValue(BatchJob.USER_ID, consumerKey);
      batchJob.setValue(BatchJob.NUM_SUBMITTED_REQUESTS, 0);

      batchJob.setValue(BatchJob.RESULT_DATA_CONTENT_TYPE, defaultResultDataContentType);
      String inputDataContentType = defaultInputDataContentType;
      final List<String> inputContentTypes = new ArrayList<>();
      if (inputDataContentTypes == null) {
      } else {
        inputContentTypes.addAll(Arrays.asList(inputDataContentTypes));
        for (final ListIterator<String> iterator = inputContentTypes.listIterator(); iterator
          .hasNext();) {
          final String inputContentType = iterator.next();
          final String contentType = getInputMediaType(businessApplication, inputContentType);
          if (contentType == null) {
            parameterErrors
              .add(Arrays.asList("inputDataContentType", inputDataContentType, "Invalid value"));
          } else {
            iterator.set(contentType);
          }
        }
        if (!businessApplication.isPerRequestInputData()) {
          if (inputContentTypes.size() == 1) {
            inputDataContentType = inputContentTypes.get(0);
            batchJob.setValue(BatchJob.INPUT_DATA_CONTENT_TYPE, inputDataContentType);
          } else {
            this.dataAccessObject.delete(batchJob);
            parameterErrors.add(Arrays.asList("inputDataContentType",
              Strings.toString(inputContentTypes), "Only one value allowed"));
          }
        }
      }
      if (resultDataContentType != null) {
        final String resultType = businessApplication.getResultContentType(resultDataContentType);
        if (resultType == null) {
          parameterErrors
            .add(Arrays.asList("resultDataContentType", resultDataContentType, "Invalid value"));
        } else {
          batchJob.setValue(BatchJob.RESULT_DATA_CONTENT_TYPE, resultType);
        }
      }

      if (Property.hasValue(notificationEmail)) {
        if (Property.hasValue(notificationUrl)) {
          parameterErrors.add(Arrays.asList("notificationUrl", notificationUrl,
            "Either a notificationUrl or notificationEmail parameter can be specified, but not both"));
          parameterErrors.add(Arrays.asList("notificationEmail", notificationEmail,
            "Either a notificationUrl or notificationEmail parameter can be specified, but not both"));
        } else {
          notificationUrl = "mailto:" + notificationEmail;
        }
      }
      if (Property.hasValue(notificationUrl)) {
        batchJob.setValue(BatchJob.NOTIFICATION_URL, notificationUrl);
      }

      validateJobParameters(parameterErrors, businessApplication, businessApplicationParameters);

      batchJob.setValue(BatchJob.BUSINESS_APPLICATION_PARAMS,
        Json.toString(businessApplicationParameters));

      try {

        inputDataUrls = cleanInputDataUrls(inputDataUrls);
        inputDataFiles = cleanInputDataFiles(inputDataFiles);

        if (inputDataUrls.isEmpty() == inputDataFiles.isEmpty()) {
          final String inputDataMessage = "The inputData file or inputDataUrl parameter must be specified, but not both";
          if (inputDataUrls.isEmpty()) {
            parameterErrors.add(Arrays.asList("inputData", "[binary content]", inputDataMessage));
            parameterErrors.add(
              Arrays.asList("inputDataUrl", Strings.toString(inputDataUrls), inputDataMessage));
          } else {
            parameterErrors.add(Arrays.asList("inputData", "-", inputDataMessage));
            parameterErrors.add(Arrays.asList("inputDataUrl", "-", inputDataMessage));
          }
        } else if (businessApplication.isPerRequestInputData()) {
          if (parameterErrors.isEmpty()) {
            batchJob.setValue(BatchJob.NUM_SUBMITTED_REQUESTS,
              inputDataFiles.size() + inputDataUrls.size());
            this.dataAccessObject.write(batchJob.getRecord());
            final long time = System.currentTimeMillis();
            batchJob.setStatus(this.batchJobService, BatchJobStatus.SUBMITTED, time);
            batchJob.setStatus(this.batchJobService, BatchJobStatus.CREATING_REQUESTS, time + 1);
            batchJob.setStatus(this.batchJobService, BatchJobStatus.PROCESSING, time + 2);
            batchJob.update();
            int requestSequenceNumber = 0;
            if (inputDataUrls.isEmpty()) {
              for (final MultipartFile file : inputDataFiles) {
                try (
                  final InputStream in = file.getInputStream()) {
                  final com.revolsys.spring.resource.Resource resource = new InputStreamResource(
                    "in", in, file.getSize());
                  this.jobController.setGroupInput(batchJobId, ++requestSequenceNumber,
                    inputDataContentType, resource);
                }
              }
            } else {
              for (final String inputDataUrl : inputDataUrls) {
                this.jobController.setGroupInput(batchJobId, ++requestSequenceNumber,
                  inputDataContentType, inputDataUrl.trim());
              }
            }
            this.batchJobService.scheduleJob(batchJob);
          }
        } else {
          createStructuredJob(parameterErrors, batchJobId, batchJob, inputDataFiles, inputDataUrls,
            inputDataContentType);
        }
      } catch (final IOException e) {
        Logs.error(this, "Error creating job: ", e);
        deleteJob(batchJob);
        return newErrorView("Error reading or writing data for job",
          HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
      } catch (final Throwable e) {
        Logs.error(this, "Error creating job: ", e);
        deleteJob(batchJob);
        if (BatchJobService.isDatabaseResourcesException(e)) {
          return newErrorView(
            "The system is at capacity and cannot accept more jobs at this time. Try again in 1 hour.",
            HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        } else {
          return newErrorView("Error creating job", HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        }
      }
      if (parameterErrors.isEmpty()) {
        HttpServletUtils.setPathVariable("batchJobId", batchJobId);

        if (businessApplication.isInfoLogEnabled()) {
          AppLogUtil.info(log, "End\tJob submit multiple\tbatchJobId=" + batchJobId, stopWatch);
        }
        final Map<String, Object> statistics = new HashMap<>();
        statistics.put("submittedJobsTime", stopWatch);
        statistics.put("submittedJobsCount", 1);
        Transaction
          .afterCommit(() -> this.statisticsService.addStatistics(businessApplication, statistics));

        if (MediaTypeUtil.isHtmlPage()) {
          this.batchJobUiBuilder.redirectPage("clientView");
        } else {
          return getJobsInfo(batchJobId.getLong(0));
        }
      } else {
        try {
          if (batchJob.getState() == RecordState.PERSISTED) {
            this.dataAccessObject.delete(batchJob);
          }
        } catch (final Throwable e2) {
          Logs.error(this, "Unable to delete job: " + batchJobId, e2);
        }
        return newErrorView(parameterErrors);
      }
    }
    return null;
  }

  /**
   * <p>Construct a new new job containing multiple requests to be processed by the
   * business application.</p>
   *
   * <p>The job and request parameters for the business application must be passed
   * using the multipart/form-data encoding in the body of a HTTP POST request (e.g. a HTML form).</p>
   *
   * <p>In addition to the standard parameters listed in the API each business
   * application has additional job and request parameters. The
   * <a href= "#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsSingle">Get Business Applications Single</a>
   * resource should be consulted to get the full list of supported parameters. </p>
   *
   * <h4>Structured Input Data</h4>
   *
   * <p>For structured input data business applications the request parameters are specified
   * in the HTTP POST form.</p>
   *
   * <h4>Opaque input data</h4>
   *
   * <p>For opaque input data (e.g. JPEG image, ESRI Shapefile) the requests can be
   * specified as one inputData files or one inputDataUrl
   * parameter. It is not possible to mix inputData and inputDataUrl parameters
   * in the same job.</p>
   *
   * <p class="note">NOTE: The maximum size including all parameters and protocol overhead of a
   * multi-part request is 20MB. Therefore inputDataUrl should be used instead
   * of inputData where possible for opaque data.</p>
   *
   *
   * @param businessApplicationName The name of the business application.
   * @param inputDataContentType The MIME type of the input data specified by
   * an inputData or inputDataUrl parameter.
   * @param inputDataFile The multi-part file containing the input data.
   * @param inputDataUrl The http: URL to the file or resource containing input
   * data.
   * @param srid The coordinate system code of the projection for the input geometry.
   * @param resultDataContentType The MIME type of the result data specified to
   * be returned after running the request.
   * @param resultSrid The coordinate system code of the projection for the
   * result geometry.
   * @param resultNumAxis The number of coordinate axis in the result geometry
   * (e.g. 2 for 2D or 3 for 3D).
   * @param resultScaleFactorXy The scale factor to apply the x, y coordinates.
   * The scale factor is 1 / minimum unit. For example if the minimum unit was
   * 1mm (0.001) the scale factor is 1000 (1 / 0.001).
   * @param resultScaleFactorZ The scale factor to apply the z coordinate. The
   * scale factor is 1 / minimum unit. For example if the minimum unit was 1mm
   * (0.001) the scale factor is 1000 (1 / 0.001).
   * @param notificationUrl The http: URL to be notified when the job is
   * completed. A copy of the Job status will be posted to process running at
   * this URL.
   * @param notificationEmail The email address to send the job status to when
   * the job is completed.
   * @return The resource.
   *
   * @web.response.status 200 <p>In a client application the resource will be returned in the body of the HTTP response in the requested format. See  {@link #getJobsInfo(long)} for the details returned.</p>
   * @web.response.status 302 <p>In a web browser the user will be redirected to the {@link #getJobsInfo(long)} page.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}/single"
  }, method = RequestMethod.POST)
  @ResponseBody
  public Object createJobWithSingleRequest(
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @RequestParam(value = "inputDataContentType", required = false) String inputDataContentType,
    @RequestParam(value = "inputData", required = false) final Object inputDataFile,
    @RequestParam(value = "inputDataUrl", required = false) final String inputDataUrl,
    @RequestParam(value = "srid", required = false) final String srid,
    @RequestParam(value = "resultDataContentType", required = false) String resultDataContentType, //
    @RequestParam(value = "resultSrid", required = false,
        defaultValue = "3005") final int resultSrid, //
    @RequestParam(value = "resultNumAxis", required = false,
        defaultValue = "2") final int resultNumAxis, //
    @RequestParam(value = "resultScaleFactorXy", required = false,
        defaultValue = "-1") final int resultScaleFactorXy, //
    @RequestParam(value = "resultScaleFactorZ", required = false,
        defaultValue = "-1") final int resultScaleFactorZ,
    @RequestParam(value = "notificationUrl", required = false) String notificationUrl,
    @RequestParam(value = "notificationEmail", required = false) final String notificationEmail)
    throws IOException {
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName,
      "clientSingle");
    if (businessApplication != null) {
      CpfUiBuilder.checkPermission(businessApplication.getBatchModeExpression(),
        "No batch mode permission for " + businessApplicationName);

      final List<List<String>> parameterErrors = new ArrayList<>();

      final BatchJob batchJob = this.dataAccessObject.newBatchJob();
      final Identifier batchJobId = batchJob.getIdentifier(BatchJob.BATCH_JOB_ID);
      batchJob.setGroupCount(1);

      final AppLog log = businessApplication.getLog();
      log.info("Start\tJob submit single\tbatchJobId=" + batchJobId);
      final String consumerKey = getConsumerKey();

      final String defaultInputDataContentType = businessApplication
        .getDefaultInputDataContentType();
      final String defaultResultDataContentType = businessApplication
        .getDefaultResultDataContentType();

      final Map<String, String> businessApplicationParameters = new HashMap<>();
      addTestParameters(businessApplication, businessApplicationParameters);

      if (!Property.hasValue(inputDataContentType)) {
        inputDataContentType = defaultInputDataContentType;
      }
      final boolean perRequestInputData = businessApplication.isPerRequestInputData();
      com.revolsys.spring.resource.Resource inputDataIn = null;
      if (perRequestInputData) {
        if (businessApplication.isInputContentTypeSupported(inputDataContentType)
          || businessApplication.isInputContentTypeSupported("*/*")) {
          inputDataIn = getResource("inputData");
          final boolean hasInputDataIn = inputDataIn != null && inputDataIn.contentLength() > 0;
          final boolean hasInputDataUrl = Property.hasValue(inputDataUrl);
          if (hasInputDataIn == hasInputDataUrl) {
            parameterErrors.add(Arrays.asList("inputData", "[binary content]",
              "Either an inputData file or inputDataUrl parameter must be specified, but not both"));
            parameterErrors.add(Arrays.asList("inputDataUrl", inputDataUrl,
              "Either an inputData file or inputDataUrl parameter must be specified, but not both"));
          }
        } else {
          parameterErrors
            .add(Arrays.asList("inputDataContentType", inputDataContentType, "Invalid value"));
        }
      }
      if (!Property.hasValue(resultDataContentType)) {
        resultDataContentType = defaultResultDataContentType;
      }
      final String resultContentType = businessApplication
        .getResultContentType(resultDataContentType);
      if (resultContentType == null) {
        parameterErrors
          .add(Arrays.asList("resultDataContentType", resultDataContentType, "Invalid value"));
      } else {
        resultDataContentType = resultContentType;
      }
      if (Property.hasValue(notificationEmail)) {
        if (Property.hasValue(notificationUrl)) {
          parameterErrors.add(Arrays.asList("notificationUrl", notificationUrl,
            "Either a notificationUrl or notificationEmail parameter can be specified, but not both"));
          parameterErrors.add(Arrays.asList("notificationEmail", notificationEmail,
            "Either a notificationUrl or notificationEmail parameter can be specified, but not both"));
        } else {
          notificationUrl = "mailto:" + notificationEmail;
        }
      }
      final RecordDefinitionImpl requestRecordDefinition = businessApplication
        .getRequestRecordDefinition();
      final Record inputData = new ArrayRecord(requestRecordDefinition);
      for (final FieldDefinition fieldDefinition : requestRecordDefinition.getFields()) {
        final String parameterName = fieldDefinition.getName();
        if (!"resultDataContentType".equals(parameterName)) {
          String value = HttpServletUtils.getParameter(parameterName);
          final boolean required = fieldDefinition.isRequired();
          boolean hasValue = value != null && value.trim().length() > 0;
          if (!hasValue) {
            final Object defaultValue = fieldDefinition.getDefaultValue();
            if (Property.hasValue(defaultValue)) {
              value = fieldDefinition.toString(defaultValue);
              hasValue = true;
            }
          }
          if (fieldDefinition.getDataType() == DataTypes.BOOLEAN) {
            if ("on".equals(value)) {
              value = "true";
            } else {
              value = "false";
            }
            hasValue = true;
          }
          if (hasValue) {
            try {
              fieldDefinition.validate(value);
              if (businessApplication.isJobParameter(parameterName)) {
                businessApplicationParameters.put(parameterName, value);
              } else {
                try {
                  BatchJobService.setStructuredInputDataValue(srid, inputData, fieldDefinition,
                    value, true);
                } catch (final IllegalArgumentException e) {
                  parameterErrors.add(Arrays.asList(parameterName, value, e.getMessage()));
                }
              }
            } catch (final IllegalArgumentException e) {
              parameterErrors.add(Arrays.asList(parameterName, value, e.getMessage()));
            }
          } else if (required) {
            parameterErrors.add(Arrays.asList(parameterName, "-", "Required"));
          }
        }
      }
      if (parameterErrors.isEmpty()) {
        batchJob.setValue(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName);
        batchJob.setValue(BatchJob.USER_ID, consumerKey);

        batchJob.setValue(BatchJob.BUSINESS_APPLICATION_PARAMS,
          Json.toString(businessApplicationParameters));

        if (Property.hasValue(notificationUrl)) {
          batchJob.setValue(BatchJob.NOTIFICATION_URL, notificationUrl);
        }
        batchJob.setValue(BatchJob.RESULT_DATA_CONTENT_TYPE, resultDataContentType);
        batchJob.setValue(BatchJob.NUM_SUBMITTED_REQUESTS, 1);
        this.dataAccessObject.write(batchJob.getRecord());
        final long time = System.currentTimeMillis();
        batchJob.setStatus(this.batchJobService, BatchJobStatus.SUBMITTED, time);
        batchJob.setStatus(this.batchJobService, BatchJobStatus.CREATING_REQUESTS, time + 1);
        batchJob.setStatus(this.batchJobService, BatchJobStatus.PROCESSING, time + 2);
        batchJob.update();
        if (perRequestInputData) {
          if (Property.hasValue(inputDataUrl)) {
            this.jobController.setGroupInput(batchJobId, 1, "url:" + inputDataContentType,
              inputDataUrl);
          } else {
            this.jobController.setGroupInput(batchJobId, 1, inputDataContentType, inputDataIn);
          }
        } else {
          inputData.put(BusinessApplication.SEQUENCE_NUMBER, 1);
          final StringWriter writer = new StringWriter();
          try (
            RecordWriter groupWriter = Tsv.newRecordWriter(requestRecordDefinition, writer, false,
              false)) {
            groupWriter.write(inputData);
          }
          this.jobController.setGroupInput(batchJobId, 1, Tsv.MIME_TYPE, writer);
        }
        batchJob.setGroupCount(1);
        this.batchJobService.scheduleJob(batchJob);

        HttpServletUtils.setPathVariable("batchJobId", batchJobId);

        AppLogUtil.infoAfterCommit(log, "End\tJob submit single\tbatchJobId=" + batchJobId,
          stopWatch);
        final Map<String, Object> statistics = new HashMap<>();
        statistics.put("submittedJobsTime", stopWatch);
        statistics.put("submittedJobsCount", 1);

        statistics.put("preProcessScheduledJobsCount", 1);
        statistics.put("preProcessScheduledJobsTime", 0);

        statistics.put("preProcessedTime", 0);
        statistics.put("preProcessedJobsCount", 1);
        statistics.put("preProcessedRequestsCount", 1);

        Transaction
          .afterCommit(() -> this.statisticsService.addStatistics(businessApplication, statistics));

        if (MediaTypeUtil.isHtmlPage()) {
          this.batchJobUiBuilder.redirectPage("clientView");
        } else {
          return getJobsInfo(batchJobId.getLong(0));
        }
      } else {
        return newErrorView(parameterErrors);
      }
    }
    return null;
  }

  private boolean createStructuredJob(final List<List<String>> parameterErrors,
    final Identifier batchJobId, final BatchJob batchJob, final List<MultipartFile> inputDataFiles,
    final List<String> inputDataUrls, final String contentType) throws IOException {
    if (!inputDataFiles.isEmpty()) {
      if (inputDataFiles.size() == 1) {
        this.dataAccessObject.write(batchJob.getRecord());

        final MultipartFile file = inputDataFiles.get(0);
        final InputStream in = file.getInputStream();
        try {
          final JobController jobController = this.batchJobService.getJobController();
          jobController.newJobInputFile(batchJobId, contentType, in);
        } finally {
          Transaction.afterCommit(() -> FileUtil.closeSilent(in));
        }
      } else {
        parameterErrors.add(Arrays.asList("inputData", "count=" + inputDataFiles.size(),
          "Must only be specified once"));
      }
    } else {
      if (inputDataUrls.size() == 1) {
        final String inputDataUrl = inputDataUrls.get(0);
        batchJob.setValue(BatchJob.STRUCTURED_INPUT_DATA_URL, inputDataUrl.trim());
      } else {
        parameterErrors.add(Arrays.asList("inputDataUrl", Strings.toString(inputDataUrls),
          "Must only be specified once"));
      }
    }
    if (parameterErrors.isEmpty()) {
      this.dataAccessObject.write(batchJob);
      batchJob.update();
      this.batchJobService.preProcess(batchJobId);
      final long time = System.currentTimeMillis();
      batchJob.setStatus(this.batchJobService, BatchJobStatus.SUBMITTED, time);
      batchJob.update();
      return true;
    } else {
      return false;
    }
  }

  private void deleteJob(final BatchJob batchJob) {
    try {
      if (batchJob.getState() == RecordState.PERSISTED) {
        this.dataAccessObject.delete(batchJob);
      }
    } catch (final Throwable e2) {
      Logs.error(this, "Unable to delete job: " + batchJob.getIdentifier(), e2);
    }
  }

  /**
   * <p>
   * Cancel the user's job. This will mark the job as cancelled and remove all requests
   * and results from the job. The job will be removed after a few days.
   * </p>
   * <p>
   * This service should be invoked after the results from the job are
   * downloaded. If this method is not called the job will automatically
   * be deleted 7 days after the result download was started.
   * </p>
   * <p>
   * This method can also be used to cancel a job before it was finished.
   * If a job was submitted in error or no longer required use this method
   * to cancel the job to help free resources on the system to process
   * other jobs.
   * </p>
   *
   * @param batchJobId The job identifier.
   * @web.response.status 200
   * <p>
   * <b>OK</b>
   * </p>
   * <p>
   * If the job was deleted an empty response will be returned.
   * </p>
   * @web.response.status 404
   * <p>
   * <b>Not Found</b>
   * </p>
   * <p>
   * If the job does not exist, has been deleted, or was owned by another
   * user.
   * </p>
   */
  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}"
  }, method = RequestMethod.DELETE)
  public void deleteJob(@PathVariable("batchJobId") final Long batchJobId) throws IOException {
    final String consumerKey = getConsumerKey();
    final Identifier batchJobIdentifier = Identifier.newIdentifier(batchJobId);
    final Record batchJob = this.batchJobService.getBatchJob(batchJobIdentifier, consumerKey);
    if (batchJob != null) {
      this.batchJobService.deleteJob(batchJobIdentifier);
    }
    final HttpServletResponse response = HttpServletUtils.getResponse();
    response.setStatus(HttpServletResponse.SC_OK);
    response.setContentType(Json.MIME_TYPE);
    try (
      Writer writer = response.getWriter()) {
      writer.write("{\"deleted\":true}");
    }
  }

  /**
   * <p>Check that a user is authenticated. This can be used by JavaScript applications to force
   * a login page to be displayed. Returns a map with authenticated=true if the user
   * is authenticated. Response is undefined if the user is not authenticated as the authentication
   * mechanism will not allow access to the resource.</p>
   *
   * @return The authenticated message.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws/authenticated"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Map<String, ? extends Object> getAuthenticated() {
    final MapEx map = new NamedLinkedHashMapEx("Authenticated");
    map.put("authenticated", true);
    return map;
  }

  private List<BusinessApplication> getAuthorizedBusinessApplications() {
    final EvaluationContext evaluationContext = CpfUiBuilder.getSecurityEvaluationContext();

    final List<BusinessApplication> businessApplications = this.batchJobService
      .getBusinessApplications();
    for (final Iterator<BusinessApplication> iterator = businessApplications.iterator(); iterator
      .hasNext();) {
      final BusinessApplication businessApplication = iterator.next();
      if (!hasPermission(businessApplication, evaluationContext)) {
        iterator.remove();
      }
    }
    return businessApplications;
  }

  private BusinessApplication getBusinessApplication(final String businessApplicationName,
    final String pageName) {
    final BusinessApplication businessApplication = this.batchJobService
      .getBusinessApplication(businessApplicationName);
    if (businessApplication == null || !businessApplication.isEnabled()) {
      throw new PageNotFoundException(
        "Business application " + businessApplicationName + " does not exist.");
    } else {
      checkPermission(businessApplication);
      return businessApplication;
    }
  }

  private PageInfo getBusinessApplicationPageInfo(final BusinessApplication businessApplication,
    final String pageName) {
    final String key = pageName + "PageInfo";
    PageInfo page = businessApplication.getProperty(key);
    if (page == null) {
      final String title = businessApplication.getTitle();
      if ("instant".equals(pageName)) {
        page = new PageInfo(title + " Instant", "", "get", "post");
      } else if ("single".equals(pageName)) {
        page = new PageInfo(title + " Create Single Request Job", "", "post");
      } else if ("multiple".equals(pageName)) {
        page = new PageInfo(title + " Create Multi-Request Job", "", "post");
      }

      page.setDescription(businessApplication.getDescription());
      page.addInputContentType(MediaType.MULTIPART_FORM_DATA);

      page.setAttribute("businessApplicationName", businessApplication.getName());
      page.setAttribute("businessApplicationTitle", title);
      page.setAttribute("businessApplicationDescription", businessApplication.getDescription());
      page.setAttribute("businessApplicationDescriptionUrl",
        businessApplication.getDescriptionUrl());

      final Set<String> inputDataContentTypes = businessApplication.getInputDataContentTypes();
      page.setAttribute("inputDataContentTypes", inputDataContentTypes);

      page.setAttribute("perRequestInputData", businessApplication.isPerRequestInputData());

      page.setAttribute("parameters", getRequestAttributeList(businessApplication));

      final Set<String> resultDataContentTypes = businessApplication.getResultDataContentTypes();
      page.setAttribute("resultDataContentTypes", resultDataContentTypes);
      page.setAttribute("perRequestResultData", businessApplication.isPerRequestInputData());

      if (!businessApplication.isPerRequestResultData()) {
        page.setAttribute("resultFields", getResultAttributeList(businessApplication));
      }
      businessApplication.setProperty(key, page);
    }
    return page;
  }

  /**
   * <p>Get the list of links to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsResources">Get Business Applications Resources</a>
   * resource for each business application the user is authorized to access.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document. Each child resource supports following custom attributes.</a>
   *
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Business Application Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>businessApplicationName</td>
   *         <td>The name of the business application.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws/apps"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getBusinessApplications() {
    final List<BusinessApplication> applications = getAuthorizedBusinessApplications();
    HttpServletUtils.setAttribute("title", "Business Applications");
    if (HtmlUiBuilder.isDataTableCallback()) {
      return this.businessAppBuilder.newDataTableMap(applications, "clientList");
    } else if (MediaTypeUtil.isHtmlPage()) {
      final String url = HttpServletUtils.getFullUrl("/ws/#businessApplication_clientList");
      final HttpServletResponse response = HttpServletUtils.getResponse();
      response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      response.setHeader("Location", url);
      return null;
    } else {
      final PageInfo page = newRootPageInfo("Business Appliations");

      for (final BusinessApplication app : applications) {
        if (app.isEnabled()) {
          final String name = app.getName();
          final String title = app.getTitle();
          final PageInfo appPage = addPage(page, name, title);
          appPage.setAttribute("businessApplicationName", name);
        }
      }
      return page;
    }
  }

  /**
   * <p>The instant resource has the two modes described below, the specification mode and the
   * execute instant request mode. If the <code>format</code> parameter is not included or the
   * <code>specification</code> parameter equals <code>true</code> then the specification mode
   * is enabled, otherwise the execute instant request mode is enabled.</p>
   *
   * <h4>Specification</h4>
   *
   * <p>Get the specification of the execute instant request mode of this service.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document with the following additional fields
   * which are the parameters to the execute instant request mode.</a>.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Business Application Instant Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>description</td>
   *         <td>The description of services offered by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationName</td>
   *         <td>The name of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationTitle</td>
   *         <td>The display title of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationDescription</td>
   *         <td>The description of services offered by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationDescriptionUrl</td>
   *         <td>A link to a web page describing more details about the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>parameters</td>
   *         <td><p>The array of parameters that can be passed to the service. Each parameter is an object
   *         containing the following fields.</p>
   *           <div class="table-responsive">
   *             <table class="table table-condensed table-striped tabled-bordered">
   *               <caption>Parameters</caption>
   *               <thead>
   *                 <tr>
   *                   <th>Attribute</th>
   *                   <th>Description</th>
   *                 </tr>
   *               </thead>
   *               <tbody>
   *                 <tr>
   *                   <td>name</td>
   *                   <td>The case sensitive name of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>type</td>
   *                   <td>The <a href="../../dataTypes.html">data type</a> of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>description</td>
   *                   <td>The description of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>descriptionUrl</td>
   *                   <td>The link to a more detailed description of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>jobParameter</td>
   *                   <td>Boolean flag indicating if the parameter can be specified globally on the job.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>requestParameter</td>
   *                   <td>Boolean flag indicating if the parameter can be specified on each request in the job.</td>
   *                 </tr>
   *               </tbody>
   *             </table>
   *           </div>
   *         </td>
   *       </tr>
   *       <tr>
   *         <td>inputDataContentTypes</td>
   *         <td>The array of MIME media types of input data accepted by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>perRequestInputData</td>
   *         <td>Boolean flag indicating that the business application accepts <a href="../../opaqueData.html">opaque data</a> (true) or <a href="../../structuredData.html">structured data</a> (false).</td>
   *       </tr>
   *       <tr>
   *         <td>resultDataContentTypes</td>
   *         <td>The array of MIME media types of result data generated by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>perRequestResultData</td>
   *         <td>Boolean flag indicating that the business application returns <a href="../../opaqueData.html">opaque data</a> (true) or <a href="../../structuredData.html">structured data</a> (false).</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * <h4>Execute Instant Request</h4>
   *
   * <p>Execute a single instant request using the business application with the results
   * returned in the request.</p>
   *
   * <p>The job and request parameters for the business application must be passed
   * using the query string parameters in a HTTP get request or
   * application/form-url-encoded, multipart/form-data encoding in the body of a HTTP POST request (e.g. a HTML form).</p>
   *
   * <p>In addition to the standard parameters listed in the API each business
   * application has additional job and request parameters. Invoke the specification mode of this
   * resource should be consulted to get the full list of supported parameters. </p>

   * <p class="note">NOTE: The instant resource does not support opaque input data.</p>
   *
   * @param businessApplicationName The name of the business application.
   * @param srid The coordinate system code of the projection for the input geometry.
   * @param format The MIME type of the result data specified to
   * be returned after running the request.
   * @param resultSrid The coordinate system code of the projection for the
   * result geometry.
   * @param resultNumAxis The number of coordinate axis in the result geometry
   * (e.g. 2 for 2D or 3 for 3D).
   * @param resultScaleFactorXy The scale factor to apply the x, y coordinates.
   * The scale factor is 1 / minimum unit. For example if the minimum unit was
   * 1mm (0.001) the scale factor is 1000 (1 / 0.001).
   * @param resultScaleFactorZ The scale factor to apply the z coordinate. The
   * scale factor is 1 / minimum unit. For example if the minimum unit was 1mm
   * (0.001) the scale factor is 1000 (1 / 0.001).
   * @return The result.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   * @web.response.status 403 <p>If the user does not have permission for this resource on the business application.</p>
   * @web.response.status 404 <p>If the business application does not exist, or is not enabled.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}/instant",
  }, method = {
    RequestMethod.GET, RequestMethod.POST
  })
  @ResponseBody
  public Object getBusinessApplicationsInstant(
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @RequestParam(value = "specification", defaultValue = "false") final boolean specification, //
    @RequestParam(value = "srid", required = false) final String srid, //
    @RequestParam(value = "format", required = false) final String format, //
    @RequestParam(value = "resultSrid", required = false,
        defaultValue = "3005") final int resultSrid, //
    @RequestParam(value = "resultNumAxis", required = false,
        defaultValue = "2") final int resultNumAxis, //
    @RequestParam(value = "resultScaleFactorXy", required = false,
        defaultValue = "-1") final int resultScaleFactorXy, //
    @RequestParam(value = "resultScaleFactorZ", required = false,
        defaultValue = "-1") final int resultScaleFactorZ) {
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName,
      "clientInstant");
    if (businessApplication == null) {
      return null;
    } else {
      final PluginAdaptor plugin = this.batchJobService
        .getBusinessApplicationPlugin(businessApplication);
      CpfUiBuilder.checkPermission(businessApplication.getInstantModeExpression(),
        "No instant mode permission for " + businessApplication.getName());

      final ElementContainer formElement;
      final boolean isApiCall = HttpServletUtils.isApiCall();
      if (specification) {
        return getBusinessApplicationPageInfo(businessApplication, "instant");
      } else {
        formElement = getFormInstant(businessApplication);
        final Form form = (Form)formElement.getElements().get(0);
        form.initialize(HttpServletUtils.getRequest());
        if (Property.hasValue(format)) {
          final boolean valid = form.isValid();
          if (valid) {
            final RecordDefinitionImpl requestRecordDefinition = businessApplication
              .getRequestRecordDefinition();
            final Record requestParameters = new ArrayRecord(requestRecordDefinition);
            for (final FieldDefinition fieldDefinition : requestRecordDefinition.getFields()) {
              final String name = fieldDefinition.getName();

              String value = HttpServletUtils.getParameter(name);

              boolean hasValue = Property.hasValue(value);
              if (fieldDefinition.getDataType() == DataTypes.BOOLEAN) {
                if ("on".equals(value)) {
                  value = "true";
                } else {
                  value = "false";
                }
                hasValue = true;
              }
              if (!hasValue) {
                if ("resultDataContentType".equals(name)) {
                  value = format;
                  hasValue = Property.hasValue(format);
                } else if (!hasValue) {
                  final Object defaultValue = fieldDefinition.getDefaultValue();
                  if (Property.hasValue(defaultValue)) {
                    value = fieldDefinition.toString(defaultValue);
                    hasValue = true;
                  }
                }
              }
              if (hasValue) {
                fieldDefinition.validate(value);
                try {
                  BatchJobService.setStructuredInputDataValue(srid, requestParameters,
                    fieldDefinition, value, true);
                } catch (final IllegalArgumentException e) {
                  throw new IllegalArgumentException(
                    "Parameter value is not valid " + name + " " + value, e);
                }
              } else if (fieldDefinition.isRequired()) {
                throw new IllegalArgumentException("Parameter is required " + name);
              }
            }
            final Map<String, Object> parameters = new LinkedHashMap<>(requestParameters);
            addTestParameters(businessApplication, parameters);
            plugin.setParameters(parameters);
            plugin.execute();
            final List<Map<String, Object>> list = plugin.getResults();
            HttpServletUtils.setAttribute("contentDispositionFileName", businessApplicationName);
            final RecordDefinition resultRecordDefinition = businessApplication
              .getResultRecordDefinition();
            for (final Entry<String, Object> entry : businessApplication.getProperties()
              .entrySet()) {
              final String name = entry.getKey();
              final Object value = entry.getValue();
              HttpServletUtils.setAttribute(name, value);
            }

            try {
              final HttpServletResponse response = HttpServletUtils.getResponse();

              final RecordWriterFactory writerFactory = IoFactory
                .factoryByMediaType(RecordWriterFactory.class, format);
              if (writerFactory == null) {
                throw new HttpMessageNotWritableException("Unsupported format " + format);
              } else {
                final String contentType = writerFactory.getMediaType(format);
                response.setContentType(contentType);
                final String fileExtension = writerFactory.getFileExtension(format);
                final String fileName = businessApplicationName + "-instant." + fileExtension;
                response.setHeader("Content-Disposition", "attachment; filename=" + fileName);
              }

              final OutputStreamResource resource = new OutputStreamResource("result",
                response.getOutputStream());
              final GeometryFactory geometryFactory = GeometryFactory.fixed(resultSrid,
                resultNumAxis, resultScaleFactorXy, resultScaleFactorXy, resultScaleFactorZ);
              final RecordWriter writer = this.batchJobService.newStructuredResultWriter(resource,
                businessApplication, resultRecordDefinition, format, "Result", geometryFactory);
              final boolean hasMultipleResults = businessApplication
                .getResultListProperty() != null;
              if (!hasMultipleResults) {
                writer.setProperty(IoConstants.SINGLE_OBJECT_PROPERTY, true);
              }
              writer.setWriteNulls(Boolean.TRUE);
              writer.open();
              int i = 1;
              final Map<String, Object> defaultProperties = new HashMap<>(writer.getProperties());

              for (final Map<String, Object> structuredResultMap : list) {
                final Record structuredResult = Records.newRecord(resultRecordDefinition,
                  structuredResultMap);

                @SuppressWarnings("unchecked")
                final Map<String, Object> properties = (Map<String, Object>)structuredResultMap
                  .get("customizationProperties");
                if (Property.hasValue(properties)) {
                  writer.setProperties(properties);
                }

                structuredResult.put("sequenceNumber", 1);
                structuredResult.put("resultNumber", i);
                writer.write(structuredResult);
                if (properties != null && !properties.isEmpty()) {
                  writer.clearProperties();
                  writer.setProperties(defaultProperties);
                }
                i++;

              }
              writer.close();
              return null;
            } catch (final IOException e) {
              return Exceptions.throwUncheckedException(e);
            }
          } else {

          }
        }
        if (isApiCall) {
          return getBusinessApplicationPageInfo(businessApplication, "instant");
        } else {
          final Map<String, Object> titleParameters = new HashMap<>();
          final String title = businessApplication.getTitle();
          titleParameters.put("businessApplicationTitle", title);
          final PageInfo page = newRootPageInfo(title + " Instant");
          setBusinessApplicationDescription(page, businessApplication);

          page.setPagesElement(formElement);

          HttpServletUtils.setAttribute("title", page.getTitle());
          HttpServletUtils.setAttribute("format", "text/html");
          return page;
        }
      }
    }
  }

  /**
   * <p>Get the list of links to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getJobsInfo">Get Jobs Info</a>
   * resource for each of the user's jobs for the business application.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document. Each child resource supports following custom attributes.</a>
   *
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Job Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>batchJobId</td>
   *         <td>The unique identifier of the job.</td>
   *       </tr>
   *       <tr>
   *         <td>batchJobUrl</td>
   *         <td>The URL to the <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getJobsInfo">Get Jobs Info</a> resource without the file format extension.</td>
   *       </tr>
   *       <tr>
   *         <td>jobStatus</td>
   *         <td>The current status of the job.</td>
   *       </tr>
   *       <tr>
   *         <td>creationTimestamp</td>
   *         <td>The time when the job was created.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @param businessApplicationName The name of the business application.
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}/jobs"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getBusinessApplicationsJobs(
    @PathVariable("businessApplicationName") final String businessApplicationName) {
    final BusinessApplication businessApplication = this.batchJobUiBuilder
      .getBusinessApplication(businessApplicationName);
    if (businessApplication == null || !businessApplication.isEnabled()) {
      throw new PageNotFoundException(
        "Business application " + businessApplicationName + " does not exist.");
    } else {
      final String consumerKey = getConsumerKey();
      ConcurrentProcessingFramework.checkPermission(businessApplication);
      if (HtmlUiBuilder.isDataTableCallback()) {
        final Map<String, Object> parameters = new HashMap<>();

        final Map<String, Object> filter = new HashMap<>();
        filter.put(BatchJob.USER_ID, consumerKey);
        filter.put(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName);
        parameters.put("filter", filter);

        return this.batchJobUiBuilder.newDataTableMap("clientAppList", parameters);
      } else if (MediaTypeUtil.isHtmlPage()) {
        this.batchJobUiBuilder.redirectToTab(BusinessApplication.class, "clientView",
          "clientAppList");
        return null;
      } else {
        final String title = businessApplication.getTitle();
        final PageInfo page = newRootPageInfo(title + " Batch Jobs");
        final List<Record> batchJobs = this.dataAccessObject
          .getBatchJobsForUserAndApplication(consumerKey, businessApplicationName);
        for (final Record job : batchJobs) {
          addBatchJobStatusLink(page, job);
        }
        final Object table = this.batchJobUiBuilder.newDataTableHandler("clientList", batchJobs);
        if (table instanceof Element) {
          final Element element = (Element)table;
          page.setPagesElement(element);
        } else {
          return table;
        }
        return page;
      }
    }
  }

  /**
   * <p>Get the specification of the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a> service.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document with the following additional fields
   * which are the parameters to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithMultipleRequests">Create Job With Multiple Requests</a>
   * service.</a>.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Business Application Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>description</td>
   *         <td>The description of services offered by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationName</td>
   *         <td>The name of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationTitle</td>
   *         <td>The display title of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationDescription</td>
   *         <td>The description of services offered by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationDescriptionUrl</td>
   *         <td>A link to a web page describing more details about the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>parameters</td>
   *         <td><p>The array of parameters that can be passed to the service. Each parameter is an object
   *         containing the following fields.</p>
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Parameters</caption>
   *               <thead>
   *                 <tr>
   *                   <th>Attribute</th>
   *                   <th>Description</th>
   *                 </tr>
   *               </thead>
   *               <tbody>
   *                 <tr>
   *                   <td>name</td>
   *                   <td>The case sensitive name of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>type</td>
   *                   <td>The <a href="../../dataTypes.html">data type</a> of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>description</td>
   *                   <td>The description of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>descriptionUrl</td>
   *                   <td>The link to a more detailed description of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>jobParameter</td>
   *                   <td>Boolean flag indicating if the parameter can be specified globally on the job.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>requestParameter</td>
   *                   <td>Boolean flag indicating if the parameter can be specified on each request in the job.</td>
   *                 </tr>
   *               </tbody>
   *             </table>
   *           </div>
   *         </td>
   *       </tr>
   *       <tr>
   *         <td>inputDataContentTypes</td>
   *         <td>The array of MIME media types of input data accepted by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>perRequestInputData</td>
   *         <td>Boolean flag indicating that the business application accepts <a href="../../opaqueData.html">opaque data</a> (true) or <a href="../../structuredData.html">structured data</a> (false).</td>
   *       </tr>
   *       <tr>
   *         <td>resultDataContentTypes</td>
   *         <td>The array of MIME media types of result data generated by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>perRequestResultData</td>
   *         <td>Boolean flag indicating that the business application returns <a href="../../opaqueData.html">opaque data</a> (true) or <a href="../../structuredData.html">structured data</a> (false).</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @param businessApplicationName The name of the business application.
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   * @web.response.status 403 <p>If the user does not have permission for this resource on the business application.</p>
   * @web.response.status 404 <p>If the business application does not exist, or is not enabled.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}/multiple"
  }, method = RequestMethod.GET)
  @ResponseBody
  public PageInfo getBusinessApplicationsMultiple(
    @PathVariable("businessApplicationName") final String businessApplicationName) {
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName,
      "clientMultiple");
    if (businessApplication == null) {
      return null;
    } else {
      CpfUiBuilder.checkPermission(businessApplication.getBatchModeExpression(),
        "No batch mode permission for " + businessApplication.getName());
      final Map<String, Object> titleParameters = new HashMap<>();

      if (MediaTypeUtil.isHtmlPage()) {
        final String title = businessApplication.getTitle();
        titleParameters.put("businessApplicationTitle", title);
        final PageInfo page = newRootPageInfo(title + "Create Multi-Request job");
        setBusinessApplicationDescription(page, businessApplication);
        page.addInputContentType(MediaType.MULTIPART_FORM_DATA);

        page.setPagesElement(getFormMultiple(businessApplication));

        HttpServletUtils.setAttribute("title", page.getTitle());
        return page;
      } else {
        return getBusinessApplicationPageInfo(businessApplication, "multiple");
      }
    }
  }

  /**
   * <p>Get the resources for a business application. The resource contains links to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsInstant">instant</a>,
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsSingle">create single request job</a>, and
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplicationsMultiple">create multi request jobs</a>
   * resources.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document.</p>
   *
   * @param businessApplicationName The name of the business application.
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   * @web.response.status 403 <p>If the user does not have permission for this resource on the business application.</p>
   * @web.response.status 404 <p>If the business application does not exist, or is not enabled.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getBusinessApplicationsResources(
    @PathVariable("businessApplicationName") final String businessApplicationName) {
    final BusinessApplication businessApplication = this.batchJobService
      .getBusinessApplication(businessApplicationName);
    if (businessApplication == null || !businessApplication.isEnabled()) {
      throw new PageNotFoundException(
        "Business application " + businessApplicationName + " does not exist.");
    } else {
      checkPermission(businessApplication);
      final Map<String, Object> titleParameters = new HashMap<>();
      final String title = businessApplication.getTitle();
      titleParameters.put("businessApplicationTitle", title);
      HttpServletUtils.setAttribute("title", title);

      if (MediaTypeUtil.isHtmlPage()) {
        HttpServletUtils.setAttribute("pageHeading", title);
        final ElementContainer container = new ElementContainer();
        final String description = businessApplication.getDescription();
        if (Property.hasValue(description)) {
          container.add(new RawContent("<p>" + description + "</p>"));
        }
        final String descriptionUrl = businessApplication.getDescriptionUrl();
        if (Property.hasValue(descriptionUrl)) {
          container.add(new RawContent("<p>Click <a href=\"" + descriptionUrl
            + "\">here</a> for a more detailed description of the service.</p>"));
        }

        final TabElementContainer tabs = new TabElementContainer();
        container.add(tabs);
        final Element specification = getElementSpecification(businessApplication);
        tabs.add("description", "Overview", specification);

        if (CpfUiBuilder.hasPermission(businessApplication.getInstantModeExpression())) {
          final Element instantForm = getFormInstant(businessApplication);
          tabs.add("instant", "Instant", instantForm);
        }
        if (CpfUiBuilder.hasPermission(businessApplication.getBatchModeExpression())) {
          final Element singleForm = getFormSingle(businessApplication);
          tabs.add("single", "Create Single Request Job", singleForm);

          final Element multipleForm = getFormMultiple(businessApplication);
          tabs.add("multiple", "Create Multi-Request Job", multipleForm);

          final Map<String, Object> parameters = new HashMap<>();
          parameters.put("serverSide", Boolean.TRUE);
          this.batchJobUiBuilder.addTabDataTable(tabs, BatchJob.BATCH_JOB, "clientAppList",
            parameters);
        }
        return container;
      } else {
        final PageInfo page = newRootPageInfo(title);

        if (CpfUiBuilder.hasPermission(businessApplication.getInstantModeExpression())) {
          addPage(page, "instant", "Instant");
        }
        if (CpfUiBuilder.hasPermission(businessApplication.getBatchModeExpression())) {

          addPage(page, "single", "Create Single Request Job", "post");
          addPage(page, "multiple", "Create Multi-Request Job", "post");
        }
        return page;
      }
    }
  }

  /**
   * <p>Get the specification of the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithSingleRequest">Create Job With Single Request</a> service.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document with the following additional fields
   * which are the parameters to the <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.createJobWithSingleRequest">Create Job With Single Request</a> service.</a>.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Business Application Single Request Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>description</td>
   *         <td>The description of services offered by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationName</td>
   *         <td>The name of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationTitle</td>
   *         <td>The display title of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationDescription</td>
   *         <td>The description of services offered by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationDescriptionUrl</td>
   *         <td>A link to a web page describing more details about the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>parameters</td>
   *         <td><p>The array of parameters that can be passed to the service. Each parameter is an object
   *         containing the following fields.</p>
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Patameters</caption>
   *               <thead>
   *                 <tr>
   *                   <th>Attribute</th>
   *                   <th>Description</th>
   *                 </tr>
   *               </thead>
   *               <tbody>
   *                 <tr>
   *                   <td>name</td>
   *                   <td>The case sensitive name of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>type</td>
   *                   <td>The <a href="../../dataTypes.html">data type</a> of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>description</td>
   *                   <td>The description of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>descriptionUrl</td>
   *                   <td>The link to a more detailed description of the parameter.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>jobParameter</td>
   *                   <td>Boolean flag indicating if the parameter can be specified globally on the job.</td>
   *                 </tr>
   *                 <tr>
   *                   <td>requestParameter</td>
   *                   <td>Boolean flag indicating if the parameter can be specified on each request in the job.</td>
   *                 </tr>
   *               </tbody>
   *             </table>
   *           </div>
   *         </td>
   *       </tr>
   *       <tr>
   *         <td>inputDataContentTypes</td>
   *         <td>The array of MIME media types of input data accepted by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>perRequestInputData</td>
   *         <td>Boolean flag indicating that the business application accepts <a href="../../opaqueData.html">opaque data</a> (true) or <a href="../../structuredData.html">structured data</a> (false).</td>
   *       </tr>
   *       <tr>
   *         <td>resultDataContentTypes</td>
   *         <td>The array of MIME media types of result data generated by the business application.</td>
   *       </tr>
   *       <tr>
   *         <td>perRequestResultData</td>
   *         <td>Boolean flag indicating that the business application returns <a href="../../opaqueData.html">opaque data</a> (true) or <a href="../../structuredData.html">structured data</a> (false).</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @param businessApplicationName The name of the business application.
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   * @web.response.status 403 <p>If the user does not have permission for this resource on the business application.</p>
   * @web.response.status 404 <p>If the business application does not exist, or is not enabled.</p>
   */
  @RequestMapping(value = {
    "/ws/apps/{businessApplicationName}/single",
  }, method = RequestMethod.GET)
  @ResponseBody
  public PageInfo getBusinessApplicationsSingle(
    @PathVariable("businessApplicationName") final String businessApplicationName) {
    final BusinessApplication businessApplication = getBusinessApplication(businessApplicationName,
      "clientSingle");
    if (businessApplication == null) {
      return null;
    } else {
      CpfUiBuilder.checkPermission(businessApplication.getBatchModeExpression(),
        "No batch mode permission for " + businessApplication.getName());
      final Map<String, Object> titleParameters = new HashMap<>();

      if (MediaTypeUtil.isHtmlPage()) {
        final String title = businessApplication.getTitle();
        titleParameters.put("businessApplicationTitle", title);

        final PageInfo page = newRootPageInfo(title + "Create Single Request job");
        setBusinessApplicationDescription(page, businessApplication);

        final Element form = getFormSingle(businessApplication);
        page.setPagesElement(form);
        HttpServletUtils.setAttribute("title", page.getTitle());
        return page;
      } else {
        return getBusinessApplicationPageInfo(businessApplication, "single");
      }
    }
  }

  private String getConsumerKey() {
    final SecurityContext securityContext = SecurityContextHolder.getContext();
    final Authentication authentication = securityContext.getAuthentication();
    final String consumerKey = authentication.getName();
    return consumerKey;
  }

  private Element getElementSpecification(final BusinessApplication businessApplication) {
    ElementContainer container = businessApplication.getProperty("specificationElement");
    if (container == null) {
      container = new ElementContainer();
      container.add(new XmlTagElement(HtmlElem.H1,
        businessApplication.getTitle() + " (" + businessApplication.getName() + ")"));

      final String description = businessApplication.getDescription();
      if (Property.hasValue(description)) {
        container.add(new RawContent("<p>" + description + "</p>"));
      }
      final String detailedDescription = businessApplication.getDetailedDescription();
      if (Property.hasValue(detailedDescription)) {
        container.add(new RawContent("<div>" + detailedDescription + "</div>"));
      }
      final String descriptionUrl = businessApplication.getDescriptionUrl();
      if (Property.hasValue(descriptionUrl)) {
        container.add(new RawContent("<p>Click <a href=\"" + descriptionUrl
          + "\">here</a> for a more detailed description of the service.</p>"));
      }

      container.add(
        new RawContent(new ClassPathResource("ca/bc/gov/open/cpf/api/web/service/services.html")));
      if (CpfUiBuilder.hasPermission(businessApplication.getInstantModeExpression())) {
        container.add(new RawContent(
          new ClassPathResource("ca/bc/gov/open/cpf/api/web/service/instantMode.html")));
      }
      if (CpfUiBuilder.hasPermission(businessApplication.getBatchModeExpression())) {
        container.add(new RawContent(
          new ClassPathResource("ca/bc/gov/open/cpf/api/web/service/batchMode.html")));
      }

      addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/inputData.html");
      final Set<String> inputDataContentTypes = businessApplication.getInputDataContentTypes();
      container.add(new ListElement(HtmlElem.UL, HtmlElem.LI, inputDataContentTypes));

      if (businessApplication.isPerRequestInputData()) {
        addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/opaqueInputData.html");
      } else {
        addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/structuredInputData.html");
      }
      final RecordDefinition requestRecordDefinition = businessApplication
        .getRequestRecordDefinition();
      final List<FieldDefinition> requestAttributes = new ArrayList<>(
        requestRecordDefinition.getFields());
      requestAttributes.remove(0);
      final List<KeySerializer> serializers = new ArrayList<>();
      serializers.add(new StringKeySerializer("name"));
      serializers.add(new BooleanImageKeySerializer(
        "properties." + BusinessApplication.JOB_PARAMETER, "Job Parameter"));
      if (!businessApplication.isPerRequestInputData()) {
        serializers.add(new BooleanImageKeySerializer(
          "properties." + BusinessApplication.REQUEST_PARAMETER, "Request Parameter"));
      }
      serializers.add(new StringKeySerializer("typeDescription", "Type"));
      serializers.add(new StringKeySerializer("minValue", "Min"));
      serializers.add(new StringKeySerializer("maxValue", "Max"));
      serializers.add(new StringKeySerializer("defaultValue", "Default"));
      serializers.add(new ElementKeySerializer(HtmlElem.P, "description"));

      final FieldDefinition inputDataContentType = new FieldDefinition("inputDataContentType",
        DataTypes.STRING, false,
        "The MIME type of the input data specified by an inputData or inputDataUrl parameter.");
      inputDataContentType.setProperty(BusinessApplication.JOB_PARAMETER, true);
      inputDataContentType.setDefaultValue(businessApplication.getDefaultInputDataContentType());
      requestAttributes.add(0, inputDataContentType);

      final FieldDefinition inputData = new FieldDefinition("inputData", DataTypes.FILE, false,
        "The multi-part file containing the input data.");
      inputData.setProperty(BusinessApplication.JOB_PARAMETER, true);
      requestAttributes.add(1, inputData);

      final FieldDefinition inputDataUrl = new FieldDefinition("inputDataUrl", DataTypes.STRING,
        false, "The http: URL to the file or resource containing input data.");
      inputDataUrl.setProperty(BusinessApplication.JOB_PARAMETER, true);
      requestAttributes.add(2, inputDataUrl);

      final FieldDefinition notificationEmail = new FieldDefinition("notificationEmail",
        DataTypes.STRING, false,
        "The email address to send the job status to when the job is completed.");
      notificationEmail.setProperty(BusinessApplication.JOB_PARAMETER, true);
      requestAttributes.add(notificationEmail);

      final FieldDefinition notificationUrl = new FieldDefinition("notificationUrl",
        DataTypes.STRING, false,
        "The http: URL to be notified when the job is completed. A copy of the Job status will be posted to process running at this URL.");
      notificationUrl.setProperty(BusinessApplication.JOB_PARAMETER, true);
      requestAttributes.add(notificationUrl);

      final RowsTableSerializer requestModel = new KeySerializerTableSerializer(serializers,
        requestAttributes);
      final TableView requestAttributesTable = new TableView(requestModel);
      container.add(requestAttributesTable);

      addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/resultFiles.html");
      final Set<String> resultDataContentTypes = businessApplication.getResultDataContentTypes();
      container.add(new ListElement(HtmlElem.UL, HtmlElem.LI, resultDataContentTypes));
      if (businessApplication.isPerRequestResultData()) {
        addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/opaqueResults.html");
      } else {
        container.add(new XmlTagElement(HtmlElem.H2, "Result Fields"));
        final RecordDefinition resultRecordDefinition = businessApplication
          .getResultRecordDefinition();
        final List<FieldDefinition> resultAttributes = resultRecordDefinition.getFields();
        final List<KeySerializer> resultSerializers = new ArrayList<>();
        resultSerializers.add(new StringKeySerializer("name"));
        resultSerializers.add(new StringKeySerializer("typeDescription", "Type"));
        resultSerializers.add(new ElementKeySerializer(HtmlElem.P, "description"));
        final RowsTableSerializer resultModel = new KeySerializerTableSerializer(resultSerializers,
          resultAttributes);
        final TableView resultAttributesTable = new TableView(resultModel);
        container.add(resultAttributesTable);
      }
      addRawContent(container, "ca/bc/gov/open/cpf/api/web/service/errorResults.html");

    }
    return container;
  }

  private Field getField(final FieldDefinition attribute) {
    final String name = attribute.getName();
    final boolean required = attribute.isRequired();
    final DataType dataType = attribute.getDataType();
    final Map<Object, Object> allowedValues = attribute.getAllowedValues();
    final Object defaultValue = attribute.getDefaultValue();
    Field field;
    if (allowedValues.isEmpty()) {
      final Class<?> typeClass = dataType.getJavaClass();
      if (dataType.equals(DataTypes.BASE64_BINARY)) {
        field = new FileField(name, required);
      } else if (dataType.equals(DataTypes.LONG)) {
        field = new LongField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.INT)) {
        field = new IntegerField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.SHORT)) {
        field = new ShortField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.BYTE)) {
        field = new ByteField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.FLOAT)) {
        field = new FloatField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.DOUBLE)) {
        field = new DoubleField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.BOOLEAN)) {
        field = new CheckBoxField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.BIG_INTEGER)) {
        field = new BigIntegerField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.DECIMAL)) {
        field = new BigDecimalField(name, required, defaultValue);
      } else if (dataType.equals(DataTypes.ANY_URI)) {
        field = new UrlField(name, required, defaultValue);
      } else if (Date.class.isAssignableFrom(typeClass)) {
        field = new DateField(name, required, defaultValue);
      } else if (Timestamp.class.isAssignableFrom(typeClass)) {
        field = new TimestampField(name, required, defaultValue);
      } else if (java.util.Date.class.isAssignableFrom(typeClass)) {
        field = new DateTimeField(name, required, defaultValue);
      } else if (Geometry.class.isAssignableFrom(dataType.getJavaClass())) {
        field = new TextAreaField(name, 60, 10, required);
      } else if (com.revolsys.geometry.model.Geometry.class
        .isAssignableFrom(dataType.getJavaClass())) {
        field = new TextAreaField(name, 60, 10, required);
      } else if (URL.class.isAssignableFrom(dataType.getJavaClass())) {
        field = new UrlField(name, required, defaultValue);
      } else if (String.class.isAssignableFrom(typeClass)) {
        int length = attribute.getLength();
        if (length == -1) {
          length = 70;
        }
        field = new TextField(name, length, defaultValue, required);
      } else {
        throw new IllegalArgumentException(
          "Values with class " + typeClass.getName() + " are not supported");
      }
    } else {
      field = new SelectField(name, defaultValue, required, allowedValues);
    }
    if (field instanceof NumberField) {
      final NumberField numberField = (NumberField)field;
      final String units = (String)attribute.getProperty("units");
      numberField.setUnits(units);
      final Number minValue = attribute.getMinValue();
      if (minValue != null) {
        numberField.setMinimumValue(minValue);
      }
      final Number maxValue = attribute.getMaxValue();
      if (maxValue != null) {
        numberField.setMaximumValue(maxValue);
      }
    }
    field.setInitialValue(defaultValue);
    return field;
  }

  private ElementContainer getFormInstant(final BusinessApplication businessApplication) {
    final ElementContainer container = new ElementContainer();

    final String url = this.businessAppBuilder.getPageUrl("clientInstant");
    final Form form = new Form("instantForm", url);
    final Map<String, String> fieldSectionMap = getFormSectionsFieldMap(businessApplication,
      "Instant");
    final PanelGroup panelGroup = getFormPanelGroup(businessApplication, "Instant");

    final boolean perRequestInputData = businessApplication.isPerRequestInputData();

    if (perRequestInputData) {
      addInputDataFields(fieldSectionMap, panelGroup, businessApplication);
    }

    final RecordDefinitionImpl requestRecordDefinition = businessApplication
      .getRequestRecordDefinition();
    for (final FieldDefinition fieldDefinition : requestRecordDefinition.getFields()) {
      final String name = fieldDefinition.getName();
      if (!businessApplication.isCoreParameter(name)) {
        if (businessApplication.isJobParameter(name) || !perRequestInputData) {
          addFieldRow(fieldSectionMap, panelGroup, fieldDefinition);
        }
      }
    }
    addGeometryFields(fieldSectionMap, panelGroup, businessApplication);
    addResultDataFields(fieldSectionMap, panelGroup, businessApplication, "format");
    addTestFields(fieldSectionMap, panelGroup, businessApplication);
    addSections("Instant", businessApplication, form, panelGroup);
    form.add(new DivElementContainer("btn-toolbar", Button.submit("instantForm", "Create Job"))
      .setRole("toolbar"));

    container.add(form);
    return container;
  }

  private Element getFormMultiple(final BusinessApplication businessApplication) {
    final RecordDefinitionImpl requestRecordDefinition = businessApplication
      .getRequestRecordDefinition();

    final Map<String, String> fieldSectionMap = getFormSectionsFieldMap(businessApplication,
      "Multiple");
    final PanelGroup panelGroup = getFormPanelGroup(businessApplication, "Multiple");

    final ElementContainer container = new ElementContainer();

    final String url = this.businessAppBuilder.getPageUrl("clientMultiple");
    final Form form = new Form("clientMultiple", url);
    form.setEncType(Form.MULTIPART_FORM_DATA);

    addMultiInputDataFields(fieldSectionMap, panelGroup, businessApplication);

    for (final FieldDefinition fieldDefinition : requestRecordDefinition.getFields()) {
      final String name = fieldDefinition.getName();
      if (!businessApplication.isCoreParameter(name)) {
        if (businessApplication.isJobParameter(name)) {
          addFieldRow(fieldSectionMap, panelGroup, fieldDefinition);
        }
      }
    }

    addGeometryFields(fieldSectionMap, panelGroup, businessApplication);

    addResultDataFields(fieldSectionMap, panelGroup, businessApplication, "resultDataContentType");

    addNotificationFields(fieldSectionMap, panelGroup);

    addTestFields(fieldSectionMap, panelGroup, businessApplication);

    addSections("Multiple", businessApplication, form, panelGroup);
    form.add(new DivElementContainer("btn-toolbar", Button.submit("clientMultiple", "Create Job"))
      .setRole("toolbar"));

    container.add(form);
    return container;
  }

  private PanelGroup getFormPanelGroup(final BusinessApplication businessApplication,
    final String formName) {
    final PanelGroup panelGroup = new PanelGroup(formName + "Fields");
    for (final String sectionName : getFormSectionsNames(businessApplication, formName)) {
      final String sectionTitle = CaseConverter.toCapitalizedWords(sectionName);
      panelGroup.addPanel(sectionName, sectionTitle);
    }
    return panelGroup;
  }

  private Map<String, String> getFormSectionsFieldMap(final BusinessApplication businessApplication,
    final String formName) {

    Map<String, String> formSectionsFieldMap = businessApplication
      .getProperty("formSectionsFieldMap" + formName);
    if (formSectionsFieldMap == null) {
      // Add defaults
      formSectionsFieldMap = new LinkedHashMap<>();

      formSectionsFieldMap.put("inputDataContentType", "inputData");
      formSectionsFieldMap.put("inputDataUrl", "inputData");
      formSectionsFieldMap.put("inputData", "inputData");
      formSectionsFieldMap.put("srid", "inputData");

      formSectionsFieldMap.put("resultSrid", "resultFormat");
      formSectionsFieldMap.put("resultDataContentType", "resultFormat");
      formSectionsFieldMap.put("format", "resultFormat");

      formSectionsFieldMap.put("resultNumAxis", "resultFormatAdvanced");
      formSectionsFieldMap.put("resultScaleFactorXy", "resultFormatAdvanced");
      formSectionsFieldMap.put("resultScaleFactorZ", "resultFormatAdvanced");

      formSectionsFieldMap.put("notificationEmail", "notification");
      formSectionsFieldMap.put("notificationUrl", "notification");

      formSectionsFieldMap.put("cpfPluginTest", "testParameters");
      formSectionsFieldMap.put("cpfMinExecutionTime", "testParameters");
      formSectionsFieldMap.put("cpfMeanExecutionTime", "testParameters");
      formSectionsFieldMap.put("cpfStandardDeviation", "testParameters");
      formSectionsFieldMap.put("cpfMaxExecutionTime", "testParameters");
      formSectionsFieldMap.put("cpfMeanNumResults", "testParameters");

      final Map<String, List<String>> formSectionsMap = getFormSectionsMap(businessApplication,
        formName);
      for (final Entry<String, List<String>> section : formSectionsMap.entrySet()) {
        final String sectionName = section.getKey();
        for (final String fieldName : section.getValue()) {
          formSectionsFieldMap.put(fieldName, sectionName);
        }
      }

      businessApplication.setProperty("formSectionsFieldMap" + formName, formSectionsFieldMap);
    }
    return formSectionsFieldMap;
  }

  private Map<String, List<String>> getFormSectionsMap(
    final BusinessApplication businessApplication, final String formName) {
    Map<String, List<String>> formSectionsMap = businessApplication
      .getProperty("formSectionsMap" + formName);
    if (formSectionsMap == null) {
      formSectionsMap = businessApplication.getProperty("formSectionsMap");
      if (formSectionsMap == null) {
        formSectionsMap = new LinkedHashMap<>();
      }
      businessApplication.setProperty("formSectionsMap" + formName, formSectionsMap);
    }
    return formSectionsMap;
  }

  private List<String> getFormSectionsNames(final BusinessApplication businessApplication,
    final String formName) {
    List<String> sectionNames = businessApplication.getProperty("formSectionsNames" + formName);
    if (sectionNames == null) {
      sectionNames = new ArrayList<>();
      final Map<String, List<String>> formSectionsMap = getFormSectionsMap(businessApplication,
        formName);
      sectionNames.addAll(formSectionsMap.keySet());
      for (final String sectionName : Arrays.asList("inputData", "applicationParameters",
        "resultFormat", "resultFormatAdvanced", "notification", "testParameters")) {
        if (!sectionNames.contains(sectionName)) {
          sectionNames.add(sectionName);
        }
      }
      businessApplication.setProperty("formSectionsNames" + formName, sectionNames);
    }
    return sectionNames;
  }

  private Set<String> getFormSectionsOpen(final BusinessApplication businessApplication,
    final String formName) {
    Set<String> openSections = businessApplication.getProperty("formSectionsOpen" + formName);
    if (openSections == null) {
      openSections = businessApplication.getProperty("formSectionsOpen");
      if (openSections == null) {
        openSections = new HashSet<>(
          Arrays.asList("applicationParameters", "inputData", "resultFormat"));
      }
      businessApplication.setProperty("formSectionsOpen" + formName, openSections);
    }
    return openSections;
  }

  private Element getFormSingle(final BusinessApplication businessApplication) {
    final RecordDefinitionImpl requestRecordDefinition = businessApplication
      .getRequestRecordDefinition();
    final Map<String, String> fieldSectionMap = getFormSectionsFieldMap(businessApplication,
      "Single");
    final PanelGroup panelGroup = getFormPanelGroup(businessApplication, "Single");

    final String url = this.businessAppBuilder.getPageUrl("clientSingle");
    final boolean perRequestInputData = businessApplication.isPerRequestInputData();

    if (perRequestInputData) {
      addInputDataFields(fieldSectionMap, panelGroup, businessApplication);
    }

    for (final FieldDefinition field : requestRecordDefinition.getFields()) {
      final String name = field.getName();
      if (!businessApplication.isCoreParameter(name)) {
        if (businessApplication.isJobParameter(name) || !perRequestInputData) {
          addFieldRow(fieldSectionMap, panelGroup, field);
        }
      }
    }
    addGeometryFields(fieldSectionMap, panelGroup, businessApplication);
    addResultDataFields(fieldSectionMap, panelGroup, businessApplication, "resultDataContentType");

    addNotificationFields(fieldSectionMap, panelGroup);

    addTestFields(fieldSectionMap, panelGroup, businessApplication);

    final Form form = new Form("createSingle", url);
    if (perRequestInputData) {
      form.setEncType(Form.MULTIPART_FORM_DATA);
    }
    addSections("Single", businessApplication, form, panelGroup);

    form.add(new DivElementContainer("btn-toolbar", Button.submit("createSingle", "Create Job"))
      .setRole("toolbar"));

    final ElementContainer container = new ElementContainer(form);
    return container;
  }

  public String getInputMediaType(final BusinessApplication application, String inputContentType) {
    if (Property.hasValue(inputContentType)) {
      if (!inputContentType.contains("/")) {
        inputContentType = application.getInputContentType(inputContentType);
        if (inputContentType == null) {
          return null;
        }
      }
      final MediaType mediaType = MediaType.valueOf(inputContentType);
      final Set<String> inputDataContentTypes = application.getInputDataContentTypes();
      for (final String contentType : inputDataContentTypes) {
        if (MediaType.valueOf(contentType).includes(mediaType)) {
          return contentType;
        }
      }
    }
    return null;
  }

  /**
   * <p>Get the list of links to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getJobsInfo">Get Jobs Info</a>
   * resource for each of the user's jobs.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document. Each child resource supports following custom attributes.</a>
   *
    * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Job Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>batchJobId</td>
   *         <td>The unique identifier of the job.</td>
   *       </tr>
   *       <tr>
   *         <td>batchJobUrl</td>
   *         <td>The URL to the <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getJobsInfo">Get Jobs Info</a> resource without the file format extension.</td>
   *       </tr>
   *       <tr>
   *         <td>jobStatus</td>
   *         <td>The current status of the job.</td>
   *       </tr>
   *       <tr>
   *         <td>creationTimestamp</td>
   *         <td>The time when the job was created.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws/jobs"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getJobs() {
    final String consumerKey = getConsumerKey();
    if (HtmlUiBuilder.isDataTableCallback()) {
      final Map<String, Object> parameters = new HashMap<>();

      final Map<String, Object> filter = new HashMap<>();
      filter.put(BatchJob.USER_ID, consumerKey);
      parameters.put("filter", filter);

      return this.batchJobUiBuilder.newDataTableMap("clientList", parameters);
    } else if (MediaTypeUtil.isHtmlPage()) {
      HttpServletUtils.setAttribute("title", "Batch Jobs");
      final String url = HttpServletUtils.getFullUrl("/ws/#batchJob_clientList");
      final HttpServletResponse response = HttpServletUtils.getResponse();
      response.setStatus(HttpServletResponse.SC_SEE_OTHER);
      response.setHeader("Location", url);
      return null;
    } else {
      final PageInfo page = newRootPageInfo("Batch Jobs");
      final List<Record> batchJobs = this.dataAccessObject.getBatchJobsForUser(consumerKey);
      for (final Record job : batchJobs) {
        addBatchJobStatusLink(page, job);
      }
      return page;
    }
  }

  /**
   * <p>Get the details of a job.</p>
   *
   *
   * <p>The method returns a BatchJob object with the following attributes.</a>
   *
   * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Job Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>id</td>
   *         <td>The unique identifier of the job.</td>
   *       </tr>
   *       <tr>
   *         <td>businessApplicationName</td>
   *         <td>The name of the business application.</td>
   *       </tr>
   *       <tr>
   *         <td><b>&lt;parameter&gt;</b></td>
   *         <td>the job parameters.</td>
   *       </tr>
   *       <tr>
   *         <td>resultSrid</td>
   *         <td>The coordinate system code of the projection for the result geometry.</td>
   *       </tr>
   *       <tr>
   *         <td>resultNumAxis</td>
   *         <td>The number of coordinate axis in the result geometry (e.g. 2 for 2D or 3 for 3D).</td>
   *       </tr>
   *       <tr>
   *         <td>resultScaleFactorXy</td>
   *         <td>The scale factor to apply the x, y coordinates. The scale factor is 1 / minimum unit. For example if the minimum unit was 1mm (0.001) the scale factor is 1000 (1 / 0.001).</td>
   *       </tr>
   *       <tr>
   *         <td>resultScaleFactorZ</td>
   *         <td>The scale factor to apply the z coordinate. The scale factor is 1 / minimum unit. For example if the minimum unit was 1mm (0.001) the scale factor is 1000 (1 / 0.001).</td>
   *       </tr>
   *       <tr>
   *         <td>resultDataContentType</td>
   *         <td>The MIME type of the result data specified to be returned after running the request.</td>
   *       </tr>
   *       <tr>
   *         <td>jobStatus</td>
   *         <td>The current status of the job.</td>
   *       </tr>
   *       <tr>
   *         <td>secondsToWaitForStatusCheck</td>
   *         <td>The number of seconds to wait before checking the status again</td>
   *       </tr>
   *       <tr>
   *         <td>numSubmittedRequests</td>
   *         <td>The number of requests submitted.</td>
   *       </tr>
   *       <tr>
   *         <td>numCompletedRequests</td>
   *         <td>The number of requests that completed execution successfully.</td>
   *       </tr>
   *       <tr>
   *         <td>numFailedRequests</td>
   *         <td>The number of requests that failed to execute.</td>
   *       </tr>
   *       <tr>
   *         <td>resultsUrl</td>
   *         <td></td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @param batchJobId The unique identifier of the job.
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getJobsInfo(@PathVariable("batchJobId") final Long batchJobId) {
    final String consumerKey = getConsumerKey();
    final BatchJob batchJob = this.batchJobService.getBatchJob(Identifier.newIdentifier(batchJobId),
      consumerKey);
    if (batchJob == null) {
      throw new PageNotFoundException("Batch Job " + batchJobId + " does not exist.");
    } else {
      if (MediaTypeUtil.isHtmlPage()) {
        HttpServletUtils.setAttribute("title", "Batch Job " + batchJobId);
        final TabElementContainer tabs = new TabElementContainer();
        this.batchJobUiBuilder.addObjectViewPage(tabs, batchJob, "client");
        final String jobStatus = batchJob.getValue(BatchJob.JOB_STATUS);
        if (BatchJobStatus.RESULTS_CREATED.equals(jobStatus)
          || BatchJobStatus.DOWNLOAD_INITIATED.equals(jobStatus)) {
          final Map<String, Object> parameters = Collections.emptyMap();
          this.batchJobUiBuilder.addTabDataTable(tabs, BatchJobResult.BATCH_JOB_RESULT,
            "clientList", parameters);
          tabs.setSelectedIndex(1);
        }
        {
          final Map<String, Object> parameters = new LinkedHashMap<>();
          final List<List<Object>> sorting = Collections
            .singletonList(Arrays.<Object> asList(1, "asc"));
          parameters.put("order", sorting);

          this.batchJobUiBuilder.addTabDataTable(tabs, BatchJobStatusChange.BATCH_JOB_STATUS_CHANGE,
            "clientList", parameters);
        }
        return tabs;
      } else {
        final String url = this.batchJobUiBuilder.getPageUrl("clientView");
        final Map<String, Object> batchJobMap = this.batchJobService.toMap(batchJob, url,
          this.batchJobUiBuilder.getTimeUntilNextCheck(batchJob));
        return batchJobMap;
      }
    }
  }

  /**
   * <p>Get the contents of a user's job result file. The content type will be the content type
   * requested in the job.</p>
   *
   * @param batchJobId The unique identifier of the job.
   * @param resultId The unique identifier of the result file.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response.</p>
   */
  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}/results/{resultId}"
  }, method = {
    RequestMethod.GET, RequestMethod.POST
  })
  @ResponseBody
  public void getJobsResult(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("batchJobId") final Long batchJobId, @PathVariable("resultId") final int resultId)
    throws IOException {
    final String consumerKey = getConsumerKey();
    final Identifier batchJobIdentifier = Identifier.newIdentifier(batchJobId);
    final BatchJob batchJob = this.batchJobService.getBatchJob(batchJobIdentifier, consumerKey);

    if (batchJob != null) {
      final Record batchJobResult = this.dataAccessObject.getBatchJobResult(batchJobIdentifier,
        resultId);
      if (batchJobResult != null && DataType.equal(batchJobIdentifier,
        batchJobResult.getValue(BatchJobResult.BATCH_JOB_ID))) {
        batchJob.setStatus(this.batchJobService, BatchJobStatus.DOWNLOAD_INITIATED);
        batchJob.update();
        this.batchJobService.downloadBatchJobResult(request, response, batchJobIdentifier, resultId,
          batchJobResult);
        return;
      }
    }
    throw new PageNotFoundException("Batch Job result " + resultId + " does not exist.");
  }

  /**
   * <p>Get the list of links to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getrsJobsResult">Get Jobs Result</a>
   * resource for each of the results for a user's job.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document. Each child resource supports following custom attributes.</a>
   *
    * <div class="table-responsive">
   *   <table class="table table-condensed table-striped tabled-bordered">
   *     <caption>Result Fields</caption>
   *     <thead>
   *       <tr>
   *         <th>Attribute</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>batchJobResultType</td>
   *         <td>The type of result file structuredResultData, opaqueResultData, errorResultData.</td>
   *       </tr>
   *       <tr>
   *         <td>batchJobResultContentType</td>
   *         <td>The MIME type of the result file.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * @param batchJobIdentifier The unique identifier of the job.
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws/jobs/{batchJobId}/results"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getJobsResults(@PathVariable("batchJobId") final Long batchJobId) {
    final String consumerKey = getConsumerKey();
    final Identifier batchJobIdentifier = Identifier.newIdentifier(batchJobId);

    final Record batchJob = this.batchJobService.getBatchJob(batchJobIdentifier, consumerKey);
    if (batchJob == null) {
      throw new PageNotFoundException("Batch Job " + batchJobIdentifier + " does not exist.");
    } else {
      final String title = "Batch Job " + batchJobIdentifier + " results";
      if (HtmlUiBuilder.isDataTableCallback()) {
        final Map<String, Object> parameters = new HashMap<>();

        final Map<String, Object> filter = new HashMap<>();
        filter.put(BatchJobResult.BATCH_JOB_ID, batchJobIdentifier);
        parameters.put("filter", filter);

        return this.batchJobResultUiBuilder.newDataTableMap("clientList", parameters);
      } else if (MediaTypeUtil.isHtmlPage()) {
        HttpServletUtils.setAttribute("title", title);
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("serverSide", false);
        final TabElementContainer tabs = new TabElementContainer();
        this.batchJobResultUiBuilder.addTabDataTable(tabs, BatchJob.BATCH_JOB, "clientList",
          parameters);
        return tabs;
      } else {
        final Map<String, Object> parameters = new HashMap<>();
        parameters.put("batchJobId", batchJobIdentifier);
        final PageInfo page = newRootPageInfo(title);
        final List<Record> results = this.dataAccessObject.getBatchJobResults(batchJobIdentifier);
        if (batchJob.getValue(BatchJob.COMPLETED_TIMESTAMP) != null && !results.isEmpty()) {
          for (final Record batchJobResult : results) {
            final Number sequenceNumber = batchJobResult.getInteger(BatchJobResult.SEQUENCE_NUMBER);
            parameters.put("sequenceNumber", sequenceNumber);
            final PageInfo resultPage = addPage(page, sequenceNumber,
              "Batch Job " + batchJobIdentifier + " result " + sequenceNumber);
            final String batchJobResultType = batchJobResult
              .getValue(BatchJobResult.BATCH_JOB_RESULT_TYPE);
            resultPage.setAttribute("batchJobResultType", batchJobResultType);
            resultPage.setAttribute("batchJobResultContentType",
              batchJobResult.getValue(BatchJobResult.RESULT_DATA_CONTENT_TYPE));
            resultPage.setAttribute("expiryDate", this.batchJobService
              .getExpiryDate((java.util.Date)batchJobResult.getValue(Common.WHEN_CREATED)));
            if (batchJobResultType.equals(BatchJobResult.OPAQUE_RESULT_DATA)) {
              resultPage.setAttribute("batchJobExecutionGroupSequenceNumber",
                batchJobResult.getValue(BatchJobResult.SEQUENCE_NUMBER));
            }
          }
        }
        return page;
      }
    }
  }

  private List<Map<String, Object>> getRequestAttributeList(
    final BusinessApplication businessApplication) {
    final List<Map<String, Object>> parameters = new ArrayList<>();
    final boolean perRequestInputData = businessApplication.isPerRequestInputData();

    addParameter(parameters, "inputDataContentType", "string", null,
      "The MIME type of the input data specified by an inputData or inputDataUrl parameter.", true,
      false, perRequestInputData, Collections.emptyList());

    addParameter(parameters, "inputData", "file", null,
      "The multi-part file containing the input data.", true, false, perRequestInputData,
      Collections.emptyList());

    addParameter(parameters, "inputDataUrl", "string", null,
      "The http: URL to the file or resource containing input data.", true, false,
      perRequestInputData, Collections.emptyList());

    final RecordDefinition requestRecordDefinition = businessApplication
      .getRequestRecordDefinition();
    for (final FieldDefinition attribute : requestRecordDefinition.getFields()) {
      addParameter(parameters, attribute, perRequestInputData);
    }

    addParameter(parameters, "notificationEmail", "string", null,
      "The email address to send the job status to when the job is completed.", true, false,
      perRequestInputData, Collections.emptyList());

    addParameter(parameters, "notificationUrl", "string", null,
      "The http: URL to be notified when the job is completed. A copy of the Job status will be posted to process running at this URL.",
      true, false, perRequestInputData, Collections.emptyList());

    return parameters;
  }

  private com.revolsys.spring.resource.Resource getResource(final String fieldName) {
    HttpServletRequest request = HttpServletUtils.getRequest();
    while (request instanceof HttpServletRequestWrapper) {
      if (request instanceof MultipartHttpServletRequest) {
        final MultipartHttpServletRequest multiPartRequest = (MultipartHttpServletRequest)request;
        final MultipartFile file = multiPartRequest.getFile(fieldName);
        if (file != null) {
          return new MultipartFileResource(file);
        }
      }
      request = (HttpServletRequest)((HttpServletRequestWrapper)request).getRequest();
    }
    final String value = HttpServletUtils.getParameter(fieldName);
    if (Property.hasValue(value)) {
      return new ByteArrayResource(fieldName, value);
    } else {
      return null;
    }
  }

  private List<Map<String, Object>> getResultAttributeList(
    final BusinessApplication businessApplication) {
    final List<Map<String, Object>> resultAttributes = new ArrayList<>();

    final RecordDefinition resultRecordDefinition = businessApplication.getResultRecordDefinition();
    for (final FieldDefinition attribute : resultRecordDefinition.getFields()) {
      final String name = attribute.getName();
      final String typeDescription = attribute.getTypeDescription();
      final String description = attribute.getDescription();

      final Map<String, Object> resultAttribute = new LinkedHashMap<>();
      resultAttribute.put("name", name);
      resultAttribute.put("type", typeDescription);
      resultAttribute.put("description", description);
      resultAttribute.put("descriptionUrl", attribute.getProperty("descriptionUrl"));
      resultAttributes.add(resultAttribute);
    }
    return resultAttributes;
  }

  /**
   * <p>Get the root resource of the CPF web services. The resource contains links to the
   * <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getJobs">Get Jobs</a>
   * and <a href="#ca.bc.gov.open.cpf.api.web.rest.ConcurrentProcessingFramework.getBusinessApplications">Get Business Applications</a>
   * resources.</p>
   *
   * <p>The method returns a <a href="../../resourceDescription.html">Resource Description</a> document.</a>
   *
   * @return The resource.
   *
   * @web.response.status 200 <p>The resource will be returned in the body of the HTTP response in the requested format.</p>
   */
  @RequestMapping(value = {
    "/ws"
  }, method = RequestMethod.GET)
  @ResponseBody
  public Object getRoot() {
    if (MediaTypeUtil.isHtmlPage()) {
      HttpServletUtils.setAttribute("title", "Concurrent Processing Framework");

      final TabElementContainer tabs = new TabElementContainer();

      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.TRUE);
      this.businessAppBuilder.addTabDataTable(tabs, BatchJob.BATCH_JOB, "clientList", parameters);

      parameters.put("serverSide", Boolean.FALSE);
      this.businessAppBuilder.addTabDataTable(tabs, BusinessApplication.class, "clientList",
        parameters);

      return tabs;
    } else {
      final PageInfo page = newRootPageInfo("Concurrent Processing Framework");
      addPage(page, "jobs", "Jobs");
      addPage(page, "apps", "Business Applications");
      return page;
    }
  }

  private Object newErrorView(final List<List<String>> parameterErrors) {
    if (MediaTypeUtil.isHtmlPage()) {
      final TableView table = new TableView(new ErrorsTableSerializer(parameterErrors));

      return new ElementContainer(//
        new XmlTagElement(HtmlElem.H1, "Form Errors"), //
        new ErrorWithBackButton(
          "The form contained errors for the following fields. Click the back button to return to the form"), //
        table//
      );
    } else {
      HttpServletUtils.getResponse().setStatus(HttpServletResponse.SC_BAD_REQUEST);
      return new LinkedHashMapEx("errors", parameterErrors);
    }
  }

  private Object newErrorView(final String message, final int statusCode) {
    HttpServletUtils.getResponse().setStatus(statusCode);
    if (MediaTypeUtil.isHtmlPage()) {
      return new XmlTagElement(HtmlElem.H1, "Error");
    } else {
      return new LinkedHashMapEx("error", message);
    }
  }

  public void setBatchJobResultUiBuilder(final BatchJobResultUiBuilder batchJobResultUiBuilder) {
    this.batchJobResultUiBuilder = batchJobResultUiBuilder;
  }

  public void setBatchJobService(final BatchJobService batchJobService) {
    this.batchJobService = batchJobService;
    this.dataAccessObject = batchJobService.getDataAccessObject();
    this.jobController = batchJobService.getJobController();
  }

  public void setBatchJobUiBuilder(final BatchJobUiBuilder batchJobUiBuilder) {
    this.batchJobUiBuilder = batchJobUiBuilder;
  }

  public void setBusinessAppBuilder(final BusinessApplicationUiBuilder businessAppBuilder) {
    this.businessAppBuilder = businessAppBuilder;
  }

  private void setBusinessApplicationDescription(final PageInfo page,
    final BusinessApplication businessApplication) {
    String description = businessApplication.getDescription();
    String descriptionUrl = businessApplication.getDescriptionUrl();
    if (Property.hasValue(descriptionUrl)) {
      descriptionUrl = "<p>Click <a href=\"" + descriptionUrl
        + "\">here</a> for a more detailed description of the service.</p>";
    }
    if (Property.hasValue(description)) {
      description = "<p>" + description + "</p>";
      if (Property.hasValue(descriptionUrl)) {
        description += descriptionUrl;
      }
    } else {
      description = descriptionUrl;
    }
    page.setHtmlDescription(description);
    page.setAttribute("businessApplicationName", businessApplication.getName());
  }

  private void validateJobParameters(final List<List<String>> parameterErrors,
    final BusinessApplication businessApplication,
    final Map<String, String> businessApplicationParameters) {
    final RecordDefinitionImpl requestRecordDefinition = businessApplication
      .getRequestRecordDefinition();
    for (final FieldDefinition fieldDefinition : requestRecordDefinition.getFields()) {
      final String parameterName = fieldDefinition.getName();
      String value = HttpServletUtils.getParameter(parameterName);
      final boolean jobParameter = businessApplication.isJobParameter(parameterName);
      final boolean requestParameter = businessApplication.isRequestParameter(parameterName);
      boolean hasValue = Property.hasValue(value);
      if (fieldDefinition.getDataType() == DataTypes.BOOLEAN) {
        if ("on".equals(value)) {
          value = "true";
        } else {
          value = "false";
        }
        hasValue = true;
      }
      if (!hasValue && jobParameter) {
        final Object defaultValue = fieldDefinition.getDefaultValue();
        if (Property.hasValue(defaultValue)) {
          value = fieldDefinition.toString(defaultValue);
          hasValue = true;
        }
      }
      if (hasValue) {
        if (jobParameter) {
          businessApplicationParameters.put(parameterName, value);
        } else if (requestParameter) {
          if (fieldDefinition.getDataType() != DataTypes.BOOLEAN
            || Property.hasValue(HttpServletUtils.getParameter(parameterName))) {
            parameterErrors.add(Arrays.asList(parameterName, value,
              "Cannot be specified on a job. It can only be specified as a field in the input data."));
          }
        }
      } else {
        if (jobParameter && !requestParameter && fieldDefinition.isRequired()) {
          parameterErrors.add(Arrays.asList(parameterName, value, "Required"));
        }
      }
    }
  }

}
