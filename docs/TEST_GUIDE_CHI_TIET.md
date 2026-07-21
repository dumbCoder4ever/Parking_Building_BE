# Huong Dan Test Chi Tiet - Phase 2 & Phase 3

## Truoc Khi Bat Dau

Dam bao:
1. App da start thanh cong (khong con loi column missing)
2. Database da chay 2 migration:
   - ALTER TABLE parking_sessions ADD COLUMN incident_authorized BOOLEAN DEFAULT FALSE;
   - ALTER TABLE incidents ADD COLUMN resolution_action VARCHAR(500);
3. Co 2 tai khoan test: Staff va Driver

---

## BUOC 1: Kiem Tra Token

### 1.1. Lay Token Staff
curl -X POST http://localhost:8080/api/auth/login \
  -H \
Content-Type:
application/json\ \
  -d '{\email\: \staff@example.com\, \password\: \password123\}'

Luu lai: staff_token

### 1.2. Lay Token Driver
curl -X POST http://localhost:8080/api/auth/login \
  -H \Content-Type:
application/json\ \
  -d '{\email\: \driver@example.com\, \password\: \password123\}'

Luu lai: driver_token

---

## BUOC 2: Tao Du Lieu Test

Chay SQL nay trong database:

-- 1. Vehicle
INSERT INTO vehicles (vehicle_id, plate_number, vehicle_type_id, brand, model, status, user_id)
VALUES ('VEH-TEST-001', '30A-123456', 'VT-CAR', 'Toyota', 'Vios', 'ACTIVE', 'user-123')
ON DUPLICATE KEY UPDATE plate_number = plate_number;

-- 2. Slot
INSERT INTO parking_slots (slot_id, slot_name, slot_status, zone_id)
VALUES ('SLOT-TEST-001', 'B-205', 'AVAILABLE', 'ZONE-A')
ON DUPLICATE KEY UPDATE slot_status = 'AVAILABLE';

-- 3. Session
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized)
VALUES ('SESSION-TEST-001', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE)
ON DUPLICATE KEY UPDATE session_status = 'ACTIVE';

-- 4. Ticket
INSERT INTO tickets (ticket_id, ticket_code, session_id, status, issued_at)
VALUES ('TICKET-001', 'TKT-789456', 'SESSION-TEST-001', 'ACTIVE', NOW())
ON DUPLICATE KEY UPDATE ticket_code = 'TKT-789456';

---

## BUOC 3: Test API 1 - Driver Tao Bao Cao

### POST /api/incidents/driver

curl -X POST http://localhost:8080/api/incidents/driver \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
DRIVER_TOKEN\ \
  -d '{\sessionId\: \SESSION-TEST-001\, \incidentType\: \DRIVER_LOST_TICKET\, \description\: \Toi
da
mat
ve
giu
xe\}'

Expected Response (201):
{\success\: true, \message\: \Driver
report
submitted\, \data\: {\incidentId\: \inc-xxx\, \sessionId\: \SESSION-TEST-001\, \ticketCode\: \TKT-789456\, \incidentType\: \DRIVER_LOST_TICKET\, \status\: \OPEN\}}

Luu lai: incidentId tu response

---

## BUOC 4: Test API 2 - Driver Xem Bao Cao Cua Minh

### GET /api/incidents/driver/me

curl -X GET http://localhost:8080/api/incidents/driver/me \
  -H \Authorization:
Bearer
DRIVER_TOKEN\

Expected: Thay bao cao vua tao o buoc 3

---

## BUOC 5: Test API 3 - Staff Xem Tat Ca Driver Reports

### GET /api/incidents/driver/all

curl -X GET http://localhost:8080/api/incidents/driver/all \
  -H \Authorization:
Bearer
STAFF_TOKEN\

Expected: Thay tat ca driver reports

---

## BUOC 6: Test API 4 - Staff Cap Nhat Incident

### PUT /api/incidents/{id}/status

Thay incidentId bang gia tri tu buoc 3

#### 6.1. Chuyen thanh IN_PROGRESS
curl -X PUT \http://localhost:8080/api/incidents/INCIDENT_ID/status?status=IN_PROGRESS\ \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\ \
  -d '{}'

#### 6.2. Resolve voi AUTHORIZE_CHECKOUT
curl -X PUT \http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED\ \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\ \
  -d '{\resolution\: \Da
xac
nhan
chu
xe
cho
phep
checkout\, \resolutionAction\: \AUTHORIZE_CHECKOUT\}'

Kiem tra database:
SELECT session_id, incident_authorized FROM parking_sessions WHERE session_id = 'SESSION-TEST-001';
Expected: incident_authorized = true

---

## BUOC 7: Test API 5 - Staff Checkout Cho Driver

### POST /api/sessions/driver/checkout

curl -X POST \http://localhost:8080/api/sessions/driver/checkout?sessionId=SESSION-TEST-001\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\

Expected: {\success\: true, \message\: \Driver
checkout
successful\, \data\: {\paymentStatus\: \PAID\}}

---

## BUOC 8: Test API 6 - Lay Incidents Theo Session

### GET /api/incidents/by-session/{sessionId}

curl -X GET \http://localhost:8080/api/incidents/by-session/SESSION-TEST-001\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\

---

## BUOC 9: Test Them - Sai Phi

Tao session moi:
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, total_fee, incident_authorized)
VALUES ('SESSION-TEST-002', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', 50000, FALSE);

Driver bao:
curl -X POST http://localhost:8080/api/incidents/driver \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
DRIVER_TOKEN\ \
  -d '{\sessionId\: \SESSION-TEST-002\, \incidentType\: \DRIVER_INCORRECT_FEE\, \description\: \Bi
tinh
50k
nhung
chi
nen
tra
30k\}'

Staff resolve:
curl -X PUT \http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED\ \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\ \
  -d '{\resolution\: \Hoan
20k
do
tinh
sai\, \resolutionAction\: \UPDATE_PAYMENT\, \adjustedAmount\: 30000}'

Kiem tra:
SELECT total_fee FROM parking_sessions WHERE session_id = 'SESSION-TEST-002';
Expected: 30000

---

## BUOC 10: Test Them - Khong Tim Thay Xe

Tao session moi:
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized)
VALUES ('SESSION-TEST-003', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE);

Driver bao:
curl -X POST http://localhost:8080/api/incidents/driver \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
DRIVER_TOKEN\ \
  -d '{\sessionId\: \SESSION-TEST-003\, \incidentType\: \DRIVER_CANNOT_FIND_VEHICLE\, \description\: \Toi
khong
nho
cho
do
xe\}'

Staff resolve:
curl -X PUT \http://localhost:8080/api/incidents/INCIDENT_ID/status?status=RESOLVED\ \
  -H \Content-Type:
application/json\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\ \
  -d '{\resolution\: \Xe
dang
o
tang
2
slot
B-205\, \resolutionAction\: \PROVIDE_VEHICLE_LOCATION\}'

---

## BUOC 11: Test Phase 2 - Auto-Create SLOT_CONFLICT

Tao xung dot:
INSERT INTO parking_sessions (session_id, slot_id, vehicle_id, session_status, checkin_time, payment_status, incident_authorized)
VALUES ('SESSION-CONFLICT-001', 'SLOT-TEST-001', 'VEH-TEST-001', 'ACTIVE', NOW(), 'UNPAID', FALSE);

Doi 60 giay, kiem tra:
curl -X GET \http://localhost:8080/api/incidents/by-session/SESSION-TEST-001\ \
  -H \Authorization:
Bearer
STAFF_TOKEN\

Expected: Co them incident moi voi incidentType = SLOT_CONFLICT

---

## CHECKLIST HOAN TAT

[ ] 1. Driver tao bao cao mat ve
[ ] 2. Driver xem bao cao cua minh
[ ] 3. Staff xem tat ca driver reports
[ ] 4. Staff chuyen IN_PROGRESS
[ ] 5. Staff resolve AUTHORIZE_CHECKOUT
[ ] 6. Staff checkout driver
[ ] 7. Staff close incident
[ ] 8. Lay incidents theo session
[ ] 9. Driver bao sai phi + UPDATE_PAYMENT
[ ] 10. Driver bao khong tim thay xe
[ ] 11. Auto-create SLOT_CONFLICT

