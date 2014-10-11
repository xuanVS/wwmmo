package au.com.codeka.warworlds.server.ctrl;

import java.util.Arrays;

import org.joda.time.DateTime;

import au.com.codeka.common.messages.ErrorReport;
import au.com.codeka.common.messages.ErrorReports;
import au.com.codeka.warworlds.server.data.DB;
import au.com.codeka.warworlds.server.data.SqlStmt;
import au.com.codeka.warworlds.server.data.Transaction;

public class ErrorReportsController {
    private DataBase db;

    public ErrorReportsController() {
        db = new DataBase();
    }
    public ErrorReportsController(Transaction trans) {
        db = new DataBase(trans);
    }

    public void saveErrorReport(ErrorReport report) {
        try {
            ErrorReports reports = new ErrorReports.Builder()
                    .reports(Arrays.asList(report))
                    .build();
            db.saveErrorReports(reports);
        } catch (Exception e) {
            // we just ignore errors here...
        }
    }

    public void saveErrorReports(ErrorReports reports) {
        try {
            db.saveErrorReports(reports);
        } catch (Exception e) {
            // we just ignore errors here...
        }
    }

    private static class DataBase extends BaseDataBase {
        public DataBase() {
            super();
        }
        public DataBase(Transaction trans) {
            super(trans);
        }

        public void saveErrorReports(ErrorReports reports) throws Exception {
            String sql = "INSERT INTO error_reports (report_date, empire_id, message, exception_class, context, data) " +
                    " VALUES (?, ?, ?, ?, ?, ?)";
            try (SqlStmt stmt = DB.prepare(sql)) {
                for (ErrorReport report : reports.reports) {
                    stmt.setDateTime(1, new DateTime(report.report_time));
                    stmt.setInt(2, report.empire_id);
                    stmt.setString(3, report.message);
                    stmt.setString(4, report.exception_class);
                    stmt.setString(5, report.context);
                    stmt.setBytes(6, report.toByteArray());
                    stmt.update();
                }
            }
        }
    }
}
