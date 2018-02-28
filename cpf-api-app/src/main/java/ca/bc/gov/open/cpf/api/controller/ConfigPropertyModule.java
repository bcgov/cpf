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

import java.net.URL;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.scheduler.StatisticsService;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;
import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;
import ca.bc.gov.open.cpf.plugin.impl.module.ClassLoaderModule;
import ca.bc.gov.open.cpf.plugin.impl.module.ModuleLoader;

import com.revolsys.maven.MavenPom;
import com.revolsys.maven.MavenRepository;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;

public class ConfigPropertyModule extends ClassLoaderModule {
  private final MavenRepository mavenRepository;

  private String mavenModuleId;

  private final String configMavenModuleId;

  private final Set<String> excludeMavenIds;

  private final ConfigPropertyModuleLoader moduleLoader;

  private final CpfDataAccessObject dataAccessObject;

  public ConfigPropertyModule(final ConfigPropertyModuleLoader moduleLoader,
    final BusinessApplicationRegistry businessApplicationRegistry, final String moduleName,
    final MavenRepository mavenRepository, final String mavenModuleId,
    final Set<String> excludeMavenIds, final ConfigPropertyLoader configPropertyLoader) {
    super(businessApplicationRegistry, moduleName, "INFO");
    this.moduleLoader = moduleLoader;
    this.mavenRepository = mavenRepository;
    this.configMavenModuleId = mavenModuleId;
    if (mavenModuleId.endsWith("{cpfVersion}")) {
      final String cpfVersion = getClass().getPackage().getImplementationVersion();
      this.mavenModuleId = mavenModuleId.substring(0, mavenModuleId.length() - 12) + cpfVersion;
    } else {
      this.mavenModuleId = mavenModuleId;
    }
    this.excludeMavenIds = excludeMavenIds;
    this.dataAccessObject = moduleLoader.getDataAccessObject();
    setConfigPropertyLoader(configPropertyLoader);
    setRemoteable(true);
  }

  @Override
  public ClassLoader getClassLoader() {
    ClassLoader classLoader = super.getClassLoader();
    if (classLoader == null) {
      final Set<String> excludeIds = new HashSet<>(this.excludeMavenIds);
      for (final String excludeId : this.excludeMavenIds) {
        try {
          excludeIds.add(MavenPom.getGroupAndArtifactId(excludeId));
          final MavenPom pom = this.mavenRepository.getPom(excludeId);
          if (pom != null) {
            for (final String dependencyId : pom.getDependencyIds(excludeIds)) {
              excludeIds.add(MavenPom.getGroupAndArtifactId(dependencyId));
            }
          }
        } catch (final IllegalArgumentException e) {
        }
      }

      final MavenPom pom = this.mavenRepository.getPom(this.mavenModuleId);
      if (pom == null) {
        throw new IllegalArgumentException("Unable to find Maven module: " + this.mavenModuleId);
      } else {
        classLoader = pom.newClassLoader(excludeIds);
        setClassLoader(classLoader);
      }
    }
    return classLoader;
  }

  public String getConfigMavenModuleId() {
    return this.configMavenModuleId;
  }

  public String getMavenModuleId() {
    return this.mavenModuleId;
  }

  @Override
  public String getModuleDescriptor() {
    return this.mavenModuleId;
  }

  @Override
  public String getModuleType() {
    return "Maven";
  }

  @Override
  public boolean isGroupNameValid(final String groupName) {
    return groupName.startsWith(getName() + "_");
  }

  @Override
  protected void preLoadApplications() {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        this.moduleLoader.refreshConfigProperties(this);
        this.moduleLoader.refreshUserGroup(this, "_ADMIN", "Application administrator for ");
        this.moduleLoader.refreshUserGroup(this, "_SECURITY", "Security administrator for ");
        this.moduleLoader.refreshUserGroups(this);
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    }
  }

  public void setMavenModuleId(final String mavenModuleId) {
    this.mavenModuleId = mavenModuleId;
  }

  public void setModuleDescriptor(final String mavenModuleId) {
    this.mavenModuleId = mavenModuleId;
  }

  @Override
  public void startDo() {
    if (isEnabled()) {
      if (isStarted()) {
        setStatus(STARTED);
      } else {
        setStatus(STARTING);
        try (
          Transaction transaction = this.dataAccessObject
            .newTransaction(Propagation.REQUIRES_NEW)) {
          try {
            try {
              clearModuleError();

              final ClassLoader classLoader = getClassLoader();
              final List<URL> configUrls = ModuleLoader.getConfigUrls(classLoader,
                false);
              if (configUrls.size() == 1) {
                final URL configUrl = configUrls.get(0);
                setConfigUrl(configUrl);
                setClassLoader(classLoader);
              } else if (configUrls.isEmpty()) {
                addModuleError(
                  "No META-INF/ca.bc.gov.open.cpf.plugin.sf.xml resource found for Maven module");
              } else {
                addModuleError(
                  "Multiple META-INF/ca.bc.gov.open.cpf.plugin.sf.xml resources found for Maven module");
              }
              if (!isHasError()) {
                super.startDo();
              }
            } catch (final Throwable e) {
              transaction.setRollbackOnly();
              addModuleError(e);
            }
            if (isHasError()) {
              stopDo();
            } else {
              final StatisticsService statisticsService = this.moduleLoader.getStatisticsService();
              statisticsService.collateStatistics();
            }
          } catch (final Throwable e) {
            throw transaction.setRollbackOnly(e);
          }
        }
      }
    } else {
      setStatus(DISABLED);
    }
  }

  @Override
  public void stopDo() {
    if (isStarted()) {
      setStatus(STOPPING);
      try (
        Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
        try {
          final List<String> businessApplicationNames = getBusinessApplicationNames();
          setStartedDate(null);

          super.stopDo();
          setClassLoader(null);
          setConfigUrl(null);
          final StatisticsService statisticsService = this.moduleLoader.getStatisticsService();
          statisticsService.scheduleSaveStatistics(businessApplicationNames);
        } catch (final Throwable e) {
          throw transaction.setRollbackOnly(e);
        }
      }
    } else if (isEnabled()) {
      setStatus(STOPPED);
    } else {
      setStatus(DISABLED);
    }
  }

  @Override
  public String toString() {
    return getName();
  }
}
