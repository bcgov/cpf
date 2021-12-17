## File formats
The table below shows the file formats supported by the CPF web service for web service resources,
structured input data, and structured result data. Click the 'view' links below to download a sample
of the resource in the each file format.

The CPF requires that <a href="https://en.wikipedia.org/wiki/UTF-8">UTF-8</a>
encoding be used for all text files. This includes the text in a .dbf file for a .shpz archive,
unless a .cpg file is provided in the .shpz archive.

<div class="table-responsive">
<table class="table table-condensed table-striped tabled-bordered">
  <thead>
    <tr>
      <th>File Extension</th>
      <th>Media Type</th>
      <th>Web Service</th>
      <th>Input</th>
      <th>Result</th>
      <th>Description</th>
    </tr>
  </thead>
  <tbody>
    <tr id="csv">
      <td>csv</td>
      <td>text/csv</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br /> <a href="input/input.csv" >view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br /> <a href="result/result.csv" >view</a></td>
      <td>A <a href="https://tools.ietf.org/html/rfc4180">Comma-Separated Values</a> file with a header line containing the field labels and one line for each feature record.</td>
    </tr>
    <tr id="dbf">
      <td>dbf</td>
      <td>application/dbf</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="input/input.dbf" >view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="result/result.dbf" >view</a></td>
      <td><p>A <a href="https://en.wikipedia.org/wiki/DBase">DBase</a> file containing the attribution.</p>
      <p>Each feature record is one record in the DBase file.</p></td>
    </tr>
    <tr id="geojson">
      <td>geojson</td>
      <td>application/vnd.geo+json<br/>(was application/x-geo+json)</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a
        href="result/result.geojson" >view</a></td>
      <td><p> <a href="https://geojson.org">GeoJSON</a> is a geospatial data
        interchange format based on <a href="https://json.org/">JavaScript
          Object Notation (JSON)</a>. GeoJSON can be used to create web browser
        based GIS applications in JavaScript. </p>
        <p>Each feature record is one feature object in a feature collection
          object. If there is only one record the feature object will not be
          wrapped in a feature collection.</p></td>
    </tr>
    <tr id="gml">
      <td>gml</td>
      <td>application/gml+xml</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a
        href="result/result.gml" >view</a></td>
      <td><p> <a href="https://en.wikipedia.org/wiki/Geography_Markup_Language">Geography
        Markup Language (GML)</a> is an <a
            href="https://www.opengeospatial.org/standards/gml">international
          standard</a> for exchange of geographical feature maintained by the <a
            href="https://www.opengeospatial.org/">Open Geospatial Consortium
            (OGC)</a>. </p>
        <p>Each feature is one GML feature of the Feature Type from the
          'Geomark Geometry Representations' table. The features are wrapped in
          a gml:FeatureCollection</p></td>
    </tr>
    <tr id="html">
      <td>html</td>
      <td>text/html</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="result/result.html" >view</a></td>
      <td><p>A HTML document containing a table with a head section containing the field lables for each column row for each feature record.</p></td>
    </tr>
    <tr id="json">
      <td>json</td>
      <td>application/json</td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a
        href="ws/apps.json">view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="input/input.json" >view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="result/result.json" >view</a></td>
      <td><a href="https://www.json.org/">JavaScript Object Notation
        (JSON)</a>. The geomark info is wrapped in a JSON object with one
        attribute for each geomark attributes. The resource links are a a JSON
        list with one JSON object per link.</td>
    </tr>
    <tr id="kml">
      <td>kml</td>
      <td>application/vnd.google-earth.kml+xml</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br /> <a href="result/result.kml" >view</a></td>
      <td><p>
          <a href="https://code.google.com/apis/kml/">Keyhole Markup Language
            (KML)</a> is used to display geographic data in an Earth browser, such
          as <a href="https://www.google.com/earth/">Google Earth</a>. <a
            href="https://www.opengeospatial.org/standards/kml/">KML is an
            international standard</a> maintained by the <a
            href="https://www.opengeospatial.org/">Open Geospatial Consortium,
            Inc. (OGC)</a>.
        </p>
        <p>Each feature record will be encoded using a Placemark with the
          geomark's attributes as ExtendedData. If there are more than one
          record the Placemarks will be wrapped in a Document element, otherwise
          the Placemark will be a child of the kml element.</p></td>
    </tr>
    <tr id="kmz">
      <td>kml</td>
      <td>application/vnd.google-earth.kmz</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br /> <a href="result/result.kmz" >view</a></td>
      <td><p>
          <a href="https://code.google.com/apis/kml/">Keyhole Markup Language
            (KML)</a> is used to display geographic data in an Earth browser, such
          as <a href="https://www.google.com/earth/">Google Earth</a>. <a
            href="https://www.opengeospatial.org/standards/kml/">KML is an
            international standard</a> maintained by the <a
            href="https://www.opengeospatial.org/">Open Geospatial Consortium,
            Inc. (OGC)</a>.
        </p>        
        <p>The KMZ is a Zip file with a single kml document. Typically named doc.kml.</p>       
        <p>Each feature record will be encoded using a Placemark with the
          geomark's attributes as ExtendedData. If there are more than one
          record the Placemarks will be wrapped in a Document element, otherwise
          the Placemark will be a child of the kml element.</p></td>
    </tr>
    <tr id="shpz">
      <td>shpz</td>
      <td>application/x-shp+zip</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br /> <a
        href="input/input.shpz" >view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br /> <a
        href="result/result.shpz" >view</a></td>
      <td>
        <p>
          developed by ESRI for ArcMap which is a multi-file GIS format that
          <a href="https://en.wikipedia.org/wiki/Shapefile">Shapefile (ESRI)</a>
          uses <a href="https://en.wikipedia.org/wiki/DBase">DBase</a> file for the attribution and a prj file for the coordinate
          system. Due to the multi-file nature the shp, dbf, shx and prj files
          are compressed into a ZIP file for download or upload.
        </p>
        <p>Each feature record is one record in the Shapefile.</p>
      </td>
    </tr>
    <tr id="xhtml">
      <td>xhtml</td>
      <td>application/xhtml+xml</td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/cross.png" alt="No" /></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="result/result.xhtml">view</a></td>
      <td><p>A XHTML document containing a table with a head section containing the field lables for each column row for each feature record.</p></td>
    </tr>
    <tr id="xml">
      <td>xml</td>
      <td>text/xml</td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a
        href="ws/apps.xml">view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="input/input.xml" >view</a></td>
      <td><img src="../images/tick.png" alt="Yes" /><br />
        <a href="result/result.xml" >view</a></td>
      <td><a href="https://www.w3.org/XML/">Extensible Markup Language
        (XML)</a>. The geomark info is wrapped in the GeomarkInfo XML tag with one
        XML tag for each geomark attributes. Each resource link is contained in
        a resourceLink XML tag with one XML tag for each resource link
        attribute.</td>
    </tr>
  </tbody>
</table>
</div>