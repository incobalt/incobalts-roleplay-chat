package com.incobalt.roleplaychat.missive;


import com.incobalt.coreapi.CoreAPI;
import com.incobalt.coreapi.chat.ChatUtils;
import com.incobalt.roleplaychat.ChatConfig;
import com.incobalt.roleplaychat.RoleplayChat;
import com.incobalt.roleplaychat.ServerClientBridge;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Hand;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.Tuple;
import net.minecraft.util.math.Vec3d;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkDirection;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

/*
    Missive System
    The Missive System is an overhaul to the standard /msg command that sends messages over time, rather than instantly,
    to give the impression of sending something out into the world that needs to be delivered. The amount of time it takes
    for a missive to arrive at its destination depends on how far away the sender and receiver are. It travels an amount
    of blocks in an amount of seconds (both set in the config, default is 100 blocks in 3 seconds).

    Missives can optionally use items. A missive might use up a consumable item (defined in the config). Consumables
    always check held items before checking the inventory. A missive might also use a held catalyst, which is an item
    that doesn't get used up but allows the player to send a message. Catalysts are checked before consumables. Both are
    optional in the config.

    Missives are complicated and require server/client packets to function with items. If items and commands aren't used,
    then they can operate on the server side alone.
 */

//register's the Missive class to the Forge bus, so that the class can receive events
@Mod.EventBusSubscriber(modid = RoleplayChat.MODID)
public class Missive {
    //we need to keep track of all active missives, in order to deliver them when their duration has finished
    private static final ArrayList<SentMissive> missiveQueue = new ArrayList<>();

    //processChatMissive is a helper function that extracts a target from a chat symbol activation
    //the pattern received by the chat processor is:
    //[target] [message] (without the brackets)
    public static boolean processChatMissive(String command, ServerPlayerEntity sender){
        //find the first space in the command, which separates the target from the message
        final int separator = command.indexOf(" ");
        if(separator == -1){
            //a player name couldn't be found, so abort!
            return false;
        }
        //strip out the target's name
        final String target = command.substring(0, separator).trim();
        //strip out the message
        final String message = command.substring(separator).trim();

        //sender should never be null, but check just in case
        if(sender == null){
            //return false in this situation
            return false;
        }

        //we have a hard limit on missives to be not more than 800 characters, check that first!
        if(message.length() >= 800){
            sender.sendMessage(ChatUtils.toTextComponent("§c§oA missive must be less than 800 characters. Your missive has " + message.length() + " characters!"));
            return true;
        }

        //there's no easy way to get a player by username, so this uses a predicate to filter the list of players
        //by the name we received from the command
        final List<ServerPlayerEntity> playersFound = sender.getServerWorld().getPlayers(p -> { return p.getName().getFormattedText().equals(target); });
        //if we find no player, then we need to return
        if(playersFound.size() == 0){
            //return false informs the calling method (chat event) that there was a problem parsing the command
            return false;
        }

        //get the first ServerPlayerEntity as the target
        final ServerPlayerEntity targetEntity = playersFound.get(0);

        //it's possible that missives might need required items. We offload this to the client to lessen the server load.
        if(ChatConfig.missivesUseItems && (ChatConfig.missiveConsumableItems.size() > 0 || ChatConfig.missiveCatalystItems.size() > 0)){
            //we're using items, which take a bit of processing. The mod can be set up to do this server side, so check that first
            if(ChatConfig.missiveServerSideItemChecks){
                //getUsedItem will provide both a boolean (if a consumable was found) and an inventory slot number
                //-3 is considered no item, while -1 and -2 are main and off hand respectively
                Tuple<Boolean, Integer> result = Missive.getUsedItem(sender);
                if(result.getB() == -3){
                    //-3 means that no item was found! getUsedItem already sends a message to the player, so just return
                    return true;
                }

                //removes the items if needed and sends the missive
                Missive.sendItemizedMissive(message, sender, targetEntity, result.getA(), result.getB());

                return true;
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
                    targetEntity.getUniqueID()
            ), sender.connection.getNetworkManager(), NetworkDirection.PLAY_TO_CLIENT);
            //we need to wait for the client to finish, so we're done here!
            return true;
        }

        //send the missive based on the information
        //sendMissive has more error checking beyond what goes on here
        Missive.sendMissive(message, sender, targetEntity);

        //returning true here means that we successfully sent the message
        return true;
    }

    //sendMissive sends a message over time to a target player. This version of sendMissive is intended for non-player
    //senders, and doesn't require an entity sender, just a name and a position.
    public static void sendMissive(String message, String senderName, Vec3d senderPos, PlayerEntity target){

        //calculate the distance and duration for the missive
        //get the distance between the source and the target (squared value!)
        final double distance = target.getDistanceSq(senderPos);
        //get the amount of time it takes for a message to be delivered.
        //this is squared distance divided by squared missive length to get distance per one second,
        //then multiplied by the missive time multiplier and the number of ticks in a second (20)
        final int duration = (int)(((distance / Math.pow(ChatConfig.missiveDistance, 2.0)) * ChatConfig.missiveTime) + ChatConfig.missiveTime) * 20;

        //add the missive to the queue to be processed over time
        missiveQueue.add( new SentMissive(duration,
                message,
                senderName,
                target)
        );
    }

    //sendMissive sends a message over time to a target player. This version of sendMissive is intended for players and is
    //called from the packet handler after offloading work to the client (if we need to check items at least). This is
    //called directly from the missive command or from the chat symbol handler if the server doesn't need to check required items.
    //sender and target are both PlayerEntities, but are likely ServerPlayerEntities
    public static void sendMissive(String message, PlayerEntity sender, PlayerEntity target){

        //if we don't get a sender, we shouldn't continue
        if(sender == null){
            return;
        }

        //if we don't get a target, then we definitely shouldn't continue, and inform the sender why
        if(target == null){
            sender.sendMessage(ChatUtils.toTextComponent("§c§oThat player doesn't exist!"));
            return;
        }

        //calculate the distance and duration for the missive
        //get the distance between the source and the target (squared value!)
        final double distance = target.getDistanceSq(sender.getPositionVec());
        //get the amount of time it takes for a message to be delivered.
        //this is squared distance divided by squared missive length to get distance per one second,
        //then multiplied by the missive time multiplier and the number of ticks in a second (20)
        int duration = (int)(((distance / Math.pow(ChatConfig.missiveDistance, 2.0)) * ChatConfig.missiveTime) + ChatConfig.missiveTime) * 20;

        //we could choose to make dimensions inaccessible for missives, but instead, we make it take longer to arrive
        if(sender.world.dimension != target.world.dimension){
            duration = duration * 8;
            //it's possible, though unlikely, that the setting will make this number overflow to negative here, so check for that
            //note that this would need settings like 10000 seconds per block traveled, and be ~27,000 blocks apart and would take
            //years to resolve, but might as well make certain it's not negative or it will immediately resolve
            if(duration < 0)
                duration = Integer.MAX_VALUE;

        }

        //add the missive to the queue to be processed
        missiveQueue.add( new SentMissive(duration,
                message,
                sender,
                target)
        );
        //inform the sender that their message was successful
        sender.sendMessage(ChatUtils.toTextComponent("§a§oYour missive to " + target.getDisplayName().getFormattedText() + " is being delivered!"));
    }

    /*
        sendItemizedMissive is a function called from the MissivePacket handler where the server has received the needed
        item usage information and is ready to send the missive. This work is kept in the Missive class for cleanliness.
        This function will use up any consumable and then send the actual missive to the target. Due to limitations with
        the packet handling system, sender and target must be PlayerEntities so we don't try to crossload ServerPlayerEntity
        in the client's packet handler (jvm will check types even if it wouldn't execute the code!)
     */
    public static void sendItemizedMissive(String message, PlayerEntity sender, PlayerEntity target, boolean usedConsumable, int consumableSlot) {
        //this tells us that we used a consumable, so we need to remove an item
        if(usedConsumable){
            //-3 means no item. We shouldn't get here, because Missive.getUsedItem checks for it first, but just in case we check for it
            if(consumableSlot == -3){
                //let the player know what happened. sendMessage requires an ITextComponent. We use a wrapper to make it look better inline.
                sender.sendMessage(ChatUtils.toTextComponent("§c§oRequired items not found!"));
                //setPacketHandled(true) tells the handler that the packet is finished. Otherwise, if there was further
                //message matches in ServerClientBridge, then it would continue on to the next one
                return;
            }
            //check the slot that we found with Missive.getUsedItem()
            if(consumableSlot == -1){
                //-1 means main hand, which means the player was holding the item in the main hand at the time
                //get the itemstack in the main hand (for decrementing)
                final ItemStack mainItem = sender.getHeldItemMainhand();
                //if an item is stackable then we can lower its stack size
                if(mainItem.isStackable()){
                    //remove an item from the stack. Minecraft automatically updates the item!
                    mainItem.setCount(mainItem.getCount() - 1);
                }else{
                    //otherwise, we just empty out the hand for non-stackable items
                    sender.setHeldItem(Hand.MAIN_HAND, ItemStack.EMPTY);
                }
                //make sure to update a held item if you change it!
                if(sender instanceof ServerPlayerEntity) {
                    ((ServerPlayerEntity)sender).updateHeldItem();
                }
            }else if(consumableSlot == -2){
                //-2 means off hand, which means the player was holding the item in the off hand at the time
                //get the itemstack in the off hand (for decrementing)
                final ItemStack offItem = sender.getHeldItemOffhand();
                //if an item is stackable then we can lower its stack size
                if(offItem.isStackable()){
                    //remove an item from the stack. Minecraft automatically updates the item!
                    offItem.setCount(offItem.getCount() - 1);
                }else{
                    //otherwise, we just empty out the hand for non-stackable items
                    sender.setHeldItem(Hand.OFF_HAND, ItemStack.EMPTY);
                }
                //make sure to update a held item if you change it!
                ((ServerPlayerEntity)sender).updateHeldItem();
            }else{
                //any slot 0 and higher means an inventory item slot
                //get the itemstack for the inventory slot in question (for decrementing)
                ItemStack stack = sender.inventory.getStackInSlot(consumableSlot);
                //if an item is stackable then we can lower its stack size
                if(stack.isStackable()){
                    //remove an item from the stack. Minecraft automatically updates the item!
                    stack.setCount(stack.getCount() - 1);
                }else{
                    //otherwise, we just empty out the slot for non-stackable items
                    sender.replaceItemInInventory(consumableSlot, ItemStack.EMPTY);
                }
            }
        }
        //when we've finished with consumables (if we needed them), send the missive
        Missive.sendMissive(message, sender, target);
    }

    //getUsedItem is used by the client to determine if their player has the required item for sending missives
    //this method is called by the client after receiving a request from the server over the ServerClientBridge
    //in this method, we check for catalysts in the hand first, then consumables in the hand and in the inventory
    //in that order. The method returns a Tuple (two value pair) of boolean (did we use a consumable?) and int
    //(which slot was the item). These are used by the server to remove consumables before sending a missive.
    public static Tuple<Boolean, Integer> getUsedItem(PlayerEntity player){
        //Tupples have a getA() and getB() to get the two values. The values cannot be set after initialization, so you
        //need to use new Tupple<> each time.
        //A is usedConsumable, B is the inventory slot (-3 doesn't have item, -1 main hand, -2 offhand)
        Tuple<Boolean, Integer> ret = new Tuple<>(false, -3);

        //we shouldn't have this situation, but if both catalysts and consumables are empty, then we need to abort early
        if(ChatConfig.missiveCatalystItems.size() == 0 && ChatConfig.missiveConsumableItems.size() == 0){
            //there aren't any items to compare to! This shouldn't happen, so write to the log
            CoreAPI.LOGGER.info("Was asked to check for missive items, but no items are defined!");
            //note, we still return a valid, passing Tupple (catalyst in slot 0), because we didn't need anything
            //catalysts aren't affected by sending missives, so it's a fake success result that pushes the missive through
            ret = new Tuple<>(false, 0);
            return ret;
        }

        //cache the item in the player's main hand
        final ItemStack mainItem = player.getHeldItemMainhand();
        //we need a ResourceLocation for comparing against item id strings (ex: minecraft:paper), so grab that too
        ResourceLocation mainItemName = null;
        //if the main hand isn't empty, then populate that item's ResourceLocation for comparison
        if(!mainItem.isEmpty()) {
            mainItemName = mainItem.getItem().getRegistryName();
        }

        //do the same for the off hand. We will use main hand and off hand multiple times this method
        final ItemStack offItem = player.getHeldItemOffhand();
        ResourceLocation offItemName = null;
        if(!offItem.isEmpty()) {
            offItemName = offItem.getItem().getRegistryName();
        }

        //loop through all of the catalyst items (if any). The list is formatted in strings
        for(String item : ChatConfig.missiveCatalystItems){
            //check the main hand first. ResourceLocation.toString() gives you the id name (ex: minecraft:paper)
            if(!mainItem.isEmpty() && mainItemName != null && mainItemName.toString().equals(item)){
                //create a new Tuple to return. This one reads, main hand, catalyst
                ret = new Tuple<>(false, -1);
                return ret;
            }
            //check the off hand next. ResourceLocation.toString() gives you the id name (ex: minecraft:paper)
            if(!offItem.isEmpty() && offItemName != null && offItemName.toString().equals(item)){
                //create a new Tuple to return. This one reads, off hand, catalyst
                ret = new Tuple<>(false, -2);
                return ret;
            }
        }

        //loop through all of the consumable items (if any). The list is formatted in strings
        //we'll do this twice, once for main hand/off hand and then for inventory slots
        for(String item : ChatConfig.missiveConsumableItems){
            //check the main hand first. ResourceLocation.toString() gives you the id name (ex: minecraft:paper)
            if(!mainItem.isEmpty() && mainItemName != null && mainItemName.toString().equals(item)){
                //create a new Tuple to return. This one reads, main hand, consumable
                ret = new Tuple<>(true, -1);
                return ret;

            }
            //check the off hand next. ResourceLocation.toString() gives you the id name (ex: minecraft:paper)
            if(!offItem.isEmpty() && offItemName != null && offItemName.toString().equals(item)){
                //create a new Tuple to return. This one reads, off hand, consumable
                ret = new Tuple<>(true, -2);
                return ret;
            }
        } //for items in ConsumableItems (hand check)

        //loop again through all of the consumable items (if any). The list is formatted in strings
        //this time for inventory items
        for(String item : ChatConfig.missiveConsumableItems) {
            //loop through each inventory slot in the player's inventory
            //this is largely the reason that we offload the work to the client
            for(int i = 0; i < player.inventory.getSizeInventory(); i++){
                //get the item stack at that slot
                ItemStack stack = player.inventory.getStackInSlot(i);
                ResourceLocation stackName = null;
                //check if the stack is an empty item
                if(!stack.isEmpty()){
                    //get the name of the stack
                    stackName = stack.getItem().getRegistryName();
                    //compare to the currently looped item
                    if(stackName != null && stackName.toString().equals(item)){
                        //if it's true, return the Tupple. This one reads consumable, item slot(i)
                        ret = new Tuple<>(true, i);
                        return ret;
                    }
                }
            }
        }

        //if we get here, then the player didn't have any of the required items and we have to inform the server of this
        //first, though, we build a message that informs the player of the problem (and informs them of the items they can use)
        //we use a StringBuilder to build up the string.
        StringBuilder errorMessage = new StringBuilder("§c§oTo send a missive ");
        if(ChatConfig.missiveCatalystItems.size() > 0){
            //if there are catalyst items, build the catalyst message
            errorMessage.append("you must be holding one of: ");
            //loop through the catalysts
            for(String item : ChatConfig.missiveCatalystItems){
                //using tryCreate makes a ResourceLocation from a id string (ex: minecraft:paper)
                ResourceLocation res = ResourceLocation.tryCreate(item);
                if (res != null) {
                    //if it's not null, then grab the item from the registry
                    //we do this simply to get the item's printed name
                    ItemStack itemRef = new ItemStack(ForgeRegistries.ITEMS.getValue(res));

                    //add the name to our string and add a comma and space
                    errorMessage.append(itemRef.getDisplayName().getFormattedText()).append(", ");
                }
            }

            //if there are consumables to report, add an or clause and make a new line
            if(ChatConfig.missiveConsumableItems.size() > 0){
                errorMessage.append("§c§oor ");
            }else{
                //we're not adding more to the string, so finalize it.
                //this removes the comma and space on the last item, and then adds a period
                errorMessage.deleteCharAt(errorMessage.length() - 1);
                errorMessage.deleteCharAt(errorMessage.length() - 1);
                errorMessage.append(".");
            }
        }

        if(ChatConfig.missiveConsumableItems.size() > 0){
            //if there are consumable items, build the consumable message
            errorMessage.append("you must have one of: ");
            //loop through the consumables
            for(String item : ChatConfig.missiveConsumableItems){
                //using tryCreate makes a ResourceLocation from a id string (ex: minecraft:paper)
                ResourceLocation res = ResourceLocation.tryCreate(item);
                if (res != null) {
                    //if it's not null, then grab the item from the registry
                    //we do this simply to get the item's printed name
                    ItemStack itemRef = new ItemStack(ForgeRegistries.ITEMS.getValue(res));

                    //add the name to our string and add a comma and space
                    errorMessage.append(itemRef.getDisplayName().getFormattedText()).append(", ");
                    /*
                    Item itemRef = ForgeRegistries.ITEMS.getValue(res);
                    //double check that we got an actual item
                    if(itemRef != null) {
                        //add the name to our string and add a comma and space
                        errorMessage.append(itemRef.getName().getFormattedText()).append(", ");
                    }
                     */
                }
            }
            //remove the extra comma from the string
            errorMessage.deleteCharAt(errorMessage.length() - 2);
            //finalize the message
            errorMessage.append("in your inventory (consumes one).");
        }
        //send the player the message. sendMessage takes an IComponentText, so we use a wrapper to make it look nice inline
        player.sendMessage(ChatUtils.toTextComponent(errorMessage.toString()));
        //returns the failure Tuple defined at the start of the method
        return ret;
    } // getUsedItem()


    //ServerTickEvent is an event that fires every tick (20 times a second)
    //you should aim to do *very little* in the tick event. Any extra processing can cause server lag!
    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event){

        //TickEvents happen twice each tick, at the start and at the end. We only want to fire once, so we choose the end
        if(event.phase == TickEvent.Phase.END) {
            //loop through the missives in the queue. If players are sending a lot of missives, this could bog down the server
            //but there's very little going on unless a missive is delivered here
            for (int i = 0; i < missiveQueue.size(); i++) {
                //get the missive in question and process a tick (see below)
                if (missiveQueue.get(i).MissiveTick()) {
                    //if the tick returns true, then it was delivered, so remove it
                    missiveQueue.remove(i);
                    //if you remove something, make sure to reduce the index by one!
                    i--;
                }
            }
        }
    }

    /*
        SentMissive is a class for missives that have been sent but haven't been delivered.
        This class is meant to be queued and will count down time when processed, until it is finished, and then it
        delivers the missive to the target (letting the sender, if any know that it was delivered)
     */
    private static class SentMissive{
        //duration is the time it takes in ticks to deliver the missive
        public final int duration;
        //message is a copy of the missive text
        public final String message;
        public final Entity sender;
        public final Entity target;
        //we cache the sender and target names in case they don't exist when the missive is delivered
        public final String senderName;
        public final String targetName;
        //this is the tick counter. This counts up each tick to track time
        private int ticks;

        //this is a version of the constructor that cares about the sender. Used mostly by entity senders.
        public SentMissive(int duration, String message, Entity sender, Entity target){
            this.duration = duration;
            this.message = message;
            this.sender = sender;
            this.target = target;
            if(sender != null) {
                this.senderName = sender.getDisplayName().getFormattedText();
            }else{
                this.senderName = "someone";
            }
            if(target != null) {
                this.targetName = target.getDisplayName().getFormattedText();
            }else{
                this.targetName = "someone";
            }
            this.ticks = 0;
        }

        //this is a version of the constructor that doesn't care about the sender. Used mostly by non-entity senders.
        public SentMissive(int duration, String message, String senderName, Entity target){
            this.duration = duration;
            this.message = message;
            this.sender = null;
            this.target = target;
            this.senderName = senderName;
            if(target != null) {
                this.targetName = target.getDisplayName().getFormattedText();
            }else{
                this.targetName = "someone";
            }
        }

        //MissiveTick is run every tick by the Missive class. As such, there should be very little happening here
        //most of the time. This is mostly just a timer that counts up to the duration then sends the missive (if
        //the target still exists)
        public boolean MissiveTick(){
            //increment the counter and test it against the expected duration
            ticks++;
            if(ticks < duration){
                //stop processing if the timer hasn't reached the duration
                //return false will keep the message in the queue
                return false;
            }
            //otherwise, process the message
            if(target == null){
                //the target was not found! Get the sender now to inform them
                if(!(sender instanceof PlayerEntity)){
                    //the sender could also not be found or wasn't a PlayerEntity There's no reason to keep the message anymore.
                    //returning true will remove the missive from the queue
                    return true;
                }
                //let the sender know their missive couldn't be delivered. We give a copy of the missive as a reference
                sender.sendMessage(ChatUtils.toTextComponent("§a§oYour missive to " + targetName + " returned unread. The missive read: §c§o" + message));
                //returning true will remove the missive from the queue
                return true;
            }
            //the target was found
            if(!(target instanceof PlayerEntity)){
                //the target was found, but wasn't a player, somehow. We'll consider this a failed delivery.
                if(!(sender instanceof PlayerEntity)){
                    //the sender could also not be found or wasn't a PlayerEntity There's no reason to keep the message anymore.
                    //returning true will remove the missive from the queue
                    return true;
                }
                //let the sender know their missive couldn't be delivered. We give a copy of the missive as a reference
                sender.sendMessage(ChatUtils.toTextComponent("§a§oYour missive to " + targetName + " returned unread. The missive read: §c§o" + message));
                //returning true will remove the missive from the queue
                return true;
            }
            //send the message to the target
            target.sendMessage(ChatUtils.toTextComponent("§a§oYou receive a missive from " + senderName + " that reads: §e§o" + message));
            //now send a message to the sender letting them know their message was delivered.
            if(!(sender instanceof PlayerEntity)){
                //the sender could also not be found or wasn't a PlayerEntity. There's no reason to keep the message anymore.
                //returning true will remove the missive from the queue
                return true;
            }
            //inform the sender of the delivery
            sender.sendMessage(ChatUtils.toTextComponent("§a§oYour missive to " + targetName + " has been delivered."));
            //returning true will remove the missive from the queue
            return true;
        }
    }
}
