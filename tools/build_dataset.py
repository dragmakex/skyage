#!/usr/bin/env python3
"""Compile the app's reference data from public sources into the flat datasets it ships.

The airports are OurAirports (public domain) and the airlines are Wikidata (CC0), both fetched
into `tools/raw/`:

    curl -sSL -o tools/raw/ourairports.csv \\
        https://davidmegginson.github.io/ourairports-data/airports.csv
    curl -sSL -o tools/raw/ourairports_countries.csv \\
        https://davidmegginson.github.io/ourairports-data/countries.csv
    curl -sS -A 'Skyage/0.1' -H 'Accept: application/sparql-results+json' \\
        --data-urlencode query@tools/airlines.rq https://query.wikidata.org/sparql \\
        -o tools/raw/wikidata_airlines.json

Parsing JSON at runtime with org.json cost ~17 s on a slow device, so rows are flattened here into
pipe-delimited lines with a fixed column order (see `DatasetParser.kt`).

    python3 tools/build_dataset.py
"""
import csv
import json
import pathlib
import re

ROOT = pathlib.Path(__file__).resolve().parent
RAW = ROOT / "raw"
OUT = ROOT.parent / "app/src/main/assets"

# Column order must match the parsers in DatasetParser.kt.
AIRPORT_COLUMNS = ["name", "iata", "icao", "city", "country", "lat", "lon", "relevance"]
AIRLINE_COLUMNS = ["name", "iata", "icao"]

# Big hubs must sort above grass strips in search; "scheduled" adds the airline-service bonus.
TYPE_RANK = {"large_airport": 300, "medium_airport": 200, "small_airport": 100}
SCHEDULED_BONUS = 50
ICAO_RE = re.compile(r"^[A-Z]{4}$")
IATA_AIRPORT_RE = re.compile(r"^[A-Z]{3}$")
IATA_AIRLINE_RE = re.compile(r"^[A-Z0-9]{2}$")
ICAO_AIRLINE_RE = re.compile(r"^[A-Z]{3}$")
QID_RE = re.compile(r"^Q\d+$")


def cell(value):
    if value is None:
        return ""
    text = str(value).replace("|", " ").replace("\n", " ").replace("\r", " ")
    return text.strip()


def load_countries():
    with (RAW / "ourairports_countries.csv").open(encoding="utf-8", newline="") as fh:
        return {row["code"]: row["name"] for row in csv.DictReader(fh)}


def flatten_airports(countries):
    """Airports the user can actually type: those with an IATA code."""
    rows = []
    with (RAW / "ourairports.csv").open(encoding="utf-8", newline="") as fh:
        for a in csv.DictReader(fh):
            iata = cell(a.get("iata_code")).upper()
            if not IATA_AIRPORT_RE.match(iata):
                continue
            try:
                lat = float(a["latitude_deg"])
                lon = float(a["longitude_deg"])
            except (KeyError, TypeError, ValueError):
                continue
            icao = cell(a.get("icao_code")).upper() or cell(a.get("gps_code")).upper()
            relevance = TYPE_RANK.get(a.get("type"), 0)
            if a.get("scheduled_service") == "yes":
                relevance += SCHEDULED_BONUS
            rows.append(
                [
                    cell(a.get("name")),
                    iata,
                    icao if ICAO_RE.match(icao) else "",
                    cell(a.get("municipality")),
                    countries.get(a.get("iso_country"), ""),
                    f"{lat:.4f}",
                    f"{lon:.4f}",
                    str(relevance),
                ]
            )
    rows.sort(key=lambda r: (-int(r[7]), r[1]))
    return rows


def flatten_airlines():
    """One row per IATA designator: the ICAO prefix is what turns UA123 into UAL123."""
    data = json.loads((RAW / "wikidata_airlines.json").read_text())["results"]["bindings"]
    best = {}
    for row in data:
        iata = cell(row.get("iata", {}).get("value")).upper()
        icao = cell(row.get("icao", {}).get("value")).upper()
        name = cell(row.get("itemLabel", {}).get("value"))
        if not iata or not ICAO_AIRLINE_RE.match(icao) or not name or QID_RE.match(name):
            continue
        # A code shared by several airlines (Ted vs United, Song vs Delta) keeps the one with the
        # most sitelinks, which is reliably the operating airline rather than a retired brand.
        sitelinks = int(cell(row.get("sitelinks", {}).get("value")) or 0)
        if iata not in best or sitelinks > best[iata][1]:
            best[iata] = (name, sitelinks, [name, iata, icao])
    return [v[2] for _, v in sorted(best.items())]


def write(name, rows, columns):
    path = OUT / name
    lines = ["|".join(cell(c) for c in row) for row in rows]
    path.write_text("#" + "|".join(columns) + "\n" + "\n".join(lines) + "\n", encoding="utf-8")
    print(f"{path.name}: {len(lines)} rows, {path.stat().st_size // 1024} KB")


def main():
    write("airports.dat", flatten_airports(load_countries()), AIRPORT_COLUMNS)
    write("airlines.dat", flatten_airlines(), AIRLINE_COLUMNS)


if __name__ == "__main__":
    main()
