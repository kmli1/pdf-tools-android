"""Offline structural check; does not replace an Android build or device test."""
from pathlib import Path
import hashlib
import re
import tomllib
import xml.etree.ElementTree as ET

root = Path(__file__).resolve().parents[1]
for path in (root / 'app/src/main').rglob('*.xml'):
    ET.parse(path)
with (root / 'gradle/libs.versions.toml').open('rb') as file:
    catalog = tomllib.load(file)
assert all('+' not in value for value in catalog['versions'].values())
resources = ET.parse(root / 'app/src/main/res/values/strings.xml').getroot()
names = {item.attrib['name'] for item in resources}
assert len(names) == len(resources), 'Duplicate resource names'
for path in (root / 'app/src/main/java').rglob('*.kt'):
    for resource in re.findall(r'R\.string\.(\w+)', path.read_text()):
        assert resource in names, (path.name, resource)
manifest = ET.parse(root / 'app/src/main/AndroidManifest.xml').getroot()
assert not manifest.findall('uses-permission'), 'Unexpected app permissions'
wrapper = root / 'gradle/wrapper/gradle-wrapper.jar'
expected = wrapper.with_suffix('.jar.sha256').read_text().strip()
assert hashlib.sha256(wrapper.read_bytes()).hexdigest() == expected
assert (root / 'gradlew').exists() and (root / 'gradlew.bat').exists()
print(f'PASS: XML, version catalog, {len(names)} string resources, permission manifest, Gradle Wrapper checksum.')
