#!/usr/bin/env python3
"""Convert file-based raster transforms into explicit value pipelines. Never edits the input."""
import argparse
import copy
from pathlib import Path
import xml.etree.ElementTree as ET

OPERATIONS = {
    'SOGIS_RASTER_CLIP':'CLIP',
    'SOGIS_RASTER_REPROJECT':'REPROJECT',
    'SOGIS_RASTER_ZONAL_STATS':'STATS',
    'GEOTOOLS_RASTER_CLIP':'CLIP',
    'GEOTOOLS_RASTER_REPROJECT':'REPROJECT',
    'GEOTOOLS_RASTER_ZONAL_STATS':'STATS',
    'SOGEO_RASTER_CLIP':'CLIP',
    'SOGEO_RASTER_ZONAL_STATS':'STATS',
}
CURRENT_TYPES = {
    'CLIP':'SOGIS_RASTER_VALUE_CLIP',
    'REPROJECT':'SOGIS_RASTER_VALUE_REPROJECT',
    'STATS':'SOGIS_RASTER_VALUE_ZONAL_STATS',
}

def layout(root):
    """Give converted pipelines a readable left-to-right layout without overlapping nodes."""
    nodes={t.findtext('name'):t for t in root.findall('transform')}
    parents={name:set() for name in nodes}
    for hop in root.findall('order/hop'):
        source,target=hop.findtext('from'),hop.findtext('to')
        if source in nodes and target in nodes and hop.findtext('enabled','Y')=='Y':
            parents[target].add(source)
    levels={};pending=set(nodes)
    while pending:
        ready=[name for name in nodes if name in pending and parents[name] <= levels.keys()]
        if not ready:ready=[name for name in nodes if name in pending]
        for name in ready:
            levels[name]=max((levels.get(parent,-1)+1 for parent in parents[name]),default=0)
            pending.remove(name)
    lanes={}
    for name,t in nodes.items():
        level=levels[name];lane=lanes.get(level,0);lanes[level]=lane+1
        gui=t.find('GUI')
        if gui is None:gui=ET.SubElement(t,'GUI')
        for key,value in [('xloc',120+220*level),('yloc',120+140*lane),('draw','Y')]:
            element=gui.find(key)
            if element is None:element=ET.SubElement(gui,key)
            element.text=str(value)

def migrate(root):
    names={t.findtext('name') for t in root.findall('transform')}
    order=root.find('order')
    if order is None: order=ET.SubElement(root,'order')
    for t in list(root.findall('transform')):
        old=t.findtext('type')
        operation=OPERATIONS.get(old)
        if operation is None: continue
        name=t.findtext('name')
        field='__raster_value_'+str(len(names))
        def unique(s):
            candidate=s
            while candidate in names: candidate+=' new'
            names.add(candidate)
            return candidate
        def node(kind,title):
            n=ET.Element('transform')
            for k,v in [('name',unique(title)),('type',kind),('copies',t.findtext('copies','1')),('distribute',t.findtext('distribute','Y')),('version','1'),('rasterField',field)]:ET.SubElement(n,k).text=v
            gui=copy.deepcopy(t.find('GUI'))
            if gui is not None:n.append(gui)
            return n
        reader=node('SOGIS_RASTER_READER',name+' source')
        for key in ('source','sourceField'):
            original=t.find(key)
            if original is not None:reader.append(copy.deepcopy(original))
        t.find('type').text=CURRENT_TYPES[operation]
        ET.SubElement(t,'version').text='1'
        ET.SubElement(t,'rasterField').text=field
        if operation=='CLIP':ET.SubElement(t,'bands').text=t.findtext('band','1')
        writer=None
        if operation!='STATS':
            writer=node('SOGIS_RASTER_WRITER',name+' write')
            for key in ('output','outputField','overwrite','prefix'):
                original=t.find(key)
                if original is not None:writer.append(copy.deepcopy(original))
        cleanup=node('SelectValues',name+' remove raster value')
        fields=ET.SubElement(cleanup,'fields');ET.SubElement(fields,'select_unspecified').text='Y'
        ET.SubElement(ET.SubElement(fields,'remove'),'name').text=field
        errors=root.find('transform_error_handling')
        if errors is not None:
            for err in errors:
                if err.findtext('target_transform')==name:err.find('target_transform').text=reader.findtext('name')
        original_errors=[] if errors is None else [err for err in errors if err.findtext('source_transform')==name]
        error_targets={err.findtext('target_transform') for err in original_errors}
        for hop in list(order):
            if hop.findtext('to')==name:hop.find('to').text=reader.findtext('name')
            if hop.findtext('from')==name and hop.findtext('to') not in error_targets:hop.find('from').text=cleanup.findtext('name')
        chain=[reader,t]+([writer] if writer is not None else [])+[cleanup]
        for a,b in zip(chain,chain[1:]):
            h=ET.SubElement(order,'hop');ET.SubElement(h,'from').text=a.findtext('name');ET.SubElement(h,'to').text=b.findtext('name');ET.SubElement(h,'enabled').text='Y'
        for n in chain:
            if n is not t:root.append(n)
        # Error Hops are graph edges as well as metadata. Strip the helper Raster field
        # from operation/consumer errors, so their rows still match errors from the Reader.
        for err in original_errors:
            target=err.findtext('target_transform')
            rejected=node('SelectValues',name+' remove rejected raster value')
            fields=ET.SubElement(rejected,'fields');ET.SubElement(fields,'select_unspecified').text='Y'
            ET.SubElement(ET.SubElement(fields,'remove'),'name').text=field
            root.append(rejected)
            rejected_name=rejected.findtext('name')
            for hop in order:
                if hop.findtext('from')==name and hop.findtext('to')==target:
                    hop.find('to').text=rejected_name
            err.find('target_transform').text=rejected_name
            def edge(source,destination):
                if any(h.findtext('from')==source and h.findtext('to')==destination for h in order):return
                h=ET.SubElement(order,'hop')
                for key,value in [('from',source),('to',destination),('enabled','Y')]:ET.SubElement(h,key).text=value
            edge(name,rejected_name)
            edge(rejected_name,target)
            reader_error=copy.deepcopy(err)
            reader_error.find('source_transform').text=reader.findtext('name')
            reader_error.find('target_transform').text=target
            errors.append(reader_error)
            edge(reader.findtext('name'),target)
            if writer is not None:
                writer_error=copy.deepcopy(err)
                writer_error.find('source_transform').text=writer.findtext('name')
                errors.append(writer_error)
                edge(writer.findtext('name'),rejected_name)
        for key in ('source','sourceField','output','outputField','overwrite'):
            element=t.find(key)
            if element is not None:t.remove(element)
    layout(root)
    return root

if __name__=='__main__':
    p=argparse.ArgumentParser(description=__doc__);p.add_argument('input',type=Path);p.add_argument('output',type=Path);a=p.parse_args()
    if a.input.resolve()==a.output.resolve() or a.output.exists():p.error('Output must be a new file')
    root=migrate(ET.parse(a.input).getroot());ET.indent(root,space='  ');ET.ElementTree(root).write(a.output,encoding='unicode')
