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

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import ca.bc.gov.open.cpf.plugin.impl.BusinessApplicationRegistry;

import com.revolsys.logging.Logs;

public interface ModuleLoader {
  static List<URL> getConfigUrls(final ClassLoader classLoader,
    final boolean useParentClassloader) {
    final List<URL> configUrls = new ArrayList<>();
    try {
      final Enumeration<URL> urls = classLoader
        .getResources("META-INF/ca.bc.gov.open.cpf.plugin.sf.xml");
      while (urls.hasMoreElements()) {
        final URL configUrl = urls.nextElement();
        if (ModuleLoader.isDefinedInClassLoader(classLoader, useParentClassloader, configUrl)) {
          configUrls.add(configUrl);
        }
      }
    } catch (final IOException e) {
      Logs.error(ModuleLoader.class, "Unable to get spring config URLs", e);
    }
    return configUrls;
  }

  static boolean isDefinedInClassLoader(final ClassLoader classLoader,
    final boolean useParentClassLoader, final URL resourceUrl) {
    if (useParentClassLoader) {
      return true;
    } else if (classLoader instanceof URLClassLoader) {
      final URLClassLoader urlClassLoader = (URLClassLoader)classLoader;
      for (final URL url : urlClassLoader.getURLs()) {
        if (resourceUrl.toString().contains(url.toString())) {
          return true;
        }
      }
      return false;
    } else {
      return true;
    }
  }

  BusinessApplicationRegistry getBusinessApplicationRegistry();

  public void refreshModules();

  void setBusinessApplicationRegistry(BusinessApplicationRegistry businessApplicationRegistry);
}
