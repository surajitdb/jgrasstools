/*
 * This file is part of JGrasstools (http://www.jgrasstools.org)
 * (C) HydroloGIS - www.hydrologis.com 
 * 
 * JGrasstools is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.jgrasstools.modules;

import static org.jgrasstools.gears.libs.modules.JGTConstants.isNovalue;
import static org.jgrasstools.gears.utils.geometry.GeometryUtilities.getGeometryType;

import java.awt.image.RenderedImage;

import javax.media.jai.iterator.RandomIter;
import javax.media.jai.iterator.RandomIterFactory;

import oms3.annotations.Author;
import oms3.annotations.Description;
import oms3.annotations.Documentation;
import oms3.annotations.Execute;
import oms3.annotations.In;
import oms3.annotations.Keywords;
import oms3.annotations.Label;
import oms3.annotations.License;
import oms3.annotations.Name;
import oms3.annotations.Out;
import oms3.annotations.Status;

import org.geotools.coverage.grid.GridCoordinates2D;
import org.geotools.coverage.grid.GridCoverage2D;
import org.geotools.coverage.grid.GridGeometry2D;
import org.geotools.data.simple.SimpleFeatureCollection;
import org.geotools.feature.FeatureCollections;
import org.geotools.feature.FeatureIterator;
import org.geotools.geometry.DirectPosition2D;
import org.geotools.geometry.Envelope2D;
import org.jgrasstools.gears.libs.exceptions.ModelsIllegalargumentException;
import org.jgrasstools.gears.libs.modules.JGTConstants;
import org.jgrasstools.gears.libs.modules.JGTModel;
import org.jgrasstools.gears.utils.features.FeatureExtender;
import org.jgrasstools.gears.utils.geometry.GeometryUtilities.GEOMETRYTYPE;
import org.opengis.feature.simple.SimpleFeature;
import org.opengis.feature.simple.SimpleFeatureType;

import com.vividsolutions.jts.geom.Coordinate;
import com.vividsolutions.jts.geom.Geometry;
import com.vividsolutions.jts.geom.Point;

@Description("Module that extracts raster categories and adds them to a feature collection.")
@Documentation("OmsRasterCatToFeatureAttribute.html")
@Author(name = "Andrea Antonello", contact = "http://www.hydrologis.com")
@Keywords("Raster, Vector")
@Status(Status.CERTIFIED)
@Label(JGTConstants.VECTORPROCESSING)
@Name("_rat2featureattr")
@License("General Public License Version 3 (GPLv3)")
@SuppressWarnings("nls")
public class OmsRasterCatToFeatureAttribute extends JGTModel{

    @Description("The raster on which to map the vector features.")
    @In
    public GridCoverage2D inRaster;

    @Description("The vector to use for the geometric mapping.")
    @In
    public SimpleFeatureCollection inVector = null;

    @Description("The name for the new field to create.")
    @In
    public String fNew = "new";

    @Description("The position of the coordinate to take in the case of multi geometries.")
    @In
    public String pPos = MIDDLE;

    @Description("The extended vector.")
    @Out
    public SimpleFeatureCollection outVector = null;

    private static final String MIDDLE = "middle";
    private static final String START = "start";
    private static final String END = "end";

    private RandomIter inIter = null;

    private GridGeometry2D gridGeometry;

    @Execute
    public void process() throws Exception {
        if (inIter == null) {
            RenderedImage inputRI = inRaster.getRenderedImage();
            inIter = RandomIterFactory.create(inputRI, null);

            // HashMap<String, Double> regionMap = getRegionParamsFromGridCoverage(inCoverage);
            // height = regionMap.get(ROWS).intValue();
            // width = regionMap.get(COLS).intValue();
            // xRes = regionMap.get(XRES);
            // yRes = regionMap.get(YRES);

            gridGeometry = inRaster.getGridGeometry();
            // GridSampleDimension[] sampleDimensions = inCoverage.getSampleDimensions();
            // double[] noDataValues = sampleDimensions[0].getNoDataValues();
            // System.out.println(noDataValues);
        }

        SimpleFeatureType featureType = inVector.getSchema();

        FeatureExtender fExt = new FeatureExtender(featureType, new String[]{fNew},
                new Class< ? >[]{Double.class});

        Envelope2D inCoverageEnvelope = inRaster.getEnvelope2D();
        outVector = FeatureCollections.newCollection();
        FeatureIterator<SimpleFeature> featureIterator = inVector.features();
        int all = inVector.size();
        pm.beginTask("Extracting raster information...", all);
        while( featureIterator.hasNext() ) {
            SimpleFeature feature = featureIterator.next();
            Geometry geometry = (Geometry) feature.getDefaultGeometry();
            double value = -1;
            Coordinate c;
            Coordinate[] coordinates = geometry.getCoordinates();
            if (getGeometryType(geometry) == GEOMETRYTYPE.POINT
                    || getGeometryType(geometry) == GEOMETRYTYPE.MULTIPOINT) {
                c = coordinates[0];
            } else if (getGeometryType(geometry) == GEOMETRYTYPE.LINE
                    || getGeometryType(geometry) == GEOMETRYTYPE.MULTILINE) {
                if (pPos.trim().equalsIgnoreCase(START)) {
                    c = coordinates[0];
                } else if (pPos.trim().equalsIgnoreCase(END)) {
                    c = coordinates[coordinates.length - 1];
                } else {// (pPos.trim().equalsIgnoreCase(MIDDLE)) {
                    c = coordinates[coordinates.length / 2];
                }
            } else if (getGeometryType(geometry) == GEOMETRYTYPE.POLYGON
                    || getGeometryType(geometry) == GEOMETRYTYPE.MULTIPOLYGON) {
                Point centroid = geometry.getCentroid();
                if (geometry.contains(centroid)) {
                    c = centroid.getCoordinate();
                } else {
                    c = coordinates[0];
                }
            } else {
                throw new ModelsIllegalargumentException("The Geometry type is not supported.",
                        this);
            }

            if (!inCoverageEnvelope.contains(c.x, c.y)) {
                continue;
            }

            GridCoordinates2D gridCoord = gridGeometry.worldToGrid(new DirectPosition2D(c.x, c.y));
            value = inIter.getSampleDouble(gridCoord.x, gridCoord.y, 0);

            // TODO make this better
            if (isNovalue(value) || value >= Float.MAX_VALUE || value <= -Float.MAX_VALUE) {
                value = -9999.0;
            }

            SimpleFeature extendedFeature = fExt.extendFeature(feature, new Object[]{value});

            outVector.add(extendedFeature);
            pm.worked(1);
        }
        featureIterator.close();
        pm.done();

    }
}