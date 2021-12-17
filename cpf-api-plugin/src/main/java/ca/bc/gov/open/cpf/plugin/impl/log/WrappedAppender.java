package ca.bc.gov.open.cpf.plugin.impl.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.Appender;
import ch.qos.logback.core.AppenderBase;

public class WrappedAppender extends AppenderBase<ILoggingEvent> {

  private final Appender<ILoggingEvent> appender;

  public WrappedAppender(final Appender<ILoggingEvent> appender) {
    this.appender = appender;
  }

  @Override
  protected void append(final ILoggingEvent event) {
    this.appender.doAppend(event);
  }

  @Override
  public synchronized void doAppend(final ILoggingEvent eventObject) {
    if (isStarted()) {
      this.appender.doAppend(eventObject);
    }
  }
}
