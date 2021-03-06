# AutoSave - Automatic Saves for Bukkit, a Minecraft Server Mod
### http://forums.bukkit.org/threads/4316/

## Installing
Simple copy AutoSave.jar to your <bukkit-directory>/plugins/ and then start
Bukkit!  Most configuration of the plugin can be done via in-game commands.
For those adventurous or for changes requiring editing of the config file, it
is required to edit the config while the plugin is not running.  The best way
is to stop Bukkit, edit the file, and then start Bukkit.  It is not supported
to edit while the plugin is running!


## Permissions
autosave.save: Allows the ability to save at any time
autosave.toggle: Allows the ability to stop and start AutoSave
autosave.status: Allows the ability to see status of AutoSave
autosave.interval: Allows the ability to see and change interval setting
autosave.broadcast: Allows the ability to see and change broadcast setting
autosave.version: Allows the ability to see the version of AutoSave
autosave.debug: Allows the ability to change the debug setting
autosave.warn: Allows the ability to see and change the warning time setting
autosave.world: Allows the ability to view the world save list
autosave.world.add: Allows the ability to add to the world save list
autosave.world.rem: Allows the ability to remove from the world save list


## License
Copyright 2011 Morgan Humes

This work is licensed under the Creative Commons 
Attribution-NonCommercial-ShareAlike 3.0 Unported License. To view a copy of 
this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/ or send 
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, 
California, 94041, USA.


## Building
AutoSave provides an Ant build script (build.xml) which should be used when
building AutoSave.  Other methods may work, but are not supported or
documented.  To learn more about Ant, visit http://ant.apache.org.


## Dependencies
AutoSave depends upon Vault (https://github.com/MilkBowl/Vault) for an
abstraction layer for Permissions, read README.vault for additional info and
documentation.  To update, simply pull the latest source code from their git
repository.
