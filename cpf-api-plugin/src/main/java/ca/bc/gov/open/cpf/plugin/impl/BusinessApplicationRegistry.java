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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;

import javax.annotation.PreDestroy;

import org.springframework.context.ApplicationListener;
import org.springframework.context.event.ContextRefreshedEvent;

import ca.bc.gov.open.cpf.plugin.impl.geometry.JtsGeometryDataType;
import ca.bc.gov.open.cpf.plugin.impl.module.ClassLoaderModule;
import ca.bc.gov.open.cpf.plugin.impl.module.Module;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleControlProcess;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEvent;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleEventListener;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleLoader;

import com.revolsys.collection.list.Lists;
import com.revolsys.comparator.IgnoreCaseStringComparator;
import com.revolsys.logging.Logs;
import com.revolsys.parallel.channel.Channel;
import com.revolsys.parallel.channel.store.Buffer;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.GeometryCollection;
import com.vividsolutions.jts.geom.LineString;
import com.vividsolutions.jts.geom.LinearRing;
import com.vividsolutions.jts.geom.MultiLineString;
import com.vividsolutions.jts.geom.MultiPoint;
import com.vividsolutions.jts.geom.MultiPolygon;
import com.vividsolutions.jts.geom.Point;
import com.vividsolutions.jts.geom.Polygon;

public final class BusinessApplicationRegistry
  implements ApplicationListener<ContextRefreshedEvent> {

  static {
    new JtsGeometryDataType("JtsGeometry", Geometry.class);
    new JtsGeometryDataType("JtsGeometryCollection", GeometryCollection.class);
    new JtsGeometryDataType("JtsPoint", Point.class);
    new JtsGeometryDataType("JtsMultiPoint", MultiPoint.class);
    new JtsGeometryDataType("JtsLineString", LineString.class);
    new JtsGeometryDataType("JtsLinearRing", LinearRing.class);
    new JtsGeometryDataType("JtsMultiLineString", MultiLineString.class);
    new JtsGeometryDataType("JtsPolygon", Polygon.class);
    new JtsGeometryDataType("JtsMultiPolygon", MultiPolygon.class);
  }

  private final Map<String, Module> modulesByName = new TreeMap<>();

  private Map<String, String> moduleNamesByBusinessApplicationName;

  private List<ModuleLoader> moduleLoaders = new ArrayList<>();

  private ConfigPropertyLoader configPropertyLoader;

  private final Set<ModuleEventListener> listeners = new LinkedHashSet<>();

  private File logDirectory;

  private File appLogDirectory;

  private Channel<Map<String, Object>> moduleControlChannel = new Channel<>("moduleControlChannel",
    new Buffer<Map<String, Object>>(10000));

  private Thread moduleControlThread;

  private boolean useModuleControlThread = true;

  private String environmentId = "master";

  public BusinessApplicationRegistry() {
    this(true);
  }

  public BusinessApplicationRegistry(final boolean useModuleControlThread) {
    this(useModuleControlThread, new ModuleLoader[0]);
  }

  public BusinessApplicationRegistry(final boolean useModuleControlThread,
    final ModuleLoader... moduleLoader) {
    this.useModuleControlThread = useModuleControlThread;
    if (useModuleControlThread) {
      this.moduleControlChannel.writeConnect();
      final ModuleControlProcess moduleControlProcess = new ModuleControlProcess(this,
        this.moduleControlChannel);
      this.moduleControlThread = new Thread(moduleControlProcess, "ModuleControl");
      this.moduleControlThread.setDaemon(true);
      this.moduleControlThread.start();
    }
    setModuleLoaders(Arrays.asList(moduleLoader));
    refreshModules();
  }

  public synchronized void addModule(final Module module) {
    clearModuleToAppCache();
    final String name = module.getName();
    final Module currentModule = this.modulesByName.get(name);
    if (currentModule != module) {
      if (currentModule == null) {
        this.modulesByName.put(name, module);
      } else {
        module.clearModuleError();
        module.addModuleError("Module with the same name is already loaded: " + name);
      }
    }
  }

  public void addModuleEventListener(final ModuleEventListener listener) {
    if (listener != null) {
      this.listeners.add(listener);
    }
  }

  public void clearModuleToAppCache() {
    this.moduleNamesByBusinessApplicationName = null;
  }

  @SuppressWarnings("deprecation")
  @PreDestroy
  public void destroy() {
    try {
      if (this.moduleControlChannel != null) {
        this.moduleControlChannel.writeDisconnect();
      }
      if (this.moduleControlThread != null) {
        this.moduleControlThread.interrupt();
      }

      this.useModuleControlThread = false;
      final List<Module> modules = getModules();
      for (final Module module : modules) {
        try {
          module.stop();
        } catch (final Throwable e) {
          Logs.error(this, "Unable to stop " + module.getName(), e);
        }
      }

    } finally {
      if (this.moduleControlThread != null) {
        final long maxWait = System.currentTimeMillis() + 5000;
        while (this.moduleControlThread.isAlive() && System.currentTimeMillis() < maxWait) {
          this.moduleControlThread.stop();
        }
      }
      this.configPropertyLoader = null;
      this.listeners.clear();
      this.moduleControlChannel = null;
      this.moduleControlThread = null;
      this.moduleLoaders.clear();
      this.modulesByName.clear();
    }
  }

  public File getAppLogDirectory() {
    return this.appLogDirectory;
  }

  public BusinessApplication getBusinessApplication(String businessApplicationName) {
    if (businessApplicationName == null) {
      return null;
    } else {
      final int colonIndex = businessApplicationName.lastIndexOf(':');
      if (colonIndex != -1) {
        businessApplicationName = businessApplicationName.substring(0, colonIndex - 1);
      }
      final Module module = getModuleForBusinessApplication(businessApplicationName);
      if (module == null) {
        return null;
      } else {
        return module.getBusinessApplication(businessApplicationName);
      }
    }
  }

  public BusinessApplication getBusinessApplication(final String businessApplicationName,
    final String businessApplicationVersion) {
    final Module module = getModuleForBusinessApplication(businessApplicationName);
    if (module == null) {
      return null;
    } else {
      return module.getBusinessApplication(businessApplicationName);
    }
  }

  public synchronized List<String> getBusinessApplicationNames() {
    final List<String> names = new ArrayList<>();
    for (final Module module : this.modulesByName.values()) {
      if (module.isEnabled()) {
        final List<String> businessApplicationNames = module.getBusinessApplicationNames();
        if (businessApplicationNames != null) {
          names.addAll(businessApplicationNames);
        }
      }
    }
    Collections.sort(names, new IgnoreCaseStringComparator());
    return names;
  }

  public PluginAdaptor getBusinessApplicationPlugin(final BusinessApplication businessApplication) {
    if (businessApplication == null) {
      return null;
    } else {
      final Module module = businessApplication.getModule();
      if (module == null) {
        return null;
      } else {
        return module.getBusinessApplicationPluginAdaptor(businessApplication, null, null);
      }
    }
  }

  public PluginAdaptor getBusinessApplicationPlugin(String businessApplicationName) {
    String businessApplicationVersion = "CURRENT";
    final int colonIndex = businessApplicationName.lastIndexOf(':');
    if (colonIndex != -1) {
      businessApplicationName = businessApplicationName.substring(0, colonIndex - 1);
      businessApplicationVersion = businessApplicationName.substring(colonIndex + 1);
    }
    return getBusinessApplicationPlugin(businessApplicationName, businessApplicationVersion);
  }

  public PluginAdaptor getBusinessApplicationPlugin(final String businessApplicationName,
    final String businessApplicationVersion) {
    final Module module = getModuleForBusinessApplication(businessApplicationName);
    if (module == null) {
      return null;
    } else {
      return module.getBusinessApplicationPluginAdaptor(businessApplicationName, null, null);
    }
  }

  public List<BusinessApplication> getBusinessApplications() {
    final List<BusinessApplication> businessApplications = new ArrayList<>();
    final List<String> businessApplicationNames = getBusinessApplicationNames();
    for (final String businessApplicationName : businessApplicationNames) {
      final BusinessApplication businessApplication = getBusinessApplication(
        businessApplicationName);
      if (businessApplication != null) {
        businessApplications.add(businessApplication);
      }
    }
    return businessApplications;
  }

  public ConfigPropertyLoader getConfigPropertyLoader() {
    return this.configPropertyLoader;
  }

  public String getEnvironmentId() {
    return this.environmentId;
  }

  public File getLogDirectory() {
    return this.logDirectory;
  }

  public synchronized Module getModule(final String moduleName) {
    if (moduleName == null) {
      return null;
    } else {
      final Module module = this.modulesByName.get(moduleName);
      return module;
    }
  }

  public BusinessApplication getModuleBusinessApplication(final String moduleName,
    final String businessApplicationName) {
    final Module module = getModule(moduleName);
    if (module != null) {
      return module.getBusinessApplication(businessApplicationName);
    }
    return null;
  }

  private synchronized Module getModuleForBusinessApplication(
    final String businessApplicationName) {
    if (this.moduleNamesByBusinessApplicationName == null) {
      this.moduleNamesByBusinessApplicationName = new HashMap<>();
      final Map<BusinessApplication, Module> businessApplicationModuleMap = new TreeMap<>();
      for (final Module module : this.modulesByName.values()) {
        for (final BusinessApplication application : module.getBusinessApplications()) {
          businessApplicationModuleMap.put(application, module);
        }
      }

      for (final Entry<BusinessApplication, Module> entry : businessApplicationModuleMap
        .entrySet()) {
        final BusinessApplication businessApplication = entry.getKey();
        final Module module = entry.getValue();
        final String name = businessApplication.getName();
        final String moduleName = module.getName();

        this.moduleNamesByBusinessApplicationName.put(name, moduleName);
      }
    }
    final String moduleName = this.moduleNamesByBusinessApplicationName
      .get(businessApplicationName);
    if (moduleName == null) {
      return null;
    } else {
      return getModule(moduleName);
    }
  }

  public List<ModuleLoader> getModuleLoaders() {
    return this.moduleLoaders;
  }

  public List<String> getModuleNames() {
    return new ArrayList<>(this.modulesByName.keySet());
  }

  public synchronized List<Module> getModules() {
    final Collection<Module> modules = this.modulesByName.values();
    return new ArrayList<>(modules);
  }

  public boolean hasBusinessApplication(final String moduleName,
    final String businessApplicationName) {
    final Module module = getModule(moduleName);
    if (module != null) {
      return module.hasBusinessApplication(businessApplicationName);
    }
    return false;
  }

  public boolean hasModule(final String moduleName) {
    return getModule(moduleName) != null;
  }

  public void moduleEvent(final Module module, final String action) {
    final ModuleEvent event = new ModuleEvent(module, action);
    for (final ModuleEventListener listener : this.listeners) {
      try {
        listener.moduleChanged(event);
      } catch (final Throwable t) {
        Logs.error(BusinessApplicationRegistry.class, "Error invoking listener", t);
      }
    }
  }

  public void moduleEvent(final Module module, final String action,
    final List<String> businessApplicationNames) {
    final ModuleEvent event = new ModuleEvent(module, action);
    event.setBusinessApplicationNames(businessApplicationNames);
    for (final ModuleEventListener listener : this.listeners) {
      try {
        listener.moduleChanged(event);
      } catch (final Throwable t) {
        Logs.error(BusinessApplicationRegistry.class, "Error invoking listener", t);
      }
    }
  }

  @Override
  public void onApplicationEvent(final ContextRefreshedEvent event) {
    refreshModules();
  }

  public void refreshModules() {
    for (final ModuleLoader moduleLoader : this.moduleLoaders) {
      moduleLoader.setBusinessApplicationRegistry(this);
      moduleLoader.refreshModules();
    }
  }

  public void removeModuleEventListener(final ModuleEventListener listener) {
    if (listener != null) {
      this.listeners.remove(listener);
    }
  }

  public void restartModule(final String moduleName) {
    if (this.useModuleControlThread) {
      final HashMap<String, Object> parameters = new HashMap<>();
      parameters.put("moduleName", moduleName);
      parameters.put("action", "restart");
      this.moduleControlChannel.write(parameters);
    } else {
      final ClassLoaderModule module = (ClassLoaderModule)getModule(moduleName);
      module.restartDo();
    }
  }

  public void setAppLogDirectory(final File appLogDirectory) {
    this.appLogDirectory = appLogDirectory;
  }

  public void setConfigPropertyLoader(final ConfigPropertyLoader configPropertyLoader) {
    this.configPropertyLoader = configPropertyLoader;
  }

  public void setEnvironmentId(final String environmentId) {
    this.environmentId = environmentId;
  }

  public void setModuleLoaders(final Collection<ModuleLoader> moduleLoaders) {
    this.moduleLoaders = Lists.toArray(moduleLoaders);
  }

  public void startModule(final String moduleName) {
    if (this.useModuleControlThread) {
      final HashMap<String, Object> parameters = new HashMap<>();
      parameters.put("moduleName", moduleName);
      parameters.put("action", "start");
      this.moduleControlChannel.write(parameters);
    } else {
      final ClassLoaderModule module = (ClassLoaderModule)getModule(moduleName);
      if (module != null) {
        module.startDo();
      }
    }
  }

  public void stopModule(final String moduleName) {
    if (this.useModuleControlThread) {
      final HashMap<String, Object> parameters = new HashMap<>();
      parameters.put("moduleName", moduleName);
      parameters.put("action", "stop");
      this.moduleControlChannel.write(parameters);
    } else {
      final ClassLoaderModule module = (ClassLoaderModule)getModule(moduleName);
      if (module != null) {
        module.stopDo();
      }
    }
  }

  public synchronized void unloadModule(final Module module) {
    Logs.debug(BusinessApplicationRegistry.class, "Unloading module " + module.toString());
    clearModuleToAppCache();
    final String moduleName = module.getName();
    if (module == this.modulesByName.get(moduleName)) {
      this.modulesByName.remove(moduleName);
    }
    module.destroy();
  }

}
