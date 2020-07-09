package com.incobalt.roleplaychat;

import com.incobalt.roleplaychat.missive.MissivePacket;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.network.NetworkRegistry;
import net.minecraftforge.fml.network.simple.SimpleChannel;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;

/*
    ServerClientBridge is a packet handler, which means it ferries data between the server and the client
    when needed. This is often used when the server wants the client to do some work and/or send back information.
    We use this mostly for checking Missive item requirements that could be quite expensive on a server,
    so we offload the heavy lifting to the client. This helps keep server load down, since server load can cause the
    server to hang while processing, which would cause server lag for each player!
 */

@Mod.EventBusSubscriber(modid = "incobalts_roleplaychat", bus = Mod.EventBusSubscriber.Bus.MOD)
public class ServerClientBridge {
    //PROTOCOL_VERSION here is used to verify that the client and server are using the same version
    private static final String PROTOCOL_VERSION = "1";
    //SimpleChannel is the actual packet handler. It requires a ResourceLocation id (we use "incobalts_roleplaychat:main" here
    //the second argument is a predicate that determines what version the client or server is using (PROTOCOL_VERSION here)
    //the third argument is how to test if the server has the proper version. In this case, we always want the server to be
    //running a Forge server with the mod installed
    //the last argument is how to test if the client has the proper version. We use a lambda to check it against
    //PROTOCOL_VERSION, NetworkRegistry.ABSENT, and NetworkRegistry.ACCEPTVANILLA. ABSENT is for forge clients without
    //this mod, and ACCEPTVANILLA is non-forge clients. Since the client could not be running the mod at all, we want both.
    public static final SimpleChannel INSTANCE = NetworkRegistry.newSimpleChannel(
            new ResourceLocation("incobalts_roleplaychat", "main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            ver -> { return ver.equals(PROTOCOL_VERSION) || ver.equals(NetworkRegistry.ABSENT) || ver.equals(NetworkRegistry.ACCEPTVANILLA); }
    );

    //we call registerMessages when the server starts, or when the client loads. It sets up the kinds of messages we can transfer
    public static void registerMessages(){
        //messages need a unique ID, so we use an int here to increment
        int id = 0;
        //registers the MissivePacket message which transfers data about a missive to be sent (see MissivePacket.java)
        INSTANCE.registerMessage(id++, MissivePacket.class, MissivePacket::encode, MissivePacket::new, MissivePacket::handler);
    }

    //regester an event to occur during setup for both client and server
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event){
        //register the messages set up above
        ServerClientBridge.registerMessages();
    }

}
