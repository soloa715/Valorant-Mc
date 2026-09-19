package com.valorantmc.mod.server;

import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.Unbreakable;

import java.util.List;

public class Weapon {

    public static final String NBT_WEAPON_TYPE    = "valorantmc_weapon_type";
    public static final String NBT_WEAPON_AMMO    = "valorantmc_weapon_ammo";
    public static final String NBT_WEAPON_RESERVE = "valorantmc_weapon_reserve";

    private final WeaponType type;
    private int     currentAmmo;
    private int     reserveAmmo;
    private boolean reloading;

    public Weapon(WeaponType type) {
        this.type        = type;
        this.currentAmmo = type.isMelee() ? 0 : type.getMagazineSize();
        this.reserveAmmo = type.isMelee() ? 0 : type.getMagazineSize() * 3;
        this.reloading   = false;
    }

    public boolean canShoot() {
        if (type.isMelee()) return true;
        return !reloading && currentAmmo > 0;
    }

    public boolean consumeBullet() {
        if (type.isMelee()) return true;
        if (currentAmmo <= 0) return false;
        currentAmmo--;
        return true;
    }

    public boolean reload() {
        if (reloading || reserveAmmo <= 0) return false;
        if (currentAmmo == type.getMagazineSize()) return false;
        int needed = type.getMagazineSize() - currentAmmo;
        int refill = Math.min(needed, reserveAmmo);
        currentAmmo  += refill;
        reserveAmmo  -= refill;
        return true;
    }

    public void refillAmmo() {
        currentAmmo = type.getMagazineSize();
        reserveAmmo = type.getMagazineSize() * 3;
    }

    public ItemStack toItemStack() {
        ItemStack stack = new ItemStack(type.getItem());

        stack.set(DataComponents.CUSTOM_MODEL_DATA,
                new CustomModelData(
                        List.of((float) type.getCustomModelId()),
                        List.of(), List.of(), List.of()));

        stack.set(DataComponents.UNBREAKABLE, new Unbreakable(false));

        stack.set(DataComponents.CUSTOM_NAME,
                Component.literal(type.getDisplayName()).withStyle(s -> s.withItalic(false)));

        CompoundTag nbt = new CompoundTag();
        nbt.putString(NBT_WEAPON_TYPE,    type.name());
        nbt.putInt(NBT_WEAPON_AMMO,       currentAmmo);
        nbt.putInt(NBT_WEAPON_RESERVE,    reserveAmmo);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(nbt));

        return stack;
    }

    // ── Static helpers ────────────────────────────────────────────────────────

    public static WeaponType getWeaponType(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return null;
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return null;
        String val = comp.copyTag().getString(NBT_WEAPON_TYPE);
        if (val.isEmpty()) return null;
        try { return WeaponType.valueOf(val); } catch (Exception e) { return null; }
    }

    public static int getStoredAmmo(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return -1;
        CompoundTag nbt = comp.copyTag();
        return nbt.contains(NBT_WEAPON_AMMO) ? nbt.getInt(NBT_WEAPON_AMMO) : -1;
    }

    public static int getStoredReserve(ItemStack stack) {
        if (stack == null || stack.isEmpty()) return -1;
        CustomData comp = stack.get(DataComponents.CUSTOM_DATA);
        if (comp == null) return -1;
        CompoundTag nbt = comp.copyTag();
        return nbt.contains(NBT_WEAPON_RESERVE) ? nbt.getInt(NBT_WEAPON_RESERVE) : -1;
    }

    // ── Getters / Setters ─────────────────────────────────────────────────────
    public WeaponType getType()              { return type;        }
    public int        getCurrentAmmo()       { return currentAmmo; }
    public int        getReserveAmmo()       { return reserveAmmo; }
    public boolean    isReloading()          { return reloading;   }
    public void       setReloading(boolean v){ reloading   = v;    }
    public void       setCurrentAmmo(int v)  { currentAmmo = v;    }
    public void       setReserveAmmo(int v)  { reserveAmmo = v;    }
}
