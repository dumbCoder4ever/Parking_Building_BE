# Huong Dan Test Chi Tiet - Phase 2 & Phase 3 & Incident Enhancement

## Truoc Khi Bat Dau

Dam bao:
1. App da start thanh cong (khong con loi column missing)
2. Database da chay migration moi (cho incident enhancement):
   - ALTER TABLE incidents ADD COLUMN verified_plate_number VARCHAR(20);
   - ALTER TABLE incidents ADD COLUMN verified_ticket_code VARCHAR(50);
   - ALTER TABLE incidents ADD COLUMN verification_result VARCHAR(20);
   - ALTER TABLE incidents ADD COLUMN verified_at DATETIME;
   - ALTER TABLE incidents ADD COLUMN verified_by VARCHAR(100);
   - ALTER TABLE incidents ADD COLUMN previous_status VARCHAR(20);
3. Co 2 tai khoan test: Staff va Driver

---

## BUOC 1: Kiem Tra Token

### 1.1. Lay Token Staff
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "staff@example.com", "password": "password123"}'
```

Luu lai: staff_token

### 1.2. Lay Token Driver
```bash
curl -X POST http://localhost:8080/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email": "driver@example.com", "password": "password123"}'
```

Luu lai: driver_token

---

## BUOC 2: Tao Du Lieu Test

Chay SQL nay trong database:

```sql
-- 1. Vehicle
INSERT INTO vehicles (vehicle_id, plate_number, vehicle_type_id, brand, model, status, user_id)
VALUES ('VEH-TEST-001', '30A-123456', 'VT-CAR', 'Toyota', 'Vios', 'ACTIVE', 'user-123')
ON DUPLICATE KEY UPDATE plate_number = plate_number;

-- 2. Slot 1 (cho session thuong)
INSERT INTO parking_slots (slot_id, slot_name, slot_status, zone_id)
VALUES ('SLOT-TEST-001', 'B-205', 'AVAILABLE', 'ZONE-A')
ON DUPLICATE KEY UPDATE slot_status = 'AVAILABLE';

-- 3. Slot 2 (cho test reassign - phai AVAILABLE)
INSERT INTO parking_slots (slot_id, slot_name, slot_status, zone_id)
VALUES ('SLOT-TEST-002', 'B-206', 'AVAILABLE', 'ZONE-A')
ON DUPLICATE KEY UPDATE slot_status = 'AVAILABLE';

-- 4. Slot 3 (da bi chiem - OCCUPIED)
INSERT INTO parking_slots (slot_id, slot_name, slot_status, zone_id)
VALUES ('SLOT-TEST-003', 'B-207', 'OCCUPIED', 'ZONE-A')
ON DUPLICATE KEY UPDATE slot_status = 'OCCUPIED';

-- 5. Session cho test LOI VE (DRIVER_LOST_TICKET)
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized, estimated_fee, total_fee)
VALUES ('SESSION-LOST-TICKET', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE, 50000, 50000)
ON DUPLICATE KEY UPDATE session_status = 'ACTIVE', incident_authorized = FALSE;

-- 6. Ticket cho session LOI VE
INSERT INTO tickets (ticket_id, ticket_code, session_id, status, issued_at)
VALUES ('TICKET-001', 'TKT-789456', 'SESSION-LOST-TICKET', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE ticket_code = 'TKT-789456';

-- 7. Session cho test SAI PHI (DRIVER_INCORRECT_FEE)
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized, estimated_fee, total_fee)
VALUES ('SESSION-INCORRECT-FEE', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE, 50000, 50000)
ON DUPLICATE KEY UPDATE session_status = 'ACTIVE', incident_authorized = FALSE;

-- 8. Ticket cho session SAI PHI
INSERT INTO tickets (ticket_id, ticket_code, session_id, status, issued_at)
VALUES ('TICKET-002', 'TKT-789457', 'SESSION-INCORRECT-FEE', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE ticket_code = 'TKT-789457';

-- 9. Session cho test SLOT BI CHIEM (DRIVER_SLOT_OCCUPIED)
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized, estimated_fee, total_fee)
VALUES ('SESSION-SLOT-OCCUPIED', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE, 50000, 50000)
ON DUPLICATE KEY UPDATE session_status = 'ACTIVE', incident_authorized = FALSE;

-- 10. Ticket cho session SLOT BI CHIEM
INSERT INTO tickets (ticket_id, ticket_code, session_id, status, issued_at)
VALUES ('TICKET-003', 'TKT-789458', 'SESSION-SLOT-OCCUPIED', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE ticket_code = 'TKT-789458';

-- 11. Session cho test KHONG TIM THAY XE
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized, estimated_fee, total_fee)
VALUES ('SESSION-NOT-FOUND', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE, 50000, 50000)
ON DUPLICATE KEY UPDATE session_status = 'ACTIVE', incident_authorized = FALSE;

-- 12. Ticket cho session KHONG TIM THAY XE
INSERT INTO tickets (ticket_id, ticket_code, session_id, status, issued_at)
VALUES ('TICKET-004', 'TKT-789459', 'SESSION-NOT-FOUND', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE ticket_code = 'TKT-789459';
```

---

# PHAN 1: TEST DRIVER_LOST_TICKET (Mat Ticket Vat Ly)

## BUOC 3: Driver Tao Bao Cao Mat Ve

### POST /api/incidents/driver

```bash
curl -X POST http://localhost:8080/api/incidents/driver \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer DRIVER_TOKEN" \
  -d '{
    "sessionId": "SESSION-LOST-TICKET",
    "incidentType": "DRIVER_LOST_TICKET",
    "description": "Toi da mat ve khi checkout"
  }'
```

Expected Response (200):
```json
{
  "success": true,
  "message": "Driver report submitted",
  "data": {
    "incidentId": "inc-xxx",
    "sessionId": "SESSION-LOST-TICKET",
    "ticketCode": "TKT-789456",
    "vehiclePlate": "30A-123456",
    "incidentType": "DRIVER_LOST_TICKET",
    "status": "OPEN",
    "verificationResult": "PENDING",
    "reportSource": "DRIVER",
    "reporterId": "driver@example.com"
  }
}
```

Luu lai: incidentId (incident_xxx)

---

## BUOC 4: Staff Nhan Incident (Chuyen OPEN -> IN_PROGRESS)

### PUT /api/incidents/{id}/status

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=IN_PROGRESS" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{}'
```

Expected Response:
```json
{
  "incidentId": "inc-xxx",
  "status": "IN_PROGRESS",
  "previousStatus": "OPEN"
}
```

---

## BUOC 5: Staff Xac Minh Chu Xe (Verify Vehicle Ownership)

### POST /api/incidents/{id}/verify-vehicle

#### 5.1. Xac minh DUNG (plate dung, co the co ticket dung)

```bash
curl -X POST "http://localhost:8080/api/incidents/INCIDENT_ID/verify-vehicle" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "plateNumber": "30A-123456",
    "ticketCode": "TKT-789456"
  }'
```

Expected Response:
```json
{
  "incidentId": "inc-xxx",
  "verificationResult": "MATCH",
  "sessionPlateNumber": "30A-123456",
  "sessionTicketCode": "TKT-789456",
  "providedPlateNumber": "30A-123456",
  "providedTicketCode": "TKT-789456",
  "message": "Vehicle ownership verified successfully"
}
```

#### 5.2. Xac minh SAI (plate sai)

```bash
curl -X POST "http://localhost:8080/api/incidents/INCIDENT_ID/verify-vehicle" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "plateNumber": "30B-999999",
    "ticketCode": "TKT-789456"
  }'
```

Expected Response:
```json
{
  "incidentId": "inc-xxx",
  "verificationResult": "MATCH",
  "sessionPlateNumber": "30A-123456",
  "sessionTicketCode": "TKT-789456",
  "providedPlateNumber": "30B-999999",
  "providedTicketCode": "TKT-789456",
  "message": "Vehicle ownership verification failed: Plate number does not match."
}
```

---

## BUOC 6: Staff Resolve - AUTHORIZE_CHECKOUT

### PUT /api/incidents/{id}/status

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolution": "Da xac minh chu xe, cho phep checkout khong can ve",
    "resolutionAction": "AUTHORIZE_CHECKOUT"
  }'
```

Kiem tra database:
```sql
SELECT session_id, incident_authorized FROM parking_sessions WHERE session_id = 'SESSION-LOST-TICKET';
```
Expected: incident_authorized = true

---

## BUOC 7: Staff Checkout Cho Driver (Sau khi da AUTHORIZE_CHECKOUT)

### POST /api/sessions/driver/checkout

```bash
curl -X POST "http://localhost:8080/api/sessions/driver/checkout?sessionId=SESSION-LOST-TICKET" \
  -H "Authorization: Bearer STAFF_TOKEN"
```

Expected:
```json
{
  "success": true,
  "message": "Driver checkout successful",
  "data": {
    "paymentStatus": "PAID"
  }
}
```

---

# PHAN 2: TEST DRIVER_INCORRECT_FEE (Sai Phi)

## BUOC 8: Driver Tao Bao Cao Sai Phi

### POST /api/incidents/driver

```bash
curl -X POST http://localhost:8080/api/incidents/driver \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer DRIVER_TOKEN" \
  -d '{
    "sessionId": "SESSION-INCORRECT-FEE",
    "incidentType": "DRIVER_INCORRECT_FEE",
    "description": "Bi tinh 50k nhung chi nen tra 30k"
  }'
```

Luu lai: incidentId (inc-fee-xxx)

---

## BUOC 9: Staff Nhan va Resolve - UPDATE_PAYMENT

### 9.1. Chuyen OPEN -> IN_PROGRESS

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=IN_PROGRESS" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{}'
```

### 9.2. Resolve voi UPDATE_PAYMENT (so dung)

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolution": "Hoan 20k do tinh sai",
    "resolutionAction": "UPDATE_PAYMENT",
    "adjustedAmount": 30000
  }'
```

Kiem tra:
```sql
SELECT total_fee FROM parking_sessions WHERE session_id = 'SESSION-INCORRECT-FEE';
```
Expected: 30000

---

## BUOC 10: Test Validation - So Am (FAIL)

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolutionAction": "UPDATE_PAYMENT",
    "adjustedAmount": -1000
  }'
```

Expected Response (400):
```json
{
  "success": false,
  "message": "Adjusted amount cannot be negative"
}
```

---

## BUOC 11: Test Validation - Vuot Max (FAIL)

Gia tri toi da = estimatedFee * 10 = 50000 * 10 = 500000

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolutionAction": "UPDATE_PAYMENT",
    "adjustedAmount": 600000
  }'
```

Expected Response (400):
```json
{
  "success": false,
  "message": "Adjusted amount exceeds maximum allowed (500000.00)"
}
```

---

# PHAN 3: TEST DRIVER_SLOT_OCCUPIED (Slot Bi Chiem)

## BUOC 12: Driver Tao Bao Cao Slot Bi Chiem

### POST /api/incidents/driver

```bash
curl -X POST http://localhost:8080/api/incidents/driver \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer DRIVER_TOKEN" \
  -d '{
    "sessionId": "SESSION-SLOT-OCCUPIED",
    "incidentType": "DRIVER_SLOT_OCCUPIED",
    "description": "Slot B-205 bi xe khac chiem mat"
  }'
```

Luu lai: incidentId (inc-slot-xxx)

---

## BUOC 13: Staff Validate Slot Moi (Kiem Tra Truoc Khi Reassign)

### 13.1. Kiem tra slot AVAILABLE (slot 2 - B-206)

```bash
curl -X POST "http://localhost:8080/api/incidents/validate-reassign?incidentId=INCIDENT_ID&newSlotId=SLOT-TEST-002" \
  -H "Authorization: Bearer STAFF_TOKEN"
```

Expected Response:
```json
{
  "slotId": "SLOT-TEST-002",
  "slotName": "B-206",
  "isAvailable": true,
  "hasActiveReservation": false,
  "isInSameBuilding": true,
  "message": "Slot is available for reassignment"
}
```

### 13.2. Kiem tra slot OCCUPIED (slot 3 - B-207)

```bash
curl -X POST "http://localhost:8080/api/incidents/validate-reassign?incidentId=INCIDENT_ID&newSlotId=SLOT-TEST-003" \
  -H "Authorization: Bearer STAFF_TOKEN"
```

Expected Response:
```json
{
  "slotId": "SLOT-TEST-003",
  "slotName": "B-207",
  "isAvailable": false,
  "hasActiveReservation": false,
  "isInSameBuilding": true,
  "message": "Slot is not available. Current status: OCCUPIED"
}
```

---

## BUOC 14: Staff Resolve - REASSIGN_SLOT

### 14.1. Chuyen OPEN -> IN_PROGRESS

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=IN_PROGRESS" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{}'
```

### 14.2. Resolve voi REASSIGN_SLOT

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolution": "Da chuyen xe sang slot B-206",
    "resolutionAction": "REASSIGN_SLOT",
    "newSlotId": "SLOT-TEST-002"
  }'
```

Kiem tra:
```sql
SELECT session_id, slot_id FROM parking_sessions WHERE session_id = 'SESSION-SLOT-OCCUPIED';
```
Expected: slot_id = 'SLOT-TEST-002'

---

## BUOC 15: Test Validation - Reassign Slot OCCUPIED (FAIL)

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolutionAction": "REASSIGN_SLOT",
    "newSlotId": "SLOT-TEST-003"
  }'
```

Expected Response (400):
```json
{
  "success": false,
  "message": "Slot is not available. Current status: OCCUPIED"
}
```

---

# PHAN 4: TEST DRIVER_CANNOT_FIND_VEHICLE (Khong Tim Thay Xe)

## BUOC 16: Driver Tao Bao Cao Khong Tim Thay Xe

### POST /api/incidents/driver

```bash
curl -X POST http://localhost:8080/api/incidents/driver \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer DRIVER_TOKEN" \
  -d '{
    "sessionId": "SESSION-NOT-FOUND",
    "incidentType": "DRIVER_CANNOT_FIND_VEHICLE",
    "description": "Toi khong nho cho do xe"
  }'
```

Luu lai: incidentId (inc-nf-xxx)

---

## BUOC 17: Staff Xac Nhan Vi Tri Xe

### 17.1. Chuyen OPEN -> IN_PROGRESS

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=IN_PROGRESS" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{}'
```

### 17.2. Resolve voi PROVIDE_VEHICLE_LOCATION

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolution": "Xe dang o tang 2, zone A, slot B-205",
    "resolutionAction": "PROVIDE_VEHICLE_LOCATION"
  }'
```

---

# PHAN 5: TEST BANG CHUNG RESERVATION (STAFF EVIDENCE)

> Muc dich: Staff co bang chung reservation moi nhat cua driver de cross-check voi 4 loai incident (mat ve, sai phi, slot bi chiem, khong tim thay xe). Rieng flow DRIVER_SLOT_OCCUPIED, staff chi thay slot trong cung floor voi reservation moi nhat.

## BUOC 18: Lay Latest Reservation Cho Incident

Ap dung cho moi flow (mat ve, sai phi, slot bi chiem, khong tim thay xe).

### GET /api/incidents/{incidentId}/latest-reservation

```bash
curl -X GET "http://localhost:8080/api/incidents/INCIDENT_ID/latest-reservation" \
  -H "Authorization: Bearer STAFF_TOKEN"
```

Expected Response:
```json
{
  "success": true,
  "message": "Latest reservation retrieved",
  "data": {
    "reservationId": "res-latest-xxx",
    "reservationCode": "RES-LATEST-001",
    "reservationStatus": "APPROVED",
    "reservationStart": "2026-07-21T15:00:00",
    "createdAt": "2026-07-21T14:30:00",
    "buildingId": "building-1",
    "buildingName": "Building A",
    "floorId": "floor-1",
    "floorName": "Floor 1",
    "floorLevel": 1,
    "zoneId": "zone-1",
    "zoneName": "Zone A",
    "slotId": "slot-original-001",
    "slotName": "A-01",
    "vehicleId": "VEH-TEST-001",
    "vehiclePlate": "30A-123456",
    "vehicleType": "Car",
    "driverUserId": "user-driver-1",
    "driverEmail": "driver@example.com",
    "driverFullName": "Test Driver"
  }
}
```

Test case:
- [ ] Lay duoc reservationCode, status, building/floor/zone/slot name
- [ ] Lay duoc vehiclePlate va vehicleType de cross-check voi plate khi verify
- [ ] Lay duoc driverEmail, driverFullName

## BUOC 19: Lay Available Slots Cho Reassign (rieng DRIVER_SLOT_OCCUPIED)

### GET /api/incidents/{incidentId}/available-slots-for-reassign

```bash
curl -X GET "http://localhost:8080/api/incidents/INCIDENT_ID/available-slots-for-reassign" \
  -H "Authorization: Bearer STAFF_TOKEN"
```

Expected Response:
```json
{
  "success": true,
  "message": "Available slots retrieved",
  "data": [
    {
      "slotId": "slot-3",
      "slotName": "A-02",
      "slotStatus": "AVAILABLE",
      "zoneId": "zone-1",
      "zoneName": "Zone A",
      "floorId": "floor-1",
      "floorName": "Floor 1",
      "floorLevel": 1,
      "buildingId": "building-1",
      "buildingName": "Building A",
      "hasActiveReservation": false
    }
  ]
}
```

## BUOC 20: Validation - Reassign Slot Khac Floor (FAIL)

Sau khi them rule moi: slot reassign phai cung floor voi reservation moi nhat.

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolution": "Thử chuyển sang slot ở tầng khác",
    "resolutionAction": "REASSIGN_SLOT",
    "newSlotId": "SLOT-KHAC-FLOOR"
  }'
```

Expected Response (400):
```json
{
  "success": false,
  "message": "Replacement slot must be in the same floor as driver's reservation. Expected floorId: floor-1, new slot floorId: floor-2"
}
```

# PHAN 6: TEST WORKFLOW ENFORCEMENT

## BUOC 21: Test - Khong Cho Phep OPEN -> RESOLVED truc tiep (FAIL)

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{
    "resolutionAction": "AUTHORIZE_CHECKOUT"
  }'
```

Expected Response (400):
```json
{
  "success": false,
  "message": "Must transition through IN_PROGRESS before RESOLVED. Please process the incident first."
}
```

---

## BUOC 22: Test - Khong Cho Phep Thay Doi CLOSED Incident (FAIL)

```bash
curl -X PUT "http://localhost:8080/api/incidents/INCIDENT_ID/status?status=OPEN" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer STAFF_TOKEN" \
  -d '{}'
```

Expected Response (400):
```json
{
  "success": false,
  "message": "Cannot change status of a closed or cancelled incident."
}
```

---

# PHAN 7: TEST API - Lay Incidents Theo Session

## BUOC 23: Lay Incidents Theo Session

### GET /api/incidents/by-session/{sessionId}

```bash
curl -X GET "http://localhost:8080/api/incidents/by-session/SESSION-LOST-TICKET" \
  -H "Authorization: Bearer STAFF_TOKEN"
```

---

# CHECKLIST HOAN TAT

## Incident Enhancement
- [ ] 1. Driver tao bao cao mat ve (DRIVER_LOST_TICKET)
- [ ] 2. Staff chuyen OPEN -> IN_PROGRESS
- [ ] 3. Staff verify vehicle MATCH
- [ ] 4. Staff verify vehicle MISMATCH (plate sai)
- [ ] 5. Staff resolve AUTHORIZE_CHECKOUT
- [ ] 6. Staff checkout driver thanh cong
- [ ] 7. Driver tao bao cao sai phi (DRIVER_INCORRECT_FEE)
- [ ] 8. Staff resolve UPDATE_PAYMENT (so dung)
- [ ] 9. Validation: so am -> FAIL
- [ ] 10. Validation: vuot max -> FAIL
- [ ] 11. Driver tao bao cao slot bi chiem (DRIVER_SLOT_OCCUPIED)
- [ ] 12. Validate slot AVAILABLE -> true
- [ ] 13. Validate slot OCCUPIED -> false
- [ ] 14. Staff resolve REASSIGN_SLOT thanh cong
- [ ] 15. Validation: reassign slot OCCUPIED -> FAIL
- [ ] 16. Driver tao bao cao khong tim thay xe
- [ ] 17. Staff resolve PROVIDE_VEHICLE_LOCATION

## Reservation Evidence (NEW)
- [ ] 18. Staff xem latest reservation cho incident (bang chung cho 4 flow)
- [ ] 19. Staff xem danh sach available slots trong cung floor (DRIVER_SLOT_OCCUPIED)
- [ ] 20. Validation: reassign slot khac floor -> FAIL
- [ ] 21. Validation: khong co latest reservation -> FAIL (latest-reservation API)
- [ ] 22. Validation: khong co latest reservation -> slot khong filter theo floor

## Workflow Enforcement
- [ ] 23. Validation: OPEN -> RESOLVED truc tiep -> FAIL
- [ ] 24. Validation: CLOSED -> OPEN -> FAIL
- [ ] 25. Lay incidents theo session

## Session Checkout
- [ ] 26. Staff checkout driver sau AUTHORIZE_CHECKOUT
- [ ] 27. Session completed thanh cong

