# incobalt's Roleplay Chat
A Minecraft Forge mod for creating a roleplay-focused chat experience for you and your players.

**incobalt's Roleplay Chat** is a small server-focused mod that alters the way chat works in Minecraft.

The main feature of this mod is a distance-based chat to keep chat localized to an area. The mod also adds commands to simulate raising (/shout) and lowering (/whisper) your voice, and an admin command to send a message to everyone on the server (/broadcast). Chat color codes are also implemented with &, but this is restricted to a configurable permission level.

Also included is a missive system that replaces Minecraft's /msg command. Missives greatly restrict long-range messaging to a time-based system that optionally uses resources.

## Say
Bare messages in the chat are sent to people in a configurable distance (default 15 blocks), and then become less clear the further away you are. After double the distance, you don't see the message at all. When it becomes less clear, it replaces characters with obfuscated text by default, but this can be changed to replace with a set character like a period.

## Shout
Players can use /shout or /s to send a message to everyone in a larger distance that Say does. The increased distance is 3 times the configured say distance (or 45 blocks by default). Shouts do not fall off like say messages do, they just can't be heard outside that range. Players can also start their chat message with an ! (configurable) to do the same.

## Whisper
Players can use /whisper or /whis to send a message to players very close to them. The default range is two blocks. Players one block outside the range will see an emotive message "<playername> whispers." but won't recieve the actual text of the message. Whispers were designed for enabling sneaky play and eavesdropping. Players can start a chat message with _ (configurable) to do the same.

## Emote
Players can use /emote to create messages in the chat that look like an action, rather than speech. This is basically a reimplementation of /me with different formatting. Chat messages starting with * (configurable) will also be converted to emotes.

## Broadcast
Since chat messages no longer reach every player on the server, this functionality has been moved to /broadcast (or /br or /bc). This sends a message to every player on the server prefaced by "[BROADCAST]". Broadcasts can be configured to be available only to certain permission levels (default permission level 2). Broadcast is intended for administrative communication to players. Chat messages starting with a # (configurable) will be converted to broadcasts.

## Missive
Perhaps the most complicated system in this mod, Missives replace the standard direct message system of Minecraft with a more flavorful one. Missives are messages sent over time based on how far away the sender and receiver are when the missive is sent. By default, the missive travels about 100 blocks in 3 seconds plus an extra 3 seconds for processing. Players receive information about when their missive is recieved, or if it couldn't be delivered. By default, missives require items to be able to send them. When a player uses the /missive command, they need to be holding a book and quill in either hand or have paper on their person. Paper will be used up, but a book and quill will remain. Both of these items can be changed, and you can have multiple items that function in either way. It always checks the non-consumable before the consumable, and the hands before the inventory. Chat messages that start with > (configurable) can be converted to missives using the following format: ">playername message".

## Other Configurable Options

I tried to make incobalt's Roleplay Chat pretty configurable in the server .toml file (you'll have to run the server to generate the file or use the one provided in the source). Here are some of the things you can do:
- Disable commands entirely and just use chat symbols
- Disable or change any of the chat symbols
- Replace vanilla commands (see **Important note!** below!)
- Set permissions on color coding, broadcast, and /msg, /tell or /w (if those aren't being used by the missive system)
- Disable missives
- Disable distance-based chat but keep missives
- Have missives use no items
- Move missive item processing to the server (**required for a server-only deployment with missive items!**)

**Important note! By default, this mod does not replace any vanilla commands!** The "Redirect Vanilla Commands" in the .toml config file defaults to false, because replacing vanilla commands can be quite invasive. Switch it to true if you want to replace /say, /me, /w, /msg, and /tell with functionality from this mod.

incobalt's Roleplay Chat was inspired by a streamed roleplay Spigot server experience that could not be replicated by existing Forge mods.

As a note: This mod can be deployed server side only and works with both vanilla and Forge clients when doing so. Commands and chat symbols work perfectly from the server. If you deploy server side only and want to use Missive items, you need to configure the mod to use server-side item checking.

incobalt's Roleplay Chat requires incobalt's Core API to be present wherever it is deployed.

As is the intention for all of incobalt's mods current or future, this mod is heavily commented to act as a learning resource.

incobalt's Roleplay Chat is released under the [Creative Commons Attribution-NonCommercial-ShareAlike 3.0 Unported](https://creativecommons.org/licenses/by-nc-sa/3.0/) license.
