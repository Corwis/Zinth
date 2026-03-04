package net.zanoria.zinth.snapshot;

import java.util.UUID;

/**
 * Immutable snapshot of a player's state at a specific tick.
 * Contains only primitives - no NMS or Bukkit objects.
 */
public record PlayerSnapshot(
    UUID playerId,
    int entityId,

    // Position
    double x, double y, double z,
    double lastX, double lastY, double lastZ,
    double deltaX, double deltaY, double deltaZ,
    double deltaXZ,

    // Rotation
    float yaw, float pitch,
    float lastYaw, float lastPitch,
    float deltaYaw, float deltaPitch,

    // Velocity
    double velX, double velY, double velZ,

    // State flags
    boolean onGround,
    boolean sprinting,
    boolean sneaking,
    boolean swimming,
    boolean gliding,

    // Misc
    int ping,
    float fallDistance,
    int chunkX, int chunkZ,
    long tickCaptured,
    String worldId
) {
    public static PlayerSnapshot of(
        UUID playerId, int entityId,
        double x, double y, double z,
        double lastX, double lastY, double lastZ,
        float yaw, float pitch,
        float lastYaw, float lastPitch,
        double velX, double velY, double velZ,
        boolean onGround, boolean sprinting, boolean sneaking,
        boolean swimming, boolean gliding,
        int ping, float fallDistance,
        long tickCaptured, String worldId
    ) {
        double deltaX = x - lastX;
        double deltaY = y - lastY;
        double deltaZ = z - lastZ;
        double deltaXZ = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);

        return new PlayerSnapshot(
            playerId, entityId,
            x, y, z, lastX, lastY, lastZ,
            deltaX, deltaY, deltaZ, deltaXZ,
            yaw, pitch, lastYaw, lastPitch,
            yaw - lastYaw, pitch - lastPitch,
            velX, velY, velZ,
            onGround, sprinting, sneaking, swimming, gliding,
            ping, fallDistance,
            (int)((long) Math.floor(x) >> 4), (int)((long) Math.floor(z) >> 4),
            tickCaptured, worldId
        );
    }
}
