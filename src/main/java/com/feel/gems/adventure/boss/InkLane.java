package com.feel.gems.adventure.boss;

import net.minecraft.world.entity.Entity;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.level.ClipContext;

/** Shared finite firing lane for the gallery guards and The Painted Verdict. */
final class InkLane {
    private InkLane() { }

    static boolean intersects(Vec3 from, Vec3 end, Vec3 point, double widthSquared) {
        Vec3 lane = end.subtract(from);
        double lengthSquared = lane.lengthSqr();
        if (lengthSquared < 0.01) return false;
        double along = point.subtract(from).dot(lane) / lengthSquared;
        return along >= 0.0 && along <= 1.0
                && point.distanceToSqr(from.add(lane.scale(along))) <= widthSquared;
    }

    static boolean exposed(ServerLevel world, Entity caster, Vec3 from, Vec3 end,
                           Vec3 point, double widthSquared) {
        return intersects(from, end, point, widthSquared)
                && world.clip(new ClipContext(from, point, ClipContext.Block.COLLIDER,
                ClipContext.Fluid.NONE, caster)).getType() == HitResult.Type.MISS;
    }

    static void draw(ServerLevel world, Entity caster, Vec3 from, Vec3 end,
                     DustParticleOptions ink, int steps) {
        Vec3 lane = end.subtract(from);
        for (int i = 1; i <= steps; i++) {
            Vec3 point = from.add(lane.scale((double) i / steps));
            if (world.clip(new ClipContext(from, point, ClipContext.Block.COLLIDER,
                    ClipContext.Fluid.NONE, caster)).getType() != HitResult.Type.MISS) break;
            world.sendParticles(ink, point.x, point.y, point.z, 1, 0, 0, 0, 0);
        }
    }
}
