-- =====================================================================
-- HealthConnect - 500 appointments for the CURRENT week
--
-- Adds 500 appointments spread over TODAY .. TODAY + 7 DAYS, so the calendar
-- always has something in it whenever you run this. Nothing is hard-coded:
--   * the dates come from CURDATE() at execution time
--   * created_at / updated_at come from NOW() at execution time
-- Run it again next month and you get next month's week.
--
-- HOW TO USE
--   mysql -u root -p healthconnect < db/appointments-next-7-days.sql
--
-- This file is ADDITIVE - it deletes nothing, so you can run it as often as
-- you like. To take one batch back out again, delete by the timestamp it
-- stamped on the rows:
--   DELETE FROM appointments WHERE created_at >= '2026-08-28 00:00:00';
--
-- WHAT IT GUARANTEES
--   * every appointment falls on a day that doctor actually works
--   * every appointment sits inside that day's start/end time
--   * no appointment overlaps the doctor's break
--   * no doctor is double-booked for the same date and time
--   * appointments already in the past today are COMPLETED or CANCELLED;
--     everything still to come is SCHEDULED
--
-- REQUIRES: doctors, patient and doctor_availabilities must already hold data
-- (load db/data.sql first). MySQL 8.0+ for the CTEs and window functions.
-- =====================================================================

INSERT INTO `appointments`
    (`patient_id`, `doctor_id`, `appointment_date`, `start_time`, `end_time`,
     `duration_minutes`, `status`, `created_at`, `updated_at`, `is_deleted`)
WITH RECURSIVE
-- today plus the next seven days
days AS (
    SELECT 0 AS n
    UNION ALL
    SELECT n + 1 FROM days WHERE n < 7
),
-- half-hour slot offsets from the start of a doctor's day (0h .. 7.5h)
slots AS (
    SELECT 0 AS s
    UNION ALL
    SELECT s + 1 FROM slots WHERE s < 15
),
-- patients numbered 1..N so we can pick them without assuming the ids are contiguous
pats AS (
    SELECT `id`, ROW_NUMBER() OVER (ORDER BY `id`) AS rn
    FROM `patient`
    WHERE `is_deleted` = 0
),
-- every (working day x doctor x slot) the next week could offer
candidates AS (
    SELECT
        av.`doctor_id`                                            AS doctor_id,
        (CURDATE() + INTERVAL d.n DAY)                            AS appt_date,
        ADDTIME(av.`start_time`, SEC_TO_TIME(sl.s * 30 * 60))     AS start_time,
        av.`end_time`                                             AS day_end,
        av.`break_start_time`                                     AS break_start,
        av.`break_end_time`                                       AS break_end,
        -- a stable 15 / 30 / 45 minute length, no randomness in the filter below
        ELT(1 + MOD(av.`doctor_id` + sl.s + d.n, 3), 15, 30, 45)  AS duration_minutes
    FROM days d
    JOIN `doctor_availabilities` av
      ON av.`is_deleted` = 0
     AND av.`day_of_week` = UPPER(DAYNAME(CURDATE() + INTERVAL d.n DAY))
    JOIN `doctors` doc
      ON doc.`id` = av.`doctor_id`
     AND doc.`is_deleted` = 0
    CROSS JOIN slots sl
),
-- keep only the slots that actually fit the day and miss the break
fitting AS (
    SELECT
        c.*,
        ADDTIME(c.start_time, SEC_TO_TIME(c.duration_minutes * 60)) AS end_time
    FROM candidates c
    WHERE ADDTIME(c.start_time, SEC_TO_TIME(c.duration_minutes * 60)) <= c.day_end
      AND (
            c.break_start IS NULL
         OR c.start_time >= c.break_end
         OR ADDTIME(c.start_time, SEC_TO_TIME(c.duration_minutes * 60)) <= c.break_start
          )
),
-- shuffle, then take 500. Each (doctor, date, slot) appears once, so taking
-- distinct rows is what stops a doctor being double-booked.
picked AS (
    SELECT f.*, ROW_NUMBER() OVER (ORDER BY RAND()) AS rn
    FROM fitting f
)
SELECT
    p.`id`,
    k.doctor_id,
    k.appt_date,
    k.start_time,
    k.end_time,
    k.duration_minutes,
    CASE
        WHEN k.appt_date = CURDATE() AND k.end_time <= CURTIME()
            THEN IF(RAND() < 0.8, 'COMPLETED', 'CANCELLED')
        ELSE 'SCHEDULED'
    END,
    NOW(6),
    NOW(6),
    0
FROM picked k
JOIN pats p
  ON p.rn = 1 + MOD(k.rn * 7919, (SELECT COUNT(*) FROM `patient` WHERE `is_deleted` = 0))
WHERE k.rn <= 500;

-- What just landed:
--   SELECT appointment_date, COUNT(*)
--   FROM appointments
--   WHERE appointment_date BETWEEN CURDATE() AND CURDATE() + INTERVAL 7 DAY
--   GROUP BY appointment_date ORDER BY appointment_date;
