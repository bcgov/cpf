package ca.bc.gov.open.cpf.api.scheduler;

import javax.annotation.Resource;

import ca.bc.gov.open.cpf.api.domain.BatchJob;

public class BatchJobPostProcess extends AbstractBatchJobChannelProcess {

  public BatchJobPostProcess() {
    super(BatchJob.PROCESSED);
  }

  @Override
  public boolean processJob(final long batchJobId) {
    final BatchJobService batchJobService = getBatchJobService();
    final long time = System.currentTimeMillis();
    if (getDataAccessObject().setBatchJobStatus(batchJobId, BatchJob.PROCESSED,
      BatchJob.CREATING_RESULTS)) {
      final long lastChangedTime = System.currentTimeMillis();
      return batchJobService.postProcessBatchJob(batchJobId, time,
        lastChangedTime);
    } else {
      return true;
    }
  }

  @Override
  @Resource(name = "batchJobService")
  public void setBatchJobService(final BatchJobService batchJobService) {
    super.setBatchJobService(batchJobService);
    batchJobService.setPostProcess(this);
  }
}
