import java.sql.*;
public class AddMaxHours {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      boolean exists = false;
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
        "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS WHERE TABLE_SCHEMA='parking_db' AND TABLE_NAME='pricing_policies' AND COLUMN_NAME='max_hours'")) {
        rs.next(); exists = rs.getInt(1) > 0;
      }
      System.out.println("Before: max_hours exists = " + exists);
      if (!exists) {
        try (Statement s = c.createStatement()) {
          s.executeUpdate("ALTER TABLE pricing_policies ADD COLUMN max_hours INT DEFAULT 24 AFTER hourly_rate");
          System.out.println("ALTER TABLE executed");
        }
      }
      try (Statement s = c.createStatement()) {
        s.executeUpdate("UPDATE pricing_policies SET max_hours = 24 WHERE max_hours IS NULL");
        System.out.println("Updated null values to 24");
      }
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SHOW COLUMNS FROM pricing_policies LIKE 'max_hours'")) {
        if (rs.next()) System.out.println("After: " + rs.getString("Field") + " | " + rs.getString("Type") + " | default=" + rs.getString("Default"));
      }
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT policy_name, hourly_rate, max_hours FROM pricing_policies LIMIT 5")) {
        while (rs.next()) System.out.printf("  %s | hourly=%s | max_hours=%s%n", rs.getString(1), rs.getString(2), rs.getString(3));
      }
    }
  }
}
