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

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;

import com.revolsys.beans.PropertyChangeSupport;
import com.revolsys.beans.PropertyChangeSupportProxy;
import com.revolsys.jdbc.io.DataSourceImpl;
import com.revolsys.logging.Logs;
import com.revolsys.util.Property;

public class CpfConfig implements PropertyChangeSupportProxy {
  private String baseUrl = "http://localhost/pub/cpf";

  private String secureBaseUrl = "https://localhost/pub/cpf/secure";

  private String internalWebServiceUrl = "https://localhost/pub/cpf";

  @Resource(name = "cpfDataSource")
  private DataSourceImpl dataSource;

  private int preProcessPoolSize = 10;

  private int postProcessPoolSize = 10;

  private int schedulerPoolSize = 10;

  private int groupResultPoolSize = 10;

  private final PropertyChangeSupport propertyChangeSupport = new PropertyChangeSupport(this);

  public String getBaseUrl() {
    return this.baseUrl;
  }

  public int getDatabaseConnectionPoolSize() {
    return this.dataSource.getMaxTotal();
  }

  public int getGroupResultPoolSize() {
    return this.groupResultPoolSize;
  }

  public String getInternalWebServiceUrl() {
    return this.internalWebServiceUrl;
  }

  public int getPostProcessPoolSize() {
    return this.postProcessPoolSize;
  }

  public int getPreProcessPoolSize() {
    return this.preProcessPoolSize;
  }

  @Override
  public PropertyChangeSupport getPropertyChangeSupport() {
    return this.propertyChangeSupport;
  }

  public int getSchedulerPoolSize() {
    return this.schedulerPoolSize;
  }

  public String getSecureBaseUrl() {
    return this.secureBaseUrl;
  }

  @PostConstruct
  public void init() {
    final String sql = "SELECT PROPERTY_NAME, PROPERTY_VALUE FROM CPF.CPF_CONFIG_PROPERTIES WHERE ENVIRONMENT_NAME = 'default' AND MODULE_NAME = 'CPF_TUNING' AND COMPONENT_NAME = 'GLOBAL'";
    try (
      Connection connection = this.dataSource.getConnection();
      PreparedStatement statement = connection.prepareStatement(sql);
      ResultSet resultSet = statement.executeQuery()) {
      while (resultSet.next()) {
        final String propertyName = resultSet.getString(1);
        final String propertyValue = resultSet.getString(2);
        Property.setSimple(this, propertyName, propertyValue);
      }
    } catch (final Throwable e) {
      Logs.error(this, "Unable to load configuration", e);
    }
  }

  public void setBaseUrl(final String baseUrl) {
    this.baseUrl = baseUrl;
  }

  public void setDatabaseConnectionPoolSize(final int poolSize) {
    this.dataSource.setMaxTotal(poolSize);
  }

  public void setGroupResultPoolSize(final int groupResultPoolSize) {
    if (groupResultPoolSize < 1) {
      throw new IllegalArgumentException(
        "groupResultPoolSize must be > 1 not " + groupResultPoolSize);
    }
    final int oldValue = this.groupResultPoolSize;
    this.groupResultPoolSize = groupResultPoolSize;
    this.propertyChangeSupport.firePropertyChange("groupResultPoolSize", oldValue,
      groupResultPoolSize);
  }

  public void setInternalWebServiceUrl(final String internalWebServiceUrl) {
    this.internalWebServiceUrl = internalWebServiceUrl;
  }

  public void setPostProcessPoolSize(final int postProcessPoolSize) {
    if (postProcessPoolSize < 1) {
      throw new IllegalArgumentException(
        "postProcessPoolSize must be > 1 not " + postProcessPoolSize);
    }
    final int oldValue = this.postProcessPoolSize;
    this.postProcessPoolSize = postProcessPoolSize;
    this.propertyChangeSupport.firePropertyChange("postProcessPoolSize", oldValue,
      postProcessPoolSize);
  }

  public void setPreProcessPoolSize(final int preProcessPoolSize) {
    if (preProcessPoolSize < 1) {
      throw new IllegalArgumentException(
        "preProcessPoolSize must be > 1 not " + preProcessPoolSize);
    }
    final int oldValue = this.preProcessPoolSize;
    this.preProcessPoolSize = preProcessPoolSize;
    this.propertyChangeSupport.firePropertyChange("preProcessPoolSize", oldValue,
      preProcessPoolSize);
  }

  public void setSchedulerPoolSize(final int schedulerPoolSize) {
    if (schedulerPoolSize < 1) {
      throw new IllegalArgumentException("schedulerPoolSize must be > 1 not " + schedulerPoolSize);
    }
    final int oldValue = this.schedulerPoolSize;
    this.schedulerPoolSize = schedulerPoolSize;
    this.propertyChangeSupport.firePropertyChange("schedulerPoolSize", oldValue, schedulerPoolSize);
  }

  public void setSecureBaseUrl(final String secureBaseUrl) {
    this.secureBaseUrl = secureBaseUrl;
  }
}
