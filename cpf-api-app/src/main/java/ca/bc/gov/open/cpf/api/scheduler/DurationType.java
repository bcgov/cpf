package ca.bc.gov.open.cpf.api.scheduler;

import java.util.Date;
import java.util.HashMap;
import java.util.Map;

import com.revolsys.record.Record;
import com.revolsys.record.code.Code;
import com.revolsys.util.Dates;

public enum DurationType implements Code {
  DAY, HOUR, MONTH, YEAR;

  private static final String DURATION_TYPE = "DURATION_TYPE";

  private static final Map<String, DurationType> TYPE_BY_LOWER = new HashMap<>();

  static {
    for (final DurationType type : DurationType.values()) {
      TYPE_BY_LOWER.put(type.lower, type);
    }
  }

  public static DurationType getDurationType(final Record record) {
    final String type = record.getValue(DURATION_TYPE);
    return getDurationType(type);
  }

  public static DurationType getDurationType(final String type) {
    return TYPE_BY_LOWER.get(type);
  }

  private String lower;

  private DurationType() {
    this.lower = toString().toLowerCase();
  }

  @SuppressWarnings("unchecked")
  @Override
  public <C> C getCode() {
    return (C)this.lower;
  }

  @Override
  public String getDescription() {
    return toString();
  }

  public String getId() {
    final Date date = new Date();
    return getId(date);
  }

  public String getId(final Date date) {
    String pattern;
    if (this == DurationType.HOUR) {
      pattern = "yyyy-MM-dd-HH";
    } else {
      if (this == DurationType.DAY) {
        pattern = "yyyy-MM-dd";
      } else {
        if (this == DurationType.MONTH) {
          pattern = "yyyy-MM";
        } else {
          if (this == DurationType.YEAR) {
            pattern = "yyyy";
          } else {
            throw new IllegalArgumentException("Invalid duration type : " + this);
          }
        }
      }
    }
    return Dates.format(pattern, date);
  }

  public String getLower() {
    return this.lower;
  }

  public DurationType getParentDurationType() {
    if (this == DurationType.HOUR) {
      return DurationType.DAY;
    } else if (this == DurationType.DAY) {
      return DurationType.MONTH;
    } else if (this == DurationType.MONTH) {
      return DurationType.YEAR;
    } else {
      return null;
    }
  }

  public boolean setValue(final Record record) {
    return record.setValue(DURATION_TYPE, this.lower);
  }
}
