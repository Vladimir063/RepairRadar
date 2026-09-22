"""Join 1C Fast Infoset house records to address dictionaries (Java + Python)."""
import argparse
import base64
import csv
import random
from pathlib import Path
import subprocess
import tempfile
import uuid

ROOT = Path(__file__).resolve().parent
ZERO = 'AAAAAAAAAAAAAAAAAAAAAA=='


def read_fi(name, limit, directory):
    subprocess.run([
        'java', '--class-path', str(ROOT / 'lib/FastInfoset-2.1.1.jar'),
        str(ROOT / 'FiToCsv.java'), '--limit', str(limit),
        '--output-dir', str(directory), str(ROOT / (name + '.FI')),
    ], check=True, capture_output=True)
    with (directory / f'{name}_first_{limit}.csv').open(encoding='utf-8-sig', newline='') as f:
        return list(csv.DictReader(f))


def decode_building(value, statuses):
    if len(value) < 25:
        raise ValueError(f'Invalid building record: {value!r}')
    binary_id = base64.b64decode(value[:24], validate=True)
    if len(binary_id) != 16:
        raise ValueError('Building identifier must contain 16 bytes')
    fields = value[25:].split('~')
    if len(fields) > 4:
        raise ValueError(f'Unsupported building fields: {value!r}')
    fields += [''] * (4 - len(fields))
    number, corpus, structure_type, structure = fields
    kind = statuses.get(('ESTSTAT', value[24]))
    if kind is None and value[24] != '0':
        raise ValueError(f'Unknown building type: {value[24]}')
    structure_label = statuses.get(('STRSTAT', structure_type), '')
    if structure and not structure_label:
        raise ValueError(f'Unknown structure type: {structure_type}')
    parts = []
    if number:
        if kind is None:
            raise ValueError('Number has no building type')
        parts.append(f'{kind} {number}')
    if corpus:
        parts.append(f'корп. {corpus}')
    if structure:
        parts.append(f'{structure_label} {structure}')
    if not parts:
        raise ValueError('Building has no address number')
    return str(uuid.UUID(bytes=binary_id)), number, corpus, structure_label, structure, ', '.join(parts)


def address_chain(key, objects):
    chain, seen = [], set()
    while key and key != ZERO:
        if key in seen:
            raise ValueError('Cycle in address hierarchy')
        seen.add(key)
        if key not in objects:
            raise ValueError(f'Missing address object: {key}')
        obj = objects[key]
        chain.append(obj)
        key = obj['PARENTGUID']
        if not key or key == ZERO:
            key = obj.get('PARENTGUIDMUN', '')
    return list(reversed(chain))


def export(limit, random_sample=False):
    with tempfile.TemporaryDirectory(prefix='fi-addresses-') as temp:
        directory = Path(temp)
        objects = {r['AOGUID']: r for r in read_fi('77_ADDROBJ', 2147483647, directory)}
        extras = {r['EXTRAGUID']: r for r in read_fi('77_EXTRAACT', 2147483647, directory)}
        statuses = {(r['TYPE'], r['ID']): r['KEY'] for r in read_fi('ADDRSTATUS', 2147483647, directory)}
        houses = read_fi('77_HOUSE', 2147483647 if random_sample else limit, directory)
    if random_sample:
        excluded = set()
        previous = ROOT / '77_HOUSE_addresses_first_10.csv'
        if previous.exists():
            with previous.open(encoding='utf-8-sig', newline='') as f:
                excluded = {r['Идентификатор дома'] for r in csv.DictReader(f, delimiter=';')}
        candidates = {}
        for group in houses:
            for value in group['BUILDINGS'].split('\t'):
                house_id = str(uuid.UUID(bytes=base64.b64decode(value[:24], validate=True)))
                if house_id not in excluded:
                    candidates.setdefault(house_id, (group, value))
        selected = random.SystemRandom().sample(list(candidates.values()), limit)
        houses = [dict(group, BUILDINGS=value) for group, value in selected]
        print(f'Random sample from {len(candidates)} distinct buildings; excluded {len(excluded)} previous IDs')
    rows = []
    for group in houses:
        chain = address_chain(group['AOGUID'], objects)
        labels = [f"{o['SHORTNAME']} {o['FORMALNAME']}" for o in chain]
        postal = extras.get(group['EXTRAGUID'], {}).get('POSTALCODE', '')
        if postal == '0':
            postal = ''
        for value in group['BUILDINGS'].split('\t'):
            house_id, number, corpus, structure_type, structure, building = decode_building(value, statuses)
            rows.append({
                'Полный адрес': ', '.join(labels + [building]),
                'Индекс': postal,
                'Регион': next((o['FORMALNAME'] for o in chain if o['AOLEVEL'] == '1'), ''),
                'Адрес до дома': ', '.join(labels),
                'Дом': number,
                'Корпус': corpus,
                'Тип строения': structure_type,
                'Строение': structure,
                'Идентификатор дома': house_id,
            })
            if len(rows) == limit:
                break
        if len(rows) == limit:
            break
    if len(rows) != limit:
        raise ValueError(f'Only {len(rows)} buildings found, requested {limit}')
    mode = 'random' if random_sample else 'first'
    output = ROOT / f'77_HOUSE_addresses_{mode}_{limit}.csv'
    with output.open('w', encoding='utf-8-sig', newline='') as f:
        writer = csv.DictWriter(f, fieldnames=list(rows[0]), delimiter=';')
        writer.writeheader()
        writer.writerows(rows)
    print(f'{output}: {len(rows)} addresses')


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--limit', type=int, default=10)
    parser.add_argument('--random', action='store_true', help='Sample distinct buildings from the whole file, excluding the first 10 export')
    args = parser.parse_args()
    if args.limit < 1:
        parser.error('--limit must be positive')
    export(args.limit, args.random)
