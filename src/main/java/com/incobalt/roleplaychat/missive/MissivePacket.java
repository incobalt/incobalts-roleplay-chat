package com.incobalt.roleplaychat.missive;

import com.incobalt.coreapi.CoreAPI;
import com.incobalt.coreapi.chat.ChatUtils;
import com.incobalt.roleplaychat.ServerClientBridge;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.Tuple;
import net.minecraftforge.fml.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/*
    MissivePacket is a packet used to offload the missive item requirements work from the server to the client. The server
    requests the client to check for items and then the client responds by telling the server what kind of item (catalyst
    or consumable) was used and where to find it. The server then uses up any consumable used and sends the missive. This
    starts in the missive command or chat symbol entry point.
 */
public class MissivePacket {
    //this is the data we want to transfer. Only the client will fill out this fully, with response data, while the server
    //will only fill out message and the two UUIDs, which it wants to use later to send the missive.
    private final String message;
    //packets can't contain much data, so we use UUIDs to store who the sender and receiver are
    //private final UUID senderID;
    private final UUID targetID;
    //usedConsumable becomes true if a consumable is the item used to satisfy missive item requirements
    private final boolean usedConsumable;
    //consumableSlot is the inventory item slot (or hand slot) for the item being used to satisfy item requirements
    // -1 will be for main hand, -2 for off hand, -3 for no found item, any other number is an inventory slot number
    private final int consumableSlot;

    //this constructor is used by the client to provide a result to the server about its item checks
    public MissivePacket(String message, UUID targetID, boolean usedConsumable, int consumableSlot){
        this.message = message;
        this.targetID = targetID;
        this.usedConsumable = usedConsumable;
        this.consumableSlot = consumableSlot;
    }

    //this simplified constructor is used by the server to request that the client perform item requirement calculations
    public MissivePacket(String message, UUID targetID){
        this.message = message;
        this.targetID = targetID;
        //the server just uses false and no item for these fields, since it doesn't know yet
        usedConsumable = false;
        consumableSlot = -3;
    }

    //this constructor is used by the ServerClientBridge (our packet handler) to read the request from client or server
    //*order is important!*
    public MissivePacket(PacketBuffer buf){
        //the read order *must* match the write order!
        //readString() only exists in client code to prevent malicious strings from being sent to the server. As such,
        //we must use readString(int maxlen) to cap the size of the missive. We've chosen a size on 800 characters.
        message = buf.readString(800);
        targetID = buf.readUniqueId();
        usedConsumable = buf.readBoolean();
        consumableSlot = buf.readInt();
    }

    //encode is used by the ServerClientBridge (our packet handler) to prepare a packet for sending
    //*order is important!*
    public void encode(PacketBuffer buf){
        //the write order *must* match the read order!
        buf.writeString(message);
        buf.writeUniqueId(targetID);
        buf.writeBoolean(usedConsumable);
        buf.writeInt(consumableSlot);
    }

    //handler is used by ServerClientBridge (our packet handler) when it receives a packet of this type
    //ctx is a very limited context variable that can tell you a little about the packet and who sent it
    public void handler(Supplier<NetworkEvent.Context> context){
        //get the context from the supplier
        final NetworkEvent.Context ctx = context.get();
        //you can't actually touch many game objects in the handler itself, so use enqueueWork to have the machine do something when ready
        ctx.enqueueWork(() -> {
            //you can tell what logical side you're on by using getReceptionSide() (gets the receiver's side)
            //this allows you to have different behavior for packets received by a client or by a server
            if(ctx.getDirection().getReceptionSide().isClient() && ctx.getDirection().getOriginationSide().isServer()){
                //the client is the receiver. For this packet, this means the server has requested the client perform missive item requirements
                //we want the player, but can't reference ClientPlayerEntity or the server will crash when registering the
                //packet. We use a proxy to do the work, avoiding the crash. The proxy is located in incobalt's Core API
                final PlayerEntity receiver = CoreAPI.proxy.getEntityFromContext(context);
                if(receiver == null) {
                    //there *should* be a player on the client, if not, something strange has happened!
                    CoreAPI.LOGGER.info("No player found on the client!");
                    //setPacketHandled(true) tells the handler that the packet is finished. Otherwise, if there was further
                    //message matches in ServerClientBridge, then it would continue on to the next one
                    ctx.setPacketHandled(true);
                    return;
                }
                //getUsedItem will provide both a boolean (if a consumable was found) and an inventory slot number
                //-3 is considered no item, while -1 and -2 are main and off hand respectively
                Tuple<Boolean, Integer> result = Missive.getUsedItem(receiver);

                if(result.getB() == -3){
                    //-3 means that no item was found! getUsedItem already sends a message to the player, so just return
                    ctx.setPacketHandled(true);
                    return;
                }

                //send the data back to the server. sendToServer just takes a packet object (MissivePacket in this case)
                ServerClientBridge.INSTANCE.sendToServer(new MissivePacket(message, targetID, result.getA(), result.getB()));
                //setPacketHandled(true) tells the handler that the packet is finished. Otherwise, if there was further
                //message matches in ServerClientBridge, then it would continue on to the next one
                ctx.setPacketHandled(true);


            }else if(ctx.getDirection().getReceptionSide().isServer() && ctx.getDirection().getOriginationSide().isClient()){
                //the server is the receiver. For this packet, this means that the server has received the requested data
                //on the server we need to process the missive (Missive.SendMissive) and consume items (if needed)
                if(targetID == null){
                    //there's no target, so it's impossible to do anything here (we need a player!)
                    //for non-player missives, this is handled without the server/client bridge!
                    ctx.setPacketHandled(true);
                    return;
                }

                //we must avoid referencing ServerPlayerEntity in the packet, or the client will crash when registering
                //the packet as a message (crossloading). We use a proxy to do this for us. The proxy is located in
                //incobalt's Core API
                PlayerEntity sender = CoreAPI.proxy.getEntityFromContext(context);


                //it's unlikely, but possible that the sender ends up null. We check just to be safe.
                if(sender == null){
                    //ServerPlayerEntity gives us needed access to a ServerWorld, so we can't continue if there's no sender
                    ctx.setPacketHandled(true);
                    return;
                }

                //we get the target from the stored UUID sent along with the request.
                PlayerEntity target = sender.world.getPlayerByUuid(targetID);

                if(target == null){
                    //target was not found! This might happen if the instant the server responded the target logged out.
                    //sendMessage requires an ITextComponent. We use a wrapper to make it look better inline.
                    sender.sendMessage(ChatUtils.toTextComponent("§c§oThat player doesn't exist, but did very recently!"));
                    //setPacketHandled(true) tells the handler that the packet is finished. Otherwise, if there was further
                    //message matches in ServerClientBridge, then it would continue on to the next one
                    ctx.setPacketHandled(true);
                    return;
                }

                //to keep things clean, we make the Missive class do the task of removing items and sending the missive
                Missive.sendItemizedMissive(message, sender, target, usedConsumable, consumableSlot);

                //setPacketHandled(true) tells the handler that the packet is finished. Otherwise, if there was further
                //message matches in ServerClientBridge, then it would continue on to the next one
                ctx.setPacketHandled(true);
            }
        });
    }
}
