import java.sql.*;
public class DbCheck2 {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      System.out.println("=== ALL RESERVATIONS admin02@gmail.com ===");
      try (PreparedStatement ps = c.prepareStatement(
        "SELECT r.reservation_code, r.reservation_start, r.reservation_end, r.reservation_status, ps.slot_name, ps.slot_status, r.created_at " +
        "FROM reservations r JOIN users u ON r.user_id = u.user_id LEFT JOIN parking_slots ps ON r.slot_id = ps.slot_id " +
        "WHERE u.email = ? ORDER BY r.created_at DESC")) {
        ps.setString(1, "admin02@gmail.com");
        try (ResultSet rs = ps.executeQuery()) {
          while (rs.next()) System.out.printf("%s | %s -> %s | %s | slot=%s (%s) | created=%s%n",
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
        }
      }
      System.out.println("\n=== NOW ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT NOW()")) {
        if (rs.next()) System.out.println(rs.getString(1));
      }
    }
  }
}
