/*
 * Copyright © 2008-2015, Province of British Columbia
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

import java.io.File;
import java.io.InputStream;
import java.util.List;

import ca.bc.gov.open.cpf.api.domain.BatchJobFile;
import ca.bc.gov.open.cpf.api.domain.CpfDataAccessObject;

import com.revolsys.data.record.Record;
import com.revolsys.data.record.schema.RecordDefinition;
import com.revolsys.format.csv.CsvRecordWriter;
import com.revolsys.io.FileUtil;
import com.revolsys.transaction.Propagation;
import com.revolsys.transaction.Transaction;

public class DatabaseJobController extends AbstractJobController {
  private final CpfDataAccessObject dataAccessObject;

  public DatabaseJobController(final CpfDataAccessObject dataAccessObject) {
    this.dataAccessObject = dataAccessObject;
  }

  @Override
  public boolean cancelJob(final long jobId) {
    return this.dataAccessObject.cancelBatchJob(jobId);
  }

  @Override
  public void createJobFile(final long jobId, final String path, final long sequenceNumber,
    final String contentType, final Object data) {
    try (
      Transaction transaction = this.dataAccessObject.createTransaction(Propagation.REQUIRES_NEW)) {
      final Record result = this.dataAccessObject.create(BatchJobFile.BATCH_JOB_FILE);
      result.setValue(BatchJobFile.BATCH_JOB_ID, jobId);
      result.setValue(BatchJobFile.PATH, path);
      result.setValue(BatchJobFile.CONTENT_TYPE, contentType);
      result.setValue(BatchJobFile.SEQUENCE_NUMBER, sequenceNumber);
      result.setValue(BatchJobFile.DATA, data);
      this.dataAccessObject.write(result);
    } catch (final Throwable e) {
      throw new RuntimeException("Unable to create file", e);
    }
  }

  @Override
  public void deleteJob(final long jobId) {
    this.dataAccessObject.deleteBatchJob(jobId);
  }

  @Override
  protected long getFileSize(final long jobId, final String path, final int sequenceNumber) {
    return this.dataAccessObject.getBatchJobFileSize(jobId, path, sequenceNumber);
  }

  @Override
  protected InputStream getFileStream(final long jobId, final String path,
    final int sequenceNumber) {
    return this.dataAccessObject.getBatchJobFileStream(jobId, JOB_INPUTS, 1);
  }

  @Override
  public String getKey() {
    return "database";
  }

  @Override
  public void setGroupInput(final long jobId, final int sequenceNumber,
    final RecordDefinition recordDefinition, final List<Record> requests) {
    if (!requests.isEmpty()) {
      final File file = FileUtil.createTempFile("job", ".csv");
      try {
        try (
          CsvRecordWriter writer = new CsvRecordWriter(recordDefinition,
            FileUtil.createUtf8Writer(file), ',', true, false)) {
          for (final Record record : requests) {
            writer.write(record);
          }
        }
        createJobFile(jobId, GROUP_INPUTS, sequenceNumber, "text/csv", file);
      } finally {
        FileUtil.delete(file);
      }
    }
  }

}
