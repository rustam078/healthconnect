-- HealthConnect dev seed: roles, accounts, 5 doctors, 5 patient, specialties, availability,
-- lab tests. No appointments (add those from the UI). Every login password is: admin123
-- Run once (see note in chat). bcrypt hash below = bcrypt('admin123').

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
  UNION SELECT 'ROLES' UNION SELECT 'USERS') m
 CROSS JOIN (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;

-- DOCTOR / CUSTOMER: full use of their own portal
INSERT INTO role_permission (role_id, module, action)
SELECT 2, 'DOCTOR_PORTAL', a.action FROM
 (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;
INSERT INTO role_permission (role_id, module, action)
SELECT 3, 'CUSTOMER_PORTAL', a.action FROM
 (SELECT 'VIEW' action UNION SELECT 'CREATE' UNION SELECT 'UPDATE' UNION SELECT 'DELETE') a;

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
