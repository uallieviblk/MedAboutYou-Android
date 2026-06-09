# MedAboutYou — User Guide

A complete, step-by-step guide to every screen and flow in MedAboutYou.

- **What it is:** an app to look up European/Italian medicine records *and* manage
  your own medication schedule — reminders, a dose log, adherence stats and refill
  forecasting.
- **Privacy:** everything about *your* medications (schedules, the dose log, stock)
  is stored only on your phone. Nothing is uploaded. The only internet use is
  looking up medicine information (EMA/AIFA) and an optional medicine image.

---

## Contents

1. [Installing & first run](#1-installing--first-run)
2. [Getting around](#2-getting-around)
3. [Finding a medicine](#3-finding-a-medicine)
4. [The medicine record](#4-the-medicine-record)
5. [Creating a schedule](#5-creating-a-schedule)
6. [Today](#6-today)
7. [Calendar & editing single doses](#7-calendar--editing-single-doses)
8. [Schedules: pause, edit, cancel](#8-schedules-pause-edit-cancel)
9. [Insights](#9-insights)
10. [Stock & refills](#10-stock--refills)
11. [Reminders & caregiver alerts](#11-reminders--caregiver-alerts)
12. [Settings](#12-settings)
13. [FAQ & troubleshooting](#13-faq--troubleshooting)
14. [Glossary](#14-glossary)

---

## 1. Installing & first run

1. Install the APK (from the [project website](../website/index.html) or your
   build of `app-debug.apk`). Android 8.0 (Oreo) or newer is required.
2. On first launch the app asks for **notification permission** — allow it so dose
   reminders can appear.
3. You land on **Today**. It's empty until you add a medicine.

> The medicine catalogue (EMA) is **downloaded once** and cached for fast, offline
> browsing. You'll be prompted to download it the first time you search EMA.

---

## 2. Getting around

MedAboutYou has **three tabs** along the bottom:

| Tab | What it's for |
|-----|---------------|
| **Today** 🏠 | Your doses for today and your adherence at a glance |
| **Calendar** 📅 | A month view and the agenda for any day; edit single doses |
| **Schedules** 💊 | The list of your active therapies |

Two more screens live in the **⋮ overflow menu** (top-right): **Insights**
(statistics) and **Settings**.

**Search** isn't a tab — you open it from the **"Add medicine"** button on the
Schedules tab, because every therapy is created from a medicine record.

<p align="center"><img src="images/today.png" width="30%" alt="Today tab"/></p>

---

## 3. Finding a medicine

Open **Schedules → Add medicine** to reach Search.

1. Pick a source with the toggle at the top:
   - **EMA (Europe)** — centrally authorised medicines; cached on-device, searches
     offline. The first time, tap **Download dataset**.
   - **AIFA (Italy)** — searched live (needs internet); type at least 2 letters.
2. Type a **name, INN (active ingredient) or substance**. Results update as you
   type.
3. For EMA you can include **veterinary** medicines with the **Vet** chip, and
   refresh the cached dataset with the ↻ button.
4. Tap a result to open its **record**.

**Not in the databases?** (e.g. a supplement or a custom product.) Type its name
and tap **"Add '…' as a custom medicine"** — this takes you straight to the
new-schedule form with the name filled in.

---

## 4. The medicine record

The record shows everything the source publishes:

- **Header** — name, active substance / INN, and **type badges** (Generic, Orphan,
  Biosimilar, PRIME, Additional monitoring, …).
- **Packaging image** when one is available.
- **Stock** — your doses on hand, with **Set stock…** and quick **+ pack** buttons
  (real marketed pack sizes for AIFA records, or a generic **+30**).
- **Therapeutic indication, Classification, Authorisation** sections.
- **Posology & administration** — opens the official **EPAR/RCP** PDF where the
  dosing is published.
- **Add to my medication schedule** — starts a schedule for this medicine
  (carrying its identity so stock stays linked).
- **Open EPAR / AIFA page** — the official source page.

---

## 5. Creating a schedule

Reached from **Add to my medication schedule** (record) or the custom-medicine
button. The form adapts to the **repeat unit** you choose.

**Steps**

1. **Medicine name** — pre-filled; editable for custom medicines.
2. **Start date** (for repeating schedules).
3. **Repeat every** — choose the unit, then the interval:
   | Unit | You set | Fires |
   |------|---------|-------|
   | **Once** | a full date + time | a single dose |
   | **Hours** | the minute of each step | every *N* hours from midnight |
   | **Days** | one or more times of day | every *N* days |
   | **Weeks** | the weekdays + times | the chosen weekdays, every *N* weeks |
   | **Months** | day-of-month (or "last day") + time | that day, every *N* months |
   | **Years** | month + day + time | that date, every *N* years |
4. **Dose times** — add as many as you need (e.g. 08:00, 14:00, 22:00). For weekly
   schedules, pick the **weekday chips** and the shared times.
5. **Allowed window (± minutes)** — how close to the scheduled time a dose counts
   as "on time"; it also controls how early you can tick a dose off (see
   [Today](#6-today)).
6. **Reminders** — *Remind me every* (repeat the local reminder) and *Alert
   caregiver after* (escalation delay). Both must stay below the window.
7. **Ends** — *Ongoing*, *On date*, or *After N* doses.
8. **Notes (optional)** — e.g. "Take with food". Notes appear on the schedule card.
9. A **live summary** ("Repeat every 2 weeks · Mon, Thu · 08:00") confirms what
   you've built. Tap **Add to calendar**.

> Months with fewer days clamp a day-31 schedule to the **last existing day** (so
> "31st" becomes 30 Sep / 28 Feb). The picker blocks 29 Feb for yearly schedules.

---

## 6. Today

Your home base.

- **Adherence ring** — *taken / total* for today, with your current **day streak**.
- **Refill banner** — appears when a medicine will run out soon.
- **Dose timeline** — grouped into **Morning / Afternoon / Evening / Night**.
  - Tick the **checkbox** to mark a dose **taken** (a short haptic confirms it);
    untick to undo. Taking a dose **debits one unit of stock**; untaking returns it.
  - **Take all** appears on a time block when it has due, untaken doses.
- **Early tick:** a dose becomes tickable from the **start of its window**
  (scheduled time − the *Allowed window*). So an 08:00 dose with a 30-minute window
  can be ticked from 07:30 — handy when you take it a little early. Doses outside
  their window stay locked.
- **Pull down to refresh.**

---

## 7. Calendar & editing single doses

<p align="center"><img src="images/calendar.png" width="30%" alt="Calendar"/> <img src="images/edit-dose.png" width="30%" alt="Edit a single dose"/></p>

- The **month grid** colours each day by state: **green** (taken), **blue**
  (upcoming), **purple** (predicted shortage), **red** (missed). Tap a day to see
  its **agenda**; use **‹ ›** to change month.
- Each agenda row shows the time, medicine, the ± window, status and stock. Tick to
  take/untake (same window rule as Today).
- **Editing one dose:** tap the **✎ (Edit dose)** icon on a row to open the
  **Edit dose** dialog:
  - **Time of dose** and **Allowed window** — retime just this occurrence.
  - **Skip this dose** — mark this single occurrence as skipped.
  - **Apply to:**
    - **This dose only** — changes (or skips) just that one dose.
    - **This and following** — splits the schedule: every dose from this date on
      uses the new time (or, with *Skip*, ends the therapy here). The past is
      untouched and your history stays intact.
  - Tap **Save**.

> New therapies are always created from a medicine (Search → record), so the
> Calendar itself has no "＋" button — use **Schedules → Add medicine**.

---

## 8. Schedules: pause, edit, cancel

<p align="center"><img src="images/schedules.png" width="30%" alt="Schedules"/></p>

The **Schedules** tab lists every active therapy with its recurrence, start date
and **notes**.

- **Tap a card** to **edit** it. Edits take effect **from today**: the past is
  preserved and a new version starts today (so your adherence history is never
  rewritten).
- **⏸ Pause / ▶ Resume** — pause a therapy **indefinitely**, or for **1 week / 2
  weeks / 1 month / until a date** (it auto-resumes). Paused therapies generate no
  doses or reminders; past days are unaffected.
- **🗑 Cancel** — stops future doses after a confirmation. The prescription is kept
  in history (soft-cancel), never deleted.
- **Add medicine** (FAB) — opens Search to start a new therapy.

---

## 9. Insights

Open from the **⋮** menu. It shows:

- **Adherence** over **7 / 30 / 90 days** (colour-coded).
- **Day streak** and **doses missed (30 days)**.
- A **30-day heatmap** (one cell per day: green = all taken, amber = partial, red =
  missed, grey = nothing scheduled).
- **Adherence by medicine** — a bar per medicine over 30 days.
- **Next refill** — every medicine that will run out, soonest first, with the date
  and doses left.

Pull down to refresh.

---

## 10. Stock & refills

- Set your **stock** on a medicine record (**Set stock…**) or top it up by whole
  **packs**.
- Each time you **take** a dose, stock drops by one; **untaking** returns it.
  Scheduling never changes stock.
- The app continuously **forecasts run-out dates** from your stock and schedule and
  surfaces them on the **Today** refill banner, as **purple "shortage"** days on
  the Calendar, and in the **Insights → Next refill** list.

---

## 11. Reminders & caregiver alerts

- **Dose reminders** are on by default (toggle in Settings). At a dose's time you
  get a notification with **Mark taken** / **Skip** actions; it repeats every
  *Remind me every* minutes until you act, within the window.
- **Caregiver alerts (optional, off by default):** in **Settings → Caregiver
  alerts**, add your name and one or more caregiver phone numbers and **grant SMS
  permission**. If a dose stays untaken past the schedule's *Alert caregiver after*
  delay, the app sends **one SMS** to those numbers. This is the only thing the app
  ever sends off your device — and only if you set it up.
- Reminders re-arm automatically after a reboot (see **Start at boot**).

---

## 12. Settings

Open from the **⋮** menu:

- **Language** — System default, English, Italiano, Français, Español, Deutsch.
- **Enable dose reminders** — master on/off.
- **Start at boot** — re-arm reminders automatically after the phone restarts.
- **Caregiver alerts** — your name + caregiver numbers + SMS permission (see above).
- **About** — version and app ID.
- A privacy note reaffirming that your data stays on the device.

---

## 13. FAQ & troubleshooting

**Search shows nothing for EMA.** Download the dataset first (the prompt's
**Download dataset** button, or the ↻ icon). It needs internet once.

**AIFA search says "type at least 2 letters".** AIFA is live; enter 2+ characters
and ensure you're online.

**I can't tick today's dose yet.** It unlocks at the start of its window
(scheduled time − *Allowed window*). Widen the window on the schedule if you often
take it earlier.

**I edited a schedule — why is there a new entry?** Edits apply *from today*; the
original is ended yesterday so your past stays intact. Ended rows are hidden from
the Schedules list automatically.

**Reminders don't fire.** Check **Settings → Enable dose reminders**, allow
notifications, and consider enabling **Start at boot**. Some phones require
disabling battery optimisation for exact alarms.

**My caregiver didn't get an SMS.** Confirm a number is set, **SMS permission** is
granted, the schedule's *Alert caregiver after* is greater than 0, and the dose was
left untaken past that delay.

---

## 14. Glossary

- **EMA** — European Medicines Agency (EU-wide medicine authorisations).
- **AIFA** — Agenzia Italiana del Farmaco (Italian medicines agency).
- **INN** — International Non-proprietary Name (the active ingredient's generic
  name).
- **EPAR / RCP** — the official European/Italian product information (where dosing
  is published).
- **Occurrence / dose** — one scheduled administration at a specific date & time.
- **Window** — minutes around the scheduled time within which a dose counts as
  on-time (and from which it can be ticked early).
- **Adherence** — the share of scheduled doses you actually took.
