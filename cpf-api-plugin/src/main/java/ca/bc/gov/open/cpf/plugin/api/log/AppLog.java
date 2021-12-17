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
package ca.bc.gov.open.cpf.plugin.api.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;

import com.revolsys.log.LogbackUtil;

/**
 * <p>The AppLog class is a logging API for use by a {@link BusinessApplicationPlugin} class.
 * Plug-in classes can record error, info and debug messages in the execute method. These messages
 * will be recorded in the log file (if that log level is enabled in the worker for the AppLog
 * class). The message will also be recorded in the module log file on the master if that log level
 * is enabled for the business application. This functionality allows viewing the logs for the
 * all the workers from the CPF admin console.</p>
 *
 * <p>The plug-in must implement the following method on the {@link BusinessApplicationPlugin} class
 * to obtain a AppLog instance for this request.</p>
 *
 * <figure><pre class="prettyprint language-java">private AppLog appLog;

public void setAppLog(final AppLog appLog) {
  this.appLog = appLog;
}</pre></figure>
 *
 */
public class AppLog {

  private static String getName(final String moduleName, final String businessApplicationName,
    String groupId) {
    if (groupId == null || groupId.trim().length() == 0) {
      groupId = String.valueOf(System.currentTimeMillis());
    }
    final String name = moduleName + "." + businessApplicationName + "." + groupId;
    return name;
  }

  /** The logging level (ERROR, INFO, WARN, DEBUG). */
  private String logLevel = "ERROR";

  private final Logger log;

  private final String name;

  public AppLog(final String name) {
    this(name, "ERROR");
  }

  public AppLog(final String name, final String logLevel) {
    this.name = name;
    this.log = LoggerFactory.getLogger(name);
    setLogLevel(logLevel);
  }

  public AppLog(final String moduleName, final String businessApplicationName, final String groupId,
    final String logLevel) {
    this(getName(moduleName, businessApplicationName, groupId), logLevel);
  }

  /**
   * <p>Record the info message in the log if {@link #isInfoEnabled()} is true.</p>
   *
   * @param message The message.
   */
  public void debug(final String message) {
    if (isDebugEnabled()) {
      this.log.debug(message);
    }
  }

  /**
   * <p>Record the error message in the log.</p>
   *
   * @param message The message.
   */
  public void error(final String message) {
    this.log.error(message);
  }

  /**
   * <p>Record the error message in the log with the exception.</p>
   *
   * @param message The message.
   */
  public void error(final String message, final Throwable exception) {
    this.log.error(message, exception);
  }

  /**
   * <p>Get the logging level (ERROR, INFO, DEBUG).</p>
   *
   * @return The logging level (ERROR, INFO, DEBUG).
   */
  public String getLogLevel() {
    return this.logLevel;
  }

  /**
   * <p>Record the info message in the log if {@link #isInfoEnabled()} is true.</p>
   *
   * @param message The message.
   */
  public void info(final String message) {
    if (isInfoEnabled()) {
      this.log.info(message);
    }
  }

  /**
   * <p>Check to see if debug level logging is enabled. Use this in an if block around
   * logging operations that create large amounts of log data to prevent that data from being
   * created if logging is not enabled.</p>
   *
   * @return True if debug level logging is enabled.
   */
  public boolean isDebugEnabled() {
    return this.logLevel.equals("DEBUG");
  }

  /**
   * <p>Check to see if info or debug level logging is enabled. Use this in an if block around
   * logging operations that create large amounts of log data to prevent that data from being
   * created if logging is not enabled.</p>
   *
   * @return True if info or debug level logging is enabled.
   */
  public boolean isInfoEnabled() {
    return this.logLevel.equals("DEBUG") || this.logLevel.equals("INFO");
  }

  /**
   * <p>Set the current logging level (ERROR, INFO, DEBUG).</p>
   *
   * @param level The logging level (ERROR, INFO, DEBUG).
   */
  public void setLogLevel(final String level) {
    this.logLevel = level;
    LogbackUtil.setLevel(this.name, level);
  }

  @Override
  public String toString() {
    return this.log.getName();
  }

  /**
   * <p>Record the warning message in the log.</p>
   *
   * @param message The warning.
   */
  public void warn(final String message) {
    this.log.warn(message);
  }
}
