# Bundled font licences

Both faces are [SIL Open Font Licence 1.1](https://openfontlicense.org), which permits
redistribution inside an application.

| File | Face | Source |
|---|---|---|
| `OFL-Inter.txt` | Inter | [github.com/rsms/inter](https://github.com/rsms/inter), via Google Fonts |
| `OFL-InstrumentSerif.txt` | Instrument Serif | Instrument, via Google Fonts |

Both are **subset** copies, not the originals:

- `res/font/inter.ttf` — optical-size axis pinned to 16, weight axis kept variable
  (100–900), glyphs reduced to ASCII + Latin-1 + the punctuation the UI uses. 876KB → 73KB.
- `res/font/instrument_serif.ttf` — same glyph coverage, static. 70KB → 32KB.

The subsetting script is not checked in because it is run once. To redo it: `pyftsubset`
with `--unicodes=U+0020-007E,U+00A0-00FF,U+2010-2015,U+2018-201F,U+2026,U+2030,U+2032-2033,U+00D7,U+2212`
and `--layout-features=kern,liga,tnum,lnum,ccmp,mark,mkmk`. **`tnum` must survive** — tabular
figures are the reason the type scale looks composed, see docs/design-system.md.

The OFL requires the licence to travel with the font, which is what this directory is for.
