package com.incobalt.roleplaychat;

import com.incobalt.coreapi.CoreAPI;
import com.incobalt.coreapi.chat.ChatUtils;
import com.incobalt.coreapi.commands.CommandBase;
import com.incobalt.roleplaychat.chat.*;
import com.incobalt.roleplaychat.missive.Missive;
import com.incobalt.roleplaychat.missive.MissiveCommand;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.command.CommandSource;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.server.FMLServerStartingEvent;

import java.util.ArrayList;

/*
    incobalt's Roleplay Chat
    A chat modification that facilitates RL/roleplaying play. Largely features distance-based chat to keep chat localized
    to an area. Adds commands to simulate raising (/shout) and lowering (/whisper) your voice, and an admin command to
    send a message to everyone on the server (/broadcast). Chat color codes are also implemented with &->§, but this is
    restricted to a configurable permission level. Also included is a missive system that replaces Minecraft's /msg
    command. Missives greatly restrict long-range messaging to a time-based system that optionally uses resources.
    incobalt's Roleplay Chat was inspired by a streamed roleplay Spigot server experience that could not be replicated
    by existing Forge mods. It is heavily commented to serve as a learning resource!
    As a note: This is mostly a server mod and can be deployed server side only unless you're using the commands. Chat
    symbols can be used to access all parts of the mod!
 */

// The value here should match an entry in the META-INF/mods.toml file
@Mod("incobalts_roleplaychat")
public class RoleplayChat
{

    //list of registered commands
    private final ArrayList <CommandBase> commandRegister = new ArrayList<>();

    public static final String MODID = "incobalts_roleplaychat";

    public RoleplayChat() {
        // Register ourselves for server and other game events we are interested in
        MinecraftForge.EVENT_BUS.register(this);

        //load the configuration file. I had to save ModLoading Context to a variable or else it returned null on server load once
        ModLoadingContext modLoadingContext = ModLoadingContext.get();
        //this is the line that actually loads and saves a config file!
        modLoadingContext.registerConfig(ModConfig.Type.SERVER, ChatConfig.SERVER_SPEC, "roleplaychat-server.toml");
    }

    // Use @SubscribeEvent for methods that want to attach to an event
    // The method name doesn't matter, just the event type in the arguments!
    @SubscribeEvent
    public void onServerStarting(FMLServerStartingEvent event) {
        // FMLServerStartingEvent triggers when the server starts
        // This is where you put code for initializing the mod

        //Send information to the log that acts as a header so you can find your mod easier
        CoreAPI.LOGGER.info("Roleplay Chat");

        //log that we're going to make commands if either distance chat commands or missive commands are being made
        if(ChatConfig.createCommands || ChatConfig.missiveCreateCommands){
            CoreAPI.LOGGER.info("Registering Commands");
        }

        //get a copy of the command dispatcher that you can use to register commands
        CommandDispatcher<CommandSource> dispatcher = event.getCommandDispatcher();

        //A helper list to build lists of aliases for specific commands. You could have this
        //directly in the command class, but this puts it all in one place.
        ArrayList<String> aliases = new ArrayList<>();

        //Check the config if we're using distance chat. We don't want to add distance chat
        //commands if it's disabled!
        if(ChatConfig.enableDistanceChat && ChatConfig.createCommands) {
            //add aliases for the shout command
            aliases.add("shout");
            aliases.add("s");
            //add the shout command to the the register. We'll register it with the dispatcher in a bit.
            commandRegister.add(new ShoutCommand(aliases));
            //clear the list and add aliases for the whisper command.
            aliases.clear();
            aliases.add("whisper");
            aliases.add("whis");
            //vanilla commands are optionally removed and re-added, so check the config to see if we
            //add that to the aliases
            if (ChatConfig.redirectVanillaCommands)
                aliases.add("w"); // /w is a vanilla command that does the same thing as /msg
            //add the whisper command to the the register. We'll register it with the dispatcher in a bit.
            commandRegister.add(new WhisperCommand(aliases));
            //clear the list and add aliases for the emote command
            aliases.clear();
            aliases.add("emote");
            //again, if vanilla commands are redirected, add /me to the list of emote aliases
            if (ChatConfig.redirectVanillaCommands)
                aliases.add("me"); // /me in vanilla already performs emotes, just not RP emotes!
            //add the emote command to the the register. We'll register it with the dispatcher in a bit.
            commandRegister.add(new EmoteCommand(aliases));
            //clear the list and add aliases for the broadcast command
            aliases.clear();
            aliases.add("broadcast");
            aliases.add("br");
            aliases.add("bc");
            //add the broadcast command to the the register. We'll register it with the dispatcher in a bit.
            commandRegister.add(new BroadcastCommand(aliases));

            //optionally add the say command to the register, if we're redirecting vanilla commands
            //if we don't do this, /say will send messages to the entire server!
            if (ChatConfig.redirectVanillaCommands) {
                aliases.clear();
                aliases.add("say");
                //add the say command to the the register. We'll register it with the dispatcher in a bit.
                commandRegister.add(new SayCommand(aliases));
            }
        } //Creating distance chat commands

        //create commands for the missive system if we need them
        if(ChatConfig.enableMissives && ChatConfig.missiveCreateCommands){
            //clear the list in case we used it and add aliases for the missive command
            aliases.clear();
            aliases.add("missive");
            //vanilla commands are optionally removed and re-added, so check the config to see if we
            //add that to the aliases
            if (ChatConfig.missiveRedirectCommands) {
                aliases.add("msg");
                aliases.add("tell");
                //the /w command might be available, so redirect it if it hasn't already been
                if (!ChatConfig.redirectVanillaCommands) {
                    aliases.add("w");
                }
            }
            //add the command to the register
            commandRegister.add(new MissiveCommand(aliases));
        }

        //register all the commands with the dispatcher. We use a base command class to do this.
        for(CommandBase command : commandRegister){
            command.registerCommand(dispatcher);
        }
    }



    // Subscribe the method to the event bus
    @SubscribeEvent
    public void onServerChatEvent(ServerChatEvent event)
    {
        // ServerChatEvent triggers whenever the server recieves a chat message to process
        // You can use this event to change how the server processes a chat message
        // We use this to convert chat messages to commands if chat symbols is enabled

        //double check first in case the event has been canceled!
        if(event.isCanceled())
            return;

        //Get the player from the event. Command blocks seem to act like players.
        ServerPlayerEntity player = event.getPlayer();

        //Test if we're on the server. isRemote is true if we're on a client
        //Realistically, since this is ServerChatEvent, it should never be remote, but it's good to check
        if(player.getServerWorld().isRemote)
            return;

        //Get the message from the event. This is the text of the chat.
        String message = event.getMessage();

        //convert & to § if they player has a high enough permission level. & was used in old versions of Minecraft
        //so it's common for chat mods to do this. Because it's an RP-focused mod, color is gated by a permission
        if(player.hasPermissionLevel(ChatConfig.colorPermissionLevel)){
            message = message.replace('&', '§');
            event.setComponent(ChatUtils.toTextComponent(message));
        }

        //check first if we're using missives and if the message is a missive chat symbol
        if(ChatConfig.enableMissives && ChatConfig.useChatSymbols){
            // Chat symbols check the start of the message and compare it to the characters in the config.
            // If they match, then the message is converted to that kind of message, as if the player had used the command
            if (message.startsWith(ChatConfig.missiveCharacter)) {
                //compare to the missiveCharacter (default '>')
                //remove the symbol from the message! Technically, shoutCharacter is a string, so it could be more than one symbol, thus we need to remove that many characters
                message = message.substring(ChatConfig.missiveCharacter.length());
                //send the command to the shout function
                if(!Missive.processChatMissive(message, event.getPlayer())){
                    //processChatMissive returns false when it couldn't find a target in the commmand
                    //therefore, we should let the player know this
                    event.getPlayer().sendMessage(ChatUtils.toTextComponent("§c§oThe missive couldn't be understood. Use the following format: \n>[playername] [message]  (without brackets)"));
                }
                //cancel the event so it doesn't process anymore
                event.setCanceled(true);
                return;
            }
        }

        //then check if we're using distance chat
        if(ChatConfig.enableDistanceChat) {

            //config may be set to not use chat symbols, so we need to check for that
            if (!ChatConfig.useChatSymbols) {
                //when we don't want to use chat symbols, then just send the message to distance chat say
                ChatCommands.ProcessDistanceSay(message, player.getDisplayName().getFormattedText(), player.getPositionVec(), player.getServerWorld());
                //use event.setCanceled to stop further processing of the event.
                event.setCanceled(true);
            } else {
                // Chat symbols check the start of the message and compare it to the characters in the config.
                // If they match, then the message is converted to that kind of message, as if the player had used the command
                if (message.startsWith(ChatConfig.shoutCharacter)) {
                    //compare to the shoutCharacter (default '!')
                    //remove the symbol from the message! Technically, shoutCharacter is a string, so it could be more than one symbol, thus we need to remove that many characters
                    message = message.substring(ChatConfig.shoutCharacter.length());
                    //send the command to the shout function
                    ChatCommands.ProcessShout(message, player.getDisplayName().getFormattedText(), player.getPositionVec(), player.getServerWorld());
                    //cancel the event so it doesn't process anymore
                    event.setCanceled(true);
                } else if (message.startsWith(ChatConfig.emoteCharacter)) {
                    //compare to the emoteCharacter (default '*')
                    //remove the symbol from the message! Technically, emoteCharacter is a string, so it could be more than one symbol, thus we need to remove that many characters
                    message = message.substring(ChatConfig.emoteCharacter.length());
                    //send the command to the emote function
                    ChatCommands.ProcessEmote(message, player.getDisplayName().getFormattedText(), player.getPositionVec(), player.getServerWorld());
                    //cancel the event so it doesn't process anymore
                    event.setCanceled(true);
                } else if (message.startsWith(ChatConfig.whisperCharacter)) {
                    //compare to the whisperCharacter (default '_')
                    //remove the symbol from the message! Technically, whisperCharacter is a string, so it could be more than one symbol, thus we need to remove that many characters
                    message = message.substring(ChatConfig.whisperCharacter.length());
                    //send the command to the whisper function
                    ChatCommands.ProcessWhisper(message, player.getDisplayName().getFormattedText(), player.getPositionVec(), player.getServerWorld());
                    //cancel the event so it doesn't process anymore
                    event.setCanceled(true);
                } else if (message.startsWith(ChatConfig.broadcastCharacter)) {
                    //compare to the broadcastCharacter (default '#')
                    //broadcast is locked behind a permission level, so make sure player has the required permission level
                    if (player.hasPermissionLevel(ChatConfig.broadcastPermissionLevel)) {
                        //remove the symbol from the message! Technically, broadcastCharacter is a string, so it could be more than one symbol, thus we need to remove that many characters
                        message = message.substring(ChatConfig.broadcastCharacter.length());
                        //send the command to the broadcast function
                        ChatCommands.ProcessBroadcast(message, player.getServerWorld());
                    } else {
                        //if the player doesn't have the permission level, send them a message informing them of that
                        //sendMessage takes an ITextComponent, which is annoying to work with inline, so we created a wrapper for it in ChatUtils.toTextComponent
                        player.sendMessage(ChatUtils.toTextComponent("§6You don't have permission to broadcast!"));
                    }
                    //cancel the event so it doesn't process anymore
                    event.setCanceled(true);
                } else {
                    //if we get to here, then the message just needs to go to the distance say function
                    ChatCommands.ProcessDistanceSay(message, player.getDisplayName().getFormattedText(), player.getPositionVec(), player.getServerWorld());
                    //cancel the event so it doesn't process anymore
                    event.setCanceled(true);
                }
            } //using distance chat symbols
        } //using distance chat
    } //server chat event

    // Subscribe the method to the event bus
    @SubscribeEvent
    public void onCommandReceived(CommandEvent event){
        // CommandEvent triggers whenever the server recieves a command from a client
        // Use this event to add functionality to commands such as permission checking

        //double check first in case the event has been canceled!
        if(event.isCanceled())
            return;

        //get the command. This basically gives you the line that the player entered
        //I couldn't figure out a better way to do this getCommand() exists, but is very limited
        String command = event.getParseResults().getReader().getString();

        //the CommandSource is whatever sent the command, such as a player or command block
        CommandSource source = event.getParseResults().getContext().getSource();

        //Test if we're on the server. isRemote is true if we're on a client
        if(source.getWorld().isRemote)
            return;

        //the /msg command has a restricted permission level in roleplay chat, so we need to capture it and /tell
        //also captures /w *if* we haven't changed it to distance whisper
        if(command.startsWith("/msg ") || command.startsWith("/tell ") || (command.startsWith("/w ") && !ChatConfig.redirectVanillaCommands) ) {
            //compare the source's permission level with the permission level set in the config
            if(!source.hasPermissionLevel(ChatConfig.tellPermissionLevel)) {
                //in order to use CommandSource.asPlayer(), we need to catch the CommandSyntaxException, best done in a try/catch block
                try {
                    //send a message to the player that they don't have the permissions
                    //sendMessage takes an ITextComponent, which is annoying to work with inline, so we created a wrapper for it in ChatUtils.toTextComponent
                    source.asPlayer().sendMessage(ChatUtils.toTextComponent("§6You don't have permission to use that command!"));
                    //cancel the event so it doesn't process anymore
                    event.setCanceled(true);
                    return;
                }catch(CommandSyntaxException e){
                    //this exception will happen if the CommandSource isn't a player
                    //in that case, we don't want to do anything to the command and just pass it back to the event handler
                    return;
                }
            }
        }
    }
}
