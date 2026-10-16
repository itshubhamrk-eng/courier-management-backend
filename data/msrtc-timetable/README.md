# MSRTC timetable data

Per-division timetable boards read **by eye** from the scanned PDFs on
msrtc.maharashtra.gov.in/GeneralPages/Timetabel.aspx (no text layer). Not verified: expect
digit errors and omitted times. Each JSON's `source` field says what was skipped and why.

| File | Boards | Notes |
|---|---|---|
| `pune-division.json` | 25 | |
| `bhandara-division.json` | 4 of 6 | Bhandara station and Sakoli skipped (illegible) |
| `jalna-division.json` | 7 of 8 | Badnapur has no station in the master |
| `yavatmal-division.json` | 3 of 9 | only the legible pages |
| `chhatrapati-sambhajinagar-division.json` | 3 of 8 | "every N min" rows expanded to explicit times |
| `wardha-division.json` | 3 of 9 | Ashti, Pulgaon, Jam (Shivai routes only); Wardha, Selu, Hinganghat, Deoli skipped |

Load into a running backend (needs a COMPANY_ADMIN login; each board replaces the whole timetable
of that depot/station):

    COMPANY_CODE=... EMAIL=... PASSWORD=... python3 -I import_timetable.py <file>.json \
        --base http://host/api/v1 [--dry-run]

## Copying rows between databases with SQL

`bus_timetable_departures.dep_time` / `arrival.arr_time` are `TIME` columns written through the
JVM's default time zone. A backend JVM on IST (local dev) stores each time **5h30 early**; the
prod JVM runs on UTC and stores them as-is. When copying rows from a local DB to prod with SQL,
add 5h30 (mod 24h) to every time, e.g. `SEC_TO_TIME((TIME_TO_SEC(t)+19800)%86400)`, and copy the
`bus_timetable_departures` / `bus_timetable_arrivals` rows as well as `bus_timetable_entries`
(copying only the entries leaves places with no times). Copy only live entries (`deleted = 0`).
