import java.sql.*;
public class PayFlowCheck {
  public static void main(String[] args) throws Exception {
    Class.forName("com.mysql.cj.jdbc.Driver");
    try (Connection c = DriverManager.getConnection("jdbc:mysql://localhost:3306/parking_db", "root", "123456")) {
      String ticket = "T-327FC4A3";
      System.out.println("=== TICKET " + ticket + " ===");
      try (PreparedStatement ps = c.prepareStatement(
        "SELECT t.ticket_code, t.status, t.is_used, ps.session_id, ps.session_status, ps.payment_status, ps.checkin_time " +
        "FROM tickets t LEFT JOIN parking_sessions ps ON ps.ticket_id = t.ticket_id WHERE t.ticket_code = ?")) {
        ps.setString(1, ticket);
        try (ResultSet rs = ps.executeQuery()) {
          if (!rs.next()) System.out.println("NOT FOUND");
          else System.out.printf("ticket=%s status=%s used=%s session=%s sessStatus=%s payStatus=%s checkin=%s%n",
            rs.getString(1), rs.getString(2), rs.getBoolean(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7));
        }
      }
      System.out.println("\n=== PAYMENTS for ticket session ===");
      try (PreparedStatement ps = c.prepareStatement(
        "SELECT p.payment_id, p.payment_method, p.payment_status, p.amount, p.created_at " +
        "FROM payments p JOIN parking_sessions ps ON p.session_id = ps.session_id JOIN tickets t ON ps.ticket_id = t.ticket_id " +
        "WHERE t.ticket_code = ? ORDER BY p.created_at DESC")) {
        ps.setString(1, ticket);
        try (ResultSet rs = ps.executeQuery()) {
          int n=0; while (rs.next()) { n++;
            System.out.printf("%s | method=%s status=%s amount=%s at=%s%n",
              rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5));
          }
          if (n==0) System.out.println("(none)");
        }
      }
      System.out.println("\n=== ACTIVE SESSIONS sample ===");
      try (Statement s = c.createStatement(); ResultSet rs = s.executeQuery(
        "SELECT t.ticket_code, ps.session_id, ps.session_status, ps.payment_status FROM parking_sessions ps JOIN tickets t ON ps.ticket_id=t.ticket_id WHERE ps.session_status='ACTIVE' LIMIT 5")) {
        int n=0; while (rs.next()) { n++;
          System.out.printf("ticket=%s session=%s status=%s pay=%s%n", rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
        }
        if (n==0) System.out.println("(none)");
      }
    }
  }
}
