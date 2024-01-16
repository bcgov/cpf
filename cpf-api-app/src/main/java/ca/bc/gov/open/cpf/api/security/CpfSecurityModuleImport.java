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
package ca.bc.gov.open.cpf.api.security;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import jakarta.servlet.Filter;

import org.springframework.beans.MutablePropertyValues;
import org.springframework.beans.PropertyValue;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionRegistry;
import org.springframework.beans.factory.support.ManagedMap;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.security.config.BeanIds;

import com.revolsys.spring.ModuleImport;

public class CpfSecurityModuleImport extends ModuleImport {
  private List<String> paths = new ArrayList<>();

  @SuppressWarnings("unchecked")
  @Override
  protected void afterPostProcessBeanDefinitionRegistry(final BeanDefinitionRegistry registry) {
    final GenericApplicationContext beanFactory = getApplicationContext(registry);

    final BeanDefinition filterChainProxy = registry.getBeanDefinition(BeanIds.FILTER_CHAIN_PROXY);
    if (filterChainProxy == null) {
      registerTargetBeanDefinition(registry, beanFactory, BeanIds.FILTER_CHAIN_PROXY,
        BeanIds.FILTER_CHAIN_PROXY);
      registerTargetBeanDefinition(registry, beanFactory, BeanIds.SPRING_SECURITY_FILTER_CHAIN,
        BeanIds.SPRING_SECURITY_FILTER_CHAIN);
    } else {
      final Filter moduleFilterChainProxy = beanFactory.getBean(BeanIds.FILTER_CHAIN_PROXY,
        Filter.class);
      if (moduleFilterChainProxy != null) {
        final Map<String, List<?>> mergedFilterMap = new ManagedMap<>();

        final MutablePropertyValues propertyValues = filterChainProxy.getPropertyValues();
        final PropertyValue filterChainMap = propertyValues.getPropertyValue("filterChainMap");
        if (filterChainMap != null) {
          final Map<String, List<?>> filterMap = (Map<String, List<?>>)filterChainMap.getValue();

          for (final String path : this.paths) {
            mergedFilterMap.put(path, Collections.singletonList(moduleFilterChainProxy));
          }
          mergedFilterMap.putAll(filterMap);

          propertyValues.removePropertyValue(filterChainMap);
          propertyValues.add("filterChainMap", mergedFilterMap);
        }
      }
    }
  }

  public List<String> getPaths() {
    return this.paths;
  }

  public void setPaths(final List<String> paths) {
    this.paths = paths;
  }

}
