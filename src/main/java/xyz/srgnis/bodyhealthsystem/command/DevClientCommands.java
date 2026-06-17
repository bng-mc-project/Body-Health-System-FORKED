package xyz.srgnis.bodyhealthsystem.command;

import net.fabricmc.fabric.api.client.command.v2.ClientCommandManager;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.minecraft.client.MinecraftClient;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

public final class DevClientCommands {
    private DevClientCommands() {}

    public static void initialize() {
        ClientCommandRegistrationCallback.EVENT.register((dispatcher, registryAccess) -> dispatcher.register(
                ClientCommandManager.literal("bhs_dev")
                        .then(ClientCommandManager.literal("dummy").executes(context -> runDummy(context.getSource())))
        ));
    }

    private static int runDummy(FabricClientCommandSource source) {
        MinecraftClient client = source.getClient();
        if (client.player == null || client.getServer() == null) {
            source.sendError(Text.translatable("command.bodyhealthsystem.dummy.needs_world"));
            return 0;
        }

        client.getServer().execute(() -> {
            ServerPlayerEntity player = client.getServer().getPlayerManager().getPlayer(client.player.getUuid());
            if (player == null) {
                return;
            }
            if (DevCommands.spawnDummy(player) != 0) {
                source.sendFeedback(Text.translatable("command.bodyhealthsystem.dummy.spawned"));
            }
        });
        return 1;
    }
}
