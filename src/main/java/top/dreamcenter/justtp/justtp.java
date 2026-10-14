package top.dreamcenter.justtp;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.logging.LogUtils;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Mod(justtp.MODID)
public class justtp {
    public static final String MODID = "justtp";
    public static final Logger LOGGER = LogUtils.getLogger();

    // 用于存储传送请求: Key 为被请求者(接收者)UUID, Value 为发起者UUID
    private static final Map<UUID, UUID> TP_REQUESTS = new ConcurrentHashMap<>();

    public justtp(IEventBus modEventBus, ModContainer modContainer) {
        // 将当前类注册到 NeoForge 全局事件总线上 (用于监听 RegisterCommandsEvent)
        NeoForge.EVENT_BUS.register(this);
    }

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();

        // 1. 注册 /tpa <targetPlayer> (所有人可用 permissionLevel = 0)
        dispatcher.register(
                Commands.literal("tpa")
                        .then(Commands.argument("target", EntityArgument.player())
                                .executes(this::executeTpa)
                        )
        );

        // 2. 注册 /tpaccept (接受传送)
        dispatcher.register(
                Commands.literal("tpaccept")
                        .executes(this::executeTpAccept)
        );

        // 3. 注册 /tpdeny (拒绝传送)
        dispatcher.register(
                Commands.literal("tpdeny")
                        .executes(this::executeTpDeny)
        );
    }

    /**
     * 发送传送请求 /tpa <target>
     */
    private int executeTpa(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer requester = context.getSource().getPlayerOrException();
        ServerPlayer target = EntityArgument.getPlayer(context, "target");

        if (requester.getUUID().equals(target.getUUID())) {
            requester.sendSystemMessage(Component.literal("§c你不能向自己发送传送请求！"));
            return 0;
        }

        // 记录请求关系 (目标玩家 UUID -> 发起玩家 UUID)
        TP_REQUESTS.put(target.getUUID(), requester.getUUID());

        requester.sendSystemMessage(Component.literal("§a已向 " + target.getScoreboardName() + " 发送传送请求。"));
        target.sendSystemMessage(Component.literal("§e玩家 " + requester.getScoreboardName() + " 请求传送至你所在的位置。"));
        target.sendSystemMessage(Component.literal("§a输入 §f/tpaccept §a接受，或输入 §c/tpdeny §a拒绝。"));

        return 1;
    }

    /**
     * 接受传送请求 /tpaccept
     */
    private int executeTpAccept(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        UUID requesterUuid = TP_REQUESTS.remove(target.getUUID());

        if (requesterUuid == null) {
            target.sendSystemMessage(Component.literal("§c你当前没有任何待处理的传送请求！"));
            return 0;
        }

        // 获取发起者玩家实例
        ServerPlayer requester = target.server.getPlayerList().getPlayer(requesterUuid);

        if (requester == null) {
            target.sendSystemMessage(Component.literal("§c发送请求的玩家已离线！"));
            return 0;
        }

        // 执行传送: 将发起者传送至目标玩家的位置
        requester.teleportTo(
                target.serverLevel(),
                target.getX(), target.getY(), target.getZ(),
                target.getYRot(), target.getXRot()
        );

        target.sendSystemMessage(Component.literal("§a已接受 " + requester.getScoreboardName() + " 的传送请求。"));
        requester.sendSystemMessage(Component.literal("§a" + target.getScoreboardName() + " 接受了你的传送请求，已完成传送！"));

        return 1;
    }

    /**
     * 拒绝传送请求 /tpdeny
     */
    private int executeTpDeny(CommandContext<CommandSourceStack> context) throws CommandSyntaxException {
        ServerPlayer target = context.getSource().getPlayerOrException();
        UUID requesterUuid = TP_REQUESTS.remove(target.getUUID());

        if (requesterUuid == null) {
            target.sendSystemMessage(Component.literal("§c你当前没有任何待处理的传送请求！"));
            return 0;
        }

        ServerPlayer requester = target.server.getPlayerList().getPlayer(requesterUuid);
        if (requester != null) {
            requester.sendSystemMessage(Component.literal("§c" + target.getScoreboardName() + " 拒绝了你的传送请求。"));
        }

        target.sendSystemMessage(Component.literal("§e已拒绝传送请求。"));

        return 1;
    }
}