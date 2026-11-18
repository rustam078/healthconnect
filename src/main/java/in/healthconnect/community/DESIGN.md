# Community Chat module

WhatsApp-style group chat ("communities") inside HealthConnect. Reuses the app's own login
(JWT), users and permission system — no `?userId=` stand-in like the reference demo.

## Who can do what

User type is read from the login account: `doctorId != null` → DOCTOR, `patientId != null` →
PATIENT, otherwise ADMIN/staff.

| Visibility      | Who discovers it                 | How you join            | Patients |
|-----------------|----------------------------------|-------------------------|----------|
| `PUBLIC`        | everyone (doctor, patient, admin)| self join / leave       | yes      |
| `PUBLIC_DOCTORS`| doctors + admins only            | doctor self joins       | never    |
| `PRIVATE`       | members only (admin invited)     | admin adds; no self-join| never    |

- **Create / delete** a group: permission-gated (`COMMUNITIES_CREATE` / `COMMUNITIES_DELETE`) —
  admin only. The creator becomes the group ADMIN.
- **Add members** (private): group admin, and patients can never be added.
- **Join / leave**: PUBLIC anyone; PUBLIC_DOCTORS doctors/admins. A member can always leave.
- **Messages**: only members read/send. Only the sender may delete their own message.
- **System messages** (`SYSTEM` type): join/leave/added/removed show inline, like WhatsApp.

## Real-time (SSE)

One Server-Sent-Events stream per user (all their communities), kept in memory
(`ChatStreamRegistry`). A message is saved, then pushed **after commit**
(`@TransactionalEventListener(AFTER_COMMIT)`) so screens never show rolled-back data.
Events: `connected`, `message`, `message_deleted`, `communities_changed`, `presence`
(a co-member went online/offline) and `presence_snapshot` (who is online, sent on connect).
A user is online while they hold at least one stream; the registry fans presence out to the
people who share a group with them.
Spring releases the EntityManager when async SSE starts, so a stream does not pin a DB
connection; set `spring.jpa.open-in-view=false` only if many concurrent streams ever pressure
the pool.

## Files / attachments

Files live on local disk; the DB keeps only a reference row (`stored_files`), never the bytes.
A `category` (PROFILE_IMAGE / LAB_REPORT / COMMUNITY_CHAT) + `reference_id` say what a file is
for, which drives validation, the disk sub-folder and who may read it. See
`in.healthconnect.storage`. A chat image is a two-step flow: upload → `POST /files` returns an
id, then send `{ message?, fileId }`.

## Tables (auto-created, ddl-auto=update)

| Table | Purpose |
|-------|---------|
| `communities` | groups: name, description, visibility, created_by |
| `community_members` | membership: role ADMIN/MEMBER, last_read_message_id |
| `chat_messages` | messages: type, body, attachment_id, soft-deleted |
| `stored_files` | file references: storage_key, category, reference_id (shared module) |

## Permission overrides (RequiredPermissionResolver)

POST endpoints that only a member uses are reads, not creates, so they are overridden to VIEW:
send message, delete own message, join, mark-read → `COMMUNITIES_VIEW`; add members →
`COMMUNITIES_UPDATE`; `/files/**` → AUTHENTICATED (fine-grained check in the file guard).
