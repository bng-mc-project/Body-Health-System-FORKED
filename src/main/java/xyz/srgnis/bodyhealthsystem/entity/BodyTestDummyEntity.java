package xyz.srgnis.bodyhealthsystem.entity;

import net.minecraft.entity.EntityType;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.attribute.DefaultAttributeContainer;
import net.minecraft.entity.attribute.EntityAttributes;
import net.minecraft.entity.damage.DamageSource;
import net.minecraft.entity.data.DataTracker;
import net.minecraft.entity.data.TrackedData;
import net.minecraft.entity.data.TrackedDataHandlerRegistry;
import net.minecraft.entity.mob.MobEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.world.World;

import java.util.Collections;

public class BodyTestDummyEntity extends MobEntity implements BodyHitHighlightTarget {
    private static final TrackedData<String> HIGHLIGHT_PART =
            DataTracker.registerData(BodyTestDummyEntity.class, TrackedDataHandlerRegistry.STRING);
    private static final TrackedData<Integer> HIGHLIGHT_UNTIL_TICK =
            DataTracker.registerData(BodyTestDummyEntity.class, TrackedDataHandlerRegistry.INTEGER);

    private static final int HIGHLIGHT_DURATION_TICKS = 80;

    public BodyTestDummyEntity(EntityType<? extends MobEntity> entityType, World world) {
        super(entityType, world);
        this.setNoGravity(true);
        this.setInvulnerable(true);
    }

    public static DefaultAttributeContainer.Builder createAttributes() {
        return LivingEntity.createLivingAttributes()
                .add(EntityAttributes.GENERIC_MAX_HEALTH, 20.0)
                .add(EntityAttributes.GENERIC_MOVEMENT_SPEED, 0.0)
                .add(EntityAttributes.GENERIC_FOLLOW_RANGE, 0.0);
    }

    @Override
    protected void initGoals() {
    }

    @Override
    protected void initDataTracker() {
        super.initDataTracker();
        this.dataTracker.startTracking(HIGHLIGHT_PART, "");
        this.dataTracker.startTracking(HIGHLIGHT_UNTIL_TICK, 0);
    }

    @Override
    public void tick() {
        super.tick();
        if (!this.getWorld().isClient && this.age > this.dataTracker.get(HIGHLIGHT_UNTIL_TICK)) {
            if (!this.dataTracker.get(HIGHLIGHT_PART).isEmpty()) {
                this.dataTracker.set(HIGHLIGHT_PART, "");
            }
        }
    }

    @Override
    public void setHighlightedPart(Identifier part) {
        if (this.getWorld().isClient) return;
        this.dataTracker.set(HIGHLIGHT_PART, part != null ? part.toString() : "");
        this.dataTracker.set(HIGHLIGHT_UNTIL_TICK, this.age + HIGHLIGHT_DURATION_TICKS);
    }

    @Override
    public Identifier getHighlightedPart() {
        if (this.age > this.dataTracker.get(HIGHLIGHT_UNTIL_TICK)) {
            return null;
        }
        String raw = this.dataTracker.get(HIGHLIGHT_PART);
        return raw.isEmpty() ? null : Identifier.tryParse(raw);
    }

    @Override
    public boolean damage(DamageSource source, float amount) {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public Arm getMainArm() {
        return Arm.RIGHT;
    }

    @Override
    public Iterable<ItemStack> getArmorItems() {
        return Collections.emptyList();
    }

    @Override
    public ItemStack getEquippedStack(EquipmentSlot slot) {
        return ItemStack.EMPTY;
    }

    @Override
    public void equipStack(EquipmentSlot slot, ItemStack stack) {
    }

    @Override
    public void readCustomDataFromNbt(NbtCompound nbt) {
        super.readCustomDataFromNbt(nbt);
        if (nbt.contains("HighlightPart")) {
            this.dataTracker.set(HIGHLIGHT_PART, nbt.getString("HighlightPart"));
        }
        if (nbt.contains("HighlightUntilTick")) {
            this.dataTracker.set(HIGHLIGHT_UNTIL_TICK, nbt.getInt("HighlightUntilTick"));
        }
    }

    @Override
    public void writeCustomDataToNbt(NbtCompound nbt) {
        super.writeCustomDataToNbt(nbt);
        nbt.putString("HighlightPart", this.dataTracker.get(HIGHLIGHT_PART));
        nbt.putInt("HighlightUntilTick", this.dataTracker.get(HIGHLIGHT_UNTIL_TICK));
    }
}
