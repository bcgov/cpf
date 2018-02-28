package ca.bc.gov.open.cpf.api.web.builder;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

import com.revolsys.record.io.format.xml.XmlWriter;
import com.revolsys.ui.html.serializer.RowsTableSerializer;

public class ErrorsTableSerializer implements RowsTableSerializer {
  private static final List<String> TITLES = Arrays.asList("Name", "Value", "Message");

  private int rowCount;

  private final List<List<String>> rows = new ArrayList<>();

  public ErrorsTableSerializer(final List<List<String>> rows) {
    setRows(rows);
  }

  @Override
  public String getBodyCssClass(final int row, final int col) {
    return "";
  }

  @Override
  public int getBodyRowCount() {
    return this.rowCount;
  }

  @Override
  public int getColumnCount() {
    return 3;
  }

  @Override
  public String getFooterCssClass(final int row, final int col) {
    return "";
  }

  @Override
  public int getFooterRowCount() {
    return 0;
  }

  @Override
  public String getHeaderCssClass(final int col) {
    return "";
  }

  @Override
  public void serializeBodyCell(final XmlWriter out, final int row, final int col) {
    if (col < 3) {
      final List<String> record = this.rows.get(row);
      final String value = record.get(col);
      out.text(value);
    } else {
      out.entityRef("nbsp");
    }
  }

  @Override
  public void serializeFooterCell(final XmlWriter out, final int row, final int col) {
  }

  @Override
  public void serializeHeaderCell(final XmlWriter out, final int col) {
    if (col < 3) {
      out.text(TITLES.get(col));
    } else {
      out.entityRef("nbsp");
    }
  }

  @SuppressWarnings("unchecked")
  @Override
  public void setRows(final Collection<? extends Object> rows) {
    this.rows.clear();
    if (rows != null) {
      this.rows.addAll((Collection<List<String>>)rows);
    }
    this.rowCount = this.rows.size();
  }

}
