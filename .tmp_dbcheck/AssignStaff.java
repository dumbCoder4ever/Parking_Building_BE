import java.sql.*;
public class AssignStaff {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      try (PreparedStatement ps = c.prepareStatement("INSERT IGNORE INTO building_staff (assignment_id, building_id, user_id) SELECT UUID(), b.building_id, u.user_id FROM users u CROSS JOIN buildings b WHERE u.email='driver.test@gmail.com' AND b.building_name='Main Parking Building'")) {
        int n = ps.executeUpdate();
        System.out.println("inserted=" + n);
      }
    }
  }
}
