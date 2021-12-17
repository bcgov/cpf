package ca.bc.gov.open.cpf.plugins.test;

import javax.annotation.PostConstruct;

public class FailOnStart {
  private boolean fail;

  @PostConstruct()
  public void init() {
    if (this.fail) {
      throw new RuntimeException("Test fail on startup");
    }
  }

  public boolean isFail() {
    return this.fail;
  }

  public void setFail(final boolean fail) {
    this.fail = fail;
  }
}
