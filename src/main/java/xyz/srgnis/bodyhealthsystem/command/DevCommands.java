package xyz.srgnis.bodyhealthsystem.command;

import com.mojang.brigadier.CommandDispatcher;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.CommandRegistryAccess;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.math.Vec3d;
import xyz.srgnis.bodyhealthsystem.body.player.BodyProvider;
import xyz.srgnis.bodyhealthsystem.entity.BodyTestDummyEntity;
import xyz.srgnis.bodyhealthsystem.network.ServerNetworking;
import xyz.srgnis.bodyhealthsystem.registry.ModEntities;

import static net.minecraft.server.command.CommandManager.literal;

public class DevCommands {

    public static void initialize() {
        CommandRegistrationCallback.EVENT.register(DevCommands::createCommands);
    }

    public static int spawnDummy(ServerPlayerEntity player) {
        if (player == null || player.getWorld() == null) {
            return 0;
        }

        BodyTestDummyEntity dummy = ModEntities.BODY_TEST_DUMMY.create(player.getWorld());
        if (dummy == null) {
            return 0;
        }

        Vec3d look = player.getRotationVec(1.0f).multiply(2.5);
        dummy.refreshPositionAndAngles(
                player.getX() + look.x,
                player.getY(),
                player.getZ() + look.z,
                player.getYaw(),
                0.0f
        );
        player.getWorld().spawnEntity(dummy);
        return 1;
    }

    private static void createCommands(
            CommandDispatcher<ServerCommandSource> dispatcher,
            CommandRegistryAccess registryAccess,
            CommandManager.RegistrationEnvironment environment
    ) {
        dispatcher.register(literal("bhs_dev")
                .requires(source -> source.hasPermissionLevel(2))
                .executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    context.getSource().sendFeedback(
                            () -> Text.literal(((BodyProvider) player).getBody().toString()),
                            false
                    );
                    return 1;
                })
                .then(literal("reset").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ((BodyProvider) player).getBody().healAll();
                    ServerNetworking.syncBody(player);
                    return 1;
                }))
                .then(literal("damage").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    ((BodyProvider) player).getBody().applyDamageBySource(1, null);
                    ServerNetworking.syncBody(player);
                    return 1;
                }))
                .then(literal("dummy").executes(context -> {
                    ServerPlayerEntity player = context.getSource().getPlayer();
                    if (player == null) {
                        return 0;
                    }
                    if (spawnDummy(player) == 0) {
                        return 0;
                    }
                    context.getSource().sendFeedback(
                            () -> Text.translatable("command.bodyhealthsystem.dummy.spawned"),
                            false
                    );
                    return 1;
                })));
    }
}
