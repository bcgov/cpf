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

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URL;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.apache.commons.beanutils.BeanUtils;
import org.apache.log4j.Logger;

import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;

import com.revolsys.collection.map.Maps;
import com.revolsys.geometry.model.BoundingBox;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryCollection;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.geometry.model.LineString;
import com.revolsys.geometry.model.Lineal;
import com.revolsys.geometry.model.Point;
import com.revolsys.geometry.model.Polygon;
import com.revolsys.geometry.model.Polygonal;
import com.revolsys.geometry.model.Punctual;
import com.revolsys.io.LazyHttpPostOutputStream;
import com.revolsys.parallel.ThreadUtil;
import com.revolsys.record.property.FieldProperties;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.util.Booleans;
import com.revolsys.util.Exceptions;
import com.revolsys.util.MathUtil;
import com.revolsys.util.Property;
import com.revolsys.util.UrlUtil;
import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.impl.PackedCoordinateSequence;

public class PluginAdaptor {

  public static final String[] INPUT_DATA_PARAMETER_NAMES = new String[] {
    "inputDataUrl", "inputDataContentType"
  };

  public static final List<String> INTERNAL_PROPERTY_NAMES = Arrays.asList("sequenceNumber",
    "resultNumber");

  public static Object getTestValue(final FieldDefinition field) {
    Object value;
    value = field.getDefaultValue();
    if (value == null) {
      final Class<?> typeClass = field.getTypeClass();
      if (Boolean.class.isAssignableFrom(typeClass)) {
        value = true;
      } else if (Number.class.isAssignableFrom(typeClass)) {
        value = 123;
      } else if (URL.class.isAssignableFrom(typeClass)) {
        String urlString = "http://www.test.com/";
        final int length = field.getLength();
        if (length > 0 && length < 4) {
          urlString = urlString.substring(0, length);
        }
        value = UrlUtil.getUrl(urlString);
      } else if (String.class.isAssignableFrom(typeClass)) {
        value = "test";
        final int length = field.getLength();
        if (length > 0 && length < 4) {
          value = ((String)value).substring(0, length);
        }
      } else if (Date.class.isAssignableFrom(typeClass)) {
        final Timestamp time = new Timestamp(System.currentTimeMillis());
        value = field.toFieldValue(time);
      } else if (LineString.class.isAssignableFrom(typeClass)) {
        value = GeometryFactory.wgs84().lineString(2, -125.0, 53.0, -125.1, 53.0);
      } else if (Polygon.class.isAssignableFrom(typeClass)) {
        final BoundingBox boundingBox = GeometryFactory.wgs84().newBoundingBox(-125.0, 53.0, -125.1,
          53.0);
        value = boundingBox.toPolygon(10);
      } else if (Lineal.class.isAssignableFrom(typeClass)) {
        final LineString line1 = GeometryFactory.wgs84().lineString(2, -125.0, 53.0, -125.1, 53.0);
        final LineString line2 = GeometryFactory.wgs84().lineString(2, -125.2, 53.0, -125.3, 53.0);
        value = GeometryFactory.wgs84().lineal(line1, line2);
      } else if (Polygonal.class.isAssignableFrom(typeClass)) {
        final BoundingBox boundingBox = GeometryFactory.wgs84().newBoundingBox(-125.0, 53.0, -125.1,
          53.0);
        final Polygon polygon1 = boundingBox.toPolygon(10);
        final BoundingBox boundingBox2 = GeometryFactory.wgs84().newBoundingBox(-125.2, 53.0,
          -125.3, 53.0);
        final Polygon polygon2 = boundingBox2.toPolygon(10);
        value = GeometryFactory.wgs84().polygonal(polygon1, polygon2);
      } else if (Point.class.isAssignableFrom(typeClass)) {
        value = GeometryFactory.wgs84().point(-125, 53);
      } else if (GeometryCollection.class.isAssignableFrom(typeClass)
        || Punctual.class.isAssignableFrom(typeClass)) {
        final Point point1 = GeometryFactory.wgs84().point(-125, 53);
        final Point point2 = GeometryFactory.wgs84().point(-125, 53.1);
        value = GeometryFactory.wgs84().punctual(point1, point2);
      } else if (Geometry.class.isAssignableFrom(typeClass)) {
        value = GeometryFactory.wgs84().point(-125, 53);
      } else if (com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(typeClass)) {
        // JTS
        final com.vividsolutions.jts.geom.GeometryFactory jtsGeometryFactory = new com.vividsolutions.jts.geom.GeometryFactory(
          new com.vividsolutions.jts.geom.PrecisionModel(
            com.vividsolutions.jts.geom.PrecisionModel.FLOATING),
          4326);
        if (com.vividsolutions.jts.geom.LineString.class.isAssignableFrom(typeClass)) {
          final PackedCoordinateSequence.Double points = new PackedCoordinateSequence.Double(
            new double[] {
              -125, 53, -125.1, 53
            }, 2);
          final com.vividsolutions.jts.geom.LineString line = jtsGeometryFactory
            .createLineString(points);
          value = line;
        } else if (Polygon.class.isAssignableFrom(typeClass)) {
          final PackedCoordinateSequence.Double points = new PackedCoordinateSequence.Double(
            new double[] {
              -125, 53, -125, 53.1, -125.1, 53.1, -125.1, 53, -125, 53
            }, 2);
          final com.vividsolutions.jts.geom.Polygon polygon = jtsGeometryFactory
            .createPolygon(points);
          value = polygon;
        } else if (com.vividsolutions.jts.geom.MultiLineString.class.isAssignableFrom(typeClass)) {
          final PackedCoordinateSequence.Double points = new PackedCoordinateSequence.Double(
            new double[] {
              -125, 53, -125.1, 53
            }, 2);
          final com.vividsolutions.jts.geom.LineString line = jtsGeometryFactory
            .createLineString(points);
          value = jtsGeometryFactory
            .createMultiLineString(new com.vividsolutions.jts.geom.LineString[] {
              line
          });
        } else if (com.vividsolutions.jts.geom.MultiPolygon.class.isAssignableFrom(typeClass)) {
          final PackedCoordinateSequence.Double points = new PackedCoordinateSequence.Double(
            new double[] {
              -125, 53, -125, 53.1, -125.1, 53.1, -125.1, 53, -125, 53
            }, 2);
          final com.vividsolutions.jts.geom.Polygon polygon = jtsGeometryFactory
            .createPolygon(points);
          value = jtsGeometryFactory.createMultiPolygon(new com.vividsolutions.jts.geom.Polygon[] {
            polygon
          });
        } else if (com.vividsolutions.jts.geom.GeometryCollection.class.isAssignableFrom(typeClass)
          || com.vividsolutions.jts.geom.MultiPoint.class.isAssignableFrom(typeClass)) {
          final Coordinate[] coordinates = new Coordinate[] {
            new Coordinate(-125, 53)
          };
          value = jtsGeometryFactory.createMultiPoint(coordinates);
        } else {
          final com.vividsolutions.jts.geom.Point point = jtsGeometryFactory
            .createPoint(new Coordinate(-125, 53));
          value = point;
        }
      } else {
        value = "Unknown";
      }
    }
    return value;
  }

  private final Object plugin;

  private final BusinessApplication application;

  private Map<String, Object> responseFields;

  private SecurityService securityService;

  private Map<String, Object> testParameters = new HashMap<>();

  private final List<Map<String, Object>> results = new ArrayList<>();

  private final Map<String, Object> parameters = new HashMap<>();

  private final AppLog appLog;

  private Map<String, Object> customizationProperties = Collections.emptyMap();

  public PluginAdaptor(final BusinessApplication application, final Object plugin,
    String executionId, final String logLevel) {
    this.application = application;
    this.plugin = plugin;
    if (!Property.hasValue(logLevel)) {
      executionId = String.valueOf(System.currentTimeMillis());
    }
    this.appLog = new AppLog(application.getModuleName(), application.getName(), executionId,
      logLevel);
    try {
      final Class<? extends Object> pluginClass = plugin.getClass();
      final Method setAppLogMethod = pluginClass.getMethod("setAppLog", AppLog.class);
      try {
        setAppLogMethod.invoke(plugin, this.appLog);
      } catch (final IllegalAccessException e) {
        Exceptions.throwCauseException(e);
      } catch (final InvocationTargetException e) {
        Exceptions.throwCauseException(e);
      }
    } catch (final NoSuchMethodException e) {
    }
  }

  public void addTestParameter(final String name, final Object value) {
    this.testParameters.put(name, value);
  }

  @SuppressWarnings("unchecked")
  public void execute() {
    final String resultListProperty = this.application.getResultListProperty();

    final boolean testMode = this.application.isTestModeEnabled()
      && Booleans.isTrue(this.testParameters.get("cpfPluginTest"));
    if (testMode) {
      double minTime = Maps.getDouble(this.testParameters, "cpfMinExecutionTime", -1.0);
      double maxTime = Maps.getDouble(this.testParameters, "cpfMaxExecutionTime", -1.0);
      final double meanTime = Maps.getDouble(this.testParameters, "cpfMeanExecutionTime", -1.0);
      final double standardDeviation = Maps.getDouble(this.testParameters, "cpfStandardDeviation",
        -1.0);
      double executionTime;
      if (standardDeviation <= 0) {
        if (minTime < 0) {
          minTime = 0.0;
        }
        if (maxTime < minTime) {
          maxTime = minTime + 10;
        }
        executionTime = MathUtil.randomRange(minTime, maxTime);
      } else {
        executionTime = MathUtil.randomGaussian(meanTime, standardDeviation);
      }
      if (minTime >= 0 && executionTime < minTime) {
        executionTime = minTime;
      }
      if (maxTime > 0 && maxTime > minTime && executionTime > maxTime) {
        executionTime = maxTime;
      }
      final long milliSeconds = (long)(executionTime * 1000);
      if (milliSeconds > 0) {
        ThreadUtil.pause(milliSeconds);
      }
      this.application.pluginTestExecute(this.plugin);
    } else {
      this.application.pluginExecute(this.plugin);
    }
    if (this.application.isHasCustomizationProperties()) {
      try {
        this.customizationProperties = (Map<String, Object>)Property.get(this.plugin,
          "customizationProperties");
      } catch (final Throwable e) {
        this.appLog.error("Unable to get customization properties", e);
      }
    }
    if (resultListProperty == null) {
      this.responseFields = getResult(this.plugin, false, testMode);
      this.results.add(this.responseFields);
    } else {
      final List<Object> resultObjects = Property.getSimple(this.plugin, resultListProperty);
      if (resultObjects == null || resultObjects.isEmpty()) {
        if (testMode) {
          final double meanNumResults = Maps.getDouble(this.testParameters, "cpfMeanNumResults",
            3.0);
          final int numResults = (int)Math
            .round(MathUtil.randomGaussian(meanNumResults, meanNumResults / 5));
          for (int i = 0; i < numResults; i++) {
            final Map<String, Object> result = getResult(this.plugin, true, testMode);
            this.results.add(result);
          }
        }
      } else {
        for (final Object resultObject : resultObjects) {
          final Map<String, Object> result = getResult(resultObject, true, false);
          this.results.add(result);
        }
      }
    }
  }

  public BusinessApplication getApplication() {
    return this.application;
  }

  public AppLog getAppLog() {
    return this.appLog;
  }

  public Map<String, Object> getResponseFields() {
    return this.responseFields;
  }

  private Map<String, Object> getResult(final Object resultObject, final boolean resultList,
    final boolean test) {
    final RecordDefinition resultRecordDefinition = this.application.getResultRecordDefinition();
    final Map<String, Object> result = new HashMap<>();
    for (final FieldDefinition field : resultRecordDefinition.getFields()) {
      final String fieldName = field.getName();
      if (!INTERNAL_PROPERTY_NAMES.contains(fieldName)) {
        Object value = null;
        try {
          value = Property.getProperty(resultObject, fieldName);
        } catch (final Throwable t) {
          if (!test) {
            throw new IllegalArgumentException(
              "Could not read property " + this.application.getName() + "." + fieldName, t);
          }
        }
        if (value == null && test) {
          value = getTestValue(field);
          result.put(fieldName, value);
        } else {
          if (value instanceof Geometry) {
            Geometry geometry = (Geometry)value;
            GeometryFactory geometryFactory = field.getProperty(FieldProperties.GEOMETRY_FACTORY);
            if (geometryFactory == GeometryFactory.DEFAULT_3D) {
              geometryFactory = geometry.getGeometryFactory();
            }
            final int srid = Maps.getInteger(this.parameters, "resultSrid",
              geometryFactory.getCoordinateSystemId());
            final int axisCount = Maps.getInteger(this.parameters, "resultNumAxis",
              geometryFactory.getAxisCount());
            final double scaleXY = Maps.getDouble(this.parameters, "resultScaleFactorXy",
              geometryFactory.getScaleXY());
            final double scaleZ = Maps.getDouble(this.parameters, "resultScaleFactorZ",
              geometryFactory.getScaleZ());

            geometryFactory = GeometryFactory.fixed(srid, axisCount, scaleXY, scaleXY, scaleZ);
            geometry = geometryFactory.geometry(geometry);
            if (geometry.getCoordinateSystemId() == 0) {
              throw new IllegalArgumentException(
                "Geometry does not have a coordinate system (SRID) specified");
            }
            final Boolean validateGeometry = field.getProperty(FieldProperties.VALIDATE_GEOMETRY);
            if (validateGeometry == true) {
              if (!geometry.isValid()) {
                throw new IllegalArgumentException(
                  "Geometry is not valid for" + this.application.getName() + "." + fieldName);
              }
            }
            result.put(fieldName, geometry);
          } else {
            result.put(fieldName, value);
          }
        }

      }
    }
    final Map<String, Object> customizationProperties = new LinkedHashMap<>(
      this.customizationProperties);
    if (resultList && this.application.isHasResultListCustomizationProperties()) {
      final Map<String, Object> resultListProperties = Property.get(resultObject,
        "customizationProperties");
      if (resultListProperties != null) {
        customizationProperties.putAll(resultListProperties);
      }
    }
    if (!customizationProperties.isEmpty()) {
      result.put("customizationProperties", customizationProperties);
    }
    return result;
  }

  public List<Map<String, Object>> getResults() {
    return this.results;
  }

  public SecurityService getSecurityService() {
    return this.securityService;
  }

  public void setParameters(final Map<String, ? extends Object> parameters) {
    for (final Entry<String, ? extends Object> entry : parameters.entrySet()) {
      final String parameterName = entry.getKey();
      if (parameterName.startsWith("cpf")) {
        final Object parameterValue = entry.getValue();
        this.testParameters.put(parameterName, parameterValue);
      }
    }

    this.application.pluginSetParameters(this.plugin, parameters);
    if (this.application.isPerRequestInputData()) {
      for (final String parameterName : INPUT_DATA_PARAMETER_NAMES) {
        final Object parameterValue = parameters.get(parameterName);
        setPluginProperty(parameterName, parameterValue);
      }
    }
    if (this.application.isPerRequestResultData()) {
      final String resultDataContentType = (String)parameters.get("resultDataContentType");
      final String resultDataUrl = (String)parameters.get("resultDataUrl");
      OutputStream resultData;
      if (resultDataUrl == null) {
        resultData = (OutputStream)parameters.get("resultData");
        if (resultData == null) {
          try {
            final File file = File.createTempFile("cpf", ".out");
            resultData = new FileOutputStream(file);
            Logger.getLogger(getClass()).info("Writing result to " + file);
          } catch (final IOException e) {
            resultData = System.out;
          }
        }
      } else {
        resultData = new LazyHttpPostOutputStream(resultDataUrl, resultDataContentType);
      }
      setPluginProperty("resultData", resultData);
      setPluginProperty("resultDataContentType", resultDataContentType);
    }
    if (this.application.isSecurityServiceRequired()) {
      if (this.securityService == null) {
        throw new IllegalArgumentException("Security service is required");
      } else {
        setPluginProperty("securityService", this.securityService);
      }
    }
  }

  public void setPluginProperty(final String parameterName,
    final Map<String, ? extends Object> parameters) {
    final Object parameterValue = parameters.get(parameterName);
    setPluginProperty(parameterName, parameterValue);
  }

  public void setPluginProperty(final String parameterName, Object parameterValue) {
    try {
      final RecordDefinitionImpl requestRecordDefinition = this.application
        .getRequestRecordDefinition();
      final FieldDefinition field = requestRecordDefinition.getField(parameterName);
      if (field != null) {
        parameterValue = field.toFieldValue(parameterValue);
      }
      if (parameterValue != null) {
        BeanUtils.setProperty(this.plugin, parameterName, parameterValue);
      }
      this.parameters.put(parameterName, parameterValue);
    } catch (final Throwable t) {
      throw new IllegalArgumentException(
        this.application.getName() + "." + parameterName + " could not be set", t);
    }
  }

  public void setSecurityService(final SecurityService securityService) {
    this.securityService = securityService;
  }

  public void setTestParameters(final Map<String, Object> testParameters) {
    this.testParameters = new HashMap<>(testParameters);
  }

  @Override
  public String toString() {
    return this.application.getName() + ": " + this.plugin.toString();
  }
}
