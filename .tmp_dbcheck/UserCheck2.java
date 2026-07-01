import java.sql.*;
public class UserCheck2 {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SHOW COLUMNS FROM users LIKE 'test_password'")) {
        System.out.println(rs.next() ? "has test_password" : "no test_password");
      }
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT email, username, role, test_password FROM users WHERE role LIKE '%STAFF%' LIMIT 20")) {
        while (rs.next()) System.out.printf("%s | %s | %s | test_pw=%s%n", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
      }
    }
  }
}
