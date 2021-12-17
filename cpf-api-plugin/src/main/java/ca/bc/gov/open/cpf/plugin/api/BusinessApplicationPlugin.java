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
package ca.bc.gov.open.cpf.plugin.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * <p>The <code>BusinessApplicationPlugin</code> annotation marks a Java class as a CPF business
 * application plug-in. The plug-in class must have the <code>public</code> keyword, be defined in a separate
 * Java file, must not be in the default package (must have a package declaration) and must not
 * have the <code>final</code> keyword.</p>
 *
 * <p>The instance of the plug-in is executed within a single thread so does not need to be
 * synchronized. Any services that it uses must however be thread safe as they will be used by
 * multiple instances of the plug-in in different threads.</p>
 *
 * <p class="note">NOTE: An instance of the plug-in class is created for each request processed by the plug-in.
 * Therefore state will not be maintained between requests it must not include the initialization
 * of any resources that have significant overhead to create. These should be defined as spring
 * beans and made available to the plug-in using spring dependency injection.
 * If there are data structures that vary based
 * on the parameters to a request then these can be created within the plug-in.</p>
 *
 * <p>The following code fragment shows the implementation of a plug-in class using all of the
 * annotation elements.</p>
 *
 *<figure><pre class="prettyprint language-java">package ca.bc.gov.demo;

import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;

&#064;BusinessApplicationPlugin(
  name                    = "Demo",
  packageName             = "ca.bc.gov.demo",
  title                   = "Demonstration Plug-in"
  version                 = "1.1.2",
  compatibleVersions      = { "1.1.0", "1.1.1" },
  description             = "Demonstrates how to use the CPF plug-in API"
  descriptionUrl          = "https://demo.gov.bc.ca",
  inputDataContentTypes   = { "image/png", "image/jpeg" },
  resultDataContentTypes  = { "image/png", "image/jpeg" },
  perRequestInputData     = true,
  perRequestResultData    = true,
  maxRequestsPerJob       = 100,
  numRequestsPerWorker    = 2,
  maxConcurrentRequests   = 10,
  batchModePermission     = "hasRoleRegex('DEMO_.*')")
  instantModePermission   = "hasRole('DEMO_USER')",
  logLevel                = "INFO")
public class Demo {
  :
}</pre></figure>
 *
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(value = ElementType.TYPE)
public @interface BusinessApplicationPlugin {
  /**
   * <p>A <a href="http://static.springsource.org/spring-security/site/docs/3.0.x/reference/el-access.html">Spring security expression</a>
   * indicating if a user has permission to submit single or multiple requests for batch execution.</p>
   *
   * @return The batch mode permission
   */
  String batchModePermission() default "permitAll";

  @Deprecated
  String[] compatibleVersions() default {};

  /**
   * <p>A description of the plug-in that is displayed on the business application
   * list page. Keep to a few sentences, use the descriptionUrl to link to a
   * page containing more information.</p>
   */
  String description() default "";

  /**
   * <p>A URL to a web page providing additional documentation for the business
   * application. This is displayed as a link on the application description
   * page.</p>
   */
  String descriptionUrl() default "";

  /**
   * <p>The array of MIME media types of input data accepted by the plug-in. For
   * perRequestInputData=false omit this value to use all the supported
   * structured data MIME media types. For perRequestInputData=true set this to
   * the MIME media types of the input data supported (e.g. image/jpeg).</p>
   */
  String[] inputDataContentTypes() default {};

  /**
   * <p>A <a href="http://static.springsource.org/spring-security/site/docs/3.0.x/reference/el-access.html">Spring security expression</a>
   * indicating if a user has permission to submit single request for instant execution.</p>
   *
   * <p class="note">NOTE: DO NOT enable this for plug-ins that consume lots of resources
   * or take more than a second to run as it will tie up available web server threads.</p>
   */
  String instantModePermission() default "denyAll";

  /**
   * <p>The level of logging to include for requests processed by the plug-in.</p>
   *
   * <div class="table-responsive">
   *   <table class="table table-striped table-bordered table-condensed">
   *     <caption>Log Levels</caption>
   *     <thead>
   *       <tr>
   *         <th>Level</th>
   *         <th>Description</th>
   *       </tr>
   *     </thead>
   *     <tbody>
   *       <tr>
   *         <td>ERROR</td>
   *         <td>Only include un-handled application errors.</td>
   *       </tr>
   *       <tr>
   *         <td>INFO</td>
   *         <td>Includes more details on the execution of groups or plug-in info level messages.</td>
   *       </tr>
   *       <tr>
   *         <td>DEBUG</td>
   *         <td>The most detailed log level, should only be used during development.</td>
   *       </tr>
   *     </tbody>
   *   </table>
   * </div>
   *
   * <p class="note">Enabling INFO or DEBUG will increase the time it takes to process a request
   * so should not be enabled in production unless an issue is being investigated.</p>
   */
  String logLevel() default "ERROR";

  /**
   * <p>The maximum number of concurrent execution groups that will be scheduled
   * for this business application. If an application has a limit to the number
   * of database connections it can use this value can be used to limit the
   * number of requests that will be executed by the workers at one time.</p>
   */
  int maxConcurrentRequests() default 100;

  /** <p>The maximum number of requests that a user can submit in a batch job.</p> */
  int maxRequestsPerJob() default Integer.MAX_VALUE;

  /**
   * <p>The name of the plug-in in (lower|Upper)CaseCamelNotation (e.g.
   * fibonacciSequence or FibonacciSequence) . Must be a valid Java identifier
   * (no spaces or special characters). This is used in the web service URL for
   * the business application and in the batch jobs in the database. The name
   * should not be changed between releases. If the name changes manual changes
   * will be required to the data in the database and any exteral applications
   * that use the plug-in.</p>
   */
  String name() default "";

  /**
   * <p>The maximum number of requests that will be added to an execution group to send to a worker
   * for sequential execution. If a plug-in takes milliseconds to execute set this to 100 to reduce
   * the communication overhead in processing the requests.</p>
   */
  int numRequestsPerWorker() default 1;

  /**
   * <p>The name of the Java package that the business application is in (e.g. <code>ca.bc.gov.gba</code>).
   * Currently this is used to set the logging level for any loggers created with that package
   * prefix.</p>
   */
  String packageName() default "";

  /**
   * <p>Boolean flag indicating that the plug-in accepts a binary blob of data for each request.
   * The binary data will be passed to the plug-in as URL used to access the binary blob of data.
   * Use the value true if the plug-in accepts an image, GML, or some other kind of file.
   * Use the value false if the plug-in is a structured
   * data plug-in and the request attributes will be set using the setter methods on the plug-in.</p>
   */
  boolean perRequestInputData() default false;

  /**
   * <p>Boolean flag indicating that the plug-in will return a binary blob of data via an
   * OutputStream as the result of execution. Use the value true if the plug-in generates an
   * image, GML, or some other kind of file. Use the value false if the plug-in is a structured
   * data plug-in and the result attributes will be read from the plug-in and these values will
   * be stored against the request.</p>
   * */
  boolean perRequestResultData() default false;

  /**
   * <p>The array of MIME media types of output data returned by the plug-in. For
   * perRequestOutputData=false omit this value to use all the supported
   * structured data MIME media types. For perRequestOutputData=true set this to
   * the MIME media types of the output data supported (e.g. image/jpeg).</p>
   */
  String[] resultDataContentTypes() default {};

  /**
   * <p>The display title displayed on the web site for the plug-in (e.g. Fibonacci
   * Sequence).</p>
   */
  String title() default "";

  @Deprecated
  String version() default "";
}
