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
package ca.bc.gov.open.cpf.api.worker;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;

import ca.bc.gov.open.cpf.plugin.impl.ConfigPropertyLoader;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.datatype.DataType;
import com.revolsys.datatype.DataTypes;
import com.revolsys.logging.Logs;
import com.revolsys.spring.config.BeanConfigurrer;
import com.revolsys.util.Property;
import com.revolsys.websocket.AsyncResult;
import com.revolsys.websocket.json.JsonAsyncSender;

public class WorkerConfigPropertyLoader extends BeanConfigurrer implements ConfigPropertyLoader {
  private final WorkerMessageHandler messageHandler;

  private final WorkerScheduler scheduler;

  public WorkerConfigPropertyLoader(final WorkerScheduler workerScheduler,
    final WorkerMessageHandler messageHandler) {
    this.scheduler = workerScheduler;
    this.messageHandler = messageHandler;
  }

  @Override
  public synchronized Map<String, Object> getConfigProperties(final String moduleName,
    final String componentName) {
    return getConfigProperties(this.getEnvironmentName(), moduleName, componentName);
  }

  @Override
  @SuppressWarnings({
    "unchecked"
  })
  public synchronized Map<String, Object> getConfigProperties(final String environmentName,
    final String moduleName, final String componentName) {
    try {
      final JsonAsyncSender messageSender = this.messageHandler.getMessageSender();
      if (messageSender != null) {
        final MapEx message = new LinkedHashMapEx("type", "moduleConfigLoad");
        message.put("moduleName", moduleName);
        message.put("environmentName", environmentName);
        message.put("componentName", componentName);
        return messageSender.sendAndWait(message, new AsyncResult<MapEx>() {
          @Override
          public <V> V getResult(final MapEx result) {
            final Map<String, Object> configProperties = new HashMap<>();
            final List<Map<String, Object>> configPropertyList = (List<Map<String, Object>>)result
              .get("properties");
            for (final Map<String, Object> configProperty : configPropertyList) {
              final String name = (String)configProperty.get("PROPERTY_NAME");
              if (Property.hasValue(name)) {
                final String stringValue = (String)configProperty.get("PROPERTY_VALUE");
                if (Property.hasValue(stringValue)) {
                  final String type = (String)configProperty.get("PROPERTY_VALUE_TYPE");
                  final DataType dataType = DataTypes.getDataType(type);
                  Object value = stringValue;
                  if (dataType != null) {
                    value = dataType.toObject(stringValue);
                  }
                  configProperties.put(name, value);
                } else {
                  configProperties.put(name, null);
                }
              }
            }
            return (V)configProperties;
          }
        });
      }
    } catch (final Throwable e) {
      Logs.error(this, "Unable to get config properties for " + moduleName, e);
    }
    return Collections.emptyMap();
  }

  private String getEnvironmentName() {
    return this.scheduler.getEnvironmentName();
  }

  @Override
  public void postProcessBeanFactory(final ConfigurableListableBeanFactory beanFactory)
    throws BeansException {
    try {
      final Map<String, Object> attributes = getConfigProperties(this.getEnvironmentName(),
        "CPF_WORKER", "GLOBAL");
      setAttributes(attributes);
      super.postProcessBeanFactory(beanFactory);
    } catch (final Throwable e) {
      Logs.error(this, "Unable to load config", e.getCause());
    }
  }
}
