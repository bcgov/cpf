## Plug-in Guide
        
The Concurrent Processing Framework (CPF) Plug-in Developers Guide provides instructions for
developers to be able to develop and test CPF plug-in modules containing business application
plug-ins in their environment and to deliver them to the ministry.

See the Plug-in links in the menu on the left of this page for more details on developing a plug-in.

### Overview

A CPF Plug-in module is a Maven project that contains one or more business application plug-ins. A 
business application plug-in is a service that takes one or more request parameters, performs an 
action using the request parameters and generates one or more result attributes. The CPF allows a 
user to create a job that contains one or more requests to be executed against a single business 
application. For example a job may contain 1 million addresses to geocode. 

For an example a power service would have the request parameters `base` and 
`exponent` and the result attribute `result`, which is the calculation of 
base to the power of exponent.

For input parameters a plug-in can accept either one or more structured request attributes or a per
request input data with an InputStream containing an opaque blob of data.

* Structured request attributes can be thought of as a key value pair map of values. The users of
  the plug-in can specify a single file (e.g. CSV, JSON) with a record containing the values for 
  each request to be executed by the plug-in. For example each address to geocode would have one 
  record in the input file and the new instance plug-in will be executed for each record. In 
  addition to request level parameters there are also job parameters that apply to all requests in 
  the job. The CPF will convert the input data from the file format specified by the user to Java 
  objects for use by the plug-in.
* Per request input data is used where the data is more complex than key value pairs, or is a
  binary blob of data. Each request is specified using a separate file stream or URL to the binary
  blob of data. For example a face detection plug-in would take a JPEG file as an input parameter
  for each face to recognize. Per request input data plug-ins cannot also have request attributes, 
  although they may have job attributes. If structured request attributes and binary blobs are 
  required for the same plug-in the plug-in can accept a URL to the input data and process that 
  URL within the plug-in.

For result fields the plug-in can either return a single record of structured result data, a list 
of structured result data or a single binary blob of data.

* Like structured request attributes structured result attributes are a key value pairs. The CPF 
  will create a single file in the requested output file for the user to download with one record 
  for each result returned from the plug-in.
* The list of structured results also creates a single output file for the user to download. There 
  will be one record for each entry in the list of results, with a relative result number showing 
  the order of the result in the list of results.
* Like per request input data, per request result data returns one binary blob of data for each 
  request. There will be one file for the user to download for each request. For example a WMS service 
  would return a JPEG, or PNG for each map to render.

The CPF and plug-ins depend heavily on the
[Spring Framework](https://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/)
 and the XML configuration mechanism. Developers should have a least a basic understanding of
 defining spring beans and dependency injection.

#### API & Download

The most recent version of the CPF is ${project.version}. For development purposes the most recent
snapshot version of the CPF libraries and the trunk sub-tree in the subversion repository should be
used. The snapshot and the trunk are only updated under controlled circumstances  when new
functionality or bug fixes are to be delivered to the plug-in developers. When delivering a final
version of the application to the Ministry the plug-in must use the same version as is deployed to
the Ministry's server.

Use the links in the following table to view the Plug-in API documentation or download the API libraries.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>API</th>
      <th>Documentation</th>
      <th>Download</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>Plug-in Java (11+)</td>
      <td><a href="java-api">API Docs</a></td>
      <td><a href="../../lib/cpf-api-plugin-${project.version}.jar">Download</a></td>
    </tr>
  </tbody>
</table></div>

### Maven Project

The CPF uses maven modules for the deployment of plug-in modules to the CPF web services. Each CPF
plug-in module is a maven project that contains all the code and dependencies of the plug-in.
Within the CPF plug-in module one or more
[Business Application Class](#Business_Application_Class) implementations can be defined.

#### Create a plug-in project

The first step is to create a plug-in project. Plug-ins must be developed using Apache Maven 3.0.x.

A new plug-in project can be created using the Maven archetype mechanism. The archetype defines a
template project that can be created by passing in parameters to the following command.

```
cd ~/projects
mvn \
  archetype:generate \
  -DinteractiveMode=false \
  -DarchetypeGroupId=ca.bc.gov.open.cpf \
  -DarchetypeArtifactId=cpf-archetype-plug-in \
  -DarchetypeVersion=${project.version} \
  -DgroupId=ca.bc.gov \
  -DartifactId=demo \
  -Dversion=1.0.0-SNAPSHOT \
  -Dpackage=ca.bc.gov.demo \
  -Dplug-inName=Demo \
  -Dplug-inAcronym=demo
```

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Parameter</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>archetypeVersion</code></td>
      <td>The most recent version of the CPF framework.</td>
    </tr>
    <tr>
      <td><code>groupId</code></td>
      <td>The maven group identifier also used as the java package name for the plug-in class.
      For BC Government plug-ins this should be ca.bc.gov.</td>
    </tr>
    <tr>
      <td><code>artifactId</code></td>
      <td>The base maven artifact identifier used for the maven modules created in the project.
      This should be the project acronym for BC Government projects.</td>
    </tr>
    <tr>
      <td><code>version</code></td>
      <td>The version identifier you’d like to give to your plug-in. Must include -SNAPSHOT the
      -SNAPSHOT is removed on migration to test and production.</td>
    </tr>
    <tr>
      <td><code>package</code></td>
      <td>The Java package name for the generated code. Must be <code>ca.bc.gov.{acronym}</code>
      for BC Government projects.</td>
    </tr>
    <tr>
      <td><code>pluginName</code></td>
      <td>The name of the plug-in. This will be used to generate the plug-in class name and for
      descriptions in the Maven build files.</td>
    </tr>
    <tr>
      <td><code>pluginAcronym</code></td>
      <td>The acronym of the plug-in. This will be used to generate the plug-in jar name.</td>
    </tr>
  </tbody>
</table></div>

The following directory structure would be created if the command were run using the parameters above.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>File/Directory</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>demo/</code></td>
      <td>The root directory of the plug-in project.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;scripts/</code></td>
      <td>Any SQL scripts required to create the database or data required by the plug-in. Samples are included.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;README.txt</code></td>
      <td>A sample readme file for deployment to the Ministry. Edit for your application.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;pom.xml</code></td>
      <td>The maven build file for the plug-in.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;src/</code></td>
      <td>The root folder containing the source code and resources.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;&nbsp;&nbsp;main/java/ca.bc.gov/demo/</code></td>
      <td>The java package for the plug-in, add any support classes in here.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;DemoPlug-in.java</code></td>
      <td>The java class for the plug-in.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;&nbsp;&nbsp;main/resources/</code></td>
      <td>The non-java resource files to include in the plug-in jar.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;META-INF/</code></td>
      <td>The META-INF directory for the plug-in jar.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;ca.bc.gov.open.cpf.plugin.sf.xml</code></td>
      <td>The main CPF spring configuration file for the plug-ins in this module.</td>
    </tr>
    <tr>
      <td><code>&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;&nbsp;ca/bc/gov/demo/Demo.sf.xml</code></td>
      <td>The spring configuration file containing any beans used by the plug-ins.</td>
    </tr>
  </tbody>
</table></div>

#### Add Dependencies

A plug-in can include dependencies to additional libraries that are not deployed as part of the core
CPF application. These must be approved prior to delivering to the Ministry.

All plug-ins must either be available in the [Maven central repository](https://search.maven.org/).
Or a private repository that the CPF is configured to access.

If any of the dependencies include dependencies to logging frameworks such as Logback, LOG4J, SLF4J and
especially commons-logging these must be
[excluded](https://maven.apache.org/guides/introduction/introduction-to-optional-and-excludes-dependencies.html#Dependency_Exclusions)
from those dependencies. This ensures that carefully controlled versions of these files are used.
The CPF internally uses SLF4J as the logging API with a Logback binding to perform the actual logging
and a bridge so that commons logging is logged via SLF4J. It is recommended for plug-ins to use
SLF4J for logging in their module. Plug-ins must not include a `log4j.xml`, or `log4j2.xml`, or `logback.xml`
file in their Jar file. They can however be included in `src/test/resources` for
testing the plug-in.

Any dependencies that are included in the CPF application itself must be marked with a scope of
`provided`. If they are dependencies of other modules then they should be excluded from that
dependency. CPF ignores any dependencies to the CPF libraries or the libraries it uses. This
ensures that the correct versions of these libraries are used at runtime.

Any dependencies to test frameworks such as junit must be included using the `test`
scope so that they are not included in the jar. Also any test code must be included below 
`src/test` as opposed to `src/main`.

#### JDBC Dependencies

The CPF supports the Oracle and PostgreSQL with PostGIS extensions JDBC drivers. The dependencies
to these drivers are included through the `cpf-api-plugin` dependency.

> **NOTE:** At this time no other versions of these JDBC drivers or 3rd party JDBC drivers
> are supported. Any dependencies to those drivers must be excluded from the plug-in's dependencies.
> Other JDBC driver versions may interfere with the running of the CPF application.

#### Build the plug-in

The plug-in is built using maven. Use the following command to create a clean build that is deployed
to your local maven repository.

```
mvn clean install
```

The plug-in must be deployed to a maven repository so that the CPF can download the plug-in. If on
a developers workstation a local maven cache can be used instead of this step.

```
mvn deploy
```

#### Deploy a Plug-in

Plug-ins are deployed dynamically using the CPF admin application. Follow this procedure to deploy
a new module.

1. Open the CPF modules admin page.
2. Click the Add button on the Modules page.
3. Enter the project acronym as the module name (e.g. DEMO), it will be converted to upper case.
4. Enter in the maven module Id for your plug-in in the format `{groupId}:{artifactId}:{version}`.
   For example `com.myapplication:demo:1.0.0-SNAPSHOT`.
5. Click the Save button
6. The module view page will be displayed.

If the version number of a module changes, follow this procedure to update the version for an existing module.

1. Open the CPF modules admin page.
2. Click on the name of the module in the table, this will open the module view page.
3. Click on the name of the module in the table, this will open the module view page.
4. Click Edit button to edit the module.
5. Enter in the maven module Id for your plug-in in the format `{groupId}:{artifactId}:{version}`.
   For example `com.myapplication:demo:1.0.0-SNAPSHOT`.
6. Click the Save button
7. The module view page will be displayed.
8. Click the restart button to load the new version of the module.

> **NOTE:** You cannot change the name of the module. Delete the existing module and create a new one.

### Business Application Class

#### Java Class

A business application plug-in is implemented as a Java class with the
[BusinessApplicationPlugin](cpf-api-plugin/java-api/#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin)
annotation. See [BusinessApplicationPlugin](cpf-api-plugin/java-api/#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin)
for details of the annotation elements that can be used to configure the business application.

A single plug-in module can have more than one plug-in class, one for each plug-in provided by that 
module.

#### Plug-in Spring Configuration File

The CPF business application plug-in classes are registered with the CPF by including the plug-in
definition spring file `src/main/resources/META-INF/ca.bc.gov.open.cpf.plugin.sf.xml` in the Maven
project.

The following file shows an example of this file.
 
```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans
  xmlns="http://www.springframework.org/schema/beans"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:util="http://www.springframework.org/schema/util"
  xmlns:p="http://www.springframework.org/schema/p"
  xsi:schemaLocation="
    http://www.springframework.org/schema/beans
    https://www.springframework.org/schema/beans/spring-beans.xsd
    http://www.springframework.org/schema/util
    https://www.springframework.org/schema/util/spring-util.xsd
  "
>
  <util:list id="beanImports">
   <value>classpath:/ca/bc/gov/demo/Demo.sf.xml</value>
  </util:list>

  <bean
   id="demo"
   class="ca.bc.gov.demo.DemoPlug-in"
   p:dataSource-ref="demoDataSource"
   scope="prototype" />
</beans>
```

This file may only contain bean definitions for the plug-ins and an optional **beanImports** list.

The bean definitions must have the id attribute equal to the business application plug-in name
defined in the plug-in class. The class must be the fully qualified class name of the plug-in class. 
The scope on the bean must be set to prototype. This ensures that a new instance of the plug-in be 
created on each request to get the bean. The
[p:{propertyName}](https://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/beans.html#beans-p-namespace)
or [p:{propertyName}-ref](https://static.springsource.org/spring/docs/3.0.x/spring-framework-reference/html/beans.html#beans-p-namespace)
attribute styles can be used to inject dependencies into the bean when it is created.

If the plug-in requires other beans such as JDBC data sources or a caching service that are
expensive to create these must be defined in plug-in resource spring configuration files. The
plug-in archetype includes a blank spring file for this purpose. The `beanImports`
list includes a reference to each plug-in resource spring file used by the plug-ins. This is used
instead of the spring <code>import</code> mechanism so that the beans defined in those files are
only instantiated on the worker nodes. Within the plug-in definition spring file no regular spring
imports can be used, spring imports can however be used in the plug-in resource spring files.
Additional `beanImports` can be added if required.

#### Plug-in Resource Beans

A new instance of the plug-in class is created for each request processed in a job. Therefore the
plug-in class should not perform any complex instantiation in the constructor. Instead any resources
(such as data sources) or a caching service should be defined as singleton beans in the plug-in
resource spring files.

The plug-in bean will have a property (set/get method pair) for each resource bean it uses.

```java
private DataSource dataSource;

private void setDataSource(DataSource dataSource) {
  this.dataSource = dataSource;
}

private DataSource getDataSource() {
  return dataSource;
}
```

The ca/bc/gov/demo/Demo.sf.xml plug-in resource spring file would define the bean as shown below.

> **NOTE:** this example introduces the `JdbcDataSourceFactoryBean`. This can be used to create a
> pooling data source without needing to know the underlying database data source used. By changing
> the URL to an Oracle JDBC URL it will create an Oracle data source. The config properties are set
> on the data source instance. Consult the database vendor's documentation for the available
> parameters.

```xml
<bean
  id="demoDataSource"
  class="com.revolsys.jdbc.io.JdbcDataSourceFactoryBean"
  p:url="jdbc:postgresql://localhost:5432/demo"
  p:username="demo"
  p:password="12345678"
>
  <property
   name="config"
  >
   <map>
     <entry
       key="initialConnections"
       value="0" />
     <entry
       key="maxConnections"
       value="10" />
    </map>
  </property>
</bean>
```

> **NOTE:* Beans should not be defined with `lazy-init="true"`. This will cause the
> system to think the plug-in has been loaded when not all beans are ready. This can cause worker
> threads to be blocked waiting for the beans to initialize.

Finally the demoDataSource bean will be injected into the `dataSource` property on the
`demo` bean definition in the plug-in definition spring file.

```xml
<?xml version="1.0" encoding="UTF-8"?>
<beans
  xmlns="http://www.springframework.org/schema/beans"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
  xmlns:util="http://www.springframework.org/schema/util"
  xmlns:p="http://www.springframework.org/schema/p"
  xsi:schemaLocation="
    http://www.springframework.org/schema/beans
    https://www.springframework.org/schema/beans/spring-beans.xsd
    http://www.springframework.org/schema/util
    https://www.springframework.org/schema/util/spring-util.xsd
  "
>
  <util:list id="beanImports">
   <value>classpath:/com/mycompany/demo/Demo.sf.xml</value>
  </util:list>

  <bean
   id="demo"
   class="ca.bc.gov.demo.DemoPlug-in"
   p:dataSource-ref="demoDataSource"
   scope="prototype" />
</beans>
```

Any required dependencies can be injected using the basic approach shown above.

#### Plug-in Job Parameters &amp; Structured Request Parameters

A plug-in can implement parameters that the user can specify when submitting a job. Parameters can
 be global for all the requests in a job using the
[JobParameter](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.JobParameter) annotation.
Parameters for 
[structured input data](reference/structuredData.html) plug-ins with
[perRequestInputData](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.perRequestInputData)=false
 must have parameters specific to an individual request within the job using the
[RequestParameter](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.RequestParameter) annotation.

Parameter methods can also have the following annotations.

* [ca.bc.gov.open.cpf.plugin.api.AllowedValues](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.AllowedValues)
* [ca.bc.gov.open.cpf.plugin.api.DefaultValue](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.DefaultValue)
* [ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration)
* [ca.bc.gov.open.cpf.plugin.api.Required](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.Required)


#### Per request input data

[Opaque input data](reference/opaqueData.html) plug-ins  with
[perRequestInputData](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.perRequestInputData)=true
must implement the following methods to set the content type of the binary blob and URL which can
be used to access the binary blob of the input data for that request. The input stream for the URL
can be read in the execute method of the plug-in. When getting the input stream for the URL any HTTP
redirects must be followed to get the final content to read.

```java
private URL inputDataUrl;

private String inputDataContentType;

public void setInputDataUrl(final URL inputDataUrl) {
  this.inputDataUrl = inputDataUrl;
}

public void setInputDataContentType (final String inputDataContentType) {
  this.inputDataContentType = inputDataContentType;
}
```

#### Structured Result Attributes

[Structured result data](reference/structuredData.html) plug-ins with
[perRequestInputData](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.perRequestResultData)=false
must implement result data properties using the
[ResultAttribute](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.ResultAttribute)
annotation for more details.


Parameter methods can also have the following annotations.


* [ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration)

##### List of Structured Result Attributes

[Structured result data](reference/structuredData.html) plug-ins can use the
[ResultList](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.ResultList) annotation
to return multiple results from a single request.

#### Structured Data Geometry Request Parameters and Result Attributes

The CPF includes spatial support for structured input data and result data files.
This enables plug-ins to define job parameters, request parameters and result attributes to use
[Java Topology Suite](http://tsusiatsoftware.net/jts/main.html) (JTS) Geometry objects.
The CPF reads the input data files (e.g. Shapefile (ESRI) in a Zip file) and converts the values into
JTS Geometry objects for processing by the business application. After processing the JTS Geometry
objects are converted back into the requested output spatial file format. The CPF also handles
projection of the geometries if required.

The CPF can use [Well-known_text](https://en.wikipedia.org/wiki/Well-known_text) (WKT)
and [Extended WKT](https://postgis.org/documentation/manual-1.5/ch04.html#EWKB_EWKT) (EWKT) geometry
strings for geometries in the input data files submitted by the user or the result data files
generated by the CPF. This can be used in non-spatial file formats or where there are multiple
geometries in a spatial file format that only supports a single geometry. These EWKT geometries
will be converted to JST Geometry objects. EWKT geometries are also used for job parameters as they
are passed as HTML form parameters. We however don't recommend using geometries as job parameters.

#### Geometry Job & Request Parameters

Adding a geometry job parameter or request parameter is exactly the same as any other business
application parameter. The parameter type must be a JTS Geometry class or subclass. The only 
difference is that the
[GeometryConfiguration](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration)
annotation can also be specified. If the configuration is not set on the method then the annotation
from the class is used.


#### Geometry Result Attributes

Adding a geometry result attribute is exactly the same as any other business application result 
attributes. The return type must be a JTS Geometry class or subclass. The only difference is that 
the 
[GeometryConfiguration](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.GeometryConfiguration)
annotation can also be specified. If the configuration is not set on the method then the annotation
from the class is used.

> **NOTE:** All geometries created by a business application must have the SRID set on the geometry object.

The user when creating a job can override the srid, numAxis, scaleFactorXy and scaleFactorZ. The
required conversion will be performed by the CPF. The business application can implement any one
of the following methods to receive the values requested by the user. This might be useful if
the application wants to perform it's own projection or reduce work by not creating z-values if
they are not required.

```java
public void setResultNumAxis(final int resultNumAxis) {
  this.resultNumAxis = resultNumAxis;
}

public void setResultScaleFactorXy(final double resultScaleFactorXy) {
  this.resultScaleFactorXy = resultScaleFactorXy;
}

public void setResultScaleFactorZ(final double resultScaleFactorZ) {
  this.resultScaleFactorZ = resultScaleFactorZ;
}

public void setResultSrid(final int resultSrid) {
   this.resultSrid = resultSrid;
}
```

##### Geometry Processing

See the [GeometryFactory](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.GeometryFactory)
class for details on creating [Java Topology Suite](http://tsusiatsoftware.net/jts/main.html) (JTS)
geometry objects.

#### Per request result data
[Opaque input data](reference/opaqueData.html) plug-ins with
[perRequestResultData](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.perRequestResultData)=true
cannot return result attributes. They can only return the binary content of the result data. The
plug-in must implement the following methods. The `resultData` output stream can be used to write
the result binary blob data. The `resultDataContentType` indicates to the plug-in the type of result
data it should generate.

```java
private OutputStream resultData;

private String resultDataContentType;

public void setResultData(final OutputStream resultData) {
  this.resultData = resultData;
}

public void setResultDataContentType(final String format) {
  this.resultDataContentType = format;
}
```

#### Execute method

The plug-in must implement a public void `execute()` method. When the CPF has set all the parameters 
on the plug-in it invokes the execute method so that the plug-in can perform the request using the 
parameters and generate the result.

The following is a simple example of an execute method that takes the value request field, 
calculates the square and stores this in the square result attribute.

```java
public void execute() {
  this.square = this.value * this.value;
}
```

The following is an example of a per request input data execute method. The plug-in gets the input
stream from the inputDataUrl, performs some processing on the data and stores the result in the
digest result attribute.

```java
public void execute() {
  try {
    MessageDigest digester = MessageDigest.getInstance("MD5");
    InputStream in = this.inputDataUrl.openStream();
    byte[] buffer = new byte[4096];
    for (int count = in.read(buffer); count != -1; count = in.read(buffer)) {
       digester.update(buffer, 0, count);
     }
    byte[] data = digester.digest();
    this.digest = new String(Hex.encodeHex(data));
  } catch (NoSuchAlgorithmException e) {
    throw new IllegalArgumentException("Cannot find digest algorithm " + algorithmName, e);
  } catch (IOException e) {
    throw new RuntimeException("Cannot read input data", e);
  }
}
```

The following is an example of a per request result data execute method. The plug-in creates query
string parameters from the plug-in request parameters and creates a connection to a web service.
The plug-in then writes the response from the web service to the result data.

```java
public void execute() {
  Map<String, Object> parameters = new LinkedHashMap<String, Object>();
  parameters.put("SERVICE", "WMS");
  parameters.put("VERSION", "1.1.1");
  parameters.put("REQUEST", "GetMap");
  parameters.put("LAYERS", this.layers);
  parameters.put("STYLES", this.styles);
  parameters.put("CRS", this.crs);
  parameters.put("BBOX", this.bbox);
  parameters.put("WIDTH", this.width);
  parameters.put("HEIGHT", this.height);
  parameters.put("FORMAT", this.resultDataContentType);
  parameters.put("EXCEPTIONS", "INIMAGE");
  String url = UrlUtil.getUrl(this.wmsUrl, parameters);
  try {
    InputStream in = new URL(url).openStream();
    try {
      FileUtil.copy(in, this.resultData);
    } finally {
      FileUtil.closeSilent(this.resultData);
      FileUtil.closeSilent(in);
    }
  } catch (MalformedURLException e) {
    throw new IllegalArgumentException(url + " is not valid URL");
  } catch (IOException e) {
    throw new RuntimeException("Unable to get map", e);
  }
}
```


#### Logging

The CPF automatically creates [Log Files](admin/log.html) for each module and
business application running within the master and worker processes. For a business application
the log level can be changed using the [Edit Business Application](admin/app.html#edit)
page without requiring a restart to the module.

> **NOTE:** Applications MUST NOT include logging configuration files (e.g. log4j.xml) in their jars.
> They must also not manually create Appenders or otherwise change the logging configuration.

The preferred approach is for business application plug-ins to use the
[AppLog](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.log.AppLog) class for logging to
have the messages appear in the appropriate log file.

The plug-in The plug-in may also use [slf4j](https://www.slf4j.org/) (preferred) with the following logger name sub trees.

* Loggers below `{ModuleName}.{BusinessApplicationName}` (e.g. `DEMO.demoApp`).
* Loggers below the [BusinessApplicationPlugin.packageName](cpf-api-plugin/java-api//#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.packageName)
(e.g. `ca.bc.gov.demo`)

#### Memory Management

Plug-ins must ensure that their data and classes can be garbage collected when the plug-in is
stopped. Below are some guidelines for ensuring garbage collection.

* Avoid using the Java singleton pattern for cached data structures. The data won't be released
  until the class is garbage collected (which maybe neve depending on the JVM). Use
  a spring bean with the default singleton scope instead.
* Avoid aspect oriented libraries, especially that use CGLIB. These sometimes create classes that
  can't get garbage collected.
* If using Java introspection/reflection, ensure that class and method references are not stored in
  a cache owned by another JVM. CPF has code to perform cache clearing for commons beanutils.
* Implement a cleanup method on each bean (other than the plug-in prototype bean). This method
  must close any resources it created (e.g. database connections), clear any cached values and
  preferably set any fields to null. See the spring framework documentation for details on
  implementing
  [Destruction call-backs](https://docs.spring.io/spring/docs/3.0.7.RELEASE/reference/beans.html#beans-factory-lifecycle-disposablebean)
  or [@PreDestory](https://docs.spring.io/spring/docs/3.0.7.RELEASE/reference/beans.html#beans-postconstruct-and-predestroy-annotations) annotation.
  Spring will automatically call those methods when the plug-in is stopped.


The CPF Tomcat JVM should be started with the following parameters to allow class unloading. Consult
your JVM's documentation for more details.

```
-XX:+UseConcMarkSweepGC -XX:+CMSClassUnloadingEnabled -XX:+UseCodeCacheFlushing
```

In a non-production environment you can also turn on the following option to see the classes that
were garbage collected on the console output from the Tomcat command.

```
-XX:+TraceClassUnloading
```

The following procedure can be used to see if a CPF module can be un-loaded successfully.

1. Restart the Tomcat JVM to ensure a clean start.
2. Make sure the CPF module is started (don't restart if already started).
3. Stop the CPF module using the CPF admin application. Or to test CPF too undeploy the CPF web apps
   using the Tomcat manager.
4. Use the jconsole tool that comes with the JDK (not JRE) to connect to the Tomcat Java process.
5. Click on the memory tab.
6. Click on 'Perform GC' about 5 times to force a garbage collection.
7. Click on the Classes tab and see if the Loaded count is lower than the Total Loaded. This
  shows that some classes were garbage collected. You may need to do 'Perform GC' or wait a while
  as there is no contract as to when the class garbage collection will occur.
8. The following command creates a memory dump of the JVM you will need to know the process id [PID]
   of the Tomcat JVM. `jmap -dump:live,format=b,file=cpf.hprof {PID}`.
9. The cpf.hprof can be loaded into a Java Heap analyzer or the following command can be used to
   create a web server where you can browse the memory.
  1. `jhat -J-mx1024m cpf.hprof` (**NOTE**: you may need more memory depending on the amount of
     memory used in the Tomcat JVM).
  2. Open [http://localhost:7000/](http://localhost:7000/) in a web browser.
     The [Show Instance Counts](http://localhost:7000/showInstanceCounts/)
     page is the most useful as it shows the counts of objects by class for non core Java class.
  3. Check that the custom classes used by your application do no appear on this page. If they do
     then the application has not been garbage collected.
  4. Click the instances link next to a class to see the instances. From there you can navigate to
     see the references to that object. This will help identify where cleanup code should be
     implemented.

### Customization

Plug-ins can customize certain aspects of the HTML forms for submitting jobs or how the files
generated by the CPF for structured results.
For example the sections to group fields on the forms or the URL to the style document to be used in
KML documents.

#### Defining Customization Properties

The customization properties can be defined using an optional `properties` map bean
in the `ca.bc.gov.open.cpf.plug-in.sf.xml` file.

The map can contain a `default` entry, or an entry for each business application name.
The value for each entry is a map of the customization properties. If the property is not specified
for a business application then the value from the default entry is used.

**Example Plug-in Customization Properties**

```xml
<util:map id="properties">
  <entry key="default">
   <map>
     <entry
       key="kmlPlaceMarkNameAttribute</b>></em>
       value="fieldA</b>></em> />
    </map>
  </entry>
  <entry key="demo">
   <map>
     <entry
       key="kmlPlaceMarkNameAttribute</b>></em>
        value="fieldB</b>></em> />
   </map>
  </entry>
</util:map>
```

Customization properties can also be set by adding a config property for a business application
using the admin web application.

1. Open the CPF modules admin page.
2. Click on the name of the module in the table, this will open the module view page.
3. Click the Business Applications tab to view the list of business applications.
4. Click on the name of the business application in the table, this will open the business application view page.
5. Click the Config Properties tab to view the current properties.
6. Click the id of a property to view it and then click Edit to change the value or click Add to add a new property.

#### Form Customization Properties

The following customization properties are used to define the sections to group fields on the
forms and the default values for fields on the job submission forms.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Name</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>formSectionsOpen</code><br />
      <code>formSectionsOpenInstant</code><br />
      <code>formSectionsOpenSingle</code><br />
      <code>formSectionsOpenMultiple</code></td>
      <td><code>List&lt;String&gt;</code></td>
      <td><p>The list of section names that are open on the job submission forms, all other sections will be closed
      by default. The open sections can be specified for all forms using <code>formSectionsOpen</code>
      or for a specific form using <code>formSectionsOpenInstant</code>, <code>formSectionsOpenSingle</code>, or
      <code>formSectionsOpenMultiple</code>. If not specified the open sections will be
      <code>applicationParameters</code>, <code>inputData</code>, and <code>resultFormat</code>.</p>
<b>Example Form Open Sections Customization</b>
<div class="source">
<pre class="xml">&lt;util:map id="properties"&gt;
  &lt;entry key="demo"&gt;
    &lt;map&gt;
      &lt;entry key="formSectionsOpen"&gt;
        &lt;set&gt;
          &lt;value&gt;applicationParameters&lt;/value&gt;
          &lt;value&gt;inputData&lt;/value&gt;
          &lt;value&gt;resultFormat&lt;/value&gt;
        &lt;/set&gt;
      &lt;/entry&gt;
      &lt;entry key="formSectionsOpenInstant"&gt;
        &lt;set&gt;
          &lt;value&gt;applicationParameters&lt;/value&gt;
          &lt;value&gt;requestParameters&lt;/value&gt;
          &lt;value&gt;resultFormat&lt;/value&gt;
        &lt;/set&gt;
      &lt;/entry&gt;
      &lt;entry key="formSectionsOpenSingle"&gt;
        &lt;set&gt;
          &lt;value&gt;applicationParameters&lt;/value&gt;
          &lt;value&gt;requestParameters&lt;/value&gt;
          &lt;value&gt;resultFormat&lt;/value&gt;
        &lt;/set&gt;
      &lt;/entry&gt;
    &lt;/map&gt;
  &lt;/entry&gt;
&lt;/util:map&gt;</pre>
</div>
</td>
      </tr>
      <tr>
        <td><code>formSectionsMap</code><br />
        <code>formSectionsMapInstant</code><br />
        <code>formSectionsMapSingle</code><br />
        <code>formSectionsMapMultiple</code></td>
        <td><code>List&lt;String&gt;</code></td>
        <td><p>This property is used to customize the sections and the fields in each section that
        appear on the job submission forms. The section field can be specified for all forms <code>formSectionsMap</code>
        or for a specific form <code>formSectionsMapInstant</code>, <code>formSectionsMapSingle</code>,
        <code>formSectionsMapMultiple</code>. The property is a <code>map</code> where the <code>key</code>
        on the <code>entry</code> is the name of the section and the value is a <code>list</code>
        of the field names. Section names should be in lowerCamelCase notation. The titles on the sections
        are converted to capitalized words from the section name (e.g. Lower Camel Case). Fields not
        applicable for a form will not be displayed.
        Any business application parameters not listed will be put into the <code>applicationParameters</code> section.
        Any CPF parameters not listed will be in the sections shown below.</p>
<b>Default Form Fields by Section</b>     
<div class="source">
<pre class="xml">&lt;map&gt;
  &lt;entry key="inputData"&gt;
    &lt;list&gt;
      &lt;value&gt;inputDataContentType&lt;/value&gt;
      &lt;value&gt;inputDataUrl&lt;/value&gt;
      &lt;value&gt;inputData&lt;/value&gt;
      &lt;value&gt;srid&lt;/value&gt;
    &lt;/list&gt;
  &lt;/entry&gt;
  &lt;entry key="resultFormat"&gt;
    &lt;list&gt;
      &lt;value&gt;resultSrid&lt;/value&gt;
      &lt;value&gt;resultDataContentType&lt;/value&gt;
      &lt;value&gt;format&lt;/value&gt;
    &lt;/list&gt;
  &lt;/entry&gt;
  &lt;entry key="resultFormatAdvanced"&gt;
    &lt;list&gt;
      &lt;value&gt;resultNumAxis&lt;/value&gt;
      &lt;value&gt;resultScaleFactorXy&lt;/value&gt;
      &lt;value&gt;resultScaleFactorZ&lt;/value&gt;
    &lt;/list&gt;
  &lt;/entry&gt;
  &lt;entry key="notification"&gt;
    &lt;list&gt;
      &lt;value&gt;notificationEmail&lt;/value&gt;
      &lt;value&gt;notificationUrl&lt;/value&gt;
    &lt;/list&gt;
  &lt;/entry&gt;
  &lt;entry key="testParameters"&gt;
    &lt;list&gt;
      &lt;value&gt;cpfPlug-inTest&lt;/value&gt;
      &lt;value&gt;cpfMinExecutionTime&lt;/value&gt;
      &lt;value&gt;cpfMeanExecutionTime&lt;/value&gt;
      &lt;value&gt;cpfStandardDeviation&lt;/value&gt;
      &lt;value&gt;cpfMaxExecutionTime&lt;/value&gt;
      &lt;value&gt;cpfMeanNumResults&lt;/value&gt;
    &lt;/list&gt;
  &lt;/entry&gt;
&lt;/map&gt;</pre>
</div>
<b>Plug-in Form Fields by Section Customization</b>
<div class="source">
<pre class="xml">&lt;util:map id="properties"&gt;
  &lt;entry key="demo"&gt;
    &lt;map&gt;
      &lt;entry key="formSectionsMapInstant"&gt;
        &lt;map&gt;
          &lt;entry key="jobParmaters"&gt;
            &lt;list&gt;
              &lt;value&gt;jobParameter1&lt;/value&gt;
            &lt;/list&gt;
          &lt;/entry&gt;
        &lt;/map&gt;
      &lt;/entry&gt;
      &lt;entry key="formSectionsMapSingle"&gt;
        &lt;map&gt;
          &lt;entry key="jobParmaters"&gt;
            &lt;list&gt;
              &lt;value&gt;jobParameter1&lt;/value&gt;
            &lt;/list&gt;
          &lt;/entry&gt;
        &lt;/map&gt;
      &lt;/entry&gt;
    &lt;/map&gt;
  &lt;/entry&gt;
&lt;/util:map&gt;</pre>
</div>
        </td>
      </tr>
      <tr>
        <td><code>inputDataContentType</code> or <code>inputDataFileExtension</code></td>
        <td><code>String</code></td>
        <td>The default value shown on forms for Input Data Content Type which is the MIME type or
        file extension of the input data specified by an inputData or inputDataUrl parameter.</td>
      </tr>
      <tr>
        <td><code>srid</code></td>
        <td><code>int</code></td>
        <td>The default value shown on forms for Srid which is the EPSG coordinate system code of
        the source geometry.</td>
      </tr>
      <tr>
        <td><code>resultDataContentType</code> or <code>resutDataFileExtension</code></td>
        <td><code>String</code></td>
        <td>The default value shown on forms for Result Data Content Type which is the MIME type or
        file extension of the result data specified by an resutData or resutDataUrl parameter.</td>
      </tr>
      <tr>
        <td><code>resultSrid</code></td>
        <td><code>int</code></td>
        <td>The default value shown on forms for Result Coordinate System which is the EPSG
        coordinate system code used for the result geometry.</td>
      </tr>
      <tr>
        <td><code>resultNumAxis</code></td>
        <td><code>int</code></td>
        <td>The default value shown on forms for Result Num Axis which is the number of coordinate
        axis in the result geometry (e.g. 2 for 2D or 3 for 3D).</td>
      </tr>
      <tr>
        <td><code>resultScaleFactorXy</code></td>
        <td><code>double</code></td>
        <td>The default value shown on forms for Result Scale Factor Xy which is the scale factor
        to apply the x, y coordinates. The scale factor is 1 / minimum unit. For example if the
        minimum unit was 1mm (0.001) the scale factor is 1000 (1 / 0.001).</td>
      </tr>
      <tr>
        <td><code>resultScaleFactorZ</code></td>
        <td><code>double</code></td>
        <td>The default value shown on forms for Result Scale Factor Z which is the scale factor
        to apply the z coordinates. The scale factor is 1 / minimum unit. For example if the minimum
        unit was 1mm (0.001) the scale factor is 1000 (1 / 0.001).</td>
      </tr>
    </tbody>
  </table></div>

#### Result File Customization Properties

The following customization properties are supported.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Name</th>
      <th>Type</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>kmlDocumentName</code></td>
      <td><code>String</code></td>
      <td>The text to use as the name of the KML Document. <b>NOTE: Don't use on a per result basis.</b></td>
    </tr>
    <tr>
      <td><code>kmlDocumentDescription</code></td>
      <td><code>String</code></td>
      <td>The text to use as the description of the KML Document. <b>NOTE: Don't use on a per result
      basis.</b></td>
    </tr>
    <tr>
      <td><code>kmlStyle</code></td>
      <td><code>String</code></td>
      <td>A list of one or more KML Style tags containing a custom style for the whole document.
      <b>NOTE: Don't use on a per result basis.</b> External styles should be preferred.</td>
    </tr>
    <tr>
      <td><code>kmlPlacemarkDescription</code></td>
      <td><code>String</code></td>
      <td>The text to use as the description of the KML Placemark created for each result.</td>
    </tr>
    <tr>
      <td><code>kmlPlaceMarkNameAttribute</code></td>
      <td><code>String</code></td>
      <td>The name result attribute to use as the Name of the KML Placemark created for each result.</td>
    </tr>
    <tr>
      <td><code>kmlSnippet</code></td>
      <td><code>String</code></td>
      <td>The text to use as the snippet of the KML Placemark created for each result.</td>
    </tr>
    <tr>
      <td><code>kmlStyleUrl</code></td>
      <td><code>String</code></td>
      <td>The text to use as the styleUrl of the KML Placemark created for each result.</td>
    </tr>
    <tr>
      <td><code>kmlWriteNulls</code></td>
      <td><code>boolean</code></td>
      <td>Flag indicating if attributes will null values are to be included in the KML document.
      By default attributes with null values are NOT included in the extended data. If true then
      the attribute will be included as a &lt;value xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:nil="true" /&gt; tag.</td>
    </tr>
    <tr>
      <td><code>kmlLookAtRange</code></td>
      <td><code>double</code></td>
      <td>The range to include in the look at element. If omitted then it will be calculated
      from the geometry. The calculation for points always returns 1000.</td>
    </tr>
    <tr>
      <td><code>kmlLookAtMinRange</code></td>
      <td><code>double</code></td>
      <td>The minimum value for the range to include in the look at element. This value is used
      if the kmlLookAtRange or calculated range is less than this value.</td>
    </tr>
    <tr>
      <td><code>kmlLookAtMaxRange</code></td>
      <td><code>double</code></td>
      <td>The maximum value for the range to include in the look at element. This value is used
      if the kmlLookAtRange or calculated range is greater than this value.</td>
    </tr>
  </tbody>
</table></div>

See the [kml reference guide](https://developers.google.com/kml/documentation/kmlreference)
for details on how to use the KML attributes.

Result file customization properties can also be specified on a plug-in class or on the result object
for plug-ins that return a list of results. This allows the property to be calculated for
each result. For example the `kmlStyleUrl` may vary based on the type of result.
The following example show the optional method that must be implemented for plug-ins that
wish to define per result configuration properties.

**Example Plug-in Result Customization Properties**

```java
public Map<String, Object> getCustomizationProperties() {
  Map<String, Object> properties = new HashMap<>();
  properties.put("propertyName", "propertyValue");
  String styleId = "demo"; /** Calculated from some field on object */
  properties.put("kmlStyleUrl", "http://gov.bc.ca/kmlStyle.kml#" + styleId);
  return properties;
}
```

### Configuration

Plug-ins can include a default configuration file that contains any configuration that will change
from environment to environment. Such as database connection URLs and passwords. The configuration
file provided in the application is a template that is used to populate the CPF configuration
database with initial values for the administrator to edit with the values for that environment.

> NOTE:** Plug-ins must fully document all supported configuration properties in their readme file
> and other documentation where appropriate.

> **NOTE:** Plug-ins must not require a configuration file to be created on the file
> system and they must not require manual editing of source code for specific environments.
> The same code must be compiled only once for use in all environments.

If a plug-in requires configuration properties the following JSON file must be created in the maven
module for the project.

```
src/main/resources/META-INF/ca.bc.gov.open.cpf.plugin.ConfigProperties.json
```

```json
[
  {
    "name": "name1",
    "type": "string",
    "value": "value1"
  },
  {
    "name": "name2",
    "type": "int",
    "value": "value2"
  },
]
```

The configuration file is a JSON list containing JSON objects for each property. Each configuration
JSON object represents one configuration property and must include the following attributes.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Attribute</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td><code>name</code></td>
      <td>The name of the property (or sub property). The name can either be place-holders in the
      spring configuration file or can override  bean properties from the spring configuration file.
      The recommendation is to reduce the number of place-holders and use overrides instead. Include
      default values in the spring bean files.</td>
    </tr>
    <tr>
      <td><code>type</code></td>
      <td>The Java [data type](reference/dataTypes.html) of the value. Must be the same type as
      the Java set property method.</td>
    </tr>
    <tr>
      <td><code>value</code></td>
      <td>The string value of the property, it must be possible to convert this value to the Java
      data type specified in the type attribute.</td>
    </tr>
  </tbody>
</table></div>

Consider the example of a data source defined with the following bean definition.

```xml
<bean
  id="demoDataSource"
  class=" com.revolsys.jdbc.io.JdbcDataSourceFactoryBean"
  p:url="jdbc:postgresql://localhost:5432/postgres"
  p:username="demo"
  p:password="12345678"
>
  <property
    name="config"
  >
    <map>
      <entry
        key="initialConnections"
        value="0" />
      <entry
        key="maxConnections"
        value="10" />
    </map>
  </property>
</bean>
```

To override the URL and the password the following properties would be created.

```json
[
  {
    "name": "demoDataSource.url",
    "type": "string",
    "value": " jdbc:postgresql://localhost:5432/demo"
  },
  {
    "name": "demo.timeout",
    "type": "int",
    "value": "10"
  },
]
```

Once a module has been deployed the configuration properties can be modified using the CPF admin application.

1. Open the CPF modules admin page.
2. Click on the name of the module in the table, this will open the module view page.
3. Click the Module Config Properties tab to view the current properties.
4. Click the id of a property to view it and then click Edit to change the value.
5. Click the id of a property to view it and then click Edit to change the value or click Add to add a new property.

### Security

The CPF provides a Security service that allows a plug-in to query information about the user,
check group memberships and check permission to access resources.

#### Security Groups

The CPF uses groups of users to manage the security policies for plug-ins. All policies are
associated with security groups as opposed to individual users.

See the CPF Admin Guide for more detail on managing groups using the CPF admin application.

There are three types of user group.

##### User type

The <code>USER_TYPE</code> user groups are virtual groups that indicate the type of user.
In the BC Government infrastructure the following `USER_TYPE` groups are supported.

<div class="simpleDataTable">
  <div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
    <thead>
      <tr>
        <th>Name</th>
        <th>Description</th>
      </tr>
    </thead>
    <tbody>
      <tr>
        <th>BCGOV_ALL</th>
        <td>BC Government All Users</td>
      </tr>
      <tr>
        <th>BCGOV_BUSINESS</th>
        <td>BC Government External Business Users</td>
      </tr>
      <tr>
        <th>BCGOV_EXTERNAL</th>
        <td>BC Government External Users</td>
      </tr>
      <tr>
        <th>BCGOV_INDIVIDUAL</th>
        <td>BC Government External Individual Users</td>
      </tr>
      <tr>
        <th>BCGOV_INTERNAL</th>
        <td>BC Government Internal Users</td>
      </tr>
      <tr>
        <th>BCGOV_VERIFIED_INDIVIDUAL</th>
        <td>BC Government External Verified Individual</td>
      </tr>
    </tbody>
  </table></div>
</div>

##### Global

The <code>GLOBAL</code> user groups are created and managed by the CPF security administrator using
the CPF user groups admin page. These are shared across all the plug-ins in the system.
There are no `GLOBAL` groups installed by default.

##### Plug-in Groups

Each plug-in can also have their own user groups that are specific to that plug-in and not shared
between plug-ins. Module specific groups must have the {Module Name}_ as a prefix (e.g. the partner 
group for the DEMO module would be DEMO_PARTNER). 

1. Open the CPF modules admin page.
2. Click on the name of the module in the table, this will open the module view page.
3. Click the User Groups tab to view the groups available for the module, it will include USER_TYPE,
   GLOBAL and module specific groups.
4. Click the name of a group to view it and then click Edit to change the group name or description.
   Editing is only possible for module specific groups.
5. Or click Add to add a new module user group.
6. On the View user group page click the User Accounts for Group tab to add or remove users from the
   group. Adding or removing users to a group is only possible for module specific groups.

#### Security Permissions

The CPF allows security permissions to be granted to groups of users. Security permissions grant
members of a group permission to perform a specified action on a resource. Negative permissions
that deny access to a resource are not supported.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Attribute</th>
      <th>Example</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>resourceClass</td>
      <td>reportName</td>
      <td>The resourceClass is used to categorize the type of resource that the policy applies to.
      This could be the same name as the plug-in parameter that contains the resource identifier to
      check.</td>
    </tr>
    <tr>
      <td>resourceId</td>
      <td>Demo Report</td>
      <td>The resourceId is the identifier of the resource.</td>
    </tr>
    <tr>
      <td>action</td>
      <td>View</td>
      <td>The name of the action that can be performed on the resource. Use common names such as
      View, Edit for the action name.</td>
    </tr>
  </tbody>
</table></div>

The special value 'All' can be used as a wildcard for the resourceClass, resourceId or action. When 
enforcing permissions the 'All' value will match any requested value for that attribute when the 
plug-in uses the security service to check if a user can access a resource.

Security permissions can be granted using the CPF admin application.

1. Open the CPF modules admin page.
2. Click on the name of the module in the table, this will open the module view page.
3. Click the User Groups tab to view the groups available for the module, it will include USER_TYPE,
   GLOBAL and module specific groups.
4. Click the name of a group to view it and then click Edit to change the group name or description.
   Editing is only possible for module specific groups.
5. On the View user group page click the User Group Permissions tab to add, view and edit
   permissions.

#### Security Configuration File

In addition to manually defining security groups and permissions using the CPF Admin Application a 
plug-in can include a configuration file in the plug-in jar. The following must be created as a 
JSON document.

```
src/main/resources/META-INF/ca.bc.gov.open.cpf.plugin.UserGroups.json
```

The User Groups config file is a JSON list of JSON objects. Each object represents a user group to
be created or to create permissions for. Only groups starting with the {Module Name}_ prefix will
be created. The USER_TYPE or GLOBAL groups must already exist for permissions to be added to them.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Attribute</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>name</td>
      <td>The name of the group to create or grant permissions to (e.g. DEMO_PARTNER).</td>
    </tr>
    <tr>
      <td>permissions</td>
      <td>A JSON list of JSON objects containing the permissions to grant. See table below for list
      of permission attributes.</td>
    </tr>
  </tbody>
</table></div>
    
The following table shows the JSON attributes for a permission object.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Attribute</th>
      <th>Example</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>resourceClass</td>
      <td>reportName</td>
      <td>The resourceClass is used to categorize the type of resource that the policy applies to.
      This could be the same name as the plug-in parameter that contains the resource identifier to
      check.</td>
    </tr>
    <tr>
      <td>resourceId</td>
      <td>Demo Report</td>
      <td>The resourceId is the identifier of the resource.</td>
    </tr>
    <tr>
      <td>action</td>
      <td>View</td>
      <td>The name of the action that can be performed on the resource. Use common names such as
      View, Edit for the action name.</td>
    </tr>
  </tbody>
</table></div>

The following example shows a group called DEMO_PARTNER that will be created if it does not exist.
The members of that group will be granted the permission to View the Demo Report. Members of the
BCGOV_INTERNAL group will be granted permission to View All reports.

```json
[
  {
    "name": "DEMO_PARTNER",
    "permissions": [
      {
        "resourceClass"  : "reportName",
        "resourceId"     : "Demo Report",
        "action"         : "View"
      }
    ]
  },
  {
    "name": BCGOV_INTERNAL,
    "permissions": [
      {
        "resourceClass  : "reportName",
        "resourceId     : "All",
        "action         : "View"
      }
    ]
  }
]
```

#### Business Application Permissions

Plug-ins can control the the users that can use a business application using the
[batchModePermission](cpf-api-plugin/java-api/#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.batchModePermission)
 or
[instantModePermission](cpf-api-plugin/java-api/#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin.instantModePermission)
annotation parameters on the plug-in class.

The permissions can be overridden on the Business Application Edit page in the CPF admin web application.

1. Open the CPF modules admin page.
2. Click on the name of the module in the table, this will open the module view page.
3. Click the Business Applications tab.
4. Click the name of a business application to view it and then click Edit.
5. Edit the Batch Mode Permission or Instant Mode Permission with a valid
   [Spring security expression](https://static.springsource.org/spring-security/site/docs/3.0.x/reference/el-access.html).

#### Request Level Security

Plugi-ins can control access to individual requests in the
[BusinessApplicationPlugin](cpf-api-plugin/java-api/#ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin)
class. See the [SecurityService](cpf-api-plugin/java-api/#ca.bc.gov.open.cpf.plugin.api.security.SecurityService
for more details.

### Testing

The business application plug-in can be tested by creating a test harness, using the CPF web site or
using a CPF client to connect to the CPF web services.

#### CPF Web Site
If the CPF is deployed to /cpf on localhost the list of business applications can be found using the
following page.

(http://localhost/cpf/ws/apps/)

Click on the link for a business application to get the list of resources for that application.
Depending on the plug-in the following resources maybe available.

<div class="table-responsive"><table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>Resource</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr>
      <td>specification</td>
      <td>The HTML page describing the parameters and result attributes of the business application.</td>
    </tr>
    <tr>
      <td>instant</td>
      <td>If the plug-in allows instant mode there will be a form that allows a user to submit a 
      single request and get the results back immediately.</td>
    </tr>
    <tr>
      <td>single</td>
      <td>The single request form allows the parameters for a single request to be entered using 
      form fields insteadof requiring a file of structured input data. Unlike the instant mode the 
      request is processed via a job.</td>
    </tr>
    <tr>
      <td>multiple</td>
      <td>The multiple request form contains fields for the job parameters and accepts a file 
      upload or a URL forthe request data fields.</td>
    </tr>
  </tbody>
</table></div>

Use one of the above forms to create a new job and then download the results to confirm that the
plug-in works as expected.

#### Test Harness
The BusinessApplicationPluginExecutor class allows developers to execute a single request against
their plug-in without deploying it to the CPF infrastructure. The executor converts the input 
parameters to a JSON object and back again to simulate what happens in the internal CPF processing.

To create a test harness construct an instance of
ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPluginExecutor and then use one of the following
methods to invoke the plug-in.
