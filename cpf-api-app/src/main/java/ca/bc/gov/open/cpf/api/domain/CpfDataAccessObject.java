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
package ca.bc.gov.open.cpf.api.domain;

import java.sql.Clob;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import javax.annotation.PreDestroy;
import javax.xml.namespace.QName;

import org.jeometry.common.data.identifier.Identifier;
import org.jeometry.common.data.type.DataType;
import org.jeometry.common.data.type.DataTypes;
import org.jeometry.common.io.PathName;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.PlatformTransactionManager;

import ca.bc.gov.open.cpf.api.scheduler.BusinessApplicationStatistics;
import ca.bc.gov.open.cpf.api.scheduler.DurationType;
import ca.bc.gov.open.cpf.plugin.impl.module.ResourcePermission;

import com.revolsys.collection.list.Lists;
import com.revolsys.collection.map.LruMap;
import com.revolsys.collection.map.Maps;
import com.revolsys.io.FileUtil;
import com.revolsys.io.Reader;
import com.revolsys.io.Writer;
import com.revolsys.jdbc.io.JdbcRecordStore;
import com.revolsys.record.Record;
import com.revolsys.record.RecordState;
import com.revolsys.record.io.RecordWriter;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.record.query.And;
import com.revolsys.record.query.Condition;
import com.revolsys.record.query.In;
import com.revolsys.record.query.Or;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.RecordDefinition;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.transaction.Transaction;
import com.revolsys.transaction.Transactionable;
import com.revolsys.ui.web.controller.PathAliasController;
import com.revolsys.util.Property;

public class CpfDataAccessObject implements Transactionable {
  public static String getUsername() {
    final SecurityContext securityContext = SecurityContextHolder.getContext();
    final Authentication authentication = securityContext.getAuthentication();
    String username;
    if (authentication == null) {
      username = "SYSTEM";
      SecurityContextHolder.clearContext();
    } else {
      username = authentication.getName();
    }
    return username;
  }

  private RecordStore recordStore;

  private RecordDefinition batchJobRecordDefinition;

  private RecordDefinition batchJobResultRecordDefinition;

  private RecordDefinition businessApplicationStatisticsRecordDefinition;

  private RecordDefinition configPropertyRecordDefinition;

  private RecordDefinition userAccountRecordDefinition;

  private RecordDefinition userGroupRecordDefinition;

  private RecordDefinition userGroupAccountXrefRecordDefinition;

  private RecordDefinition userGroupPermissionRecordDefinition;

  private Map<Identifier, BatchJob> batchJobById = new LruMap<>(1000);

  private final Map<String, Set<Identifier>> batchJobIdsByBusinessApplication = new HashMap<>();

  public CpfDataAccessObject() {
  }

  private BatchJob addBatchJob(final Record record, final Identifier batchJobId) {
    final BatchJob batchJob = new BatchJob(record);
    for (final String fieldName : Arrays.asList(BatchJob.BUSINESS_APPLICATION_PARAMS,
      BatchJob.PROPERTIES, BatchJob.COMPLETED_GROUP_RANGE, BatchJob.COMPLETED_GROUP_RANGE,
      BatchJob.FAILED_REQUEST_RANGE)) {
      Object value = record.get(fieldName);
      if (value instanceof Clob) {
        final Clob clob = (Clob)value;
        final RecordState state = record.getState();
        try (
          java.io.Reader reader = clob.getCharacterStream()) {
          value = FileUtil.getString(reader);
          record.setState(RecordState.INITIALIZING);
          record.setValue(fieldName, value);
        } catch (final Throwable e) {
        } finally {
          record.setState(state);
        }
      }
    }
    cacheBatchJob(batchJob, batchJobId);
    return batchJob;
  }

  private void cacheBatchJob(final BatchJob batchJob, final Identifier batchJobId) {
    if (!batchJob.isCancelled()) {
      synchronized (this.batchJobById) {
        this.batchJobById.put(batchJobId, batchJob);
        final String businessApplicationName = batchJob
          .getString(BatchJob.BUSINESS_APPLICATION_NAME);
        Maps.addToSet(this.batchJobIdsByBusinessApplication, businessApplicationName, batchJobId);
      }
    }
  }

  public BatchJob clearBatchJob(final Identifier batchJobId) {
    synchronized (this.batchJobById) {
      final BatchJob batchJob = this.batchJobById.remove(batchJobId);
      if (batchJob != null) {
        final String businessApplicationName = batchJob
          .getString(BatchJob.BUSINESS_APPLICATION_NAME);
        Maps.removeFromCollection(this.batchJobIdsByBusinessApplication, businessApplicationName,
          batchJobId);
      }
      return batchJob;
    }
  }

  public void clearBatchJobs(final String businessApplicationName) {
    synchronized (this.batchJobById) {
      final Set<Identifier> batchJobIds = this.batchJobIdsByBusinessApplication
        .remove(businessApplicationName);
      if (batchJobIds != null) {
        for (final Identifier batchJobId : batchJobIds) {
          this.batchJobById.remove(batchJobId);
        }
      }
    }
  }

  @PreDestroy
  public void close() {
    this.batchJobById = null;
    this.batchJobRecordDefinition = null;
    this.batchJobResultRecordDefinition = null;
    this.businessApplicationStatisticsRecordDefinition = null;
    this.configPropertyRecordDefinition = null;
    this.recordStore = null;
    this.userAccountRecordDefinition = null;
    this.userGroupRecordDefinition = null;
    this.userGroupAccountXrefRecordDefinition = null;
    this.userGroupPermissionRecordDefinition = null;
  }

  public void delete(final Record record) {
    this.recordStore.deleteRecord(record);
  }

  public int deleteBatchJob(final Identifier batchJobId) {
    deleteBatchJobResults(batchJobId);

    final Query query = Query.equal(this.batchJobRecordDefinition, BatchJob.BATCH_JOB_ID,
      batchJobId);
    return this.recordStore.deleteRecords(query);
  }

  public int deleteBatchJobResults(final Identifier batchJobId) {
    final Query query = Query.equal(this.batchJobResultRecordDefinition,
      BatchJobResult.BATCH_JOB_ID, batchJobId);
    return this.recordStore.deleteRecords(query);
  }

  public int deleteBusinessApplicationStatistics(final Identifier businessApplicationStatisticsId) {
    final Query query = Query.equal(this.businessApplicationStatisticsRecordDefinition,
      BusinessApplicationStatistics.APPLICATION_STATISTIC_ID, businessApplicationStatisticsId);
    return this.recordStore.deleteRecords(query);
  }

  public int deleteConfigPropertiesForModule(final String moduleName) {
    final Query query = Query.equal(this.configPropertyRecordDefinition, ConfigProperty.MODULE_NAME,
      moduleName);
    return this.recordStore.deleteRecords(query);
  }

  public void deleteUserAccount(final Record userAccount) {
    final Identifier userAccountId = userAccount.getIdentifier();
    final Query membersQuery = Query.equal(this.userGroupAccountXrefRecordDefinition,
      UserGroupAccountXref.USER_ACCOUNT_ID, userAccountId);
    this.recordStore.deleteRecords(membersQuery);

    final String consumerKey = userAccount.getString(UserAccount.CONSUMER_KEY);
    final Query jobsQuery = Query.equal(this.batchJobRecordDefinition, BatchJob.USER_ID,
      consumerKey);
    this.recordStore.deleteRecords(jobsQuery);

    delete(userAccount);
  }

  public void deleteUserGroup(final Record userGroup) {
    final Identifier userGroupId = userGroup.getIdentifier();

    final Query membersQuery = Query.equal(this.userGroupAccountXrefRecordDefinition,
      UserGroupAccountXref.USER_GROUP_ID, userGroupId);
    this.recordStore.deleteRecords(membersQuery);

    final Query permissionsQuery = Query.equal(this.userGroupPermissionRecordDefinition,
      UserGroupPermission.USER_GROUP_ID, userGroupId);
    this.recordStore.deleteRecords(permissionsQuery);

    delete(userGroup);
  }

  public int deleteUserGroupAccountXref(final Record userGroup, final Record userAccount) {
    final Identifier userGroupId = userGroup.getIdentifier();
    final Identifier userAccountId = userAccount.getIdentifier();

    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(UserGroupAccountXref.USER_GROUP_ID, userGroupId);
    filter.put(UserGroupAccountXref.USER_ACCOUNT_ID, userAccountId);

    final Query query = Query.and(this.userGroupAccountXrefRecordDefinition, filter);
    return this.recordStore.deleteRecords(query);
  }

  public int deleteUserGroupsForModule(final String moduleName) {
    final Query query = Query.equal(this.userGroupRecordDefinition, UserGroup.MODULE_NAME,
      moduleName);
    int i = 0;
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      for (final Record userGroup : reader) {
        deleteUserGroup(userGroup);
        i++;
      }
    }
    return i;
  }

  public synchronized BatchJob getBatchJob(final Identifier batchJobId) {
    if (batchJobId == null) {
      return null;
    } else {
      synchronized (this.batchJobById) {
        BatchJob batchJob = this.batchJobById.get(batchJobId);
        if (batchJob == null) {
          final Record record = this.recordStore.getRecord(BatchJob.BATCH_JOB, batchJobId);
          if (record != null) {
            batchJob = addBatchJob(record, batchJobId);
          }
        }
        return batchJob;
      }
    }
  }

  public BatchJob getBatchJob(final Record record) {
    if (record == null) {
      return null;
    } else if (record instanceof BatchJob) {
      return (BatchJob)record;

    } else {
      synchronized (this.batchJobById) {
        final Identifier batchJobId = record.getIdentifier(BatchJob.BATCH_JOB_ID);
        if (batchJobId == null) {
          return null;
        } else {
          BatchJob batchJob = this.batchJobById.get(batchJobId);
          if (batchJob == null) {
            batchJob = addBatchJob(record, batchJobId);
          }
          return batchJob;
        }
      }
    }
  }

  public Record getBatchJob(final String consumerKey, final Identifier batchJobId) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(BatchJob.USER_ID, consumerKey);
    filter.put(BatchJob.BATCH_JOB_ID, batchJobId);

    final Query query = Query.and(this.batchJobRecordDefinition, filter);
    return this.recordStore.getRecords(query).getFirst();
  }

  public List<Identifier> getBatchJobIds(final String businessApplicationName,
    final String jobStatus) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName);
    filter.put(BatchJob.JOB_STATUS, jobStatus);
    final Query query = Query.and(this.batchJobRecordDefinition, filter);
    query.setFieldNames(BatchJob.BATCH_JOB_ID);
    final Reader<Record> batchJobs = this.recordStore.getRecords(query);
    try {
      final List<Identifier> batchJobIds = new ArrayList<>();
      for (final Record batchJob : batchJobs) {
        final Identifier batchJobId = batchJob.getIdentifier(BatchJob.BATCH_JOB_ID);
        batchJobIds.add(batchJobId);
      }
      return batchJobIds;
    } finally {
      batchJobs.close();
    }
  }

  public List<Identifier> getBatchJobIdsToSchedule(final String businessApplicationName) {
    final Query query = new Query(this.batchJobRecordDefinition);
    query.setFieldNames(BatchJob.BATCH_JOB_ID);
    // TODO move to scheduling groups
    final String where = "JOB_STATUS IN ( 'processing') AND BUSINESS_APPLICATION_NAME = ?";
    query.setWhereCondition(Q.sql(where, businessApplicationName));
    query.addOrderBy(BatchJob.LAST_SCHEDULED_TIMESTAMP, true);
    query.addOrderBy(BatchJob.BATCH_JOB_ID, true);
    try (
      Transaction transaction = this.recordStore.newTransaction();
      final Reader<Record> batchJobs = this.recordStore.getRecords(query);) {
      final List<Identifier> batchJobIds = new ArrayList<>();
      for (final Record batchJob : batchJobs) {
        final Identifier batchJobId = batchJob.getIdentifier(BatchJob.BATCH_JOB_ID);
        batchJobIds.add(batchJobId);
      }
      return batchJobIds;
    }
  }

  public Record getBatchJobLocked(final Identifier batchJobId) {
    return this.recordStore.getRecord(BatchJob.BATCH_JOB, batchJobId);
  }

  public Record getBatchJobResult(final Identifier batchJobId, final long sequenceNumber) {
    final And where = Q.and(Q.equal(BatchJobResult.BATCH_JOB_ID, batchJobId),
      Q.equal(BatchJobResult.SEQUENCE_NUMBER, sequenceNumber));
    final Query query = new Query(BatchJobResult.BATCH_JOB_RESULT, where);
    return this.recordStore.getRecords(query).getFirst();
  }

  public List<Record> getBatchJobResults(final Identifier batchJobId) {
    final Query query = Query.equal(this.batchJobResultRecordDefinition,
      BatchJobResult.BATCH_JOB_ID, batchJobId);
    query.setFieldNames(BatchJobResult.ALL_EXCEPT_BLOB);
    query.addOrderBy(BatchJobResult.SEQUENCE_NUMBER, true);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public List<Record> getBatchJobsForUser(final String consumerKey) {
    final Query query = Query.equal(this.batchJobRecordDefinition, BatchJob.USER_ID, consumerKey);
    query.addOrderBy(BatchJob.BATCH_JOB_ID, false);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public List<Record> getBatchJobsForUserAndApplication(final String consumerKey,
    final String businessApplicationName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(BatchJob.USER_ID, consumerKey);
    filter.put(BatchJob.BUSINESS_APPLICATION_NAME, businessApplicationName);
    final Query query = Query.and(this.batchJobRecordDefinition, filter);

    query.addOrderBy(BatchJob.BATCH_JOB_ID, false);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public List<Record> getConfigPropertiesForAllModules(final String environmentName,
    final String componentName, final String propertyName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(ConfigProperty.ENVIRONMENT_NAME, environmentName);
    filter.put(ConfigProperty.COMPONENT_NAME, componentName);
    filter.put(ConfigProperty.PROPERTY_NAME, propertyName);
    if (this.configPropertyRecordDefinition == null) {
      return Collections.emptyList();
    } else {
      final Query query = Query.and(this.configPropertyRecordDefinition, filter);

      try (
        final Reader<Record> reader = this.recordStore.getRecords(query)) {
        return reader.toList();
      }
    }
  }

  public List<Record> getConfigPropertiesForComponent(final String moduleName,
    final String componentName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(ConfigProperty.MODULE_NAME, moduleName);
    filter.put(ConfigProperty.COMPONENT_NAME, componentName);
    final Query query = Query.and(this.configPropertyRecordDefinition, filter);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public List<Record> getConfigPropertiesForModule(final String environmentName,
    final String moduleName, final String componentName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(ConfigProperty.ENVIRONMENT_NAME, environmentName);
    filter.put(ConfigProperty.MODULE_NAME, moduleName);
    filter.put(ConfigProperty.COMPONENT_NAME, componentName);
    final Query query = Query.and(this.configPropertyRecordDefinition, filter);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public Record getConfigProperty(final String environmentName, final String moduleName,
    final String componentName, final String propertyName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(ConfigProperty.ENVIRONMENT_NAME, environmentName);
    filter.put(ConfigProperty.MODULE_NAME, moduleName);
    filter.put(ConfigProperty.COMPONENT_NAME, componentName);
    filter.put(ConfigProperty.PROPERTY_NAME, propertyName);
    final Query query = Query.and(this.configPropertyRecordDefinition, filter);
    return this.recordStore.getRecords(query).getFirst();
  }

  /**
   * Get all the jobs that are either marked for deletion, or that have had a
   * status change timestamp less than the passed timestamp.
   *
   * @param keepUntilTimestamp The timestamp of the maximum age of the completed
   *          jobs to be retained.
   * @return The batch job ids.
   */
  public List<Identifier> getOldBatchJobIds(final Timestamp keepUntilTimestamp) {
    final Query query = new Query(this.batchJobRecordDefinition);
    query.setFieldNames(BatchJob.BATCH_JOB_ID);
    final And and = new And(
      new In(BatchJob.JOB_STATUS, BatchJobStatus.RESULTS_CREATED, BatchJobStatus.DOWNLOAD_INITIATED,
        BatchJobStatus.CANCELLED),
      Q.lessThan(this.batchJobRecordDefinition.getField(BatchJob.WHEN_STATUS_CHANGED),
        keepUntilTimestamp));
    query.setWhereCondition(and);
    try (
      final Reader<Record> batchJobs = this.recordStore.getRecords(query)) {
      final List<Identifier> batchJobIds = new ArrayList<>();
      for (final Record batchJob : batchJobs) {
        final Identifier batchJobId = batchJob.getIdentifier(BatchJob.BATCH_JOB_ID);
        batchJobIds.add(batchJobId);
      }
      return batchJobIds;
    }
  }

  public RecordStore getRecordStore() {
    return this.recordStore;
  }

  @Override
  public PlatformTransactionManager getTransactionManager() {
    return this.recordStore.getTransactionManager();
  }

  /**
   * Get the user with the specified consumer key.
   *
   * @param consumerKey The external user class.
   * @return The user account if it exists, null otherwise.
   */
  public Record getUserAccount(final String consumerKey) {
    final Query query = Query.equal(this.userAccountRecordDefinition, UserAccount.CONSUMER_KEY,
      consumerKey);
    return this.recordStore.getRecords(query).getFirst();
  }

  /**
   * Get the user with the specified external user class and external user name.
   *
   * @param userClass The external user class.
   * @param userName The external user name.
   * @return The user account if it exists, null otherwise.
   */
  public Record getUserAccount(final String userClass, final String userName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(UserAccount.USER_ACCOUNT_CLASS, userClass);
    filter.put(UserAccount.USER_NAME, userName);
    final Query query = Query.and(this.userAccountRecordDefinition, filter);
    return this.recordStore.getRecords(query).getFirst();
  }

  public List<Record> getUserAccountsLikeName(final String name) {
    if (Property.hasValue(name)) {
      final Condition consumerKeyLike = Q.iLike(UserAccount.CONSUMER_KEY, name);
      final Condition userNameLike = Q.iLike(UserAccount.USER_NAME, name);
      final Condition[] conditions = {
        consumerKeyLike, userNameLike
      };
      final Or or = new Or(conditions);
      final Query query = new Query(UserAccount.USER_ACCOUNT, or);
      try (
        final Reader<Record> reader = this.recordStore.getRecords(query)) {
        return Lists.toArray(reader, 20);
      }
    } else {
      return Collections.emptyList();
    }
  }

  public Record getUserGroup(final long userGroupId) {
    return this.recordStore.getRecord(UserGroup.USER_GROUP, userGroupId);
  }

  public Record getUserGroup(final String groupName) {
    final Query query = Query.equal(this.userGroupRecordDefinition, UserGroup.USER_GROUP_NAME,
      groupName);
    return this.recordStore.getRecords(query).getFirst();
  }

  public Record getUserGroup(final String moduleName, final String groupName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(UserGroup.MODULE_NAME, moduleName);
    filter.put(UserGroup.USER_GROUP_NAME, groupName);
    final Query query = Query.and(this.userGroupRecordDefinition, filter);
    return this.recordStore.getRecords(query).getFirst();
  }

  public Record getUserGroupPermission(final List<String> userGroupNames, final String moduleName,
    final String resourceClass, final String resourceId, final String actionName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(UserGroup.USER_GROUP_NAME, userGroupNames);
    filter.put("T." + UserGroupPermission.MODULE_NAME, moduleName);
    filter.put(UserGroupPermission.RESOURCE_CLASS, resourceClass);
    filter.put(UserGroupPermission.RESOURCE_ID, resourceId);
    filter.put(UserGroupPermission.ACTION_NAME, actionName);
    final Query query = Query.and(this.userGroupPermissionRecordDefinition, filter);
    query.setFromClause("CPF.CPF_USER_GROUP_PERMISSIONS T"
      + " JOIN CPF.CPF_USER_GROUPS G ON T.USER_GROUP_ID = G.USER_GROUP_ID");
    return this.recordStore.getRecords(query).getFirst();
  }

  public List<Record> getUserGroupPermissions(final Record userGroup, final String moduleName) {
    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(UserGroupPermission.USER_GROUP_ID, userGroup.getIdentifier());
    filter.put(UserGroupPermission.MODULE_NAME, moduleName);
    final Query query = Query.and(this.userGroupPermissionRecordDefinition, filter);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public List<Record> getUserGroupsForModule(final String moduleName) {
    final Query query = Query.equal(this.userGroupRecordDefinition, UserGroup.MODULE_NAME,
      moduleName);
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      return reader.toList();
    }
  }

  public Set<Record> getUserGroupsForUserAccount(final Record userAccount) {
    final Query query = new Query(UserGroup.USER_GROUP);
    query.setFromClause("CPF.CPF_USER_GROUPS T"
      + " JOIN CPF.CPF_USER_GROUP_ACCOUNT_XREF X ON T.USER_GROUP_ID = X.USER_GROUP_ID");

    query.setWhereCondition(Q.equal("X.USER_ACCOUNT_ID", userAccount.getIdentifier().getLong(0)));
    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      final List<Record> groups = reader.toList();
      return new LinkedHashSet<>(groups);
    }
  }

  public boolean hasBatchJobUnexecutedJobs(final Identifier batchJobId) {
    final BatchJob batchJob = getBatchJob(batchJobId);
    if (batchJob == null) {
      return false;
    } else {
      return batchJob.hasAvailableGroup();
    }
  }

  private void insertStatistics(final BusinessApplicationStatistics statistics,
    final String businessApplicationName, final DurationType durationType, final Date startTime,
    final String valuesString) {
    final Record applicationStatistics;
    final Identifier databaseId = this.recordStore
      .newPrimaryIdentifier(BusinessApplicationStatistics.APPLICATION_STATISTICS);
    applicationStatistics = this.recordStore
      .newRecord(BusinessApplicationStatistics.APPLICATION_STATISTICS);
    applicationStatistics.setValue(BusinessApplicationStatistics.APPLICATION_STATISTIC_ID,
      databaseId);
    applicationStatistics.setValue(BusinessApplicationStatistics.BUSINESS_APPLICATION_NAME,
      businessApplicationName);
    applicationStatistics.setValue(BusinessApplicationStatistics.START_TIMESTAMP, startTime);
    durationType.setValue(applicationStatistics);
    applicationStatistics.setValue(BusinessApplicationStatistics.STATISTIC_VALUES, valuesString);
    this.recordStore.insertRecord(applicationStatistics);
    statistics.setDatabaseId(databaseId);
  }

  public BatchJob newBatchJob() {
    final Record record = newRecord(BatchJob.BATCH_JOB);
    final Identifier batchJobId = this.recordStore.newPrimaryIdentifier(BatchJob.BATCH_JOB);
    record.setIdentifier(batchJobId);
    final String prefix = PathAliasController.getAlias();
    if (prefix != null) {
      final Map<String, String> properties = new HashMap<>();
      properties.put("webServicePrefix", prefix);
      record.setValue(BatchJob.PROPERTIES, Json.toString(properties));
    }
    record.setValue(BatchJob.NUM_SUBMITTED_GROUPS, 0);
    record.setValue(BatchJob.GROUP_SIZE, 1);
    record.setValue(BatchJob.JOB_STATUS, BatchJobStatus.SUBMITTED);
    final Timestamp now = new Timestamp(System.currentTimeMillis());
    record.setValue(BatchJob.WHEN_STATUS_CHANGED, now);

    final BatchJob batchJob = new BatchJob(record);
    cacheBatchJob(batchJob, batchJobId);
    return batchJob;
  }

  public Record newConfigProperty(final String environmentName, final String moduleName,
    final String componentName, final String propertyName, final Object propertyValue,
    final DataType propertyValueType) {
    final Record configProperty = newRecord(ConfigProperty.CONFIG_PROPERTY);
    configProperty.setValue(ConfigProperty.ENVIRONMENT_NAME, environmentName);
    configProperty.setValue(ConfigProperty.MODULE_NAME, moduleName);
    configProperty.setValue(ConfigProperty.COMPONENT_NAME, componentName);
    configProperty.setValue(ConfigProperty.PROPERTY_NAME, propertyName);
    configProperty.setValue(ConfigProperty.PROPERTY_VALUE_TYPE, propertyValueType.toString());

    setConfigPropertyValue(configProperty, propertyValue);
    write(configProperty);
    return configProperty;
  }

  public Record newRecord(final PathName typeName) {
    return this.recordStore.newRecord(typeName);
  }

  public Record newUserAccount(final String userAccountClass, final String userAccountName,
    final String consumerKey, final String consumerSecret) {
    final Record userAccount = newRecord(UserAccount.USER_ACCOUNT);

    userAccount.setValue(UserAccount.USER_NAME, userAccountName);
    userAccount.setValue(UserAccount.USER_ACCOUNT_CLASS, userAccountClass);
    userAccount.setValue(UserAccount.CONSUMER_KEY, consumerKey);
    userAccount.setValue(UserAccount.CONSUMER_SECRET, consumerSecret);
    userAccount.setValue(UserAccount.ACTIVE_IND, 1);

    write(userAccount);
    return userAccount;
  }

  public Record newUserGroup(final String moduleName, final String groupName,
    final String description) {
    Record userGroup = getUserGroup(moduleName, groupName);
    if (userGroup == null) {
      userGroup = newRecord(UserGroup.USER_GROUP);
      userGroup.setValue(UserGroup.MODULE_NAME, moduleName);
      userGroup.setValue(UserGroup.USER_GROUP_NAME, groupName);
      userGroup.setValue(UserGroup.DESCRIPTION, description);
      userGroup.setValue(UserGroup.ACTIVE_IND, 1);
      write(userGroup);
    }
    return userGroup;
  }

  public Record newUserGroupAccountXref(final Record userGroup, final Record userAccount) {
    final Identifier userGroupId = userGroup.getIdentifier();
    final Identifier userAccountId = userAccount.getIdentifier();

    final Map<String, Object> filter = new LinkedHashMap<>();
    filter.put(UserGroupAccountXref.USER_GROUP_ID, userGroupId);
    filter.put(UserGroupAccountXref.USER_ACCOUNT_ID, userAccountId);

    final Query query = Query.and(this.userGroupAccountXrefRecordDefinition, filter);

    Record userGroupAccountXref = this.recordStore.getRecords(query).getFirst();
    if (userGroupAccountXref == null) {

      userGroupAccountXref = newRecord(UserGroupAccountXref.USER_GROUP_ACCOUNT_XREF);
      userGroupAccountXref.setValue(UserGroupAccountXref.USER_GROUP_ID, userGroupId);
      userGroupAccountXref.setValue(UserGroupAccountXref.USER_ACCOUNT_ID, userAccountId);

      write(userGroupAccountXref);
    }
    return userGroupAccountXref;
  }

  public Record newUserGroupPermission(final Record userGroup, final String groupName,
    final ResourcePermission permission) {
    final Record userGroupPermission = newRecord(UserGroup.USER_GROUP);

    userGroupPermission.setValue(UserGroup.MODULE_NAME, userGroup);
    userGroupPermission.setValue(UserGroup.USER_GROUP_NAME, groupName);
    userGroupPermission.setValue(UserGroup.DESCRIPTION, permission);

    write(userGroupPermission);
    return userGroup;
  }

  public void saveStatistics(final BusinessApplicationStatistics statistics) {
    final Identifier databaseId = statistics.getDatabaseId();
    final String businessApplicationName = statistics.getBusinessApplicationName();
    final DurationType durationType = statistics.getDurationType();
    final Date startTime = statistics.getStartTime();

    final Map<String, Long> values = statistics.toMap();
    if (values.isEmpty()) {
      if (databaseId != null) {
        deleteBusinessApplicationStatistics(databaseId);
      }
    } else {
      final String valuesString = Json.toString(values);

      final Record applicationStatistics;
      if (databaseId == null) {
        insertStatistics(statistics, businessApplicationName, durationType, startTime,
          valuesString);
      } else if (statistics.isModified()) {
        applicationStatistics = this.recordStore
          .getRecord(BusinessApplicationStatistics.APPLICATION_STATISTICS, databaseId);
        if (applicationStatistics == null) {
          insertStatistics(statistics, businessApplicationName, durationType, startTime,
            valuesString);
        } else {
          applicationStatistics.setValue(BusinessApplicationStatistics.STATISTIC_VALUES,
            valuesString);
          this.recordStore.updateRecord(applicationStatistics);
        }
      }

      statistics.setModified(false);
    }
  }

  public boolean setBatchJobFailed(final BatchJob batchJob) {
    final JdbcRecordStore jdbcRecordStore = (JdbcRecordStore)this.recordStore;

    final String sql = "UPDATE CPF.CPF_BATCH_JOBS SET " + "COMPLETED_REQUEST_RANGE = null, "//
      + "FAILED_REQUEST_RANGE = concat('1~', NUM_SUBMITTED_REQUESTS), "//
      + "JOB_STATUS = 'resultsCreated', COMPLETED_TIMESTAMP = ?, WHEN_STATUS_CHANGED = ?, WHEN_UPDATED = ?, WHO_UPDATED = ? "
      + "WHERE JOB_STATUS = 'creatingRequests' AND BATCH_JOB_ID = ?";
    try {
      final Timestamp now = new Timestamp(System.currentTimeMillis());
      final String username = getUsername();
      final Long batchJobId = batchJob.getIdentifier().getLong(0);
      final int updateCount = jdbcRecordStore.executeUpdate(sql, now, now, now, username,
        batchJobId);
      batchJob.setValue(BatchJob.JOB_STATUS, BatchJobStatus.RESULTS_CREATED);
      batchJob.setValue(BatchJob.COMPLETED_REQUEST_RANGE, null);
      batchJob.setValue(BatchJob.FAILED_REQUEST_RANGE,
        "1~" + batchJob.getValue(BatchJob.NUM_SUBMITTED_REQUESTS));
      batchJob.setValue(BatchJob.COMPLETED_TIMESTAMP, now);
      batchJob.setValue(BatchJob.WHEN_STATUS_CHANGED, now);
      batchJob.setValue(BatchJob.WHEN_UPDATED, now);
      batchJob.setValue(BatchJob.WHO_UPDATED, username);
      return updateCount == 1;
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to set started status", e);
    }
  }

  public boolean setBatchJobRequestsFailed(final Identifier batchJobId,
    final int numSubmittedRequests, final int numFailedRequests, final int groupSize,
    final int numGroups) {
    final JdbcRecordStore jdbcRecordStore = (JdbcRecordStore)this.recordStore;
    final String sql = "UPDATE CPF.CPF_BATCH_JOBS SET " //
      + "NUM_SUBMITTED_REQUESTS = ?, "//
      + "FAILED_REQUEST_RANGE = ?, "//
      + "GROUP_SIZE = ?, "//
      + "NUM_SUBMITTED_GROUPS = ?, "//
      + "LAST_SCHEDULED_TIMESTAMP = ?, "//
      + "WHEN_STATUS_CHANGED = ?, "//
      + "WHEN_UPDATED = ?, "//
      + "WHO_UPDATED = ?  "//
      + "WHERE JOB_STATUS = ('creatingRequests', 'processed') AND BATCH_JOB_ID = ?";
    final Timestamp now = new Timestamp(System.currentTimeMillis());
    final String username = getUsername();
    final Long id = batchJobId.getLong(0);
    final boolean result = jdbcRecordStore.executeUpdate(sql, numSubmittedRequests,
      numFailedRequests, groupSize, numGroups, now, now, now, username, id) == 1;
    return result;
  }

  public void setConfigPropertyValue(final Record object, final Object value) {
    if (value == null) {
      object.setValue(ConfigProperty.PROPERTY_VALUE, value);
    } else {
      final String stringValue;
      final String valueType = object.getValue(ConfigProperty.PROPERTY_VALUE_TYPE);
      final DataType dataType = DataTypes.getDataType(QName.valueOf(valueType));
      if (dataType == null) {
        stringValue = value.toString();
      } else {
        stringValue = dataType.toString(value);
      }
      if (Property.hasValue(stringValue)) {
        object.setValue(ConfigProperty.PROPERTY_VALUE, stringValue);
      } else {
        object.setValue(ConfigProperty.PROPERTY_VALUE, null);
      }
    }
  }

  public void setRecordStore(final RecordStore recordStore) {
    this.recordStore = recordStore;
    this.batchJobRecordDefinition = recordStore.getRecordDefinition(BatchJob.BATCH_JOB);
    this.batchJobResultRecordDefinition = recordStore
      .getRecordDefinition(BatchJobResult.BATCH_JOB_RESULT);
    this.businessApplicationStatisticsRecordDefinition = recordStore
      .getRecordDefinition(BusinessApplicationStatistics.APPLICATION_STATISTICS);
    this.configPropertyRecordDefinition = recordStore
      .getRecordDefinition(ConfigProperty.CONFIG_PROPERTY);
    this.userAccountRecordDefinition = recordStore.getRecordDefinition(UserAccount.USER_ACCOUNT);
    this.userGroupRecordDefinition = recordStore.getRecordDefinition(UserGroup.USER_GROUP);
    this.userGroupPermissionRecordDefinition = recordStore
      .getRecordDefinition(UserGroupPermission.USER_GROUP_PERMISSION);
    this.userGroupAccountXrefRecordDefinition = recordStore
      .getRecordDefinition(UserGroupAccountXref.USER_GROUP_ACCOUNT_XREF);
  }

  /**
   * Update the status to processed of all the {@link BatchJob}s which are in
   * the processed status and have the request execution/failed counts equal to
   * the number of submitted requests.
   *
   * @param businessApplicationName The business application names to update
   *          the status for.
   * @return The number of records updated.
   */
  public int updateBatchJobProcessedStatus(final String businessApplicationName) {
    // if (this.recordStore instanceof JdbcRecordStore) {
    // final JdbcRecordStore jdbcRecordStore =
    // (JdbcRecordStore)this.recordStore;
    // final String sql = "UPDATE CPF.CPF_BATCH_JOBS BJ SET "
    // + "JOB_STATUS = 'processed', WHEN_STATUS_CHANGED = ?, WHEN_UPDATED = ?,
    // WHO_UPDATED = 'SYSTEM' "
    // + "WHERE JOB_STATUS = 'processing' AND BUSINESS_APPLICATION_NAME = ? AND
    // COMPLETED_REQUEST_RANGE + FAILED_REQUEST_RANGE = NUM_SUBMITTED_REQUESTS";
    // try {
    // final Timestamp now = new Timestamp(System.currentTimeMillis());
    // return JdbcUtils.executeUpdate(jdbcRecordStore, sql, now, now,
    // businessApplicationName);
    // } catch (final Throwable e) {
    // throw new RuntimeException("Unable to update status: " + sql, e);
    // }
    // }

    return 0;
  }

  public int updateJobUserId(final String oldUserId, final String newUserId) {
    final JdbcRecordStore jdbcRecordStore = (JdbcRecordStore)this.recordStore;
    final String sql = "UPDATE CPF.CPF_BATCH_JOBS SET USER_ID = ? WHERE USER_ID = ?";
    try {
      return jdbcRecordStore.executeUpdate(sql, newUserId, oldUserId);
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to change jobs for user rename", e);
    }
  }

  public void write(final Record record) {
    try (
      final RecordWriter writer = this.recordStore.newRecordWriter()) {
      write(writer, record);
    }
  }

  protected void write(final Writer<Record> writer, final Record record) {
    final String username = getUsername();
    final Timestamp time = new Timestamp(System.currentTimeMillis());
    switch (record.getState()) {
      case NEW:
        final RecordDefinition recordDefinition = record.getRecordDefinition();

        if (recordDefinition.getIdFieldIndex() != -1 && record.getIdentifier() == null) {
          final Identifier id = this.recordStore
            .newPrimaryIdentifier(recordDefinition.getPathName());
          record.setIdentifier(id);
        }
        record.setValue(Common.WHO_CREATED, username);
        record.setValue(Common.WHEN_CREATED, time);
        record.setValue(Common.WHO_UPDATED, username);
        record.setValue(Common.WHEN_UPDATED, time);
      break;
      case MODIFIED:
        record.setValue(Common.WHO_UPDATED, username);
        record.setValue(Common.WHEN_UPDATED, time);
      break;
      default:
      break;
    }
    writer.write(record);
  }
}
