/*
 * Copyright (C) 2013  Camptocamp
 *
 * This file is part of MapFish Print
 *
 * MapFish Print is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * MapFish Print is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with MapFish Print.  If not, see <http://www.gnu.org/licenses/>.
 */

package org.mapfish.print.map.readers;

import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

import javax.xml.parsers.ParserConfigurationException;

import org.junit.Test;
import org.mapfish.print.PrintTestCase;
import org.xml.sax.SAXException;

public class WMSServiceInfoTest extends PrintTestCase {

    @Test
    public void testParseTileCache() throws IOException, SAXException, ParserConfigurationException {
        String response = """
                <?xml version='1.0' encoding="ISO-8859-1" standalone="no" ?>
                        <!DOCTYPE WMT_MS_Capabilities SYSTEM\s
                            "http://schemas.opengeospatial.net/wms/1.1.1/WMS_MS_Capabilities.dtd" [
                              <!ELEMENT VendorSpecificCapabilities (TileSet*) >
                              <!ELEMENT TileSet (SRS, BoundingBox?, Resolutions,
                                                 Width, Height, Format, Layers*, Styles*) >
                              <!ELEMENT Resolutions (#PCDATA) >
                              <!ELEMENT Width (#PCDATA) >
                              <!ELEMENT Height (#PCDATA) >
                              <!ELEMENT Layers (#PCDATA) >
                              <!ELEMENT Styles (#PCDATA) >
                        ]>\s
                        <WMT_MS_Capabilities version="1.1.1">
                
                          <Service>
                            <Name>OGC:WMS</Name>
                            <Title></Title>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com?"/>
                          </Service>
                       \s
                          <Capability>
                            <Request>
                              <GetCapabilities>
                
                                <Format>application/vnd.ogc.wms_xml</Format>
                                <DCPType>
                                  <HTTP>
                                    <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com?"/></Get>
                                  </HTTP>
                                </DCPType>
                              </GetCapabilities>
                              <GetMap>
                
                                <Format>image/png</Format>
                
                                <DCPType>
                                  <HTTP>
                                    <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com?"/></Get>
                                  </HTTP>
                                </DCPType>
                              </GetMap>
                            </Request>
                
                            <Exception>
                              <Format>text/plain</Format>
                            </Exception>
                            <VendorSpecificCapabilities>
                              <TileSet>
                                <SRS>EPSG:21781</SRS>
                                <BoundingBox SRS="EPSG:21781" minx="155000.000000" miny="-253050.000000"
                                                      maxx="1365000.000000" maxy="583050.000000" />
                                <Resolutions>800.00000000000000000000 400.00000000000000000000 200.00000000000000000000 100.00000000000000000000 50.00000000000000000000 20.00000000000000000000 10.00000000000000000000 5.00000000000000000000 2.50000000000000000000</Resolutions>
                
                                <Width>256</Width>
                                <Height>256</Height>
                                <Format>image/png</Format>
                                <Layers>cn</Layers>
                                <Styles></Styles>
                              </TileSet>
                            </VendorSpecificCapabilities>
                            <UserDefinedSymbolization SupportSLD="0" UserLayer="0"
                                                      UserStyle="0" RemoteWFS="0"/>
                            <Layer>
                              <Title>TileCache Layers</Title>
                            <Layer queryable="0" opaque="0" cascaded="1">
                
                              <Name>cn</Name>
                              <Title>cn</Title>
                              <SRS>EPSG:21781</SRS>
                              <BoundingBox SRS="EPSG:21781" minx="155000.000000" miny="-253050.000000"
                                                    maxx="1365000.000000" maxy="583050.000000" />
                            </Layer>
                            </Layer>
                          </Capability>
                        </WMT_MS_Capabilities>""";

        InputStream stream = new ByteArrayInputStream(response.getBytes("ISO-8859-1"));
        WMSServiceInfo info = new WMSServiceInfo.WMSServiceInfoLoader().parseInfo(stream);
        assertEquals(true, info.isTileCache());
        TileCacheLayerInfo layerInfo = info.getTileCacheLayer("cn");
        assertNotNull(layerInfo);
        assertEquals(256, layerInfo.getWidth());
        assertEquals(256, layerInfo.getHeight());
        final double[] resolutions = layerInfo.getResolutions();
        final double[] expectedResolutions = {
                800.0,
                400.0,
                200.0,
                100.0,
                50.0,
                20.0,
                10.0,
                5.0,
                2.5};
        assertTrue(Arrays.equals(expectedResolutions, resolutions));

        final TileCacheLayerInfo.ResolutionInfo higherRes = new TileCacheLayerInfo.ResolutionInfo(8, 2.5F);
        final TileCacheLayerInfo.ResolutionInfo midRes = new TileCacheLayerInfo.ResolutionInfo(7, 5.0F);
        final TileCacheLayerInfo.ResolutionInfo lowerRes = new TileCacheLayerInfo.ResolutionInfo(0, 800.0F);

        assertEquals(higherRes, layerInfo.getNearestResolution(0.1F));
        assertEquals(higherRes, layerInfo.getNearestResolution(2.5F));
        assertEquals(higherRes, layerInfo.getNearestResolution(2.6F));
        assertEquals(midRes, layerInfo.getNearestResolution(4.99999F));
        assertEquals(midRes, layerInfo.getNearestResolution(5.0F));
        assertEquals(lowerRes, layerInfo.getNearestResolution(1000.0F));

        assertEquals(155000.0F, layerInfo.getMinX(), 0.00001);
        assertEquals(-253050.0F, layerInfo.getMinY(), 0.00001);
        assertEquals("png", layerInfo.getExtension());
    }

    /**
     * Tilecache with resolutions not in the correct order.
     */
    @Test
    public void testParseWeirdTileCache() throws IOException, SAXException, ParserConfigurationException {
        String response = """
                <?xml version='1.0' encoding="ISO-8859-1" standalone="no" ?>
                        <!DOCTYPE WMT_MS_Capabilities SYSTEM\s
                            "http://schemas.opengeospatial.net/wms/1.1.1/WMS_MS_Capabilities.dtd" [
                              <!ELEMENT VendorSpecificCapabilities (TileSet*) >
                              <!ELEMENT TileSet (SRS, BoundingBox?, Resolutions,
                                                 Width, Height, Format, Layers*, Styles*) >
                              <!ELEMENT Resolutions (#PCDATA) >
                              <!ELEMENT Width (#PCDATA) >
                              <!ELEMENT Height (#PCDATA) >
                              <!ELEMENT Layers (#PCDATA) >
                              <!ELEMENT Styles (#PCDATA) >
                        ]>\s
                        <WMT_MS_Capabilities version="1.1.1">
                
                          <Service>
                            <Name>OGC:WMS</Name>
                            <Title></Title>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com?"/>
                          </Service>
                       \s
                          <Capability>
                            <Request>
                              <GetCapabilities>
                
                                <Format>application/vnd.ogc.wms_xml</Format>
                                <DCPType>
                                  <HTTP>
                                    <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com?"/></Get>
                                  </HTTP>
                                </DCPType>
                              </GetCapabilities>
                              <GetMap>
                
                                <Format>image/png</Format>
                
                                <DCPType>
                                  <HTTP>
                                    <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com?"/></Get>
                                  </HTTP>
                                </DCPType>
                              </GetMap>
                            </Request>
                
                            <Exception>
                              <Format>text/plain</Format>
                            </Exception>
                            <VendorSpecificCapabilities>
                              <TileSet>
                                <SRS>EPSG:21781</SRS>
                                <BoundingBox SRS="EPSG:21781" minx="155000.000000" miny="-253050.000000"
                                                      maxx="1365000.000000" maxy="583050.000000" />
                                <Resolutions>400.00000000000000000000 800.00000000000000000000 200.00000000000000000000 100.00000000000000000000 50.00000000000000000000 20.00000000000000000000 10.00000000000000000000 5.00000000000000000000 2.50000000000000000000</Resolutions>
                
                                <Width>256</Width>
                                <Height>256</Height>
                                <Format>image/png</Format>
                                <Layers>cn</Layers>
                                <Styles></Styles>
                              </TileSet>
                            </VendorSpecificCapabilities>
                            <UserDefinedSymbolization SupportSLD="0" UserLayer="0"
                                                      UserStyle="0" RemoteWFS="0"/>
                            <Layer>
                              <Title>TileCache Layers</Title>
                            <Layer queryable="0" opaque="0" cascaded="1">
                
                              <Name>cn</Name>
                              <Title>cn</Title>
                              <SRS>EPSG:21781</SRS>
                              <BoundingBox SRS="EPSG:21781" minx="155000.000000" miny="-253050.000000"
                                                    maxx="1365000.000000" maxy="583050.000000" />
                            </Layer>
                            </Layer>
                          </Capability>
                        </WMT_MS_Capabilities>""";

        InputStream stream = new ByteArrayInputStream(response.getBytes("ISO-8859-1"));
        WMSServiceInfo info = new WMSServiceInfo.WMSServiceInfoLoader().parseInfo(stream);
        assertEquals(true, info.isTileCache());
        TileCacheLayerInfo layerInfo = info.getTileCacheLayer("cn");
        assertNotNull(layerInfo);
        assertEquals(256, layerInfo.getWidth());
        assertEquals(256, layerInfo.getHeight());
        final double[] resolutions = layerInfo.getResolutions();
        final double[] expectedResolutions = {
                800.0,
                400.0,
                200.0,
                100.0,
                50.0,
                20.0,
                10.0,
                5.0,
                2.5};
        assertTrue(Arrays.equals(expectedResolutions, resolutions));

        final TileCacheLayerInfo.ResolutionInfo higherRes = new TileCacheLayerInfo.ResolutionInfo(8, 2.5F);
        final TileCacheLayerInfo.ResolutionInfo midRes = new TileCacheLayerInfo.ResolutionInfo(7, 5.0F);
        final TileCacheLayerInfo.ResolutionInfo lowerRes = new TileCacheLayerInfo.ResolutionInfo(0, 800.0F);

        assertEquals(higherRes, layerInfo.getNearestResolution(0.1F));
        assertEquals(higherRes, layerInfo.getNearestResolution(2.5F));
        assertEquals(higherRes, layerInfo.getNearestResolution(2.6F));
        assertEquals(midRes, layerInfo.getNearestResolution(4.99999F));
        assertEquals(midRes, layerInfo.getNearestResolution(5.0F));
        assertEquals(lowerRes, layerInfo.getNearestResolution(1000.0F));

        assertEquals(155000.0F, layerInfo.getMinX(), 0.00001);
        assertEquals(-253050.0F, layerInfo.getMinY(), 0.00001);
        assertEquals("png", layerInfo.getExtension());
    }

    @Test
    public void testParseMapServer() throws IOException, SAXException, ParserConfigurationException {
        String response = """
                <?xml version='1.0' encoding="UTF-8" standalone="no" ?>
                <!DOCTYPE WMT_MS_Capabilities SYSTEM "http://schemas.opengis.net/wms/1.1.1/WMS_MS_Capabilities.dtd"
                 [
                 <!ELEMENT VendorSpecificCapabilities EMPTY>
                 ]>  <!-- end of DOCTYPE declaration -->
                
                <WMT_MS_Capabilities version="1.1.1">
                
                <!-- MapServer version 5.0.3 OUTPUT=GIF OUTPUT=PNG OUTPUT=JPEG OUTPUT=WBMP OUTPUT=SVG SUPPORTS=PROJ SUPPORTS=AGG SUPPORTS=FREETYPE SUPPORTS=WMS_SERVER SUPPORTS=WMS_CLIENT SUPPORTS=WFS_SERVER SUPPORTS=WFS_CLIENT SUPPORTS=WCS_SERVER SUPPORTS=FASTCGI SUPPORTS=THREADS SUPPORTS=GEOS INPUT=EPPL7 INPUT=POSTGIS INPUT=OGR INPUT=GDAL INPUT=SHAPEFILE -->
                
                <Service>
                  <Name>OGC:WMS</Name>
                  <Title>SwissTopo raster WMS Server</Title>
                  <Abstract>WMS Server serving swisstopo raster maps</Abstract>
                  <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/>
                  <ContactInformation>
                  </ContactInformation>
                </Service>
                
                <Capability>
                  <Request>
                    <GetCapabilities>
                      <Format>application/vnd.ogc.wms_xml</Format>
                      <DCPType>
                        <HTTP>
                          <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Get>
                          <Post><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Post>
                        </HTTP>
                      </DCPType>
                    </GetCapabilities>
                    <GetMap>
                      <Format>image/tiff</Format>
                      <Format>image/gif</Format>
                      <Format>image/png; mode=24bit</Format>
                      <Format>image/wbmp</Format>
                      <Format>image/svg+xml</Format>
                      <DCPType>
                        <HTTP>
                          <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Get>
                          <Post><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Post>
                        </HTTP>
                      </DCPType>
                    </GetMap>
                    <GetFeatureInfo>
                      <Format>text/plain</Format>
                      <Format>application/vnd.ogc.gml</Format>
                      <DCPType>
                        <HTTP>
                          <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Get>
                          <Post><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Post>
                        </HTTP>
                      </DCPType>
                    </GetFeatureInfo>
                    <DescribeLayer>
                      <Format>text/xml</Format>
                      <DCPType>
                        <HTTP>
                          <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Get>
                          <Post><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Post>
                        </HTTP>
                      </DCPType>
                    </DescribeLayer>
                    <GetLegendGraphic>
                      <Format>image/gif</Format>
                      <Format>image/png; mode=24bit</Format>
                      <Format>image/wbmp</Format>
                      <DCPType>
                        <HTTP>
                          <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Get>
                          <Post><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Post>
                        </HTTP>
                      </DCPType>
                    </GetLegendGraphic>
                    <GetStyles>
                      <Format>text/xml</Format>
                      <DCPType>
                        <HTTP>
                          <Get><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Get>
                          <Post><OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:href="http://www.example.com/cgi-bin/mapserver?"/></Post>
                        </HTTP>
                      </DCPType>
                    </GetStyles>
                  </Request>
                  <Exception>
                    <Format>application/vnd.ogc.se_xml</Format>
                    <Format>application/vnd.ogc.se_inimage</Format>
                    <Format>application/vnd.ogc.se_blank</Format>
                  </Exception>
                  <VendorSpecificCapabilities />
                  <UserDefinedSymbolization SupportSLD="1" UserLayer="0" UserStyle="1" RemoteWFS="0"/>
                  <Layer>
                    <Name>SwissTopo</Name>
                    <Title>SwissTopo raster WMS Server</Title>
                    <SRS>epsg:21781</SRS>
                    <SRS>epsg:4326</SRS>
                    <LatLonBoundingBox minx="1.20539" miny="42.4702" maxx="18.1119" maxy="50.3953" />
                    <BoundingBox SRS="EPSG:21781"
                                minx="155000" miny="-253050" maxx="1.365e+06" maxy="583050" />
                    <Layer>
                      <Name>cn</Name>
                      <Title>SwissTopo</Title>
                      <Abstract>cn</Abstract>
                      <Layer queryable="0" opaque="0" cascaded="0">
                        <Name>cn25k</Name>
                        <Title>cn25k</Title>
                        <SRS>epsg:21781</SRS>
                        <SRS>epsg:4326</SRS>
                        <ScaleHint min="0.0707106399349092" max="5.23258735518328" />
                      </Layer>
                    </Layer>
                  </Layer>
                
                </Capability>
                </WMT_MS_Capabilities>""";

        InputStream stream = new ByteArrayInputStream(response.getBytes("UTF-8"));
        WMSServiceInfo info = new WMSServiceInfo.WMSServiceInfoLoader().parseInfo(stream);
        assertEquals(false, info.isTileCache());
    }

    @Test
    public void testParseGeoServer() throws IOException, SAXException, ParserConfigurationException {
        String response = """
                <?xml version="1.0" encoding="UTF-8"?>
                <!DOCTYPE WMT_MS_Capabilities SYSTEM "http://wms.example.com:8080/geoserver/schemas/wms/1.1.1/WMS_MS_Capabilities.dtd">
                <WMT_MS_Capabilities version="1.1.1">
                  <Service>
                    <Name>OGC:WMS</Name>
                    <Title>GeoNetwork opensource embedded Web Map Server</Title>
                    <Abstract>
                Web Map Services provided by GeoServer for GeoNetwork opensource.
                     </Abstract>
                    <KeywordList>
                      <Keyword>WFS</Keyword>
                      <Keyword>WMS</Keyword>
                      <Keyword>GEOSERVER</Keyword>
                      <Keyword>GEONETWORK</Keyword>
                      <Keyword>OSGeo</Keyword>
                    </KeywordList>
                    <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://geonetwork-opensource.org/"/>
                    <ContactInformation>
                      <ContactPersonPrimary>
                        <ContactPerson/>
                        <ContactOrganization/>
                      </ContactPersonPrimary>
                      <ContactPosition/>
                      <ContactAddress>
                        <AddressType/>
                        <Address/>
                        <City/>
                        <StateOrProvince/>
                        <PostCode/>
                        <Country/>
                      </ContactAddress>
                      <ContactVoiceTelephone/>
                      <ContactFacsimileTelephone/>
                      <ContactElectronicMailAddress/>
                    </ContactInformation>
                    <Fees>NONE</Fees>
                    <AccessConstraints>NONE</AccessConstraints>
                  </Service>
                  <Capability>
                    <Request>
                      <GetCapabilities>
                        <Format>application/vnd.ogc.wms_xml</Format>
                        <DCPType>
                          <HTTP>
                            <Get>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Get>
                            <Post>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Post>
                          </HTTP>
                        </DCPType>
                      </GetCapabilities>
                      <GetMap>
                        <Format>image/png</Format>
                        <Format>application/atom+xml</Format>
                        <Format>application/openlayers</Format>
                        <Format>application/pdf</Format>
                        <Format>application/rss+xml</Format>
                        <Format>application/vnd.google-earth.kml+xml</Format>
                        <Format>application/vnd.google-earth.kmz</Format>
                        <Format>image/geotiff</Format>
                        <Format>image/geotiff8</Format>
                        <Format>image/gif</Format>
                        <Format>image/jpeg</Format>
                        <Format>image/png8</Format>
                        <Format>image/svg+xml</Format>
                        <Format>image/tiff</Format>
                        <Format>image/tiff8</Format>
                        <DCPType>
                          <HTTP>
                            <Get>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Get>
                          </HTTP>
                        </DCPType>
                      </GetMap>
                      <GetFeatureInfo>
                        <Format>text/plain</Format>
                        <Format>text/html</Format>
                        <Format>application/vnd.ogc.gml</Format>
                        <DCPType>
                          <HTTP>
                            <Get>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Get>
                            <Post>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Post>
                          </HTTP>
                        </DCPType>
                      </GetFeatureInfo>
                      <DescribeLayer>
                        <Format>application/vnd.ogc.wms_xml</Format>
                        <DCPType>
                          <HTTP>
                            <Get>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Get>
                          </HTTP>
                        </DCPType>
                      </DescribeLayer>
                      <GetLegendGraphic>
                        <Format>image/png</Format>
                        <Format>image/jpeg</Format>
                        <Format>image/gif</Format>
                        <DCPType>
                          <HTTP>
                            <Get>
                              <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms?SERVICE=WMS&amp;"/>
                            </Get>
                          </HTTP>
                        </DCPType>
                      </GetLegendGraphic>
                    </Request>
                    <Exception>
                      <Format>application/vnd.ogc.se_xml</Format>
                    </Exception>
                    <UserDefinedSymbolization SupportSLD="1" UserLayer="1" UserStyle="1" RemoteWFS="0"/>
                    <Layer>
                      <Title>GeoNetwork opensource embedded Web Map Server</Title>
                      <Abstract>
                Web Map Services provided by GeoServer for GeoNetwork opensource.
                     </Abstract>
                      <!--common SRS:-->
                      <SRS>EPSG:21781</SRS>
                      <!--All supported EPSG projections:-->
                      <SRS>EPSG:2000</SRS>
                      <SRS>EPSG:2001</SRS>
                      <SRS>EPSG:2002</SRS>  <!-- ...cut... -->
                      <SRS>EPSG:42304</SRS>
                      <SRS>EPSG:42303</SRS>
                      <LatLonBoundingBox minx="-180.0" miny="45.78874927621686" maxx="10.558901428148609" maxy="180.0"/>
                      <Layer queryable="1">
                        <Name>gn:countries</Name>
                        <Title>countries_Type</Title>
                        <Abstract>Generated from countries</Abstract>
                        <KeywordList>
                          <Keyword>countries</Keyword>
                        </KeywordList>
                        <SRS>EPSG:21781</SRS>
                        <!--WKT definition of this CRS:
                PROJCS["CH1903 / LV03",\s
                  GEOGCS["CH1903",\s
                    DATUM["CH1903",\s
                      SPHEROID["Bessel 1841", 6377397.155, 299.1528128, AUTHORITY["EPSG","7004"]],\s
                      TOWGS84[674.4, 15.1, 405.3, 0.0, 0.0, 0.0, 0.0],\s
                      AUTHORITY["EPSG","6149"]],\s
                    PRIMEM["Greenwich", 0.0, AUTHORITY["EPSG","8901"]],\s
                    UNIT["degree", 0.017453292519943295],\s
                    AXIS["Geodetic longitude", EAST],\s
                    AXIS["Geodetic latitude", NORTH],\s
                    AUTHORITY["EPSG","4149"]],\s
                  PROJECTION["Oblique Mercator", AUTHORITY["EPSG","9815"]],\s
                  PARAMETER["longitude_of_center", 7.439583333333333],\s
                  PARAMETER["latitude_of_center", 46.952405555555565],\s
                  PARAMETER["azimuth", 90.0],\s
                  PARAMETER["scale_factor", 1.0],\s
                  PARAMETER["false_easting", 600000.0],\s
                  PARAMETER["false_northing", 200000.0],\s
                  PARAMETER["rectified_grid_angle", 90.0],\s
                  UNIT["m", 1.0],\s
                  AXIS["Easting", EAST],\s
                  AXIS["Northing", NORTH],\s
                  AUTHORITY["EPSG","21781"]]-->
                        <LatLonBoundingBox minx="5.956640769345093" miny="45.81975202969038" maxx="10.493459252966687" maxy="47.810475823557454"/>
                        <BoundingBox SRS="EPSG:21781" minx="5.956640769345093" miny="45.81975202969038" maxx="10.493459252966687" maxy="47.810475823557454"/>
                        <Style>
                          <Name>Selection</Name>
                          <Title>A style to show the selected feature</Title>
                          <Abstract>A yellow line with a 2 pixel width</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:countries"/>
                          </LegendURL>
                        </Style>
                        <Style>
                          <Name>Selection</Name>
                          <Title>A style to show the selected feature</Title>
                          <Abstract>A yellow line with a 2 pixel width</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:countries"/>
                          </LegendURL>
                        </Style>
                      </Layer>
                      <Layer queryable="1">
                        <Name>gn:gemeindenBB</Name>
                        <Title>gemeindenBB_Type</Title>
                        <Abstract>Generated from gemeindenBB</Abstract>
                        <KeywordList>
                          <Keyword>gemeindenBB</Keyword>
                        </KeywordList>
                        <SRS>EPSG:21781</SRS>
                        <!--WKT definition of this CRS:
                PROJCS["CH1903 / LV03",\s
                  GEOGCS["CH1903",\s
                    DATUM["CH1903",\s
                      SPHEROID["Bessel 1841", 6377397.155, 299.1528128, AUTHORITY["EPSG","7004"]],\s
                      TOWGS84[674.4, 15.1, 405.3, 0.0, 0.0, 0.0, 0.0],\s
                      AUTHORITY["EPSG","6149"]],\s
                    PRIMEM["Greenwich", 0.0, AUTHORITY["EPSG","8901"]],\s
                    UNIT["degree", 0.017453292519943295],\s
                    AXIS["Geodetic longitude", EAST],\s
                    AXIS["Geodetic latitude", NORTH],\s
                    AUTHORITY["EPSG","4149"]],\s
                  PROJECTION["Oblique Mercator", AUTHORITY["EPSG","9815"]],\s
                  PARAMETER["longitude_of_center", 7.439583333333333],\s
                  PARAMETER["latitude_of_center", 46.952405555555565],\s
                  PARAMETER["azimuth", 90.0],\s
                  PARAMETER["scale_factor", 1.0],\s
                  PARAMETER["false_easting", 600000.0],\s
                  PARAMETER["false_northing", 200000.0],\s
                  PARAMETER["rectified_grid_angle", 90.0],\s
                  UNIT["m", 1.0],\s
                  AXIS["Easting", EAST],\s
                  AXIS["Northing", NORTH],\s
                  AUTHORITY["EPSG","21781"]]-->
                        <LatLonBoundingBox minx="5.956610444770297" miny="45.81975202969038" maxx="10.493459252966687" maxy="47.810475823557454"/>
                        <BoundingBox SRS="EPSG:21781" minx="484807.6327910628" miny="74247.28126117215" maxx="837389.5575765288" maxy="300004.7975591116"/>
                        <Style>
                          <Name>Selection</Name>
                          <Title>A style to show the selected feature</Title>
                          <Abstract>A yellow line with a 2 pixel width</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:gemeindenBB"/>
                          </LegendURL>
                        </Style>
                      </Layer>
                      <Layer queryable="1">
                        <Name>gn:kantoneBB</Name>
                        <Title>kantoneBB_Type</Title>
                        <Abstract>Generated from kantoneBB</Abstract>
                        <KeywordList>
                          <Keyword>kantoneBB</Keyword>
                        </KeywordList>
                        <SRS>EPSG:21781</SRS>
                        <!--WKT definition of this CRS:
                PROJCS["CH1903 / LV03",\s
                  GEOGCS["CH1903",\s
                    DATUM["CH1903",\s
                      SPHEROID["Bessel 1841", 6377397.155, 299.1528128, AUTHORITY["EPSG","7004"]],\s
                      TOWGS84[674.4, 15.1, 405.3, 0.0, 0.0, 0.0, 0.0],\s
                      AUTHORITY["EPSG","6149"]],\s
                    PRIMEM["Greenwich", 0.0, AUTHORITY["EPSG","8901"]],\s
                    UNIT["degree", 0.017453292519943295],\s
                    AXIS["Geodetic longitude", EAST],\s
                    AXIS["Geodetic latitude", NORTH],\s
                    AUTHORITY["EPSG","4149"]],\s
                  PROJECTION["Oblique Mercator", AUTHORITY["EPSG","9815"]],\s
                  PARAMETER["longitude_of_center", 7.439583333333333],\s
                  PARAMETER["latitude_of_center", 46.952405555555565],\s
                  PARAMETER["azimuth", 90.0],\s
                  PARAMETER["scale_factor", 1.0],\s
                  PARAMETER["false_easting", 600000.0],\s
                  PARAMETER["false_northing", 200000.0],\s
                  PARAMETER["rectified_grid_angle", 90.0],\s
                  UNIT["m", 1.0],\s
                  AXIS["Easting", EAST],\s
                  AXIS["Northing", NORTH],\s
                  AUTHORITY["EPSG","21781"]]-->
                        <LatLonBoundingBox minx="5.908953517650008" miny="45.78874927621686" maxx="10.558901428148609" maxy="47.81382548271046"/>
                        <BoundingBox SRS="EPSG:21781" minx="485410.0" miny="75270.0" maxx="833840.7" maxy="295935.0"/>
                        <Style>
                          <Name>Selection</Name>
                          <Title>A style to show the selected feature</Title>
                          <Abstract>A yellow line with a 2 pixel width</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:kantoneBB"/>
                          </LegendURL>
                        </Style>
                        <Style>
                          <Name>Selection</Name>
                          <Title>A style to show the selected feature</Title>
                          <Abstract>A yellow line with a 2 pixel width</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:kantoneBB"/>
                          </LegendURL>
                        </Style>
                      </Layer>
                      <Layer queryable="1">
                        <Name>gn:xlinks</Name>
                        <Title>xlinks</Title>
                        <Abstract>xlinks</Abstract>
                        <KeywordList>
                          <Keyword>xlinks</Keyword>
                        </KeywordList>
                        <SRS>EPSG:21781</SRS>
                        <!--WKT definition of this CRS:
                PROJCS["CH1903 / LV03",\s
                  GEOGCS["CH1903",\s
                    DATUM["CH1903",\s
                      SPHEROID["Bessel 1841", 6377397.155, 299.1528128, AUTHORITY["EPSG","7004"]],\s
                      TOWGS84[674.4, 15.1, 405.3, 0.0, 0.0, 0.0, 0.0],\s
                      AUTHORITY["EPSG","6149"]],\s
                    PRIMEM["Greenwich", 0.0, AUTHORITY["EPSG","8901"]],\s
                    UNIT["degree", 0.017453292519943295],\s
                    AXIS["Geodetic longitude", EAST],\s
                    AXIS["Geodetic latitude", NORTH],\s
                    AUTHORITY["EPSG","4149"]],\s
                  PROJECTION["Oblique Mercator", AUTHORITY["EPSG","9815"]],\s
                  PARAMETER["longitude_of_center", 7.439583333333333],\s
                  PARAMETER["latitude_of_center", 46.952405555555565],\s
                  PARAMETER["azimuth", 90.0],\s
                  PARAMETER["scale_factor", 1.0],\s
                  PARAMETER["false_easting", 600000.0],\s
                  PARAMETER["false_northing", 200000.0],\s
                  PARAMETER["rectified_grid_angle", 90.0],\s
                  UNIT["m", 1.0],\s
                  AXIS["Easting", EAST],\s
                  AXIS["Northing", NORTH],\s
                  AUTHORITY["EPSG","21781"]]-->
                        <LatLonBoundingBox minx="-180.0" miny="90.0" maxx="-90.0" maxy="180.0"/>
                        <BoundingBox SRS="EPSG:21781" minx="9.626899504917674" miny="47.12853893946158" maxx="9.82825134054292" maxy="47.24631077121012"/>
                        <Style>
                          <Name>polygon</Name>
                          <Title>A boring default style</Title>
                          <Abstract>A sample style that just prints out a transparent red interior with a red outline</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:xlinks"/>
                          </LegendURL>
                        </Style>
                      </Layer>
                      <Layer queryable="0">
                        <Name>gn:world</Name>
                        <Title>Blue Marble world image</Title>
                        <Abstract>Blue Marble world image</Abstract>
                        <KeywordList>
                          <Keyword>Blue</Keyword>
                          <Keyword>Marble</Keyword>
                          <Keyword>world</Keyword>
                          <Keyword>topography</Keyword>
                          <Keyword>bathymetry</Keyword>
                          <Keyword>200407</Keyword>
                        </KeywordList>
                        <!--WKT definition of this CRS:
                GEOGCS["WGS 84",\s
                  DATUM["World Geodetic System 1984",\s
                    SPHEROID["WGS 84", 6378137.0, 298.257223563, AUTHORITY["EPSG","7030"]],\s
                    AUTHORITY["EPSG","6326"]],\s
                  PRIMEM["Greenwich", 0.0, AUTHORITY["EPSG","8901"]],\s
                  UNIT["degree", 0.017453292519943295],\s
                  AXIS["Geodetic longitude", EAST],\s
                  AXIS["Geodetic latitude", NORTH],\s
                  AUTHORITY["EPSG","4326"]]-->
                        <SRS>EPSG:4326</SRS>
                        <LatLonBoundingBox minx="-180.0" miny="-90.0" maxx="180.0" maxy="90.0"/>
                        <BoundingBox SRS="EPSG:4326" minx="-180.0" miny="-90.0" maxx="180.0" maxy="90.0"/>
                        <Style>
                          <Name>raster</Name>
                          <Title>A boring default style</Title>
                          <Abstract>A sample style for rasters</Abstract>
                          <LegendURL width="20" height="20">
                            <Format>image/png</Format>
                            <OnlineResource xmlns:xlink="http://www.w3.org/1999/xlink" xlink:type="simple" xlink:href="http://wms.example.com:8080/geoserver/wms/GetLegendGraphic?VERSION=1.0.0&amp;FORMAT=image/png&amp;WIDTH=20&amp;HEIGHT=20&amp;LAYER=gn:world"/>
                          </LegendURL>
                        </Style>
                      </Layer>
                    </Layer>
                  </Capability>
                
                </WMT_MS_Capabilities>""";

        InputStream stream = new ByteArrayInputStream(response.getBytes("UTF-8"));
        WMSServiceInfo info = new WMSServiceInfo.WMSServiceInfoLoader().parseInfo(stream);
        assertEquals(false, info.isTileCache());
    }
}
