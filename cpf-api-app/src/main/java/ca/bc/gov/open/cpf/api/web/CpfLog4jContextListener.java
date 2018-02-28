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
package ca.bc.gov.open.cpf.api.web;

import java.io.File;

import javax.servlet.ServletContext;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;

import org.apache.log4j.ConsoleAppender;
import org.apache.log4j.Logger;
import org.apache.log4j.PatternLayout;
import org.apache.log4j.rolling.FixedWindowRollingPolicy;
import org.apache.log4j.rolling.RollingFileAppender;
import org.apache.log4j.rolling.SizeBasedTriggeringPolicy;

import com.revolsys.logging.log4j.ContextClassLoaderRepositorySelector;
import com.revolsys.util.Property;

public class CpfLog4jContextListener implements ServletContextListener {

  @Override
  public void contextDestroyed(final ServletContextEvent event) {
    ContextClassLoaderRepositorySelector.remove();
  }

  @Override
  public void contextInitialized(final ServletContextEvent event) {
    final Logger logger = Logger.getRootLogger();
    logger.removeAllAppenders();
    final ServletContext context = event.getServletContext();
    String cpfLogDirectory = context.getInitParameter("cpfLogDirectory");
    if (!Property.hasValue(cpfLogDirectory)) {
      cpfLogDirectory = System.getProperty("cpfLogDirectory", "logs/cpf");
    }
    File rootDirectory = new File(cpfLogDirectory);
    if (rootDirectory.exists()) {
      if (!rootDirectory.isDirectory()) {
        rootDirectory = null;
      }
    } else {
      if (!rootDirectory.mkdirs()) {
        rootDirectory = null;
      }
    }
    if (rootDirectory == null || !(rootDirectory.exists() || rootDirectory.mkdirs())) {
      new ConsoleAppender().activateOptions();
      final ConsoleAppender appender = new ConsoleAppender();
      appender.activateOptions();
      appender.setLayout(new PatternLayout("%d\t%p\t%c\t%m%n"));
      logger.addAppender(appender);
    } else {
      final String baseFileName = rootDirectory + "/cpf-app";
      final String activeFileName = baseFileName + ".log";
      final FixedWindowRollingPolicy rollingPolicy = new FixedWindowRollingPolicy();
      rollingPolicy.setActiveFileName(activeFileName);
      final String fileNamePattern = baseFileName + ".%i.log";
      rollingPolicy.setFileNamePattern(fileNamePattern);

      final RollingFileAppender appender = new RollingFileAppender();

      appender.setFile(activeFileName);
      appender.setRollingPolicy(rollingPolicy);
      appender.setTriggeringPolicy(new SizeBasedTriggeringPolicy(1024 * 1024 * 10));
      appender.activateOptions();
      appender.setLayout(new PatternLayout("%d\t%p\t%c\t%m%n"));
      appender.rollover();
      logger.addAppender(appender);
    }
  }
}
