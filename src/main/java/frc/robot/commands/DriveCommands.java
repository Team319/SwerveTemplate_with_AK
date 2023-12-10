// Copyright 2021-2023 FRC 6328
// http://github.com/Mechanical-Advantage
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// version 3 as published by the Free Software Foundation or
// available in the root directory of this project.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.

package frc.robot.commands;

import edu.wpi.first.math.MathUtil;
import edu.wpi.first.math.geometry.Pose2d;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.geometry.Transform2d;
import edu.wpi.first.math.geometry.Translation2d;
import edu.wpi.first.math.kinematics.ChassisSpeeds;
import edu.wpi.first.wpilibj2.command.Command;
import edu.wpi.first.wpilibj2.command.Commands;
import frc.robot.subsystems.drive.Drive;
import java.util.function.DoubleSupplier;

public class DriveCommands {
  private static final double DEADBAND = 0.2;
  private static final double JOYSTICK_GOVERNOR = 0.3; // this value must not exceed 1.0
  private static final double THROTTLE_GOVERNOR = 1.0 - JOYSTICK_GOVERNOR;

  private DriveCommands() {}

  /**
   * Field relative drive command using two joysticks (controlling linear and angular velocities).
   */
  public static Command joystickDrive(
      Drive drive,
      DoubleSupplier xSupplier,
      DoubleSupplier ySupplier,
      DoubleSupplier omegaSupplier,
      DoubleSupplier throttleSupplier) {
    return Commands.run(
        () -> {
          // Apply deadband
          double linearMagnitude =
              MathUtil.applyDeadband(
                  Math.hypot(xSupplier.getAsDouble(), ySupplier.getAsDouble()),
                  DEADBAND); // get the magnitude of the joystick

          Rotation2d linearDirection =
              new Rotation2d(xSupplier.getAsDouble(), ySupplier.getAsDouble());

          double omega = MathUtil.applyDeadband(omegaSupplier.getAsDouble(), DEADBAND);

          double throttle = MathUtil.applyDeadband(throttleSupplier.getAsDouble(), DEADBAND);

          //  Old implementation : just square values
          // linearMagnitude = linearMagnitude * linearMagnitude;
          // omega = Math.copySign(omega * omega, omega);

          // New implementation : square values and apply relative throttle
          // Note : we don't care about the magnitude of the throttle, as we have the
          // linearDirection to apply later
          // Note : only apply throttle if we have provided a linear magnitude

          linearMagnitude = (linearMagnitude * linearMagnitude * JOYSTICK_GOVERNOR);
          if (linearMagnitude > 0.0 && throttle > 0.0) {
            linearMagnitude +=
                Math.copySign(throttleSupplier.getAsDouble() * THROTTLE_GOVERNOR, linearMagnitude);
          }

          // Note : we need to consider the sign as we don't have a linearDirection for the right
          // joystick axis
          omega = Math.copySign((omega * omega * JOYSTICK_GOVERNOR), omega);
          if (omega != 0.0 && throttle > 0.0) {
            omega += Math.copySign(throttleSupplier.getAsDouble() * THROTTLE_GOVERNOR, omega);
          }

          // Calcaulate new linear velocity
          Translation2d linearVelocity =
              new Pose2d(new Translation2d(), linearDirection)
                  .transformBy(new Transform2d(linearMagnitude, 0.0, new Rotation2d()))
                  .getTranslation();

          // Convert to field relative speeds & send command
          drive.runVelocity(
              ChassisSpeeds.fromFieldRelativeSpeeds(
                  linearVelocity.getX() * drive.getMaxLinearSpeedMetersPerSec(),
                  linearVelocity.getY() * drive.getMaxLinearSpeedMetersPerSec(),
                  omega * drive.getMaxAngularSpeedRadPerSec(),
                  drive.getRotation()));
        },
        drive);
  }
}
