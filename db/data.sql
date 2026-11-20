-- =====================================================================
-- HealthConnect — seed data (realistic demo dataset)
--
-- WHAT THIS IS
--   A complete, believable starter dataset: roles & permissions, 10 doctors,
--   12 patients, login accounts, specialties, weekly availability, the lab-test
--   catalogue, and ~20 appointments — past ones carry a consultation, a
--   prescription, ordered tests and reports; upcoming ones are scheduled.
--
-- HOW TO USE (manual load — nothing here runs automatically)
--   1. Start the backend once so Hibernate (ddl-auto=update) creates every table.
--   2. Then load this file:   mysql -u root -p < db/data.sql
--   3. Load db/dashboard.sql, db/appsetting.sql and db/prescription.sql after this.
--
--   This file picks its own database (CREATE DATABASE IF NOT EXISTS below) and
--   only inserts DATA — it creates no tables. It wipes the tables it owns first,
--   so loading it again resets to exactly this dataset.
--
-- LOGIN  every account's password is  admin123
--   admin                 (full access)
--   doctor1 .. doctor10    (doctor portal, linked to doctors 1..10)
--   patient1 .. patient12  (patient portal, linked to patients 1..12)
--
-- NOTE  The app also seeds roles/admin/lab-tests on first boot (Java seeders).
--   This file overrides those tables with the fixed dataset above, so the
--   passwords become admin123. app_setting (settings + PDF template) is NOT
--   touched here — those live in db/appsetting.sql and db/prescription.sql.
--
-- NOTE  patient_report rows below are metadata only; the actual files are not on
--   disk, so a report download will 404 until a real file is uploaded from the UI.
-- =====================================================================

CREATE DATABASE IF NOT EXISTS `healthconnect`
  CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
USE `healthconnect`;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;
SET SQL_MODE = 'NO_AUTO_VALUE_ON_ZERO';

-- ---------- clean slate (children first), tables this file owns ----------
DELETE FROM `referral`;
DELETE FROM `ordered_test`;
DELETE FROM `prescription_medicines`;
DELETE FROM `consultations`;
DELETE FROM `patient_report`;
DELETE FROM `appointments`;
DELETE FROM `doctor_availabilities`;
DELETE FROM `doctor_specialties_map`;
DELETE FROM `role_permission`;
DELETE FROM `app_user`;
DELETE FROM `lab_test`;
DELETE FROM `doctors`;
DELETE FROM `patient`;
DELETE FROM `specialties`;
DELETE FROM `role`;

ALTER TABLE `referral`               AUTO_INCREMENT = 1;
ALTER TABLE `ordered_test`           AUTO_INCREMENT = 1;
ALTER TABLE `prescription_medicines` AUTO_INCREMENT = 1;
ALTER TABLE `consultations`          AUTO_INCREMENT = 1;
ALTER TABLE `patient_report`         AUTO_INCREMENT = 1;
ALTER TABLE `appointments`           AUTO_INCREMENT = 1;
ALTER TABLE `doctor_availabilities`  AUTO_INCREMENT = 1;
ALTER TABLE `doctor_specialties_map` AUTO_INCREMENT = 1;
ALTER TABLE `app_user`               AUTO_INCREMENT = 1;
ALTER TABLE `lab_test`               AUTO_INCREMENT = 1;
ALTER TABLE `doctors`                AUTO_INCREMENT = 1;
ALTER TABLE `patient`                AUTO_INCREMENT = 1;
ALTER TABLE `specialties`            AUTO_INCREMENT = 1;
ALTER TABLE `role`                   AUTO_INCREMENT = 1;

-- ---------- roles ----------
INSERT INTO `role` (`id`, `name`, `description`, `is_system`, `data_scope`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1, 'ADMIN',    'Full administrative access',        1, 'ALL',        0, NOW(6), NOW(6)),
  (2, 'DOCTOR',   'Doctor portal (own department)',    0, 'DEPARTMENT', 0, NOW(6), NOW(6)),
  (3, 'CUSTOMER', 'Patient portal (own records)',      0, 'SELF',       0, NOW(6), NOW(6));

-- ADMIN: every module x every action
INSERT INTO `role_permission` (`role_id`, `module`, `action`)
SELECT 1, m.module, a.action FROM
  (SELECT 'DASHBOARD' module UNION SELECT 'PATIENTS' UNION SELECT 'DOCTORS' UNION SELECT 'SPECIALTIES'
   UNION SELECT 'APPOINTMENTS' UNION SELECT 'SETTINGS' UNION SELECT 'WIDGETS' UNION SELECT 'REPORTS'
   UNION SELECT 'ROLES' UNION SELECT 'USERS' UNION SELECT 'COMMUNITIES') m
  CROSS JOIN (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;

-- DOCTOR / CUSTOMER: full use of their own portal
INSERT INTO `role_permission` (`role_id`, `module`, `action`)
SELECT 2, 'DOCTOR_PORTAL', a.action FROM
  (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;
INSERT INTO `role_permission` (`role_id`, `module`, `action`)
SELECT 3, 'CUSTOMER_PORTAL', a.action FROM
  (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;

-- Community chat: doctors and patients may view/join/send (admin already has it above).
INSERT INTO `role_permission` (`role_id`, `module`, `action`) VALUES
  (2, 'COMMUNITIES', 'VIEW'), (3, 'COMMUNITIES', 'VIEW');

-- ---------- specialties ----------
INSERT INTO `specialties` (`id`, `name`, `description`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1,  'Cardiology',       'Heart and cardiovascular care',              0, NOW(6), NOW(6)),
  (2,  'Dermatology',      'Skin, hair and nail conditions',             0, NOW(6), NOW(6)),
  (3,  'Pediatrics',       'Medical care of infants and children',       0, NOW(6), NOW(6)),
  (4,  'Orthopedics',      'Bones, joints and musculoskeletal injuries', 0, NOW(6), NOW(6)),
  (5,  'General Medicine', 'Broad diagnosis and treatment of adults',    0, NOW(6), NOW(6)),
  (6,  'ENT',              'Ear, nose and throat conditions',            0, NOW(6), NOW(6)),
  (7,  'Gynecology',       'Women''s reproductive health',               0, NOW(6), NOW(6)),
  (8,  'Neurology',        'Brain and nervous system disorders',         0, NOW(6), NOW(6)),
  (9,  'Gastroenterology', 'Digestive system and related organs',        0, NOW(6), NOW(6)),
  (10, 'Ophthalmology',    'Eye and vision care',                        0, NOW(6), NOW(6));

-- ---------- doctors ----------
INSERT INTO `doctors` (`id`, `doctor_code`, `first_name`, `last_name`, `email`, `phone`, `gender`, `date_of_birth`, `qualification`, `experience_years`, `consultation_fee`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1,  'DOC-001', 'Rahul',  'Sharma',    'rahul.sharma@healthconnect.in',    '9876500001', 'MALE',   '1980-04-12', 'MBBS, MD (Cardiology)',       15, 800.00, 0, NOW(6), NOW(6)),
  (2,  'DOC-002', 'Priya',  'Verma',     'priya.verma@healthconnect.in',     '9876500002', 'FEMALE', '1985-09-20', 'MBBS, MD (Dermatology)',      10, 600.00, 0, NOW(6), NOW(6)),
  (3,  'DOC-003', 'Amit',   'Nair',      'amit.nair@healthconnect.in',       '9876500003', 'MALE',   '1982-01-05', 'MBBS, DCH (Pediatrics)',      12, 650.00, 0, NOW(6), NOW(6)),
  (4,  'DOC-004', 'Sneha',  'Rao',       'sneha.rao@healthconnect.in',       '9876500004', 'FEMALE', '1987-07-30', 'MBBS, MS (Orthopedics)',       9, 700.00, 0, NOW(6), NOW(6)),
  (5,  'DOC-005', 'Vikram', 'Singh',     'vikram.singh@healthconnect.in',    '9876500005', 'MALE',   '1979-11-15', 'MBBS, MD (General Medicine)', 18, 550.00, 0, NOW(6), NOW(6)),
  (6,  'DOC-006', 'Neha',   'Kulkarni',  'neha.kulkarni@healthconnect.in',   '9876500006', 'FEMALE', '1988-02-25', 'MBBS, MS (ENT)',               8, 600.00, 0, NOW(6), NOW(6)),
  (7,  'DOC-007', 'Ananya', 'Iyer',      'ananya.iyer@healthconnect.in',     '9876500007', 'FEMALE', '1981-06-11', 'MBBS, MD (Gynecology)',       14, 750.00, 0, NOW(6), NOW(6)),
  (8,  'DOC-008', 'Rajesh', 'Menon',     'rajesh.menon@healthconnect.in',    '9876500008', 'MALE',   '1977-10-03', 'MBBS, DM (Neurology)',        16, 900.00, 0, NOW(6), NOW(6)),
  (9,  'DOC-009', 'Kavya',  'Reddy',     'kavya.reddy@healthconnect.in',     '9876500009', 'FEMALE', '1984-03-19', 'MBBS, DM (Gastroenterology)', 11, 700.00, 0, NOW(6), NOW(6)),
  (10, 'DOC-010', 'Arjun',  'Desai',     'arjun.desai@healthconnect.in',     '9876500010', 'MALE',   '1989-12-08', 'MBBS, MS (Ophthalmology)',     7, 650.00, 0, NOW(6), NOW(6));

-- each doctor's specialty; doctors 1 and 5 carry a second one to show multi-specialty
INSERT INTO `doctor_specialties_map` (`id`, `doctor_id`, `specialty_id`) VALUES
  (1, 1, 1), (2, 2, 2), (3, 3, 3), (4, 4, 4), (5, 5, 5),
  (6, 6, 6), (7, 7, 7), (8, 8, 8), (9, 9, 9), (10, 10, 10),
  (11, 1, 5), (12, 5, 1);

-- weekly availability: Mon-Fri 09:00-17:00 with a 13:00-14:00 break for every doctor
INSERT INTO `doctor_availabilities` (`doctor_id`, `day_of_week`, `start_time`, `end_time`, `break_start_time`, `break_end_time`, `is_deleted`, `created_at`, `updated_at`)
SELECT d.id, dow.day, '09:00:00', '17:00:00', '13:00:00', '14:00:00', 0, NOW(6), NOW(6)
FROM (SELECT 1 id UNION SELECT 2 UNION SELECT 3 UNION SELECT 4 UNION SELECT 5
      UNION SELECT 6 UNION SELECT 7 UNION SELECT 8 UNION SELECT 9 UNION SELECT 10) d
CROSS JOIN (SELECT 'MONDAY' day UNION SELECT 'TUESDAY' UNION SELECT 'WEDNESDAY'
            UNION SELECT 'THURSDAY' UNION SELECT 'FRIDAY') dow;

-- a few doctors also run a Saturday morning clinic (09:00-13:00, no break)
INSERT INTO `doctor_availabilities` (`doctor_id`, `day_of_week`, `start_time`, `end_time`, `break_start_time`, `break_end_time`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1, 'SATURDAY', '09:00:00', '13:00:00', NULL, NULL, 0, NOW(6), NOW(6)),
  (3, 'SATURDAY', '09:00:00', '13:00:00', NULL, NULL, 0, NOW(6), NOW(6)),
  (5, 'SATURDAY', '09:00:00', '13:00:00', NULL, NULL, 0, NOW(6), NOW(6));

-- ---------- patients ----------
INSERT INTO `patient` (`id`, `patient_code`, `first_name`, `last_name`, `date_of_birth`, `gender`, `phone`, `email`, `address`, `blood_group`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1,  'PAT-001', 'Aarav',   'Gupta',      '1995-03-10', 'MALE',   '9000000001', 'aarav.gupta@gmail.com',      'Baner, Pune, Maharashtra',          'O_POSITIVE',  0, NOW(6), NOW(6)),
  (2,  'PAT-002', 'Diya',    'Patel',      '1998-06-22', 'FEMALE', '9000000002', 'diya.patel@gmail.com',       'Andheri, Mumbai, Maharashtra',      'A_POSITIVE',  0, NOW(6), NOW(6)),
  (3,  'PAT-003', 'Kabir',   'Mehta',      '1990-12-01', 'MALE',   '9000000003', 'kabir.mehta@gmail.com',      'Rohini, New Delhi',                 'B_POSITIVE',  0, NOW(6), NOW(6)),
  (4,  'PAT-004', 'Anaya',   'Reddy',      '2000-08-18', 'FEMALE', '9000000004', 'anaya.reddy@gmail.com',      'Gachibowli, Hyderabad, Telangana',  'AB_POSITIVE', 0, NOW(6), NOW(6)),
  (5,  'PAT-005', 'Arjun',   'Kumar',      '1993-01-27', 'MALE',   '9000000005', 'arjun.kumar@gmail.com',      'Adyar, Chennai, Tamil Nadu',        'O_NEGATIVE',  0, NOW(6), NOW(6)),
  (6,  'PAT-006', 'Ishaan',  'Joshi',      '1988-05-14', 'MALE',   '9000000006', 'ishaan.joshi@gmail.com',     'Koramangala, Bengaluru, Karnataka', 'A_NEGATIVE',  0, NOW(6), NOW(6)),
  (7,  'PAT-007', 'Saanvi',  'Nair',       '2002-11-09', 'FEMALE', '9000000007', 'saanvi.nair@gmail.com',      'Kakkanad, Kochi, Kerala',           'B_NEGATIVE',  0, NOW(6), NOW(6)),
  (8,  'PAT-008', 'Vivaan',  'Shah',       '1975-07-02', 'MALE',   '9000000008', 'vivaan.shah@gmail.com',      'Satellite, Ahmedabad, Gujarat',     'O_POSITIVE',  0, NOW(6), NOW(6)),
  (9,  'PAT-009', 'Myra',    'Fernandes',  '1996-09-30', 'FEMALE', '9000000009', 'myra.fernandes@gmail.com',   'Panaji, Goa',                       'AB_NEGATIVE', 0, NOW(6), NOW(6)),
  (10, 'PAT-010', 'Reyansh', 'Malhotra',   '1983-02-19', 'MALE',   '9000000010', 'reyansh.malhotra@gmail.com', 'Vaishali Nagar, Jaipur, Rajasthan', 'A_POSITIVE',  0, NOW(6), NOW(6)),
  (11, 'PAT-011', 'Aadhya',  'Pillai',     '2015-04-25', 'FEMALE', '9000000011', 'aadhya.pillai@gmail.com',    'Kowdiar, Trivandrum, Kerala',       'O_POSITIVE',  0, NOW(6), NOW(6)),
  (12, 'PAT-012', 'Kiaan',   'Verma',      '1969-12-12', 'MALE',   '9000000012', 'kiaan.verma@gmail.com',      'Gomti Nagar, Lucknow, UP',          'B_POSITIVE',  0, NOW(6), NOW(6));

-- ---------- login accounts (password for all = admin123) ----------
INSERT INTO `app_user` (`id`, `username`, `password_hash`, `enabled`, `role_id`, `doctor_id`, `patient_id`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1,  'admin',     '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 1, NULL, NULL, 0, NOW(6), NOW(6)),
  (2,  'doctor1',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 1,    NULL, 0, NOW(6), NOW(6)),
  (3,  'doctor2',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 2,    NULL, 0, NOW(6), NOW(6)),
  (4,  'doctor3',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 3,    NULL, 0, NOW(6), NOW(6)),
  (5,  'doctor4',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 4,    NULL, 0, NOW(6), NOW(6)),
  (6,  'doctor5',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 5,    NULL, 0, NOW(6), NOW(6)),
  (7,  'doctor6',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 6,    NULL, 0, NOW(6), NOW(6)),
  (8,  'doctor7',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 7,    NULL, 0, NOW(6), NOW(6)),
  (9,  'doctor8',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 8,    NULL, 0, NOW(6), NOW(6)),
  (10, 'doctor9',   '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 9,    NULL, 0, NOW(6), NOW(6)),
  (11, 'doctor10',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 2, 10,   NULL, 0, NOW(6), NOW(6)),
  (12, 'patient1',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 1,    0, NOW(6), NOW(6)),
  (13, 'patient2',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 2,    0, NOW(6), NOW(6)),
  (14, 'patient3',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 3,    0, NOW(6), NOW(6)),
  (15, 'patient4',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 4,    0, NOW(6), NOW(6)),
  (16, 'patient5',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 5,    0, NOW(6), NOW(6)),
  (17, 'patient6',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 6,    0, NOW(6), NOW(6)),
  (18, 'patient7',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 7,    0, NOW(6), NOW(6)),
  (19, 'patient8',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 8,    0, NOW(6), NOW(6)),
  (20, 'patient9',  '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 9,    0, NOW(6), NOW(6)),
  (21, 'patient10', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 10,   0, NOW(6), NOW(6)),
  (22, 'patient11', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 11,   0, NOW(6), NOW(6)),
  (23, 'patient12', '$2a$10$4RzFAmO8sSAHw0yGKbwPMOmgNV2.ExeBBQAg2VCbaKm.Bb2CLrcUy', 1, 3, NULL, 12,   0, NOW(6), NOW(6));

-- ---------- lab-test catalogue ----------
INSERT INTO `lab_test` (`name`, `category`, `active`, `is_deleted`, `created_at`, `updated_at`) VALUES
  ('Complete Blood Count (CBC)',   'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Lipid Profile',                'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Liver Function Test (LFT)',    'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Kidney Function Test (KFT)',   'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Blood Sugar Fasting',          'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('HbA1c',                        'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Thyroid Profile (TSH/T3/T4)',  'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Urine Routine',                'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Vitamin D',                    'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Vitamin B12',                  'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('C-Reactive Protein (CRP)',     'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Dengue NS1',                   'LABORATORY', 1, 0, NOW(6), NOW(6)),
  ('Chest X-Ray',                  'RADIOLOGY',  1, 0, NOW(6), NOW(6)),
  ('Ultrasound Abdomen',           'RADIOLOGY',  1, 0, NOW(6), NOW(6)),
  ('MRI Brain',                    'RADIOLOGY',  1, 0, NOW(6), NOW(6)),
  ('CT Scan Chest',                'RADIOLOGY',  1, 0, NOW(6), NOW(6)),
  ('ECG',                          'RADIOLOGY',  1, 0, NOW(6), NOW(6)),
  ('2D Echocardiography',          'RADIOLOGY',  1, 0, NOW(6), NOW(6));

-- ---------- appointments ----------
-- 1-8  COMPLETED (each has a consultation + prescription; some have tests/reports)
-- 9-16 SCHEDULED (upcoming)   17-18 CANCELLED   19-20 NO_SHOW
-- follow_up = valid-until date for a follow-up booking (appointment_date + 30 days).
INSERT INTO `appointments` (`id`, `patient_id`, `doctor_id`, `appointment_date`, `start_time`, `end_time`, `duration_minutes`, `status`, `appointment_type`, `follow_up`, `follow_up_appointment_id`, `reason`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1,  1,  1,  '2026-09-15', '10:00:00', '10:30:00', 30, 'COMPLETED', 'NEW',       '2026-10-15', NULL, 'Chest pain and breathlessness on exertion', 0, NOW(6), NOW(6)),
  (2,  3,  5,  '2026-09-22', '11:00:00', '11:30:00', 30, 'COMPLETED', 'NEW',       '2026-10-22', NULL, 'Fever, body ache and mild cough',           0, NOW(6), NOW(6)),
  (3,  2,  2,  '2026-10-02', '09:30:00', '10:00:00', 30, 'COMPLETED', 'NEW',       '2026-11-01', NULL, 'Itchy red rash on both forearms',           0, NOW(6), NOW(6)),
  (4,  11, 3,  '2026-10-05', '10:00:00', '10:30:00', 30, 'COMPLETED', 'NEW',       '2026-11-04', NULL, 'High fever and cold in child',              0, NOW(6), NOW(6)),
  (5,  5,  4,  '2026-10-10', '15:00:00', '15:30:00', 30, 'COMPLETED', 'NEW',       '2026-11-09', NULL, 'Right knee pain and swelling after a fall', 0, NOW(6), NOW(6)),
  (6,  8,  9,  '2026-10-18', '12:00:00', '12:30:00', 30, 'COMPLETED', 'NEW',       '2026-11-17', NULL, 'Acidity and burning stomach pain',          0, NOW(6), NOW(6)),
  (7,  12, 1,  '2026-10-25', '09:00:00', '09:30:00', 30, 'COMPLETED', 'FOLLOW_UP', '2026-11-24', NULL, 'Hypertension review',                       0, NOW(6), NOW(6)),
  (8,  6,  8,  '2026-11-03', '11:30:00', '12:00:00', 30, 'COMPLETED', 'NEW',       '2026-12-03', NULL, 'Frequent headaches and light sensitivity',  0, NOW(6), NOW(6)),
  (9,  1,  1,  '2026-11-22', '10:00:00', '10:30:00', 30, 'SCHEDULED', 'FOLLOW_UP', '2026-12-22', NULL, 'Cardiology follow-up review',               0, NOW(6), NOW(6)),
  (10, 2,  2,  '2026-11-24', '09:30:00', '10:00:00', 30, 'SCHEDULED', 'NEW',       '2026-12-24', NULL, 'Acne and skin consultation',                0, NOW(6), NOW(6)),
  (11, 7,  7,  '2026-11-25', '14:00:00', '14:30:00', 30, 'SCHEDULED', 'NEW',       '2026-12-25', NULL, 'Routine gynecology checkup',                0, NOW(6), NOW(6)),
  (12, 9,  10, '2026-11-26', '11:00:00', '11:30:00', 30, 'SCHEDULED', 'NEW',       '2026-12-26', NULL, 'Blurred vision and eye strain',             0, NOW(6), NOW(6)),
  (13, 10, 5,  '2026-11-27', '10:30:00', '11:00:00', 30, 'SCHEDULED', 'NEW',       '2026-12-27', NULL, 'General health checkup',                    0, NOW(6), NOW(6)),
  (14, 4,  6,  '2026-11-28', '15:30:00', '16:00:00', 30, 'SCHEDULED', 'NEW',       '2026-12-28', NULL, 'Sore throat and ear pain',                  0, NOW(6), NOW(6)),
  (15, 3,  5,  '2026-12-01', '09:00:00', '09:30:00', 30, 'SCHEDULED', 'FOLLOW_UP', '2026-12-31', NULL, 'Fever follow-up',                           0, NOW(6), NOW(6)),
  (16, 11, 3,  '2026-12-02', '10:00:00', '10:30:00', 30, 'SCHEDULED', 'NEW',       '2027-01-01', NULL, 'Child routine vaccination',                 0, NOW(6), NOW(6)),
  (17, 5,  4,  '2026-11-10', '16:00:00', '16:30:00', 30, 'CANCELLED', 'FOLLOW_UP', '2026-12-10', NULL, 'Knee follow-up (cancelled by patient)',     0, NOW(6), NOW(6)),
  (18, 8,  9,  '2026-11-12', '12:30:00', '13:00:00', 30, 'CANCELLED', 'NEW',       '2026-12-12', NULL, 'Stomach pain (cancelled)',                  0, NOW(6), NOW(6)),
  (19, 6,  8,  '2026-11-05', '11:00:00', '11:30:00', 30, 'NO_SHOW',   'FOLLOW_UP', '2026-12-05', NULL, 'Headache review (patient did not attend)',  0, NOW(6), NOW(6)),
  (20, 12, 1,  '2026-11-08', '09:30:00', '10:00:00', 30, 'NO_SHOW',   'FOLLOW_UP', '2026-12-08', NULL, 'BP review (patient did not attend)',        0, NOW(6), NOW(6));

-- ---------- consultations (for the completed appointments) ----------
INSERT INTO `consultations` (`id`, `appointment_id`, `chief_complaint`, `severity`, `diagnosis`, `notes`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1, 1, 'Chest pain and breathlessness on exertion for two weeks', 'MODERATE', 'Stable angina', 'Advised low-salt diet, regular walking and stress reduction. Review in one month.', 0, NOW(6), NOW(6)),
  (2, 2, 'Fever up to 101F, body ache and mild cough for 3 days',    'MILD',     'Viral fever',   'Rest and plenty of fluids. Return if fever persists beyond 3 days.',                 0, NOW(6), NOW(6)),
  (3, 3, 'Itchy red rash on both forearms after new detergent',      'MILD',     'Contact dermatitis', 'Avoid the suspected allergen. Apply topical steroid twice daily.',              0, NOW(6), NOW(6)),
  (4, 4, 'High-grade fever with running nose and cold',              'MODERATE', 'Upper respiratory tract infection', 'Syrups as prescribed, steam inhalation, plenty of fluids.',          0, NOW(6), NOW(6)),
  (5, 5, 'Right knee pain and swelling after a fall while playing',  'MODERATE', 'Knee ligament sprain', 'Rest, ice packs, knee support. Start physiotherapy after swelling settles.',    0, NOW(6), NOW(6)),
  (6, 6, 'Burning sensation in the stomach and acidity after meals', 'MILD',     'Gastritis with acid reflux (GERD)', 'Avoid spicy and oily food. Take antacid before meals.',             0, NOW(6), NOW(6)),
  (7, 7, 'Routine hypertension review, occasional dizziness',        'MODERATE', 'Essential hypertension - controlled', 'Continue medication. Monitor blood pressure daily and record it.', 0, NOW(6), NOW(6)),
  (8, 8, 'Frequent throbbing headaches with light sensitivity',      'SEVERE',   'Migraine without aura', 'Identify and avoid triggers. Start prophylaxis. Review in two weeks.',          0, NOW(6), NOW(6));

-- ---------- prescription medicines (per consultation) ----------
INSERT INTO `prescription_medicines` (`id`, `consultation_id`, `medicine_name`, `dosage`, `frequency`, `duration`, `instructions`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1,  1, 'Aspirin',      '75mg',  '0-1-0', '30 days', 'After lunch',            0, NOW(6), NOW(6)),
  (2,  1, 'Atorvastatin', '10mg',  '0-0-1', '30 days', 'At bedtime',             0, NOW(6), NOW(6)),
  (3,  1, 'Metoprolol',   '25mg',  '1-0-1', '30 days', 'After food',             0, NOW(6), NOW(6)),
  (4,  2, 'Paracetamol',  '500mg', '1-1-1', '5 days',  'After food, if fever',   0, NOW(6), NOW(6)),
  (5,  2, 'Cetirizine',   '10mg',  '0-0-1', '5 days',  'At bedtime',             0, NOW(6), NOW(6)),
  (6,  3, 'Mometasone cream', '0.1%', '0-0-1', '7 days', 'Thin layer on rash', 0, NOW(6), NOW(6)),
  (7,  3, 'Levocetirizine',   '5mg',  '0-0-1', '7 days', 'At bedtime',         0, NOW(6), NOW(6)),
  (8,  4, 'Paracetamol Syrup', '5ml', '1-1-1', '3 days', 'After food',         0, NOW(6), NOW(6)),
  (9,  4, 'Cetirizine Syrup',  '5ml', '0-0-1', '5 days', 'At bedtime',         0, NOW(6), NOW(6)),
  (10, 5, 'Ibuprofen',    '400mg', '1-0-1', '5 days',  'After food',             0, NOW(6), NOW(6)),
  (11, 5, 'Calcium + Vitamin D3', '500mg', '0-1-0', '30 days', 'After lunch', 0, NOW(6), NOW(6)),
  (12, 6, 'Pantoprazole', '40mg',  '1-0-0', '14 days', 'Before breakfast',       0, NOW(6), NOW(6)),
  (13, 6, 'Antacid Syrup', '10ml', '0-0-1', '7 days',  'At bedtime',             0, NOW(6), NOW(6)),
  (14, 7, 'Amlodipine',   '5mg',   '1-0-0', '30 days', 'In the morning',         0, NOW(6), NOW(6)),
  (15, 7, 'Telmisartan',  '40mg',  '1-0-0', '30 days', 'In the morning',         0, NOW(6), NOW(6)),
  (16, 8, 'Propranolol',  '40mg',  '1-0-1', '30 days', 'After food',             0, NOW(6), NOW(6)),
  (17, 8, 'Sumatriptan',  '50mg',  'SOS',   'As needed', 'At onset of a migraine', 0, NOW(6), NOW(6));

-- ---------- ordered tests (status AVAILABLE rows link to a patient_report) ----------
INSERT INTO `ordered_test` (`id`, `appointment_id`, `test_name`, `category`, `status`, `report_id`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1, 1, 'ECG',               'RADIOLOGY',  'AVAILABLE', 1,    0, NOW(6), NOW(6)),
  (2, 1, 'Lipid Profile',     'LABORATORY', 'AVAILABLE', 2,    0, NOW(6), NOW(6)),
  (3, 2, 'Complete Blood Count (CBC)', 'LABORATORY', 'ORDERED', NULL, 0, NOW(6), NOW(6)),
  (4, 5, 'X-Ray Knee (AP/Lateral)',    'RADIOLOGY',  'AVAILABLE', 3, 0, NOW(6), NOW(6)),
  (5, 6, 'Ultrasound Abdomen', 'RADIOLOGY', 'ORDERED', NULL, 0, NOW(6), NOW(6)),
  (6, 8, 'MRI Brain',          'RADIOLOGY', 'ORDERED', NULL, 0, NOW(6), NOW(6));

-- ---------- patient reports (metadata only — actual files are not on disk) ----------
INSERT INTO `patient_report` (`id`, `patient_id`, `appointment_id`, `title`, `original_file_name`, `stored_file_name`, `content_type`, `file_size_bytes`, `uploaded_by_user_id`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1, 1, 1, 'ECG Report',            'ecg-report.pdf',       'seed-report-0001.pdf', 'application/pdf', 185000, 1, 0, NOW(6), NOW(6)),
  (2, 1, 1, 'Lipid Profile Report',  'lipid-profile.pdf',    'seed-report-0002.pdf', 'application/pdf', 142000, 1, 0, NOW(6), NOW(6)),
  (3, 5, 5, 'Knee X-Ray Report',     'knee-xray.pdf',        'seed-report-0003.pdf', 'application/pdf', 320000, 1, 0, NOW(6), NOW(6));

-- ---------- referrals ----------
INSERT INTO `referral` (`id`, `appointment_id`, `specialty_id`, `from_doctor_id`, `reason`, `status`, `is_deleted`, `created_at`, `updated_at`) VALUES
  (1, 1, 5, 1, 'For diabetes screening and general metabolic evaluation', 'REQUESTED', 0, NOW(6), NOW(6)),
  (2, 8, 5, 8, 'Rule out secondary causes of headache',                   'COMPLETED', 0, NOW(6), NOW(6));

SET FOREIGN_KEY_CHECKS = 1;

-- ---------- sanity check after loading ----------
-- SELECT 'specialties' t, COUNT(*) n FROM specialties
-- UNION ALL SELECT 'doctors',      COUNT(*) FROM doctors
-- UNION ALL SELECT 'patient',      COUNT(*) FROM patient
-- UNION ALL SELECT 'app_user',     COUNT(*) FROM app_user
-- UNION ALL SELECT 'appointments', COUNT(*) FROM appointments
-- UNION ALL SELECT 'consultations',COUNT(*) FROM consultations
-- UNION ALL SELECT 'prescription_medicines', COUNT(*) FROM prescription_medicines
-- UNION ALL SELECT 'ordered_test', COUNT(*) FROM ordered_test
-- UNION ALL SELECT 'patient_report', COUNT(*) FROM patient_report;
