# Updates III. Database Design in SRS docx from railway_migration.sql schema
$ErrorActionPreference = 'Stop'
$sourceDocx = 'C:\Users\Admin\Desktop\ParkingBuildingManageSystem_SRS_final.docx'
$outputDocx = 'C:\Users\Admin\Desktop\ParkingBuildingManageSystem_SRS_final.docx'
$backupDocx = 'C:\Users\Admin\Desktop\ParkingBuildingManageSystem_SRS_final.backup.docx'
if (-not (Test-Path $backupDocx)) {
    Copy-Item -Path $sourceDocx -Destination $backupDocx -Force
}

$tableDescriptions = @(
    @('1','users','Stores all system accounts. role: ROLE_ADMIN, ROLE_MANAGER, ROLE_STAFF, ROLE_DRIVER. Supports soft delete.'),
    @('2','buildings','Parking building master data: name, address, floors, operating hours, status.'),
    @('3','building_staff','Assignment of ROLE_STAFF users to buildings (many-to-many).'),
    @('4','vehicle_types','Vehicle type catalog (Motorbike, Car, SUV, Truck) with size category.'),
    @('5','floors','Floors in a building, each linked to one vehicle type. Tracks max/current occupancy.'),
    @('6','zones','Zones within a floor. status: ACTIVE, INACTIVE, FULL, MAINTENANCE.'),
    @('7','parking_slots','Individual parking slots. slot_status: AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE.'),
    @('8','vehicles','Registered vehicles owned by users. plate_number is unique.'),
    @('9','reservations','Slot reservations with time window, grace period, and approval workflow.'),
    @('10','tickets','Ticket/QR issued per reservation. Tracks used/lost status.'),
    @('11','parking_sessions','Active parking session from check-in to checkout/exit.'),
    @('12','payments','Payment records linked to parking sessions (CASH, BANKING, MOMO, VNPAY, PAYOS).'),
    @('13','pricing_policies','Tiered/hourly/daily pricing rules per vehicle type.'),
    @('14','incidents','Incident reports for lost ticket, wrong vehicle, overtime, unpaid, etc.')
)

$tableDetails = @{
    users = @(
        @('1','user_id','CHAR(36)','','Yes','Yes','PK','UUID, auto-generated'),
        @('2','username','VARCHAR','50','Yes','Yes','','Login name'),
        @('3','password_hash','VARCHAR','255','','Yes','','Bcrypt hash'),
        @('4','full_name','VARCHAR','100','','','',''),
        @('5','phone_number','VARCHAR','20','','','',''),
        @('6','email','VARCHAR','100','','','',''),
        @('7','avatar_url','VARCHAR','255','','','',''),
        @('8','role','ENUM','','','Yes','','ROLE_ADMIN, ROLE_MANAGER, ROLE_STAFF, ROLE_DRIVER'),
        @('9','status','ENUM','','','','','ACTIVE, INACTIVE, BANNED'),
        @('10','is_active','BOOLEAN','','','','','Default TRUE'),
        @('11','is_deleted','TINYINT(1)','','','Yes','','Soft delete flag'),
        @('12','deleted_at','DATETIME','','','','',''),
        @('13','last_login','DATETIME','','','','',''),
        @('14','created_at','DATETIME','','','','',''),
        @('15','updated_at','DATETIME','','','','','Auto-updated')
    )
    buildings = @(
        @('1','building_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','building_name','VARCHAR','100','','Yes','',''),
        @('3','address','VARCHAR','255','','Yes','',''),
        @('4','total_floors','INT','','','Yes','',''),
        @('5','operating_start_time','TIME','','','','',''),
        @('6','operating_end_time','TIME','','','','',''),
        @('7','contact_number','VARCHAR','20','','','',''),
        @('8','status','ENUM','','','','','ACTIVE, INACTIVE, MAINTENANCE'),
        @('9','created_at','DATETIME','','','','',''),
        @('10','updated_at','DATETIME','','','','','')
    )
    building_staff = @(
        @('1','assignment_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','building_id','CHAR(36)','','','Yes','FK','FK -> buildings'),
        @('3','user_id','CHAR(36)','','','Yes','FK','FK -> users'),
        @('4','assigned_at','DATETIME','','','','','')
    )
    vehicle_types = @(
        @('1','vehicle_type_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','type_name','VARCHAR','50','','Yes','','e.g. Motorbike, Car'),
        @('3','size_category','VARCHAR','30','','','','SMALL, MEDIUM, LARGE'),
        @('4','description','VARCHAR','255','','','',''),
        @('5','created_at','DATETIME','','','','','')
    )
    floors = @(
        @('1','floor_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','building_id','CHAR(36)','','','Yes','FK','FK -> buildings'),
        @('3','vehicle_type_id','CHAR(36)','','','Yes','FK','FK -> vehicle_types'),
        @('4','floor_name','VARCHAR','50','','Yes','',''),
        @('5','floor_level','INT','','','Yes','',''),
        @('6','max_capacity','INT','','','','','Default 0'),
        @('7','current_occupancy','INT','','','','','Default 0'),
        @('8','status','ENUM','','','','','ACTIVE, INACTIVE, MAINTENANCE'),
        @('9','created_at','DATETIME','','','','',''),
        @('10','updated_at','DATETIME','','','','','')
    )
    zones = @(
        @('1','zone_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','floor_id','CHAR(36)','','','Yes','FK','FK -> floors'),
        @('3','zone_name','VARCHAR','50','','Yes','',''),
        @('4','max_capacity','INT','','','','','Default 0'),
        @('5','current_occupancy','INT','','','','','Default 0'),
        @('6','status','ENUM','','','','','ACTIVE, INACTIVE, FULL, MAINTENANCE'),
        @('7','created_at','DATETIME','','','','',''),
        @('8','updated_at','DATETIME','','','','','')
    )
    parking_slots = @(
        @('1','slot_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','zone_id','CHAR(36)','','','Yes','FK','FK -> zones'),
        @('3','slot_name','VARCHAR','50','','Yes','','Unique per zone'),
        @('4','slot_status','ENUM','','','','','AVAILABLE, RESERVED, OCCUPIED, MAINTENANCE'),
        @('5','note','VARCHAR','255','','','',''),
        @('6','created_at','DATETIME','','','','',''),
        @('7','updated_at','DATETIME','','','','','')
    )
    vehicles = @(
        @('1','vehicle_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','user_id','CHAR(36)','','','Yes','FK','FK -> users'),
        @('3','vehicle_type_id','CHAR(36)','','','Yes','FK','FK -> vehicle_types'),
        @('4','plate_number','VARCHAR','20','Yes','Yes','','Unique'),
        @('5','vehicle_color','VARCHAR','30','','','',''),
        @('6','brand','VARCHAR','50','','','',''),
        @('7','model','VARCHAR','50','','','',''),
        @('8','status','ENUM','','','','','ACTIVE, INACTIVE, BLOCKED'),
        @('9','created_at','DATETIME','','','','',''),
        @('10','updated_at','DATETIME','','','','','')
    )
    reservations = @(
        @('1','reservation_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','user_id','CHAR(36)','','','Yes','FK','FK -> users'),
        @('3','vehicle_id','CHAR(36)','','','Yes','FK','FK -> vehicles'),
        @('4','slot_id','CHAR(36)','','','Yes','FK','FK -> parking_slots'),
        @('5','reservation_code','VARCHAR','50','Yes','Yes','','Unique code'),
        @('6','reservation_start','DATETIME','','','Yes','',''),
        @('7','reservation_end','DATETIME','','','Yes','',''),
        @('8','grace_period_minutes','INT','','','','','Default 15'),
        @('9','estimated_fee','DECIMAL(10,2)','','','','','Default 0'),
        @('10','reservation_status','ENUM','','','','','PENDING, APPROVED, REJECTED, CANCELLED, EXPIRED, COMPLETED'),
        @('11','note','VARCHAR','255','','','',''),
        @('12','created_at','DATETIME','','','','',''),
        @('13','updated_at','DATETIME','','','','','')
    )
    tickets = @(
        @('1','ticket_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','reservation_id','CHAR(36)','','Yes','Yes','FK','FK -> reservations, 1:1'),
        @('3','ticket_code','VARCHAR','50','Yes','Yes','','Unique'),
        @('4','is_used','BOOLEAN','','','','','Default FALSE'),
        @('5','is_lost','BOOLEAN','','','','','Default FALSE'),
        @('6','status','ENUM','','','','','ACTIVE, USED, EXPIRED, LOST'),
        @('7','issued_at','DATETIME','','','','',''),
        @('8','expired_at','DATETIME','','','','',''),
        @('9','created_at','DATETIME','','','','','')
    )
    parking_sessions = @(
        @('1','session_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','vehicle_id','CHAR(36)','','','Yes','FK','FK -> vehicles'),
        @('3','slot_id','CHAR(36)','','','Yes','FK','FK -> parking_slots'),
        @('4','ticket_id','CHAR(36)','','','','FK','FK -> tickets, nullable'),
        @('5','reservation_id','CHAR(36)','','','','FK','FK -> reservations, nullable'),
        @('6','checkin_time','DATETIME','','','Yes','',''),
        @('7','checkout_time','DATETIME','','','','',''),
        @('8','estimated_fee','DECIMAL(10,2)','','','','',''),
        @('9','total_fee','DECIMAL(10,2)','','','','',''),
        @('10','parking_duration','INT','','','','','Minutes'),
        @('11','payment_status','ENUM','','','','','UNPAID, PAID, FAILED'),
        @('12','session_status','ENUM','','','','','ACTIVE, PENDING_PAYMENT, PENDING_EXIT, COMPLETED, CANCELLED'),
        @('13','note','VARCHAR','255','','','',''),
        @('14','created_by','CHAR(36)','','','','FK','FK -> users (staff)'),
        @('15','updated_by','CHAR(36)','','','','FK','FK -> users (staff)'),
        @('16','created_at','DATETIME','','','','',''),
        @('17','updated_at','DATETIME','','','','','')
    )
    payments = @(
        @('1','payment_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','session_id','CHAR(36)','','','Yes','FK','FK -> parking_sessions'),
        @('3','payment_method','ENUM','','','Yes','','CASH, BANKING, MOMO, VNPAY, PAYOS'),
        @('4','amount','DECIMAL(10,2)','','','Yes','',''),
        @('5','payment_time','DATETIME','','','','',''),
        @('6','payment_status','ENUM','','','','','PENDING, PAID, CONFIRMED, SUCCESS, FAILED'),
        @('7','transaction_code','VARCHAR','100','','','','Gateway transaction ID'),
        @('8','note','VARCHAR','255','','','',''),
        @('9','created_at','DATETIME','','','','','')
    )
    pricing_policies = @(
        @('1','policy_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','vehicle_type_id','CHAR(36)','','','Yes','FK','FK -> vehicle_types'),
        @('3','policy_name','VARCHAR','100','','Yes','',''),
        @('4','pricing_type','ENUM','','','Yes','','HOURLY, DAILY, OVERNIGHT, TIERED'),
        @('5','base_price','DECIMAL(10,2)','','','','',''),
        @('6','hourly_rate','DECIMAL(10,2)','','','','',''),
        @('7','overnight_fee','DECIMAL(10,2)','','','','',''),
        @('8','lost_ticket_fee','DECIMAL(10,2)','','','','',''),
        @('9','peak_hour_multiplier','DECIMAL(5,2)','','','','','Default 1'),
        @('10','max_daily_fee','DECIMAL(10,2)','','','','',''),
        @('11','tier1_hours','INT','','','','',''),
        @('12','tier1_price','DECIMAL(10,2)','','','','',''),
        @('13','tier2_hours','INT','','','','',''),
        @('14','tier2_price','DECIMAL(10,2)','','','','',''),
        @('15','tier3_hours','INT','','','','',''),
        @('16','tier3_price','DECIMAL(10,2)','','','','',''),
        @('17','tier4_hours','INT','','','','',''),
        @('18','tier4_price','DECIMAL(10,2)','','','','',''),
        @('19','per_day_price','DECIMAL(10,2)','','','','',''),
        @('20','effective_from','DATETIME','','','','',''),
        @('21','effective_to','DATETIME','','','','',''),
        @('22','status','ENUM','','','','','ACTIVE, INACTIVE'),
        @('23','created_at','DATETIME','','','','','')
    )
    incidents = @(
        @('1','incident_id','CHAR(36)','','Yes','Yes','PK','UUID'),
        @('2','session_id','CHAR(36)','','','Yes','FK','FK -> parking_sessions'),
        @('3','incident_type','ENUM','','','Yes','','LOST_TICKET, WRONG_VEHICLE, OVERTIME, UNPAID, OTHER'),
        @('4','description','VARCHAR','500','','','',''),
        @('5','status','ENUM','','','','','OPEN, PROCESSING, RESOLVED'),
        @('6','created_at','DATETIME','','','','','')
    )
}

function Add-Heading($doc, $text, [bool]$bold = $true) {
    $p = $doc.Paragraphs.Add()
    $p.Range.Text = $text
    $p.Range.Font.Bold = $bold
    $p.Range.Font.Size = 12
    $p.Range.InsertParagraphAfter() | Out-Null
}

function Add-Paragraph($doc, $text) {
    $p = $doc.Paragraphs.Add()
    $p.Range.Text = $text
    $p.Range.Font.Bold = $false
    $p.Range.Font.Size = 11
    $p.Range.InsertParagraphAfter() | Out-Null
}

function Add-DescriptionTable($doc, $rows) {
    $p = $doc.Paragraphs.Add()
    $range = $p.Range
    $table = $doc.Tables.Add($range, $rows.Count + 1, 3)
    $table.Cell(1,1).Range.Text = 'No'
    $table.Cell(1,2).Range.Text = 'Table'
    $table.Cell(1,3).Range.Text = 'Description'
    for ($i = 0; $i -lt $rows.Count; $i++) {
        $table.Cell($i+2,1).Range.Text = $rows[$i][0]
        $table.Cell($i+2,2).Range.Text = $rows[$i][1]
        $table.Cell($i+2,3).Range.Text = $rows[$i][2]
    }
    $table.Rows(1).Range.Font.Bold = $true
    $table.Borders.Enable = 1
    $doc.Paragraphs.Add().Range.InsertParagraphAfter() | Out-Null
}

function Add-DetailTable($doc, $tableName, $fields) {
    Add-Heading $doc $tableName
    $p = $doc.Paragraphs.Add()
    $range = $p.Range
    $table = $doc.Tables.Add($range, $fields.Count + 1, 8)
    $headers = @('#','Field name','Type','Size','Unique','Not Null','PK/FK','Notes')
    for ($c = 1; $c -le 8; $c++) { $table.Cell(1,$c).Range.Text = $headers[$c-1] }
    for ($i = 0; $i -lt $fields.Count; $i++) {
        for ($c = 1; $c -le 8; $c++) { $table.Cell($i+2,$c).Range.Text = $fields[$i][$c-1] }
    }
    $table.Rows(1).Range.Font.Bold = $true
    $table.Borders.Enable = 1
    $doc.Paragraphs.Add().Range.InsertParagraphAfter() | Out-Null
}

$word = New-Object -ComObject Word.Application
$word.Visible = $false
$doc = $word.Documents.Open($backupDocx)

$find = $doc.Content.Find
$find.ClearFormatting()
$found = $find.Execute('III. Database Design')
if (-not $found) { throw 'Section III. Database Design not found' }
$start = $find.Parent.Start
$end = $doc.Content.End
$doc.Range($start, $end).Delete()

Add-Heading $doc 'III. Database Design'
Add-Heading $doc '1. Database Schema'
Add-Paragraph $doc 'The following schema describes the complete entity-relationship design for the Parking Building Management System. All primary keys use UUID (CHAR(36)). The database runs on MySQL (Railway). Soft deletes are supported on the users table via is_deleted and deleted_at.'
Add-Paragraph $doc 'Entity relationship overview: users own vehicles and make reservations; reservations generate tickets and lead to parking_sessions; payments are linked to sessions; buildings contain floors, zones, and parking_slots; pricing_policies apply per vehicle_type; incidents are reported against parking_sessions; building_staff maps staff users to buildings.'
Add-Paragraph $doc 'Table Description'
Add-DescriptionTable $doc $tableDescriptions
Add-Heading $doc '2. Table Detail'
Add-Paragraph $doc 'Below are the detailed field descriptions for each table in the database, derived from railway_migration.sql.'
foreach ($name in @('users','buildings','building_staff','vehicle_types','floors','zones','parking_slots','vehicles','reservations','tickets','parking_sessions','payments','pricing_policies','incidents')) {
    Add-DetailTable $doc $name $tableDetails[$name]
}

$doc.SaveAs([ref]$outputDocx)
$doc.Close($false)
$word.Quit()
[System.Runtime.Interopservices.Marshal]::ReleaseComObject($word) | Out-Null
Write-Host "Updated: $outputDocx"
Write-Host "Backup:  $backupDocx"
