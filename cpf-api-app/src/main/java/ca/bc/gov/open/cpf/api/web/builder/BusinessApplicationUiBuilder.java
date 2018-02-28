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
package ca.bc.gov.open.cpf.api.web.builder;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.commons.beanutils.PropertyUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.ConfigProperty;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.scheduler.BusinessApplicationStatistics;
import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

import com.revolsys.collection.list.Lists;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.io.PathName;
import com.revolsys.logging.Logs;
import com.revolsys.record.ArrayRecord;
import com.revolsys.record.Record;
import com.revolsys.record.io.ListRecordReader;
import com.revolsys.record.io.RecordReader;
import com.revolsys.record.schema.RecordDefinitionImpl;
import com.revolsys.ui.html.fields.CheckBoxField;
import com.revolsys.ui.html.fields.IntegerField;
import com.revolsys.ui.html.fields.SelectField;
import com.revolsys.ui.html.fields.SpelExpressionField;
import com.revolsys.ui.html.form.Form;
import com.revolsys.ui.html.serializer.key.BooleanImageKeySerializer;
import com.revolsys.ui.html.serializer.key.BulletListKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.view.ButtonsToolbarElement;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.ElementContainer;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.model.Menu;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.config.Page;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.utils.HttpServletUtils;
import com.revolsys.util.Property;

@Controller
public class BusinessApplicationUiBuilder extends CpfUiBuilder {

  public BusinessApplicationUiBuilder() {
    super("businessApplication", "Business Application", "Business Applications");
    setIdParameterName("businessApplicationName");
    setIdPropertyName("name");

    addPage(new Page("clientList", "Business Applications", "/ws/apps"));
    addPage(new Page("clientView", "/ws/apps/{businessApplicationName}/"));
    addPage(new Page("clientInstant", "/ws/apps/{businessApplicationName}/instant/"));
    addPage(new Page("clientSingle", "/ws/apps/{businessApplicationName}/single/"));
    addPage(new Page("clientMultiple", "/ws/apps/{businessApplicationName}/multiple/"));

    newView("clientList",
      Lists.newArray(new PageLinkKeySerializer("clientView", "title", "Name", "clientView") //
        .addParameterKey("businessApplicationName", "name"), "description"));
  }

  @RequestMapping("/ws/sample/input")
  @ResponseBody
  public RecordReader getSampleInputData() {
    final RecordDefinitionImpl recordDefinition = new RecordDefinitionImpl(
      PathName.newPathName("/Buffer"));
    final GeometryFactory factory = GeometryFactory.fixed2d(3005, 1000.0, 1000.0);
    recordDefinition.setGeometryFactory(factory);
    recordDefinition.addField("title", DataTypes.STRING);
    recordDefinition.addField("buffer", DataTypes.DOUBLE);
    recordDefinition.addField("geometry", DataTypes.GEOMETRY);

    final List<Record> objects = new ArrayList<>();

    final Record object1 = new ArrayRecord(recordDefinition);
    object1.setValue("title", "Buffered centroid of BC");
    object1.setValue("buffer", 10000);
    object1.setGeometryValue(factory.point(921100.281, 1076394.357));
    objects.add(object1);

    final Record object2 = new ArrayRecord(recordDefinition);
    object2.setValue("title", "Stanley Park");
    object2.setValue("buffer", 1000);
    object2.setGeometryValue(factory.point(1207714.288, 480508.637));
    objects.add(object2);

    final ListRecordReader reader = new ListRecordReader(recordDefinition, objects);
    return reader;
  }

  @RequestMapping("/ws/sample/result")
  @ResponseBody
  public RecordReader getSampleResultData() {
    final RecordDefinitionImpl recordDefinition = new RecordDefinitionImpl(
      PathName.newPathName("/Buffer"));
    final GeometryFactory factory = GeometryFactory.fixed2d(3005, 1000.0, 1000.0);
    recordDefinition.setGeometryFactory(factory);
    recordDefinition.addField("sequenceNumber", DataTypes.INT);
    recordDefinition.addField("resultNumber", DataTypes.INT);
    recordDefinition.addField("title", DataTypes.STRING);
    recordDefinition.addField("buffer", DataTypes.DOUBLE);
    recordDefinition.addField("geometry", DataTypes.GEOMETRY);

    final List<Record> objects = new ArrayList<>();

    final Record object1 = new ArrayRecord(recordDefinition);
    object1.setValue("sequenceNumber", 1);
    object1.setValue("resultNumber", 1);
    object1.setValue("title", "Buffered centroid of BC");
    object1.setValue("buffer", 10000);
    object1.setGeometryValue(factory.point(921100.281, 1076394.357).buffer(10000));
    objects.add(object1);

    final Record object2 = new ArrayRecord(recordDefinition);
    object2.setValue("sequenceNumber", 2);
    object2.setValue("resultNumber", 1);
    object2.setValue("title", "Stanley Park");
    object2.setValue("buffer", 1000);
    object2.setGeometryValue(factory.point(1207714.288, 480508.637).buffer(1000));
    objects.add(object2);

    final ListRecordReader reader = new ListRecordReader(recordDefinition, objects);
    return reader;
  }

  @Override
  protected void initFields() {
    super.initFields();
    addField(new SelectField("logLevel", true) //
      .addOption("ERROR") //
      .addOption("INFO") //
      .addOption("DEBUG"));
    addField(new CheckBoxField("testModeEnabled"));
    addField(new SpelExpressionField("batchModePermission", true));
    addField(new SpelExpressionField("instantModePermission", true));
    addField(new IntegerField("maxRequestsPerJob", true));
    addField(new IntegerField("maxConcurrentRequests", true));
    addField(new IntegerField("numRequestsPerWorker", true));
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    addKeySerializer(
      new PageLinkKeySerializer("module_name_link", "module.name", "Module", "view"));
    addKeySerializer(new PageLinkKeySerializer("name_link", "name", "Name", "moduleView") //
      .addParameterKey("businessApplicationName", "name") //
      .addParameterKey("moduleName", "module.name"));
    addKeySerializer(new BulletListKeySerializer("inputDataContentTypes"));
    addKeySerializer(new BulletListKeySerializer("resultDataContentTypes"));
    addKeySerializer(new BooleanImageKeySerializer("testModeEnabled"));
  }

  @RequestMapping(value = {
    "/admin/apps"
  }, title = "Business Applications", method = RequestMethod.GET, fieldNames = {
    "name_link", "module_name_link", "title",
  }, permission = "hasRole('ROLE_ADMIN') or hasRoleRegex('ROLE_ADMIN_MODULE_.*_ADMIN')")
  @ResponseBody
  public Object list(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException {
    checkAdminOrAnyModuleAdminExceptSecurity();
    HttpServletUtils.setAttribute("title", "Business Applications");
    final List<BusinessApplication> businessApplications = getBusinessApplications();
    final Map<String, Object> parameters = Collections.emptyMap();

    final ElementContainer table = newDataTable(request, "list", parameters, businessApplications);

    final TabElementContainer tabs = new TabElementContainer();
    tabs.add("Business Applications", table);
    return tabs;
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/edit"
  }, method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "logLevel", "testModeEnabled", "batchModePermission", "instantModePermission",
    "maxRequestsPerJob", "maxConcurrentRequests", "numRequestsPerWorker",
  }, title = "Edit Business Application {businessApplicationName}")
  @ResponseBody
  public Element moduleEdit(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("moduleName") String moduleName,
    final @PathVariable("businessApplicationName") String businessApplicationName)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    final BusinessApplicationRegistry businessApplicationRegistry = getBusinessApplicationRegistry();
    final BusinessApplication businessApplication = businessApplicationRegistry
      .getModuleBusinessApplication(moduleName, businessApplicationName);
    if (businessApplication != null) {
      final ConfigPropertyLoader configPropertyLoader = businessApplicationRegistry
        .getConfigPropertyLoader();
      final String componentName = "APP_" + businessApplicationName;
      final Map<String, Object> configProperties = configPropertyLoader
        .getConfigProperties(moduleName, componentName);
      final String pageName = "moduleEdit";
      final String viewName = "moduleView";

      final List<String> propertyNames = getKeyList(pageName);
      for (final String propertyName : propertyNames) {
        if (!configProperties.containsKey(propertyName)) {
          final Object value = Property.get(businessApplication, propertyName);
          configProperties.put(propertyName, value);
        }
      }

      final Form form = newTableForm(configProperties, pageName);
      form.initialize(request);

      if (form.isPosted() && form.isMainFormTask()) {
        if (form.isValid()) {
          final CpfDataAccessObject dataAccessObject = getDataAccessObject();
          final BusinessApplicationPlugin pluginAnnotation = businessApplication
            .getPluginAnnotation();
          for (final String propertyName : propertyNames) {
            final Object defaultValue = Property.get(pluginAnnotation, propertyName);
            final Object newValue = form.getValue(propertyName);
            final boolean equal = DataType.equal(defaultValue, newValue);

            Record configProperty = dataAccessObject.getConfigProperty(ConfigProperty.DEFAULT,
              moduleName, componentName, propertyName);
            if (configProperty == null) {
              if (!equal) {
                try {
                  final Class<?> propertyClass = PropertyUtils.getPropertyType(businessApplication,
                    propertyName);
                  final DataType valueType = DataTypes.getDataType(propertyClass);

                  configProperty = dataAccessObject.newConfigProperty(ConfigProperty.DEFAULT,
                    moduleName, componentName, propertyName, newValue, valueType);
                } catch (final Throwable e) {
                  Logs.error(this, "Unable to set property " + propertyName, e);
                }
              }
            } else {
              if (equal) {
                dataAccessObject.delete(configProperty);
              } else {
                dataAccessObject.setConfigPropertyValue(configProperty, newValue);
                dataAccessObject.write(configProperty);
              }
            }
            Property.setSimple(businessApplication, propertyName, newValue);
          }

          final Map<String, Object> parameters = new HashMap<>();

          final String url = getPageUrl(viewName, parameters);
          response.sendRedirect(url);
          return null;
        }
      }

      final Page page = getPage(pageName);

      final String title = page.getExpandedTitle();
      request.setAttribute("title", title);

      final Menu actionMenu = new Menu();
      addMenuItem(actionMenu, null, viewName, "Cancel", "_top").addProperty("buttonClass",
        "btn-danger");
      addMenuItem(actionMenu, null, pageName, "Revert to Saved", "_top").addProperty("buttonClass",
        "btn-warning");
      final String name = form.getName();
      actionMenu.addMenuItem(new Menu("Save", "javascript:$('#" + name + "').submit()")
        .addProperty("buttonClass", "btn-primary"));

      final ButtonsToolbarElement buttonsToolbar = new ButtonsToolbarElement(actionMenu);
      final ElementContainer view = new ElementContainer(form, buttonsToolbar);
      final TabElementContainer tabs = new TabElementContainer();
      tabs.add(title, view);
      return tabs;
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps"
  }, title = "Business Applications", method = RequestMethod.GET,
      permission = "hasRole('ROLE_ADMIN')  or hasRole('ROLE_ADMIN_MODULE_' + #moduleName + '_ADMIN')",
      fieldNames = {
        "name_link", "module_name_link", "title",
      })
  @ResponseBody
  public Object moduleList(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("moduleName") String moduleName) throws IOException {
    final Module module = getModule(request, moduleName);
    checkAdminOrModuleAdmin(moduleName);
    return newDataTableHandlerOrRedirect(request, "moduleList", module::getBusinessApplications,
      Module.class, "view");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}"
  }, title = "Business Application {businessApplicationName}", method = RequestMethod.GET,
      fieldNames = {
        "name", "module_name_link", "title", "descriptionUrl", "logLevel", "testModeEnabled",
        "batchModePermission", "instantModePermission", "geometryFactory", "validateGeometry",
        "perRequestInputData", "inputDataContentTypes", "hasGeometryRequestAttribute",
        "perRequestResultData", "resultDataContentTypes", "hasCustomizationProperties",
        "resultListProperty", "hasResultListCustomizationProperties", "hasGeometryResultAttribute",
        "maxRequestsPerJob", "maxConcurrentRequests", "numRequestsPerWorker",
      })
  @ResponseBody
  public Element moduleView(final HttpServletRequest request, final HttpServletResponse response,
    final @PathVariable("moduleName") String moduleName,
    final @PathVariable("businessApplicationName") String businessApplicationName)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    final BusinessApplication businessApplication = getBusinessApplicationRegistry()
      .getModuleBusinessApplication(moduleName, businessApplicationName);
    if (businessApplication != null) {
      final TabElementContainer tabs = new TabElementContainer();
      addObjectViewPage(tabs, businessApplication, "module");

      final Map<String, Object> parameters = new HashMap<>();
      parameters.put("serverSide", Boolean.FALSE);

      addTabDataTable(tabs, BusinessApplicationStatistics.class.getName(), "moduleAppList",
        parameters);

      parameters.put("serverSide", Boolean.TRUE);
      addTabDataTable(tabs, ConfigProperty.CONFIG_PROPERTY, "moduleAppList", parameters);
      addTabDataTable(tabs, BatchJob.BATCH_JOB, "moduleAppList", parameters);

      return tabs;
    }
    throw new PageNotFoundException();
  }
}
