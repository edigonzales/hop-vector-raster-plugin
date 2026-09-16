#!/usr/bin/env python3
"""Regression checks for metadata and graph rewiring of file-based raster pipelines."""
from pathlib import Path
import runpy
import xml.etree.ElementTree as E

migrate=runpy.run_path(str(Path(__file__).with_name('migrate-raster-values.py')))['migrate']
root=E.fromstring('''<pipeline><order>
<hop><from>rows</from><to>clip</to><enabled>Y</enabled></hop>
<hop><from>clip</from><to>next</to><enabled>Y</enabled></hop>
<hop><from>clip</from><to>reject</to><enabled>Y</enabled></hop></order>
<transform><name>rows</name><type>RowGenerator</type></transform>
<transform><name>clip</name><type>SOGIS_RASTER_CLIP</type><source>${INPUT}</source>
<sourceField>N</sourceField><band>2</band><output>destination</output><outputField>Y</outputField>
<prefix>clip_</prefix><overwrite>N</overwrite></transform>
<transform><name>next</name><type>Dummy</type></transform>
<transform><name>reject</name><type>Dummy</type></transform>
<transform_error_handling><error><source_transform>clip</source_transform>
<target_transform>reject</target_transform><is_enabled>Y</is_enabled>
<descriptions_valuename>error_description</descriptions_valuename></error></transform_error_handling>
</pipeline>''')
result=migrate(root)
nodes={t.findtext('name'):t for t in result.findall('transform')}
edges={(h.findtext('from'),h.findtext('to')) for h in result.findall('order/hop')}
assert nodes['clip'].findtext('bands')=='2'
reader=next(t for t in nodes.values() if t.findtext('type')=='SOGIS_RASTER_READER')
writer=next(t for t in nodes.values() if t.findtext('type')=='SOGIS_RASTER_WRITER')
assert reader.findtext('source')=='${INPUT}' and reader.findtext('sourceField')=='N'
assert writer.findtext('output')=='destination' and writer.findtext('outputField')=='Y'
errors=result.findall('transform_error_handling/error')
assert len(errors)==3
for error in errors:
    source,target=error.findtext('source_transform'),error.findtext('target_transform')
    assert (source,target) in edges
    assert error.findtext('descriptions_valuename')=='error_description'
    if source==reader.findtext('name'):
        assert target=='reject'
    else:
        assert nodes[target].findtext('fields/remove/name')==nodes['clip'].findtext('rasterField')
        assert (target,'reject') in edges
positions=[(t.findtext('GUI/xloc'),t.findtext('GUI/yloc')) for t in nodes.values()]
assert len(set(positions))==len(positions)
assert not any(t.findtext('type')=='SOGIS_RASTER_CLIP' for t in nodes.values())
print('Raster migration: field bindings, variables, band, Error Hops and distinct layout OK')
