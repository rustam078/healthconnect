// Regenerates db/data.sql for HealthConnect.
//
// Usage, from the project root:
//   node db/generate-data-sql.js db/data.sql
//
// Then prove it still loads cleanly:
//   ./mvnw test -Dtest=SeedDataIT
// Deterministic: a seeded PRNG, so re-running produces byte-identical output.

const fs = require('fs')
const path = require('path')

// The three JSON dumps in seed-input/ are the AI knowledge, AI examples and widget
// definitions exported from a working database - they are what makes the seeded widgets
// and boards real rather than invented.
const SP = require('path').join(__dirname, 'seed-input')
const OUT = process.argv[2]

// ---------- deterministic PRNG (mulberry32) ----------
let _s = 20260827
function rnd() {
  _s |= 0; _s = (_s + 0x6D2B79F5) | 0
  let t = Math.imul(_s ^ (_s >>> 15), 1 | _s)
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296
}
const int = (a, b) => a + Math.floor(rnd() * (b - a + 1))
const pick = (arr) => arr[Math.floor(rnd() * arr.length)]

// ---------- SQL helpers ----------
function esc(v) {
  if (v === null || v === undefined) return 'NULL'
  return "'" + String(v).replace(/\\/g, '\\\\').replace(/'/g, "\\'") + "'"
}
const num = (v) => (v === null || v === undefined ? 'NULL' : String(v))

function insertBatch(out, table, cols, rows, perStmt = 500) {
  for (let i = 0; i < rows.length; i += perStmt) {
    const chunk = rows.slice(i, i + perStmt)
    out.push(`INSERT INTO \`${table}\` (${cols.map((c) => '`' + c + '`').join(', ')}) VALUES`)
    out.push(chunk.map((r) => '  (' + r.join(', ') + ')').join(',\n') + ';')
    out.push('')
  }
}

const TS = "'2026-08-01 09:00:00.000000'"
const pad = (n, w) => String(n).padStart(w, '0')
const two = (n) => pad(n, 2)
const hhmmss = (mins) => `${two(Math.floor(mins / 60))}:${two(mins % 60)}:00`

// ---------- reference pools ----------
const SPECIALTIES = [
  ['Cardiology', 'Diagnosis and treatment of heart and cardiovascular diseases'],
  ['Neurology', 'Disorders of the brain, spinal cord and nervous system'],
  ['Orthopedics', 'Bones, joints, ligaments and musculoskeletal injuries'],
  ['Pediatrics', 'Medical care of infants, children and adolescents'],
  ['Dermatology', 'Conditions of the skin, hair and nails'],
  ['Gastroenterology', 'Digestive system and related organs'],
  ['Oncology', 'Diagnosis and treatment of cancer'],
  ['Ophthalmology', 'Eye and vision care'],
  ['ENT', 'Ear, nose and throat conditions'],
  ['Psychiatry', 'Mental health assessment and treatment'],
  ['Urology', 'Urinary tract and male reproductive system'],
  ['Nephrology', 'Kidney function and kidney disease'],
  ['Endocrinology', 'Hormones, diabetes and metabolic disorders'],
  ['Pulmonology', 'Lungs and the respiratory system'],
  ['Rheumatology', 'Arthritis and autoimmune joint conditions'],
  ['General Medicine', 'Broad diagnosis and treatment of adult illness'],
  ['General Surgery', 'Common surgical procedures'],
  ['Gynecology', "Women's reproductive health"],
  ['Anesthesiology', 'Anaesthesia and perioperative care'],
  ['Radiology', 'Medical imaging and image-guided diagnosis'],
]

const FIRST_M = ['Rajesh','Amit','Sanjay','Rahul','Vikash','Imran','Arjun','Karan','Rohit','Manish','Deepak','Suresh','Nikhil','Aditya','Faizan','Sameer','Vivek','Anil','Gaurav','Harsh','Yash','Pranav','Tarun','Mohit','Ashok']
const FIRST_F = ['Priya','Sneha','Neha','Anjali','Pooja','Kavita','Sana','Divya','Meera','Ritu','Shreya','Nisha','Aarti','Swati','Fatima','Preeti','Rekha','Sunita','Isha','Payal','Komal','Radha','Tanvi','Alka','Nidhi']
const LAST = ['Kumar','Sharma','Verma','Singh','Patel','Gupta','Mishra','Khan','Reddy','Nair','Iyer','Joshi','Desai','Mehta','Chopra','Malhotra','Bose','Das','Pillai','Rao','Shah','Bhatt','Ahmed','Kulkarni','Sinha','Yadav','Thakur','Ghosh','Menon','Saxena']
const QUALS = ['MBBS, MD','MBBS, MS','MBBS, DM','MBBS, DNB','MBBS, MD, DM','MBBS, MS, MCh','MBBS','MBBS, MD, FRCP']
const BLOOD = ['A_POSITIVE','A_NEGATIVE','B_POSITIVE','B_NEGATIVE','AB_POSITIVE','AB_NEGATIVE','O_POSITIVE','O_NEGATIVE']
const CITIES = ['Mumbai','Pune','Bengaluru','Hyderabad','Chennai','Delhi','Kolkata','Ahmedabad','Jaipur','Lucknow','Indore','Bhopal','Nagpur','Surat','Patna']
const STREETS = ['MG Road','Station Road','Gandhi Nagar','Park Street','Ring Road','Nehru Marg','Civil Lines','Model Town','Lake View','Green Park']
const DAYS = ['MONDAY','TUESDAY','WEDNESDAY','THURSDAY','FRIDAY','SATURDAY','SUNDAY']

// When the appointment was booked. Past appointments: the morning of the day itself.
// Future ones: the seed's reference timestamp, because created_at must never be in the
// future - a row cannot have been created after the moment you are reading it.
const REF_DATE = '2026-08-01'
const bookedAt = (iso) => (iso <= REF_DATE ? esc(`${iso} 08:00:00.000000`) : TS)

// ---------- build ----------
const out = []
out.push('-- =====================================================================')
out.push('-- HealthConnect seed data')
out.push('--')
out.push('-- Generated, deterministic sample data covering 2020-2026:')
out.push('--   20 specialties, 200 doctors, 5,000 patients, ~1,150 availability rows,')
out.push('--   10,000 appointments, 6 AI knowledge rows, 7 AI examples,')
out.push('--   20 widgets, 5 boards, 2 app settings.')
out.push('--')
out.push('-- HOW TO USE')
out.push('--   mysql -u root -p healthconnect < db/data.sql')
out.push('--')
out.push('-- The schema must already exist. This file only inserts DATA - it creates no')
out.push('-- tables. Let Hibernate build the schema first (start the app once, or run a')
out.push('-- seed IT with ddl-auto=update), then load this.')
out.push('--')
out.push('-- It DELETES the contents of every table it seeds, so loading it twice is safe')
out.push('-- and always produces the same database.')
out.push('--')
out.push("-- THE API KEY IS A PLACEHOLDER. app_setting seeds nim.api-key with the literal")
out.push("--   'paste api key here'")
out.push('-- so the row exists ready to edit - no real credential is committed. Replace it')
out.push('-- after loading, or the AI answers with a 502 from the provider:')
out.push(`--   curl -X PUT http://localhost:8080/api/v1/settings/3 -H "Content-Type: application/json" -d '{"name":"nim.api-key","value":"nvapi-...","secret":true}'`)
out.push('-- =====================================================================')
out.push('')
out.push('SET NAMES utf8mb4;')
out.push('SET FOREIGN_KEY_CHECKS = 0;')
out.push('SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";')
out.push('')
out.push('-- Child tables first, then parents.')
for (const t of ['widget_execution_log','appointments','doctor_availabilities','doctor_specialties_map','board','widget','app_setting','ai_prompt_example','ai_knowledge','patient','doctors','specialties']) {
  out.push(`DELETE FROM \`${t}\`;`)
  out.push(`ALTER TABLE \`${t}\` AUTO_INCREMENT = 1;`)
}
out.push('')

// ---------- specialties ----------
out.push('-- ---------- specialties (20) ----------')
insertBatch(out, 'specialties', ['id','name','description','created_at','updated_at','is_deleted'],
  SPECIALTIES.map((s, i) => [num(i + 1), esc(s[0]), esc(s[1]), TS, TS, '0']))

// ---------- doctors ----------
out.push('-- ---------- doctors (200) ----------')
const doctors = []
const doctorRows = []
const usedEmail = new Set()
for (let i = 1; i <= 200; i++) {
  const gender = rnd() < 0.55 ? 'MALE' : 'FEMALE'
  const first = gender === 'MALE' ? pick(FIRST_M) : pick(FIRST_F)
  const last = pick(LAST)
  let email = `${first}.${last}${i}@healthconnect.in`.toLowerCase()
  while (usedEmail.has(email)) email = `${first}.${last}${i}x@healthconnect.in`.toLowerCase()
  usedEmail.add(email)
  const birthYear = int(1960, 1994)
  const exp = Math.max(1, Math.min(35, 2026 - birthYear - int(26, 30)))
  doctors.push({ id: i, first, last, gender })
  doctorRows.push([
    num(i), esc('DOC' + pad(i, 4)), esc(first), esc(last), esc(email),
    esc('9' + String(int(100000000, 999999999))), esc(gender),
    esc(`${birthYear}-${two(int(1, 12))}-${two(int(1, 28))}`),
    esc(pick(QUALS)), num(exp), num(int(6, 40) * 50), TS, TS, '0',
  ])
}
insertBatch(out, 'doctors',
  ['id','doctor_code','first_name','last_name','email','phone','gender','date_of_birth','qualification','experience_years','consultation_fee','created_at','updated_at','is_deleted'],
  doctorRows)

// ---------- doctor_specialties_map ----------
out.push('-- ---------- doctor_specialties_map (1-2 specialties per doctor) ----------')
const mapRows = []
let mapId = 1
for (const d of doctors) {
  const chosen = new Set([int(1, 20)])
  if (rnd() < 0.25) chosen.add(int(1, 20))
  for (const sp of chosen) mapRows.push([num(mapId++), num(d.id), num(sp)])
}
insertBatch(out, 'doctor_specialties_map', ['id','doctor_id','specialty_id'], mapRows)

// ---------- patients ----------
out.push('-- ---------- patient (5,000) ----------')
const patientRows = []
for (let i = 1; i <= 5000; i++) {
  const gender = rnd() < 0.5 ? 'MALE' : (rnd() < 0.97 ? 'FEMALE' : 'OTHER')
  const first = gender === 'MALE' ? pick(FIRST_M) : pick(FIRST_F)
  const last = pick(LAST)
  const birthYear = int(1936, 2024)
  patientRows.push([
    num(i), esc('PAT' + pad(i, 5)), esc(first), esc(last),
    esc(`${birthYear}-${two(int(1, 12))}-${two(int(1, 28))}`), esc(gender),
    esc('8' + String(int(100000000, 999999999))),
    esc(`${first}.${last}${i}@example.com`.toLowerCase()),
    esc(`${int(1, 999)} ${pick(STREETS)}, ${pick(CITIES)}`),
    // ~4% have no blood group recorded - the column is nullable and real data is patchy
    rnd() < 0.04 ? 'NULL' : esc(pick(BLOOD)),
    TS, TS, '0',
  ])
}
insertBatch(out, 'patient',
  ['id','patient_code','first_name','last_name','date_of_birth','gender','phone','email','address','blood_group','created_at','updated_at','is_deleted'],
  patientRows)

// ---------- doctor_availabilities ----------
out.push('-- ---------- doctor_availabilities ----------')
out.push('-- Every doctor has at least one weekly holiday, and some have two. A working day')
out.push('-- spans AT MOST 10 hours including its break; short days are 5 hours with no break.')
const availRows = []
const availByDoctor = new Map()
let availId = 1
for (const d of doctors) {
  // one guaranteed holiday, rotating so every weekday is somebody's day off
  const off = new Set([DAYS[(d.id - 1) % 7]])
  if (rnd() < 0.35) off.add(DAYS[(d.id + 2) % 7])   // ~a third work a 5-day week
  const working = []
  for (const day of DAYS) {
    if (off.has(day)) continue
    let startMin, endMin, bStart = null, bEnd = null
    if (rnd() < 0.3) {
      // short day: exactly 5 hours, no break at all
      startMin = pick([8, 9, 10, 14]) * 60
      endMin = startMin + 5 * 60
    } else {
      // long day: 8-10 hours INCLUDING the break, never more
      startMin = pick([8, 9, 10]) * 60
      const spanH = pick([8, 9, 10])
      endMin = startMin + spanH * 60
      const breakLen = pick([30, 45, 60])
      bStart = startMin + pick([3, 4, 5]) * 60
      bEnd = bStart + breakLen
      if (bEnd > endMin - 60) { bStart = null; bEnd = null }  // no room: skip the break
    }
    working.push({ day, startMin, endMin, bStart, bEnd })
    availRows.push([
      num(availId++), num(d.id), esc(day), esc(hhmmss(startMin)), esc(hhmmss(endMin)),
      bStart === null ? 'NULL' : esc(hhmmss(bStart)),
      bEnd === null ? 'NULL' : esc(hhmmss(bEnd)),
      TS, TS, '0',
    ])
  }
  availByDoctor.set(d.id, working)
}
insertBatch(out, 'doctor_availabilities',
  ['id','doctor_id','day_of_week','start_time','end_time','break_start_time','break_end_time','created_at','updated_at','is_deleted'],
  availRows)

// ---------- appointments ----------
out.push('-- ---------- appointments (10,000, spread over 2020-2026) ----------')
out.push('-- Every appointment falls on a day the doctor actually works, inside their hours,')
out.push('-- and never during their break - so availability queries and appointment queries agree.')
const DAY_INDEX = { SUNDAY: 0, MONDAY: 1, TUESDAY: 2, WEDNESDAY: 3, THURSDAY: 4, FRIDAY: 5, SATURDAY: 6 }
const START = Date.UTC(2020, 0, 1)
const END = Date.UTC(2026, 11, 31)
const TODAY = Date.UTC(2026, 7, 27)   // 2026-08-27
const SPAN_DAYS = Math.round((END - START) / 86400000)
const apptRows = []
const seen = new Set()
let apptId = 1
while (apptId <= 10000) {
  const d = doctors[int(0, doctors.length - 1)]
  const working = availByDoctor.get(d.id)
  if (!working.length) continue
  const slot = working[int(0, working.length - 1)]
  // find a date in range whose weekday matches that slot
  let date = null
  for (let tries = 0; tries < 20; tries++) {
    const cand = new Date(START + int(0, SPAN_DAYS) * 86400000)
    if (cand.getUTCDay() === DAY_INDEX[slot.day]) { date = cand; break }
  }
  if (!date) continue
  const duration = pick([15, 20, 30, 45, 60])
  // a start inside the working window, on a 15-minute boundary, that also fits
  const latest = slot.endMin - duration
  if (latest <= slot.startMin) continue
  // Step in 15-minute slots, but never past `latest`: with a 20-minute appointment the
  // window is not a whole number of 15-minute steps, and rounding up overshot the end
  // of the working day.
  const steps = Math.floor((latest - slot.startMin) / 15)
  let startMin = slot.startMin + int(0, steps) * 15
  // never schedule across the break
  if (slot.bStart !== null && startMin + duration > slot.bStart && startMin < slot.bEnd) {
    startMin = slot.bEnd
    if (startMin + duration > slot.endMin) continue
  }
  const iso = `${date.getUTCFullYear()}-${two(date.getUTCMonth() + 1)}-${two(date.getUTCDate())}`
  const key = `${d.id}|${iso}|${startMin}`
  if (seen.has(key)) continue           // no double-booking the same doctor and slot
  seen.add(key)
  const ts = Date.UTC(date.getUTCFullYear(), date.getUTCMonth(), date.getUTCDate())
  const status = ts >= TODAY ? 'SCHEDULED' : (rnd() < 0.85 ? 'COMPLETED' : 'CANCELLED')
  apptRows.push([
    num(apptId), num(int(1, 5000)), num(d.id), esc(iso),
    esc(hhmmss(startMin)), esc(hhmmss(startMin + duration)), num(duration), esc(status),
    // Booked on the day it happened for past appointments; for anything dated after the
    // seed reference date, booked ON that reference date - a row cannot be created in
    // the future.
    bookedAt(iso), bookedAt(iso), '0',
  ])
  apptId++
}
insertBatch(out, 'appointments',
  ['id','patient_id','doctor_id','appointment_date','start_time','end_time','duration_minutes','status','created_at','updated_at','is_deleted'],
  apptRows, 400)

// ---------- ai_knowledge ----------
out.push('-- ---------- ai_knowledge (what the AI is told about the schema) ----------')
const knowledge = JSON.parse(fs.readFileSync(path.join(SP, 'knowledge.json'), 'utf8')).data
insertBatch(out, 'ai_knowledge',
  ['id','table_name','purpose','columns_info','hints','enabled','created_at','updated_at','is_deleted'],
  knowledge.map((k, i) => [num(i + 1), esc(k.tableName), esc(k.purpose), esc(k.columnsInfo), esc(k.hints), '1', TS, TS, '0']))

// ---------- ai_prompt_example ----------
out.push('-- ---------- ai_prompt_example (question -> known-good SQL) ----------')
const examples = JSON.parse(fs.readFileSync(path.join(SP, 'examples.json'), 'utf8')).data
insertBatch(out, 'ai_prompt_example',
  ['id','question','generated_sql','enabled','created_at','updated_at','is_deleted'],
  examples.map((e, i) => [num(i + 1), esc(e.question), esc(e.generatedSql), '1', TS, TS, '0']))

// ---------- app_setting ----------
out.push('-- ---------- app_setting ----------')
out.push('-- nim.api-key is seeded with a PLACEHOLDER value, not a real key. Replace it after')
out.push('-- loading - see the note at the top of this file.')
insertBatch(out, 'app_setting',
  ['id','name','setting_value','secret','description','enabled','created_at','updated_at','is_deleted'],
  [
    [num(1), esc('nim.model'), esc('nvidia/nemotron-3-super-120b-a12b'), '0', esc('NIM model used for text-to-SQL'), '1', TS, TS, '0'],
    [num(2), esc('nim.base-url'), esc('https://integrate.api.nvidia.com/v1'), '0', esc('NVIDIA NIM OpenAI-compatible base URL'), '1', TS, TS, '0'],
    // A PLACEHOLDER, not a credential - the row exists so the key is easy to fill in.
    // Replace it after loading:
    //   PUT /api/v1/settings/3  {"name":"nim.api-key","value":"nvapi-...","secret":true}
    [num(3), esc('nim.api-key'), esc('paste api key here'), '1', esc('NVIDIA NIM API key'), '1', TS, TS, '0'],
  ])

// ---------- widgets ----------
out.push('-- ---------- widget (20: 14 hand-built + 6 AI-generated) ----------')
const widgets = JSON.parse(fs.readFileSync(path.join(SP, 'widgets.json'), 'utf8'))
const widgetRows = widgets.map((w, i) => [
  num(i + 1), esc(w.code), esc(w.nm), esc(w.descr), esc(w.mod), esc(w.typ),
  esc('APPROVED'), '1', w.flt === null || w.flt === undefined ? 'NULL' : esc(w.flt),
  esc(w.sqlt), TS, TS, '0',
])
insertBatch(out, 'widget',
  ['id','code','name','description','module','type','status','enabled','filters','sql_template','created_at','updated_at','is_deleted'],
  widgetRows, 5)

// ---------- boards ----------
out.push('-- ---------- board (5) ----------')
out.push('-- layout is JSON: [{"widgetId":N,"x":0-2,"y":row,"w":1-3,"h":rows}]. The engine')
out.push('-- enforces x + w <= 3, so every row below adds up to at most 3 columns.')
const byCode = {}
widgets.forEach((w, i) => { byCode[w.code] = i + 1 })
const L = (widgetId, x, y, w, h) => ({ widgetId, x, y, w, h })
const boards = [
  ['Hospital Overview', [
    L(byCode['total-active-doctors'], 0, 0, 1, 4),
    L(byCode['total-active-patients'], 1, 0, 1, 4),
    L(byCode['todays-appointments'], 2, 0, 1, 4),
    L(byCode['appointment-status-distribution'], 0, 4, 1, 5),
    L(byCode['appointments-by-specialty'], 1, 4, 2, 5),
    L(byCode['doctor-appointment-schedule'], 0, 9, 3, 6),
  ]],
  ['Appointments', [
    L(byCode['scheduled-appointments'], 0, 0, 1, 4),
    L(byCode['completed-appointments'], 1, 0, 1, 4),
    L(byCode['todays-appointments'], 2, 0, 1, 4),
    L(byCode['appointments-last-30-days'], 0, 4, 3, 5),
    L(byCode['monthly-appointments'], 0, 9, 2, 5),
    L(byCode['appointments-by-doctor'], 2, 9, 1, 5),
  ]],
  ['Doctors', [
    L(byCode['total-active-doctors'], 0, 0, 1, 4),
    L(byCode['doctors-by-specialty'], 1, 0, 2, 4),
    L(byCode['how-many-doctors-work-on-sunday'], 0, 4, 1, 4),
    L(byCode['i-want-all-doctor-all-day-working-time-and-doctor-firstname-and-lastname-as-name'], 1, 4, 2, 6),
    L(byCode['list-top-5-doctor-having-maximum-appointment-2'], 0, 10, 3, 5),
  ]],
  ['Patients', [
    L(byCode['total-active-patients'], 0, 0, 1, 4),
    L(byCode['patient-gender-distribution'], 1, 0, 2, 4),
    L(byCode['list-all-patients-with-their-blood-group'], 0, 4, 3, 6),
  ]],
  ['Specialties & AI', [
    L(byCode['specialty-list'], 0, 0, 1, 5),
    L(byCode['doctors-by-specialty'], 1, 0, 2, 5),
    L(byCode['list-only-cardiology-doctor-available-on-monday-also-can-you-join-firstname-and-lastname-as-name-also-its-timing'], 0, 5, 3, 6),
  ]],
]
insertBatch(out, 'board', ['id','name','layout','created_at','updated_at','is_deleted'],
  boards.map((b, i) => [num(i + 1), esc(b[0]), esc(JSON.stringify(b[1])), TS, TS, '0']), 5)

out.push('SET FOREIGN_KEY_CHECKS = 1;')
out.push('')
out.push('-- Sanity check after loading:')
out.push("--   SELECT 'specialties', COUNT(*) FROM specialties")
out.push("--   UNION ALL SELECT 'doctors', COUNT(*) FROM doctors")
out.push("--   UNION ALL SELECT 'patient', COUNT(*) FROM patient")
out.push("--   UNION ALL SELECT 'doctor_availabilities', COUNT(*) FROM doctor_availabilities")
out.push("--   UNION ALL SELECT 'appointments', COUNT(*) FROM appointments")
out.push("--   UNION ALL SELECT 'widget', COUNT(*) FROM widget")
out.push("--   UNION ALL SELECT 'board', COUNT(*) FROM board;")
out.push('')

fs.mkdirSync(path.dirname(OUT), { recursive: true })
fs.writeFileSync(OUT, out.join('\n'), 'utf8')

// ---------- report ----------
const bad = []
for (const r of availRows) {
  const s = r[3].replace(/'/g, ''), e = r[4].replace(/'/g, '')
  const mins = (t) => Number(t.slice(0, 2)) * 60 + Number(t.slice(3, 5))
  if (mins(e) - mins(s) > 600) bad.push(r[1] + ' ' + r[2])
}
console.log('written:', OUT)
console.log('specialties 20, doctors 200, map', mapRows.length, ', patients 5000')
console.log('availability rows:', availRows.length, '| spans over 10h:', bad.length)
console.log('appointments:', apptRows.length)
console.log('knowledge', knowledge.length, '| examples', examples.length, '| widgets', widgetRows.length, '| boards', boards.length)
const undef = widgetRows.filter((r) => r.includes(undefined)).length
console.log('widget rows with undefined:', undef)
console.log('board layouts referencing a missing widget:',
  boards.filter((b) => b[1].some((it) => !it.widgetId)).length)
