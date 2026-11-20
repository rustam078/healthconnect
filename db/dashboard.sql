-- HealthConnect - dashboard seed
--
-- Everything the widget engine draws from: the reusable filter catalogue, the widget
-- library, and the boards those widgets sit on. Kept apart from data.sql because this is
-- the part you will rewrite while designing dashboards, whereas the patients, doctors and
-- appointments underneath it stay put.
--
-- Re-runnable: it clears the three tables first, so loading it twice leaves the same rows
-- rather than duplicates. Execution logs go too - they point at widget ids that are about
-- to mean something different.
--
-- Load AFTER data.sql:
--   mysql -uroot -p healthconnect < db/data.sql
--   mysql -uroot -p healthconnect < db/dashboard.sql

SET FOREIGN_KEY_CHECKS = 0;

DELETE FROM widget_execution_log;
DELETE FROM board;
DELETE FROM widget;
DELETE FROM widget_filter;

-- ---------- widget_filter: the reusable filter catalogue ----------
--
-- A filter is defined ONCE here and referenced by id from any widget that needs it. The
-- `source` query returns two columns - id and value - and is what fills the dropdown.
-- It may take the same named parameters a widget takes, so a list can narrow itself.
INSERT INTO widget_filter (id, name, description, source) VALUES
  ('doctorId', 'Doctor', 'Active doctors, searchable by name.',
   'SELECT CAST(d.id AS CHAR) id, CONCAT(d.first_name, '' '', d.last_name) value
FROM doctors d
WHERE d.is_deleted = false
  AND (:search IS NULL OR CONCAT(d.first_name, '' '', d.last_name) LIKE CONCAT(''%'', :search, ''%''))
ORDER BY d.first_name'),

  ('patientId', 'Patient', 'Active patients, searchable by name or code.',
   'SELECT CAST(p.id AS CHAR) id, CONCAT(p.first_name, '' '', p.last_name, '' ('', p.patient_code, '')'') value
FROM patient p
WHERE p.is_deleted = false
  AND (:search IS NULL OR CONCAT(p.first_name, '' '', p.last_name, '' '', p.patient_code) LIKE CONCAT(''%'', :search, ''%''))
ORDER BY p.first_name'),

  ('specialtyId', 'Specialty', 'Medical specialties.',
   'SELECT CAST(s.id AS CHAR) id, s.name value
FROM specialties s
WHERE s.is_deleted = false
ORDER BY s.name'),

  ('status', 'Appointment status', 'The three states an appointment can be in.',
   'SELECT ''SCHEDULED'' id, ''Scheduled'' value
UNION ALL SELECT ''COMPLETED'', ''Completed''
UNION ALL SELECT ''CANCELLED'', ''Cancelled'''),

  ('bloodGroup', 'Blood group', 'Blood groups as they are written on a form.',
   'SELECT DISTINCT p.blood_group id,
       REPLACE(REPLACE(p.blood_group, ''_POSITIVE'', ''+''), ''_NEGATIVE'', ''-'') value
FROM patient p
WHERE p.is_deleted = false AND p.blood_group IS NOT NULL
ORDER BY 2'),

  ('gender', 'Gender', 'Gender values used across the app.',
   'SELECT ''MALE'' id, ''Male'' value
UNION ALL SELECT ''FEMALE'', ''Female''
UNION ALL SELECT ''OTHER'', ''Other''');

-- ---------- widget: the dashboard library ----------
--
-- Two things to know about the `filters` column.
--
-- 1. It is a plain ARRAY of the filters a widget wants a CONTROL for. An entry with
--    "source":"db" names a row in widget_filter, which knows how to fetch the choices;
--    date and text entries need no source at all.
--
-- 2. It is NOT a list of the query's parameters. Any :name in the SQL can be sent by name
--    in the request body without appearing here - a filter is only declared when a person
--    needs something to click. That is why the date-range widgets below carry filters and
--    the plain COUNT cards carry none, while both bind their parameters the same way.
INSERT INTO `widget` (`id`, `code`, `name`, `description`, `module`, `type`, `status`, `enabled`, `filters`, `sql_template`, `created_at`, `updated_at`, `is_deleted`) VALUES

-- ----- plain counts: no controls, nothing to declare -----
(1, 'total-doctors', 'Total Doctors', 'Active doctors on the books.', 'WIDGET', 'COUNT', 'APPROVED', 1, NULL,
 'SELECT COUNT(*) AS `Doctors` FROM doctors WHERE is_deleted = false',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(2, 'total-patients', 'Total Patients', 'Active patients on the books.', 'WIDGET', 'COUNT', 'APPROVED', 1, NULL,
 'SELECT COUNT(*) AS `Patients` FROM patient WHERE is_deleted = false',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(3, 'todays-appointments', 'Today''s Appointments', 'Appointments booked for today.', 'WIDGET', 'COUNT', 'APPROVED', 1, NULL,
 'SELECT COUNT(*) AS `Today` FROM appointments WHERE is_deleted = false AND appointment_date = CURDATE()',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

-- ----- a count WITH controls: doctor, status and a date range -----
(4, 'appointment-count', 'Appointment Count', 'Appointments matching the chosen doctor, status and dates.', 'WIDGET', 'COUNT', 'APPROVED', 1,
 '[{"id":"doctorId","type":"single-select","label":"Doctor","source":"db"},
   {"id":"status","type":"single-select","label":"Status","source":"db"},
   {"id":"fromDate","type":"date","label":"From"},
   {"id":"toDate","type":"date","label":"To"}]',
 'SELECT COUNT(*) AS `Appointments`
FROM appointments a
WHERE a.is_deleted = false
  AND a.doctor_id = COALESCE(:doctorId, a.doctor_id)
  AND a.status = COALESCE(:status, a.status)
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

-- ----- charts -----
(5, 'appointment-status-split', 'Appointment Status Split', 'How appointments divide between the three states.', 'WIDGET', 'PIE', 'APPROVED', 1,
 '[{"id":"fromDate","type":"date","label":"From"},{"id":"toDate","type":"date","label":"To"}]',
 'SELECT a.status AS `Status`, COUNT(*) AS `Appointments`
FROM appointments a
WHERE a.is_deleted = false
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
GROUP BY a.status',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(6, 'appointments-per-day', 'Appointments Per Day', 'Daily trend over the chosen window.', 'WIDGET', 'LINE', 'APPROVED', 1,
 '[{"id":"doctorId","type":"single-select","label":"Doctor","source":"db"},
   {"id":"fromDate","type":"date","label":"From"},
   {"id":"toDate","type":"date","label":"To"}]',
 'SELECT DATE_FORMAT(a.appointment_date, ''%d %b'') AS `Day`, COUNT(*) AS `Appointments`
FROM appointments a
WHERE a.is_deleted = false
  AND a.doctor_id = COALESCE(:doctorId, a.doctor_id)
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
GROUP BY a.appointment_date
ORDER BY a.appointment_date',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(7, 'appointments-by-specialty', 'Appointments by Specialty', 'Which specialties are busiest.', 'WIDGET', 'BAR', 'APPROVED', 1,
 '[{"id":"specialtyId","type":"single-select","label":"Specialty","source":"db"},
   {"id":"fromDate","type":"date","label":"From"},
   {"id":"toDate","type":"date","label":"To"}]',
 'SELECT s.name AS `Specialty`, COUNT(a.id) AS `Appointments`
FROM appointments a
JOIN doctor_specialties_map m ON m.doctor_id = a.doctor_id
JOIN specialties s ON s.id = m.specialty_id
WHERE a.is_deleted = false AND s.is_deleted = false
  AND s.id = COALESCE(:specialtyId, s.id)
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
GROUP BY s.id, s.name
ORDER BY `Appointments` DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(8, 'doctors-per-specialty', 'Doctors per Specialty', 'How many doctors each specialty has.', 'WIDGET', 'BAR', 'APPROVED', 1, NULL,
 'SELECT s.name AS `Specialty`, COUNT(DISTINCT d.id) AS `Doctors`
FROM doctors d
JOIN doctor_specialties_map m ON m.doctor_id = d.id
JOIN specialties s ON s.id = m.specialty_id
WHERE d.is_deleted = false AND s.is_deleted = false
GROUP BY s.id, s.name
ORDER BY `Doctors` DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(9, 'patients-by-blood-group', 'Patients by Blood Group', 'Blood group spread across the register.', 'WIDGET', 'BAR', 'APPROVED', 1,
 '[{"id":"gender","type":"single-select","label":"Gender","source":"db"}]',
 'SELECT REPLACE(REPLACE(p.blood_group, ''_POSITIVE'', ''+''), ''_NEGATIVE'', ''-'') AS `Blood group`,
       COUNT(*) AS `Patients`
FROM patient p
WHERE p.is_deleted = false AND p.blood_group IS NOT NULL
  AND p.gender = COALESCE(:gender, p.gender)
GROUP BY p.blood_group
ORDER BY `Patients` DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(10, 'patients-by-gender', 'Patients by Gender', 'Gender split of the patient register.', 'WIDGET', 'PIE', 'APPROVED', 1, NULL,
 'SELECT p.gender AS `Gender`, COUNT(*) AS `Patients`
FROM patient p WHERE p.is_deleted = false GROUP BY p.gender',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(11, 'busiest-doctors', 'Busiest Doctors', 'Doctors with the most appointments in the window.', 'WIDGET', 'BAR', 'APPROVED', 1,
 '[{"id":"fromDate","type":"date","label":"From"},{"id":"toDate","type":"date","label":"To"}]',
 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, COUNT(a.id) AS `Appointments`
FROM appointments a
JOIN doctors d ON d.id = a.doctor_id
WHERE a.is_deleted = false AND d.is_deleted = false
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
GROUP BY d.id, d.first_name, d.last_name
ORDER BY `Appointments` DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

-- ----- tables -----
(12, 'appointment-schedule', 'Appointment Schedule', 'Who is seeing whom, and when.', 'WIDGET', 'TABLE', 'APPROVED', 1,
 '[{"id":"doctorId","type":"single-select","label":"Doctor","source":"db"},
   {"id":"status","type":"single-select","label":"Status","source":"db"},
   {"id":"fromDate","type":"date","label":"From"},
   {"id":"toDate","type":"date","label":"To"}]',
 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`,
       CONCAT(p.first_name, '' '', p.last_name) AS `Patient`,
       DATE_FORMAT(a.appointment_date, ''%d %b %Y'') AS `Date`,
       DATE_FORMAT(a.start_time, ''%h:%i %p'') AS `Start`,
       CONCAT(a.duration_minutes, '' min'') AS `Duration`,
       a.status AS `Status`
FROM appointments a
JOIN doctors d ON d.id = a.doctor_id
JOIN patient p ON p.id = a.patient_id
WHERE a.is_deleted = false AND d.is_deleted = false
  AND a.doctor_id = COALESCE(:doctorId, a.doctor_id)
  AND a.status = COALESCE(:status, a.status)
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
ORDER BY a.appointment_date DESC, a.start_time',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(13, 'patient-directory', 'Patient Directory', 'The patient register, searchable.', 'WIDGET', 'TABLE', 'APPROVED', 1,
 '[{"id":"patientId","type":"single-select","label":"Patient","source":"db"},
   {"id":"bloodGroup","type":"single-select","label":"Blood group","source":"db"},
   {"id":"gender","type":"single-select","label":"Gender","source":"db"},
   {"id":"search","type":"text","label":"Search name"}]',
 'SELECT CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, p.patient_code AS `Code`,
       p.phone AS `Phone`, p.gender AS `Gender`, p.blood_group AS `Blood group`
FROM patient p
WHERE p.is_deleted = false
  AND p.id = COALESCE(:patientId, p.id)
  AND p.blood_group = COALESCE(:bloodGroup, p.blood_group)
  AND p.gender = COALESCE(:gender, p.gender)
  AND (:search IS NULL OR CONCAT(p.first_name, '' '', p.last_name) LIKE CONCAT(''%'', :search, ''%''))
ORDER BY p.first_name',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(14, 'doctor-directory', 'Doctor Directory', 'Doctors with their qualification and fee.', 'WIDGET', 'TABLE', 'APPROVED', 1,
 '[{"id":"specialtyId","type":"single-select","label":"Specialty","source":"db"},
   {"id":"gender","type":"single-select","label":"Gender","source":"db"},
   {"id":"minYears","type":"number","label":"Min years"}]',
 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, d.qualification AS `Qualification`,
       d.experience_years AS `Years`, d.consultation_fee AS `Fee`, d.gender AS `Gender`
FROM doctors d
LEFT JOIN doctor_specialties_map m ON m.doctor_id = d.id
WHERE d.is_deleted = false
  AND (:specialtyId IS NULL OR m.specialty_id = :specialtyId)
  AND d.gender = COALESCE(:gender, d.gender)
  AND d.experience_years >= COALESCE(:minYears, d.experience_years)
GROUP BY d.id, d.first_name, d.last_name, d.qualification, d.experience_years, d.consultation_fee, d.gender
ORDER BY d.experience_years DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(15, 'specialty-list', 'Specialty List', 'Specialties and what they cover.', 'WIDGET', 'TABLE', 'APPROVED', 1,
 '[{"id":"search","type":"text","label":"Search"}]',
 'SELECT s.name AS `Specialty`, s.description AS `Details`
FROM specialties s
WHERE s.is_deleted = false
  AND (:search IS NULL OR s.name LIKE CONCAT(''%'', :search, ''%''))
ORDER BY s.name',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(16, 'patient-visit-history', 'Patient Visit History', 'Every visit for one patient.', 'WIDGET', 'TABLE', 'APPROVED', 1,
 '[{"id":"patientId","type":"single-select","label":"Patient","source":"db"},
   {"id":"status","type":"single-select","label":"Status","source":"db"}]',
 'SELECT DATE_FORMAT(a.appointment_date, ''%d %b %Y'') AS `Date`,
       CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`,
       DATE_FORMAT(a.start_time, ''%h:%i %p'') AS `Start`, a.status AS `Status`
FROM appointments a
JOIN doctors d ON d.id = a.doctor_id
WHERE a.is_deleted = false
  AND a.patient_id = COALESCE(:patientId, a.patient_id)
  AND a.status = COALESCE(:status, a.status)
ORDER BY a.appointment_date DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(17, 'doctor-availability', 'Doctor Availability', 'Working hours and breaks by weekday.', 'WIDGET', 'TABLE', 'APPROVED', 1,
 '[{"id":"doctorId","type":"single-select","label":"Doctor","source":"db"}]',
 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, av.day_of_week AS `Day`,
       DATE_FORMAT(av.start_time, ''%h:%i %p'') AS `From`, DATE_FORMAT(av.end_time, ''%h:%i %p'') AS `To`,
       DATE_FORMAT(av.break_start_time, ''%h:%i %p'') AS `Break from`, DATE_FORMAT(av.break_end_time, ''%h:%i %p'') AS `Break to`
FROM doctor_availabilities av
JOIN doctors d ON d.id = av.doctor_id
WHERE av.is_deleted = false AND d.is_deleted = false
  AND av.doctor_id = COALESCE(:doctorId, av.doctor_id)
ORDER BY d.first_name, FIELD(av.day_of_week, ''MONDAY'',''TUESDAY'',''WEDNESDAY'',''THURSDAY'',''FRIDAY'',''SATURDAY'',''SUNDAY'')',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(18, 'revenue-by-doctor', 'Revenue by Doctor', 'Fees from completed visits.', 'WIDGET', 'BAR', 'APPROVED', 1,
 '[{"id":"doctorId","type":"single-select","label":"Doctor","source":"db"},
   {"id":"fromDate","type":"date","label":"From"},
   {"id":"toDate","type":"date","label":"To"}]',
 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, SUM(d.consultation_fee) AS `Revenue`
FROM appointments a
JOIN doctors d ON d.id = a.doctor_id
WHERE a.is_deleted = false AND a.status = ''COMPLETED''
  AND a.doctor_id = COALESCE(:doctorId, a.doctor_id)
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
GROUP BY d.id, d.first_name, d.last_name
ORDER BY `Revenue` DESC',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

-- ----- integrations: saved queries used as an API, not shown in the gallery -----
(19, 'appointments-api', 'Appointments API', 'Appointments as a callable endpoint.', 'INTEGRATION', 'TABLE', 'APPROVED', 1,
 '[{"id":"doctorId","type":"single-select","label":"Doctor","source":"db"},
   {"id":"status","type":"single-select","label":"Status","source":"db"},
   {"id":"fromDate","type":"date","label":"From"},
   {"id":"toDate","type":"date","label":"To"}]',
 'SELECT a.id AS `Ref`, CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`,
       CONCAT(p.first_name, '' '', p.last_name) AS `Patient`,
       a.appointment_date AS `Date`, a.start_time AS `Start`, a.status AS `Status`
FROM appointments a
JOIN doctors d ON d.id = a.doctor_id
JOIN patient p ON p.id = a.patient_id
WHERE a.is_deleted = false
  AND a.doctor_id = COALESCE(:doctorId, a.doctor_id)
  AND a.status = COALESCE(:status, a.status)
  AND a.appointment_date >= COALESCE(:fromDate, a.appointment_date)
  AND a.appointment_date <= COALESCE(:toDate, a.appointment_date)
ORDER BY a.appointment_date DESC, a.start_time',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),

(20, 'doctor-lookup-api', 'Doctor Lookup API', 'Doctor id and name, for another system to consume.', 'INTEGRATION', 'TABLE', 'APPROVED', 1, NULL,
 'SELECT d.id AS `id`, CONCAT(d.first_name, '' '', d.last_name) AS `name`, d.qualification AS `qualification`
FROM doctors d
WHERE d.is_deleted = false
  AND (:search IS NULL OR CONCAT(d.first_name, '' '', d.last_name) LIKE CONCAT(''%'', :search, ''%''))
ORDER BY d.first_name',
 '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0);

INSERT INTO `board` (`id`, `name`, `layout`, `created_at`, `updated_at`, `is_deleted`) VALUES
 (1, 'Hospital Overview',
  '[{"widgetId":1,"x":0,"y":0,"w":1,"h":3},{"widgetId":2,"x":1,"y":0,"w":1,"h":3},{"widgetId":3,"x":2,"y":0,"w":1,"h":3},{"widgetId":4,"x":0,"y":3,"w":1,"h":4},{"widgetId":5,"x":1,"y":3,"w":2,"h":4},{"widgetId":6,"x":0,"y":7,"w":3,"h":5},{"widgetId":12,"x":0,"y":12,"w":3,"h":7}]',
  '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0),
 (2, 'People',
  '[{"widgetId":13,"x":0,"y":0,"w":3,"h":6},{"widgetId":9,"x":0,"y":6,"w":2,"h":5},{"widgetId":10,"x":2,"y":6,"w":1,"h":5},{"widgetId":14,"x":0,"y":11,"w":3,"h":6}]',
  '2026-08-29 09:00:00.000000', '2026-08-29 09:00:00.000000', 0);


SET FOREIGN_KEY_CHECKS = 1;

-- Sanity check after loading:
--   SELECT 'widget_filter', COUNT(*) FROM widget_filter
--   UNION ALL SELECT 'widget', COUNT(*) FROM widget
--   UNION ALL SELECT 'board', COUNT(*) FROM board;


    -- ---------- ai_knowledge (what the AI is told about the schema) ----------
INSERT INTO `ai_knowledge` (`id`, `table_name`, `purpose`, `columns_info`, `hints`, `enabled`, `created_at`, `updated_at`, `is_deleted`) VALUES
   (1, 'doctors', 'Doctors who work at the hospital.', 'id, doctor_code, first_name, last_name, email, phone, gender, qualification, experience_years, consultation_fee, is_deleted', 'Always filter is_deleted = false. A doctor\'s specialty is found by joining to specialties through doctor_specialties_map.', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (2, 'specialties', 'Medical specialties, e.g. Cardiology, Neurology.', 'id, name, description, is_deleted', 'The specialty name is in specialties.name (e.g. \'Cardiology\'). Filter is_deleted = false.', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (3, 'doctor_specialties_map', 'Link table connecting doctors and specialties (many-to-many).', 'id, doctor_id, specialty_id', 'Join doctors.id = doctor_specialties_map.doctor_id and specialties.id = doctor_specialties_map.specialty_id.', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (4, 'patient', 'Patients registered at the hospital.', 'id, patient_code, first_name, last_name, date_of_birth, gender, phone, email, address, blood_group, is_deleted', 'Filter is_deleted = false. Table name is singular: patient.', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (5, 'appointments', 'Appointments between a patient and a doctor.', 'id, patient_id, doctor_id, appointment_date, start_time, end_time, duration_minutes, status, is_deleted', 'Join appointments.doctor_id = doctors.id and appointments.patient_id = patient.id. status is an enum. Filter is_deleted = false.', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (6, 'doctor_availabilities', 'The weekly working hours of each doctor: which days they work, from when to when, and their daily break.', 'id, doctor_id, day_of_week, start_time, end_time, break_start_time, break_end_time, is_deleted', 'Join doctor_availabilities.doctor_id = doctors.id. Filter is_deleted = false. day_of_week is text holding an uppercase English day name: MONDAY, TUESDAY, WEDNESDAY, THURSDAY, FRIDAY, SATURDAY, SUNDAY - compare it as a string, never as a number. start_time and end_time are TIME columns (e.g. 09:00:00) and are never null. break_start_time and break_end_time are TIME columns that CAN be null, meaning that day has no break - use them only with a null check. There is at most ONE row per doctor per day (unique on doctor_id + day_of_week), so counting rows for a doctor gives the number of days they work. A doctor with no row for a day does not work that day. To ask about today, compare day_of_week to UPPER(DAYNAME(CURDATE())). Working minutes for a day = TIMESTAMPDIFF(MINUTE, start_time, end_time) minus the break when both break columns are set.', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0);

-- ---------- ai_prompt_example (question -> known-good SQL) ----------
-- Two jobs, both real:
--   1. sent to the AI as few-shot examples, so it copies these joins and aliases
--   2. an EXACT question match skips the AI entirely and reuses the SQL below
--      (SqlDraftService step 1) - free, instant, and always correct.
-- `category` groups them in the Ask AI tab sample-prompt dialog.
-- These are deliberately REALISTIC: several columns, joins across doctors,
-- patients, specialties, appointments and availability - not one-column counts.
-- Every query here was run against this data first: all return rows, none error.
INSERT INTO `ai_prompt_example` (`id`, `question`, `generated_sql`, `category`, `enabled`, `created_at`, `updated_at`, `is_deleted`) VALUES
  (1, 'list doctors with their specialty, experience and consultation fee', 'SELECT d.first_name AS `First Name`, d.last_name AS `Last Name`, s.name AS `Specialty`, d.experience_years AS `Experience`, d.consultation_fee AS `Fee` FROM doctors d JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false AND s.is_deleted = false ORDER BY d.consultation_fee DESC', 'Doctors', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (2, 'show doctor contact details with qualification and specialty', 'SELECT d.doctor_code AS `Code`, CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, d.qualification AS `Qualification`, s.name AS `Specialty`, d.phone AS `Phone`, d.email AS `Email` FROM doctors d JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false ORDER BY d.first_name', 'Doctors', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (3, 'count doctors by specialty and gender', 'SELECT s.name AS `Specialty`, d.gender AS `Gender`, COUNT(*) AS `Doctors` FROM doctors d JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false AND s.is_deleted = false GROUP BY s.name, d.gender ORDER BY s.name, `Doctors` DESC', 'Doctors', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (4, 'which doctors see the most different patients', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, COUNT(DISTINCT a.patient_id) AS `Patients Seen`, COUNT(a.id) AS `Appointments` FROM doctors d JOIN appointments a ON a.doctor_id = d.id AND a.is_deleted = false JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false GROUP BY d.id, d.first_name, d.last_name, s.name ORDER BY `Patients Seen` DESC LIMIT 25', 'Doctors', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (5, 'most experienced doctor in each specialty', 'SELECT s.name AS `Specialty`, CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, d.experience_years AS `Experience`, d.consultation_fee AS `Fee` FROM doctors d JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false AND d.experience_years = (SELECT MAX(d2.experience_years) FROM doctors d2 JOIN doctor_specialties_map m2 ON m2.doctor_id = d2.id WHERE m2.specialty_id = s.id AND d2.is_deleted = false) ORDER BY s.name', 'Doctors', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (6, 'list patients with age, gender, blood group and phone', 'SELECT p.patient_code AS `Code`, CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, TIMESTAMPDIFF(YEAR, p.date_of_birth, CURDATE()) AS `Age`, p.gender AS `Gender`, p.blood_group AS `Blood Group`, p.phone AS `Phone` FROM patient p WHERE p.is_deleted = false ORDER BY p.first_name', 'Patients', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (7, 'show each patient last appointment with the doctor they saw', 'SELECT CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, MAX(a.appointment_date) AS `Last Visit`, CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty` FROM patient p JOIN appointments a ON a.patient_id = p.id AND a.is_deleted = false JOIN doctors d ON d.id = a.doctor_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE p.is_deleted = false GROUP BY p.id, p.first_name, p.last_name, d.first_name, d.last_name, s.name ORDER BY `Last Visit` DESC LIMIT 50', 'Patients', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (8, 'which patients visit most often and how much have they spent', 'SELECT CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, p.blood_group AS `Blood Group`, COUNT(a.id) AS `Visits`, SUM(d.consultation_fee) AS `Total Fees` FROM patient p JOIN appointments a ON a.patient_id = p.id AND a.is_deleted = false JOIN doctors d ON d.id = a.doctor_id WHERE p.is_deleted = false GROUP BY p.id, p.first_name, p.last_name, p.blood_group ORDER BY `Visits` DESC LIMIT 25', 'Patients', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (9, 'count patients by blood group and gender', 'SELECT p.blood_group AS `Blood Group`, p.gender AS `Gender`, COUNT(*) AS `Patients` FROM patient p WHERE p.is_deleted = false AND p.blood_group IS NOT NULL GROUP BY p.blood_group, p.gender ORDER BY p.blood_group, `Patients` DESC', 'Patients', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (10, 'list patients who have never booked an appointment', 'SELECT p.patient_code AS `Code`, CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, p.gender AS `Gender`, p.phone AS `Phone`, p.email AS `Email` FROM patient p LEFT JOIN appointments a ON a.patient_id = p.id AND a.is_deleted = false WHERE p.is_deleted = false AND a.id IS NULL ORDER BY p.first_name LIMIT 100', 'Patients', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (11, 'show todays appointments with doctor, patient, time and status', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, a.start_time AS `Start`, a.duration_minutes AS `Minutes`, a.status AS `Status` FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN patient p ON p.id = a.patient_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE a.is_deleted = false AND a.appointment_date = CURDATE() ORDER BY a.start_time', 'Appointments', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (12, 'list upcoming appointments with specialty, doctor, patient and date', 'SELECT a.appointment_date AS `Date`, a.start_time AS `Start`, s.name AS `Specialty`, CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, CONCAT(p.first_name, '' '', p.last_name) AS `Patient` FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN patient p ON p.id = a.patient_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE a.is_deleted = false AND a.appointment_date >= CURDATE() AND a.status = ''SCHEDULED'' ORDER BY a.appointment_date, a.start_time LIMIT 100', 'Appointments', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (13, 'how many appointments each doctor completed and cancelled', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, SUM(a.status = ''COMPLETED'') AS `Completed`, SUM(a.status = ''CANCELLED'') AS `Cancelled`, COUNT(*) AS `Total` FROM doctors d JOIN appointments a ON a.doctor_id = d.id AND a.is_deleted = false JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false GROUP BY d.id, d.first_name, d.last_name, s.name ORDER BY `Total` DESC LIMIT 30', 'Appointments', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (14, 'which days are busiest and how many appointments were booked', 'SELECT a.appointment_date AS `Date`, DAYNAME(a.appointment_date) AS `Day`, COUNT(*) AS `Appointments`, COUNT(DISTINCT a.doctor_id) AS `Doctors Booked` FROM appointments a WHERE a.is_deleted = false GROUP BY a.appointment_date ORDER BY `Appointments` DESC LIMIT 20', 'Appointments', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (15, 'show long appointments over 30 minutes with doctor and patient', 'SELECT a.appointment_date AS `Date`, a.start_time AS `Start`, a.duration_minutes AS `Minutes`, CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, CONCAT(p.first_name, '' '', p.last_name) AS `Patient`, a.status AS `Status` FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN patient p ON p.id = a.patient_id WHERE a.is_deleted = false AND a.duration_minutes > 30 ORDER BY a.duration_minutes DESC, a.appointment_date DESC LIMIT 100', 'Appointments', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (16, 'cancellation rate for each specialty', 'SELECT s.name AS `Specialty`, COUNT(*) AS `Total`, SUM(a.status = ''CANCELLED'') AS `Cancelled`, ROUND(100 * SUM(a.status = ''CANCELLED'') / COUNT(*), 1) AS `Cancelled Percent` FROM appointments a JOIN doctor_specialties_map m ON m.doctor_id = a.doctor_id JOIN specialties s ON s.id = m.specialty_id WHERE a.is_deleted = false AND s.is_deleted = false GROUP BY s.id, s.name ORDER BY `Cancelled Percent` DESC', 'Appointments', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (17, 'total revenue by specialty from completed appointments', 'SELECT s.name AS `Specialty`, COUNT(a.id) AS `Completed Visits`, SUM(d.consultation_fee) AS `Revenue`, ROUND(AVG(d.consultation_fee), 0) AS `Average Fee` FROM appointments a JOIN doctors d ON d.id = a.doctor_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE a.is_deleted = false AND a.status = ''COMPLETED'' AND s.is_deleted = false GROUP BY s.id, s.name ORDER BY `Revenue` DESC', 'Revenue', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (18, 'which doctors earned the most from completed appointments', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, d.consultation_fee AS `Fee`, COUNT(a.id) AS `Completed`, SUM(d.consultation_fee) AS `Revenue` FROM doctors d JOIN appointments a ON a.doctor_id = d.id AND a.is_deleted = false AND a.status = ''COMPLETED'' JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE d.is_deleted = false GROUP BY d.id, d.first_name, d.last_name, s.name, d.consultation_fee ORDER BY `Revenue` DESC LIMIT 25', 'Revenue', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (19, 'monthly revenue from completed appointments', 'SELECT DATE_FORMAT(a.appointment_date, ''%Y-%m'') AS `Month`, COUNT(a.id) AS `Completed Visits`, SUM(d.consultation_fee) AS `Revenue` FROM appointments a JOIN doctors d ON d.id = a.doctor_id WHERE a.is_deleted = false AND a.status = ''COMPLETED'' GROUP BY DATE_FORMAT(a.appointment_date, ''%Y-%m'') ORDER BY `Month`', 'Revenue', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (20, 'show each doctor weekly schedule with timing and break', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, av.day_of_week AS `Day`, av.start_time AS `Starts`, av.end_time AS `Ends`, av.break_start_time AS `Break From`, av.break_end_time AS `Break To` FROM doctor_availabilities av JOIN doctors d ON d.id = av.doctor_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE av.is_deleted = false AND d.is_deleted = false ORDER BY d.first_name, av.day_of_week LIMIT 200', 'Schedule', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (21, 'which doctors are working today with their specialty and timing', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, av.start_time AS `Starts`, av.end_time AS `Ends`, d.consultation_fee AS `Fee` FROM doctor_availabilities av JOIN doctors d ON d.id = av.doctor_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE av.is_deleted = false AND d.is_deleted = false AND av.day_of_week = UPPER(DAYNAME(CURDATE())) ORDER BY av.start_time, d.first_name', 'Schedule', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (22, 'weekly working hours for each doctor with specialty', 'SELECT CONCAT(d.first_name, '' '', d.last_name) AS `Doctor`, s.name AS `Specialty`, COUNT(*) AS `Working Days`, ROUND(SUM(TIMESTAMPDIFF(MINUTE, av.start_time, av.end_time) - COALESCE(TIMESTAMPDIFF(MINUTE, av.break_start_time, av.break_end_time), 0)) / 60, 1) AS `Hours` FROM doctor_availabilities av JOIN doctors d ON d.id = av.doctor_id JOIN doctor_specialties_map m ON m.doctor_id = d.id JOIN specialties s ON s.id = m.specialty_id WHERE av.is_deleted = false AND d.is_deleted = false GROUP BY d.id, d.first_name, d.last_name, s.name ORDER BY `Hours` DESC LIMIT 50', 'Schedule', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (23, 'how many doctors are available on each day of the week', 'SELECT av.day_of_week AS `Day`, COUNT(DISTINCT av.doctor_id) AS `Doctors Available`, MIN(av.start_time) AS `Earliest Start`, MAX(av.end_time) AS `Latest End` FROM doctor_availabilities av JOIN doctors d ON d.id = av.doctor_id WHERE av.is_deleted = false AND d.is_deleted = false GROUP BY av.day_of_week ORDER BY `Doctors Available` DESC', 'Schedule', 1, '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0);
