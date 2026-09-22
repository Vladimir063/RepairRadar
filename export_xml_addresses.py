"""Export a random sample of active GAR addresses using streaming XML parsing."""
import argparse
import csv
import random
import xml.etree.ElementTree as ET
from pathlib import Path


def records(path):
    context = ET.iterparse(path, events=("start", "end"))
    _, root = next(context)
    for event, elem in context:
        if event == "end" and elem is not root:
            yield elem.attrib
            root.clear()


def source(folder, name):
    paths = list(folder.glob(f"{name}_2*.XML"))
    if len(paths) != 1:
        raise ValueError(f"Expected one {name} XML, found {len(paths)}")
    return paths[0]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--folder", type=Path, default=Path("77"))
    parser.add_argument("--output", type=Path, default=Path("77/moscow_random_50.csv"))
    parser.add_argument("--count", type=int, default=50)
    parser.add_argument("--seed", type=int)
    args = parser.parse_args()
    if args.count < 1:
        parser.error("count must be positive")
    rng = random.Random(args.seed)
    objects = {
        a["OBJECTID"]: dict(a)
        for a in records(source(args.folder, "AS_ADDR_OBJ"))
        if a.get("ISACTIVE") == "1" and a.get("ISACTUAL") == "1"
    }
    houses = {
        a["OBJECTID"]: dict(a)
        for a in records(source(args.folder, "AS_HOUSES"))
        if a.get("ISACTIVE") == "1" and a.get("ISACTUAL") == "1"
        and a.get("HOUSENUM", "").strip()
    }
    print(f"Active address objects: {len(objects)}; numbered houses: {len(houses)}", flush=True)
    sample, seen = [], set()
    eligible = 0
    for a in records(source(args.folder, "AS_ADM_HIERARCHY")):
        oid = a.get("OBJECTID")
        if a.get("ISACTIVE") != "1" or a.get("REGIONCODE") != "77" or oid not in houses:
            continue
        if oid in seen:
            continue
        ancestors = [objects[p] for p in a.get("PATH", "").split(".")[:-1] if p in objects]
        street = next((p for p in reversed(ancestors) if p.get("LEVEL") == "8" and p.get("NAME", "").strip()), None)
        if street is None:
            continue
        seen.add(oid)
        eligible += 1
        row = (houses[oid], street, ancestors)
        if len(sample) < args.count:
            sample.append(row)
        else:
            index = rng.randrange(eligible)
            if index < args.count:
                sample[index] = row
    if len(sample) != args.count:
        raise ValueError(f"Only {len(sample)} eligible addresses found")
    rng.shuffle(sample)
    fields = ["Город", "Адресная_иерархия", "Улица", "Дом", "Доп_номер_1", "Тип_доп_номера_1", "Доп_номер_2", "Тип_доп_номера_2", "Тип_дома", "Улица_OBJECTID", "Улица_OBJECTGUID", "Дом_OBJECTID", "Дом_OBJECTGUID"]
    with args.output.open("w", encoding="utf-8-sig", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=fields, delimiter=";")
        writer.writeheader()
        for house, street, ancestors in sample:
            writer.writerow(dict(zip(fields, [
                "Москва", ", ".join(f"{p['TYPENAME']} {p['NAME']}" for p in ancestors),
                f"{street['TYPENAME']} {street['NAME']}", house["HOUSENUM"],
                house.get("ADDNUM1", ""), house.get("ADDTYPE1", ""),
                house.get("ADDNUM2", ""), house.get("ADDTYPE2", ""), house.get("HOUSETYPE", ""),
                street["OBJECTID"], street.get("OBJECTGUID", ""),
                house["OBJECTID"], house.get("OBJECTGUID", ""),
            ])))
    with args.output.open(encoding="utf-8-sig", newline="") as f:
        rows = list(csv.DictReader(f, delimiter=";"))
    assert len(rows) == args.count
    assert len({r["Дом_OBJECTID"] for r in rows}) == args.count
    assert all(all(r[k] for k in ("Улица", "Дом", "Улица_OBJECTID", "Улица_OBJECTGUID", "Дом_OBJECTID", "Дом_OBJECTGUID")) for r in rows)
    print(f"Exported and validated {len(rows)} unique addresses from {eligible} eligible houses: {args.output}")


if __name__ == "__main__":
    main()
