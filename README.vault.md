# Vault - Abstraction Library for Bukkit Plugins

## Installing
Vault is not meant to be installed directly on Servers, but rather to be
included within existing plugins.  Plugins that support Vault will need no
additional work!


## Permissions
None!  Vault has no permission nodes itself.


## License
Copyright 2011 Morgan Humes

This work is licensed under the Creative Commons 
Attribution-NonCommercial-ShareAlike 3.0 Unported License. To view a copy of 
this license, visit http://creativecommons.org/licenses/by-nc-sa/3.0/ or send 
a letter to Creative Commons, 444 Castro Street, Suite 900, Mountain View, 
California, 94041, USA.


## Building
Vault itself should not be built, however it can easily be built by using any
JDK used along side your plugin!  A testing class may eventually be added, but
there is little to no need at this moment.


## Dependencies
Because Vault provides a bridge to other plugins, their binaries will be
required to build from.  To ease this, they have been included in the lib
folder and will be updated from time to time.


## Supported Plugins
Vault provides abstraction for the following categories and plugins:

    * Economy
          o BOSEconomy (http://forums.bukkit.org/threads/19025/)
          o iConomy 4 & 5 (http://forums.bukkit.org/threads/40/)

    * Permissions
          o Permissions 2 & 3 (http://forums.bukkit.org/threads/18430/)
          o Permissions Ex (http://forums.bukkit.org/threads/18140/)


## Implementing Economy
Implementation is simple, create an instance and load!  Makes it simple to
declare and initialize outside of your onEnable() method, and then load during.

Example:

```java
public void onEnable() {
    EconomyManager econManager = new EconomyManager(this);
    if(!econManager.load()) {
        // No valid economies were found, probably best to bail out
        // You should choose how to best handle this situation for your plugin!
        log.warning(String.format("[%s] No Economies were found! Disabling plugin.", getDescription().getName()));
	getPluginLoader().disablePlugin(this);
    }

    // Now that we have initialized our Economy, lets give Cerealk 1000!
    EconomyResponse response = econManager.depositPlayer("Cerealk", 1000);
    if(response.transactionSuccess()) {
        log.info(String.format("Cerealk was paid %s and now has %s!",
            econManager.format(response.amount), econManager.format(response.balance)));
    } else {
        log.warning("I could not pay Cerealk!!!" + response.errorMessage);
    }
}
```

## Implementing Permissions
Implementation is simple, create an instance and load!  Makes it simple to
declare and initialize outside of your onEnable() method, and then load during.

Example:

```java
public void onEnable() {
     PermissionManager permManager = new PermissionManager(this);
     if(!permManager.load()) {
        // No valid permissions were found, probably best to bail out
        // You should choose how to best handle this situation for your plugin!
        log.warning(String.format("[%s] No Permissions were found! Disabling plugin.", getDescription().getName()));
        getPluginLoader().disablePlugin(this);
    }

    // Now that we have initialized our Permissions, lets see if Cerealk has
    // example.test permission (node)!
    if(permManager.hasPermission("Cerealk", "example.test")) {
        // Indeed he does!
        log.info("Cerealk has example.test node!");
    } else {
        // Nope, he sucks
        log.info("Cerealk does NOT have example.test node!");
    }
}
```
