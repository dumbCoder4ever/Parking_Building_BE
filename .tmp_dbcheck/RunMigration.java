import java.sql.*;
import java.nio.file.*;
public class RunMigration {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    String sql = Files.readString(Path.of("C:/Users/Admin/Desktop/New folder/PROJECT/Parking_Building_BE/src/main/resources/db/simplify_pricing_policies.sql"));
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      for (String stmt : sql.split(";")) {
        String s = stmt.trim();
        if (s.isEmpty() || s.startsWith("--")) continue;
        try (Statement st = c.createStatement()) {
          boolean hasResult = st.execute(s);
          if (hasResult) {
            try (ResultSet rs = st.getResultSet()) {
              if (rs.next()) System.out.println(rs.getString(1));
            }
          } else {
            System.out.println("OK: " + s.substring(0, Math.min(60, s.length())).replace('\n',' ') + "...");
          }
        }
      }
      try (Statement st = c.createStatement(); ResultSet rs = st.executeQuery("SHOW COLUMNS FROM pricing_policies LIKE 'max_hours'")) {
        if (rs.next()) System.out.println("VERIFIED: max_hours | " + rs.getString("Type") + " | Default=" + rs.getString("Default"));
        else System.out.println("FAILED: max_hours not found");
      }
    }
  }
}
