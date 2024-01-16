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
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import ca.bc.gov.open.cpf.api.domain.ConfigProperty;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;

import com.revolsys.record.Record;
import com.revolsys.record.query.Condition;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.ui.html.fields.Field;
import com.revolsys.ui.html.fields.SelectField;
import com.revolsys.ui.html.fields.TextAreaField;
import com.revolsys.ui.html.fields.TextField;
import com.revolsys.ui.html.form.Form;
import com.revolsys.ui.html.serializer.key.ActionFormKeySerializer;
import com.revolsys.ui.html.serializer.key.MultipleKeySerializer;
import com.revolsys.ui.html.serializer.key.PageLinkKeySerializer;
import com.revolsys.ui.html.serializer.key.StringKeySerializer;
import com.revolsys.ui.html.view.Element;
import com.revolsys.ui.html.view.ElementContainer;
import com.revolsys.ui.html.view.TabElementContainer;
import com.revolsys.ui.web.annotation.ColumnSortOrder;
import com.revolsys.ui.web.annotation.RequestMapping;
import com.revolsys.ui.web.exception.PageNotFoundException;
import com.revolsys.ui.web.utils.HttpServletUtils;

@Controller
public class ConfigPropertyUiBuilder extends CpfUiBuilder {

  private static final List<String> GLOBAL_MODULE_NAMES = Arrays.asList("CPF", "CPF_WORKER");

  private final List<String> INTERNAL_APP_PROPERTY_NAMES = Arrays.asList("maxConcurrentRequests",
    "numRequestsPerWorker", "maxRequestsPerJob", "logLevel", "batchModePermission",
    "instantModePermission", "testModeEnabled");

  public ConfigPropertyUiBuilder() {
    super("configProperty", ConfigProperty.CONFIG_PROPERTY, "CONFIG_PROPERTY_ID",
      "Config WebProperty", "Config Properties");
  }

  @RequestMapping(value = {
    "/admin/configProperties/add"
  }, title = "Add Config Property", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "ENVIRONMENT_NAME", "MODULE_NAME", "PROPERTY_NAME", "PROPERTY_VALUE", "PROPERTY_VALUE_TYPE"
  })
  @ResponseBody
  public Element add(final HttpServletRequest request, final HttpServletResponse response)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    final Map<String, Object> defaultValues = new HashMap<>();
    defaultValues.put(ConfigProperty.COMPONENT_NAME, ConfigProperty.GLOBAL);

    final Element result = super.newObjectAddPage(defaultValues, null, "preInsert");
    return result;
  }

  @RequestMapping(value = {
    "/admin/configProperties/{configPropertyId}/delete"
  }, title = "Delete Config Property {configPropertyId}", method = RequestMethod.POST)
  public void delete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);

    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null) {
      final String moduleName = configProperty.getValue(ConfigProperty.MODULE_NAME);
      if (GLOBAL_MODULE_NAMES.contains(moduleName)) {
        final String componentName = configProperty.getValue(ConfigProperty.COMPONENT_NAME);
        if (componentName.equals(ConfigProperty.GLOBAL)) {
          final RecordStore recordStore = getRecordStore();
          recordStore.deleteRecord(configProperty);
          redirectPage("list");
          return;
        }
      }
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/configProperties/{configPropertyId}/edit"
  }, title = "Edit Config Property {configPropertyId}", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "ENVIRONMENT_NAME", "MODULE_NAME", "PROPERTY_NAME", "PROPERTY_VALUE", "PROPERTY_VALUE_TYPE"
  })
  @ResponseBody
  public Element edit(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkHasAnyRole(ADMIN);
    final Record configProperty = loadObject(configPropertyId);
    return super.newObjectEditPage(configProperty, null);
  }

  private String getAppComponentName(final String businessApplicationName) {
    final String componentName = "APP_" + businessApplicationName;
    return componentName;
  }

  @Override
  protected void initFields() {
    super.initFields();
    addField(new TextField("ENVIRONMENT_NAME", 70, 255, true));
    addField(new TextField("PROPERTY_NAME", 70, 255, true));
    addField(new TextAreaField("PROPERTY_VALUE", 70, 5, 4000, true));

    addField(new SelectField("MODULE_NAME", true) //
      .addOption("CPF") //
      .addOption("CPF_WORKER", "CPF_WORKER") //
    );

    addField(new SelectField("PROPERTY_VALUE_TYPE", true) //
      .addOption("string", "String") //
      .addOption("boolean", "Boolean") //
      .addOption("long", "Long") //
      .addOption("int", "Int") //
      .addOption("double", "Double") //
      .addOption("float", "Float") //
      .addOption("short", "Short") //
      .addOption("byte", "Byte") //
      .addOption("decimal", "Big Decimal") //
      .addOption("integer", "Big Integer") //
      .addOption("QName", "Qualified name") //
      .addOption("anyURI", "URI") //
      .addOption("date", "Date (YYYY-MM-DD)") //
      .addOption("dateTime", "Date + Time (YYYY-MM-DDTHH:MM:SS)") //
      .addOption("Geometry", "WKT Geometry") //
      .addOption("Point", "WKT Point") //
      .addOption("LineString", "WKT LineString") //
      .addOption("Polygon", "WKT Polygon") //
      .addOption("GeometryCollection", "WKT Geometry Collection") //
      .addOption("MultiPoint", "WKT Multi-Point") //
      .addOption("MultiLineString", "WKT Multi-LineString") //
      .addOption("MultiPolygon", "WKT Multi-Polygon") //
    );
  }

  @Override
  protected void initSerializers() {
    super.initSerializers();

    addKeySerializer(
      new PageLinkKeySerializer("CONFIG_PROPERTY_ID_VIEW", "CONFIG_PROPERTY_ID", "ID", "view"));

    addKeySerializer(new PageLinkKeySerializer("CONFIG_PROPERTY_ID_MODULE_VIEW",
      "CONFIG_PROPERTY_ID", "ID", "moduleView"));

    addKeySerializer(new PageLinkKeySerializer("CONFIG_PROPERTY_ID_MODULE_APP_VIEW",
      "CONFIG_PROPERTY_ID", "ID", "moduleAppView"));

    addKeySerializer(new StringKeySerializer("ENVIRONMENT_NAME", "Environment Name"));
    addKeySerializer(new StringKeySerializer("MODULE_NAME", "Module Name"));
    addKeySerializer(new StringKeySerializer("PROPERTY_NAME", "Property Name"));
    addKeySerializer(new StringKeySerializer("PROPERTY_VALUE", "Value"));
    addKeySerializer(new StringKeySerializer("PROPERTY_VALUE_TYPE", "Value Type"));

    final MultipleKeySerializer cpfActions = new MultipleKeySerializer("cpfActions", "Actions");
    cpfActions.addSerializer(new ActionFormKeySerializer("delete", "Delete", "fa fa-trash")//
      .addParameterName("configPropertyId", "CONFIG_PROPERTY_ID"));
    addKeySerializer(cpfActions);

    final MultipleKeySerializer moduleActions = new MultipleKeySerializer("moduleActions",
      "Actions");
    moduleActions.addSerializer(new ActionFormKeySerializer("moduleDelete", "Delete", "fa fa-trash")//
      .addParameterName("configPropertyId", "CONFIG_PROPERTY_ID"));
    addKeySerializer(moduleActions);

    final MultipleKeySerializer moduleAppActions = new MultipleKeySerializer("moduleAppActions",
      "Actions");
    moduleAppActions
      .addSerializer(new ActionFormKeySerializer("moduleAppDelete", "Delete", "fa fa-trash")//
        .addParameterName("configPropertyId", "CONFIG_PROPERTY_ID"));
    addKeySerializer(moduleAppActions);
  }

  @RequestMapping(value = {
    "/admin/configProperties"
  }, title = "Config Properties", method = RequestMethod.GET, fieldNames = {
    "CONFIG_PROPERTY_ID_VIEW", "ENVIRONMENT_NAME", "MODULE_NAME", "PROPERTY_NAME", "PROPERTY_VALUE",
    "PROPERTY_VALUE_TYPE", "cpfActions"
  }, columnSortOrder = {
    @ColumnSortOrder("ENVIRONMENT_NAME"), @ColumnSortOrder("MODULE_NAME"),
    @ColumnSortOrder("PROPERTY_NAME")
  }, permission = "hasRole('ROLE_ADMIN')")
  @ResponseBody
  public Object list(final HttpServletRequest request, final HttpServletResponse response) {
    checkHasAnyRole(ADMIN);
    HttpServletUtils.setAttribute("title", "Config Properties");
    final Map<String, Object> parameters = new LinkedHashMap<>();

    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(ConfigProperty.MODULE_NAME, GLOBAL_MODULE_NAMES);
    parameters.put("filter", filter);

    return newDataTableHandler(request, "list", parameters);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/configProperties/add"
  }, title = "Module Add Config Property", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "ENVIRONMENT_NAME", "PROPERTY_NAME", "PROPERTY_VALUE", "PROPERTY_VALUE_TYPE"
  })
  @ResponseBody
  public Element moduleAdd(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName) throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);
    final Map<String, Object> defaultValues = new HashMap<>();
    defaultValues.put(ConfigProperty.MODULE_NAME, moduleName);
    defaultValues.put(ConfigProperty.COMPONENT_NAME, ConfigProperty.MODULE_BEAN_PROPERTY);

    return newObjectAddPage(defaultValues, "module", "preInsertModule");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/configProperties/add"
  }, title = "Businness Application Add Config Property", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "ENVIRONMENT_NAME", "PROPERTY_NAME", "PROPERTY_VALUE", "PROPERTY_VALUE_TYPE"
  })
  @ResponseBody
  public Element moduleAppAdd(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final String componentName = getAppComponentName(businessApplicationName);
    final Map<String, Object> defaultValues = new HashMap<>();
    defaultValues.put(ConfigProperty.MODULE_NAME, moduleName);
    defaultValues.put(ConfigProperty.COMPONENT_NAME, componentName);

    return newObjectAddPage(defaultValues, "moduleApp", "preInsertModule");
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/configProperties/{configPropertyId}/delete"
  }, title = "Businness Application Delete Config Property {configPropertyId}",
      method = RequestMethod.POST)
  public void moduleAppDelete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final String componentName = getAppComponentName(businessApplicationName);
    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null
      && configProperty.getValue(ConfigProperty.MODULE_NAME).equals(moduleName)
      && configProperty.getValue(ConfigProperty.COMPONENT_NAME).equals(componentName)) {
      getRecordStore().deleteRecord(configProperty);
      redirectPage("moduleAppList");
      return;
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/configProperties/{configPropertyId}/edit"
  }, title = "Businness Application Edit Config Property {configPropertyId}", method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "ENVIRONMENT_NAME", "PROPERTY_NAME", "PROPERTY_VALUE", "PROPERTY_VALUE_TYPE"
  })
  @ResponseBody
  public Element moduleAppEdit(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final String componentName = getAppComponentName(businessApplicationName);
    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null
      && configProperty.getValue(ConfigProperty.MODULE_NAME).equals(moduleName)
      && configProperty.getValue(ConfigProperty.COMPONENT_NAME).equals(componentName)) {
      return newObjectEditPage(configProperty, "moduleApp");
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/configProperties"
  }, title = "Config Properties", method = RequestMethod.GET, fieldNames = {
    "CONFIG_PROPERTY_ID_MODULE_APP_VIEW", "ENVIRONMENT_NAME", "PROPERTY_NAME", "PROPERTY_VALUE",
    "PROPERTY_VALUE_TYPE", "moduleAppActions"
  }, permission = "hasRole('ROLE_ADMIN')  or hasRole('ROLE_ADMIN_MODULE_' + #moduleName + '_ADMIN')")
  @ResponseBody
  public Object moduleAppList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName)
    throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final String componentName = getAppComponentName(businessApplicationName);
    final Map<String, Object> parameters = new LinkedHashMap<>();

    final Condition where = Q.and(Q.equal(ConfigProperty.MODULE_NAME, moduleName),
      Q.equal(ConfigProperty.COMPONENT_NAME, componentName),
      Q.not(Q.in(ConfigProperty.PROPERTY_NAME, this.INTERNAL_APP_PROPERTY_NAMES)));

    final Query query = new Query(getTableName(), where);
    parameters.put("query", query);

    return newDataTableHandlerOrRedirect(request, response, "moduleAppList", Module.class, "view",
      parameters);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/apps/{businessApplicationName}/configProperties/{configPropertyId}"
  }, title = "Businness Application Config Property {configPropertyId}", method = RequestMethod.GET,
      fieldNames = {
        "CONFIG_PROPERTY_ID", "ENVIRONMENT_NAME", "MODULE_NAME", "PROPERTY_NAME", "PROPERTY_VALUE",
        "PROPERTY_VALUE_TYPE", "WHO_CREATED", "WHEN_CREATED", "WHO_UPDATED", "WHEN_UPDATED",
        "moduleAppActions"
      })
  @ResponseBody
  public Element moduleAppView(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("businessApplicationName") final String businessApplicationName,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final String componentName = getAppComponentName(businessApplicationName);
    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null
      && configProperty.getValue(ConfigProperty.MODULE_NAME).equals(moduleName)
      && configProperty.getValue(ConfigProperty.COMPONENT_NAME).equals(componentName)) {
      final TabElementContainer tabs = new TabElementContainer();
      addObjectViewPage(tabs, configProperty, "moduleApp");
      return tabs;
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/configProperties/{configPropertyId}/delete"
  }, title = "Module Delete Config Property {configPropertyId}", method = RequestMethod.POST)
  public void moduleDelete(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null
      && configProperty.getValue(ConfigProperty.MODULE_NAME).equals(moduleName)
      && configProperty.getValue(ConfigProperty.COMPONENT_NAME)
        .equals(ConfigProperty.MODULE_BEAN_PROPERTY)) {
      getRecordStore().deleteRecord(configProperty);
      redirectPage("moduleList");
      return;
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/configProperties/{configPropertyId}/edit"
  }, method = {
    RequestMethod.GET, RequestMethod.POST
  }, fieldNames = {
    "ENVIRONMENT_NAME", "PROPERTY_NAME", "PROPERTY_VALUE", "PROPERTY_VALUE_TYPE"
  }, title = "Module Edit Config Property {configPropertyId}")
  @ResponseBody
  public Element moduleEdit(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null
      && configProperty.getValue(ConfigProperty.MODULE_NAME).equals(moduleName)
      && configProperty.getValue(ConfigProperty.COMPONENT_NAME)
        .equals(ConfigProperty.MODULE_BEAN_PROPERTY)) {
      return newObjectEditPage(configProperty, "module");
    }
    throw new PageNotFoundException();
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/configProperties"
  }, title = "Config Properties", method = RequestMethod.GET, fieldNames = {
    "CONFIG_PROPERTY_ID_MODULE_VIEW", "ENVIRONMENT_NAME", "PROPERTY_NAME", "PROPERTY_VALUE",
    "PROPERTY_VALUE_TYPE", "moduleActions"
  }, columnSortOrder = {
    @ColumnSortOrder("ENVIRONMENT_NAME"), @ColumnSortOrder("PROPERTY_NAME")
  }, permission = "hasRole('ROLE_ADMIN')  or hasRole('ROLE_ADMIN_MODULE_' + #moduleName + '_ADMIN')")
  @ResponseBody
  public Object moduleList(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName) throws IOException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Map<String, Object> parameters = new LinkedHashMap<>();

    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(ConfigProperty.MODULE_NAME, moduleName);
    filter.put(ConfigProperty.COMPONENT_NAME, ConfigProperty.MODULE_BEAN_PROPERTY);
    parameters.put("filter", filter);

    return newDataTableHandlerOrRedirect(request, response, "moduleList", Module.class, "view",
      parameters);
  }

  @RequestMapping(value = {
    "/admin/modules/{moduleName}/configProperties/{configPropertyId}"
  }, title = "Module Config Property {configPropertyId}", method = RequestMethod.GET, fieldNames = {
    "CONFIG_PROPERTY_ID", "ENVIRONMENT_NAME", "MODULE_NAME", "PROPERTY_NAME", "PROPERTY_VALUE",
    "PROPERTY_VALUE_TYPE", "WHO_CREATED", "WHEN_CREATED", "WHO_UPDATED", "WHEN_UPDATED",
    "moduleActions"
  })
  @ResponseBody
  public Element moduleView(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("moduleName") final String moduleName,
    @PathVariable("configPropertyId") final Integer configPropertyId)
    throws IOException, ServletException {
    checkAdminOrModuleAdmin(moduleName);
    hasModule(request, moduleName);

    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null
      && configProperty.getValue(ConfigProperty.MODULE_NAME).equals(moduleName)
      && configProperty.getValue(ConfigProperty.COMPONENT_NAME)
        .equals(ConfigProperty.MODULE_BEAN_PROPERTY)) {
      final TabElementContainer tabs = new TabElementContainer();
      addObjectViewPage(tabs, configProperty, "module");
      return tabs;
    }
    throw new PageNotFoundException();
  }

  @Override
  public boolean preInsert(final Form form, final Record configProperty) {
    final Field field = form.getField(ConfigProperty.MODULE_NAME);
    final String moduleName = field.getValue();
    if (GLOBAL_MODULE_NAMES.contains(moduleName)) {
      return true;
    } else {
      field.addValidationError("Module name must be either CPF or CPF_WORKER");
      return false;
    }
  }

  public boolean preInsertModule(final Form form, final Record object) {
    return true;
  }

  @RequestMapping(value = {
    "/admin/configProperties/{configPropertyId}"
  }, title = "Config Property {configPropertyId}", method = RequestMethod.GET, fieldNames = {
    "CONFIG_PROPERTY_ID", "ENVIRONMENT_NAME", "MODULE_NAME", "PROPERTY_NAME", "PROPERTY_VALUE",
    "PROPERTY_VALUE_TYPE", "WHO_CREATED", "WHEN_CREATED", "WHO_UPDATED", "WHEN_UPDATED",
    "cpfActions"
  })
  @ResponseBody
  public ElementContainer view(final HttpServletRequest request, final HttpServletResponse response,
    @PathVariable("configPropertyId") final Integer configPropertyId) {
    checkHasAnyRole(ADMIN);
    final Record configProperty = loadObject(configPropertyId);
    if (configProperty != null) {
      final String moduleName = configProperty.getValue(ConfigProperty.MODULE_NAME);
      if (GLOBAL_MODULE_NAMES.contains(moduleName)) {
        final String componentName = configProperty.getValue(ConfigProperty.COMPONENT_NAME);
        if (componentName.equals(ConfigProperty.GLOBAL)) {
          final TabElementContainer tabs = new TabElementContainer();
          addObjectViewPage(tabs, configProperty, null);
          return tabs;
        }
      }
    }
    throw new PageNotFoundException();
  }

}
