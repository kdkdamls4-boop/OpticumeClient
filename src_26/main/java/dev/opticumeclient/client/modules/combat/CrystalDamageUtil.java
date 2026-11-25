package dev.opticumeclient.client.modules.combat;

import dev.opticumeclient.client.util.InventoryUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

/**
 * Accurate explosion damage calculator for EndCrystal (power 6.0) and RespawnAnchor (power 5.0).
 * Accounts for distance falloff, difficulty scaling, multi-point exposure raycasts,
 * armor & toughness, Protection / Blast Protection EPF, and Resistance effect.
 */
public final class CrystalDamageUtil {
    private CrystalDamageUtil() {
    }

    public static float calculateCrystalDamage(LivingEntity target, Vec3 explosionPos) {
        return calculateExplosionDamage(target, explosionPos, 6.0f);
    }

    public static float calculateAnchorDamage(LivingEntity target, Vec3 explosionPos) {
        return calculateExplosionDamage(target, explosionPos, 5.0f);
    }

    public static float calculateExplosionDamage(LivingEntity target, Vec3 explosionPos, float power) {
        Minecraft mc = Minecraft.getInstance();
        if (target == null || mc.level == null || !target.isAlive()) return 0.0f;

        double doubleRadius = power * 2.0;
        double dist = target.position().distanceTo(explosionPos);
        if (dist > doubleRadius) return 0.0f;

        double distFactor = dist / doubleRadius;
        double exposure = getSeenPercent(mc.level, explosionPos, target);
        double impact = (1.0 - distFactor) * exposure;
        float damage = (float) ((impact * impact + impact) / 2.0 * 7.0 * doubleRadius + 1.0);

        // Difficulty scaling
        Difficulty diff = mc.level.getDifficulty();
        if (diff == Difficulty.PEACEFUL) return 0.0f;
        if (diff == Difficulty.EASY) damage = Math.min(damage / 2.0f + 1.0f, damage);
        else if (diff == Difficulty.HARD) damage = damage * 1.5f;

        // Armor & Toughness
        double armor = target.getArmorValue();
        double toughness = target.getAttributeValue(Attributes.ARMOR_TOUGHNESS);
        float f = (float) (2.0 + toughness / 4.0);
        float g = (float) Math.max(armor - damage / f, armor * 0.2);
        damage *= 1.0f - Math.min(20.0f, g) / 25.0f;

        // Resistance potion effect
        MobEffectInstance resistance = target.getEffect(MobEffects.RESISTANCE);
        if (resistance != null) {
            int amp = resistance.getAmplifier() + 1;
            damage *= Math.max(0.0f, 1.0f - amp * 0.2f);
        }

        // Enchantment Protection Factor (Protection = 1 EPF/lvl, Blast Protection = 2 EPF/lvl, max 20 EPF)
        int epf = 0;
        for (EquipmentSlot slot : new EquipmentSlot[]{
                EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET}) {
            ItemStack armorPiece = target.getItemBySlot(slot);
            if (!armorPiece.isEmpty()) {
                epf += InventoryUtil.enchantLevel(armorPiece, Enchantments.PROTECTION);
                epf += InventoryUtil.enchantLevel(armorPiece, Enchantments.BLAST_PROTECTION) * 2;
            }
        }
        epf = Math.min(20, epf);
        damage *= 1.0f - epf / 25.0f;

        return Math.max(0.0f, damage);
    }

    private static double getSeenPercent(Level level, Vec3 explosionPos, LivingEntity target) {
        AABB box = target.getBoundingBox();
        double dx = 1.0 / ((box.maxX - box.minX) * 2.0 + 1.0);
        double dy = 1.0 / ((box.maxY - box.minY) * 2.0 + 1.0);
        double dz = 1.0 / ((box.maxZ - box.minZ) * 2.0 + 1.0);
        double offX = (1.0 - Math.floor(1.0 / dx) * dx) / 2.0;
        double offZ = (1.0 - Math.floor(1.0 / dz) * dz) / 2.0;

        if (dx < 0.0 || dy < 0.0 || dz < 0.0) return 0.0;

        int clear = 0;
        int total = 0;
        for (double fx = 0.0; fx <= 1.0; fx += dx) {
            for (double fy = 0.0; fy <= 1.0; fy += dy) {
                for (double fz = 0.0; fz <= 1.0; fz += dz) {
                    double px = box.minX + (box.maxX - box.minX) * fx + offX;
                    double py = box.minY + (box.maxY - box.minY) * fy;
                    double pz = box.minZ + (box.maxZ - box.minZ) * fz + offZ;
                    HitResult hit = level.clip(new ClipContext(
                            new Vec3(px, py, pz),
                            explosionPos,
                            ClipContext.Block.COLLIDER,
                            ClipContext.Fluid.NONE,
                            target
                    ));
                    if (hit.getType() == HitResult.Type.MISS) {
                        clear++;
                    }
                    total++;
                }
            }
        }
        return total == 0 ? 0.0 : (double) clear / total;
    }
}
