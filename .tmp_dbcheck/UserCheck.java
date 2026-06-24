import java.sql.*;
public class UserCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT email, role FROM users WHERE role LIKE '%STAFF%' OR role LIKE '%DRIVER%' LIMIT 10")) {
        while (rs.next()) System.out.printf("%s | %s%n", rs.getString(1), rs.getString(2));
      }
    }
  }
}
