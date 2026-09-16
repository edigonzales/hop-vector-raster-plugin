"""Installed pipeline scenarios specifically exercising native Raster row transport."""
from pathlib import Path
import xml.etree.ElementTree as E


def create(work: Path, source: Path):
    root=E.Element('pipeline');info=E.SubElement(root,'info');E.SubElement(info,'name').text='Raster spill and branches';E.SubElement(info,'pipeline_type').text='Normal';order=E.SubElement(root,'order')
    def node(name,kind,**values):
        t=E.SubElement(root,'transform')
        for k,v in dict(name=name,type=kind,copies=1,distribute='Y',**values).items():E.SubElement(t,k).text=str(v)
        return t
    def hop(a,b):
        h=E.SubElement(order,'hop')
        for k,v in dict(**{'from':a},to=b,enabled='Y').items():E.SubElement(h,k).text=v
    rows=node('rows','RowGenerator',limit=8,never_ending='N');field=E.SubElement(E.SubElement(rows,'fields'),'field')
    for k,v in dict(name='id',type='Integer',nullif=1).items():E.SubElement(field,k).text=str(v)
    node('reader','SOGIS_RASTER_READER',version=1,source=str(source),rasterField='raster')
    node('clip','SOGIS_RASTER_VALUE_CLIP',version=1,rasterField='raster',clipMethod='BOUNDING_BOX',explicitCrs='EPSG:2056',minX=2600001,minY=1200001,maxX=2600003,maxY=1200003,bands=1,noData=255)
    sort=node('spill','SortRows',directory=str(work),sort_prefix='raster-spill',sort_size=2,free_memory=0,compress='N',unique_rows='N');sort.find('distribute').text='N'
    f=E.SubElement(E.SubElement(sort,'fields'),'field')
    for k,v in dict(name='raster',ascending='Y',case_sensitive='N',presorted='N').items():E.SubElement(f,k).text=v
    node('info','SOGIS_RASTER_INFO',version=1,rasterField='raster',infoFields='width height crs bands',prefix='info_')
    for suffix in ('a','b'):node('writer-'+suffix,'SOGIS_RASTER_WRITER',version=1,rasterField='raster',output=str(work/('branch-'+suffix+'.tif')),overwrite='Y',prefix=suffix+'_')
    for a,b in [('rows','reader'),('reader','clip'),('clip','spill'),('spill','writer-a'),('spill','info'),('info','writer-b')]:hop(a,b)
    E.SubElement(root,'transform_error_handling');E.SubElement(root,'attributes');E.indent(root,space='  ');path=work/'raster-values.hpl';path.write_text(E.tostring(root,encoding='unicode'));return path
