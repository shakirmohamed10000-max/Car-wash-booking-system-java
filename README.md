# Car Wash Booking System (Java)

A terminal application for running a car wash: register customers and their vehicles, keep a
price list per vehicle type, schedule appointments, track bookings, take payments, and review
daily reports. It is written in plain
Java 17 with no external libraries, and it behaves like a small database: everything you enter is
saved to disk immediately, so closing the terminal and starting the program again brings back
exactly what was there before.

This is the OOP2 project. Stages 1 to 4 are complete; payment recording, queue estimates, daily
reports, appointment scheduling, the regular-customer discount and the "who is next" screen are also
implemented. Attendant login remains a future idea.

---

## Contents

1. [What the program does](#what-the-program-does)
2. [Requirements](#requirements)
3. [Building and running](#building-and-running)
4. [Project structure](#project-structure)
5. [How the code is organised](#how-the-code-is-organised)
6. [Stage 1: Customers](#stage-1-customers)
7. [Stage 2: Vehicles](#stage-2-vehicles)
8. [Stage 3: Wash services and pricing](#stage-3-wash-services-and-pricing)
9. [Stage 4: Bookings](#stage-4-bookings)
10. [Payments, queue, appointments and daily reports](#payments-queue-appointments-and-daily-reports)
11. [The storage engine](#the-storage-engine)
12. [Data file reference](#data-file-reference)
13. [Input handling and the terminal interface](#input-handling-and-the-terminal-interface)
14. [Privacy and security](#privacy-and-security)
15. [Error handling and logging](#error-handling-and-logging)
16. [Testing](#testing)
17. [Configuration](#configuration)
18. [Design decisions](#design-decisions)
19. [Roadmap](#roadmap)
20. [Troubleshooting](#troubleshooting)

---

## What the program does

| Stage | Area | What you can do |
|---|---|---|
| 1 | Customers | Register, list, view, search, update and delete customers |
| 2 | Vehicles | Add a Car, Bike, SUV or Truck for a customer; list, search, update, transfer and delete |
| 3 | Wash services | Four standard services, each with a price per vehicle type; add, edit, hide and delete services |
| 4 | Bookings | Book one or more services for a vehicle, total calculated automatically, print a slip, start, complete, edit or cancel |
| 5 | Payments | Cash, M-Pesa and card-reference entries; part-payments, balances, numbered receipts, cancellation refund records |
| 6 | Operations | Computed queue/wait estimates, non-overlapping appointments with rescheduling, and date-based daily reports |

Data is stored in plain text files in a `data/` folder next to the program. There is no database
server to install.

---

## Requirements

* Java Development Kit (JDK) **17 or newer** (`javac -version` should print 17 or higher)
* A terminal. Linux and macOS are fully supported; Windows works too (see the note on file
  permissions under [Privacy and security](#privacy-and-security)).

Nothing else is needed. There is no Maven, Gradle or library to download.

---

## Building and running

Always run the program **from the project root folder** (the folder that contains `src/`). The
`data/` folder is created relative to where you start the program, so starting it from a different
folder would create a second, empty set of data.

### Linux and macOS

```bash
./run.sh          # compiles if needed, then starts the program
./test.sh         # compiles and runs the automated tests
```

Or by hand:

```bash
javac --release 17 -d out $(find src -name '*.java')
java -cp out app.Main
```

### Windows (Command Prompt)

```bat
dir /s /b src\*.java > sources.txt
javac --release 17 -d out @sources.txt
java -cp out app.Main
```

### First run

On the first run the program creates the `data/` and `logs/` folders, the empty data files, and
the four standard wash services. On later runs it loads whatever is already there. Services are only
seeded when the services file did not exist, so deleting a standard service never brings it back.

### Leaving the program

Choose `0` in the main menu. Closing the terminal, pressing Ctrl+D or Ctrl+C is also safe, because
every change has already been written to disk by the time you see its confirmation message.

---

## Project structure

```
Car-wash-booking-system-java/
├── README.md
├── .gitignore
├── run.sh                  start the program (compiles first if needed)
├── test.sh                 compile and run the automated tests
├── src/
│   ├── app/
│   │   ├── Main.java       entry point, wires the layers together
│   │   ├── Config.java     business name, limits, time zone, folders
│   │   └── AppLog.java     file-only logging with phone masking
│   ├── exception/
│   │   ├── AppException.java
│   │   ├── ValidationException.java
│   │   ├── DuplicateException.java
│   │   ├── NotFoundException.java
│   │   ├── BusinessRuleException.java
│   │   └── StorageException.java
│   ├── model/
│   │   ├── Customer.java
│   │   ├── Vehicle.java
│   │   ├── VehicleType.java
│   │   ├── WashService.java
│   │   ├── Booking.java
│   │   ├── BookingItem.java
│   │   └── BookingStatus.java
│   ├── storage/
│   │   ├── FileStore.java          locking, atomic writes, backups, line format
│   │   ├── Repository.java         generic file-backed table
│   │   ├── CustomerRepository.java
│   │   ├── VehicleRepository.java
│   │   ├── ServiceRepository.java
│   │   └── BookingRepository.java
│   ├── service/
│   │   ├── Validators.java         cleaning and checking of all text input
│   │   ├── Money.java
│   │   ├── DeleteOutcome.java
│   │   ├── CustomerService.java
│   │   ├── VehicleService.java
│   │   ├── LoyaltyService.java     regular-customer discount rules
│   │   ├── ServiceCatalog.java
│   │   ├── BookingService.java
│   │   └── IntegrityChecker.java
│   └── ui/
│       ├── InputReader.java
│       ├── TablePrinter.java
│       ├── ReceiptPrinter.java
│       ├── Views.java
│       ├── Pickers.java
│       ├── Menu.java
│       ├── MainMenu.java
│       ├── CustomerMenu.java
│       ├── VehicleMenu.java
│       ├── ServiceMenu.java
│       ├── BookingMenu.java
│       ├── CancelledException.java
│       └── InputClosedException.java
├── test/
│   └── tests/
│       └── TestRunner.java
├── data/                   created at run time, never committed
├── logs/                   created at run time, never committed
└── out/                    compiled classes, never committed
```

---

## How the code is organised

The program is split into layers. Each layer only talks to the one below it, which keeps every
class small and makes the rules testable without a terminal.

```
  ui         menus, prompts, tables, slips          (never touches files, never decides rules)
   │
  service    business rules and validation          (never prints, never touches files directly)
   │
  storage    repositories and file handling         (knows nothing about menus or rules)
   │
  model      plain immutable data classes           (no logic beyond their own consistency)
```

| Package | Responsibility |
|---|---|
| `app` | Starting the program, settings, logging |
| `exception` | The few error types the program throws on purpose. Their messages are always safe to show to a user |
| `model` | `Customer`, `Vehicle`, `WashService`, `Booking` and friends. Immutable Java records: a change produces a new object |
| `storage` | Reading and writing the text files |
| `service` | Every business rule: duplicates, what can be deleted, price calculation, status flow |
| `ui` | Everything the user sees and types |

Errors travel upward as exceptions and are turned into one plain sentence in a single place
(`Menu.guard`). That is why no stack trace can ever appear on the terminal.

---

## Stage 1: Customers

**Menu:** Customers

### Fields

| Field | Notes |
|---|---|
| ID | Assigned automatically, starts at 1, never reused |
| Name | 2 to 60 characters |
| Phone | Stored in one standard form, `+254XXXXXXXXX` |
| Registered | Date and time, set by the system clock |
| Active | `false` once archived |

### Name rules

* Letters (any language, so Swahili and Arabic names work), spaces, apostrophes and hyphens only.
* Extra spaces are collapsed. Leading and trailing spaces are removed.
* A word typed all in lower case or all in capitals is converted to a capital first letter
  (`amina HASSAN` becomes `Amina Hassan`, `o'brien` becomes `O'Brien`). A word that already has
  mixed case (`McDonald`) is left exactly as typed.
* Digits, `|` and other symbols are rejected.

### Phone rules

All of these are accepted and stored as `+254712345678`:

`0712345678`, `0712 345 678`, `0712-345-678`, `+254712345678`, `254712345678`

Numbers starting `01` are accepted as well as `07`. Anything else (letters, wrong length, another
country code) is rejected with an example of the right format.

The phone number is the customer's identity, so it must be **unique**.

### Operations

| Operation | Behaviour |
|---|---|
| Register | Name and phone. A customer with the same name but a different phone gives a warning that asks whether to continue (two real people can share a name). A phone that is already registered is refused and the existing customer is named |
| List | All active customers sorted by name, with a masked phone and a vehicle count |
| View | Full details: name, full phone, registration date, vehicles, number of bookings |
| Search | Case-insensitive part of a name, or at least three digits of a phone number |
| Update | Press Enter to keep a field. Changing the phone re-checks that it is unique. Saving with nothing changed says so |
| Delete | Needs the full word `yes`. See the table below |

### Finding a customer

Wherever the program asks for a customer you can type an **ID**, a **phone number** or **part of a
name**. If a name matches several customers a short list is shown and you pick an ID. In the
booking and vehicle flows, if nobody matches, the program offers to register the customer on the
spot.

### What deleting a customer does

| Situation | Result |
|---|---|
| Customer has an active (waiting or in-progress) booking | Blocked |
| Customer still has vehicles | Blocked: remove the vehicles first |
| Customer has booking history, or only archived vehicles | **Archived**: hidden from every list but kept in the file |
| Customer has nothing linked to them | Removed from the file |

Archiving exists so that old bookings and receipts never lose their owner. If an archived
customer's phone number is registered again, the program offers to **restore** the old record with
its history.

---

## Stage 2: Vehicles

**Menu:** Vehicles

### Fields

| Field | Notes |
|---|---|
| ID | Assigned automatically, never reused |
| Plate | Normalised, for example `KAA 123A`. Unique across the whole system |
| Type | `Bike`, `Car`, `SUV` or `Truck` (an enum, so no other value can exist) |
| Owner | The customer's **ID**, not their name, so renaming a customer never breaks the link |
| Registered, Active | As for customers |

### Plate rules

| Style | Example | Used for |
|---|---|---|
| `K` + two letters + three digits + letter | `KAA 123A` | Cars, SUVs, trucks |
| `KM` + two letters + three digits + letter | `KMAA 123A` | Motorcycles |
| `GK` + three digits + letter | `GK 123A` | Government vehicles |

Typing is forgiving: `kaa123a`, `KAA-123A` and ` kaa  123a ` all become `KAA 123A`.

If the plate style and the chosen type disagree (a `KM..` plate saved as a Car, or a Bike with an
ordinary plate) the program shows a **warning and asks whether to save anyway**, because
three-wheelers and older plates are real exceptions.

### Operations

| Operation | Behaviour |
|---|---|
| Add | The owner must be an existing active customer. If there are no customers the program offers to register one first |
| List all / by customer | Sorted by plate |
| Search | Part of a plate, ignoring case and spacing (`123a` finds `KAA 123A`) |
| View | Plate, type, owner, registration date, number of bookings |
| Update | Enter keeps a field. Change the plate, type and/or **transfer the vehicle to another owner** |
| Delete | Needs the full word `yes` |

### Update rules

* The plate must stay unique.
* **Type and owner cannot change while the vehicle has an active booking** (the price depends on
  the type, and the booking belongs to the owner). The plate can be corrected at any time; if the
  vehicle has booking history the program warns that old bookings keep the old plate.

### What deleting a vehicle does

| Situation | Result |
|---|---|
| Active booking | Blocked |
| Has booking history | **Archived** (hidden, kept) |
| No bookings ever | Removed from the file |

If a plate that was archived is registered again, the old record is brought back with the new owner
and type, so the vehicle's history stays attached to the plate.

---

## Stage 3: Wash services and pricing

**Menu:** Wash services

### Pricing model

Each service has **its own price for each vehicle type**. A type with no price means that
combination is **not offered** (you cannot book an interior clean on a bike).

Standard services created on first run (prices in KSh):

| Service | Minutes | Bike | Car | SUV | Truck |
|---|---|---|---|---|---|
| Basic Wash | 30 | 100 | 300 | 400 | 600 |
| Full Wash + Wax | 90 | 200 | 600 | 800 | 1,200 |
| Interior + Exterior | 60 | not offered | 500 | 650 | 900 |
| Engine Wash | 30 | not offered | 400 | 500 | 800 |

You can change all of this from the menu without touching the code.

### Rules

* Service names are unique, ignoring case.
* Prices are whole shillings, from 1 to 100,000. Cents never appear anywhere in the system. Commas, when used, must group thousands correctly (for example `1,200`; `1,2` is rejected).
* Duration is 5 to 480 minutes. Queue estimates use a copy of each selected duration saved with the booking.
* A service needs a price for at least one vehicle type.
* **Price changes only affect new bookings.** Existing bookings keep the name and price they were
  made with.

### Operations

| Operation | Behaviour |
|---|---|
| List | All services including inactive ones, shown as a price table. A dash means not offered |
| Add | Name, duration, then a price per type (Enter means not offered) |
| Edit | Enter keeps a value. Type `-` for a price to stop offering that type |
| Activate / deactivate | A deactivated service disappears from new bookings but stays readable on old ones |
| Delete | Only allowed if the service has **never** been booked. Otherwise deactivate it |

---

## Stage 4: Bookings

**Menu:** Bookings

### What a booking holds

| Field | Notes |
|---|---|
| Booking number | `BK-000001`, `BK-000002`, and so on |
| Customer, vehicle | Linked by ID, **and** a copy of the customer name, plate and vehicle type taken at booking time |
| Services | One or more. Each stores copies of the service name, price charged, and duration |
| Subtotal, discount, total | Whole shillings. The total is calculated automatically; loyalty discounts are not enabled yet |
| Status | See below |
| Timestamps | Created, started, completed, cancelled, plus optional scheduled appointment time |
| Cancel reason | Required when cancelling |

Because the name, plate and prices are **snapshots**, editing a customer, renaming a service or
changing a price later never rewrites history. A receipt printed next year still shows what was
true on the day.

### Status flow

```
BOOKED ──► IN_PROGRESS ──► COMPLETED
   │            │
   └────────────┴──► CANCELLED
```

* `COMPLETED` and `CANCELLED` are final. Bookings are never deleted.
* Illegal moves (completing a cancelled booking, cancelling twice, starting a completed one) are
  refused with a clear sentence. The rule lives in one place, `Booking`, so it cannot be bypassed.

### Making a booking

1. **Customer.** Type an ID, phone number or part of a name. If nobody matches, register them on
   the spot.
2. **Vehicle.** Pick one of the customer's vehicles, or type `N` to add a new one.
3. **Services.** Only active services that are offered for that vehicle's type are listed, each
   with its time and price. Type the numbers separated by commas, for example `1,4`.
4. **Overlap note.** If you pick services that normally cover the same work (for example Basic
   Wash with Full Wash + Wax) the program says so and asks whether to continue. It is a warning,
   not a block. The pairs are listed in `Config.OVERLAPPING_SERVICES`.
5. **Summary and confirmation.** Each line, the total, and a yes/no question.
6. **Saved.** A booking slip is printed.

### Booking rules

* A vehicle can have **only one active booking** at a time. This stops a double entry or two
  attendants booking the same car. Once the booking is completed or cancelled the vehicle can be
  booked again.
* The vehicle must belong to the customer.
* Everything is checked again at the moment of saving. If a service was deactivated while the
  attendant was choosing, the booking is refused instead of saved with stale data.
* No duplicate services in one booking, and at least one service.

### Other booking operations

| Operation | Behaviour |
|---|---|
| Waiting and in-progress | Active bookings, oldest first, which is the order cars should be served |
| All bookings | Every booking, newest first |
| View or reprint | Enter `BK-000012` or just `12` |
| Start wash | `BOOKED` to `IN_PROGRESS`, with a yes/no check |
| Complete wash | `IN_PROGRESS` to `COMPLETED`, shows the total |
| Add or remove services | Only while the booking is still `BOOKED` and has no payment recorded. The total is recalculated. A booking must keep at least one service. After payment or wash start, the services cannot be edited |
| Cancel | From `BOOKED` or `IN_PROGRESS`. Needs a reason (3 to 120 characters) and the full word `yes`. The record is kept and a cancellation slip is printed |
| Find | By part of a plate (spacing ignored) or part of a customer name, including past bookings |

### Slips

Printed as plain text, 40 characters wide. The title depends on the status.

```
                CAR WASH
              BOOKING SLIP
----------------------------------------
Booking   BK-000001
Date      07 Nov 2026 20:47
Customer  Amina Hassan
Vehicle   KAA 123A (Car)
Status    Booked
----------------------------------------
Basic Wash                       KSh 300
Interior + Exterior              KSh 500
----------------------------------------
Subtotal                         KSh 800
TOTAL                            KSh 800
----------------------------------------
```

| Status | Slip title |
|---|---|
| Booked, In progress | BOOKING SLIP |
| Completed | SERVICE SUMMARY |
| Cancelled | CANCELLATION SLIP (the total is labelled "not charged") |

A completed booking prints a *service summary*. Payment receipts are separate and available in the
Payments menu. **Phone numbers are never printed on a slip.** The business name comes from
`Config.BUSINESS_NAME`.

---

## Payments, queue, appointments and daily reports

These functions are available as additional numbered choices in the existing terminal menus; no
web interface or external payment integration is introduced.

### Payments

Choose **Payments → Record a payment**, select a booking, then Cash, M-Pesa or Card. Payments may
be split into partial amounts; the system shows the remaining balance. Cash records the amount
applied, amount tendered and change. M-Pesa codes must be exactly 10 letters/digits and cannot be
reused. Card entries accept only a short approval reference or last four digits; full card numbers
are rejected and never stored. Each ledger entry gets an `RC-000001` style receipt number. The
ledger is append-only.

When a paid booking is cancelled, its net payments are recorded as a refund ledger entry. This is
an audit record only: the program does **not** contact M-Pesa, a bank, or a card processor, so the
attendant must issue the actual refund outside the application. The receipt explicitly says this.
If the application stops between saving the cancellation and saving its refund entry, the missing
refund is retried automatically at the next startup. A booking with any payment cannot have its
services edited, preventing a changed total from leaving an overpayment in the ledger.

### Queue and estimated wait

**Bookings → Queue and estimated waits** computes the active queue from booking status, appointment
times, and service durations copied when the booking was made. For legacy records without saved
durations, the current service catalog supplies the best available estimate. Estimates are approximate; they do
not account for delays, staffing, or live wash progress. The current implementation assumes one
wash bay when preventing overlapping appointments.

### Appointment scheduling

Choose **Bookings → Schedule appointment**, select the same customer, vehicle and services as a
normal booking, and enter a future local time as `YYYY-MM-DD HH:MM` (Africa/Nairobi). Active
appointment intervals cannot overlap based on their saved service durations. Appointments can be
listed and rescheduled while they are still waiting; rescheduling and service edits re-check slot
conflicts. Invalid dates such as February 30 and invalid times such as 24:00 are rejected rather
than silently adjusted. A vehicle still cannot have more than one active booking.

### Daily reports

Choose **Daily reports** to view today's summary or enter a date. The report shows bookings created,
washes completed, cancellations, completed wash value, payments received by method, refunds, and
net recorded payments. Payment totals are based on when the transaction was entered; completed
wash value is based on the completion timestamp. Dates use the local business time zone.

### Regular-customer discount

A customer becomes a **regular** after **5 paid, completed washes** and then gets **10% off every new
booking**. The numbers are fixed in `Config` (`LOYALTY_MIN_WASHES`, `LOYALTY_DISCOUNT_PERCENT`,
`LOYALTY_MIN_VISIT_TOTAL`, and a hard ceiling `LOYALTY_MAX_PERCENT`).

| Rule | Why |
|---|---|
| The percentage is calculated from saved history, never typed in | Nobody can give a discount by hand |
| A wash counts only if it is **completed**, its total was at least the minimum, and it was **paid in full** (payments minus refunds) | Cancelled, unpaid, part-paid and tiny bookings cannot be used to farm visits |
| History is tied to the customer **ID** | Renaming or changing a phone number neither gains nor loses the discount |
| The percentage and amount are stored on each booking | Old bills never change when the rules change; editing services keeps the same percentage |
| The booking screen shows subtotal, discount and total **before** you confirm; slips print the discount line | No surprises at the counter |
| The start-up data check reports any booking whose discount is above the ceiling or is not exactly percent-of-subtotal | Detects a hand-edited or damaged data file |
| Missing payment data means no discount | The feature fails closed |
| The log records only ids (`Booking#7 for Customer#1`) | No personal data in logs |

The customer detail screen shows the loyalty standing, for example `3 more paid washes to become a regular
customer (2 of 5 so far)`.

### Who is next

**Bookings > Who is next?** shows the vehicle being washed now and the vehicle to wash next (first waiting
booking: due appointments first, then walk-ins in arrival order, then future appointments), with the services
and the estimated wait. The same "Next up" lines are printed under the queue table.

---

## The storage engine

The "database" is a set of text files managed by two classes: `FileStore` (the safe file
handling) and `Repository<T>` (a generic table). Each entity only describes how one record turns
into a line of text and back; loading, saving, finding, adding, updating and removing are written
once.

### How it behaves

* **Everything is loaded into memory at startup** and every change is written to disk before the
  menu shows its confirmation.
* **Writes are crash-safe.** A save writes a temporary file, forces it to disk, then swaps it into
  place in one step. If the power fails mid-save you have either the complete old file or the
  complete new file, never half of one.
* **One backup generation.** Before each save the previous version is copied to `<file>.bak`.
* **A failed save changes nothing.** If a write fails (disk full, for example) the in-memory change
  is rolled back and the user sees "Could not save the data. Nothing was changed."
* **IDs are never reused.** Each file remembers the next ID in its header line. Deleting the
  newest record does not make its number available again.
* **Time** is stored as UTC (`2026-11-07T17:47:12Z`) and shown in the `Africa/Nairobi` time zone.
* **Missing or empty files** are treated as empty, not as errors.

### Damaged files

If a line cannot be read (edited by hand, disk problem, malformed UTF-8):

1. The original file is first copied to `data/backup/<file>.corrupt-<timestamp>`.
2. The unreadable lines are skipped, the rest loads normally, and the file is rewritten clean.
3. A notice is shown once at startup, and details (IDs only) go to the log.

Nothing is silently lost, because the untouched original is always kept.

### Integrity check

At startup the program also checks that records still point at things that exist (a vehicle whose
owner is missing, a booking whose vehicle is missing, or a payment whose booking is missing).
Problems are summarised on screen and detailed in the log. Normal use through the menus can never
create such orphans, because deletion rules archive instead of orphaning.

### One copy at a time

`data/.lock` is an exclusive lock held while the program runs. Starting a second copy (a second
terminal) prints "Another copy of this program is already using the data" and exits, so two
programs can never overwrite each other's changes. The lock is released automatically if the
program or the computer crashes.

### Line format

* One record per line, UTF-8, fields separated by `|`.
* Inside a field, `|` is written `\|`, a backslash `\\`, and line breaks `\n` and `\r`, so no
  value can ever break the format.
* The first line of each file is a header: `#v1|nextId=N|column,names`. The `v1` tag allows the
  format to change in future without guessing.

---

## Data file reference

All files are in `data/`. They are plain text and can be opened in any editor, but edit them only
with the program closed.

| File | Columns |
|---|---|
| `customers.txt` | `id`, `name`, `phone`, `createdAt`, `active` |
| `vehicles.txt` | `id`, `plate`, `type`, `customerId`, `createdAt`, `active` |
| `services.txt` | `id`, `name`, `durationMinutes`, `active`, `prices` |
| `bookings.txt` | `id`, `customerId`, `customerName`, `vehicleId`, `plate`, `type`, `items`, `subtotal`, `discountPercent`, `discountAmount`, `total`, `status`, `createdAt`, `startedAt`, `completedAt`, `cancelledAt`, `cancelReason`, `scheduledAt` |
| `payments.txt` | `id`, `bookingId`, `type`, `method`, `amount`, `tendered`, `reference`, `createdAt` |

Examples:

```
services.txt   2|Full Wash + Wax|90|true|BIKE=200;CAR=600;SUV=800;TRUCK=1200
vehicles.txt   1|KAA 123A|CAR|1|2026-11-07T17:47:12Z|true
customers.txt  1|Amina Hassan|+254712345678|2026-11-07T17:47:12Z|true
```

Notes on the formats:

* `prices` is `TYPE=amount` pairs joined by `;`. A type that is missing is not offered.
* `items` (in bookings) is `serviceId~name~price~durationMinutes` entries joined by `;`, with the name
  URL-encoded. A booking and its services live in **one line**, so they are always saved together
  in a single atomic write and can never get out of step.
* `startedAt`, `completedAt`, `cancelledAt`, `cancelReason` and `scheduledAt` are empty when they do not apply.
* Legacy booking rows with three-part service items and no `scheduledAt` still load; old duration snapshots default to zero and queue estimates use the current catalog duration.
* Payment `type` is `PAYMENT` or `REFUND`. Methods are `CASH`, `MPESA`, `CARD`, or `REFUND`; no full card data is stored.
* A booking line is checked when it is loaded: the subtotal must equal the sum of its items and the
  total must equal subtotal minus discount. A line that fails is treated as damaged (see above).

Other items in `data/`:

| Item | Purpose |
|---|---|
| `*.bak` | The previous version of each file |
| `*.tmp` | Exists only for an instant during a save |
| `.lock` | The single-instance lock |
| `backup/` | Copies of damaged files, kept before anything was overwritten |

---

## Input handling and the terminal interface

All typing goes through one class, `InputReader`, which keeps asking until the answer is usable.

* **Nothing the user types can crash the program.** Letters where a number is expected, blank
  lines, extremely long lines (limit 200 characters), control characters: all are handled and
  re-asked.
* **`0` cancels.** At any data-entry prompt, typing `0` abandons the action and returns to the
  menu. In a menu, `0` means back.
* **Enter keeps the current value** in every update prompt, and the current value is shown in
  square brackets.
* **Destructive actions** (delete, cancel booking) need the full word `yes`. Pressing Enter or
  typing anything else declines.
* **Ctrl+D / closed input** exits cleanly with a short message.
* **Tables** are aligned, amounts are right-aligned, and long lists are shown 15 rows at a time
  (Enter for more, `0` to stop).
* **Messages are short and factual**, for example `Customer saved. ID 3.` or
  `Plate KAA 123A is already registered to Amina Hassan.`
* **Money** is always shown as `KSh 1,200`.

---

## Privacy and security

Names and phone numbers are personal data (Kenya's Data Protection Act, 2019 applies), and this
repository is public. The project is built so that real data cannot leak by accident.

| Risk | What the code does |
|---|---|
| Customer data pushed to GitHub | `.gitignore` excludes `data/`, `logs/`, `out/` and `*.class`. **If `data/` was ever committed, remove it with `git rm -r --cached data` and treat the old history as exposed** |
| Phone numbers on screen | Lists show `0712 *** 678`. The full number appears only in the single-customer view |
| Phone numbers on slips | Never printed |
| Phone numbers in logs | Masked automatically before anything is written (`0712***678`) |
| Stack traces on screen | Never shown. Users see one plain sentence; technical details go to `logs/app.log` |
| Personal data in objects | `Customer`, `Vehicle` and `Booking` override `toString()` to print only an ID, so accidentally logging an object cannot leak a name or number |
| Payment card data | The system never asks for or stores full card numbers; only the method and a short approval reference or last four digits are accepted |
| File permissions | On Linux and macOS, `data/`, `logs/` and every file in them are set to owner-only (`rwx------` and `rw-------`). Windows does not support this setting; the code skips it there |
| Path tricks | Every file path is fixed in code. A user can never type a path or file name |
| Injection through the file format | Fields are escaped on write and unescaped on read, and names are validated to letters only |
| Two programs corrupting data | Exclusive lock file (see above) |
| Machine details | No absolute paths, user names or machine names appear in messages or logs |

Input is validated at the service layer, not only in the menus, so every rule holds no matter how
the code is called.

---

## Error handling and logging

Expected problems are turned into exceptions with a message that is safe to show:

| Exception | Meaning | Example message |
|---|---|---|
| `ValidationException` | Input is badly formed | `Enter a valid Kenyan number, for example 0712345678 or +254712345678.` |
| `DuplicateException` | A unique value is already taken | `That number is already registered to Amina Hassan (ID 1).` |
| `NotFoundException` | The record does not exist | `No customer with ID 9.` |
| `BusinessRuleException` | A rule forbids it | `This customer still has vehicles. Remove the vehicles first.` |
| `StorageException` | A file could not be read or written | `Could not save the data. Nothing was changed.` |

Anything unexpected is caught by `Menu.guard`, logged, and shown as *"Something went wrong. Nothing
was saved. Details were written to the log."*

**Logging** goes to `logs/app.0.log` only, never to the terminal. Files rotate at about 500 KB and
three are kept. Logging is best effort: if the log cannot be created the program still runs.

---

## Testing

There is a plain-Java test runner, so no test library has to be downloaded.

```bash
./test.sh
# or
javac --release 17 -d out $(find src test -name '*.java')
java -cp out tests.TestRunner
```

It uses throw-away folders, never your real `data/`, and exits with a non-zero code if anything
fails. It currently runs 28 groups of checks covering:

* name, phone, plate, price and duration cleaning, including bad input
* phone masking and log masking
* the file line format round trip with awkward characters (`|`, `\`, line breaks)
* customer, vehicle, service and booking rules, including every blocked case
* delete versus archive behaviour and restoring an archived customer
* the booking status flow, including illegal moves
* adding and removing services on a booking, and recalculated totals
* appointment conflicts, rescheduling, service-duration checks and queue wait estimates
* payment methods, unique M-Pesa codes, cash change, balances, receipts, reference prompt retries and cancellation refunds
* paid booking edit safeguards, interrupted-refund reconciliation and duplicate-refund protection
* local-date report totals, legacy queue estimates, and loading legacy booking rows
* snapshots: history unchanged after renaming a customer, changing a plate or changing a price
* **persistence**: data written by one run is present in the next run
* ids never reused after a delete
* damaged line backed up, skipped and not repeated on the next start
* malformed UTF-8 lines backed up and skipped
* second copy locked out
* the integrity checker finding orphaned records

---

## Configuration

Settings that are not typed by users live in `src/app/Config.java`:

| Setting | Default | Meaning |
|---|---|---|
| `BUSINESS_NAME` | `Car Wash` | Shown at startup, in the main menu and on slips |
| `ZONE` | `Africa/Nairobi` | Time zone used for display |
| `PAGE_SIZE` | 15 | Rows per page in lists |
| `MAX_INPUT_LENGTH` | 200 | Longest accepted line |
| `MAX_PRICE` | 100,000 | Highest allowed price |
| `MIN_DURATION_MINUTES` / `MAX_DURATION_MINUTES` | 5 / 480 | Allowed service duration |
| `SLIP_WIDTH` | 40 | Characters per line on slips |
| `OVERLAPPING_SERVICES` | three pairs | Which service combinations give an overlap note |

Folders are `data/` and `logs/` relative to the start folder. For automated tests they can be
redirected with the Java options `-Dcarwash.data=<folder>` and `-Dcarwash.logs=<folder>`; this is
a start-up option for developers and is not reachable from inside the program.

---

## Design decisions

* **Plain text files instead of a database.** The brief was a terminal program that behaves like
  a database without extra software. Text files can be inspected, backed up and understood by
  anyone, and the repository layer isolates the choice, so a real database could replace it later
  without touching the rules or the menus.
* **Customers and vehicles are archived, not erased, when they have history.** Receipts and
  bookings must stay readable years later.
* **Bookings keep snapshots.** History must not change when live records do.
* **Whole shillings as `long`.** No floating-point rounding errors anywhere. The future discount
  uses one rounding rule (half up, applied once to the subtotal).
* **A booking and its services share one line.** This gives atomic saves without needing
  transactions across two files.
* **Owner stored as an ID, not a name.** Two customers can share a name, and a customer can be
  renamed.
* **Immutable records.** A change creates a new object, so a half-edited object can never be
  saved by accident, and a failed save simply restores the old one.
* **Rules live in the service layer.** Menus only collect answers, which is why the tests can
  exercise every rule without a terminal.
* **No login yet.** The structure leaves room for one: the services are the only entry point to the
  data.

---

## Roadmap

Payment records, queue estimates, daily reports, and appointment scheduling are implemented and
documented above. The regular-customer discount and "who is next" are implemented (see below).
Attendant login remains a possible future improvement.

---

## Troubleshooting

| Problem | Cause and fix |
|---|---|
| `Another copy of this program is already using the data` | Another terminal is running the program. Close it. If none is, check for a stray `java` process; the lock is released when it exits |
| My data is missing after starting from another folder | `data/` is relative to where the program starts. Start it from the project root, or use `./run.sh` |
| `Data check:` appears at startup | A file had unreadable lines or records pointing at missing ones. Your original is in `data/backup/`. Details are in `logs/` |
| `Could not save the data. Nothing was changed.` | The disk is full or the folder is not writable. Free space or fix permissions, then repeat the action |
| `javac: invalid flag: --release 17` | The JDK is older than 17. Install JDK 17 or newer |
| I want to start again with empty data | Close the program and delete the `data/` folder. The standard services are recreated on the next run |
| Customer data appears on GitHub | See the first row of the privacy table |
