package com.incobalt.roleplaychat.chat;

import com.incobalt.coreapi.chat.ChatUtils;
import com.incobalt.roleplaychat.ChatConfig;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.util.math.AxisAlignedBB;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.Random;

/*
    ChatCommands is the place where all the chat processing happens. All of the Roleplay Chat features are implemented here.
    This is done to keep all the code for each kind of chat in one place, and to avoid duplicating code.

    These functions purposefully avoid needing an entity, in case you want to use them from a block or some other non-entity type.
 */

public class ChatCommands {

    /*
        ProcessDistanceSay is the core concept of this mod, and it is the most complicated of the various kinds of chat.
        This function gathers players in a configurable radius around the sender and sends the chat message only to them.
        For players outside of this range, message readability becomes worse until it is completely unreadable.
        Players more than twice the range away don't see any form of the message.

        This simulates a local, audible range of chat.
     */
    public static void ProcessDistanceSay(String msg, String displayName, Vec3d pos, ServerWorld world) {
        //this function only operates on the server, so ensure that we're not on a remote machine (client)
        //realistically, since we've requested a ServerWorld, this should never be true, but this is just a precaution
        if (world.isRemote)
            return;

        //we use twice the chat range here to create a falloff range beyond the fully clear range
        final double chatDistance = ChatConfig.distanceChatRange * 2;
        //the distance functions used later calculates the squared distance for performance reasons, so we need to get a squared distance to compare against
        //players inside this range will see the message clearly
        final double sqDistance = ChatConfig.distanceChatRange * ChatConfig.distanceChatRange;
        //any player outside the above range but inside this range will see the message being muffled based on how far away
        final double sqChatDistance = chatDistance * chatDistance;
        //we use random to give the muffled chat a little bit of variation.
        //this could be removed, but then you would notice the first character of every message being clear until fully muffled
        final Random random = new Random();

        //startPos will start our muffleProgress off at a different position for each message, giving a little bit of a variation
        final double startPos = random.nextDouble() * sqChatDistance;

        //this gets all server players within a box around the chat source position. Boxes are faster than spheres in 3D.
        //an alternate way could be to just get the players from the server and compare their distances, but this could
        //create some overhead with large servers
        List<ServerPlayerEntity> players = world.getEntitiesWithinAABB(
                ServerPlayerEntity.class,
                new AxisAlignedBB(
                        pos.x - chatDistance,
                        pos.y - chatDistance,
                        pos.z - chatDistance,
                        pos.x + chatDistance,
                        pos.y + chatDistance,
                        pos.z + chatDistance
                ));

        //loop through all the players that we found and send messages as appropriate
        for (ServerPlayerEntity p : players) {
            //get the *squared* distance between the player and the source position. The distance formula for 3D objects is:
            //sqrt[ (x2 - x1)^2 + (y2 - y1)^2 + (z2 - z1)^2 ]
            //sqrt is expensive on a computer. We can skip the square root step if we compare against *squared* values
            final double distance = p.getDistanceSq(pos);
            //comparing the distances
            if (distance < sqDistance) {
                //first, if we're within the first step of the range, just send the message to the player
                p.sendMessage(ChatUtils.toTextComponent(displayName + ": " + msg));
            } else if (distance < sqChatDistance) {
                //if we're outside that first step, but inside the second step, then we've got to muffle the message!

                //we use a string builder to build out muffled text. Honestly, this was just because IDEA suggested I do this
                //instead of using + to jam strings together.
                StringBuilder muffleText = new StringBuilder();
                //we start with the display name of the source, which is clear no matter the range. This could have been done
                //with a configurable pattern, and might in the future. For now playername: <message> is chosen because it's
                //pretty readable in the chat window.
                muffleText.append(displayName).append(": ");
                //muffleProgress is a counter that counts up to a threshhold after which we muffle the next character of the message
                //random is used to give a little variation
                double muffleProgress = startPos;
                //i is our progress through the provided message
                int i = 0;
                //obfuscated is used in the case of using §k to muffle text to avoid putting costly format codes in front of each character
                //this will only become true if the config says to use obfuscation!
                boolean obfuscated = false;
                //loop through each character in the message
                while (i < msg.length()) {
                    //we skip spaces just like §k does, so if there's a space, just copy it to the final string and return to the loop
                    if (msg.charAt(i) == ' ') {
                        i++;
                        muffleText.append(" ");
                        continue;
                    }
                    //we skip format codes, so when we encounter them, we write them into the string
                    if (msg.charAt(i) == '§') {
                        muffleText.append("§");
                        i++;
                        //format codes have a character afterwards that we need to grab as well
                        muffleText.append(msg.charAt(i));
                        i++;
                        //color codes will overwrite format codes, so we need to make sure that the obfuscated format is reapplied if it was already
                        if(obfuscated) {
                            muffleText.append("§k");
                        }
                        continue;
                    }
                    //increase the muffleProgress by a little more than our distance. This creates a falloff range where about
                    //the last 20% of the range gets a fully muffled message. Without the * 1.25 here, only the very edge
                    //might *sometimes* see the fully muffled message
                    muffleProgress += distance * 1.25;
                    //we use sqChatDistance as our threshold for when to muffle the text. This makes the muffling get worse
                    //the farther away the player is from the source.
                    if (muffleProgress >= sqChatDistance) {
                        //using -= instead of = 0 helps with creating uniformly muffled messages, carrying the spillover into
                        //the next character
                        muffleProgress -= sqChatDistance;
                        //here we branch based on whether the server owner has decided to use obfuscation or a character replacement
                        //obfuscation can be a bit difficult to look at all the time, particularly for players with visual issues,
                        //so the option is there to accommodate them.
                        if (ChatConfig.useObfuscation) {
                            //only add §k once per block of obfucscated text! This cuts out some overhead.
                            if (!obfuscated) {
                                muffleText.append("§k");
                            }
                            //add the character to the muffled string!
                            muffleText.append(msg.charAt(i));
                            obfuscated = true;
                        } else {
                            //instead of adding the character to the muffled string, add our configurable substitution character
                            muffleText.append(ChatConfig.obfuscateCharacter);
                        }
                    } else {
                        //if the next character isn't supposed to be obfuscated, but it previously was, then we need to add
                        //the reset format code in. This will only happen if we're using obfuscation (because obfuscated will
                        //only be true if that setting is enabled)
                        if (obfuscated) {
                            obfuscated = false;
                            muffleText.append("§r");
                        }
                        //add the character to the muffled string!
                        muffleText.append(msg.charAt(i));
                    }
                    //IMPORTANT! This is a while loop so it's prone to infinite looping! Make sure you advance the counter in every branch!
                    i++;
                } //while (i < msg.length()

                //send the message to the player. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
                p.sendMessage(ChatUtils.toTextComponent(muffleText.toString()));
            } //else if (distance < sqChatDistance)
            //note: no else is needed here, but it's important to understand why we need an else if. We got every player in
            //a box around the source position, but we're only interested in a radius around the source. That means that we
            //might have gotten players outside that radius, in the corners of the box, and we want to exclude them! If we
            //don't it might not seem like an organic or natural distance.
        } //for (p : players)
    }

    /*
        ProcessEmote is for sending emotive messages to players in a configurable radius around the sender.
        Unlike ProcessDistanceSay, the message does not get muffled after that range
     */
    public static void ProcessEmote(String msg, String displayName, Vec3d pos, ServerWorld world) {
        //this function only operates on the server, so ensure that we're not on a remote machine (client)
        //realistically, since we've requested a ServerWorld, this should never be true, but this is just a precaution
        if (world.isRemote)
            return;

        //emotes look the same to everyone who sees it so this sets up the string to send to players
        //this mimics existing /me usage, but adds a grey color to the message to make it stand out from regular chat
        final String message = ("§7§o*" + displayName + " " + msg);

        //we need to get the *squared* distance of the range to compare against the distance between sender and receivers
        final double range = ChatConfig.distanceChatRange * ChatConfig.distanceChatRange;

        //this gets all server players within a box around the chat source position. Boxes are faster than spheres in 3D.
        //an alternate way could be to just get the players from the server and compare their distances, but this could
        //create some overhead with large servers
        List<ServerPlayerEntity> players = world.getEntitiesWithinAABB(ServerPlayerEntity.class, new AxisAlignedBB(
                pos.x - ChatConfig.distanceChatRange,
                pos.y - ChatConfig.distanceChatRange,
                pos.z - ChatConfig.distanceChatRange,
                pos.x + ChatConfig.distanceChatRange,
                pos.y + ChatConfig.distanceChatRange,
                pos.z + ChatConfig.distanceChatRange
        ));

        //loop through the players found inside that box
        for(ServerPlayerEntity p : players) {
            //because we get all players in a box, there might be players outside the range in the corners of that box.
            //thus, we use a distance formula to compare against the desired range. The distance formula for 3D objects is:
            //sqrt[ (x2 - x1)^2 + (y2 - y1)^2 + (z2 - z1)^2 ]
            //sqrt is expensive on a computer. We can skip the square root step if we compare against *squared* values
            if(p.getDistanceSq(pos) < range) {
                //send the message to the player. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
                p.sendMessage(ChatUtils.toTextComponent(message));
            }
        }
    }

    /*
        ProcessShout is for sending messages to players in a greater range around the sender than ProcessDistanceSay does.
        Unlike ProcessDistanceSay, the message does not get muffled after that range.
     */
    public static void ProcessShout(String msg, String displayName, Vec3d pos, ServerWorld world) {
        //this function only operates on the server, so ensure that we're not on a remote machine (client)
        //realistically, since we've requested a ServerWorld, this should never be true, but this is just a precaution
        if (world.isRemote)
            return;

        //shoutRange is 3 times the normal Distance Chat Range in the config. By default this is a 45 block radius.
        final double shoutRange = ChatConfig.distanceChatRange * 3.0;
        //to do distance calculations, we need the *squared* range to compare against (distance formula gives a squared distance)
        final double sqRange = shoutRange * shoutRange;

        //this gets all server players within a box around the chat source position. Boxes are faster than spheres in 3D.
        //an alternate way could be to just get the players from the server and compare their distances, but this could
        //create some overhead with large servers
        List<ServerPlayerEntity> players = world.getEntitiesWithinAABB(ServerPlayerEntity.class, new AxisAlignedBB(
                pos.x - shoutRange,
                pos.y - shoutRange,
                pos.z - shoutRange,
                pos.x + shoutRange,
                pos.y + shoutRange,
                pos.z + shoutRange
        ));

        //loop through the players found inside that box
        for(ServerPlayerEntity p : players) {
            //shout gives a different message to the sender than to other players, so we test for that here
            //getDisplayName() gives us an ITextController, so we need .getFormattedText() to get the string.
            //implicitly, this means that when we call this function we need to pass in
            //source.getDisplayName.getFormattedText() for displayName!
            if (p.getDisplayName().getFormattedText().equals(displayName)) {
                //send the message to the sender. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
                p.sendMessage(ChatUtils.toTextComponent("§lYou shout: " + msg));
            } else {
                //because we get all players in a box, there might be players outside the range in the corners of that box.
                //thus, we use a distance formula to compare against the desired range. The distance formula for 3D objects is:
                //sqrt[ (x2 - x1)^2 + (y2 - y1)^2 + (z2 - z1)^2 ]
                //sqrt is expensive on a computer. We can skip the square root step if we compare against *squared* values
                if(p.getDistanceSq(pos) < sqRange) {
                    //send the message to the player. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
                    p.sendMessage(ChatUtils.toTextComponent("§l" + displayName + " shouts: " + msg));
                }
            }
        }
    }

    /*
        ProcessWhisper is for sending messages to players in a much shorter range around the sender than ProcessDistanceSay does.
        Unlike ProcessDistanceSay, the message does not get muffled after that range, but players one block outside the range
        receive an emotive message informing them that the sender has whispered something (but do not receive the message itself).
     */
    public static void ProcessWhisper(String msg, String displayName, Vec3d pos, ServerWorld world) {
        //this function only operates on the server, so ensure that we're not on a remote machine (client)
        //realistically, since we've requested a ServerWorld, this should never be true, but this is just a precaution
        if (world.isRemote)
            return;

        //distance functions return *squared* distances, so we need a squared range to compare against
        final double sqRange = ChatConfig.whisperRange * ChatConfig.whisperRange;
        //players 1 block outside the range above get an emote message like *playername whispers.
        final double emoteRange = ChatConfig.whisperRange + 1.0;
        //again, we need this to be squared to compare to distances
        final double sqEmoteRange = emoteRange * emoteRange;

        //this gets all server players within a box around the chat source position. Boxes are faster than spheres in 3D.
        //an alternate way could be to just get the players from the server and compare their distances, but this could
        //create some overhead with large servers
        List<ServerPlayerEntity> players = world.getEntitiesWithinAABB(ServerPlayerEntity.class, new AxisAlignedBB(
                pos.x - emoteRange,
                pos.y - emoteRange,
                pos.z - emoteRange,
                pos.x + emoteRange,
                pos.y + emoteRange,
                pos.z + emoteRange
        ));

        //loop through the players found inside that box
        for(ServerPlayerEntity p : players) {
            //get the *squared* distance between the player and the source position. The distance formula for 3D objects is:
            //sqrt[ (x2 - x1)^2 + (y2 - y1)^2 + (z2 - z1)^2 ]
            //sqrt is expensive on a computer. We can skip the square root step if we compare against *squared* values
            final double distance = p.getDistanceSq(pos);
            if (distance < sqRange) {
                //players inside the range receive the message
                //whisper gives a different message to the sender than to other players, so we test for that here
                //getDisplayName() gives us an ITextController, so we need .getFormattedText() to get the string.
                //implicitly, this means that when we call this function we need to pass in
                //source.getDisplayName.getFormattedText() for displayName!
                if (p.getDisplayName().getFormattedText().equals(displayName)) {
                    //send the message to the sender. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
                    p.sendMessage(ChatUtils.toTextComponent("§7§oYou whisper: " + msg));
                } else {
                    //send the message to the player. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
                    p.sendMessage(ChatUtils.toTextComponent("§7§o" + displayName + " whispers: " + msg));
                }
            } else if(distance < sqEmoteRange) {
                //we might get players outside the emote range with our getEntities call earlier (the corners will be at a greater distance!)
                //thus, we compare against the one block extra squared range here to determine who we should include
                p.sendMessage(ChatUtils.toTextComponent("§7§o*" + displayName + " whispers."));
            }
        }
    }

    /*
        ProcessBroadcast actually functions a lot line normal Minecraft chat. It's terribly simple and doesn't require much,
        but the commands and methods that call ProcessBroadcast will have a permissions check. Additionally, unlike regular
        chat, a broadcast does not provide a sender. Instead it reads <BROADCAST> (message). It is intended for admin purposes.
     */
    public static void ProcessBroadcast(String msg, ServerWorld world) {
        //this function only operates on the server, so ensure that we're not on a remote machine (client)
        //realistically, since we've requested a ServerWorld, this should never be true, but this is just a precaution
        if (world.isRemote)
            return;

        //loop through all players on the server
        for (ServerPlayerEntity p : world.getPlayers()) {
            //send the message to each player. sendMessage requires an ITextComponent, so we use a wrapper to make that look ok inline
            p.sendMessage(ChatUtils.toTextComponent("§6<§bBROADCAST§6>§b " + msg));
        }

    }

}
