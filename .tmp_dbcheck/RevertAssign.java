import java.sql.*;
public class RevertAssign {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      try (PreparedStatement ps = c.prepareStatement("DELETE bs FROM building_staff bs JOIN users u ON bs.user_id=u.user_id WHERE u.email='driver.test@gmail.com'")) {
        System.out.println("deleted=" + ps.executeUpdate());
      }
    }
  }
}
