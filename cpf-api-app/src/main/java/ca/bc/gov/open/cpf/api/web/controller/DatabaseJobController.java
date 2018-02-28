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
package ca.bc.gov.open.cpf.api.web.controller;

import java.io.InputStream;
import java.sql.Blob;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import ca.bc.gov.open.cpf.api.domain.BatchJob;
import ca.bc.gov.open.cpf.api.domain.BatchJobFile;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;
import ca.bc.gov.open.cpf.api.scheduler.DatabasePreProcessGroup;
import ca.bc.gov.open.cpf.api.scheduler.JobPreProcessTask;
import ca.bc.gov.open.cpf.api.scheduler.PreProcessGroup;
import ca.bc.gov.open.cpf.plugin.impl.BusinessApplication;

import com.revolsys.collection.map.LinkedHashMapEx;
import com.revolsys.collection.map.MapEx;
import com.revolsys.identifier.Identifier;
import com.revolsys.logging.Logs;
import com.revolsys.record.Record;
import com.revolsys.record.io.RecordReader;
import com.revolsys.record.query.Q;
import com.revolsys.record.query.Query;
import com.revolsys.record.schema.RecordStore;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;
import com.revolsys.util.Exceptions;

public class DatabaseJobController extends AbstractJobController {
  private final RecordStore recordStore;

  public DatabaseJobController(final CpfDataAccessObject dataAccessObject) {
    super(dataAccessObject);
    this.recordStore = dataAccessObject.getRecordStore();
  }

  @Override
  protected String getFileContentType(final Identifier jobId, final String path,
    final int sequenceNumber) {
    final Query query = new Query(BatchJobFile.BATCH_JOB_FILE);
    query.setFieldNames(BatchJobFile.CONTENT_TYPE);
    query.and(Q.equal(BatchJobFile.BATCH_JOB_ID, jobId));
    query.and(Q.equal(BatchJobFile.FILE_TYPE, path));
    query.and(Q.equal(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber));
    final Record file = this.recordStore.getRecords(query).getFirst();
    if (file != null) {
      return file.getValue(BatchJobFile.CONTENT_TYPE);
    }
    return null;
  }

  @Override
  public List<MapEx> getFiles(final Identifier jobId, final String path) {
    final Query query = new Query(BatchJobFile.BATCH_JOB_FILE);
    query.and(Q.equal(BatchJobFile.BATCH_JOB_ID, jobId));
    query.and(Q.equal(BatchJobFile.FILE_TYPE, path));
    final List<MapEx> files = new ArrayList<>();
    try (
      RecordReader records = this.recordStore.getRecords(query)) {
      for (final Record record : records) {
        final MapEx result = new LinkedHashMapEx();
        final Blob resultData = record.getValue(BatchJobFile.DATA);
        result.put("batchJobId", jobId);
        result.put("filePath", path);
        result.put("sequenceNumber", record.getInteger(BatchJobFile.SEQUENCE_NUMBER));
        result.put("contentType", record.getString(BatchJobFile.CONTENT_TYPE));
        if (resultData == null) {
          result.put("size", "0");
        } else {
          result.put("size", resultData.length());
        }
        files.add(result);
      }
    } catch (final SQLException e) {
      Logs.error(this, "Unable to list files: " + jobId + " " + path, e);
    }
    return files;
  }

  @Override
  protected long getFileSize(final Identifier jobId, final String path, final int sequenceNumber) {
    final Query query = new Query(BatchJobFile.BATCH_JOB_FILE);
    query.setFieldNames(BatchJobFile.DATA);
    query.and(Q.equal(BatchJobFile.BATCH_JOB_ID, jobId));
    query.and(Q.equal(BatchJobFile.FILE_TYPE, path));
    query.and(Q.equal(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber));
    final Record file = this.recordStore.getRecords(query).getFirst();
    if (file != null) {
      try {
        final Blob resultData = file.getValue(BatchJobFile.DATA);
        return resultData.length();
      } catch (final SQLException e) {
        throw Exceptions.wrap(e);
      }
    }
    return 0;
  }

  @Override
  protected InputStream getFileStream(final Identifier jobId, final String path,
    final int sequenceNumber) {
    final Query query = new Query(BatchJobFile.BATCH_JOB_FILE);
    query.setFieldNames(BatchJobFile.DATA);
    query.and(Q.equal(BatchJobFile.BATCH_JOB_ID, jobId));
    query.and(Q.equal(BatchJobFile.FILE_TYPE, path));
    query.and(Q.equal(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber));
    final Record file = this.recordStore.getRecords(query).getFirst();
    if (file != null) {
      try {
        final Blob resultData = file.getValue(BatchJobFile.DATA);
        return resultData.getBinaryStream();
      } catch (final SQLException e) {
        throw Exceptions.wrap(e);
      }
    }
    return null;
  }

  @Override
  protected InputStream getFileStream(final Identifier jobId, final String path,
    final int sequenceNumber, final long fromIndex, final long toIndex) {
    final Query query = new Query(BatchJobFile.BATCH_JOB_FILE);
    query.setFieldNames(BatchJobFile.DATA);
    query.and(Q.equal(BatchJobFile.BATCH_JOB_ID, jobId));
    query.and(Q.equal(BatchJobFile.FILE_TYPE, path));
    query.and(Q.equal(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber));
    final Record file = this.recordStore.getRecords(query).getFirst();
    if (file != null) {
      try {
        final Blob resultData = file.getValue(BatchJobFile.DATA);
        return resultData.getBinaryStream(fromIndex, toIndex - fromIndex);
      } catch (final SQLException e) {
        throw Exceptions.wrap(e);
      }
    }
    return null;
  }

  @Override
  public String getGroupInputString(final Identifier jobId, final int sequenceNumber) {
    try (
      Transaction transaction = this.recordStore.newTransaction(Propagation.REQUIRED)) {
      return super.getGroupInputString(jobId, sequenceNumber);
    }
  }

  @Override
  public String getKey() {
    return "database";
  }

  @Override
  public void newJobFile(final Identifier jobId, final String path, final long sequenceNumber,
    final String contentType, final Object data) {
    final CpfDataAccessObject dataAccessObject = getDataAccessObject();
    try (
      Transaction transaction = dataAccessObject.newTransaction(Propagation.REQUIRED)) {
      final Query deleteQuery = new Query(BatchJobFile.BATCH_JOB_FILE, Q.and(//
        Q.equal(BatchJobFile.BATCH_JOB_ID, jobId), //
        Q.equal(BatchJobFile.FILE_TYPE, path), //
        Q.equal(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber) //
      ));
      final RecordStore recordStore = dataAccessObject.getRecordStore();
      recordStore.deleteRecords(deleteQuery);
      final Record result = dataAccessObject.newRecord(BatchJobFile.BATCH_JOB_FILE);
      result.setValue(BatchJobFile.BATCH_JOB_ID, jobId);
      result.setValue(BatchJobFile.FILE_TYPE, path);
      result.setValue(BatchJobFile.CONTENT_TYPE, contentType);
      result.setValue(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber);
      result.setValue(BatchJobFile.DATA, data);
      dataAccessObject.write(result);
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to create file", e);
    }
  }

  @Override
  public PreProcessGroup newPreProcessGroup(final JobPreProcessTask preProcess,
    final BusinessApplication businessApplication, final BatchJob batchJob,
    final Map<String, String> jobParameters, final int groupSequenceNumber) {
    return new DatabasePreProcessGroup(preProcess, this, businessApplication, batchJob,
      jobParameters, groupSequenceNumber);
  }
}
