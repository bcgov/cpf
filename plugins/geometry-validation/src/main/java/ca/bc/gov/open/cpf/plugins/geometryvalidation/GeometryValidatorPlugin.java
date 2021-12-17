/*
 * Copyright © 2008-2016, Province of British Columbia
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *         http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package ca.bc.gov.open.cpf.plugins.geometryvalidation;

import ca.bc.gov.open.cpf.plugin.api.BusinessApplicationPlugin;
import ca.bc.gov.open.cpf.plugin.api.RequestParameter;
import ca.bc.gov.open.cpf.plugin.api.Required;
import ca.bc.gov.open.cpf.plugin.api.ResultAttribute;

import com.revolsys.geometry.model.Geometry;
import com.revolsys.geometry.model.GeometryFactory;
import com.revolsys.geometry.model.Point;
import com.revolsys.geometry.operation.valid.GeometryValidationError;
import com.revolsys.geometry.operation.valid.IsValidOp;
import com.revolsys.geometry.precision.MinimumClearance;

/**
 * The GeometryValidatorPlugin converts the geometry to the requested coordinate
 * system and precision model. It then checks to see if the geometry
 * {@link Geometry#isSimple()}, {@link Geometry#isValid()}, and calculates the
 * {@link MinimumClearance#getDistance()}.
 */
@BusinessApplicationPlugin(name = "GeometryValidator",
    description = "The Geometry Validator takes your input geometry and determines if it is simple, valid, and robust. It will also compute the geometry's minimum clearance. The Validator converts your geometry to a coordinate system of your choice then applies a precision model before analysing it. The definition of terms such as simple, valid, robust, and minimum clearance can be found in the detailed service description.",
    descriptionUrl = "http://www.data.gov.bc.ca/dbc/geo/geomark/index.page",
    numRequestsPerWorker = 100, instantModePermission = "permitAll")
public class GeometryValidatorPlugin {
  /** The geometry to validate. */
  private Geometry geometry;

  /** The flag indicating if the converted geometry was isSimple. */
  private boolean isSimple;

  /**
   * The number of coordinate axis to use for the converted geometry (2 for x, 7
   * and 3 for x, y, z).
   */
  private int resultNumAxis;

  /** The scale factor used to round the x, y coordinates. */
  private double resultScaleFactorXy;

  /** The scale factor used to round the z coordinate. */
  private double resultScaleFactorZ;

  /** The EPSG coordinate system id to convert the geometry to. */
  private int resultSrid;

  /** The flag indicating if the converted geometry was valid. */
  private boolean isValid;

  /** The minimum clearance required for the geometry to still be valid. */
  private double minimumClearance;

  /** The flag indicating if the geometry minimum clearance is robust. */
  private boolean isRobust = true;

  /** The description of any geometry validation errors. */
  private String validationError;

  /** The point location of the first geometry validation error. */
  private Point validationLocation;

  /**
   * Perform the geometry conversion and geometry validation.
   */
  public void execute() {
    if (this.geometry == null) {
      throw new IllegalArgumentException("Geometry cannot be null");
    } else {
      final GeometryFactory sourceGeometryFactory = this.geometry.getGeometryFactory();
      if (this.resultSrid == 0) {
        this.resultSrid = sourceGeometryFactory.getCoordinateSystemId();
      }
      if (this.resultNumAxis == 0) {
        this.resultNumAxis = sourceGeometryFactory.getAxisCount();
      }
      if (this.resultScaleFactorXy == 0) {
        this.resultScaleFactorXy = sourceGeometryFactory.getScaleXY();
      }
      if (this.resultScaleFactorZ == 0) {
        this.resultScaleFactorZ = sourceGeometryFactory.getScaleZ();
      }
      final GeometryFactory geometryFactory = GeometryFactory.fixed(this.resultSrid,
        this.resultNumAxis, this.resultScaleFactorXy, this.resultScaleFactorXy,
        this.resultScaleFactorZ);
      this.geometry = geometryFactory.geometry(this.geometry);

      final IsValidOp validOp = new IsValidOp(this.geometry);
      this.isValid = validOp.isValid();
      if (this.isValid) {
        this.validationError = null;
        this.validationLocation = null;
      } else {
        final GeometryValidationError topologyError = validOp.getValidationError();
        this.validationError = topologyError.getMessage();
        final Point point = topologyError.getErrorPoint();
        this.validationLocation = geometryFactory.point(point);
      }
      this.isSimple = this.geometry.isSimple();

      this.minimumClearance = MinimumClearance.getDistance(this.geometry);

      double clearanceScaleFactor;
      double clearanceTolerance;
      if (this.resultScaleFactorXy == 0) {
        if (geometryFactory.isGeographic()) {
          clearanceTolerance = .1;
          clearanceScaleFactor = 10000000;
        } else {
          clearanceTolerance = 0.001;
          clearanceScaleFactor = 1000;
        }
      } else {
        clearanceTolerance = 1 / this.resultScaleFactorXy;
        clearanceScaleFactor = this.resultScaleFactorXy;
      }
      if (Double.compare(this.minimumClearance, clearanceTolerance) < 0) {
        this.isRobust = false;
      }

      if (this.minimumClearance >= clearanceTolerance) {
        this.minimumClearance = Math.ceil(this.minimumClearance * clearanceScaleFactor)
          / clearanceScaleFactor;
      }
      if (Double.isNaN(this.minimumClearance) || Double.isInfinite(this.minimumClearance)
        || this.minimumClearance > 999999999) {
        this.minimumClearance = 999999999;
      }
    }
  }

  /**
   * Get the geometry to validate that has been converted to the required
   * coordinate system and precision model.
   *
   * @return The geometry to validate that has been converted to the required
   *         coordinate system and precision model.
   */
  @ResultAttribute(index = 0)
  public Geometry getGeometry() {
    return this.geometry;
  }

  /**
   * Get the minimum clearance required for the geometry to still be valid.
   *
   * @return The minimum clearance required for the geometry to still be valid.
   */
  @ResultAttribute(index = 6,
      description = "The minimum clearance required for the geometry to still be valid.")
  public double getMinimumClearance() {
    return this.minimumClearance;
  }

  /**
   * Get the description of any geometry validation errors.
   *
   * @return The description of any geometry validation errors.
   */
  @ResultAttribute(index = 3,
      description = "The description of the first geometry validation error.")
  public String getValidationError() {
    return this.validationError;
  }

  /**
   * Get the point location of the first geometry validation error.
   *
   * @return The point location of the first geometry validation error.
   */
  @ResultAttribute(index = 4,
      description = "The point location of the first geometry validation error.")
  public Point getValidationLocation() {
    return this.validationLocation;
  }

  /**
   * Get the flag indicating if the geometry minimum clearance is robust.
   *
   * @return The flag indicating if the geometry minimum clearance is robust.
   */
  @ResultAttribute(index = 5,
      description = "Flag indicating if the geometry minimum clearance is robust.")
  public boolean isIsRobust() {
    return this.isRobust;
  }

  /**
   * Get the flag indicating if the converted geometry was isSimple.
   *
   * @return The flag indicating if the converted geometry was isSimple.
   */
  @ResultAttribute(index = 1,
      description = "Flag indicating if the converted geometry was isSimple.")
  public boolean isIsSimple() {
    return this.isSimple;
  }

  /**
   * Get the flag indicating if the converted geometry was valid.
   *
   * @return The flag indicating if the converted geometry was valid.
   */
  @ResultAttribute(index = 2, description = "Flag indicating if the converted geometry was valid.")
  public boolean isIsValid() {
    return this.isValid;
  }

  /**
   * Set the geometry to validate.
   *
   * @param geometry The geometry to validate.
   */
  @Required
  @RequestParameter(
      description = "The geometry to validate. For single and instant requests enter a WKT encoded geometry or a Geomark URL.")
  public void setGeometry(final Geometry geometry) {
    this.geometry = geometry;
  }

  /**
   * Get the number of coordinate axis to use for the converted geometry (2 for
   * x, 7 and 3 for x, y, z).
   *
   * @param resultNumAxis The number of coordinate axis to use for the converted
   *          geometry (2 for x, 7 and 3 for x, y, z).
   */
  public void setResultNumAxis(final int resultNumAxis) {
    this.resultNumAxis = resultNumAxis;
  }

  /**
   * Get the scale factor used to round the x, y coordinates.
   *
   * @param resultScaleFactorXy The scale factor used to round the x, y coordinates.
   */
  public void setResultScaleFactorXy(final double resultScaleFactorXy) {
    this.resultScaleFactorXy = resultScaleFactorXy;
  }

  /**
   * Get the scale factor used to round the z coordinates.
   *
   * @param resultScaleZ The scale factor used to round the z coordinates.
   */
  public void setResultScaleFactorZ(final double setResultScaleFactorZ) {
    this.resultScaleFactorZ = setResultScaleFactorZ;
  }

  /**
   * Set the EPSG coordinate system id to convert the geometry to.
   *
   * @param resultSrid The EPSG coordinate system id to convert the geometry to.
   */
  public void setResultSrid(final int resultSrid) {
    this.resultSrid = resultSrid;
  }

}
