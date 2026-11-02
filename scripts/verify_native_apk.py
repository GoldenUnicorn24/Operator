"""Audit the actual package instead of inferring native-only behavior from source."""
import sys, zipfile, hashlib, json
from pathlib import Path
path=Path(sys.argv[1])
with zipfile.ZipFile(path) as package:
    names=package.namelist()
    forbidden=[n for n in names if n.lower().endswith(('.html','.htm','.js','.css'))]
    assert not forbidden, f'Web documents in APK: {forbidden}'
    assert not any(b'android/webkit/WebView' in package.read(n) for n in names if n.endswith('.dex')), 'WebView class reference in dex'
    for name in ['states.geojson','hamburg-districts.json','hamburg-roads.json','art/portrait-atlas.png','art/gear-atlas.png','art/vehicle-atlas.png','art/vehicle-extras-atlas.png','geo/germany-base.json']:
        assert f'assets/{name}' in names, f'Missing offline resource {name}'
    for code in ['BW','BY','BE','BB','HB','HH','HE','MV','NI','NW','RP','SL','SN','ST','SH','TH']:
        assert f'assets/geo/roads-DE-{code}.json' in names, f'Missing offline road network DE-{code}'
    assert len([n for n in names if n.startswith('assets/audio/') and n.endswith('.wav')])>=10
print(json.dumps({'apk':str(path),'bytes':path.stat().st_size,'sha256':hashlib.sha256(path.read_bytes()).hexdigest(),'webDocuments':forbidden,'nativeOnly':True},indent=2))
