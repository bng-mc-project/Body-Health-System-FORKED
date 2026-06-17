package xyz.srgnis.bodyhealthsystem.client.render;

import net.minecraft.client.render.entity.BipedEntityRenderer;
import net.minecraft.client.render.entity.EntityRendererFactory;
import net.minecraft.client.render.entity.model.BipedEntityModel;
import net.minecraft.client.render.entity.model.EntityModelLayers;
import net.minecraft.util.Identifier;
import xyz.srgnis.bodyhealthsystem.entity.BodyTestDummyEntity;

public class BodyTestDummyEntityRenderer extends BipedEntityRenderer<BodyTestDummyEntity, BipedEntityModel<BodyTestDummyEntity>> {
    private static final Identifier TEXTURE = new Identifier("minecraft", "textures/entity/steve.png");

    public BodyTestDummyEntityRenderer(EntityRendererFactory.Context context) {
        super(context, new BipedEntityModel<>(context.getPart(EntityModelLayers.PLAYER)), 0.5f);
    }

    @Override
    public Identifier getTexture(BodyTestDummyEntity entity) {
        return TEXTURE;
    }
}
