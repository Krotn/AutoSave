/**
 * Copyright 2011 Morgan Humes
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 */

package sh.cereal.bukkit.plugin.AutoSave;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.HashMap;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Properties;
import java.util.UUID;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.Event.Priority;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

public class AutoSave extends JavaPlugin {
	protected final Logger log = Logger.getLogger("Minecraft");
	public static PermissionHandler PERMISSIONS = null;
	private static final String CONFIG_FILE_NAME = "plugins/AutoSave/config.properties";
	
	private PluginDescriptionFile pdfFile = null;
	private AutoSaveThread saveThread = null;
	private ReportThread reportThread = null;
	private AutoSaveConfig config = new AutoSaveConfig();
	private AutoSavePlayerListener playerListener = null;
	protected int numPlayers = 0;
	
	private static HashMap<String, BukkitVersion> recommendedBuilds = new HashMap<String, BukkitVersion>();
	static {
		recommendedBuilds.put("git-Bukkit-0.0.0-544-g6c6c30a-b556jnks (MC: 1.3)", new BukkitVersion("git-Bukkit-0.0.0-544-g6c6c30a-b556jnks (MC: 1.3)", true, 556, true));
		recommendedBuilds.put("git-Bukkit-0.0.0-516-gdf87bb3-b531jnks (MC: 1.3)", new BukkitVersion("git-Bukkit-0.0.0-516-gdf87bb3-b531jnks (MC: 1.3)", true, 531, true));
		recommendedBuilds.put("git-Bukkit-0.0.0-512-g63bc855-b527jnks (MC: 1.3)", new BukkitVersion("git-Bukkit-0.0.0-512-g63bc855-b527jnks (MC: 1.3)", true, 527, true));
		recommendedBuilds.put("git-Bukkit-0.0.0-511-g5fae618-b526jnks (MC: 1.3)", new BukkitVersion("git-Bukkit-0.0.0-511-g5fae618-b526jnks (MC: 1.3)", true, 526, true));
		recommendedBuilds.put("git-Bukkit-0.0.0-506-g4e9d448-b522jnks (MC: 1.3)", new BukkitVersion("git-Bukkit-0.0.0-506-g4e9d448-b522jnks (MC: 1.3)", true, 522, true));
		recommendedBuilds.put("git-Bukkit-0.0.0-493-g8b5496e-b493jnks (MC: 1.3)", new BukkitVersion("git-Bukkit-0.0.0-493-g8b5496e-b493jnks (MC: 1.3)", true, 493, true));
	}

	@Override
	public void onDisable() {
		// Stop thread
		stopSaveThread();
		
		// Write Config File
		writeConfigFile();
		
		log.info(String.format("[%s] Version %s is disabled!", pdfFile.getName(), pdfFile.getVersion()));
	}

	@Override
	public void onEnable() {		
		// Get Plugin Info
		pdfFile = this.getDescription();
		
		// Check Server Version String
		if(recommendedBuilds.containsKey(getServer().getVersion())) {
			// Known Build
			BukkitVersion ver = recommendedBuilds.get(getServer().getVersion());
			log.info(String.format("[%s] Server Version is %s%d",  pdfFile.getName(), ver.recommendedBuild ? "Recommended Build " : "Build ", ver.buildNumber, ver.supported ? "is supported" : "is NOT supported"));
		} else {
			// Unknown Build -- Warn
			log.warning(String.format("[%s] UNKNOWN SERVER VERSION: It has NOT been tested and %s MAY NOT function properly: %s",  pdfFile.getName(), pdfFile.getName(), getServer().getVersion()));
		}
		
		// Ensure our folder exists...
		File dir = new File("plugins/AutoSave");
		dir.mkdir();
		
		// Load configuration 
		loadConfigFile();
		
		// Test the waters, make sure we are running a build that has the methods we NEED
		try {
			// Check Server
			Class<?> s = Class.forName("org.bukkit.Server");
			s.getMethod("savePlayers", new Class[] {});
			
			// Check World
			Class<?> w = Class.forName("org.bukkit.World");
			w.getMethod("save", new Class[] {});
		} catch(ClassNotFoundException e) {
			// Do error stuff
			log.severe(String.format("[%s] ERROR: Server version is incompatible with %s!", pdfFile.getName(), pdfFile.getName()));
			log.severe(String.format("[%s] Could not find class \"%s\", disabling!", pdfFile.getName(), e.getMessage()));
			
			// Clean up
			getPluginLoader().disablePlugin(this);
			return;			
		} catch(NoSuchMethodException e) {
			// Do error stuff
			log.severe(String.format("[%s] ERROR: Server version is incompatible with %s!", pdfFile.getName(), pdfFile.getName()));
			log.severe(String.format("[%s] Could not find method \"%s\", disabling!", pdfFile.getName(), e.getMessage()));
			
			// Clean up
			getPluginLoader().disablePlugin(this);
			return;			
		}
		
		// Register Events -- WEEE
		playerListener = new AutoSavePlayerListener(this);
		PluginManager pm = getServer().getPluginManager();
		pm.registerEvent(Event.Type.PLAYER_JOIN, playerListener, Priority.Monitor, this);
		pm.registerEvent(Event.Type.PLAYER_QUIT, playerListener, Priority.Monitor, this);
		
		// Make an HTTP request for anonymous statistic collection
		reportThread = new ReportThread(this, config);
		reportThread.start();
		
		// Notify on logger load
		log.info(String.format("[%s] Version %s is enabled!", pdfFile.getName(), pdfFile.getVersion()));		
	}
	
	public void obtainPermissions() {
		// Test if Permissions exists
		if (config.varPermissions) {
			Plugin test = this.getServer().getPluginManager().getPlugin("Permissions");
			if (PERMISSIONS == null) {
				if (test != null) {
					PERMISSIONS = ((Permissions) test).getHandler();
					log.info(String.format("[%s] %s", pdfFile.getName(), "Permission system acquired."));
				} else {
					log.info(String.format("[%s] %s", pdfFile.getName(), "Permission system not enabled. Disabling plugin."));
					this.getServer().getPluginManager().disablePlugin(this);
					return;
				}
			}
		}
	}
	
	public void writeConfigFile() {
		// Log config
		logObject(config);
		
		// Write properties file
		log.info(String.format("[%s] Saving config file", pdfFile.getName()));
		Properties props = new Properties();
		
		// Messages
		props.setProperty("message.broadcastpre", config.messageBroadcastPre);
		props.setProperty("message.broadcastpost", config.messageBroadcastPost);
		props.setProperty("message.insufficentpermissions", config.messageInsufficientPermissions);
		props.setProperty("message.saveworlds", config.messageSaveWorlds);
		props.setProperty("message.saveplayers", config.messageSavePlayers);
		props.setProperty("message.warning", config.messageWarning);
		//props.setProperty("message.starting", config.messageStarting);
		//props.setProperty("message.statusfail", config.messageStatusFail);
		//props.setProperty("message.statusoff", config.messageStatusOff);
		//props.setProperty("message.statussuccess", config.messageStatusSuccess);
		//props.setProperty("message.stopping", config.messageStopping);
		//props.setProperty("message.intervalnotanumber", config.messageIntervalNotANnumber);
		//props.setProperty("message.intervalchangesuccess", config.messageIntervalChangeSuccess);
		//props.setProperty("message.intervallookup", config.messageIntervalLookup);
		//props.setProperty("message.broadcastchangesuccess", config.messageBroadcastChangeSuccess);
		//props.setProperty("message.broadcastlookup", config.messageBroadcastLookup);
		//props.setProperty("message.broadcastnotvalid", config.messageBroadcastNotValid);
		//props.setProperty("message.worldchangesuccess", config.messageWorldChangeSuccess);
		//props.setProperty("message.worldlookup", config.messageWorldLookup);
		//props.setProperty("message.version", config.messageVersion);
		//props.setProperty("message.warnchangesuccess", config.messageWarnChangeSuccess);
		//props.setProperty("message.warnlookup", config.messageWarnLookup);
		//props.setProperty("message.warnnotanumber", config.messageWarnNotANnumber);
		//props.setProperty("message.reportlookup", config.messageReportLookup);
		//props.setProperty("message.reportnotvalid", config.messageReportNotValid);
		//props.setProperty("message.reportchangesuccess", config.messageReportChangeSuccess);
		
		// Values
		props.setProperty("value.on", config.valueOn);
		props.setProperty("value.off", config.valueOff);
		
		// Variables
		props.setProperty("var.debug", String.valueOf(config.varDebug));
		props.setProperty("var.interval", String.valueOf(config.varInterval));
		props.setProperty("var.permissions", String.valueOf(config.varPermissions));
		props.setProperty("var.broadcast", String.valueOf(config.varBroadcast));
		if(config.varWorlds == null) {
			props.setProperty("var.worlds", "*");
		} else {
			props.setProperty("var.worlds", Generic.join(",", config.varWorlds));
		}
		props.setProperty("var.warntime", String.valueOf(config.varWarnTime));
		props.setProperty("var.uuid", config.varUuid.toString());
		props.setProperty("var.report", String.valueOf(config.varReport));
		
		try {
			props.storeToXML(new FileOutputStream(CONFIG_FILE_NAME), null);
		} catch (FileNotFoundException e) {
			// Shouldn't happen...report and continue
			log.info(String.format("[%s] FileNotFoundException while saving config file", pdfFile.getName()));
		} catch (IOException e) {
			// Report and continue
			log.info(String.format("[%s] IOException while saving config file", pdfFile.getName()));
		}		
	}
	
	public void loadConfigFile() {
		log.info(String.format("[%s] Loading config file", pdfFile.getName()));
		File confFile = new File(CONFIG_FILE_NAME);
		if(!confFile.exists()) {
			writeConfigFile();
		}
		
		Properties props = new Properties();
		try {
			props.loadFromXML(new FileInputStream(confFile));
		} catch(FileNotFoundException e) {
			// Hmmm, shouldnt happen...
			log.info(String.format("[%s] FileNotFoundException while loading config file", pdfFile.getName()));
		} catch(InvalidPropertiesFormatException e) {
			// Report and continue
			log.info(String.format("[%s] InvalidPropertieFormatException while loading config file", pdfFile.getName()));
		} catch(IOException e) {
			// Report and continue
			log.info(String.format("[%s] IOException while loading config file", pdfFile.getName()));
		}
		
		/**
		 * Attempt to load Version 1.0.3 and before values if present, otherwise load 1.1 version
		 */
		
		// Messages
		config.messageBroadcastPre = props.getProperty("message.broadcastpre", config.messageBroadcastPre);
		config.messageBroadcastPost = props.getProperty("message.broadcastpost", config.messageBroadcastPost);
		config.messageInsufficientPermissions = props.getProperty("message.insufficentpermissions", config.messageInsufficientPermissions);
		config.messageSaveWorlds = props.getProperty("message.saveworlds", config.messageSaveWorlds);
		config.messageSavePlayers = props.getProperty("message.saveplayers", config.messageSavePlayers);
		config.messageDebugChangeSuccess = props.getProperty("message.debugchangesuccess", config.messageDebugChangeSuccess);
		config.messageDebugLookup = props.getProperty("message.debuglookup", config.messageDebugLookup);
		config.messageDebugNotValid = props.getProperty("message.debugnotvalue", config.messageDebugNotValid);
		config.messageWarning = props.getProperty("message.warning", config.messageWarning);		
		//config.messageStarting = props.getProperty("message.starting", config.messageStarting);
		//config.messageStatusFail = props.getProperty("message.statusfail", config.messageStatusFail);
		//config.messageStatusOff = props.getProperty("cmessage.statusoff", config.messageStatusOff);
		//config.messageStatusSuccess = props.getProperty("message.statussuccess", config.messageStatusSuccess);
		//config.messageStopping = props.getProperty("message.stopping", config.messageStopping);
		//config.messageIntervalNotANnumber = props.getProperty("message.intervalnotanumber", config.messageIntervalNotANnumber);
		//config.messageIntervalChangeSuccess = props.getProperty("message.intervalchangesuccess", config.messageIntervalChangeSuccess);
		//config.messageIntervalLookup = props.getProperty("message.intervallookup", config.messageIntervalLookup);
		//config.messageBroadcastChangeSuccess = props.getProperty("message.broadcastchangesuccess", config.messageBroadcastChangeSuccess);
		//config.messageBroadcastLookup = props.getProperty("message.broadcastlookup", config.messageBroadcastLookup);
		//config.messageBroadcastNotValid = props.getProperty("message.broadcastnotvalid", config.messageBroadcastNotValid);
		//config.messageVersion = props.getProperty("message.version", config.messageVersion);
		//config.messageWorldChangeSuccess = props.getProperty("message.worldchangesuccess", config.messageWorldChangeSuccess);
		//config.messageWorldLookup = props.getProperty("message.worldlookup", config.messageWorldLookup);
		//config.messageWarnChangeSuccess = props.getProperty("message.warnchangesuccess", config.messageWarnChangeSuccess);
		//config.messageWarnLookup = props.getProperty("message.warnlookup", config.messageWarnLookup);
		//config.messageWarnNotANnumber = props.getProperty("message.warnnotanumber", config.messageWarnNotANnumber);
		//config.messageReportLookup = props.getProperty("message.reportlookup", config.messageReportLookup);
		//config.messageReportNotValid = props.getProperty("message.reportnotvalid", config.messageReportNotValid);
		//config.messageReportChangeSuccess = props.getProperty("message.reportchangesuccess", config.messageReportChangeSuccess);
		
		// Values
		if(props.containsKey("command.on")) {
			config.valueOn = props.getProperty("command.on", config.valueOn);
		} else {
			config.valueOn = props.getProperty("value.on", config.valueOn);
		}
		if(props.containsKey("command.off")) {
			config.valueOff = props.getProperty("command.off", config.valueOff);
		} else {
			config.valueOff = props.getProperty("value.off", config.valueOff);
		}
		
		// Variables
		if(props.containsKey("debug")) {
			config.varDebug = Boolean.parseBoolean(props.getProperty("debug", String.valueOf(config.varDebug)));
		} else {
			config.varDebug = Boolean.parseBoolean(props.getProperty("var.debug", String.valueOf(config.varDebug)));
		}
		if(props.containsKey("broadcast.enable")) {
			config.varBroadcast = Boolean.parseBoolean(props.getProperty("broadcast.enable", String.valueOf(config.varBroadcast)));
		} else {
			config.varBroadcast = Boolean.parseBoolean(props.getProperty("var.broadcast", String.valueOf(config.varBroadcast)));
		}
		if(props.containsKey("permissions")) {
			config.varPermissions = Boolean.parseBoolean(props.getProperty("permissions", String.valueOf(config.varPermissions)));
		} else {
			config.varPermissions = Boolean.parseBoolean(props.getProperty("var.permissions", String.valueOf(config.varPermissions)));
		}
		if(props.containsKey("interval")) {
			config.varInterval = Integer.parseInt(props.getProperty("interval", String.valueOf(config.varInterval)));
		} else {
			config.varInterval = Integer.parseInt(props.getProperty("var.interval", String.valueOf(config.varInterval)));
		}
		
		String tmpWorlds = props.getProperty("var.worlds", "*");
		config.varWorlds = new ArrayList<String>(Arrays.asList(tmpWorlds.split(",")));
		config.varWarnTime = Integer.parseInt(props.getProperty("var.warntime", String.valueOf(config.varWarnTime)));
		String strUuid = props.getProperty("var.uuid", "");
		try {
			config.varUuid = UUID.fromString(strUuid);
		} catch(IllegalArgumentException e) {
			config.varUuid = UUID.randomUUID();
		}
		config.varReport = Boolean.parseBoolean(props.getProperty("var.report", String.valueOf(config.varReport)));
		
		logObject(config);
	}
	
	public void logObject(Object o) {
		String className = o.getClass().getName();
		// Log the Object
		for(Field field : o.getClass().getDeclaredFields()) {		
			// Get our data
			String name = field.getName();
			String value = "";
			try {
				value = field.get(config).toString();
			} catch (IllegalAccessException e) {
				continue;
			}
			
			// Log it
			log.info(String.format("[%s] %s=%s", className, name, value));
		}		
	}
	
	public boolean checkPermissions(String permission, Player player) {
		if(player == null) {
			return true;
		} else if(config.varPermissions) {
			// Permissions -- check it!
			obtainPermissions();
			return PERMISSIONS.has(player, permission);
		} else {
			// No permissions, default to Op status
			// All permissions pass or fail on this
			return player.isOp();
		}
	}
	
    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {       
    	String commandName = command.getName().toLowerCase();
    	Player player = null;
    	if((sender instanceof Player)) {
    		player = (Player) sender;
        }

        if (commandName.equals("autosave")) {
        	if(args.length == 0) {
        		// Check Permissions
				if (!checkPermissions("autosave.save", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
				
				// Perform save
				// Players
				savePlayers();
				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageSavePlayers));
				// Worlds
				int worlds = saveWorlds();
				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageSaveWorlds.replaceAll("\\{%NUMSAVED%\\}", String.valueOf(worlds))));
				if(worlds > 0) {
					return true;
				} else {
					return false;
				}
        	} else if(args.length == 1 && args[0].equalsIgnoreCase("help")) {
        		// Shows help for allowed commands
    			// /save
				if (checkPermissions("autosave.save", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save - Saves all players & worlds"));
				}
				
				// /save help
				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save help - Displays this dialogue"));
				
				// /save toggle
				if (checkPermissions("autosave.toggle", player)) { 
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save toggle - Toggles the AutoSave system"));
				}
				
				// /save status
				if (checkPermissions("autosave.status", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save status - Reports thread status and last run time"));
				}
				
				// /save interval
				if (checkPermissions("autosave.interval", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save interval [value] - Sets & retrieves the save interval"));
				}
				
				// /save broadcast
				if (checkPermissions("autosave.broadcast", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save broadcast [on|off] - Sets & retrieves the broadcast value"));
				}
				
				// /save report
				if (checkPermissions("autosave.report", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save report [on|off] - Sets & retrieves the report value"));
				}				
				
				// /save warn
				if (checkPermissions("autosave.warn", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save warn [value] - Sets & retrieves the warn time in seconds"));
				}
				
				// /save version
				if (checkPermissions("autosave.version", player)) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "/save version - Prints the version of AutoSave"));
				}
        	} else if(args.length == 1 && args[0].equalsIgnoreCase("toggle")) {
				// Check Permissions
				if (!checkPermissions("autosave.toggle", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
				
				// Start thread
				if(saveThread == null) {
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageStarting));
					return startSaveThread();
				} else { // Stop thread
					sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageStopping));
					return stopSaveThread();
				}       		
        	} else if(args.length == 1 && args[0].equalsIgnoreCase("status")) {
				// Check Permissions
				if (!checkPermissions("autosave.status", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		// Get Thread Status
        		if(saveThread == null) {
        			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageStatusOff));
        		} else {
            		if(saveThread.isAlive()) {
            			Date lastSaved = saveThread.getLastSave();
            			if(lastSaved == null) {
            				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageStatusNotRun));
            				return true;
            			} else {
            				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageStatusSuccess.replaceAll("\\{%DATE%\\}", lastSaved.toString())));
            				return true;
            			}
            		} else {
            			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageStatusFail));
            			return true;
            		}        			
        		}
        	} else if(args.length >= 1 && args[0].equalsIgnoreCase("interval")) {
				// Check Permissions
				if (!checkPermissions("autosave.interval", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		if(args.length == 1) {
        			// Report interval!
        			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageIntervalLookup.replaceAll("\\{%INTERVAL%\\}", String.valueOf(config.varInterval))));
        			return true;
        		} else if(args.length == 2) {
        			// Change interval!
        			try {
        				int newInterval = Integer.parseInt(args[1]);
        				config.varInterval = newInterval;
        				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageIntervalChangeSuccess.replaceAll("\\{%INTERVAL%\\}", String.valueOf(config.varInterval))));
        				return true;
        			} catch(NumberFormatException e) {
        				sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageIntervalNotANnumber));
        				return false;
        			}
        		}
        	} else if(args.length >= 1 && args[0].equalsIgnoreCase("warn")) {
				// Check Permissions
				if (!checkPermissions("autosave.warn", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		if(args.length == 1) {
        			// Report interval!
        			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageWarnLookup.replaceAll("\\{%WARN%\\}", String.valueOf(config.varWarnTime))));
        			return true;
        		} else if(args.length == 2) {
        			// Change interval!
        			try {
        				int newWarnTime = Integer.parseInt(args[1]);
        				config.varWarnTime = newWarnTime;
        				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageWarnChangeSuccess.replaceAll("\\{%WARN%\\}", String.valueOf(config.varWarnTime))));
        				return true;
        			} catch(NumberFormatException e) {
        				sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageWarnNotANnumber));
        				return false;
        			}
        		}
        	} else if(args.length >= 1 && args[0].equalsIgnoreCase("broadcast")) {
				// Check Permissions
				if (!checkPermissions("autosave.broadcast", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		if(args.length == 1) {
        			// Report broadcast status!
        			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageBroadcastLookup.replaceAll("\\{%BROADCAST%\\}", String.valueOf(config.varBroadcast ? config.valueOn : config.valueOff))));
        			return true;
        		} else if(args.length == 2) {
        				// Change broadcast status!
        				boolean newSetting = false;
        				if(args[1].equalsIgnoreCase(config.valueOn)) {
        					newSetting = true;
        				} else if(args[1].equalsIgnoreCase(config.valueOff)) {
        					newSetting = false;
        				} else {
        					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageBroadcastNotValid.replaceAll("\\{%ON%\\}", config.valueOn).replaceAll("\\{%OFF%\\}", config.valueOff)));
        					return false;
        				}
        				config.varBroadcast = newSetting;
        				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageBroadcastChangeSuccess.replaceAll("\\{%BROADCAST%\\}", String.valueOf(config.varBroadcast ? config.valueOn : config.valueOff))));
        				return true;
        		}
        	} else if(args.length >= 1 && args[0].equalsIgnoreCase("debug")) {
				// Check Permissions
				if (!checkPermissions("autosave.debug", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		if(args.length == 1) {
        			// Report debug status!
        			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageDebugLookup.replaceAll("\\{%DEBUG%\\}", String.valueOf(config.varDebug ? config.valueOn : config.valueOff))));
        			return true;
        		} else if(args.length == 2) {
    				// Change debug status!
    				boolean newSetting = false;
    				if(args[1].equalsIgnoreCase(config.valueOn)) {
    					newSetting = true;
    				} else if(args[1].equalsIgnoreCase(config.valueOff)) {
    					newSetting = false;
    				} else {
    					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageDebugNotValid.replaceAll("\\{%ON%\\}", config.valueOn).replaceAll("\\{%OFF%\\}", config.valueOff)));
    					return false;
    				}
    				config.varDebug = newSetting;
    				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageDebugChangeSuccess.replaceAll("\\{%DEBUG%\\}", String.valueOf(config.varDebug ? config.valueOn : config.valueOff))));
    				return true;        			
        		}
        	} else if(args.length >= 1 && args[0].equalsIgnoreCase("report")) {
				// Check Permissions
				if (!checkPermissions("autosave.report", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		if(args.length == 1) {
        			// Report report status!
        			sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageReportLookup.replaceAll("\\{%REPORT%\\}", String.valueOf(config.varReport ? config.valueOn : config.valueOff))));
        			return true;
        		} else if(args.length == 2) {
    				// Change report status!
    				boolean newSetting = false;
    				if(args[1].equalsIgnoreCase(config.valueOn)) {
    					if(reportThread == null || !reportThread.isAlive()) {
    						reportThread = new ReportThread(this, config);
    						reportThread.start();
    					}
    					newSetting = true;
    				} else if(args[1].equalsIgnoreCase(config.valueOff)) {
    					if(reportThread != null) {
    						reportThread.setRun(false);
    					}
    					newSetting = false;
    				} else {
    					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageReportNotValid.replaceAll("\\{%ON%\\}", config.valueOn).replaceAll("\\{%OFF%\\}", config.valueOff)));
    					return false;
    				}
    				config.varReport = newSetting;
    				sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageReportChangeSuccess.replaceAll("\\{%REPORT%\\}", String.valueOf(config.varReport ? config.valueOn : config.valueOff))));
    				return true;        			
        		}        		
        	} else if(args.length == 2 && args[0].equalsIgnoreCase("addworld")) {
				// Check Permissions
				if (!checkPermissions("autosave.world.add", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		config.varWorlds.add(args[1]);
        		sender.sendMessage(config.messageWorldChangeSuccess.replaceAll("\\{%WORLDS%\\}", Generic.join(", ", config.varWorlds)));
        		
        		return true;
        	} else if(args.length == 2 && args[0].equalsIgnoreCase("remworld")) {
				// Check Permissions
				if (!checkPermissions("autosave.world.rem", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		config.varWorlds.remove(args[1]);
        		sender.sendMessage(config.messageWorldChangeSuccess.replaceAll("\\{%WORLDS%\\}", Generic.join(", ", config.varWorlds)));
        		
        		return true;
        	} else if(args.length == 1 && args[0].equalsIgnoreCase("world")) {
				// Check Permissions
				if (!checkPermissions("autosave.world", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		sender.sendMessage(config.messageWorldLookup.replaceAll("\\{%WORLDS%\\}", Generic.join(", ", config.varWorlds)));
        		
        		return true;
        	} else if(args.length == 1 && args[0].equalsIgnoreCase("version")) {
				// Check Permissions
				if (!checkPermissions("autosave.version", player)) {
					// Permission check failed!
					sender.sendMessage(String.format("%s%s", ChatColor.RED, config.messageInsufficientPermissions));
					return false;
				}
        		
        		sender.sendMessage(String.format("%s%s", ChatColor.BLUE, config.messageVersion.replaceAll("\\{%VERSION%\\}", pdfFile.getVersion())));
        		return true;
        	}
        } else {
        	sender.sendMessage(String.format("Unknown command \"%s\" handled by %s", commandName, pdfFile.getName()));
        }
        return false;
    }
    
    protected boolean startSaveThread() {
    	if(saveThread == null || !saveThread.isAlive()) {
    		saveThread = new AutoSaveThread(this, config);
			saveThread.start();
    	}
    	return true;
    }
    
    protected boolean stopSaveThread() {
		if (saveThread != null) {
			saveThread.setRun(false);
			try {
				saveThread.join(5000);
				saveThread = null;
				return true;
			} catch (InterruptedException e) {
				log.info(String.format("[%s] Could not stop AutoSaveThread", pdfFile.getName()));
				return false;
			}
		} else {
			return true;
		}
    }
    
    public void savePlayers() {
    	// Save the players
    	if(config.varDebug) {
    		log.info(String.format("[%s] Saving players", pdfFile.getName()));
    	}
    	this.getServer().savePlayers();
    }
    
    public int saveWorlds(List<String> worldNames) {
    	// Save our worlds...
    	int i = 0;
    	List<World> worlds = this.getServer().getWorlds();
    	for(World world : worlds) {
    		if(worldNames.contains(world.getName())) {
    			if(config.varDebug) {
    				log.info(String.format("[%s] Saving the world: %s", pdfFile.getName(), world.getName()));
    			}
    			world.save();
    			i++;
    		}
    	}    	
    	return i;
    }
    
    public int saveWorlds() {
    	// Save our worlds
    	int i = 0;
    	List<World> worlds = this.getServer().getWorlds();
    	for(World world : worlds) {
    		if(config.varDebug) {
    			log.info(String.format("[%s] Saving the world: %s", pdfFile.getName(), world.getName()));
    		}
    		world.save();
    		i++;
    	}
    	return i;
    	//return CommandHelper.queueConsoleCommand(getServer(), "save-all");
    }
	
}
