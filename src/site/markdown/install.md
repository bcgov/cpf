## CPF Installation

Plug-in developers will want to install a copy of CPF on their local environment to develop and
test their CPF plug-ins. The following sections included detailed steps on installing the CPF
database and web application.

## Database Installation

The CPF requires a PostgreSQL or Oracle database to be installed for use by the CPF application.

A local copy of the CPF databases should be deployed at a developer's site. For projects with
multiple developers it is recommended to install a local database on each developer's workstation
and one on a central integration test server.

> **NOTE:** Installation of database for any business application plug-ins is outside the
> scope of the CPF.

### Requirements

The CPF requires a database to store the CPF configuration, Job requests and Job results. The
following databases are currently supported. 

* PostgreSQL 11.5+
* Oracle 12c+


**NOTE:** Older versions may work but haven't been recently tested.

A developer may also require additional databases for use by their plug-in. They must deliver all
required SQL scripts and instructions on how to install these databases.

> **NOTE:** For PostgreSQL the server instance can be shared with other applications but it is recommended
> to have a separate "database" within that instance for CPF. For Oracle there is no such restriction.

### Download SQL Scripts

The SQL scripts to install the database can be downloaded from the https://github.com/bcgov/cpf
repository.

Use the following scripts will download the CPF Oracle and PostgreSQL scripts.

** UNIX/Mac**

```bash
svn co https://github.com/bcgov/cpf/trunk/sql
cd sql
```
  
**Windows**

```winbatch
svn co https://github.com/bcgov/cpf/trunk/sql
cd sql
```

> **NOTE:** If you have previously downloaded the SQL use the following command from the sql
> directory to ensure that you have the latest version.

```
svn up
```

### CPF Database Install Configuration

The database install scripts use the db.properties configuration file for database connection
and configuration parameters. Copy the db-sample.properties file from the postgresql or oracle
directory to use as a template.

```
cd postgresql # or cd oracle
cp db-sample.properties db.properties
chmod 600 db.properties
```

> **NOTE:** Change permissions on the `db.properties` so that only you have read/write permissions on
> the file to keep the passwords secret.

Edit the `db.properties` file.

|Property                |Example Value|Description|
|------------------------|-------------|-----------|
|`DB_HOSTNAME`           |`localhost`  |The hostname of the PostgreSQL sever. Not used for Oracle.|
|`DB_PORT`               |`5432`       |The port of the PostgreSQL sever. Not used for Oracle.|
|`DB_NAME`               |`cpf`        |The PostgreSQL database name or Oracle TNSNAME, tnsnames.ora must be configured.|
|`CPF_PASSWORD`          |`cpf_0wn3r`  |The password to create the CPF database account with.|
|`PROXY_CPF_WEB_PASSWORD`|`c0ncurr3n7` |The password to create the PROXY_CPF_WEB database account with.|
|`TABLESPACE_DIR`        |`/data/postgres/cpf`|The directory to create the database tablespace in. The directory must exist on the server and the PostgreSQL or Oracle process must have write permissions on this directory.|

For PostgreSQL, to avoid needing to enter in the passwords for each SQL command create a `~/.pgpass`
on UNIX or `%APPDATA%\postgresql\pgpass.conf` file on Windows. Set the permissions so that only you
can read/write the `pgpass.conf` file. The file can be deleted after installation if required. The
file should look something like this. Change the last item on the two lines to be the password for
those users.

```
localhost:5432:*:postgres:postgres
localhost:5432:*:cpf:cpf_0wn3r
```
  
### CPF Database and schema install

The CPF installation process will create the following database objects. If the objects already
exist the script will either ignore that step or in the case of CPF tables it will prompt to confirm
that the existing tables should be deleted.

* Create a cpf database for PostgreSQL. An existing Oracle database must already exist.
* Create a `CPF tablespace` to store the CPF data.
* Create a `CPF_WEB_PROXY` database role that will have CRUD permission on the tables.
* Create a `CPF` database user account that this the owner of all the CPF tables. **NOTE:**
  This account must not be used for any other purpose than managing the table definitions.
* Create a `PROXY_CPF_WEB` user account that is used by the CPF web application to access the database.
* Create a `CPF` schema for all of the CPF tables, sequences and indexes.
* Create all the CPF tables, sequences and indexes and grant appropriate permissions for these tables.

NOTE: For Oracle you will need to know the password for the SYSTEM database account, you will be
prompted to enter this in the script.

**Unix/Mac**

```bash
DB_VENDOR={postgresql|oracle}
cd cpf/sql/${DB_VENDOR}
chmod 755 install.sh
./install.sh
```

**Windows**

```winbatch
set DB_VENDOR={postgresql|oracle}
cd  cpf\sql\%DB_VENDOR%
install.cmd
```
  
During the installation script you may be prompted for the following information.


* Passwords for the `SYSTEM` or `postgres` user accounts.
* If the database already exists the following prompt will be displayed. Entering **YES** will delete
  all the existing data and create new tables.  
  ```WARN: Do you want to drop the existing database including all data (YES/NO)?```


> **NOTE:** Contact the CPF developer, if there are any errors while running the script that are NOT
> related to file permissions or unknown user account passwords.

## Web Application Installation

A local copy of the CPF web applications and databases should be deployed at the developer's site.
For projects with multiple developers it is recommended to install a local database and J2EE servlet
container (Apached Tomcat) on each developer's workstation and one on a central integration test server.

The CPF applications are deployed to a J2EE application server or servlet container. To deploy to a
J2EE Servlet container the individual wars are deployed to the J2EE Servlet container.

Deployment is currently supported on [Apache Tomcat > 9.0.x](https://tomcat.apache.org). CPF may work
withother J2EE Servlet or application containers but this has not been tested.

For Tomcat 9.0.x you will need to add a user account in the manager-script role to deploy the web
applications to the tomcat contained. If a user does not exist edit the `tomcat-users.xml`
file in the tomcat conf directory.

```xml
<role rolename="manager-script"/>
<user username="admin" password="..." roles="manager-script"/>
```

### Create CPF directories

The CPF requires directories to be created on the server. The following directories must be created.

> **NOTE**: This assumes the CPF home directory is /apps/cpf. Modify the commands and configuration
> below if a different directory is used.

|Directory                     |User Perms|J2EE Server Perms|Description|
|------------------------------|----------|-----------------|-----------|
|`/apps/cpf/config`            |`rw`      |`r`              |The directory containing the CPF configuration file for the database URL, username and password.|
|`/apps/cpf/log`               |`r`       |`rw`             |The directory to store the CPF logs.|
|`/apps/cpf/repository,/home/{username}/.m2/repository` |`rw`      |`rw`  |The local Maven repository cache. If the J2EE server is on the developers workstation use the user's local maven repository cache.|


Create the directories using the following commands. Make sure the directory permissions are set
as shown in the table above.

**UNIX/Mac**

```bash
mkdir -p /apps/cpf
mkdir -p /apps/cpf/config
mkdir -p /apps/cpf/log
mkdir -p /apps/cpf/repository
```

**Windows**

```winbatch
md \apps\cpf
md \apps\cpf\config
md \apps\cpf\log
md \apps\cpf\repository
```

### Update maven settings.xml to use the CPF maven repository

Edit ~/.m2/settings.xml to include the cpf-artifactory profile

```
<?xml version="1.0" encoding="UTF-8"?>
<settings
  xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.1.0 http://maven.apache.org/xsd/settings-1.1.0.xsd"
  xmlns="http://maven.apache.org/SETTINGS/1.1.0"
  xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
>
  <profiles>

    <profile>
      <id>cpf-artifactory</id>
       <activation>
        <activeByDefault>true</activeByDefault>
      </activation>
      <repositories>
        <repository>
          <id>cpf-release-local</id>
          <name>cpf-release-local</name>
          <releases>
            <enabled>true</enabled>
          </releases>
          <snapshots>
            <enabled>false</enabled>
          </snapshots>
          <url>https://open.revolsys.com/artifactory/cpf-release-local</url>
        </repository>
        <repository>
          <id>cpf-snapshot-local</id>
          <name>cpf-snapshot-local</name>
          <releases>
            <enabled>false</enabled>
          </releases>
          <snapshots>
            <enabled>true</enabled>
          </snapshots>
          <url>https://open.revolsys.com/artifactory/cpf-snapshot-local</url>
        </repository>
      </repositories>
    </profile>

  </profiles>
</settings>

```

### Create a CPF web application project

The CPF application can be configured to connect to different types of database and be configured or
extended in other ways. Therefore instead of delivering a pre-packaged war file a maven project is
created for each installation that contains the configuration for that environment.

Create the maven project using the following maven archetype commands. Replace any parameters
with the correct values for your environment.

> **NOTE:** Java 11 and Maven 3.6+ must be install. JAVA_HOME and M2_HOME must be set and the
> bin directories from both must be in the PATH.

**UNIX/Mac**

```bash
CPF_VERSION=6.1.x-SNAPSHOT
cd ~/projects
mvn \
  archetype:generate \
  -DarchTypeCatalog=cpf-release-local,cpf-snapshot-local,remote,local \
  -DinteractiveMode=false \
  -DarchetypeGroupId=ca.bc.gov.open.cpf \
  -DarchetypeArtifactId=cpf-archetype-web \
  -DarchetypeVersion=${CPF_VERSION} \
  -DgroupId=com.mycompany \
  -DartifactId=cpf \
  -Dversion=1.0.0-SNAPSHOT \
  -DcpfVersion=${CPF_VERSION} \
  -DmodulePrefix=cpf \
  -DdatabaseVendor=postgresql \
  -DdatabasePassword=c0ncurr3n7 \
  -DworkerPassword=cpf_w0rk3r \
  -DcpfLogDirectory=/apps/cpf/log \
  -DcpfDirectoryUrl=file:///apps/cpf \
  -DmavenCacheDirectoryUrl=file:///apps/cpf/repository
```

**Windows**

```winbatch
set CPF_VERSION=6.1.x-SNAPSHOT
cd %HOMEDRIVE%%HOMEPATH%\projects
mvn ^
  archetype:generate ^
  -DarchTypeCatalog=cpf-release-local,cpf-snapshot-local,remote,local ^
  -DinteractiveMode=false ^
  -DarchetypeGroupId=ca.bc.gov.open.cpf ^
  -DarchetypeArtifactId=cpf-archetype-web ^
  -DarchetypeVersion=%CPF_VERSION% ^
  -DgroupId=com.mycompany ^
  -DartifactId=cpf ^
  -Dversion=1.0.0-SNAPSHOT ^
  -DcpfVersion=%CPF_VERSION% ^
  -DmodulePrefix=cpf ^
  -DdatabaseVendor=postgresql ^
  -DdatabasePassword=c0ncurr3n7 ^
  -DworkerPassword=cpf_w0rk3r ^
  -DcpfLogDirectory=C:/apps/cpf/log ^
  -DcpfDirectoryUrl=file:///C:/apps/cpf ^
  -DmavenCacheDirectoryUrl=file:///C:/apps/cpf/repository
```

> **NOTE:** Windows and Unix require commands to be entered on a single line. The \ or ^ character
> are line continuation character that treats multiple lines as a single line. Therefore you can cut
> and paste the above text into a command window.

|Parameter               |Description|
|------------------------|-----------|
|`archetypeVersion`      |The most recent version of the CPF framework.|
|`cpfVersion`      |The most recent version of the CPF framework.|
|`groupId`               |The maven group identifier. This should be your company name if deploying within your development environment.|
|`artifactId`            |The base maven artifact identifier used for the maven modules created in the project.|
|`version`               |The version identifier you’d like to give to your plug-in.|
|`modulePrefix`          |The prefix to use on the web applications.|
|`databaseVendor`        |The database type that the CPF uses for its data. Supported values include postgresql and oracle.|
|`databasePassword`      |The password for the PROXY_CPF_WEB user (PROXY_CPF_WEB_PASSWORD from db.properties). |
|`workerPassword`        |The password for the cpf_worker CPF user account. Default is cpf_w0rk3r. Change if required using the CPF admin application.|
|`cpfLogDirectory`       |The directory for the CPF log files will be stored in (e.g. `/apps/cpf/log` or `C:\apps\cpf\log`).|
|`cpfDirectoryUrl`       |The root directory the CPF configuration file and log files will be stored in (e.g. `file:///apps/cpf` or `file:///C:/apps/cpf`).|
|`mavenCacheDirectoryUrl`|The file URL to local Maven repository cache. **NOTE:** Must start with file:/// and use web slashes / instead of windows slashes \. If the J2EE server is on the developers workstation use the user's local maven repository cache. Otherwise use the repository directory below the `cpfDirectory` defined above (e.g. `file:///apps/cpf/repository` or `file:///C:/apps/cpf/repository`).|


The following directory structure would be created if the command were run using the parameters above.

|File/Directory                            |Description|
|------------------------------------------|-----------|
|`cpf`                                     |The root directory of the web project.|
|`  cpf.app`                               |The maven module for the web application containing the web services and scheduler.|
|`    pom.xml`                             |The maven build file for the web services and scheduler.|
|`    src/main/resources`                  |The resources to be included in the web application jar file.|
|`      cpf-api-properties.sf.xml`         |The configuration file for the CPF API components.|
|`      cpf-web-properties.sf.xml`         |The configuration file for the CPF web components.|
|`    src/main/webapp/META-INF/context.xml`|Tomcat context configuration.|
|`    src/main/webapp/WEB-INF/web.xml`     |The web.xml file.|
|`  cpf.worker`                            |The maven module for the web application containing the worker.|
|`    pom.xml`                             |The maven build file for the worker.|
|`    src/main/resources`                  |The resources to be included in the web applications jar file.|
|`      cpfWorker.json`                    |The configuration file for the worker.|
|`    src/main/webapp/META-INF/context.xml`|Tomcat context configuration.|
|`  pom.xml`                               |The parent maven build file that builds all modules.|
|`  sample-config/cpf.properties`          |A sample config file to copy to `/apps/cpf/conf/`.|


  
> **NOTE:** Developers shouldn't need to edit any of these configuration files. They are 
> populated using the parameters specified in the maven archetype. If you need to change the cpf 
> directory location or the database vendor it's probably easier to delete the project and create a 
> new project using the maven archetype. All database configurations are done using runtime 
> configuration files and plug-in configuration is stored in the database.

### Maven Settings Configuration

A profile must be created in the `~/.m2/settings.xml` for each server environment that the CPF 
components are deployed to. The following example shows a full settings file with a single profile 
for the localhost server.

If you want to deploy the application to multiple servers you can create a profile for each server 
in your `~/.m2/settings.xml`. The profile id should be the name of the server to deploy to. For 
example the following shows a profile for the localhost.

```xml
<settings>
  <profile>
    <id>localhost</id>
    <properties>
      <!-- Include the following for Tomcat deployment -->
      <tomcatManagerUrl>http://localhost:8080/manager/text</tomcatManagerUrl>
      <tomcatManagerUsername>admin</tomcatManagerUsername>
      <tomcatManagerPassword>...</tomcatManagerPassword>
    </properties>
  </profile>
</settings>
```

### CPF Runtime Configuration

Any configuration that changes from one server to another is included in a runtime configuration
file in the `/apps/cpf/config` directory on the server. No environment specific configuration is
included in the compiled WAR files, i.e. the same war could be deployed to delivery, test and
production environments.

Copy the `sample-config/cpf.properties` file to the `/apps/cpf/config/cpf.properties` directory on
the server.

|Property                 |Description|
|-------------------------|-----------|
|`cpfConfig.baseUrl`      |The base URL to the CPF apps web application (e.g. `http://localhost/pub/cpf`).|
|`cpfDataSource.url`      |The full JDBC URL to the CPF database server (e.g. jdbc:postgresql:cpf).|
|`cpfDataSource.password` |The password for the CPF database (e.g. c0ncurr3n7).|
|`cpfWorker.webServiceUrl`|The base url to the internal web services (e.g. `http://localhost:8080/pub/cpf`). Must be the direct tomcat HTTP port and not behind an Apache reverse proxy.|
|`cpfWorker.password`     |The password used in the internal web services (e.g. cpf_w0rk3r). Must be an `http://open.gov.bc.ca/cpf/SystemUser` user in the cpf.cpf_user_accounts table.|

### External Maven Repository Configuration

If the CPF is not deployed on the development server then the worker will need to be configured to
point to the maven respositories that the CPF plug-ins are deployed to.

Edit the following configuration file. You will need to re-build and deploy if this file is modified.

```
~/projects/cpf/cpf.app/src/main/resources/cpf-web-properties.sf.xml
```

Add the following entries to the cpfWebProperties map. The first entry must be modified to the
value used for the mavenCacheDirectory specified above. The second entry value must be the local
file URL or remote http URL to a shared maven repository that you will deploy the plug-ins to.
See [Maven Deploy Plug-in](http://maven.apache.org/plug-ins/maven-deploy-plug-in/) for deploying to
a maven repository.

```xml
<entry
  key="mavenRepository.root"
  value="file:///apps/cpf/repository/"
/>

<entry key="mavenRepository.repositoryLocations">
  <list>
    <value>http://mycompany.com/maven/repository/</value>
  </list>
</entry>
```

### Deploy to Tomcat 9.0

The plug-in project web services &amp; scheduler war and worker war files can be deployed to a
Tomcat 9 server.

Use the following command to compile and deploy to Tomcat.

```
mvn -P tomcat9Deploy,localhost clean install
```

If you created multiple profiles use the profile name of the server you wish to deploy to.

