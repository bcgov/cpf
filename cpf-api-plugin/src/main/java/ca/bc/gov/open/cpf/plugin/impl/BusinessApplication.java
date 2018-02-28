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
package ca.bc.gov.open.cpf.plugin.impl;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;

import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import org.springframework.expression.Expression;
import org.springframework.expression.spel.standard.SpelExpressionParser;

import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;
import ca.bc.gov.open.cpf.plugin.api.RequestParameter;
import ca.bc.gov.open.cpf.plugin.api.ResultAttribute;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

import com.revolsys.collection.CollectionUtil;
import com.revolsys.collection.map.Maps;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.cs.CoordinateSystem;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.io.PathName;
import com.revolsys.properties.BaseObjectWithProperties;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.util.Booleans;
import com.revolsys.util.CaseConverter;
import com.revolsys.util.Property;

/**
 * The BusinessApplication describes a business application which can be invoked
 * via the CPF. It contains all the metadata to generate a capabilities document
 * for the service and for the CPF to be able to invoke the application.
 *
 * @author paustin
 * @version 1.0
 */
public class BusinessApplication extends BaseObjectWithProperties
  implements Comparable<BusinessApplication> {

  public static final String CORE_PARAMETER = BusinessApplication.class.getName()
    + "/CORE_PARAMETER";

  public static final String JOB_PARAMETER = BusinessApplication.class.getName() + "/JOB_PARAMETER";

  public static final String REQUEST_PARAMETER = BusinessApplication.class.getName()
    + "/REQUEST_PARAMETER";

  private static final Object[] NO_ARGS = new Object[0];

  public static final String SEQUENCE_NUMBER = "\u039D";

  public static String getDefaultFileExtension(final Map<String, ?> fileExtensionMap) {
    final Collection<String> fileExtensions = fileExtensionMap.keySet();
    String defaultValue = "csv";
    if (!fileExtensions.contains(defaultValue)) {
      if (fileExtensions.isEmpty()) {
        defaultValue = "*";
      } else {
        defaultValue = CollectionUtil.get(fileExtensions, 0);
      }
    }
    return defaultValue;
  }

  public static String getDefaultMimeType(final Collection<String> mimeTypes) {
    String defaultValue = "application/json";
    if (!mimeTypes.contains(defaultValue)) {
      if (mimeTypes.isEmpty()) {
        defaultValue = "*/*";
      } else {
        defaultValue = CollectionUtil.get(mimeTypes, 0);
      }
    }
    return defaultValue;
  }

  private String detailedDescription;

  private final List<String> resultFieldNames = new ArrayList<>();

  private String packageName;

  private String defaultResultDataContentType;

  private Expression batchModeExpression;

  private String batchModePermission;

  private List<CoordinateSystem> coordinateSystems;

  private String description;

  /**
   * The descriptionUrl is a link to a URL which provides more detailed
   * instructions on using the business application.
   */
  private String descriptionUrl;

  private GeometryFactory geometryFactory = GeometryFactory.DEFAULT_3D;

  private boolean hasCustomizationProperties;

  private boolean hasGeometryRequestAttribute = false;

  private boolean hasGeometryResultAttribute = false;

  private boolean hasNonGeometryRequestAttribute;

  private boolean hasResultListCustomizationProperties;

  /**
   * The id field is the unique identifier for the BusinessApplication.
   */
  private String id = UUID.randomUUID().toString();

  /**
   * The inputDataContentTypes is the list of supported MIME content types the
   * BusinessApplication can accept for input data.
   */
  private final Set<String> inputDataContentTypes = new LinkedHashSet<>();

  private Map<String, String> inputDataFileExtensions = new LinkedHashMap<>();

  private final Map<String, String> inputFileExtensionToContentType = new LinkedHashMap<>();

  private Expression instantModeExpression;

  private String instantModePermission;

  private AppLog log;

  private int maxConcurrentRequests;

  /**
   * The maxRequestsPerJob is the maximum number of requests a user can submit
   * in one job to the BusinessApplication.
   */
  private int maxRequestsPerJob = Integer.MAX_VALUE;

  private Module module;

  /**
   * The name is the name of the BusinessApplication.
   */
  private String name;

  private int numRequestsPerWorker = 1;

  /**
   * The perRequestInputData flag indicates if the input data must be specified
   * as opaque data for each BatchJobExecutionGroup (true) or in one structured data
   * file for the whole Batch Job (false).
   */
  private boolean perRequestInputData;

  /**
   * The perRequestResultData flag indicates if the result data will be returned
   * as one opaque data file for each BatchJobExecutionGroup (true) or in one
   * structured data file for the whole Batch Job (false).
   */
  private boolean perRequestResultData;

  private BusinessApplicationPlugin pluginAnnotation;

  private final Map<String, FieldDefinition> requestFieldByNameMap = new TreeMap<>();

  private final Map<Integer, FieldDefinition> requestFieldMap = new TreeMap<>();

  private boolean requestFieldMapInitialized = false;

  private RecordDefinitionImpl requestRecordDefinition;

  private RecordDefinitionImpl internalRequestRecordDefinition;

  private final Map<Integer, FieldDefinition> resultFieldMap = new TreeMap<>();

  /**
   * The resultDataContentTypes is the list of supported MIME content types the
   * BusinessApplication can accept for result data.
   */
  private final Set<String> resultDataContentTypes = new LinkedHashSet<>();

  private final Map<String, String> resultDataFileExtensions = new LinkedHashMap<>();

  private final Map<String, String> resultFileExtensionToContentType = new LinkedHashMap<>();

  private String resultListProperty;

  private RecordDefinitionImpl resultRecordDefinition;

  private boolean securityServiceRequired;

  private boolean testModeEnabled = false;

  /**
   * The name is the name of the BusinessApplication.
   */
  private String title;

  private boolean validateGeometry;

  private String defaultInputDataContentType;

  private String defaultInputDataFileExtension;

  private String defaultResultDataFileExtension;

  private Method executeMethod;

  private Method testExecuteMethod;

  private final Map<String, Method> requestFieldMethodMap = new HashMap<>();

  private final Map<String, Method> resultFieldMethodMap = new HashMap<>();

  public BusinessApplication(final BusinessApplicationPlugin pluginAnnotation, final Module module,
    final String name) {
    this.name = name;
    this.title = name;
    this.pluginAnnotation = pluginAnnotation;
    this.module = module;
    this.name = name;
    this.log = new AppLog(module.getName() + "." + name);
    this.requestRecordDefinition = new RecordDefinitionImpl(PathName.newPathName("/" + name));
    this.resultRecordDefinition = new RecordDefinitionImpl(PathName.newPathName("/" + name));
    this.internalRequestRecordDefinition = new RecordDefinitionImpl(
      PathName.newPathName("/" + name));
  }

  public BusinessApplication(final String name) {
    this.name = name;
    this.title = name;
  }

  private void addFieldRequestSrid() {
    final FieldDefinition requestSrid = new FieldDefinition("srid", DataTypes.INT, false,
      "The coordinate system code of the source geometry. This value is used if the input data file does not specify a coordinate system.");
    requestSrid.setProperty(BusinessApplication.CORE_PARAMETER, true);
    requestSrid.setProperty(BusinessApplication.JOB_PARAMETER, true);
    Integer firstSrid = null;
    Integer defaultValue = Property.getInteger(this, "srid");
    for (final CoordinateSystem coordinateSystem : this.coordinateSystems) {
      final int srid = coordinateSystem.getCoordinateSystemId();
      if (firstSrid == null || srid == 3005) {
        firstSrid = srid;
      }
      final String name = coordinateSystem.getCoordinateSystemName();
      requestSrid.addAllowedValue(srid, srid + " - " + name);
    }
    if (defaultValue == null) {
      defaultValue = firstSrid;
    }
    requestSrid.setDefaultValue(defaultValue);
    requestSrid.setMinValue(0);
    this.requestRecordDefinition.addField(requestSrid);
  }

  private void addFieldResultDataContentType() {
    final FieldDefinition resultDataContentType = new FieldDefinition("resultDataContentType",
      DataTypes.STRING, false,
      "The MIME type of the result data specified to be returned after running the request.");
    resultDataContentType.setProperty(BusinessApplication.CORE_PARAMETER, true);
    resultDataContentType.setProperty(BusinessApplication.JOB_PARAMETER, true);

    if (this.defaultResultDataContentType == null) {
      this.defaultResultDataFileExtension = getDefaultFileExtension(
        this.resultFileExtensionToContentType);
      this.defaultResultDataContentType = getDefaultMimeType(this.resultDataContentTypes);
    }
    resultDataContentType.setDefaultValue(this.defaultResultDataFileExtension);
    this.requestRecordDefinition.addField(resultDataContentType);
  }

  private void addFieldResultNumAxis() {
    final FieldDefinition resultNumAxis = new FieldDefinition("resultNumAxis", DataTypes.INT, false,
      "The number of coordinate axis in the result geometry (e.g. 2 for 2D or 3 for 3D).");
    resultNumAxis.setProperty(BusinessApplication.CORE_PARAMETER, true);
    resultNumAxis.setProperty(BusinessApplication.JOB_PARAMETER, true);
    resultNumAxis.addAllowedValue(2, "2D");
    resultNumAxis.addAllowedValue(3, "3D");
    Integer defaultValue = Property.getInteger(this, "resultNumAxis", 2);
    if (defaultValue < 2) {
      defaultValue = 2;
    } else if (defaultValue > 3) {
      defaultValue = 3;
    }
    resultNumAxis.setDefaultValue(defaultValue);
    resultNumAxis.setMinValue(2);
    resultNumAxis.setMaxValue(3);
    this.requestRecordDefinition.addField(resultNumAxis);
  }

  private void addFieldResultSrid() {
    final FieldDefinition resultSrid = new FieldDefinition("resultSrid", DataTypes.INT, false,
      "The coordinate system code of the projection for the result geometry.");
    resultSrid.setProperty(BusinessApplication.CORE_PARAMETER, true);
    resultSrid.setProperty(BusinessApplication.JOB_PARAMETER, true);
    Integer firstSrid = null;
    Integer defaultValue = Property.getInteger(this, "resultSrid");
    for (final CoordinateSystem coordinateSystem : this.coordinateSystems) {
      final int srid = coordinateSystem.getCoordinateSystemId();
      if (firstSrid == null || srid == 3005) {
        firstSrid = 3005;
      }
      final String name = coordinateSystem.getCoordinateSystemName();
      resultSrid.addAllowedValue(srid, srid + " - " + name);
    }
    if (defaultValue == null) {
      defaultValue = firstSrid;
    }
    resultSrid.setDefaultValue(defaultValue);
    resultSrid.setMinValue(0);
    this.requestRecordDefinition.addField(resultSrid);
  }

  private void addFieldScaleFactorXy() {
    final FieldDefinition resultScaleFactorXy = new FieldDefinition("resultScaleFactorXy",
      DataTypes.DOUBLE, false,
      "The scale factor to apply the x, y coordinates. The scale factor is 1 / minimum unit. For example if the minimum unit was 1mm (0.001) the scale factor is 1000 (1 / 0.001).");
    resultScaleFactorXy.setProperty(BusinessApplication.CORE_PARAMETER, true);
    resultScaleFactorXy.setProperty(BusinessApplication.JOB_PARAMETER, true);

    double defaultValue = Property.getDouble(this, "resultScaleFactorXy", 1000);
    if (defaultValue < 0) {
      defaultValue = 1000;
    }
    resultScaleFactorXy.setDefaultValue(defaultValue);

    this.requestRecordDefinition.addField(resultScaleFactorXy);
  }

  private void addFieldScaleFactorZ() {
    final FieldDefinition resultScaleFactorZ = new FieldDefinition("resultScaleFactorZ",
      DataTypes.DOUBLE, false,
      "The scale factor to apply the z coordinate. The scale factor is 1 / minimum unit. For example if the minimum unit was 1mm (0.001) the scale factor is 1000 (1 / 0.001).");
    resultScaleFactorZ.setProperty(BusinessApplication.CORE_PARAMETER, true);
    double defaultValue = Property.getDouble(this, "resultScaleFactorZ", 1000);
    if (defaultValue < 0) {
      defaultValue = 1000;
    }
    resultScaleFactorZ.setDefaultValue(defaultValue);
    resultScaleFactorZ.setProperty(BusinessApplication.JOB_PARAMETER, true);
    this.requestRecordDefinition.addField(resultScaleFactorZ);
  }

  public void addInputDataContentType(final String contentType) {
    this.inputDataContentTypes.add(contentType);
  }

  public void addInputDataContentType(final String contentType, final String description,
    final String fileExtension) {
    final String inputDataContentType = Property.getString(this, "inputDataContentType");
    final String inputDataFileExtension = Property.getString(this, "inputDataFileExtension");
    if (isContentTypeOrFileExtensionEqual(inputDataContentType, contentType, fileExtension)
      || isContentTypeOrFileExtensionEqual(inputDataFileExtension, contentType, fileExtension)) {
      this.defaultInputDataContentType = contentType;
      this.defaultInputDataFileExtension = fileExtension;
    }

    this.inputDataContentTypes.add(contentType);

    this.inputDataFileExtensions.put(fileExtension, description);
    this.inputDataFileExtensions = Maps.sortByValues(this.inputDataFileExtensions);
    this.inputFileExtensionToContentType.put(fileExtension, contentType);
  }

  public void addRequestField(int index, final FieldDefinition field, final Method method) {
    if (field == null) {
      throw new RuntimeException("Unknown field");
    }
    if (index == -1) {
      index = 200000 + this.requestFieldMap.size();
    }
    final String fieldName = field.getName();
    if (this.requestFieldMap.containsKey(index)) {
      throw new IllegalArgumentException("Business Application " + getName()
        + " Duplicate index for " + RequestParameter.class + " on " + fieldName);
    } else {
      final Class<?> fieldClass = field.getTypeClass();
      if (Geometry.class.isAssignableFrom(fieldClass)
        || com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(fieldClass)) {
        this.hasGeometryRequestAttribute = true;
      } else {
        this.hasNonGeometryRequestAttribute = true;
      }
      this.requestFieldMap.put(index, field);
    }
    this.requestFieldByNameMap.put(fieldName, field);
    this.requestFieldMethodMap.put(fieldName, method);
  }

  public void addResultDataContentType(final String contentType) {
    this.resultDataContentTypes.add(contentType);
  }

  public void addResultDataContentType(final String contentType, final String fileExtension,
    final String description) {
    final String resultDataContentType = Property.getString(this, "resultDataContentType");
    final String resultDataFileExtension = Property.getString(this, "resultDataFileExtension");
    if (isContentTypeOrFileExtensionEqual(resultDataContentType, contentType, fileExtension)
      || isContentTypeOrFileExtensionEqual(resultDataFileExtension, contentType, fileExtension)) {
      this.defaultResultDataContentType = contentType;
      this.defaultResultDataFileExtension = fileExtension;
    }

    this.resultDataContentTypes.add(contentType);
    this.resultDataFileExtensions.put(fileExtension, description);
    this.resultFileExtensionToContentType.put(fileExtension, contentType);
  }

  public void addResultField(int index, final FieldDefinition field, final Method method) {
    if (index == -1) {
      index = 100000 + this.resultFieldMap.size();
    }
    final String fieldName = field.getName();
    if (this.resultFieldMap.containsKey(index)) {
      throw new IllegalArgumentException("Business Application " + getName()
        + " Duplicate index for " + ResultAttribute.class + " on " + fieldName);
    } else {
      final Class<?> fieldClass = field.getTypeClass();
      if (Geometry.class.isAssignableFrom(fieldClass)
        || com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(fieldClass)) {
        this.hasGeometryResultAttribute = true;
      }
      this.resultFieldMap.put(index, field);
      this.resultFieldMethodMap.put(fieldName, method);
    }
  }

  /**
   * Compare the business applications, return in alphabetical order (ignoring
   * case), followed by versions with the lowest version number first.
   *
   * @param businessApplication
   */
  @Override
  public int compareTo(final BusinessApplication businessApplication) {
    if (businessApplication == this) {
      return 0;
    } else {
      final int nameCompare = getName().compareToIgnoreCase(businessApplication.getName());
      if (nameCompare == 0) {
        return getId().compareTo(getId());
      } else {
        return nameCompare;
      }
    }
  }

  public Expression getBatchModeExpression() {
    return this.batchModeExpression;
  }

  public String getBatchModePermission() {
    return this.batchModePermission;
  }

  public List<CoordinateSystem> getCoordinateSystems() {
    return this.coordinateSystems;
  }

  public String getDefaultInputDataContentType() {
    return this.defaultInputDataContentType;
  }

  public String getDefaultInputDataFileExtension() {
    return this.defaultInputDataFileExtension;
  }

  public String getDefaultResultDataContentType() {
    return this.defaultResultDataContentType;
  }

  public String getDefaultResultDataFileExtension() {
    return this.defaultResultDataFileExtension;
  }

  public String getDescription() {
    return this.description;
  }

  public String getDescriptionUrl() {
    return this.descriptionUrl;
  }

  public String getDetailedDescription() {
    return this.detailedDescription;
  }

  public GeometryFactory getGeometryFactory() {
    return this.geometryFactory;
  }

  public String getId() {
    return this.id;
  }

  public String getInputContentType(final String fileExtension) {
    if (isInputContentTypeSupported(fileExtension)) {
      return fileExtension;
    } else {
      return this.inputFileExtensionToContentType.get(fileExtension);
    }
  }

  public Set<String> getInputDataContentTypes() {
    return this.inputDataContentTypes;
  }

  public Map<String, String> getInputDataFileExetensions() {
    return this.inputDataFileExtensions;
  }

  public Map<String, String> getInputFileExtensionToContentType() {
    return this.inputFileExtensionToContentType;
  }

  public Map<String, String> getInputFileExtensionToMediaType() {
    return this.inputFileExtensionToContentType;
  }

  public Expression getInstantModeExpression() {
    return this.instantModeExpression;
  }

  public String getInstantModePermission() {
    return this.instantModePermission;
  }

  public synchronized RecordDefinitionImpl getInternalRequestRecordDefinition() {
    if (this.internalRequestRecordDefinition.getFieldCount() == 0) {
      if (isRequestFieldMapInitialized()) {
        final FieldDefinition requestSequenceNumber = this.internalRequestRecordDefinition
          .addField(SEQUENCE_NUMBER, DataTypes.INT);
        requestSequenceNumber.setProperty(BusinessApplication.CORE_PARAMETER, true);
        requestSequenceNumber.setMinValue(1);

        for (final FieldDefinition field : this.requestFieldMap.values()) {
          if (Booleans.getBoolean(field.getProperty(BusinessApplication.REQUEST_PARAMETER))) {
            this.internalRequestRecordDefinition.addField(field.clone());
          }
        }
      }
    }

    return this.internalRequestRecordDefinition;
  }

  public AppLog getLog() {
    return this.log;
  }

  public String getLogLevel() {
    return this.log.getLogLevel();
  }

  public int getMaxConcurrentRequests() {
    return this.maxConcurrentRequests;
  }

  public int getMaxRequestsPerJob() {
    return this.maxRequestsPerJob;
  }

  public Module getModule() {
    return this.module;
  }

  public String getModuleName() {
    return this.module.getName();
  }

  public String getName() {
    return this.name;
  }

  public int getNumRequestsPerWorker() {
    return this.numRequestsPerWorker;
  }

  public String getPackageName() {
    return this.packageName;
  }

  public BusinessApplicationPlugin getPluginAnnotation() {
    return this.pluginAnnotation;
  }

  public synchronized RecordDefinitionImpl getRequestRecordDefinition() {
    if (this.requestRecordDefinition.getFieldCount() == 0) {
      if (isRequestFieldMapInitialized()) {
        final FieldDefinition requestSequenceNumber = this.requestRecordDefinition
          .addField(SEQUENCE_NUMBER, DataTypes.INT);
        requestSequenceNumber.setProperty(BusinessApplication.CORE_PARAMETER, true);
        requestSequenceNumber.setMinValue(1);

        if (this.defaultInputDataContentType == null) {
          this.defaultInputDataFileExtension = getDefaultFileExtension(
            this.inputFileExtensionToContentType);
          this.defaultInputDataContentType = getDefaultMimeType(this.inputDataContentTypes);
        }

        if (this.hasGeometryRequestAttribute) {
          addFieldRequestSrid();
        }

        addFieldResultDataContentType();

        if (this.hasGeometryResultAttribute) {
          addFieldResultSrid();
          addFieldResultNumAxis();
          addFieldScaleFactorXy();
          addFieldScaleFactorZ();
        }
        for (final FieldDefinition fieldDefinition : this.requestFieldMap.values()) {
          this.requestRecordDefinition.addField(fieldDefinition.clone());
        }
      }
    }
    return this.requestRecordDefinition;
  }

  public String getResultContentType(final String fileExtension) {
    if (isResultContentTypeSupported(fileExtension)) {
      return fileExtension;
    } else {
      return this.resultFileExtensionToContentType.get(fileExtension);
    }
  }

  public Set<String> getResultDataContentTypes() {
    return this.resultDataContentTypes;
  }

  public Map<String, String> getResultDataFileExtensions() {
    return this.resultDataFileExtensions;
  }

  public List<String> getResultFieldNames() {
    return this.resultFieldNames;
  }

  public String getResultListProperty() {
    return this.resultListProperty;
  }

  public synchronized RecordDefinition getResultRecordDefinition() {
    if (this.resultRecordDefinition.getFieldCount() == 0) {
      if (this.resultFieldMap.size() > 0) {
        this.resultRecordDefinition.addField(new FieldDefinition("sequenceNumber", DataTypes.INT,
          true, "The index of the request record that this result relates to."));
        if (this.resultListProperty != null) {
          this.resultRecordDefinition.addField(new FieldDefinition("resultNumber", DataTypes.INT,
            true, "The index of the result record within the result for a request."));
        }
        for (final FieldDefinition fieldDefinition : this.resultFieldMap.values()) {
          final String name = fieldDefinition.getName();
          final FieldDefinition requestAttribute = this.requestFieldByNameMap.get(name);
          if (requestAttribute != null) {
            String description = fieldDefinition.getDescription();
            if (!Property.hasValue(description)) {
              description = requestAttribute.getDescription();
              fieldDefinition.setDescription(description);
            }
            Object defaultValue = fieldDefinition.getDefaultValue();
            if (defaultValue == null) {
              defaultValue = requestAttribute.getDefaultValue();
              fieldDefinition.setDefaultValue(defaultValue);
            }
          }

          this.resultRecordDefinition.addField(fieldDefinition);
        }
        this.resultFieldNames.addAll(this.resultRecordDefinition.getFieldNames());
        if (isHasCustomizationProperties() || isHasResultListCustomizationProperties()) {
          this.resultFieldNames.add("customizationProperties");
        }
      }
    }
    return this.resultRecordDefinition;
  }

  public String getTitle() {
    if (this.title == null && this.name != null) {
      this.title = CaseConverter.toCapitalizedWords(this.name);
    }
    return this.title;
  }

  private boolean isContentTypeOrFileExtensionEqual(final String match, final String contentType,
    final String fileExtension) {
    if (Property.hasValue(match)) {
      return DataType.equal(match, contentType) || DataType.equal(match, fileExtension);
    } else {
      return false;
    }
  }

  public boolean isCoreParameter(final String fieldName) {
    final FieldDefinition attribute = this.requestRecordDefinition.getField(fieldName);
    if (attribute == null) {
      throw new IllegalArgumentException("Parameter does not exist" + fieldName);
    } else {
      return Booleans.getBoolean(attribute.getProperty(CORE_PARAMETER));
    }
  }

  public boolean isEnabled() {
    return this.module.isEnabled();
  }

  public boolean isHasCustomizationProperties() {
    return this.hasCustomizationProperties;
  }

  public boolean isHasGeometryRequestAttribute() {
    return this.hasGeometryRequestAttribute;
  }

  public boolean isHasGeometryResultAttribute() {
    return this.hasGeometryResultAttribute;
  }

  public boolean isHasNonGeometryRequestAttribute() {
    return this.hasNonGeometryRequestAttribute;
  }

  public boolean isHasResultListCustomizationProperties() {
    return this.hasResultListCustomizationProperties;
  }

  public boolean isHasResultListProperty() {
    return getResultListProperty() != null;
  }

  public boolean isInfoLogEnabled() {
    return this.log.isInfoEnabled();
  }

  public boolean isInputContentTypeSupported(final String contentType) {
    return this.inputDataContentTypes.contains(contentType);
  }

  public boolean isJobParameter(final String fieldName) {
    final FieldDefinition attribute = this.requestRecordDefinition.getField(fieldName);
    if (attribute == null) {
      throw new IllegalArgumentException("Parameter does not exist" + fieldName);
    } else {
      return Booleans.getBoolean(attribute.getProperty(JOB_PARAMETER));
    }
  }

  public boolean isPerRequestInputData() {
    return this.perRequestInputData;
  }

  public boolean isPerRequestResultData() {
    return this.perRequestResultData;
  }

  public boolean isRequestFieldMapInitialized() {
    return this.requestFieldMapInitialized;
  }

  public boolean isRequestParameter(final String fieldName) {
    final FieldDefinition attribute = this.requestRecordDefinition.getField(fieldName);
    if (attribute == null) {
      throw new IllegalArgumentException("Parameter does not exist" + fieldName);
    } else {
      return Booleans.getBoolean(attribute.getProperty(REQUEST_PARAMETER));
    }
  }

  public boolean isResultContentTypeSupported(final String contentType) {
    return this.resultDataContentTypes.contains(contentType);
  }

  public boolean isSecurityServiceRequired() {
    return this.securityServiceRequired;
  }

  public boolean isTestModeEnabled() {
    return this.testModeEnabled;
  }

  public boolean isValidateGeometry() {
    return this.validateGeometry;
  }

  public void pluginExecute(final Object plugin) {
    try {
      this.executeMethod.invoke(plugin, NO_ARGS);
    } catch (final InvocationTargetException e) {
      final Throwable cause = e.getCause();
      if (cause instanceof RuntimeException) {
        throw (RuntimeException)cause;
      } else if (cause instanceof Error) {
        throw (Error)cause;
      } else {
        throw new RuntimeException("Unable to invoke execute on " + this.name, cause);
      }
    } catch (final Throwable t) {
      throw new RuntimeException("Unable to invoke execute on " + this.name, t);
    }
  }

  public Object pluginGetResultFieldValue(final Object plugin, final String fieldName) {
    final Method method = this.resultFieldMethodMap.get(fieldName);
    if (method == null) {
      return null;
    } else {
      try {
        return method.invoke(plugin, NO_ARGS);
      } catch (final InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException)cause;
        } else if (cause instanceof Error) {
          throw (Error)cause;
        } else {
          throw new RuntimeException("Cannot get " + this.name + "." + fieldName, cause);
        }
      } catch (final Throwable t) {
        throw new RuntimeException("Cannot get " + this.name + "." + fieldName, t);
      }
    }
  }

  public void pluginSetParameters(final Object plugin,
    final Map<String, ? extends Object> parameters) {
    final RecordDefinitionImpl requestRecordDefinition = getRequestRecordDefinition();
    for (final FieldDefinition field : requestRecordDefinition.getFields()) {
      final String parameterName = field.getName();
      Object parameterValue = parameters.get(parameterName);
      if (!"resultDataContentType".equals(parameterName)) {
        if (parameterValue == null) {
          parameterValue = field.getDefaultValue();
        }
        parameterValue = field.validate(parameterValue);
      }
      try {
        final Method method = this.requestFieldMethodMap.get(parameterName);
        if (method == null) {
        } else {
          method.invoke(plugin, parameterValue);
        }
      } catch (final InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException)cause;
        } else if (cause instanceof Error) {
          throw (Error)cause;
        } else {
          throw new IllegalArgumentException(this.name + "." + parameterName + " could not be set",
            cause);
        }
      } catch (final Throwable t) {
        throw new IllegalArgumentException(this.name + "." + parameterName + " could not be set",
          t);
      }
    }
  }

  public void pluginTestExecute(final Object plugin) {
    if (this.testExecuteMethod != null) {
      try {
        this.testExecuteMethod.invoke(plugin, NO_ARGS);
      } catch (final InvocationTargetException e) {
        final Throwable cause = e.getCause();
        if (cause instanceof RuntimeException) {
          throw (RuntimeException)cause;
        } else if (cause instanceof Error) {
          throw (Error)cause;
        } else {
          throw new RuntimeException("Unable to invoke testExecute on " + this.name, cause);
        }
      } catch (final Throwable t) {
        throw new RuntimeException("Unable to invoke testExecute on " + this.name, t);
      }
    }
  }

  public void setBatchModePermission(final String batchModePermission) {
    if (Property.hasValue(batchModePermission)) {
      this.batchModePermission = batchModePermission;
    } else {
      this.batchModePermission = "permitAll";
    }
    this.batchModeExpression = new SpelExpressionParser().parseExpression(this.batchModePermission);
  }

  public void setCoordinateSystems(final List<CoordinateSystem> coordinateSystems) {
    this.coordinateSystems = coordinateSystems;
  }

  public void setDescription(final String description) {
    this.description = description;
  }

  public void setDescriptionUrl(final String descriptionUrl) {
    this.descriptionUrl = descriptionUrl;
  }

  public void setDetailedDescription(final String detailedDescription) {
    this.detailedDescription = detailedDescription;
  }

  public void setExecuteMethod(final Method method) {
    this.executeMethod = method;
  }

  public void setGeometryFactory(final GeometryFactory geometryFactory) {
    this.geometryFactory = geometryFactory;
  }

  public void setHasCustomizationProperties(final boolean hasCustomizationProperties) {
    this.hasCustomizationProperties = hasCustomizationProperties;
  }

  public void setHasResultListCustomizationProperties(
    final boolean hasResultListCustomizationProperties) {
    this.hasResultListCustomizationProperties = hasResultListCustomizationProperties;
  }

  public void setId(final String id) {
    this.id = id;
  }

  public void setInstantModePermission(final String instantModePermission) {
    if (Property.hasValue(instantModePermission)) {
      this.instantModePermission = instantModePermission;
    } else {
      this.instantModePermission = "permitAll";
    }
    this.instantModeExpression = new SpelExpressionParser()
      .parseExpression(this.instantModePermission);
  }

  public void setLogLevel(final String logLevel) {
    this.log.setLogLevel(logLevel);
    final Level level = Level.toLevel(logLevel);
    final String moduleName = getModuleName();
    Logger.getLogger(moduleName + "." + this.name).setLevel(level);
    // Tempory fix for geocoder logging
    Logger.getLogger(moduleName + ".ca").setLevel(level);
    Logger.getLogger(getPackageName()).setLevel(level);
  }

  public void setMaxConcurrentRequests(final int maxConcurrentRequests) {
    this.maxConcurrentRequests = maxConcurrentRequests;
  }

  public void setMaxRequestsPerJob(final int maxRequestsPerJob) {
    this.maxRequestsPerJob = maxRequestsPerJob;
  }

  public void setName(final String name) {
    this.name = name;
  }

  public void setNumRequestsPerWorker(final int maximumRequestsPerNode) {
    this.numRequestsPerWorker = maximumRequestsPerNode;
  }

  public void setPackageName(final String packageName) {
    this.packageName = packageName;
  }

  public void setPerRequestInputData(final boolean perRequestInputData) {
    this.perRequestInputData = perRequestInputData;
  }

  public void setPerRequestResultData(final boolean perRequestResultData) {
    this.perRequestResultData = perRequestResultData;
  }

  public void setRequestFieldMapInitialized(final boolean requestFieldMapInitialized) {
    this.requestFieldMapInitialized = requestFieldMapInitialized;
  }

  public void setResultListProperty(final String resultListProperty) {
    this.resultListProperty = resultListProperty;
  }

  public void setSecurityServiceRequired(final boolean securityServiceRequired) {
    this.securityServiceRequired = securityServiceRequired;
  }

  public void setTestExecuteMethod(final Method testExecuteMethod) {
    this.testExecuteMethod = testExecuteMethod;
  }

  public void setTestModeEnabled(final boolean testModeEnabled) {
    this.testModeEnabled = testModeEnabled;
  }

  public void setTitle(final String title) {
    this.title = title;
  }

  public void setValidateGeometry(final boolean validateGeometry) {
    this.validateGeometry = validateGeometry;
  }

  @Override
  public String toString() {
    return this.name;
  }
}
