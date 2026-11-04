# Data sources

Everything the app ships is either public domain, CC0, or the OpenSky Network under their terms.
No asset is extracted from another app.

| What | Where it ships | Source | Licence |
| --- | --- | --- | --- |
| Airports (name, IATA/ICAO code, city, country, coordinates) | `app/src/main/assets/airports.dat` | [OurAirports](https://ourairports.com/data/) — `airports.csv` + `countries.csv` | Public domain |
| Airlines (name, IATA → ICAO designator) | `app/src/main/assets/airlines.dat` | [Wikidata](https://www.wikidata.org) SPARQL: airlines with P229 (IATA) and P230 (ICAO) | CC0 |
| Live aircraft positions | fetched at runtime from `https://opensky-network.org/api/states/all` | [OpenSky Network](https://opensky-network.org) | See their [terms](https://openskynetwork.org/about/terms-of-use) — commercial use needs a licence, research use requires a citation |
| Airline marks | *not shipped* | — | Airline logos are trademarks; the UI shows the IATA code in a tile instead |

## Regenerating the bundled datasets

```bash
curl -sSL -o tools/raw/ourairports.csv https://davidmegginson.github.io/ourairports-data/airports.csv
curl -sSL -o tools/raw/ourairports_countries.csv https://davidmegginson.github.io/ourairports-data/countries.csv
curl -sS -A 'Skyage/0.1' -H 'Accept: application/sparql-results+json' \
  --data-urlencode query@tools/airlines.rq https://query.wikidata.org/sparql \
  -o tools/raw/wikidata_airlines.json
python3 tools/build_dataset.py
```

`tools/build_dataset.py` writes the `|`-delimited files and prints the row counts; the column order
is documented in `data/DatasetParser.kt` and asserted by `DatasetParserTest`.

Choosing rows:

* **Airports** — every OurAirports entry with a three-letter IATA code (what a user can type).
  `relevance` is derived: `large_airport` 300, `medium_airport` 200, `small_airport` 100, plus 50 for
  scheduled airline service, so hubs rank above grass strips in search. OurAirports carries no
  timezone, and the app does not display one.
* **Airlines** — one row per IATA designator, keeping the ICAO prefix that turns `UA123` into the
  ADS-B callsign `UAL123`. Where several Wikidata items share a code (Ted vs United, Song vs Delta)
  the entry with the most sitelinks wins; airlines without an English label (Korean Air) fall back
  to another latin-script label rather than a bare Q-id.

## Attribution in the app

`HomeScreen` renders a `DataCredit` line under the flight list, naming OpenSky, OurAirports and
Wikidata. OpenSky also asks research users to cite:

> Matthias Schäfer, Martin Strohmeier, Vincent Lenders, Ivan Martinovic and Matthias Wilhelm.
> "Bringing Up OpenSky: A Large-scale ADS-B Sensor Network for Research".
> In Proceedings of the 13th IEEE/ACM International Symposium on Information Processing in Sensor
> Networks (IPSN), pages 83–94, April 2014.

## Live data caveats

ADS-B positions are radio observations: they can lag, drift, or go missing, and they say nothing
about schedules, gates or delays. The app only claims what the feed proves, which is why progress
is withheld when the aircraft is more than 400 km off the great-circle route.
