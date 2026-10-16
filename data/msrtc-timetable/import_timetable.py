#!/usr/bin/env python3
"""Loads an MSRTC timetable data file (e.g. pune-division.json) into a running backend.

Each board replaces the whole timetable of the depot / station with that code (PUT
/bus-timetables/depots/{id}); a depot missing from the master is created first when the
file carries a `create` block (stations only). Needs a COMPANY_ADMIN login.

  COMPANY_CODE=... EMAIL=... PASSWORD=... python3 -I import_timetable.py pune-division.json \
      --base https://host/api/v1 [--dry-run]

Credentials come from the environment, never the command line or this file.
"""
import argparse, json, os, sys, urllib.error, urllib.request


def call(base, path, method="GET", body=None, token=None):
    headers = {"Content-Type": "application/json"}
    if token:
        headers["Authorization"] = "Bearer " + token
    data = json.dumps(body).encode() if body is not None else None
    req = urllib.request.Request(base + path, method=method, data=data, headers=headers)
    try:
        return json.load(urllib.request.urlopen(req, timeout=60))
    except urllib.error.HTTPError as e:
        return json.load(e)


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("file")
    ap.add_argument("--base", required=True, help="API root, e.g. http://localhost:8080/api/v1")
    ap.add_argument("--dry-run", action="store_true")
    a = ap.parse_args()
    data = json.load(open(a.file, encoding="utf-8"))

    login = call(a.base, "/auth/login", "POST", {
        "companyCode": os.environ["COMPANY_CODE"], "email": os.environ["EMAIL"], "password": os.environ["PASSWORD"]})
    if not login.get("success"):
        sys.exit("login failed: " + str(login.get("message")))
    token = login["data"]["accessToken"]

    depots = {d["depotCode"]: d for d in call(a.base, "/bus-depots", token=token)["data"]}
    ref = next((d for d in depots.values() if d.get("divisionName", "") .lower() == data["division"].lower()), None)
    failed = 0
    for code, board in data["boards"].items():
        depot = depots.get(code)
        if depot is None and "create" in board and ref:
            c = board["create"]
            if a.dry_run:
                print(f"{code}: would create {c['depotName']}")
                continue
            r = call(a.base, "/bus-depots", "POST", {
                "depotCode": code, "depotName": c["depotName"], "depotType": c["depotType"], "branchId": None,
                "divisionId": ref["divisionId"], "parentDepotId": None, "address": None, "city": c.get("city"),
                "district": c.get("district"), "state": c.get("state"), "pincode": None,
                "contactPerson": None, "contactMobile": None}, token)
            if not r.get("success"):
                print(f"{code}: create failed: {r.get('message')}"); failed += 1; continue
            depot = r["data"]
        if depot is None:
            print(f"{code}: depot not found in master, skipped"); failed += 1; continue
        if a.dry_run:
            print(f"{code}: would save {len(board['rows'])} rows to {depot['depotName']}")
            continue
        r = call(a.base, f"/bus-timetables/depots/{depot['id']}", "PUT", {"rows": board["rows"]}, token)
        ok = r.get("success")
        print(f"{code}: {'OK' if ok else r.get('message')} ({len(board['rows'])} rows) {depot['depotName']}")
        failed += 0 if ok else 1
    sys.exit(1 if failed else 0)


if __name__ == "__main__":
    main()
