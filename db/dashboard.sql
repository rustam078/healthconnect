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
