import java.sql.*;
public class StaffCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      System.out.println("=== STAFF USERS ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT email, username, role, status FROM users WHERE role='ROLE_STAFF'")) {
        while (rs.next()) System.out.printf("%s | %s | %s | %s%n", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
      }
      System.out.println("\n=== BUILDING STAFF ASSIGNMENTS ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT u.email, b.building_name FROM building_staff bs JOIN users u ON bs.user_id=u.user_id JOIN buildings b ON bs.building_id=b.building_id")) {
        while (rs.next()) System.out.printf("%s -> %s%n", rs.getString(1), rs.getString(2));
      }
    }
  }
}
