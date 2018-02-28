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
package ca.bc.gov.open.cpf.api.scheduler;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import javax.annotation.Resource;

import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;

import com.revolsys.identifier.Identifier;
import com.revolsys.io.Reader;
import com.revolsys.logging.Logs;
import com.revolsys.parallel.channel.Channel;
import com.revolsys.parallel.process.BaseInProcess;
import com.revolsys.record.Record;
import com.revolsys.record.io.format.json.Json;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Property;

public class StatisticsService extends BaseInProcess<Map<String, ? extends Object>> {
  public static final String COLLATE = "COLLATE";

  public static final String SAVE = "SAVE";

  private Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId = new HashMap<>();

  private RecordStore recordStore;

  @Resource
  private CpfDataAccessObject dataAccessObject;

  @Resource
  private BatchJobService batchJobService;

  public StatisticsService() {
    setInBufferSize(100000);
  }

  protected void addStatisticRollUp(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId,
    final String businessApplicationName, final String statisticsId,
    final Map<String, ? extends Object> values) {
    if (statisticsId != null) {
      final BusinessApplicationStatistics statistics = getStatistics(statisticsByAppAndId,
        businessApplicationName, statisticsId);
      statistics.addStatistics(values);
      final String parentStatisticsId = statistics.getParentId();
      addStatisticRollUp(statisticsByAppAndId, businessApplicationName, parentStatisticsId, values);
    }
  }

  public void addStatistics(final BusinessApplication businessApplication,
    final Map<String, Object> values) {
    values.put("businessApplicationName", businessApplication.getName());
    values.put("time", new Date(System.currentTimeMillis()));
    sendStatistics(values);
  }

  protected void addStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId,
    final BusinessApplicationStatistics statistics) {
    final String statisticsId = statistics.getId();
    final String businessApplicationName = statistics.getBusinessApplicationName();
    final Map<String, BusinessApplicationStatistics> statisticsById = getStatistics(
      statisticsByAppAndId, businessApplicationName);
    final BusinessApplicationStatistics previousStatistics = statisticsById.get(statisticsId);
    if (previousStatistics == null) {
      statisticsById.put(statisticsId, statistics);
    } else {
      previousStatistics.addStatistics(statistics);
      if (previousStatistics.getDatabaseId() == null) {
        final Identifier databaseId = statistics.getDatabaseId();
        previousStatistics.setDatabaseId(databaseId);
      }
    }
    addStatisticRollUp(statisticsByAppAndId, businessApplicationName, statistics.getParentId(),
      statistics.toMap());
  }

  public boolean canDeleteStatistic(final BusinessApplicationStatistics statistics,
    final Date currentTime) {
    final DurationType durationType = statistics.getDurationType();

    if (BusinessApplicationStatistics.MONTH_OR_YEAR.contains(durationType)) {
      return false;
    } else {
      final DurationType parentDurationType = statistics.getParentDurationType();
      final String parentId = statistics.getParentId();
      final String currentParentId = parentDurationType.getId(currentTime);
      return parentId.compareTo(currentParentId) < 0;
    }
  }

  protected void collateAllStatistics() {
    try (
      Transaction transaction = this.dataAccessObject.newTransaction(Propagation.REQUIRES_NEW)) {
      try {
        final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId = new HashMap<>();

        collateInMemoryStatistics(statisticsByAppAndId);

        collateDatabaseStatistics(statisticsByAppAndId);

        collateYearStatistics(statisticsByAppAndId);

        saveStatistics(statisticsByAppAndId);

        setStatisticsByAppAndId(statisticsByAppAndId);
      } catch (final Throwable e) {
        throw transaction.setRollbackOnly(e);
      }
    } catch (final Throwable e) {
      Logs.error(this, "Unable to collate statistics", e);
    }
  }

  private void collateDatabaseStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId) {
    final Query query = new Query(BusinessApplicationStatistics.APPLICATION_STATISTICS);
    query.addOrderBy(BusinessApplicationStatistics.START_TIMESTAMP, true);

    try (
      final Reader<Record> reader = this.recordStore.getRecords(query)) {
      for (final Record statisticsRecord : reader) {
        boolean delete = false;
        final Date startTime = statisticsRecord
          .getValue(BusinessApplicationStatistics.START_TIMESTAMP);
        final DurationType durationType = DurationType.getDurationType(statisticsRecord);
        final String businessApplicationName = statisticsRecord
          .getValue(BusinessApplicationStatistics.BUSINESS_APPLICATION_NAME);
        final BusinessApplication businessApplication = this.batchJobService
          .getBusinessApplication(businessApplicationName);
        if (businessApplication != null) {
          final String statisticsId = durationType.getId(startTime);
          final String valuesString = statisticsRecord
            .getValue(BusinessApplicationStatistics.STATISTIC_VALUES);
          if (Property.hasValue(valuesString)) {
            final Map<String, Object> values = Json.toObjectMap(valuesString);
            if (values.isEmpty()) {
              delete = true;
            } else {
              final BusinessApplicationStatistics statistics = getStatistics(statisticsByAppAndId,
                businessApplicationName, statisticsId);

              final Identifier databaseId = statisticsRecord
                .getIdentifier(BusinessApplicationStatistics.APPLICATION_STATISTIC_ID);
              final Identifier previousDatabaseId = statistics.getDatabaseId();
              if (previousDatabaseId == null) {
                statistics.setDatabaseId(databaseId);
                statistics.addStatistics(values);
                final String parentStatisticsId = statistics.getParentId();
                addStatisticRollUp(statisticsByAppAndId, businessApplicationName,
                  parentStatisticsId, values);
              } else if (!databaseId.equals(previousDatabaseId)) {
                statistics.addStatistics(values);
                final String parentStatisticsId = statistics.getParentId();
                addStatisticRollUp(statisticsByAppAndId, businessApplicationName,
                  parentStatisticsId, values);
                delete = true;
              }
            }
          } else {
            delete = true;
          }

          if (delete && !BusinessApplicationStatistics.MONTH_OR_YEAR.contains(durationType)) {
            this.recordStore.deleteRecord(statisticsRecord);
          }
        }
      }
    }
  }

  private void collateInMemoryStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId) {
    final Map<String, Map<String, BusinessApplicationStatistics>> oldStatistics = getStatisticsByAppAndId();
    for (final Map<String, BusinessApplicationStatistics> statsById : oldStatistics.values()) {
      for (final BusinessApplicationStatistics statistics : statsById.values()) {
        final DurationType durationType = statistics.getDurationType();
        if (durationType == DurationType.HOUR) {
          final Identifier databaseId = statistics.getDatabaseId();
          if (databaseId == null || statistics.isModified()) {
            addStatistics(statisticsByAppAndId, statistics);
          }
        }
      }
    }
  }

  public void collateStatistics() {
    final Map<String, ?> values = Collections.singletonMap(COLLATE, Boolean.TRUE);
    sendStatistics(values);
  }

  private void collateYearStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId) {
    for (final Entry<String, Map<String, BusinessApplicationStatistics>> entry : statisticsByAppAndId
      .entrySet()) {
      final String businessApplicationName = entry.getKey();
      final Map<String, BusinessApplicationStatistics> statsById = entry.getValue();
      final Set<String> yearsWithMonthStats = new HashSet<>();
      for (final BusinessApplicationStatistics statistics : statsById.values()) {
        if (DurationType.MONTH == statistics.getDurationType()) {
          final String yearId = statistics.getParentId();
          final BusinessApplicationStatistics yearStatistics = getStatistics(statisticsByAppAndId,
            businessApplicationName, yearId);
          if (yearsWithMonthStats.add(yearId)) {
            yearStatistics.clearStatistics();
          }
          yearStatistics.addStatistics(statistics);
        }
      }
    }
  }

  @Override
  @PreDestroy
  protected void destroy() {
    saveAllStatistics();
    getIn().writeDisconnect();
    this.statisticsByAppAndId.clear();
  }

  protected Map<String, BusinessApplicationStatistics> getStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId,
    final String businessApplicationName) {
    Map<String, BusinessApplicationStatistics> statistics = statisticsByAppAndId
      .get(businessApplicationName);
    if (statistics == null) {
      statistics = new HashMap<>();
      statisticsByAppAndId.put(businessApplicationName, statistics);
    }
    return statistics;
  }

  protected BusinessApplicationStatistics getStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId,
    final String businessApplicationName, final String statisticsId) {
    final Map<String, BusinessApplicationStatistics> statisticsById = getStatistics(
      statisticsByAppAndId, businessApplicationName);
    BusinessApplicationStatistics statistics = statisticsById.get(statisticsId);
    if (statistics == null) {
      statistics = new BusinessApplicationStatistics(businessApplicationName, statisticsId);
      statisticsById.put(statisticsId, statistics);
    }
    return statistics;
  }

  public BusinessApplicationStatistics getStatistics(final String businessApplicationName,
    final String statisticsId) {
    synchronized (this.statisticsByAppAndId) {
      return getStatistics(this.statisticsByAppAndId, businessApplicationName, statisticsId);
    }
  }

  public Map<String, Map<String, BusinessApplicationStatistics>> getStatisticsByAppAndId() {
    return this.statisticsByAppAndId;
  }

  public List<BusinessApplicationStatistics> getStatisticsList(
    final String businessApplicationName) {
    synchronized (this.statisticsByAppAndId) {
      final Map<String, BusinessApplicationStatistics> statisticsById = getStatistics(
        this.statisticsByAppAndId, businessApplicationName);
      return new ArrayList<>(statisticsById.values());
    }
  }

  @Override
  @PostConstruct
  public void init() {
    this.recordStore = this.dataAccessObject.getRecordStore();
    getIn().writeConnect();
  }

  @Override
  protected void preRun(final Channel<Map<String, ? extends Object>> in) {
    super.preRun(in);
    collateAllStatistics();
  }

  @Override
  protected void process(final Channel<Map<String, ? extends Object>> in,
    final Map<String, ? extends Object> values) {
    try {
      if (Boolean.TRUE == values.get(COLLATE)) {
        collateAllStatistics();
      } else if (Boolean.TRUE == values.get(SAVE)) {
        @SuppressWarnings("unchecked")
        final List<String> businessApplicationNames = (List<String>)values
          .get("businessApplicationNames");
        saveStatistics(businessApplicationNames);
      } else {
        final Date time = (Date)values.get("time");
        final String businessApplicationName = (String)values.get("businessApplicationName");
        for (final DurationType durationType : BusinessApplicationStatistics.DURATION_TYPES) {
          final String statisticsId = durationType.getId(time);
          final BusinessApplicationStatistics statistics = getStatistics(businessApplicationName,
            statisticsId);
          statistics.addStatistics(values);
        }
      }
    } catch (final Throwable e) {
      Logs.error(this, "Unable to save statistics:" + values, e);

    }
  }

  protected void saveAllStatistics() {
    List<String> businessApplicationNames;
    synchronized (this.statisticsByAppAndId) {
      businessApplicationNames = new ArrayList<>(this.statisticsByAppAndId.keySet());
    }
    saveStatistics(businessApplicationNames);
  }

  protected void saveStatistics(final Collection<String> businessApplicationNames) {
    for (final String businessApplicationName : businessApplicationNames) {
      Map<String, BusinessApplicationStatistics> statisticsById;
      synchronized (this.statisticsByAppAndId) {
        statisticsById = this.statisticsByAppAndId.remove(businessApplicationName);
      }
      if (statisticsById != null) {
        for (final BusinessApplicationStatistics statistics : statisticsById.values()) {
          final DurationType durationType = statistics.getDurationType();
          if (durationType == DurationType.HOUR) {
            this.dataAccessObject.saveStatistics(statistics);
          }
        }
      }
    }
  }

  protected void saveStatistics(final Map<String, BusinessApplicationStatistics> statsById,
    final Date currentTime) {
    for (final Iterator<BusinessApplicationStatistics> iterator = statsById.values()
      .iterator(); iterator.hasNext();) {
      final BusinessApplicationStatistics statistics = iterator.next();
      if (canDeleteStatistic(statistics, currentTime)) {
        iterator.remove();
        final Identifier databaseId = statistics.getDatabaseId();
        if (databaseId != null) {
          this.dataAccessObject.deleteBusinessApplicationStatistics(databaseId);
        }
      } else {
        final DurationType durationType = statistics.getDurationType();
        final String currentId = durationType.getId(currentTime);
        if (!currentId.equals(statistics.getId())) {
          this.dataAccessObject.saveStatistics(statistics);
        } else {
          final Identifier databaseId = statistics.getDatabaseId();
          if (databaseId != null) {
            this.dataAccessObject.deleteBusinessApplicationStatistics(databaseId);
          }
        }
      }
    }
  }

  protected void saveStatistics(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId) {
    final Date currentTime = new Date(System.currentTimeMillis());
    for (final Map<String, BusinessApplicationStatistics> statsById : statisticsByAppAndId
      .values()) {
      saveStatistics(statsById, currentTime);
    }
  }

  public void scheduleSaveStatistics(final List<String> businessApplicationNames) {
    final Map<String, Object> values = new HashMap<>();
    values.put(SAVE, Boolean.TRUE);
    values.put("businessApplicationNames", businessApplicationNames);
    sendStatistics(values);
  }

  private void sendStatistics(final Map<String, ?> values) {
    final Channel<Map<String, ? extends Object>> in = getIn();
    if (!in.isClosed()) {
      in.write(values);
    }
  }

  private void setStatisticsByAppAndId(
    final Map<String, Map<String, BusinessApplicationStatistics>> statisticsByAppAndId) {
    this.statisticsByAppAndId = statisticsByAppAndId;
  }

  public long updateGroupStatistics(final BatchJobRequestExecutionGroup group,
    final BusinessApplication businessApplication, final String moduleName,
    final long applicationExecutedTime, final long groupExecutedTime, final int successCount,
    final int errorCount) {
    final Map<String, Object> appExecutedStatistics = new HashMap<>();
    appExecutedStatistics.put("applicationExecutedGroupsCount", 1);
    appExecutedStatistics.put("applicationExecutedRequestsCount", successCount + errorCount);
    appExecutedStatistics.put("applicationExecutedFailedRequestsCount", errorCount);
    appExecutedStatistics.put("applicationExecutedTime", applicationExecutedTime);
    appExecutedStatistics.put("executedTime", groupExecutedTime);

    addStatistics(businessApplication, appExecutedStatistics);

    final long executionStartTime = group.getExecutionStartTime();
    final long durationInMillis = System.currentTimeMillis() - executionStartTime;
    final Map<String, Object> executedStatistics = new HashMap<>();
    executedStatistics.put("executedGroupsCount", 1);
    executedStatistics.put("executedRequestsCount", successCount + errorCount);
    executedStatistics.put("executedTime", durationInMillis);

    Transaction.afterCommit(() -> addStatistics(businessApplication, executedStatistics));

    group.setNumCompletedRequests(successCount);
    group.setNumFailedRequests(errorCount);
    return durationInMillis;
  }
}
