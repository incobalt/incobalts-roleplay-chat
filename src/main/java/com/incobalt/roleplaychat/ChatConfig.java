package com.incobalt.roleplaychat;

import net.minecraft.util.ResourceLocation;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;

import java.util.ArrayList;
import java.util.List;

/*
    ChatConfig holds and sets up the mod's .toml config file. This is mostly a server mod, so
    there is only a server config available.
 */

//subscribe the class to the event bus subscriber so that the class can receive event callbacks
//this class uses the ModConfigEvent, so we need to register it to the MOD bus!
@Mod.EventBusSubscriber(modid = RoleplayChat.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public class ChatConfig {

    static final ForgeConfigSpec.Builder ServerBuilder = new ForgeConfigSpec.Builder();

    //the following builds the config file and assigns it toe the ForgeConfigSpec
    public static final ServerConfig SERVER;
    public static final ForgeConfigSpec SERVER_SPEC;
    static {
        //this builds the config and assigns it to the spec here. It's done in a static block to define the above values
        SERVER = new ServerConfig(ServerBuilder);
        SERVER_SPEC = ServerBuilder.build();
        //this is the other way of doing the same as the above. It's less readable and confusing, so I'm not using it.
        //final Pair<ServerConfig, ForgeConfigSpec> specPair = new ForgeConfigSpec.Builder().configure(ServerConfig::new);
        //SERVER_SPEC = specPair.getRight();
        //SERVER = specPair.getLeft();
    }

    //the following is a list of static variables that are the endpoint of a loaded config file. we store them here to
    //lower the overhead of accessing them from SERVER with .get(). You can forego this if you are using too much memory
    //overhead and access the variables from SERVER directly [ex: ChatConfig.SERVER.enableDistanceChat.get()]
    public static boolean enableDistanceChat;
    public static boolean useObfuscation;
    public static boolean enableMissives;
    public static boolean createCommands;
    public static boolean redirectVanillaCommands;
    public static boolean useChatSymbols;
    public static boolean missivesUseItems;
    public static boolean missiveServerSideItemChecks;
    public static boolean missiveCreateCommands;
    public static boolean missiveRedirectCommands;
    public static double distanceChatRange;
    public static double whisperRange;
    public static double missiveDistance;
    public static double missiveTime;
    public static int colorPermissionLevel;
    public static int broadcastPermissionLevel;
    public static int tellPermissionLevel;
    public static String shoutCharacter;
    public static String emoteCharacter;
    public static String whisperCharacter;
    public static String broadcastCharacter;
    public static String obfuscateCharacter;
    public static String missiveCharacter;
    public static ArrayList<String> missiveConsumableItems;
    public static ArrayList<String> missiveCatalystItems;


    //Subscribe this function to the event bus to receive events
    @SubscribeEvent
    public static void onModConfigEvent(final ModConfig.ModConfigEvent configEvent){
        //ModConfigEvent occurs after mod config has been loaded

        //a function that copies the values from the config spec to the ChatConfig class
        bakeConfig();

    }

    //simply copies the variables from the config subclass to the static variables in the parent class
    //you don't need this if you are going to access the variables using .get() [ex: ChatConfig.SERVER.enableDistanceChat.get()]
    public static void bakeConfig() {
        //note: you have to use .get() because the variables aren't regular variable types, but special ones for ForgeConfigSpec
        ChatConfig.enableDistanceChat = SERVER.enableDistanceChat.get();
        ChatConfig.useObfuscation = SERVER.useObfuscation.get();
        ChatConfig.createCommands = SERVER.createCommands.get();
        ChatConfig.redirectVanillaCommands = SERVER.redirectVanillaCommands.get();
        ChatConfig.useChatSymbols = SERVER.useChatSymbols.get();
        ChatConfig.enableMissives = SERVER.enableMissives.get();
        ChatConfig.missiveCreateCommands = SERVER.missiveCreateCommands.get();
        ChatConfig.missiveRedirectCommands = SERVER.missiveRedirectCommands.get();
        ChatConfig.missiveServerSideItemChecks = SERVER.missiveServerSideItemChecks.get();
        ChatConfig.missivesUseItems = SERVER.missiveUsesItems.get();
        ChatConfig.distanceChatRange = SERVER.distanceChatRange.get();
        ChatConfig.whisperRange = SERVER.whisperRange.get();
        ChatConfig.missiveDistance = SERVER.missiveDistance.get();
        ChatConfig.missiveTime = SERVER.missiveTime.get();
        ChatConfig.colorPermissionLevel = SERVER.colorPermissionLevel.get();
        ChatConfig.broadcastPermissionLevel = SERVER.broadcastPermissionLevel.get();
        ChatConfig.tellPermissionLevel = SERVER.tellPermissionLevel.get();
        ChatConfig.shoutCharacter = SERVER.shoutCharacter.get();
        ChatConfig.emoteCharacter = SERVER.emoteCharacter.get();
        ChatConfig.whisperCharacter = SERVER.whisperCharacter.get();
        ChatConfig.broadcastCharacter = SERVER.broadcastCharacter.get();
        ChatConfig.obfuscateCharacter = SERVER.obfuscateCharacter.get();
        ChatConfig.missiveCharacter = SERVER.missiveCharacter.get();
        ChatConfig.missiveConsumableItems = new ArrayList<>(SERVER.missiveConsumableItems.get());
        ChatConfig.missiveCatalystItems = new ArrayList<>(SERVER.missiveCatalystItems.get());
    }

    //this class is where you indicate what you want in the config file
    public static class ServerConfig{
        //these are all the variables that your config file will populate. They're all ForgeConfigSpec.ConfigValues
        //(net.minecraftforge.common.ForgeConfigSpec has some built in like BooleanValue, IntValue, and DoubleValue.
        //For anything not built in you'll need ConfigValue<type>, such as ConfigValue<String> used here.
        public final ForgeConfigSpec.BooleanValue enableDistanceChat;
        public final ForgeConfigSpec.BooleanValue useObfuscation;
        public final ForgeConfigSpec.BooleanValue enableMissives;
        public final ForgeConfigSpec.BooleanValue createCommands;
        public final ForgeConfigSpec.BooleanValue redirectVanillaCommands;
        public final ForgeConfigSpec.BooleanValue useChatSymbols;
        public final ForgeConfigSpec.BooleanValue missiveUsesItems;
        public final ForgeConfigSpec.BooleanValue missiveServerSideItemChecks;
        public final ForgeConfigSpec.BooleanValue missiveCreateCommands;
        public final ForgeConfigSpec.BooleanValue missiveRedirectCommands;
        public final ForgeConfigSpec.DoubleValue distanceChatRange;
        public final ForgeConfigSpec.DoubleValue whisperRange;
        public final ForgeConfigSpec.DoubleValue missiveDistance;
        public final ForgeConfigSpec.DoubleValue missiveTime;
        public final ForgeConfigSpec.IntValue colorPermissionLevel;
        public final ForgeConfigSpec.IntValue broadcastPermissionLevel;
        public final ForgeConfigSpec.IntValue tellPermissionLevel;
        public final ForgeConfigSpec.ConfigValue<String> shoutCharacter;
        public final ForgeConfigSpec.ConfigValue<String> emoteCharacter;
        public final ForgeConfigSpec.ConfigValue<String> whisperCharacter;
        public final ForgeConfigSpec.ConfigValue<String> broadcastCharacter;
        public final ForgeConfigSpec.ConfigValue<String> obfuscateCharacter;
        public final ForgeConfigSpec.ConfigValue<String> missiveCharacter;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> missiveConsumableItems;
        public final ForgeConfigSpec.ConfigValue<List<? extends String>> missiveCatalystItems;

        //the constructor of this class is where you actually set up the config file, including comments and default values
        public ServerConfig(ForgeConfigSpec.Builder builder){
            //ForgeConfigSpec.Builder.push(String) creates a category in your config file. You need to use pop() to close it later!
            //To add a comment to the category, start with .comment() and then go to .push(), as seen here.
            //comment can take multiple strings, but it seems like this jumbles up the order of configs in that category.
            builder.comment("Ranges, command, and symbol chat hooks")
                .push("Distance Chat Settings");
                //for each variable above, add a comment and then define it.
                enableDistanceChat = builder.comment("Enable the distance chat system.")
                        .define("Enable Distance Chat", true); //"Enable Distance Chat" will appear in your config file, so make it make sense to your users
                //for numeric values, you can define a range of acceptable values using defineInRange()
                distanceChatRange = builder.comment("Range (in blocks) regular chat is fully readable. Chat is obfuscated up to twice this range. Emotes are visible to this range. Shouts are three times this range.")
                        .defineInRange("Distance Chat Range", 15.0, 1.0, 256.0);
                useObfuscation = builder.comment("Determines if §k (obfuscated text) is used to muffle chat beyond distance chat range.")
                        .define("Use Obfuscation for Muffle", true);
                obfuscateCharacter = builder.comment("If not using obfuscation, what character replaces text when muffled?")
                    .define("Muffle Character", ".");
                whisperRange = builder.comment("Range (in blocks) whispers can be heard. Players 1 block outside of this range see '*Playername whispers.'")
                        .defineInRange("Whisper Range", 2.0, 1.0, 256.0);
                createCommands = builder.comment("Creates /shout, /whisper, and /emote (Roleplay chat can be used commandless!)")
                        .define("Create Distance Chat Commands", true);
                redirectVanillaCommands = builder.comment("Capture and redirect vanilla the commands /say, /w, and /me (only if Create Commands is true!)")
                        .define("Redirect Vanilla Commands", false);
            builder.pop(); //Distance Chat Settings

            //push a new category to the file
            builder.comment("Missives are an alternative to /mgs that sends messages over time.")
                    .push("Missive System");
                //define the relevant settings to show up in this category
                enableMissives = builder.comment("Enable missive system for allowing players to send messages long-distance under certain conditions.")
                        .define("Enable Missives", true);
                missiveDistance = builder.comment("The number of blocks to travel per time unit (set below)")
                        .defineInRange("Missive Distance", 100.0, 1.0, 10000);
                missiveTime = builder.comment("The amount of time in seconds it takes to travel the above amount of blocks")
                        .defineInRange("Missive Duration", 3.0, 1.0, 10000);
                missiveCreateCommands = builder.comment("Creates /missive for sending missives (missives can use chat symbols to remain commandless)")
                        .define("Create Missive Commands", true);
                missiveRedirectCommands = builder.comment("Redirects /msg and /tell to the /missive command (and /w if not redirected by distance chat settings). Only if /missive is created.")
                        .define("Redirect Missive Commands", true);
                missiveUsesItems = builder.comment("When a *player* sends a missive do they need an item to do it?")
                        .define("Missives Use Items", true);
                missiveServerSideItemChecks = builder.comment("This keeps the task of checking items on the server, which can allow you to have missive items and deploy the mod server only. This can cause a performance hit to the server!")
                    .define("Missive Item Processing on Server", false);
                ArrayList<String> exampleCons = new ArrayList<>();
                exampleCons.add("minecraft:paper");
                missiveConsumableItems = builder.comment("Missives use up one of these, unless holding a catalyst. Order: held first then left to right. Use [] to not have any consumable items.")
                        .defineList("Consumed Items", exampleCons, obj -> ResourceLocation.tryCreate((String)obj) != null);
                ArrayList<String> exampleCata = new ArrayList<>();
                exampleCata.add("minecraft:writable_book");
                missiveCatalystItems = builder.comment("When holding one of these, missives don't consume items. Use [] to not have any catalysts.")
                        .defineList("Catalyst Items", exampleCata, obj -> ResourceLocation.tryCreate((String)obj) != null);
            builder.pop(); //Missive System

            //add a new category to the file
            builder.comment("Chat messages that start with these symbols are converted to shout, emotes, whispers, or broadcasts. Does not affect the /say command!")
                    .push("Chat Symbols");
                //define the relevant settings to show up in this category
                useChatSymbols = builder.comment("This setting enables chat symbol conversion.")
                        .define("Use Chat Symbols", true);
                shoutCharacter = builder.comment("Chat messages starting with this character will be converted to shouts.")
                        .define("Shout Character", "!");
                emoteCharacter = builder.comment("Chat messages starting with this character will be converted to emotes.")
                        .define("Emote Character", "*");
                whisperCharacter = builder.comment("Chat messages starting with this character will be converted to whispers.")
                        .define("Whisper Character", "_");
                broadcastCharacter = builder.comment("Chat messages starting with this character will be converted to broadcasts.")
                        .define("Broadcast Character", "#");
                missiveCharacter = builder.comment("Chat messages starting with this character will be converted to missives.")
                        .define("Broadcast Character", ">");
                //Remember to pop your categories!
            builder.pop(); //Chat Symbols

            //push a new category to the file
            builder.comment("Various parts of this mod can be restricted to permission levels. These are all defaulted to level 2 (op is 4).")
                    .push("Permissions");
                //define the relevant settings to show up in this category
                colorPermissionLevel = builder.comment("Permission level required for players to be able to use color codes in chat commands using & in place of §.")
                        .defineInRange("Color Permission Level", 2, 0, Integer.MAX_VALUE);
                broadcastPermissionLevel = builder.comment("Permission level required for players to be able to use the /broadcast command, sending a message to everyone on the server.")
                        .defineInRange("Broadcast Permission Level", 2, 0, Integer.MAX_VALUE);
                tellPermissionLevel = builder.comment("Permission level required for players to be able to use the /tell, /msg, and /w vanilla commands, unless redirected")
                        .defineInRange("Tell Permission Level", 2, 0, Integer.MAX_VALUE);
            builder.pop(); //Permissions

        }
    }
}
