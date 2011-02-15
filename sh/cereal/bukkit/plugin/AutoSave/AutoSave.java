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
import java.util.InvalidPropertiesFormatException;
import java.util.Properties;
import java.util.logging.Logger;

import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginLoader;
import org.bukkit.plugin.java.JavaPlugin;

public class AutoSave extends JavaPlugin {
	protected final Logger log = Logger.getLogger("Minecraft");
	private static final String CONFIG_FILE_NAME = "plugins/AutoSave/config.properties";
	private PluginDescriptionFile pdfFile = this.getDescription();
	private AutoSaveThread saveThread = null;
	private AutoSaveConfig config = new AutoSaveConfig();

	public AutoSave(PluginLoader pluginLoader, Server instance, PluginDescriptionFile desc, File folder, File plugin, ClassLoader cLoader) {
		super(pluginLoader, instance, desc, folder, plugin, cLoader);
	}

	@Override
	public void onDisable() {
		// Stop thread
		saveThread.setRun(false);
		try {
			saveThread.join(5000);
		} catch (InterruptedException e) {
			log.info("Could not stop AutoSaveThread");
		}
		
		// Write Config File
		writeConfigFile();
		
		log.info(String.format("[%s] Version %s is disabled!", pdfFile.getName(), pdfFile.getVersion()));
	}

	@Override
	public void onEnable() {
		// Notify on logger load
		log.info(String.format("[%s] Version %s is enabled!", pdfFile.getName(), pdfFile.getVersion()));
		
		// Ensure our folder exists...
		File dir = new File("plugins/AutoSave");
		dir.mkdir();
		
		// Load configuration 
		loadConfigFile();
		
		// Start our thread
		saveThread = new AutoSaveThread(this, config);
		saveThread.start();
	}
	
	public void writeConfigFile() {
		// Write properties file
		log.info(String.format("[%s] Saving config file", pdfFile.getName()));
		Properties props = new Properties();
		props.setProperty("announce.message", config.announceMessage);
		props.setProperty("broadcast.enable", String.valueOf(config.broadcast));
		props.setProperty("interval", String.valueOf(config.interval));		
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
		config.interval = Integer.parseInt(props.getProperty("interval", String.valueOf(config.interval)));
		config.announceMessage = props.getProperty("announce.message", config.announceMessage);
		config.broadcast = Boolean.parseBoolean(props.getProperty("broadcast.enable", String.valueOf(config.broadcast)));
	}
	
    @Override
    public boolean onCommand(CommandSender sender, Command command, String commandLabel, String[] args) {
        String commandName = command.getName().toLowerCase();


        if (commandName.equals("save")) {
        	sender.sendMessage(String.format("%s%s", ChatColor.BLUE, "Save Complete"));
        	return save();
        }
        return false;
    }
    
    public boolean save() {
    	return CommandHelper.queueConsoleCommand(getServer(), "save-all");
    }
	
}
