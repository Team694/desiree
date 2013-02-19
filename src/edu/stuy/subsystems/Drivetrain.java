/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package edu.stuy.subsystems;

import edu.stuy.Constants;
import edu.stuy.util.Gamepad;
import edu.wpi.first.wpilibj.Compressor;
import edu.wpi.first.wpilibj.Encoder;
import edu.wpi.first.wpilibj.Gyro;
import edu.wpi.first.wpilibj.PIDController;
import edu.wpi.first.wpilibj.PIDOutput;
import edu.wpi.first.wpilibj.RobotDrive;
import edu.wpi.first.wpilibj.Timer;

/**
 * The robot drivetrain.
 *
 * @author kevin, R4D4
 */

public class Drivetrain {

    double driveStraightSpeed = 0.8;
    
    private static Drivetrain instance;
    private RobotDrive drivetrain;
    private Gyro gyro;
    private Compressor compressor;
    PIDController forwardController;
    PIDController backwardController;
    private Encoder encoderRight;
    private Encoder encoderLeft;

    private Drivetrain() {
        drivetrain = new RobotDrive(Constants.DRIVETRAIN_LEFT_CHANNEL, Constants.DRIVETRAIN_RIGHT_CHANNEL);
        drivetrain.setSafetyEnabled(false);
        gyro = new Gyro(Constants.GYRO_CHANNEL);
        gyro.setSensitivity(0.007);
        gyroReset();

        encoderLeft = new Encoder(Constants.DRIVE_ENCODER_LEFT_A_CHANNEL, Constants.DRIVE_ENCODER_LEFT_B_CHANNEL);
        encoderRight = new Encoder(Constants.DRIVE_ENCODER_RIGHT_A_CHANNEL, Constants.DRIVE_ENCODER_RIGHT_B_CHANNEL);
        encoderLeft.setDistancePerPulse(Constants.ENCODER_DISTANCE_PER_PULSE);
        encoderRight.setDistancePerPulse(Constants.ENCODER_DISTANCE_PER_PULSE);
        encoderLeft.start();
        encoderRight.start();

        compressor = new Compressor(Constants.PRESSURE_SWITCH_CHANNEL, Constants.COMPRESSOR_RELAY_CHANNEL);
        compressor.start();

        forwardController = new PIDController(Constants.PVAL_D, Constants.IVAL_D, Constants.DVAL_D, gyro, new PIDOutput() {
            public void pidWrite(double output) {
                drivetrain.arcadeDrive(driveStraightSpeed, output);
            }
        }, 0.005);
        forwardController.setInputRange(-360.0, 360.0);
        forwardController.setPercentTolerance(1 / 90. * 100);
        forwardController.disable();

        backwardController = new PIDController(Constants.PVAL_D, Constants.IVAL_D, Constants.DVAL_D, gyro, new PIDOutput() {
            public void pidWrite(double output) {
                drivetrain.arcadeDrive(-driveStraightSpeed, output);
            }
        }, 0.005);
        backwardController.setInputRange(-360.0, 360.0);
        backwardController.setPercentTolerance(1 / 90. * 100);
        backwardController.disable();
    }

    public static Drivetrain getInstance() {
        if (instance == null) {
            instance = new Drivetrain();
        }
        return instance;
    }

    public void tankDrive(double leftValue, double rightValue) {
        drivetrain.tankDrive(leftValue, rightValue);
    }

    /**
     * Tank drive using a gamepad's left and right analog sticks.
     *
     * @param gamepad Gamepad to tank drive with
     */
    public void tankDrive(Gamepad gamepad) {
        tankDrive(gamepad.getLeftY(), gamepad.getRightY());
    }
    
    public void reset() {
        tankDrive(0.0, 0.0);
        gyroReset();
        resetEncoders();
    }

    public double getAngle() {
        return gyro.getAngle();
    }

    public void gyroReset() {
        gyro.reset();
    }
    
    public void stopCompressor() {
        compressor.stop();
    }

    public boolean getPressure() {
        return compressor.getPressureSwitchValue();
    }
    /* 
     * Allows drivetrain to move forward by enabling the PID controllers.
     */
    public void enableDriveStraight(boolean forward) {
        if (forward) {
            forwardController.setSetpoint(0);
            forwardController.enable();
        } else {
            backwardController.setSetpoint(0);
            backwardController.enable();
        }
    }
    /*
     * Disables both of the PID controllers (forward and backward).
     */
    public void disableDriveStraight() {
        forwardController.disable();
        backwardController.disable();
    }

    public double getLeftEnc() {
        return encoderLeft.getDistance();
    }

    public double getRightEnc() {
        return encoderRight.getDistance();
    }
    /*
     * Drives forward in inches for less than seven seconds. 
     */
    public void driveStraightInches(double inches) {
        resetEncoders();
        double startTime = Timer.getFPGATimestamp();
        boolean fwd = inches >= 0;
        enableDriveStraight(fwd);
        // Do nothing because drive straight is enabled.
        while (((fwd && getAvgDistance() < inches)
                || (!fwd && getAvgDistance() > inches))
                && (Timer.getFPGATimestamp() - startTime) < Constants.DRIVE_STRAIGHT_TIMEOUT) {}
        disableDriveStraight();
    }

    public void resetEncoders() {
        encoderLeft.reset();
        encoderRight.reset();
    }

    public double getAvgDistance() {
        return (getLeftEnc() + getRightEnc()) / 2.0;
    }
    /*
     * Turns around one hundred eighty degrees in half a second.
     */
    public void spin180() {
        tankDrive(-1, 1);
        Timer.delay(Constants.SPIN_TIME);
        tankDrive(0, 0);
    }
}
