# Cake photo models

On-device models behind **Order → Suggest from photo** (`scan/CakeReader.kt`):

| File | What it does |
|---|---|
| `cake_parts.tflite` | Finds toppers (simple / complex / 3D character) and edible prints |
| `cake_tiers.tflite` | 1, 2 or 3+ tiers |
| `cake_print.tflite` | Edible print yes / no |

Copy the trained files to `app/src/main/assets/`. Without them the app still suggests icing colours.

## Retrain
Photos aren't committed. `labels.json` + `proposals.json` hold every label; photo names start with
`own_` (bakery's own, from Drive) or `ov_` (Openverse, commercial-use licences; credits in `credits.csv`).

1. Put 1024px JPEGs in `data/images/` (`resize_own.py`, `fetch.py`, then `dedupe.py`, `cakefilter.py`).
2. `propose.py data` draws candidate boxes (OWLv2), `sheets.py data` renders review pages,
   `label.py data pNNN "0=2,1s,3c 1=1,P …"` saves them: tiers, then box + s/c/h(3D character)/p, `P` = has a print.
3. `docker build --platform linux/amd64 -t cake-trainer .` then
   `docker run --rm --platform linux/amd64 -v $PWD/data:/data -v $PWD:/tools -v $PWD/out:/out cake-trainer python /tools/train.py /data /out`
