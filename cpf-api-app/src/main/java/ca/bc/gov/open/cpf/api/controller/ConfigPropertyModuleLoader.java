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
package ca.bc.gov.open.cpf.api.controller;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import ca.bc.gov.open.cpf.api.domain.ConfigProperty;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.domain.UserGroup;
import ca.bc.gov.open.cpf.api.scheduler.BatchJobService;
import ca.bc.gov.open.cpf.api.scheduler.StatisticsService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleLoader;
import ca.bc.gov.open.cpf.plugin.impl.module.ResourcePermission;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.datatype.DataTypes;
import com.revolsys.io.map.MapReader;
import com.revolsys.logging.Logs;
import com.revolsys.maven.MavenRepository;
import com.revolsys.record.Record;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.spring.resource.ClassPathResource;
import com.revolsys.spring.resource.InputStreamResource;
import com.revolsys.spring.resource.NoSuchResourceException;
import com.revolsys.spring.resource.Resource;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Property;

public class ConfigPropertyModuleLoader implements ModuleLoader {
  private static final String ACTION = "action";

  private static final String DELETE = "delete";

  private static final String ENABLED = "enabled";

  private static final String MAVEN_MODULE_ID = "mavenModuleId";

  private BatchJobService batchJobService;

  private BusinessApplicationRegistry businessApplicationRegistry;

  private final String cpfVersion = getClass().getPackage().getImplementationVersion();

  private CpfDataAccessObject dataAccessObject;

  private final Set<String> excludeMavenIds = new LinkedHashSet<>();

  private MavenRepository mavenRepository;

  private final Map<String, ConfigPropertyModule> modulesByName = new LinkedHashMap<>();

  private StatisticsService statisticsService;

  private final MapEx defaultMavenModuleIds = new LinkedHashMapEx();

  public ConfigPropertyModuleLoader() {
    this.excludeMavenIds.add("ca.bc.gov.open.cpf:cpf-api-app:" + this.cpfVersion);
    this.excludeMavenIds.add("ca.bc.gov.open.cpf:cpf-api-plugin:" + this.cpfVersion);
    this.excludeMavenIds.add("ca.bc.gov.open.cpf:cpf-api-client:" + this.cpfVersion);
  }

  public void deleteModule(final Module module) {
    final String moduleName = module.getName();
    this.modulesByName.remove(moduleName);
    this.businessApplicationRegistry.unloadModule(module);
    this.dataAccessObject.deleteUserGroupsForModule("ADMIN_MODULE_" + moduleName);
    this.dataAccessObject.deleteConfigPropertiesForModule(moduleName);
  }

  public BatchJobService getBatchJobService() {
    return this.batchJobService;
  }

  @Override
  public BusinessApplicationRegistry getBusinessApplicationRegistry() {
    return this.businessApplicationRegistry;
  }

  public Record getConfigProperty(final ConfigPropertyModule module,
    final Map<String, Object> property) {
    final Record configProperty = this.dataAccessObject.newRecord(ConfigProperty.CONFIG_PROPERTY);
    String environmentName = (String)property.get("environmentName");
    if (!Property.hasValue(environmentName)) {
      environmentName = ConfigProperty.DEFAULT;
    }
    configProperty.setValue(ConfigProperty.ENVIRONMENT_NAME, environmentName);
    final String moduleName = module.getName();
    configProperty.setValue(ConfigProperty.MODULE_NAME, moduleName);
    configProperty.setValue(ConfigProperty.COMPONENT_NAME, ConfigProperty.MODULE_BEAN_PROPERTY);

    final String name = (String)property.get("name");
    if (!Property.hasValue(name)) {
      module.addModuleError("Config property must have a name " + property);
    }
    configProperty.setValue(ConfigProperty.PROPERTY_NAME, name);

    String type = (String)property.get("type");
    if (!Property.hasValue(type)) {
      type = "string";
    }
    configProperty.setValue(ConfigProperty.PROPERTY_VALUE_TYPE, type);

    final Object value = property.get("value");
    this.dataAccessObject.setConfigPropertyValue(configProperty, value);
    return configProperty;
  }

  public CpfDataAccessObject getDataAccessObject() {
    return this.dataAccessObject;
  }

  public Map<String, List<String>> getDeletePropertiesByEnvironment(
    final ConfigPropertyModule module, final List<MapEx> pluginProperties) {
    final Map<String, List<String>> deletePropertiesByEnvironment = new HashMap<>();
    for (final Map<String, Object> property : pluginProperties) {
      final String action = (String)property.get(ACTION);
      if (DELETE.equals(action)) {
        String environmentName = (String)property.get("environmentName");
        if (!Property.hasValue(environmentName)) {
          environmentName = ConfigProperty.DEFAULT;
        }
        List<String> propertyNames = deletePropertiesByEnvironment.get(environmentName);
        if (propertyNames == null) {
          propertyNames = new ArrayList<>();
          deletePropertiesByEnvironment.put(environmentName, propertyNames);
        }
        final String propertyName = (String)property.get("name");
        propertyNames.add(propertyName);
      }
    }
    return deletePropertiesByEnvironment;
  }

  public MavenRepository getMavenRepository() {
    return this.mavenRepository;
  }

  public Map<String, Map<String, Record>> getPropertiesByEnvironment(
    final ConfigPropertyModule module, final List<MapEx> pluginProperties) {
    final Map<String, Map<String, Record>> propertiesByEnvironment = new HashMap<>();
    for (final Map<String, Object> property : pluginProperties) {
      final String action = (String)property.get(ACTION);
      if (!DELETE.equals(action)) {
        final Record configProperty = getConfigProperty(module, property);
        final String environmentName = configProperty.getValue(ConfigProperty.ENVIRONMENT_NAME);
        Map<String, Record> propertiesByName = propertiesByEnvironment.get(environmentName);
        if (propertiesByName == null) {
          propertiesByName = new HashMap<>();
          propertiesByEnvironment.put(environmentName, propertiesByName);
        }
        final String propertyName = configProperty.getValue(ConfigProperty.PROPERTY_NAME);
        propertiesByName.put(propertyName, configProperty);
      }
    }
    return propertiesByEnvironment;
  }

  public StatisticsService getStatisticsService() {
    return this.statisticsService;
  }

  private void initConfig() {
    this.defaultMavenModuleIds.clear();
    final Resource configResource = new ClassPathResource("/cpfModules.json");
    try {
      final MapEx config = Json.toMap(configResource);
      final List<MapEx> modules = config.getValue("modules", Collections.emptyList());
      for (final MapEx moduleConfig : modules) {
        final String name = moduleConfig.getString("name");
        final String mavenModuleId = moduleConfig.getString("mavenModuleId");
        if (Property.hasValuesAll(name, mavenModuleId)) {
          this.defaultMavenModuleIds.put(name, mavenModuleId);
        }
      }
    } catch (final NoSuchResourceException e) {
      // Ignore missing resource
    } catch (final Throwable e) {
      Logs.error(this, "Error reading config:" + configResource, e);
    }
  }

  private boolean isConfigPropertyDeleted(
    final Map<String, List<String>> deletePropertiesByEnvironment, final String environmentName,
    final String propertyName) {
    boolean delete = false;
    final List<String> deleteProperties = deletePropertiesByEnvironment.get(environmentName);
    if (deleteProperties != null) {
      delete = deleteProperties.contains(propertyName);
    }
    if (!delete && !ConfigProperty.DEFAULT.equals(environmentName)) {
      delete = isConfigPropertyDeleted(deletePropertiesByEnvironment, ConfigProperty.DEFAULT,
        propertyName);
    }
    return delete;
  }

  private boolean isEnabledInConfig(final String moduleName, final String mavenModuleId) {
    final Record enabledProperty = this.dataAccessObject.getConfigProperty(ConfigProperty.DEFAULT,
      moduleName, ConfigProperty.MODULE_CONFIG, ENABLED);
    if (enabledProperty == null) {
      setMavenModuleConfigProperties(moduleName, mavenModuleId, true);
      return true;
    } else {
      final String value = enabledProperty.getValue(ConfigProperty.PROPERTY_VALUE);
      final boolean enabled = !"false".equalsIgnoreCase(value);
      return enabled;
    }
  }

  protected void refreshConfigProperties(final ConfigPropertyModule module) {
    try {
      final String moduleName = module.getName();
      final ClassLoader classLoader = module.getClassLoader();
      final InputStream in = classLoader
        .getResourceAsStream("META-INF/ca.bc.gov.open.cpf.plugin.ConfigProperties.json");
      if (in != null) {
        final InputStreamResource resource = new InputStreamResource("properties.json", in);
        final MapReader reader = MapReader.newMapReader(resource);
        final List<MapEx> pluginProperties = reader.toList();

        final Map<String, Map<String, Record>> propertiesByEnvironment = getPropertiesByEnvironment(
          module, pluginProperties);
        final Map<String, List<String>> deletePropertiesByEnvironment = getDeletePropertiesByEnvironment(
          module, pluginProperties);

        // Exclude and properties that already exist in the config database
        for (final Record configProperty : this.dataAccessObject
          .getConfigPropertiesForComponent(moduleName, ConfigProperty.MODULE_BEAN_PROPERTY)) {
          final String environmentName = configProperty.getValue(ConfigProperty.ENVIRONMENT_NAME);
          final String propertyName = configProperty.getValue(ConfigProperty.PROPERTY_NAME);
          final Map<String, Record> properties = propertiesByEnvironment.get(environmentName);
          if (properties != null) {
            properties.remove(propertyName);
          }
          if (isConfigPropertyDeleted(deletePropertiesByEnvironment, environmentName,
            propertyName)) {
            this.dataAccessObject.delete(configProperty);
          }
        }

        // Add config properties for new properties
        for (final Map<String, Record> properties : propertiesByEnvironment.values()) {
          for (final Record property : properties.values()) {
            this.dataAccessObject.write(property);
          }
        }
      }
    } catch (final Throwable t) {
      module.addModuleError(t);
    }
  }

  private void refreshMavenModule(final String moduleName, final String mavenModuleId) {
    ConfigPropertyModule module = this.modulesByName.get(moduleName);
    try {
      if (module != null && !module.getMavenModuleId().equals(mavenModuleId)) {
        this.businessApplicationRegistry.unloadModule(module);
        module = null;
      }
      final boolean newModule = module == null;
      final boolean enabled = isEnabledInConfig(moduleName, mavenModuleId);
      if (newModule) {
        final ConfigPropertyLoader configPropertyLoader = this.businessApplicationRegistry
          .getConfigPropertyLoader();
        module = new ConfigPropertyModule(this, this.businessApplicationRegistry, moduleName,
          this.mavenRepository, mavenModuleId, this.excludeMavenIds, configPropertyLoader);
        this.modulesByName.put(moduleName, module);
      }
      if (newModule) {
        this.businessApplicationRegistry.addModule(module);
      }
      if (enabled) {
        module.enable();
      } else {
        module.disable();
      }
    } catch (final Throwable e) {
      if (module == null) {
        Logs.error(this, "Unable to load module " + moduleName, e);
      } else {
        module.addModuleError(e);
      }
    }
  }

  private void refreshModule(final Map<String, Module> modulesToDelete,
    final Map<String, Module> modulesToUnload, final Map<String, String> modulesToRefresh,
    final Set<String> allModuleNames, final String moduleName, final String mavenModuleId) {
    allModuleNames.add(moduleName);

    final ConfigPropertyModule module = this.modulesByName.get(moduleName);
    if (module != null && mavenModuleId.equals(module.getMavenModuleId())) {
      modulesToUnload.remove(moduleName);
    }
    modulesToDelete.remove(moduleName);
    modulesToRefresh.put(moduleName, mavenModuleId);
  }

  @Override
  public void refreshModules() {
    synchronized (this.modulesByName) {
      initConfig();
      try (
        Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
        try {
          final Map<String, Module> modulesToDelete = new HashMap<>(this.modulesByName);
          final Map<String, Module> modulesToUnload = new HashMap<>(this.modulesByName);

          final Map<String, String> modulesToRefresh = new HashMap<>();

          final Set<String> allModuleNames = new HashSet<>();
          for (final Record property : this.dataAccessObject.getConfigPropertiesForAllModules(
            ConfigProperty.DEFAULT, ConfigProperty.MODULE_CONFIG, MAVEN_MODULE_ID)) {
            final String moduleName = property.getValue(ConfigProperty.MODULE_NAME);
            String mavenModuleId = property.getValue(ConfigProperty.PROPERTY_VALUE);
            final String defaultMavenModuleId = this.defaultMavenModuleIds.getString(moduleName);
            if (defaultMavenModuleId != null) {
              if (!defaultMavenModuleId.equals(mavenModuleId)) {
                mavenModuleId = defaultMavenModuleId;
                setMavenModuleConfigProperties(moduleName, mavenModuleId, null);
              }
            }
            refreshModule(modulesToDelete, modulesToUnload, modulesToRefresh, allModuleNames,
              moduleName, mavenModuleId);
          }

          for (final String moduleName : this.defaultMavenModuleIds.keySet()) {
            if (!allModuleNames.contains(moduleName)) {
              final String mavenModuleId = this.defaultMavenModuleIds.getString(moduleName);
              setMavenModuleConfigProperties(moduleName, mavenModuleId, true);
              refreshModule(modulesToDelete, modulesToUnload, modulesToRefresh, allModuleNames,
                moduleName, mavenModuleId);
            }
          }

          for (final Module module : modulesToDelete.values()) {
            deleteModule(module);
          }
          for (final Module module : modulesToUnload.values()) {
            this.businessApplicationRegistry.unloadModule(module);
          }
          for (final Entry<String, String> entry : modulesToRefresh.entrySet()) {
            final String moduleName = entry.getKey();
            final String mavenModuleId = entry.getValue();
            refreshMavenModule(moduleName, mavenModuleId);
          }
        } catch (final Throwable e) {
          throw transaction.setRollbackOnly(e);
        }
      }
    }
  }

  protected void refreshUserGroup(final ConfigPropertyModule module, final String groupNameSuffix,
    final String descriptionSuffix) {
    final String moduleName = module.getName();
    final String adminModuleName = "ADMIN_MODULE_" + moduleName;
    final String groupName = adminModuleName + groupNameSuffix;
    final String description = descriptionSuffix + moduleName;
    this.dataAccessObject.newUserGroup(adminModuleName, groupName, description);
  }

  protected void refreshUserGroups(final ConfigPropertyModule module) {
    try {
      final String moduleName = module.getName();

      final Map<String, Set<ResourcePermission>> permissionsByGroupName = module
        .getPermissionsByGroupName();
      final Set<String> groupNamesToDelete = module.getGroupNamesToDelete();

      if (!module.isHasError()) {
        final Set<String> existingGroupNames = new HashSet<>();
        // Exclude or delete user groups that already exist in the database
        for (final Record userGroup : this.dataAccessObject.getUserGroupsForModule(moduleName)) {
          final String groupName = userGroup.getValue(UserGroup.USER_GROUP_NAME);
          if (groupName.startsWith(moduleName.toUpperCase())) {
            if (groupNamesToDelete != null && groupNamesToDelete.contains(groupName)) {
              this.dataAccessObject.delete(userGroup);
            } else {
              existingGroupNames.add(groupName);
            }
          }
        }

        if (permissionsByGroupName != null) {
          for (final Entry<String, Set<ResourcePermission>> groupPermissions : permissionsByGroupName
            .entrySet()) {
            final String groupName = groupPermissions.getKey();
            if (groupName.startsWith(moduleName + "_") && !existingGroupNames.contains(groupName)) {

              this.dataAccessObject.newUserGroup(moduleName, groupName, null);
            }
            final Record group = this.dataAccessObject.getUserGroup(groupName);
            if (group == null) {
              module.getLog().error("Group not found " + groupName);
            } else {
              final Set<ResourcePermission> newPermissions = new HashSet<>(
                groupPermissions.getValue());
              if (newPermissions != null && !newPermissions.isEmpty()) {
                for (final Record userGroupPermission : this.dataAccessObject
                  .getUserGroupPermissions(group, moduleName)) {
                  final ResourcePermission permission = new ResourcePermission(userGroupPermission);
                  newPermissions.remove(permission);
                }
                for (final ResourcePermission newPermission : newPermissions) {

                  this.dataAccessObject.newUserGroupPermission(group, moduleName, newPermission);
                }
              }
            }
          }
        }
      }
    } catch (final Throwable t) {
      module.addModuleError(t);
    }
  }

  @javax.annotation.Resource(name = "batchJobService")
  public void setBatchJobService(final BatchJobService batchJobService) {
    this.batchJobService = batchJobService;
  }

  @javax.annotation.Resource(name = "statisticsService")
  public void setBatchJobService(final StatisticsService statisticsService) {
    this.statisticsService = statisticsService;
  }

  @Override
  public void setBusinessApplicationRegistry(
    final BusinessApplicationRegistry businessApplicationRegistry) {
    if (this.businessApplicationRegistry != businessApplicationRegistry) {
      businessApplicationRegistry.addModuleEventListener(this.batchJobService);
      this.businessApplicationRegistry = businessApplicationRegistry;
    }
  }

  public void setDataAccessObject(final CpfDataAccessObject dataAccessObject) {
    this.dataAccessObject = dataAccessObject;
  }

  public void setMavenModuleConfigProperties(final String moduleName, final String mavenModuleId,
    final Boolean enabled) {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        final String environmentName = ConfigProperty.DEFAULT;
        final String componentName = ConfigProperty.MODULE_CONFIG;
        final String propertyName = MAVEN_MODULE_ID;
        Record moduleIdProperty = this.dataAccessObject.getConfigProperty(environmentName,
          moduleName, componentName, propertyName);
        if (moduleIdProperty == null) {
          moduleIdProperty = this.dataAccessObject.newConfigProperty(environmentName, moduleName,
            componentName, propertyName, mavenModuleId, DataTypes.STRING);
        } else {
          this.dataAccessObject.setConfigPropertyValue(moduleIdProperty, mavenModuleId);
          this.dataAccessObject.write(moduleIdProperty);
        }
        if (enabled != null) {
          Record moduleEnabledProperty = this.dataAccessObject.getConfigProperty(environmentName,
            moduleName, componentName, ENABLED);
          if (moduleEnabledProperty == null) {
            moduleEnabledProperty = this.dataAccessObject.newConfigProperty(environmentName,
              moduleName, componentName, ENABLED, enabled, DataTypes.BOOLEAN);
          } else {
            this.dataAccessObject.setConfigPropertyValue(moduleEnabledProperty, enabled);
            this.dataAccessObject.write(moduleEnabledProperty);
          }
        }
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    }
  }

  public void setMavenRepository(final MavenRepository mavenRepository) {
    this.mavenRepository = mavenRepository;
  }
}
