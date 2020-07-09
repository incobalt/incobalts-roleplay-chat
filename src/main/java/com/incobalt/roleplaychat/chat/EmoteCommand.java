package com.incobalt.roleplaychat.chat;

import com.incobalt.coreapi.CoreAPI;
import com.incobalt.coreapi.commands.CommandBase;
import com.incobalt.roleplaychat.ChatConfig;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.command.CommandSource;
import net.minecraft.command.Commands;
import net.minecraft.command.arguments.MessageArgument;
import net.minecraft.util.math.Vec3d;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.ArrayList;

/*
    EmoteCommand is the command class that registers /emote (and /me if vanilla commands are replaced).
    Emotes are sent to every player within Distance Chat Range (set in the server config)
    This is implemented mostly in ChatCommands, this class is mostly the gel to connect the command to Minecraft
    This is a fairly standard one-argument command class that can be used as a template for other commands

    EmoteCommand extends CommandBase, which means it must implement a constructor and registerCommand()
    CommandBase was moved to incobalt's Core API mod!
 */
public class EmoteCommand extends CommandBase {

    //NOTE: CommandBase provides the variable String[] aliases

    //constructor. Must match the constructor in CommandBase
    public EmoteCommand(ArrayList<String> a) {
        //The constructor only needs to call super(a) because CommandBase does the work
        super(a);
    }

    //the main part of this class. This registers the command with Minecraft's command dispatcher
    public void registerCommand(CommandDispatcher<CommandSource> dispatcher){
        //let the log know what we're doing. With commands, it's helpful to do this for mod conflict problems
        CoreAPI.LOGGER.info("Registering Emote Command");

        //loop through the aliases to register
        for(String a : aliases) {
            //if we're redirecting commands, then we need to remove commands that may exist
            //this is pretty invasive to Minecraft, so be careful about doing this!
            if(ChatConfig.redirectVanillaCommands){
                dispatcher.getRoot().getChildren().removeIf(cmd -> cmd.getName().equals(a));
            }

            //this builds the actual command, and it's a pretty complicated process.
            //Commands.literal defines what the player types after the / (emote for /emote)
            //.then seems to add a step to the command. Every literal needs a .then to add an argument.
                //standalone arguments (like /seed) can go straight to .executes
                //to build the argument, use Commands.argument, which needs a name and an argument type (MessageArgument.message() here)
                //there are several built-in argument types in net.minecraft.command.arguments
            //.executes is what is executed when the command is sent by a player. It can be a lambda like here, or a separate field.
                //executes provides a CommandContext (ctx here)
            LiteralArgumentBuilder<CommandSource> commandLiteral = Commands.literal(a)
                    .then(Commands.argument("message", MessageArgument.message())
                            .executes( ctx ->{
                                //this is what happens when the command is executed

                                //get the command source from the context (source is information about what sent the command)
                                CommandSource source = ctx.getSource();
                                //this command only executes on the server, so don't bother if we're on a remote machine (a client is a remote machine)
                                if(source.getWorld().isRemote)
                                    return 0; //return 0 in a command predicate like this means failure.
                                //this command needs to know the source's position in the world, so grab the Vec3d from the source
                                Vec3d pos = source.getPos();
                                //this is how you get your arguments. getMessage returns an ITextComponent, so we need to use getFormattedText() to retrieve the actual string
                                String message = MessageArgument.getMessage(ctx, "message").getFormattedText();
                                //color permissions might require a permission level, so test for it and replace & with § if the source has that permission level
                                if(source.hasPermissionLevel(ChatConfig.colorPermissionLevel)){
                                    message = message.replace('&', '§');
                                }
                                //send the command to be executed. The execution for these commands are kept in a central place for ease of access
                                ChatCommands.ProcessEmote(message, source.getDisplayName().getFormattedText(), pos, source.getWorld());
                                //Command.SINGLE_SUCCESS is actually just 1, but this has better readability.
                                return Command.SINGLE_SUCCESS;
                            }));
            //this is an important line! This actually puts your command into the game, registering it with the Minecraft command dispatcher
            dispatcher.register(commandLiteral);
        }
    }
}
