import java.sql.*;
public class ResCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      System.out.println("=== NOW (DB) ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery("SELECT NOW()")) { if (rs.next()) System.out.println(rs.getString(1)); }
      System.out.println("\n=== RECENT RESERVATIONS ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
        "SELECT r.reservation_code, r.reservation_status, r.reservation_start, r.reservation_end, r.grace_period_minutes, r.created_at, t.ticket_code, t.expired_at " +
        "FROM reservations r LEFT JOIN tickets t ON t.reservation_id=r.reservation_id ORDER BY r.created_at DESC LIMIT 8")) {
        while (rs.next()) {
          System.out.printf("%s | %s | start=%s | end=%s | grace=%s | created=%s | ticket=%s | expiredAt=%s%n",
            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8));
        }
      }
    }
  }
}
