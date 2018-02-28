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
package ca.bc.gov.open.cpf.plugin.impl.module;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeSet;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;

import org.apache.log4j.PatternLayout;
import org.apache.log4j.rolling.FixedWindowRollingPolicy;
import org.apache.log4j.rolling.RollingFileAppender;
import org.apache.log4j.rolling.SizeBasedTriggeringPolicy;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.ConstructorArgumentValues;
import org.springframework.beans.factory.support.BeanDefinitionReaderUtils;
import org.springframework.beans.factory.support.GenericBeanDefinition;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.annotation.AnnotationConfigUtils;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.util.StopWatch;

import ca.bc.gov.open.cpf.plugin.api.AllowedValues;
import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;
import ca.bc.gov.open.cpf.plugin.api.DefaultValue;
import ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration;
import ca.bc.gov.open.cpf.plugin.api.JobParameter;
import ca.bc.gov.open.cpf.plugin.api.RequestParameter;
import ca.bc.gov.open.cpf.plugin.api.Required;
import ca.bc.gov.open.cpf.plugin.api.ResultAttribute;
import ca.bc.gov.open.cpf.plugin.api.ResultList;
import ca.bc.gov.open.cpf.plugin.api.log.AppLog;
import ca.bc.gov.open.cpf.plugin.api.security.SecurityService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;
import ca.bc.gov.open.cpf.plugin.impl.PluginAdaptor;
import ca.bc.gov.open.cpf.plugin.impl.log.AppLogUtil;

import com.revolsys.beans.Classes;
import com.revolsys.collection.ArrayUtil;
import com.revolsys.collection.map.AttributeMap;
import com.revolsys.collection.map.MapEx;
import com.revolsys.collection.map.Maps;
import com.revolsys.collection.set.Sets;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.cs.CoordinateSystem;
import com.revolsys.geometry.cs.epsg.EpsgCoordinateSystems;
import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.io.FileUtil;
import com.revolsys.io.IoFactory;
import com.revolsys.io.map.MapReader;
import com.revolsys.io.map.MapReaderFactory;
import com.revolsys.open.compiler.annotation.Documentation;
import com.revolsys.record.io.RecordWriterFactory;
import com.revolsys.record.property.FieldProperties;
import com.revolsys.record.schema.FieldDefinition;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.spring.config.AttributesBeanConfigurer;
import com.revolsys.spring.resource.Resource;
import com.revolsys.spring.resource.UrlResource;
import com.revolsys.util.Exceptions;
import com.revolsys.util.JavaBeanUtil;
import com.revolsys.util.Property;
import com.revolsys.util.UrlUtil;

public class ClassLoaderModule implements Module {

  protected static final String STOPPING = "Stopping";

  private static final String ENABLED = "Enabled";

  protected static final String STARTING = "Starting";

  protected static final String STARTED = "Started";

  @SuppressWarnings("unchecked")
  private static final Class<? extends Annotation>[] STANDARD_METHOD_EXCLUDE_ANNOTATIONS = ArrayUtil
    .newArray(JobParameter.class, RequestParameter.class, Required.class);

  private static final Map<String, Class<?>[]> STANDARD_METHODS = new HashMap<>();

  protected static final String STOPPED = "Stopped";

  protected static final String DISABLED = "Disabled";

  private static Set<String> SUPPORTED_MIME_TYPES = Sets.newHash("application/dbase",
    "application/dbf", "application/gml+xml", "application/gpx+xml", "application/json",
    "application/vnd.geo+json", "application/vnd.google-earth.kml+xml",
    "application/vnd.google-earth.kmz", "application/x-geo+json", "application/x-shp",
    "application/x-shp+zip", "application/xhtml+xml", "text/csv", "text/html",
    "text/tab-separated-values", "text/x-wkt", "text/xml");

  private GenericApplicationContext applicationContext;

  private boolean applicationsLoaded;

  private List<String> businessApplicationNames = Collections.emptyList();

  private BusinessApplicationRegistry businessApplicationRegistry;

  private Map<String, BusinessApplication> businessApplicationsByName = Collections.emptyMap();

  private Map<BusinessApplication, String> businessApplicationsToBeanNames = Collections.emptyMap();

  private ClassLoader classLoader;

  private ConfigPropertyLoader configPropertyLoader;

  private URL configUrl;

  private long lastStartTime;

  private final AppLog log;

  private final List<CoordinateSystem> coordinateSystems = EpsgCoordinateSystems
    .getCoordinateSystems(Arrays.asList(4326, 4269, 3005, 26907, 26908, 26909, 26910, 26911));

  private boolean enabled = false;

  private Set<String> groupNamesToDelete;

  private boolean initialized;

  private String moduleError = "";

  private final String name;

  private Map<String, Set<ResourcePermission>> permissionsByGroupName = new HashMap<>();

  private boolean remoteable;

  private Date startedDate;

  {
    // Prime the JTS Geometry factory
    ca.bc.gov.open.cpf.plugin.api.GeometryFactory.getFactory();

    STANDARD_METHODS.put("setInputDataUrl", new Class<?>[] {
      URL.class
    });
    STANDARD_METHODS.put("setInputDataContentType", new Class<?>[] {
      String.class
    });
    STANDARD_METHODS.put("setResultSrid", new Class<?>[] {
      Integer.TYPE
    });
    STANDARD_METHODS.put("setResultNumAxis", new Class<?>[] {
      Integer.TYPE
    });
    STANDARD_METHODS.put("setResultScaleFactorXy", new Class<?>[] {
      Double.TYPE
    });
    STANDARD_METHODS.put("setResultScaleFactorZ", new Class<?>[] {
      Double.TYPE
    });
    STANDARD_METHODS.put("setResultData", new Class<?>[] {
      OutputStream.class
    });
    STANDARD_METHODS.put("setResultContentType", new Class<?>[] {
      String.class
    });
  }

  private boolean started = false;

  private String status;

  private final String environmentId;

  private List<String> beanImports = Collections.emptyList();

  public ClassLoaderModule(final BusinessApplicationRegistry businessApplicationRegistry,
    final String moduleName, final ClassLoader classLoader,
    final ConfigPropertyLoader configPropertyLoader, final URL configUrl, final String logLevel) {
    this(businessApplicationRegistry, moduleName, logLevel);
    this.classLoader = classLoader;
    this.configPropertyLoader = configPropertyLoader;
    this.configUrl = configUrl;
  }

  public ClassLoaderModule(final BusinessApplicationRegistry businessApplicationRegistry,
    final String moduleName, final String logLevel) {
    this.businessApplicationRegistry = businessApplicationRegistry;
    this.name = moduleName;
    this.log = new AppLog(moduleName, logLevel);
    this.environmentId = businessApplicationRegistry.getEnvironmentId();
  }

  @Override
  public void addModuleError(final String message) {
    addModuleError(message, null);
  }

  public void addModuleError(String message, final Throwable e) {
    if (Property.hasValue(message)) {
      this.log.error("Unable to initialize module " + getName() + ":\n  " + message, e);
      if (e != null) {
        message += ":\n  " + Exceptions.toString(e);
      }
    } else {
      this.log.error("Unable to initialize module " + getName(), e);
      message = Exceptions.toString(e);
    }

    this.moduleError += message + "\n";
    if (this.moduleError.length() > 2000) {
      this.moduleError = this.moduleError.substring(0, 1997) + "...";
    }
    stopDo();
    setStatus("Start Failed");
  }

  public void addModuleError(final Throwable e) {
    addModuleError(null, e);
  }

  private void addResourcePermissions(
    final Map<String, Set<ResourcePermission>> resourcePermissionsByGroupName,
    final String groupName, final Map<String, Object> pluginGroup) {
    final List<Map<String, Object>> permissions = Maps.get(pluginGroup, "permissions");
    final List<ResourcePermission> resourcePermissions = ResourcePermission
      .getPermissions(permissions);
    resourcePermissionsByGroupName.put(groupName, new HashSet<>(resourcePermissions));
  }

  private void checkStandardMethod(final Method method, final Class<?>[] standardMethodParameters) {
    final Class<?> pluginClass = method.getDeclaringClass();
    final String pluginClassName = pluginClass.getName();
    final String methodName = method.getName();
    final Class<?>[] methodParameters = method.getParameterTypes();
    if (methodParameters.length != standardMethodParameters.length) {
      throw new IllegalArgumentException(pluginClassName + "." + methodName
        + " must have the parameters " + Arrays.toString(standardMethodParameters));
    }
    for (int i = 0; i < standardMethodParameters.length; i++) {
      final Class<?> parameter1 = standardMethodParameters[i];
      final Class<?> parameter2 = methodParameters[i];
      if (parameter1 != parameter2) {
        throw new IllegalArgumentException(pluginClassName + "." + methodName
          + " must have the parameters " + Arrays.toString(standardMethodParameters));
      }

    }
    for (final Class<? extends Annotation> annotationClass : STANDARD_METHOD_EXCLUDE_ANNOTATIONS) {
      if (!method.isAnnotationPresent(annotationClass)) {
        throw new IllegalArgumentException(pluginClassName + "." + methodName
          + " standard method must not have annotation " + annotationClass);
      }
    }
  }

  @Override
  public void clearModuleError() {
    this.moduleError = "";
  }

  private void closeAppLogAppender(final String name) {
    final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(name);
    synchronized (logger) {
      logger.removeAllAppenders();
      logger.setAdditivity(true);
    }
  }

  @Override
  @PreDestroy
  public void destroy() {
    stopDo();
    if (this.classLoader instanceof URLClassLoader) {
      final URLClassLoader urlClassLoader = (URLClassLoader)this.classLoader;
      try {
        urlClassLoader.close();
      } catch (final IOException e) {
      }
    }
    this.classLoader = null;
    this.businessApplicationRegistry = null;
  }

  @Override
  public synchronized void disable() {
    if (this.enabled) {
      this.enabled = false;
    }
    if (isStarted()) {
      stop();
    }
    this.initialized = false;
    setStatus(DISABLED);
  }

  @Override
  public synchronized void enable() {
    if (!this.enabled) {
      this.enabled = true;
    }
    if (!isInitialized()) {
      setStatus(ENABLED);
      this.initialized = true;
      start();
    }
  }

  private synchronized GenericApplicationContext getApplicationContext() {
    loadApplications();
    return this.applicationContext;
  }

  @Override
  public BusinessApplication getBusinessApplication(final String businessApplicationName) {
    if (businessApplicationName == null) {
      return null;
    } else {
      return this.businessApplicationsByName.get(businessApplicationName);
    }
  }

  @Override
  public List<String> getBusinessApplicationNames() {
    return this.businessApplicationNames;
  }

  @Override
  public Object getBusinessApplicationPlugin(final BusinessApplication application,
    final String executionId, String logLevel) {
    if (application == null) {
      return null;
    } else {
      final String businessApplicationName = application.getName();
      if (logLevel == null) {
        logLevel = application.getLogLevel();
      }
      Object plugin;
      final GenericApplicationContext applicationContext = getApplicationContext();
      if (applicationContext == null) {
        throw new IllegalArgumentException("Unable to instantiate plugin " + businessApplicationName
          + ": unable to get application context");
      } else {
        try {
          final String beanName = this.businessApplicationsToBeanNames.get(application);
          plugin = applicationContext.getBean(beanName);
          return plugin;
        } catch (final Throwable t) {
          throw new IllegalArgumentException(
            "Unable to instantiate plugin " + businessApplicationName, t);
        }
      }
    }
  }

  @Override
  public Object getBusinessApplicationPlugin(final String businessApplicationName,
    final String executionId, final String logLevel) {
    final BusinessApplication application = getBusinessApplication(businessApplicationName);
    if (application == null) {
      return null;
    } else {
      return getBusinessApplicationPlugin(application, executionId, logLevel);
    }
  }

  @Override
  public PluginAdaptor getBusinessApplicationPluginAdaptor(final BusinessApplication application,
    final String executionId, String logLevel) {
    if (application == null) {
      return null;
    } else {
      final String businessApplicationName = application.getName();
      if (logLevel == null) {
        logLevel = application.getLogLevel();
      }
      Object plugin;
      final GenericApplicationContext applicationContext = getApplicationContext();
      if (applicationContext == null) {
        throw new IllegalArgumentException("Unable to instantiate plugin " + businessApplicationName
          + ": unable to get application context");
      } else {
        try {
          final String beanName = this.businessApplicationsToBeanNames.get(application);
          plugin = applicationContext.getBean(beanName);
          final PluginAdaptor pluginAdaptor = new PluginAdaptor(application, plugin, executionId,
            logLevel);
          return pluginAdaptor;
        } catch (final Throwable t) {
          throw new IllegalArgumentException(
            "Unable to instantiate plugin " + businessApplicationName, t);
        }
      }
    }
  }

  @Override
  public PluginAdaptor getBusinessApplicationPluginAdaptor(final String businessApplicationName,
    final String executionId, final String logLevel) {
    final BusinessApplication application = getBusinessApplication(businessApplicationName);
    if (application == null) {
      return null;
    } else {
      return getBusinessApplicationPluginAdaptor(application, executionId, logLevel);
    }
  }

  public BusinessApplicationRegistry getBusinessApplicationRegistry() {
    return this.businessApplicationRegistry;
  }

  @Override
  public List<BusinessApplication> getBusinessApplications() {
    final List<BusinessApplication> businessApplications = new ArrayList<>();
    for (final String businessApplicationName : getBusinessApplicationNames()) {
      final BusinessApplication businessApplication = getBusinessApplication(
        businessApplicationName);
      if (businessApplication != null) {
        businessApplications.add(businessApplication);
      }
    }
    return businessApplications;
  }

  private BusinessApplication getBusinessApplicaton(final String moduleName,
    final Class<?> pluginClass, final Map<String, Map<String, Object>> propertiesByName) {
    final String className = pluginClass.getName();
    final BusinessApplicationPlugin pluginAnnotation = pluginClass
      .getAnnotation(BusinessApplicationPlugin.class);
    if (pluginAnnotation == null) {
      throw new IllegalArgumentException(
        className + " does not have the annotation " + BusinessApplicationPlugin.class);
    } else {
      String businessApplicationName = pluginAnnotation.name();
      if (businessApplicationName == null || businessApplicationName.trim().length() == 0) {
        businessApplicationName = className.substring(className.lastIndexOf('.') + 1)
          .replaceAll("Plugin$", "");
      }

      final BusinessApplication businessApplication = new BusinessApplication(pluginAnnotation,
        this, businessApplicationName);

      final Map<String, Object> defaultProperties = propertiesByName.get("default");
      businessApplication.setProperties(defaultProperties);
      final Map<String, Object> properties = propertiesByName.get(businessApplicationName);
      businessApplication.setProperties(properties);

      businessApplication.setCoordinateSystems(this.coordinateSystems);

      final String descriptionUrl = pluginAnnotation.descriptionUrl();
      businessApplication.setDescriptionUrl(descriptionUrl);

      final String description = pluginAnnotation.description();
      businessApplication.setDescription(description);

      final Documentation detailedDescriptionAnnotation = pluginClass
        .getAnnotation(Documentation.class);
      if (detailedDescriptionAnnotation != null) {
        final String detailedDescription = detailedDescriptionAnnotation.value();
        if (Property.hasValue(detailedDescription)) {
          businessApplication.setDetailedDescription(detailedDescription);
        }
      }
      final String title = pluginAnnotation.title();
      if (title != null && title.trim().length() > 0) {
        businessApplication.setTitle(title);
      }

      final String instantModePermission = pluginAnnotation.instantModePermission();
      businessApplication.setInstantModePermission(instantModePermission);

      final String batchModePermission = pluginAnnotation.batchModePermission();
      businessApplication.setBatchModePermission(batchModePermission);

      String packageName = pluginAnnotation.packageName();
      if (!Property.hasValue(packageName)) {
        packageName = "ca.bc.gov." + moduleName.toLowerCase();
      }
      businessApplication.setPackageName(packageName);

      final boolean perRequestInputData = pluginAnnotation.perRequestInputData();
      businessApplication.setPerRequestInputData(perRequestInputData);

      final boolean perRequestResultData = pluginAnnotation.perRequestResultData();
      businessApplication.setPerRequestResultData(perRequestResultData);

      final int maxRequestsPerJob = pluginAnnotation.maxRequestsPerJob();
      businessApplication.setMaxRequestsPerJob(maxRequestsPerJob);

      final int numRequestsPerWorker = pluginAnnotation.numRequestsPerWorker();
      businessApplication.setNumRequestsPerWorker(numRequestsPerWorker);

      final int maxConcurrentRequests = pluginAnnotation.maxConcurrentRequests();
      businessApplication.setMaxConcurrentRequests(maxConcurrentRequests);

      final String logLevel = pluginAnnotation.logLevel();
      businessApplication.setLogLevel(logLevel);

      final GeometryConfiguration geometryConfiguration = pluginClass
        .getAnnotation(GeometryConfiguration.class);
      if (geometryConfiguration != null) {
        final GeometryFactory geometryFactory = getGeometryFactory(GeometryFactory.DEFAULT_3D,
          className, geometryConfiguration);
        businessApplication.setGeometryFactory(geometryFactory);
        final boolean validateGeometry = geometryConfiguration.validate();
        businessApplication.setValidateGeometry(validateGeometry);
      }

      Method resultListMethod = null;
      final List<Method> methods = JavaBeanUtil.getMethods(pluginClass);
      for (final Method method : methods) {

        final String methodName = method.getName();
        if (methodName.equals("execute")) {
          processExecute(businessApplication, method);
        } else if (methodName.equals("testExecute")) {
          processTestExecute(businessApplication, method);
        } else {
          processParameter(pluginClass, businessApplication, method);
          processResultAttribute(pluginClass, businessApplication, method, false);
          if (method.isAnnotationPresent(ResultList.class)) {
            if (resultListMethod == null) {
              resultListMethod = method;
            } else {
              throw new IllegalArgumentException("Business Application " + businessApplicationName
                + " may only have one method with the annotation " + ResultList.class);
            }
          }
        }
      }
      businessApplication.setRequestFieldMapInitialized(true);
      if (perRequestResultData) {
        final RecordDefinition resultRecordDefinition = businessApplication
          .getResultRecordDefinition();
        try {
          pluginClass.getMethod("setResultData", OutputStream.class);
        } catch (final Throwable e) {
          throw new IllegalArgumentException("Business Application " + businessApplicationName
            + " must have a public voud setResultData(OutputStrean resultData) method", e);
        }
        try {
          pluginClass.getMethod("setResultDataContentType", String.class);
        } catch (final Throwable e) {
          throw new IllegalArgumentException("Business Application " + businessApplicationName
            + " must have a public voud setResultDataContentType(String resultContentType) method",
            e);
        }
        businessApplication.setPerRequestResultData(true);
        if (resultRecordDefinition.getFieldCount() > 0) {
          throw new IllegalArgumentException("Business Application " + businessApplicationName
            + " cannot have a setResultData method and result fields");
        } else if (resultListMethod != null) {
          throw new IllegalArgumentException("Business Application " + businessApplicationName
            + " cannot have a setResultData method and a method with the annotation "
            + ResultList.class);
        }

      } else {
        if (resultListMethod == null) {
          final RecordDefinition resultRecordDefinition = businessApplication
            .getResultRecordDefinition();
          if (resultRecordDefinition.getFieldCount() == 0) {
            throw new IllegalArgumentException(
              "Business Application " + businessApplicationName + " must have result fields");
          }
        } else {
          processResultListMethod(businessApplication, resultListMethod);
        }
      }

      final String[] inputDataContentTypes = pluginAnnotation.inputDataContentTypes();
      if (perRequestInputData) {
        if (inputDataContentTypes.length == 0) {
          businessApplication.addInputDataContentType("*/*", "Any content type", "*");
        } else {
          for (final String contentType : inputDataContentTypes) {
            businessApplication.addInputDataContentType(contentType, contentType, contentType);
          }
        }
      } else {
        if (inputDataContentTypes.length == 0) {
          final List<MapReaderFactory> factories = IoFactory.factories(MapReaderFactory.class);
          for (final MapReaderFactory factory : factories) {
            if (factory.isSingleFile()) {
              if (factory.isCustomFieldsSupported()) {
                for (final String contentType : factory.getMediaTypes()) {
                  if (SUPPORTED_MIME_TYPES.contains(contentType)) {
                    final String fileExtension = factory.getFileExtension(contentType);
                    if (fileExtension == null) {
                      businessApplication.addInputDataContentType(contentType);
                    } else {
                      final String typeDescription = factory.getName() + " (" + fileExtension + ")";
                      businessApplication.addInputDataContentType(contentType, typeDescription,
                        fileExtension);
                    }
                  }
                }
              }
            }
          }
        } else {
          for (final String contentType : inputDataContentTypes) {
            if (SUPPORTED_MIME_TYPES.contains(contentType)) {
              final MapReaderFactory factory = IoFactory.factoryByMediaType(MapReaderFactory.class,
                contentType);
              if (factory != null && factory.isSingleFile()) {
                if (factory.isCustomFieldsSupported()) {
                  final String fileExtension = factory.getFileExtension(contentType);
                  if (fileExtension != null) {
                    final String typeDescription = factory.getName() + " (" + fileExtension + ")";
                    businessApplication.addInputDataContentType(contentType, typeDescription,
                      fileExtension);
                  }
                }
              }
            }
          }
        }
      }

      final String[] resultDataContentTypes = pluginAnnotation.resultDataContentTypes();
      if (perRequestResultData) {
        if (resultDataContentTypes.length == 0) {
          businessApplication.addResultDataContentType("*/*", "*", "Any content type");
        } else {
          for (final String contentType : resultDataContentTypes) {
            businessApplication.addResultDataContentType(contentType, contentType, contentType);
          }
        }
      } else {
        final boolean hasResultGeometry = businessApplication.isHasGeometryResultAttribute();
        if (resultDataContentTypes.length == 0) {
          final List<RecordWriterFactory> writerFactories = IoFactory
            .factories(RecordWriterFactory.class);
          for (final RecordWriterFactory factory : writerFactories) {
            if (factory.isSingleFile()) {
              if (!hasResultGeometry || factory.isGeometrySupported()) {
                if (factory.isCustomFieldsSupported()) {
                  for (final String contentType : factory.getMediaTypes()) {
                    if (SUPPORTED_MIME_TYPES.contains(contentType)) {
                      final String fileNameExtension = factory.getFileExtension(contentType);
                      if (fileNameExtension == null) {
                        businessApplication.addResultDataContentType(contentType);
                      } else if (fileNameExtension != null && !"xlsx".equals(fileNameExtension)) {
                        final String typeDescription = factory.getName() + " (" + fileNameExtension
                          + ")";
                        businessApplication.addResultDataContentType(contentType, fileNameExtension,
                          typeDescription);
                      }
                    }
                  }
                }
              }
            }
          }
        } else {
          for (final String contentType : resultDataContentTypes) {
            if (SUPPORTED_MIME_TYPES.contains(contentType)) {
              final RecordWriterFactory factory = IoFactory
                .factoryByMediaType(RecordWriterFactory.class, contentType);
              if (factory != null && factory.isSingleFile()) {
                if (!hasResultGeometry || factory.isGeometrySupported()) {
                  if (factory.isCustomFieldsSupported()) {
                    final String fileNameExtension = factory.getFileExtension(contentType);
                    final String typeDescription = factory.getName() + " (" + fileNameExtension
                      + ")";
                    businessApplication.addResultDataContentType(contentType, fileNameExtension,
                      typeDescription);
                  }
                }
              }
            }
          }
        }
      }

      // TODO move these fields out of the request record definition as they are
      // job only parameters
      final RecordDefinitionImpl requestRecordDefinition = businessApplication
        .getRequestRecordDefinition();
      final FieldDefinition resultDataContentType = requestRecordDefinition
        .getField("resultDataContentType");
      final Set<String> resultDataContentTypeSet = businessApplication.getResultDataContentTypes();
      resultDataContentType.setAllowedValues(businessApplication.getResultDataFileExtensions());

      final String defaultResultDataContentType = BusinessApplication
        .getDefaultMimeType(resultDataContentTypeSet);
      resultDataContentType.setDefaultValue(defaultResultDataContentType);

      try {
        pluginClass.getMethod("setSecurityService", SecurityService.class);
        businessApplication.setSecurityServiceRequired(true);
      } catch (final NoSuchMethodException e) {
      } catch (final Throwable e) {
        throw new IllegalArgumentException("Business Application " + businessApplicationName
          + " has a setSecurityService(SecurityService) method but there was an error accessing it",
          e);
      }

      final Map<String, Object> configProperties = getConfigProperties(moduleName,
        "APP_" + businessApplicationName.toUpperCase());
      businessApplication.setProperties(configProperties);
      return businessApplication;
    }
  }

  @Override
  public ClassLoader getClassLoader() {
    return this.classLoader;
  }

  private Map<String, Object> getConfigProperties(final String moduleName,
    final String componentName) {
    if (this.configPropertyLoader == null) {
      return new HashMap<>();
    } else {
      final Map<String, Object> configProperties = this.configPropertyLoader
        .getConfigProperties(moduleName, componentName);
      return configProperties;
    }
  }

  public ConfigPropertyLoader getConfigPropertyLoader() {
    return this.configPropertyLoader;
  }

  @Override
  public URL getConfigUrl() {
    return this.configUrl;
  }

  /**
   * Get the geometry factory instance for the specified geometry configuration.
   *
   * @param geometryFactory
   * @param message The message to prefix any log messages with.
   * @param geometryConfiguration The geometry configuration.
   * @return The geometry factory.
   */
  private GeometryFactory getGeometryFactory(final GeometryFactory geometryFactory,
    final String message, final GeometryConfiguration geometryConfiguration) {
    int srid = geometryConfiguration.srid();
    if (srid < 0) {
      this.log.warn(message + " srid must be >= 0");
      srid = geometryFactory.getCoordinateSystemId();
    } else if (srid == 0) {
      srid = geometryFactory.getCoordinateSystemId();
    }
    int axisCount = geometryConfiguration.numAxis();
    if (axisCount == 0) {
      axisCount = geometryFactory.getAxisCount();
    } else if (axisCount < 2) {
      this.log.warn(message + " axisCount must be >= 2");
      axisCount = 2;
    } else if (axisCount > 3) {
      this.log.warn(message + " axisCount must be <= 3");
      axisCount = 3;
    }
    double scaleXy = geometryConfiguration.scaleFactorXy();
    if (scaleXy == 0) {
      scaleXy = geometryFactory.getScaleXY();
    } else if (scaleXy < 0) {
      this.log.warn(message + " scaleXy must be >= 0");
      scaleXy = geometryFactory.getScaleXY();
    }
    double scaleZ = geometryConfiguration.scaleFactorZ();
    if (scaleXy == 0) {
      scaleXy = geometryFactory.getScaleZ();
    } else if (scaleZ < 0) {
      this.log.warn(message + " scaleZ must be >= 0");
      scaleZ = geometryFactory.getScaleZ();
    }
    return GeometryFactory.fixed(srid, axisCount, scaleXy, scaleXy, scaleZ);
  }

  public Set<String> getGroupNamesToDelete() {
    return this.groupNamesToDelete;
  }

  @Override
  public int getJarCount() {
    if (isEnabled()) {
      final ClassLoader classLoader = getClassLoader();
      if (classLoader instanceof URLClassLoader) {
        @SuppressWarnings("resource")
        final URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
        final URL[] urls = urlClassLoader.getURLs();
        return urls.length;
      }
    }
    return 0;
  }

  @Override
  public URL getJarUrl(final int urlIndex) {
    if (isEnabled()) {
      final ClassLoader classLoader = getClassLoader();
      if (classLoader instanceof URLClassLoader) {
        @SuppressWarnings("resource")
        final URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
        final URL[] urls = urlClassLoader.getURLs();
        if (urlIndex >= 0 && urlIndex < urls.length) {
          return urls[urlIndex];
        }
      }
    }
    return null;
  }

  @Override
  public List<URL> getJarUrls() {
    if (isEnabled()) {
      final ClassLoader classLoader = getClassLoader();
      if (classLoader instanceof URLClassLoader) {
        @SuppressWarnings("resource")
        final URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
        final URL[] urls = urlClassLoader.getURLs();
        final List<URL> jarUrls = new ArrayList<>();
        for (final URL url : urls) {
          jarUrls.add(url);
        }
        return jarUrls;
      }
    }
    return Collections.emptyList();
  }

  @Override
  public long getLastStartTime() {
    return this.lastStartTime;
  }

  public AppLog getLog() {
    return this.log;
  }

  @Override
  public String getModuleDescriptor() {
    if (this.classLoader == null) {
      return "Class Loader Module undefined";
    } else {
      return this.classLoader.toString();
    }
  }

  @Override
  public String getModuleError() {
    return this.moduleError;
  }

  @Override
  public String getModuleType() {
    return "ClassLoader";
  }

  @Override
  public String getName() {
    return this.name;
  }

  @Override
  public Map<String, Set<ResourcePermission>> getPermissionsByGroupName() {
    return this.permissionsByGroupName;
  }

  @Override
  public Date getStartedDate() {
    return this.startedDate;
  }

  @Override
  public long getStartedTime() {
    if (this.startedDate == null) {
      return -1;
    } else {
      return this.startedDate.getTime();
    }
  }

  public String getStatus() {
    return this.status;
  }

  private List<MapEx> getUserGroupMaps() {
    try {
      final ClassLoader classLoader = getClassLoader();
      if (!isHasError()) {
        final String parentUrl = UrlUtil.getParentString(this.configUrl);
        final Enumeration<URL> urls = classLoader
          .getResources("META-INF/ca.bc.gov.open.cpf.plugin.UserGroups.json");
        while (urls.hasMoreElements()) {
          final URL userGroups = urls.nextElement();
          if (userGroups.toString().startsWith(parentUrl)) {
            final Resource resource = new UrlResource(userGroups);
            final MapReader reader = MapReader.newMapReader(resource);
            return reader.toList();
          }
        }
      }
    } catch (final Throwable t) {
      addModuleError(t);
    }
    return Collections.emptyList();
  }

  @Override
  public boolean hasBusinessApplication(final String businessApplicationName) {
    return getBusinessApplication(businessApplicationName) != null;
  }

  @SuppressWarnings("deprecation")
  private void initAppLogAppender(final String businessApplicationName) {
    final String fileName;
    String logName = this.name;
    final boolean isApp = Property.hasValue(businessApplicationName);
    if (isApp) {
      logName += "." + businessApplicationName;
      fileName = this.name + "_" + businessApplicationName + "_" + this.environmentId;
    } else {
      fileName = this.name + "_" + this.environmentId;
    }
    final org.apache.log4j.Logger logger = org.apache.log4j.Logger.getLogger(logName);
    synchronized (logger) {
      logger.removeAllAppenders();
      final File rootDirectory = this.businessApplicationRegistry.getAppLogDirectory();
      if (rootDirectory == null || !(rootDirectory.exists() || rootDirectory.mkdirs())) {
        logger.setAdditivity(true);
      } else {
        logger.setAdditivity(false);

        File logDirectory = FileUtil.getDirectory(rootDirectory, this.name);
        if (isApp) {
          logDirectory = FileUtil.getDirectory(logDirectory, businessApplicationName);
        }

        final String baseFileName = logDirectory + "/" + fileName;
        final String activeFileName = baseFileName + ".log";
        final FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
        rollingPolicy.setActiveFileName(activeFileName);
        final String fileNamePattern = baseFileName + ".%i.log";
        rollingPolicy.setFileNamePattern(fileNamePattern);

        final RollingFileAppender appender = new RollingFileAppender();
        appender.setName(businessApplicationName);
        appender.setFile(activeFileName);
        appender.setLayout(new PatternLayout("%d\t%p\t%c\t%m%n"));
        appender.setRollingPolicy(rollingPolicy);
        appender.setTriggeringPolicy(new SizeBasedTriggeringPolicy(1024 * 1024 * 10));
        appender.activateOptions();
        appender.rollover();
        logger.addAppender(appender);
      }
    }
  }

  private void initializeGroupPermissions() {
    try {
      final Map<String, Set<ResourcePermission>> permissionsByGroupName = new HashMap<>();
      final Set<String> groupNamesToDelete = new HashSet<>();
      final Map<String, Map<String, Object>> groupsByName = new HashMap<>();
      for (final MapEx pluginGroup : getUserGroupMaps()) {
        String groupName = pluginGroup.getString("name");
        if (groupName == null) {
          addModuleError("A UserGroup must have a name: " + pluginGroup);
        } else {
          groupName = groupName.toUpperCase();
          final String action = pluginGroup.getString("action");
          if (groupsByName.containsKey(groupName)) {
            addModuleError("A UserGroup must have a unique name: " + pluginGroup);
          } else if (groupNamesToDelete.contains(groupName)) {
            addModuleError(
              "A UserGroup cannot be deleted and created in the same file: " + pluginGroup);
          } else if (groupName.startsWith("ROLE_" + this.name.toUpperCase())
            && "delete".equalsIgnoreCase(action)) {
            groupNamesToDelete.add(groupName);
          } else {
            groupsByName.put(groupName, pluginGroup);
            addResourcePermissions(permissionsByGroupName, groupName, pluginGroup);
          }
        }
      }
      this.permissionsByGroupName = permissionsByGroupName;
      this.groupNamesToDelete = groupNamesToDelete;
    } catch (final Throwable t) {
      addModuleError(t);
    }
  }

  @Override
  public boolean isApplicationsLoaded() {
    return this.applicationsLoaded;
  }

  @Override
  public boolean isEnabled() {
    return this.enabled;
  }

  public boolean isGroupNameValid(final String groupName) {
    return true;
  }

  public boolean isHasError() {
    return Property.hasValue(getModuleError());
  }

  public boolean isInitialized() {
    return this.initialized;
  }

  @Override
  public boolean isRelaodable() {
    return false;
  }

  @Override
  public boolean isRemoteable() {
    return this.remoteable;
  }

  @Override
  public boolean isStarted() {
    return this.started;
  }

  @Override
  public synchronized void loadApplications() {
    if (isStarted() && !isApplicationsLoaded()) {
      this.log.info("Start\tLoad plugin beans\t" + this.configUrl);
      try {
        final ClassLoader classLoader = getClassLoader();
        final GenericApplicationContext applicationContext = new GenericApplicationContext();
        applicationContext.setClassLoader(classLoader);

        AnnotationConfigUtils.registerAnnotationConfigProcessors(applicationContext, null);
        final Map<String, Object> configProperties = getConfigProperties(this.name,
          "MODULE_BEAN_PROPERTY");

        final AttributesBeanConfigurer attributesConfig = new AttributesBeanConfigurer(
          applicationContext, configProperties);
        applicationContext.addBeanFactoryPostProcessor(attributesConfig);
        registerConfigPropertyBeans(this.name, applicationContext);

        final XmlBeanDefinitionReader beanReader = new XmlBeanDefinitionReader(applicationContext);
        beanReader.setBeanClassLoader(classLoader);
        beanReader.loadBeanDefinitions(new UrlResource(this.configUrl));
        if (!isHasError()) {
          for (final String beanImport : this.beanImports) {
            try {
              final org.springframework.core.io.Resource[] resources = applicationContext
                .getResources(beanImport);
              for (final org.springframework.core.io.Resource resource : resources) {
                this.log.info("Start\tImport beans\t" + resource);
                beanReader.loadBeanDefinitions(resource);
                this.log.info("End\tImport beans\t" + resource);
              }
            } catch (final Throwable e) {
              addModuleError("Error loading bean import " + beanImport + " from " + this.configUrl,
                e);
            }
          }
        }
        if (!isHasError()) {
          applicationContext.refresh();
          this.applicationContext = applicationContext;
        }
        if (!isHasError()) {
          this.applicationsLoaded = true;
        }
      } catch (final Throwable t) {
        addModuleError(t);
      } finally {
        this.log.info("End\tLoad plugin beans\t" + this.configUrl);
      }
    }
  }

  private BusinessApplication loadBusinessApplication(
    final Map<String, BusinessApplication> businessApplicationsByName, final String moduleName,
    final String pluginClassName, final Map<String, Map<String, Object>> propertiesByName) {
    try {
      if (isEnabled()) {
        final ClassLoader classLoader = getClassLoader();
        this.log.info("Start\tLoading plugin\tclass=" + pluginClassName);
        final Class<?> pluginClass = Class.forName(pluginClassName.trim(), true, classLoader);
        final BusinessApplication businessApplication = getBusinessApplicaton(moduleName,
          pluginClass, propertiesByName);
        final String pluginName = businessApplication.getName();
        businessApplicationsByName.put(pluginName, businessApplication);
        this.log.info("End\tLoading plugin\tclass=" + pluginClassName + "\tbusinessApplicationName="
          + pluginName);
        return businessApplication;
      }
    } catch (final Throwable e) {
      addModuleError("Error loading plugin " + pluginClassName, e);
    }
    return null;

  }

  @SuppressWarnings("unchecked")
  private void loadBusinessApplications() {
    final Date date = new Date(System.currentTimeMillis());
    final Set<String> businessApplicationNames = new TreeSet<>();
    final Map<BusinessApplication, String> businessApplicationsToBeanNames = new HashMap<>();
    clearModuleError();
    final Map<String, BusinessApplication> businessApplicationsByName = new HashMap<>();
    this.log.info("Start\tLoad plugin metadata\t" + this.configUrl);
    final GenericApplicationContext applicationContext = new GenericApplicationContext();
    try {
      final ClassLoader classLoader = getClassLoader();
      applicationContext.setClassLoader(classLoader);
      final XmlBeanDefinitionReader beanReader = new XmlBeanDefinitionReader(applicationContext);
      beanReader.setBeanClassLoader(classLoader);
      beanReader.loadBeanDefinitions(new UrlResource(this.configUrl));
      applicationContext.refresh();

      Map<String, Map<String, Object>> propertiesByName;
      if (applicationContext.containsBean("properties")) {
        propertiesByName = (Map<String, Map<String, Object>>)applicationContext
          .getBean("properties");
      } else {
        propertiesByName = Collections.emptyMap();
      }
      if (applicationContext.containsBeanDefinition("beanImports")) {
        this.beanImports = (List<String>)applicationContext.getBean("beanImports");
      }
      for (final String beanName : applicationContext.getBeanDefinitionNames()) {
        try {
          final BeanDefinition beanDefinition = applicationContext.getBeanDefinition(beanName);
          final String pluginClassName = beanDefinition.getBeanClassName();
          if (applicationContext.findAnnotationOnBean(beanName,
            BusinessApplicationPlugin.class) != null) {
            final String scope = beanDefinition.getScope();
            if (BeanDefinition.SCOPE_PROTOTYPE.equals(scope)) {

              final BusinessApplication businessApplication = loadBusinessApplication(
                businessApplicationsByName, this.name, pluginClassName, propertiesByName);
              if (businessApplication != null) {
                final String businessApplicationName = businessApplication.getName();
                initAppLogAppender(businessApplicationName);
                businessApplicationNames.add(businessApplicationName);
                businessApplicationsToBeanNames.put(businessApplication, beanName);
                final String componentName = "APP_" + businessApplicationName;
                final ConfigPropertyLoader propertyLoader = getConfigPropertyLoader();
                final String moduleName = getName();
                if (propertyLoader != null) {
                  final Map<String, Object> configProperties = propertyLoader
                    .getConfigProperties(moduleName, componentName);
                  if (configProperties != null) {
                    businessApplication.setProperties(configProperties);
                    for (final Entry<String, Object> entry : configProperties.entrySet()) {
                      final String propertyName = entry.getKey();
                      final Object propertyValue = entry.getValue();
                      try {
                        Property.setSimple(businessApplication, propertyName, propertyValue);
                      } catch (final Throwable t) {
                      }
                    }
                  }
                }
              }
            } else {
              addModuleError("Plugin bean scope " + scope + " != expected value prototype "
                + pluginClassName + " from " + this.configUrl);
            }
          } else if (beanName.equals("properties")) {

          } else if (!beanName.equals("beanImports") && !beanName.equals("name")
            && !beanName.equals("properties")) {
            addModuleError("Plugin spring file cannot have any non-plugin beans " + beanName
              + " class=" + pluginClassName + " from " + this.configUrl);
          }
        } catch (final Throwable e) {
          addModuleError("Error loading plugin " + beanName + " from " + this.configUrl, e);
        }
      }
      registerConfigPropertyBeans(this.name, applicationContext);
    } finally {
      applicationContext.close();
      this.log.info("End\tLoad plugin metadata\t" + this.configUrl);
    }
    if (isHasError()) {
      stop();
    } else {
      this.businessApplicationNames = new ArrayList<>(businessApplicationNames);
      if (this.startedDate == null) {
        this.startedDate = date;
      }
      this.started = true;
      this.businessApplicationsByName = businessApplicationsByName;
      this.businessApplicationsToBeanNames = businessApplicationsToBeanNames;
    }
  }

  protected void preLoadApplications() {

  }

  private void processExecute(final BusinessApplication businessApplication, final Method method) {
    boolean hasError = false;
    if (method.getReturnType().equals(Void.TYPE)) {
      if (method.getParameterTypes().length > 0) {
        hasError = true;
      } else if (!Modifier.isPublic(method.getModifiers())) {
        hasError = true;
      } else {
        businessApplication.setExecuteMethod(method);
      }
    } else {
      hasError = true;
    }
    if (hasError) {
      throw new IllegalArgumentException("Business Application " + businessApplication.getName()
        + " testExecuteMethod must match public void testExecute()");
    }
  }

  private void processParameter(final Class<?> pluginClass,
    final BusinessApplication businessApplication, final Method method) {
    final String methodName = method.getName();

    String descriptionUrl = null;
    final JobParameter jobParameterAnnotation = method.getAnnotation(JobParameter.class);
    if (jobParameterAnnotation != null) {
      final String jobDescriptionUrl = jobParameterAnnotation.descriptionUrl();
      if (Property.hasValue(jobDescriptionUrl)) {
        descriptionUrl = jobDescriptionUrl;
      }
    }
    final RequestParameter requestParameterAnnotation = method
      .getAnnotation(RequestParameter.class);
    if (requestParameterAnnotation != null) {
      final String requestDescriptionUrl = requestParameterAnnotation.descriptionUrl();
      if (Property.hasValue(requestDescriptionUrl)) {
        descriptionUrl = requestDescriptionUrl;
      }
    }
    final boolean requestParameter = requestParameterAnnotation != null;
    final boolean jobParameter = jobParameterAnnotation != null;
    if (requestParameter || jobParameter) {
      final Class<?>[] parameterTypes = method.getParameterTypes();
      final Class<?>[] standardMethodParameters = STANDARD_METHODS.get(methodName);
      if (standardMethodParameters == null) {
        if (methodName.startsWith("set") && parameterTypes.length == 1) {
          String description;
          int length;
          int scale;
          String units;
          String minValue;
          String maxValue;
          if (requestParameter) {
            description = requestParameterAnnotation.description();
            length = requestParameterAnnotation.length();
            scale = requestParameterAnnotation.scale();
            units = requestParameterAnnotation.units();
            minValue = requestParameterAnnotation.minValue();
            maxValue = requestParameterAnnotation.maxValue();
          } else {
            description = jobParameterAnnotation.description();
            length = jobParameterAnnotation.length();
            scale = jobParameterAnnotation.scale();
            units = jobParameterAnnotation.units();
            minValue = jobParameterAnnotation.minValue();
            maxValue = jobParameterAnnotation.maxValue();
          }
          final String parameterName = methodName.substring(3, 4).toLowerCase()
            + methodName.substring(4);
          final boolean required = method.getAnnotation(Required.class) != null;
          final AllowedValues allowedValuesMetadata = method.getAnnotation(AllowedValues.class);
          String[] allowedValues = {};
          if (allowedValuesMetadata != null) {
            allowedValues = allowedValuesMetadata.value();
          }

          final boolean perRequestInputData = businessApplication.isPerRequestInputData();
          final Class<?> parameterType = parameterTypes[0];
          if (perRequestInputData) {
            if (requestParameter) {
              throw new IllegalArgumentException(pluginClass.getName() + "." + method
                + " cannot be a RequestParameter, only setInputDataUrl(Url url) or setInputDataContentType(Url url) can be specified for per request input data plug-ins");
            }
          }

          final DataType dataType = DataTypes.getDataType(parameterType);
          if (dataType == null) {
            throw new IllegalArgumentException(pluginClass.getName() + "." + method.getName()
              + " has an unsupported return type " + parameterType);
          } else {
            int index = -1;
            if (jobParameterAnnotation != null) {
              final int jobParameterIndex = jobParameterAnnotation.index();
              if (jobParameterIndex != -1) {
                index = jobParameterIndex;
              }
            }
            if (index == -1 && requestParameterAnnotation != null) {
              final int requestParameterIndex = requestParameterAnnotation.index();
              if (requestParameterIndex != -1) {
                index = 100000 + requestParameterIndex;
              }
            }
            final FieldDefinition field = new FieldDefinition(parameterName, dataType, length,
              scale, required, description);
            field.setProperty("units", units);
            if (Property.hasValue(minValue)) {
              final Object value = minValue;
              field.setMinValue(dataType.toObject(value));
            }
            if (Property.hasValue(maxValue)) {
              final Object value = maxValue;
              field.setMaxValue(dataType.toObject(value));
            }
            field.setAllowedValues(Arrays.asList(allowedValues));
            field.setProperty("units", units);

            final DefaultValue defaultValueMetadata = method.getAnnotation(DefaultValue.class);
            if (defaultValueMetadata != null) {
              final String defaultValueString = defaultValueMetadata.value();
              final Object defaultValue = dataType.toObject(defaultValueString);
              field.setDefaultValue(defaultValue);
            }

            if (jobParameter) {
              field.setProperty(BusinessApplication.JOB_PARAMETER, true);
            }
            if (requestParameter) {
              field.setProperty(BusinessApplication.REQUEST_PARAMETER, true);
            }
            final GeometryConfiguration geometryConfiguration = pluginClass
              .getAnnotation(GeometryConfiguration.class);
            if (Geometry.class.isAssignableFrom(parameterType)
              || com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(parameterType)) {
              GeometryFactory geometryFactory = businessApplication.getGeometryFactory();
              boolean validateGeometry = businessApplication.isValidateGeometry();
              if (geometryConfiguration != null) {
                geometryFactory = getGeometryFactory(geometryFactory,
                  businessApplication.getName() + "." + methodName, geometryConfiguration);
                businessApplication.setGeometryFactory(geometryFactory);
                validateGeometry = geometryConfiguration.validate();
              }
              field.setProperty(FieldProperties.GEOMETRY_FACTORY, geometryFactory);
              field.setProperty(FieldProperties.VALIDATE_GEOMETRY, validateGeometry);
            } else if (geometryConfiguration != null) {
              throw new IllegalArgumentException(pluginClass.getName() + "." + method.getName()
                + " cannot have a geometry configuration as is not a geometry attribute");
            }
            if (descriptionUrl != null) {
              field.setProperty("descriptionUrl", descriptionUrl);
            }
            businessApplication.addRequestField(index, field, method);
          }
        } else {
          throw new IllegalArgumentException(pluginClass.getName() + "." + method.getName()
            + " has the " + RequestParameter.class.getName() + " or " + JobParameter.class.getName()
            + " annotation but is not a setXXX(value) method");
        }
      } else {
        checkStandardMethod(method, standardMethodParameters);
      }
    }
  }

  private void processResultAttribute(final Class<?> pluginClass,
    final BusinessApplication businessApplication, final Method method, final boolean resultList) {
    final String methodName = method.getName();

    final ResultAttribute resultAttributeMetaData = method.getAnnotation(ResultAttribute.class);
    if (methodName.equals("getCustomizationProperties")) {
      if (method.getParameterTypes().length == 0 && method.getReturnType() == Map.class
        && Modifier.isPublic(method.getModifiers())) {
        if (resultList) {
          businessApplication.setHasResultListCustomizationProperties(true);
        } else {
          businessApplication.setHasCustomizationProperties(true);
        }
      } else {
        throw new IllegalArgumentException(
          "Method must have signature public Map<String, Object> getCustomizationProperties() not "
            + method);
      }

    } else if (resultAttributeMetaData != null) {
      if (methodName.startsWith("get") && methodName.length() > 3
        || methodName.startsWith("is") && methodName.length() > 2) {
        final Class<?>[] parameterTypes = method.getParameterTypes();
        if (parameterTypes.length == 0) {
          final String fieldName = JavaBeanUtil.getPropertyName(methodName);
          final String description = resultAttributeMetaData.description();
          final Class<?> returnType = method.getReturnType();
          final DataType dataType;
          if (com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(returnType)) {
            final String className = Classes.className(returnType);
            dataType = DataTypes.getDataType(className);
          } else {
            dataType = DataTypes.getDataType(returnType);
          }
          if (dataType == null) {
            throw new IllegalArgumentException(pluginClass.getName() + "." + method.getName()
              + " has an unsupported return type " + returnType);
          } else {
            final int index = resultAttributeMetaData.index();
            final int length = resultAttributeMetaData.length();
            final int scale = resultAttributeMetaData.scale();
            final boolean required = method.getAnnotation(Required.class) != null;
            final FieldDefinition field = new FieldDefinition(fieldName, dataType, length, scale,
              required, description);
            final GeometryConfiguration geometryConfiguration = method
              .getAnnotation(GeometryConfiguration.class);
            if (Geometry.class.isAssignableFrom(returnType)
              || com.vividsolutions.jts.geom.Geometry.class.isAssignableFrom(returnType)) {
              GeometryFactory geometryFactory = businessApplication.getGeometryFactory();
              boolean validateGeometry = businessApplication.isValidateGeometry();
              if (geometryConfiguration != null) {
                geometryFactory = getGeometryFactory(geometryFactory,
                  businessApplication.getName() + "." + methodName, geometryConfiguration);
                businessApplication.setGeometryFactory(geometryFactory);
                validateGeometry = geometryConfiguration.validate();
              }
              field.setProperty(FieldProperties.GEOMETRY_FACTORY, geometryFactory);
              field.setProperty(FieldProperties.VALIDATE_GEOMETRY, validateGeometry);
            } else if (geometryConfiguration != null) {
              throw new IllegalArgumentException(pluginClass.getName() + "." + method.getName()
                + " cannot have a geometry configuration as is not a geometry attribute");
            }
            businessApplication.addResultField(index, field, method);
          }
        }
      } else {
        throw new IllegalArgumentException(pluginClass.getName() + "." + method.getName()
          + " has the " + ResultAttribute.class.getName()
          + " annotation but is not a getXXX() or isXXX() method");
      }
    }
  }

  private void processResultListMethod(final BusinessApplication businessApplication,
    final Method resultListMethod) {
    final String businessApplicationName = businessApplication.getName();
    RecordDefinition resultRecordDefinition = businessApplication.getResultRecordDefinition();
    if (resultRecordDefinition.getFieldCount() > 0) {
      throw new IllegalArgumentException("Business Application " + businessApplicationName
        + " may not have result fields and the annotation " + ResultList.class);
    }
    final String methodName = resultListMethod.getName();
    final String resultListProperty = JavaBeanUtil.getPropertyName(methodName);
    businessApplication.setResultListProperty(resultListProperty);
    try {
      final Class<?> resultClass = JavaBeanUtil.getTypeParameterClass(resultListMethod, List.class);
      for (final Method method : JavaBeanUtil.getMethods(resultClass)) {
        processResultAttribute(resultClass, businessApplication, method, true);
      }
      resultRecordDefinition = businessApplication.getResultRecordDefinition();
      if (resultRecordDefinition.getFieldCount() == 0) {
        throw new IllegalArgumentException("Business Application " + businessApplicationName
          + " result class " + resultClass.getName() + " must have result fields");
      }
    } catch (final IllegalArgumentException e) {
      throw new IllegalArgumentException(
        "Business Application " + businessApplicationName + " method ", e);
    }
  }

  private void processTestExecute(final BusinessApplication businessApplication,
    final Method method) {
    boolean hasError = false;
    if (method.getReturnType().equals(Void.TYPE)) {
      if (method.getParameterTypes().length > 0) {
        hasError = true;
      } else if (!Modifier.isPublic(method.getModifiers())) {
        hasError = true;
      } else {
        businessApplication.setTestExecuteMethod(method);
      }
    } else {
      hasError = true;
    }
    if (hasError) {
      throw new IllegalArgumentException("Business Application " + businessApplication.getName()
        + " testExecuteMethod must match public void testExecute()");
    }
  }

  private void registerConfigPropertyBeans(final String moduleName,
    final GenericApplicationContext applicationContext) {
    final Map<String, Object> configProperties = getConfigProperties(moduleName,
      "MODULE_BEAN_PROPERTY");

    if (!configProperties.isEmpty()) {
      final GenericBeanDefinition propertiesBeanDefinition = new GenericBeanDefinition();
      propertiesBeanDefinition.setBeanClass(AttributeMap.class);
      final ConstructorArgumentValues constructorArgumentValues = new ConstructorArgumentValues();
      constructorArgumentValues.addGenericArgumentValue(configProperties);
      propertiesBeanDefinition.setConstructorArgumentValues(constructorArgumentValues);
      final String beanName = BeanDefinitionReaderUtils.generateBeanName(propertiesBeanDefinition,
        applicationContext);
      applicationContext.registerBeanDefinition(beanName, propertiesBeanDefinition);
    }
  }

  @Override
  public void restart() {
    final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
    if (businessApplicationRegistry != null) {
      businessApplicationRegistry.restartModule(this.name);
    }
  }

  public void restartDo() {
    try {
      stopDo();
    } finally {
      startDo();
    }
  }

  public void setClassLoader(final ClassLoader classLoader) {
    this.classLoader = classLoader;
  }

  public void setConfigPropertyLoader(final ConfigPropertyLoader configPropertyLoader) {
    this.configPropertyLoader = configPropertyLoader;
  }

  protected void setConfigUrl(final URL configUrl) {
    this.configUrl = configUrl;
  }

  protected void setRemoteable(final boolean remoteable) {
    this.remoteable = remoteable;
  }

  public void setStartedDate(final Date date) {
    this.startedDate = date;
  }

  public void setStatus(final String status) {
    this.status = status;
  }

  @Override
  @PostConstruct
  public void start() {
    if (isEnabled()) {
      setStatus("Start Requested");
      final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
      if (businessApplicationRegistry != null) {
        businessApplicationRegistry.startModule(this.name);
      }
    }
  }

  public void startDo() {
    if (isEnabled()) {
      if (isStarted()) {
        setStatus(STARTED);
      } else {
        setStatus(STARTING);
        final StopWatch stopWatch = new StopWatch();
        stopWatch.start();
        initAppLogAppender(null);
        this.log.info("Start\tModule Start\tmoduleName=" + this.name);
        clearModuleError();
        try {
          initializeGroupPermissions();
          preLoadApplications();
          if (!isHasError()) {
            for (final String businessApplicationName : getBusinessApplicationNames()) {
              this.log.info("Found business application " + businessApplicationName + " from "
                + this.configUrl);
            }
            loadBusinessApplications();
            this.businessApplicationRegistry.clearModuleToAppCache();
            if (isHasError()) {
              this.businessApplicationRegistry.moduleEvent(this, ModuleEvent.START_FAILED);
            } else {
              setStatus(STARTED);
              this.businessApplicationRegistry.moduleEvent(this, ModuleEvent.START);
            }
          }
        } catch (final Throwable e) {
          addModuleError(e);
        }
        AppLogUtil.info(this.log, "End\tModule Start\tmoduleName=" + this.name, stopWatch);
      }
    } else {
      setStatus(DISABLED);
    }
  }

  @Override
  public void stop() {
    setStatus("Stop Requested");
    final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
    if (businessApplicationRegistry != null) {
      businessApplicationRegistry.stopModule(this.name);
    }
  }

  public void stopDo() {
    setStatus(STOPPING);
    final StopWatch stopWatch = new StopWatch();
    stopWatch.start();
    this.log.info("Start\tModule Stop\tmoduleName=" + this.name);
    this.started = false;
    this.applicationsLoaded = false;
    final GenericApplicationContext applicationContext = this.applicationContext;
    if (applicationContext != null && applicationContext.isActive()) {
      applicationContext.close();
    }
    final List<String> names = this.businessApplicationNames;
    this.applicationContext = null;
    this.businessApplicationsByName = Collections.emptyMap();
    this.businessApplicationsToBeanNames = Collections.emptyMap();
    this.businessApplicationNames = Collections.emptyList();
    this.permissionsByGroupName = null;
    this.groupNamesToDelete = null;
    this.businessApplicationRegistry.clearModuleToAppCache();
    for (final String businessApplicationName : names) {
      closeAppLogAppender(this.name + "." + businessApplicationName);
    }
    try {
      this.businessApplicationRegistry.moduleEvent(this, ModuleEvent.STOP, names);
    } finally {
      this.lastStartTime = getStartedTime();
      this.startedDate = null;
      AppLogUtil.info(this.log, "End\tModule Stop\tmoduleName=" + this.name, stopWatch);
      closeAppLogAppender(this.name);
      if (isEnabled()) {
        setStatus(STOPPED);
      } else {
        setStatus(DISABLED);
      }
    }
  }

  @Override
  public String toString() {
    return this.configUrl.toString();
  }

}
