-- =====================================================================
-- HealthConnect seed data
--
-- Generated, deterministic sample data covering 2020-2026:
--   20 specialties, 200 doctors, 5,000 patients, ~1,150 availability rows,
--   10,000 appointments, 6 AI knowledge rows, 7 AI examples,
--   20 widgets, 5 boards, 2 app settings.
--
-- HOW TO USE
--   mysql -u root -p healthconnect < db/data.sql
--
-- The schema must already exist. This file only inserts DATA - it creates no
-- tables. Let Hibernate build the schema first (start the app once, or run a
-- seed IT with ddl-auto=update), then load this.
--
-- It DELETES the contents of every table it seeds, so loading it twice is safe
-- and always produces the same database.
--
-- THE API KEY IS A PLACEHOLDER. app_setting seeds nim.api-key with the literal
--   'paste api key here'
-- so the row exists ready to edit - no real credential is committed. Replace it
-- after loading, or the AI answers with a 502 from the provider:
--   curl -X PUT http://localhost:8080/api/v1/settings/3 -H "Content-Type: application/json" -d '{"name":"nim.api-key","value":"nvapi-...","secret":true}'
-- =====================================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";

-- Child tables first, then parents.
DELETE FROM `widget_execution_log`;
ALTER TABLE `widget_execution_log` AUTO_INCREMENT = 1;
DELETE FROM `appointments`;
ALTER TABLE `appointments` AUTO_INCREMENT = 1;
DELETE FROM `doctor_availabilities`;
ALTER TABLE `doctor_availabilities` AUTO_INCREMENT = 1;
DELETE FROM `doctor_specialties_map`;
ALTER TABLE `doctor_specialties_map` AUTO_INCREMENT = 1;
DELETE FROM `board`;
ALTER TABLE `board` AUTO_INCREMENT = 1;
DELETE FROM `widget`;
ALTER TABLE `widget` AUTO_INCREMENT = 1;
DELETE FROM `app_setting`;
ALTER TABLE `app_setting` AUTO_INCREMENT = 1;
DELETE FROM `ai_prompt_example`;
ALTER TABLE `ai_prompt_example` AUTO_INCREMENT = 1;
DELETE FROM `ai_knowledge`;
ALTER TABLE `ai_knowledge` AUTO_INCREMENT = 1;
DELETE FROM `patient`;
ALTER TABLE `patient` AUTO_INCREMENT = 1;
DELETE FROM `doctors`;
ALTER TABLE `doctors` AUTO_INCREMENT = 1;
DELETE FROM `specialties`;
ALTER TABLE `specialties` AUTO_INCREMENT = 1;

-- ---------- specialties (20) ----------
INSERT INTO `specialties` (`id`, `name`, `description`, `created_at`, `updated_at`, `is_deleted`) VALUES
  (1, 'Cardiology', 'Diagnosis and treatment of heart and cardiovascular diseases', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (2, 'Neurology', 'Disorders of the brain, spinal cord and nervous system', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (3, 'Orthopedics', 'Bones, joints, ligaments and musculoskeletal injuries', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (4, 'Pediatrics', 'Medical care of infants, children and adolescents', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (5, 'Dermatology', 'Conditions of the skin, hair and nails', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (6, 'Gastroenterology', 'Digestive system and related organs', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (7, 'Oncology', 'Diagnosis and treatment of cancer', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (8, 'Ophthalmology', 'Eye and vision care', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (9, 'ENT', 'Ear, nose and throat conditions', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (10, 'Psychiatry', 'Mental health assessment and treatment', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (11, 'Urology', 'Urinary tract and male reproductive system', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (12, 'Nephrology', 'Kidney function and kidney disease', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (13, 'Endocrinology', 'Hormones, diabetes and metabolic disorders', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (14, 'Pulmonology', 'Lungs and the respiratory system', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (15, 'Rheumatology', 'Arthritis and autoimmune joint conditions', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (16, 'General Medicine', 'Broad diagnosis and treatment of adult illness', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (17, 'General Surgery', 'Common surgical procedures', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (18, 'Gynecology', 'Women\'s reproductive health', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (19, 'Anesthesiology', 'Anaesthesia and perioperative care', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0),
  (20, 'Radiology', 'Medical imaging and image-guided diagnosis', '2026-08-01 09:00:00.000000', '2026-08-01 09:00:00.000000', 0);

-- ---------- add 10 doctors with test accout password admin123 () ----------
SET FOREIGN_KEY_CHECKS = 0;

-- Clean slate for the seeded tables (keeps app_setting / PDF template untouched).
DELETE FROM referral;
DELETE FROM ordered_test;
DELETE FROM recommended_test;
DELETE FROM prescription_medicines;
DELETE FROM consultations;
DELETE FROM patient_report;
DELETE FROM appointments;
DELETE FROM doctor_specialties_map;
DELETE FROM doctor_availabilities;
DELETE FROM role_permission;
DELETE FROM app_user;
DELETE FROM lab_test;
DELETE FROM doctors;
DELETE FROM patient;
DELETE FROM specialties;
DELETE FROM role;

-- ---------- Roles ----------
INSERT INTO role (id, name, description, is_system, data_scope, is_deleted, created_at, updated_at) VALUES
 (1, 'ADMIN', 'Full access', true, 'ALL', false, NOW(), NOW()),
 (2, 'DOCTOR', 'Doctor portal', false, 'DEPARTMENT', false, NOW(), NOW()),
 (3, 'CUSTOMER', 'Patient portal', false, 'SELF', false, NOW(), NOW());

-- ADMIN: every module x every action
INSERT INTO role_permission (role_id, module, action)
SELECT 1, m.module, a.action FROM
 (SELECT 'DASHBOARD' module UNION SELECT 'PATIENTS' UNION SELECT 'DOCTORS' UNION SELECT 'SPECIALTIES'
  UNION SELECT 'APPOINTMENTS' UNION SELECT 'SETTINGS' UNION SELECT 'WIDGETS' UNION SELECT 'REPORTS'
  UNION SELECT 'ROLES' UNION SELECT 'USERS' UNION SELECT 'COMMUNITIES') m
 CROSS JOIN (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;

-- DOCTOR / CUSTOMER: full use of their own portal
INSERT INTO role_permission (role_id, module, action)
SELECT 2, 'DOCTOR_PORTAL', a.action FROM
 (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;
INSERT INTO role_permission (role_id, module, action)
SELECT 3, 'CUSTOMER_PORTAL', a.action FROM
 (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;

-- Community chat: doctors and patients may use it (view/join/send); admin already has all via the union above.
INSERT INTO role_permission (role_id, module, action) VALUES (2, 'COMMUNITIES', 'VIEW'), (3, 'COMMUNITIES', 'VIEW');

-- ---------- Specialties ----------
INSERT INTO specialties (id, name, description, is_deleted, created_at, updated_at) VALUES
 (1, 'Cardiology', 'Heart care', false, NOW(), NOW()),
 (2, 'Dermatology', 'Skin care', false, NOW(), NOW()),
 (3, 'Pediatrics', 'Child care', false, NOW(), NOW()),
 (4, 'Orthopedics', 'Bones and joints', false, NOW(), NOW()),
 (5, 'General Medicine', 'General physician', false, NOW(), NOW()),
 (6, 'ENT', 'Ear, nose and throat', false, NOW(), NOW());

-- ---------- Doctors ----------
INSERT INTO doctors (id, doctor_code, first_name, last_name, email, phone, gender, date_of_birth, qualification, experience_years, consultation_fee, is_deleted, created_at, updated_at) VALUES
 (1, 'DOC-001', 'Rahul', 'Sharma', 'rahul.sharma@hc.test', '9876500001', 'MALE',   '1980-04-12', 'MBBS, MD (Cardiology)',  15, 800.00, false, NOW(), NOW()),
 (2, 'DOC-002', 'Priya', 'Verma', 'priya.verma@hc.test', '9876500002', 'FEMALE', '1985-09-20', 'MBBS, MD (Dermatology)', 10, 600.00, false, NOW(), NOW()),
 (3, 'DOC-003', 'Amit',  'Nair',  'amit.nair@hc.test',   '9876500003', 'MALE',   '1982-01-05', 'MBBS, DCH (Pediatrics)', 12, 650.00, false, NOW(), NOW()),
 (4, 'DOC-004', 'Sneha', 'Rao',   'sneha.rao@hc.test',   '9876500004', 'FEMALE', '1987-07-30', 'MBBS, MS (Orthopedics)', 9,  700.00, false, NOW(), NOW()),
 (5, 'DOC-005', 'Vikram','Singh', 'vikram.singh@hc.test','9876500005', 'MALE',   '1979-11-15', 'MBBS, MD (Medicine)',    18, 550.00, false, NOW(), NOW());

INSERT INTO doctor_specialties_map (id, doctor_id, specialty_id) VALUES
 (1, 1, 1), (2, 2, 2), (3, 3, 3), (4, 4, 4), (5, 5, 5);

-- Mon-Fri 09:00-17:00 with a 13:00-14:00 break, for every doctor
INSERT INTO doctor_availabilities (doctor_id, day_of_week, start_time, end_time, break_start_time, break_end_time, is_deleted, created_at, updated_at)
SELECT d.id, dow.day, '09:00:00', '17:00:00', '13:00:00', '14:00:00', false, NOW(), NOW()
FROM (SELECT 1 id UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5) d
CROSS JOIN (SELECT 'MONDAY' day UNION SELECT 'TUESDAY' UNION SELECT 'WEDNESDAY' UNION SELECT 'THURSDAY' UNION SELECT 'FRIDAY') dow;

-- ---------- Patients ----------
INSERT INTO patient (id, patient_code, first_name, last_name, date_of_birth, gender, phone, email, address, blood_group, is_deleted, created_at, updated_at) VALUES
 (1, 'PAT-001', 'Aarav', 'Gupta',  '1995-03-10', 'MALE',   '9000000001', 'aarav.gupta@hc.test',  'Pune',      'O_POSITIVE',  false, NOW(), NOW()),
 (2, 'PAT-002', 'Diya',  'Patel',  '1998-06-22', 'FEMALE', '9000000002', 'diya.patel@hc.test',   'Mumbai',    'A_POSITIVE',  false, NOW(), NOW()),
 (3, 'PAT-003', 'Kabir', 'Mehta',  '1990-12-01', 'MALE',   '9000000003', 'kabir.mehta@hc.test',  'Delhi',     'B_POSITIVE',  false, NOW(), NOW()),
 (4, 'PAT-004', 'Anaya', 'Reddy',  '2000-08-18', 'FEMALE', '9000000004', 'anaya.reddy@hc.test',  'Hyderabad', 'AB_POSITIVE', false, NOW(), NOW()),
 (5, 'PAT-005', 'Arjun', 'Kumar',  '1993-01-27', 'MALE',   '9000000005', 'arjun.kumar@hc.test',  'Chennai',   'O_NEGATIVE',  false, NOW(), NOW());

-- ---------- Login accounts (all password = admin123) ----------
INSERT INTO app_user (id, username, password_hash, enabled, role_id, doctor_id, patient_id, is_deleted, created_at, updated_at) VALUES
 (1,  'admin',    '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 1, NULL, NULL, false, NOW(), NOW()),
 (2,  'doctor1',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 2, 1,    NULL, false, NOW(), NOW()),
 (3,  'doctor2',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 2, 2,    NULL, false, NOW(), NOW()),
 (4,  'doctor3',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 2, 3,    NULL, false, NOW(), NOW()),
 (5,  'doctor4',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 2, 4,    NULL, false, NOW(), NOW()),
 (6,  'doctor5',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 2, 5,    NULL, false, NOW(), NOW()),
 (7,  'patient1', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 3, NULL, 1,    false, NOW(), NOW()),
 (8,  'patient2', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 3, NULL, 2,    false, NOW(), NOW()),
 (9,  'patient3', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 3, NULL, 3,    false, NOW(), NOW()),
 (10, 'patient4', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 3, NULL, 4,    false, NOW(), NOW()),
 (11, 'patient5', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', true, 3, NULL, 5,    false, NOW(), NOW());

-- ---------- Lab test catalogue ----------
INSERT INTO lab_test (name, category, active, is_deleted, created_at, updated_at) VALUES
 ('Complete Blood Count (CBC)', 'LABORATORY', true, false, NOW(), NOW()),
 ('Lipid Profile',              'LABORATORY', true, false, NOW(), NOW()),
 ('Liver Function Test (LFT)',  'LABORATORY', true, false, NOW(), NOW()),
 ('Kidney Function Test (KFT)', 'LABORATORY', true, false, NOW(), NOW()),
 ('Blood Sugar Fasting',        'LABORATORY', true, false, NOW(), NOW()),
 ('HbA1c',                      'LABORATORY', true, false, NOW(), NOW()),
 ('Thyroid Profile (TSH/T3/T4)','LABORATORY', true, false, NOW(), NOW()),
 ('Urine Routine',              'LABORATORY', true, false, NOW(), NOW()),
 ('Vitamin D',                  'LABORATORY', true, false, NOW(), NOW()),
 ('Vitamin B12',                'LABORATORY', true, false, NOW(), NOW()),
 ('C-Reactive Protein (CRP)',   'LABORATORY', true, false, NOW(), NOW()),
 ('Dengue NS1',                 'LABORATORY', true, false, NOW(), NOW()),
 ('Chest X-Ray',                'RADIOLOGY',  true, false, NOW(), NOW()),
 ('Ultrasound Abdomen',         'RADIOLOGY',  true, false, NOW(), NOW()),
 ('MRI Brain',                  'RADIOLOGY',  true, false, NOW(), NOW()),
 ('CT Scan Chest',              'RADIOLOGY',  true, false, NOW(), NOW()),
 ('ECG',                        'RADIOLOGY',  true, false, NOW(), NOW()),
 ('2D Echocardiography',        'RADIOLOGY',  true, false, NOW(), NOW());

SET FOREIGN_KEY_CHECKS = 1;

CREATE INDEX idx_ordered_test_appointment
    ON ordered_test(appointment_id, id);

CREATE INDEX idx_ordered_test_status_appointment
    ON ordered_test(status, appointment_id);

CREATE INDEX idx_patient_report_appointment
    ON patient_report(appointment_id);

CREATE INDEX idx_appointment_patient
    ON appointments(patient_id);

-- ---------- doctor_specialties_map (1-2 specialties per doctor) ----------


-- ---------- add 10 patient with test accouunt password admin123 () ----------

-- ---------- doctor_availabilities ----------
-- Every doctor has at least one weekly holiday, and some have two. A working day
-- spans AT MOST 10 hours including its break; short days are 5 hours with no break.



-- Sanity check after loading:
--   SELECT 'specialties', COUNT(*) FROM specialties
--   UNION ALL SELECT 'doctors', COUNT(*) FROM doctors
--   UNION ALL SELECT 'patient', COUNT(*) FROM patient
--   UNION ALL SELECT 'doctor_availabilities', COUNT(*) FROM doctor_availabilities
--   UNION ALL SELECT 'appointments', COUNT(*) FROM appointments;
--
-- The filter catalogue, the widgets and the boards are in db/dashboard.sql - load that
-- next. They are kept apart because dashboards get rewritten while this data stays put.
