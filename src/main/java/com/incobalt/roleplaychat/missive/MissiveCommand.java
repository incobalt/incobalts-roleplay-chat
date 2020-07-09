package com.incobalt.roleplaychat.missive;

import com.incobalt.coreapi.CoreAPI;
import com.incobalt.coreapi.chat.ChatUtils;
import com.incobalt.coreapi.commands.CommandBase;
import com.incobalt.roleplaychat.ChatConfig;
import com.incobalt.roleplaychat.ServerClientBridge;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.MessageArgument;
import net.minecraft.command.arguments.EntityArgument;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.fml.network.NetworkDirection;

import java.util.ArrayList;

/*
    MissiveCommand is a more complex command that requires two arguments, a player and a message. It also can interface
    with the client to offload some of the processing power of sifting through a player's items.
    MissiveCommand is for sending a message across long distances over time (like a time-based /msg)

    MissiveCommand extends CommandBase, which means it must implement a constructor and registerCommand()
    CommandBase was moved to incobalt's Core API mod!
 */

public class MissiveCommand extends CommandBase {

    //NOTE: CommandBase provides the variable String[] aliases

    //constructor. Must match the constructor in CommandBase
    public MissiveCommand(ArrayList<String> a) {
        //The constructor only needs to call super(a) because CommandBase does the work
        super(a);
    }

    //the main part of this class. This registers the command with Minecraft's command dispatcher
    public void registerCommand(CommandDispatcher<CommandSource> dispatcher){
        //let the log know what we're doing. With commands, it's helpful to do this for mod conflict problems
        CoreAPI.LOGGER.info("Registering Missive Command");

        //loop through the aliases to register
        for(String a : aliases) {
            //if we're redirecting commands, then we need to remove commands that may exist
            //this is pretty invasive to Minecraft, so be careful about doing this!
            if(ChatConfig.redirectVanillaCommands){
                dispatcher.getRoot().getChildren().removeIf(cmd -> cmd.getName().equals(a));
            }
            //this builds the actual command, and it's a pretty complicated process.
            //Commands.literal defines what the player types after the / (missive for /missive)
            //.then seems to add a step to the command. Every literal needs a .then to add an argument.
            //standalone arguments (like /seed) can go straight to .executes
            //to build the argument, use Commands.argument, which needs a name and an argument type (MessageArgument.message() here)
            //EntityArgument.player() also is used here. note that EntityArgument has many forms (we just want players!)
            //there are several built-in argument types in net.minecraft.command.arguments
            //.executes is what is executed when the command is sent by a player. It can be a lambda like here, or a separate field.
            //executes provides a CommandContext (ctx here)
            LiteralArgumentBuilder<CommandSource> commandLiteral = Commands.literal(a)
                    .then(Commands.argument("target", EntityArgument.player())
                            .executes(ctx -> executeNoMessage(ctx.getSource(), EntityArgument.getPlayer(ctx, "target")))
                            .then(Commands.argument("message", MessageArgument.message())
                                .executes( ctx -> execute(ctx.getSource(),
                                        EntityArgument.getPlayer(ctx, "target"),
                                        MessageArgument.getMessage(ctx, "message").getFormattedText()
                                        )
                                )
                            )
                    );
            //this is an important line! This actually puts your command into the game, registering it with the Minecraft command dispatcher
            dispatcher.register(commandLiteral);
        }

    }


    private int execute(CommandSource source, ServerPlayerEntity target, String message) {
        {
            //this is what happens when the command is executed

            //this command only executes on the server, so don't bother if we're on a remote machine (a client is a remote machine)
            if(source.getWorld().isRemote)
                return Command.SINGLE_SUCCESS;//return success even though we're not processing.
            //here, we try to determine if the sender is a player or something else (like a command block)
            ServerPlayerEntity sender = null;
            try{
                //asPlayer will raise an exception if the source isn't a player, otherwise it returns a ServerEntityPlayer
                sender = source.asPlayer();
            }catch (CommandSyntaxException e){
                //source is an entity, not a player! It's probably a command block
                //we have a special form of sendMissive for non-entities
                //getDisplayName() returns an ITextComponent, so we need to use getFormattedText() to get the full string
                String name = source.getDisplayName().getFormattedText();
                //we also need a position of the command, since Missives are distance-based
                Vec3d pos = source.getPos();
                //send the missive. this branch bypasses any item checking
                Missive.sendMissive(message, name, pos, target);
                //Command.SINGLE_SUCCESS is actually just 1, but this has better readability.
                return Command.SINGLE_SUCCESS;
            }
            //we have a hard limit on missives to be not more than 800 characters, check that first!
            if(message.length() >= 800){
                sender.sendMessage(ChatUtils.toTextComponent("§c§oA missive must be less than 800 characters. Your missive has " + message.length() + " characters!"));
                return Command.SINGLE_SUCCESS;
            }
            //sender is an actual player!
            if((!ChatConfig.missivesUseItems) || (ChatConfig.missiveCatalystItems.size() == 0 && ChatConfig.missiveConsumableItems.size() == 0 )){
                //if we're not using items, then we don't have to do some server/client work!
                Missive.sendMissive(message, sender, target);
                //Command.SINGLE_SUCCESS is actually just 1, but this has better readability.
                return Command.SINGLE_SUCCESS;
            }
            //we're using items, which take a bit of processing. The mod can be set up to do this server side, so check that first
            if(ChatConfig.missiveServerSideItemChecks){
                //getUsedItem will provide both a boolean (if a consumable was found) and an inventory slot number
                //-3 is considered no item, while -1 and -2 are main and off hand respectively
                Tuple<Boolean, Integer> result = Missive.getUsedItem(sender);
                if(result.getB() == -3){
                    //-3 means that no item was found! getUsedItem already sends a message to the player, so just return
                    return Command.SINGLE_SUCCESS;
                }

                //removes the items if needed and sends the missive
                Missive.sendItemizedMissive(message, sender, target, result.getA(), result.getB());

                return Command.SINGLE_SUCCESS;
            }

            //if we don't have the server do it, then we need to make the client do it for us!
            //this way, the server doesn't get lagged every time someone sends a missive!
            //we need to send a request to the client to check for the required items
            //we do this with a packet handler, like ServerClientBridge that we set up
            //sendTo sends a packet based on its parameters. In this case, we're sending to the client
            //sender.connection.getNetworkManager() is who we want to send to (the sender in this case)
            //NetworkDirection.PLAY_TO_CLIENT tells the handler that this is from the logical server to the client
            ServerClientBridge.INSTANCE.sendTo(new MissivePacket(
                    message,
                    target.getUniqueID()
            ), sender.connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
            //the actual Missive.sendMissive call happens in the server callback (see MissivePacket.java

            //Command.SINGLE_SUCCESS is actually just 1, but this has better readability.
            return Command.SINGLE_SUCCESS;
        }
    }


    private int executeNoMessage(CommandSource source, ServerPlayerEntity target) {
        Entity entity = source.getEntity();
        if(entity instanceof PlayerEntity) {
            entity.sendMessage(ChatUtils.toTextComponent("Please provide a message to send!"));
        }
        return Command.SINGLE_SUCCESS;
    }

}
