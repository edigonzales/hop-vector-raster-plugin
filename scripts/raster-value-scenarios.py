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


def create_overview(work: Path, output_format: str, resampling: str, add_overviews: bool, mode="AUTO"):
    """A nonzero-origin, odd-sized clip that requires internal overviews."""
    root = E.Element("pipeline")
    info = E.SubElement(root, "info")
    E.SubElement(info, "name").text = "Clipped internal overviews"
    E.SubElement(info, "pipeline_type").text = "Normal"
    order = E.SubElement(root, "order")
    name = f"clip-{output_format}-{resampling}-{add_overviews}-{mode}"
    values = [
        ("reader", "SOGIS_RASTER_READER", dict(source=str(work / "input-large.tif"))),
        ("clip", "SOGIS_RASTER_VALUE_CLIP", dict(clipMethod="BOUNDING_BOX",
            explicitCrs="EPSG:2056", minX=2600007, minY=1200326,
            maxX=2600784, maxY=1200991, bands=1, noData=-9999)),
        ("writer", "SOGIS_RASTER_WRITER", dict(output=str(work / (name + ".tif")),
            format=output_format, compression="Deflate", addOverviews="Y" if add_overviews else "N",
            overviews=mode, overviewResampling=resampling, overwrite="Y")),
    ]
    for transform_name, kind, settings in values:
        transform = E.SubElement(root, "transform")
        for key, value in dict(name=transform_name, type=kind, copies=1, distribute="Y",
                               version=1, rasterField="raster", **settings).items():
            E.SubElement(transform, key).text = str(value)
    for source, target in (("reader", "clip"), ("clip", "writer")):
        hop = E.SubElement(order, "hop")
        for key, value in {"from": source, "to": target, "enabled": "Y"}.items():
            E.SubElement(hop, key).text = value
    E.SubElement(root, "transform_error_handling")
    E.SubElement(root, "attributes")
    E.indent(root, space="  ")
    path = work / (name + ".hpl")
    path.write_text(E.tostring(root, encoding="unicode"), encoding="utf-8")
    return path
