#!/usr/bin/env python3
"""Validate Android versions and provide release metadata to GitHub Actions."""
import argparse
import os
import re
from pathlib import Path


def read_version(path):
    values = {}
    for line in Path(path).read_text().splitlines():
        line = line.strip()
        if not line or line.startswith('#'):
            continue
        key, separator, value = line.partition('=')
        if not separator or key.strip() in values:
            raise ValueError(f'Invalid or duplicate entry in {path}: {line}')
        values[key.strip()] = value.strip()
    name = values.get('VERSION_NAME', '')
    code = values.get('VERSION_CODE', '')
    if not re.fullmatch(r'(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)\.(0|[1-9][0-9]*)', name):
        raise ValueError('VERSION_NAME must use major.minor.patch, for example 0.2.0')
    if not re.fullmatch(r'[1-9][0-9]*', code) or int(code) > 2100000000:
        raise ValueError('VERSION_CODE must be an integer between 1 and 2100000000')
    return name, int(code)


def release_metadata(current, previous=None):
    name, code = read_version(current)
    changed = True
    if previous and Path(previous).is_file():
        old_name, old_code = read_version(previous)
        changed = (name, code) != (old_name, old_code)
        if changed:
            if code <= old_code:
                raise ValueError('Increase VERSION_CODE when publishing a new version')
            if tuple(map(int, name.split('.'))) <= tuple(map(int, old_name.split('.'))):
                raise ValueError('Increase VERSION_NAME when publishing a new version')
    return {'name': name, 'code': str(code), 'tag': f'v{name}', 'changed': str(changed).lower()}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--file', default='version.properties')
    parser.add_argument('--previous')
    args = parser.parse_args()
    try:
        metadata = release_metadata(args.file, args.previous)
    except (ValueError, OSError) as error:
        parser.error(str(error))
    output = '\n'.join(f'{key}={value}' for key, value in metadata.items()) + '\n'
    print(output, end='')
    if os.environ.get('GITHUB_OUTPUT'):
        with open(os.environ['GITHUB_OUTPUT'], 'a') as target:
            target.write(output)
