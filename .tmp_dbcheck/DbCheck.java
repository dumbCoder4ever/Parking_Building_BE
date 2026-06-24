import java.sql.*;
public class DbCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      System.out.println("=== ACTIVE RESERVATIONS ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
        "SELECT r.reservation_code, u.email, u.username, r.reservation_start, r.reservation_end, r.reservation_status, ps.slot_name " +
        "FROM reservations r JOIN users u ON r.user_id = u.user_id LEFT JOIN parking_slots ps ON r.slot_id = ps.slot_id " +
        "WHERE r.reservation_status IN ('PENDING','APPROVED') ORDER BY r.created_at DESC")) {
        int n=0; while (rs.next()) { n++;
          System.out.printf("%s | %s (%s) | %s -> %s | %s | slot=%s%n",
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
        }
        if (n==0) System.out.println("(none)");
      }
      System.out.println("\n=== USERS (DRIVER) ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
        "SELECT email, username, role FROM users WHERE role='ROLE_DRIVER' LIMIT 10")) {
        while (rs.next()) System.out.printf("%s | %s | %s%n", rs.getString(1), rs.getString(2), rs.getString(3));
      }
    }
  }
}
