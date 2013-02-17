/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.stuy.subsystems;

import com.sun.squawk.util.MathUtils;
import edu.stuy.Constants;
import edu.stuy.util.Gamepad;
import edu.stuy.util.NetworkIO;
import edu.wpi.first.wpilibj.ADXL345_I2C;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.PIDController;
import edu.wpi.first.wpilibj.PIDOutput;
import edu.wpi.first.wpilibj.Talon;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj.tables.TableKeyNotDefinedException;
import java.util.Timer;
import java.util.TimerTask;
import java.util.Vector;

/**
 * 
 * @author kevin, abdullah, eric
 */

public class Tilter {
    private static Tilter instance;
    private Talon leadscrew;
    /**
     * Mount upside down, with the y-axis positive arrow pointed towards the
     * mouth of the shooter.
     */
    private ADXL345_I2C accel;
    
    private PIDController controller;
    private Encoder enc;
    private double initialLeadLength;
    private NetworkIO net;
    
    private boolean isCVAiming = false;
    
    private Vector accelMeasurements;
    private Timer updateMeasurements;
    private final int ACCEL_MEASUREMENT_SIZE = 10; //Number of measurements to average
    private final int ACCEL_UPDATE_PERIOD = 10; //Time between measurements. DO NOT USE ANY VALUE LESS THAN 10.
    
    private Tilter() {
        leadscrew = new Talon(Constants.TILTER_CHANNEL);
        accel = new ADXL345_I2C(Constants.ACCELEROMETER_CHANNEL, ADXL345_I2C.DataFormat_Range.k2G);
        accelMeasurements = new Vector();
        updateAccel();
        enc = new Encoder(Constants.TILT_ENCODER_A,Constants.TILT_ENCODER_B);
        initialLeadLength = getLeadscrewLength(getAbsoluteAngle() * Math.PI / 180);
        enc.setDistancePerPulse(Constants.TILTER_DISTANCE_PER_PULSE);
        enc.start();
        net = new NetworkIO();
        controller = new PIDController(Constants.PVAL_T, Constants.IVAL_T, Constants.DVAL_T, enc, new PIDOutput() {
            public void pidWrite(double output) {
                setLeadscrewMotor(output);
            }
        });
        controller.setPercentTolerance(0.01);
        controller.disable();
        updatePID();
        printAngle();
    }
    
    public static Tilter getInstance() {
        if (instance == null) {
            instance = new Tilter();
        }
        return instance;
    }
    
    public void enableAngleControl() {
        controller.enable();
    }
    
    public void disableAngleControl() {
        controller.disable();
    }
    
    public boolean isCVAiming() {
        return isCVAiming;
    }
    
    public void updatePID() {
        double pVal;
        double iVal;
        double dVal;
        try {
            pVal = SmartDashboard.getNumber("Tilter P");
            iVal = SmartDashboard.getNumber("Tilter I");
            dVal = SmartDashboard.getNumber("Tilter D");
            controller.setPID(pVal, iVal, dVal);
        } catch (TableKeyNotDefinedException e) {
            SmartDashboard.putNumber("Tilter P", 0.0);
            SmartDashboard.putNumber("Tilter I", 0.0);
            SmartDashboard.putNumber("Tilter D", 0.0);
        }
    }
    
    public void setRelativeAngle(double deltaAngle) {
        double initialAngle = getShooterAngle();
        double absoluteAngle = deltaAngle + initialAngle;
        setAbsoluteAngle(absoluteAngle);
    }
    
    public void setAbsoluteAngle(double angle) {
        double leadScrewLength = getLeadscrewLength(angle);
        double deltaLeadScrewLength = leadScrewLength - getLeadscrewLength();
        controller.setSetpoint(deltaLeadScrewLength + enc.getDistance());
    }

    private void setLeadscrewMotor(double speed){
        leadscrew.set(speed);
    }
    
    public void stopLeadscrewMotor() {
        leadscrew.set(0);
    }
    
     /**
     * Starts the update thread.
     */
    public void updateAccel() {
        accelStop();
        updateMeasurements = new Timer();
        updateMeasurements.schedule(new TimerTask() {
            public void run() {
                synchronized (Tilter.this) {
                    accelMeasurements.addElement(new Double(getInstantAngle()));
                    if (accelMeasurements.size() > ACCEL_MEASUREMENT_SIZE) {
                        accelMeasurements.removeElementAt(0);
                    }
                }
            }
        }, 0, ACCEL_UPDATE_PERIOD);
    }
    
    public void accelStop() {
        if (updateMeasurements != null) {
            updateMeasurements.cancel();
        }
    }
    
    public void resetAccelMeasurements() {
        accelMeasurements.removeAllElements();
    }

    public double getCVRelativeAngle () {
        double relativeAngle = net.getCurrent();
        return relativeAngle;
    }
    
    public double getXAcceleration() {
        return accel.getAcceleration(ADXL345_I2C.Axes.kX);
    }
    
    public double getYAcceleration() {
        return accel.getAcceleration(ADXL345_I2C.Axes.kY);
    }
    
    public double getZAcceleration() {
        return accel.getAcceleration(ADXL345_I2C.Axes.kZ);
    }
    
    /**
     * Gets the angle from the measurements of the last 10 accelerations
     */
    public double getAbsoluteAngle() {
        if (accelMeasurements.isEmpty()) {
            return 0;
        }
        double sum = 0;
        double min = ((Double) accelMeasurements.elementAt(0)).doubleValue();
        double max = min;
        synchronized (this) {
            for (int i = 0; i < accelMeasurements.size(); i++) {
                double measure = ((Double) accelMeasurements.elementAt(i)).doubleValue();
                sum += measure;
                min = (min < measure) ? min : measure;
                max = (max > measure) ? max : measure;
            }
            if (accelMeasurements.size() >= 3) {
                return (sum - min - max) / (accelMeasurements.size() - 2); //Removes the max and min values to get rid of any weird fluctuations
            }
            else {
                return sum / accelMeasurements.size();
            }
        }
    }
    
    /**
     * Gets instantaneous angle
     */
    public double getInstantAngle() {
        return MathUtils.atan(getYAcceleration() / -getZAcceleration()) * 180.0 / Math.PI;
    }
    
    public double getLeadscrewLength() {
        return initialLeadLength + enc.getDistance();
    }
    
    private double square(double x) {
        return x * x;
    }
    
    /**
     * ======== v(q) uses distance formula ========
     * v(q) = sqrt((zcosq - x)^2 + (zcosq - y)^2)
     * v = leadscrew length
     * q = angle of shooter
     * z = distance from pivot to where leadscrew hits shooter
     * x = distance from pivot to base of base of leadscrew
     * y = height of the leadscrew
     */
    public double getLeadscrewLength(double angle) {
        return Math.sqrt(square(Constants.SHOOTER_DISTANCE_TO_LEADSCREW * Math.cos(angle) - Constants.DISTANCE_TO_LEADSCREW_BASE)
                + square(Constants.SHOOTER_DISTANCE_TO_LEADSCREW * Math.sin(angle) - Constants.LEADSCREW_HEIGHT));
    }
    
    /**
     * ======== q(v) adds two angles ========
     * q(v) = atan(y/x) + acos( (v^2 + x^2 + y^2 - z^2) / (2vsqrt(x^2 + y^2)) )
     * variables are defined above
     */
    public double getShooterAngle() {
        double leadscrewLength = getLeadscrewLength();
        double heightSquared = square(Constants.LEADSCREW_HEIGHT);
        double baseSquared = square(Constants.DISTANCE_TO_LEADSCREW_BASE);
        double hypSquared = square(Constants.SHOOTER_DISTANCE_TO_LEADSCREW);
        return MathUtils.atan(Constants.LEADSCREW_HEIGHT / Constants.DISTANCE_TO_LEADSCREW_BASE) + 
               MathUtils.acos((square(leadscrewLength) + baseSquared + heightSquared - hypSquared) / 
               (2 * leadscrewLength * Math.sqrt(baseSquared + heightSquared)));
    }
    
    public void manualTilterControl(Gamepad gamepad) {
        if (gamepad.getBottomButton()) {
            isCVAiming = true;
            setRelativeAngle(getCVRelativeAngle());
            enableAngleControl();
        }
        else if (gamepad.getTopButton()) {
            isCVAiming = false;
            setAbsoluteAngle(Constants.FEEDER_STATION_ANGLE);
            enableAngleControl();
        }
        else {
            isCVAiming = false;
            disableAngleControl();
            leadscrew.set(gamepad.getRightY());
        }
    }
    
    public void printAngle() {
        if (getCVRelativeAngle() != 694) {
            SmartDashboard.putNumber("CV Angle", getCVRelativeAngle());
            SmartDashboard.putNumber("Absolute Angle", getAbsoluteAngle());
            SmartDashboard.putNumber("LS-Based Angle", getShooterAngle());
            SmartDashboard.putNumber("Instant Angle", getInstantAngle());
        }
        else {
            SmartDashboard.putString("CV Angle", "DRIPTO THE ANGLE'S SMOKING!");
            SmartDashboard.putNumber("Absolute Angle", getAbsoluteAngle());
            SmartDashboard.putNumber("LS-Based Angle", getShooterAngle());
            SmartDashboard.putNumber("Instant Angle", getInstantAngle());
        }
    }
}
