# HHO — Household Objects

HHO is a **self-hosted home inventory and organization system**. You run it on your own
hardware (a Raspberry Pi, a NAS, a home server), and it helps a household track what it owns:
what's in the garage, which box has the holiday lights, whether the drill is still under
warranty. No cloud account, no subscription, no external service required to operate it.

HHO ships as two coupled products :

- **HHO Server** ( `https://github.com/ankek/household-objects-srv/server/`) — a single Go binary / container. SQLite storage, an embedded
  Vue 3 web UI, and a documented public REST API. This is the whole product for a browser-only
  household.
- **HHO Android app** (this one repository,`/android-app/`) — native Kotlin + Jetpack Compose, offline-first,
  barcode-driven stock control for standing in front of a shelf with no signal. Syncs to the
  server. *(Not yet functional — see [Project status](#project-status) below.)*

Licensed **AGPL-3.0-only**; see [License](#license).

## Project status
This project is under development — the HHO Server MVP

## License

HHO and all components are licensed under the **GNU Affero General Public License v3.0 only** (`AGPL-3.0-only`) — see
[`LICENSE`](LICENSE). The AGPL's network-use clause (section 13) is the deliberate reason it was
chosen over plain GPL: if you run a modified version of HHO as a network service, you must offer
its source to your users. Sorry, bro. Sad but true.