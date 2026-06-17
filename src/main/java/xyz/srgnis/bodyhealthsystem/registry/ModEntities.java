package xyz.srgnis.bodyhealthsystem.registry;

import net.fabricmc.fabric.api.object.builder.v1.entity.FabricDefaultAttributeRegistry;
import net.fabricmc.fabric.api.object.builder.v1.entity.FabricEntityTypeBuilder;
import net.minecraft.entity.EntityDimensions;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.SpawnGroup;
import net.minecraft.registry.Registries;
import net.minecraft.registry.Registry;
import xyz.srgnis.bodyhealthsystem.BHSMain;
import xyz.srgnis.bodyhealthsystem.entity.BodyTestDummyEntity;

public final class ModEntities {
    public static final EntityType<BodyTestDummyEntity> BODY_TEST_DUMMY = Registry.register(
            Registries.ENTITY_TYPE,
            BHSMain.id("body_test_dummy"),
            FabricEntityTypeBuilder.<BodyTestDummyEntity>create(SpawnGroup.MISC, BodyTestDummyEntity::new)
                    .dimensions(EntityDimensions.fixed(0.6f, 1.8f))
                    .trackRangeBlocks(64)
                    .trackedUpdateRate(2)
                    .build()
    );

    private ModEntities() {}

    public static void registerEntities() {
        FabricDefaultAttributeRegistry.register(BODY_TEST_DUMMY, BodyTestDummyEntity.createAttributes());
    }
}
