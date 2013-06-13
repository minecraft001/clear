package clear;

import java.io.File;
import java.util.regex.Pattern;

import lib.Config;
import lib.Eco;
import lib.Format;
import lib.HashList;
import lib.Lib;
import lib.Per;
import lib.ReloadConfigEvent;
import lib.Util;

import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.InvalidConfigurationException;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitScheduler;

public class Main extends JavaPlugin implements Listener{
	//basic
	private Lib lib;
	private Server server;
	private String pn;
	private PluginManager pm;
	private BukkitScheduler scheduler;
	/**
	 * 服务端所在的文件夹路径
	 */
	private String mainPath;
	/**
	 * 插件文件夹路径
	 */
	private String pluginPath;
	/**
	 * 插件数据文件夹路径
	 */
	private String dataFolder;
	private String pluginVersion;
	//need
	private Config con;
	private Format f;
	private Per per;
	private Eco eco;
	//other
	private ServerManager serverManager;

	private Clear clear;
	private RedStone redStone;
	private Crop crop;
	
	private String per_clear;
	
	//启动插件
	public void onEnable() {
		//basic
		initBasic();
		//need
		initNeed();
		//load config
		initConfig();
		loadConfig0(con.getConfig(pn));
		//other
		serverManager = new ServerManager(this);
		redStone = new RedStone(this);
		crop = new Crop(this);
		clear = new Clear(this);
		server.getPluginManager().registerEvents(this, this);
		
		//成功启动
		sendConsoleMessage(f.f(pn, "pluginEnabled", new Object[]{pn,pluginVersion}));
	}
	
	//停止插件
	public void onDisable() {
		//计时器
		if (scheduler != null) scheduler.cancelAllTasks();
		//显示插件成功停止信息
		sendConsoleMessage(f.f(pn, "pluginDisabled", new Object[]{pn,pluginVersion}));
	}

	@Override
	public boolean onCommand(CommandSender sender, Command cmd,String label, String[] args) {
		Player p = null;
		if (sender instanceof Player) p = (Player)sender;
		String cmdName = cmd.getName();
		int length = args.length;
		if (p != null && !Lib.checkSpeed(p)) return true;//命令速度检测
		try {
			if (cmdName.equalsIgnoreCase("clear")) {
				if (!(length == 1 && args[0].equalsIgnoreCase("?"))) {
					if (length == 1) {
						if (args[0].equalsIgnoreCase("reloadConfig") || args[0].equalsIgnoreCase("rc")) {
							reloadConfig(sender);
							return true;
						}else if (args[0].equalsIgnoreCase("info")) {
							if (sender instanceof Player && !per.has((Player)sender,per_clear)) {
								sender.sendMessage(f.f(pn, "noPer", per_clear));
								return true;
							}
							clear.info(sender);
							return true;
						}else if (args[0].equalsIgnoreCase("start")) {
							if (sender instanceof Player && !per.has((Player)sender,per_clear)) {
								sender.sendMessage(f.f(pn, "noPer", per_clear));
								return true;
							}
							clear.clear(true, -1);
							return true;
						}
					}else if (length == 2) {
						if (args[0].equalsIgnoreCase("start")) {
							if (sender instanceof Player && !per.has((Player)sender,per_clear)) {
								sender.sendMessage(f.f(pn, "noPer", per_clear));
								return true;
							}
							clear.clear(true, Integer.parseInt(args[1]));
							return true;
						}
					}
				}
				sender.sendMessage(f.f(pn, "cmdHelpHeader",get(10)));
				if (p == null || per.has(p, per_clear)) {
					sender.sendMessage(f.f(pn,"cmdHelpItem",new Object[]{get(15),get(20)}));
					sender.sendMessage(f.f(pn,"cmdHelpItem",new Object[]{get(1215),get(1220)}));
					sender.sendMessage(f.f(pn,"cmdHelpItem",new Object[]{get(1225),get(1230)}));
				}
			}
		} catch (NumberFormatException e) {
			sender.sendMessage(f.f(pn, "fail", get(190)));
		} 
		return true;
	}

	@EventHandler(priority=EventPriority.LOW)
	public void reloadConfig(ReloadConfigEvent e) {
		if (e.getCallPlugin().equals(pn)) {
			loadConfig0(e.getConfig());
		}
	}
	
	/**
	 * 表示将信息显示到控制台上
	 * @param msg 显示的信息
	 */
	private void sendConsoleMessage(String msg) {
		if (server.getConsoleSender() != null) server.getConsoleSender().sendMessage(msg);
		else server.getLogger().info(msg);
	}
	
	private void initBasic() {
		server = getServer();
		pn = getName();
		pm = server.getPluginManager();
		scheduler = server.getScheduler();
		mainPath = System.getProperty("user.dir");
		pluginPath = getFile().getParentFile().getAbsolutePath();
		dataFolder = pluginPath+File.separator+pn;
		pluginVersion = Util.getPluginVersion(getFile());
	}
	
	private void initNeed() {
		if (!pm.getPlugin("lib").isEnabled()) pm.enablePlugin(pm.getPlugin("lib"));
		lib = (Lib)(pm.getPlugin("lib"));
		con = lib.getCon();
		f = lib.getFormat();
		per = lib.getPer();
		eco = lib.getEco();
	}
	
	private void initConfig() {
		HashList<Pattern> filter = con.getDefaultFilter();
		con.register(new File(pluginPath+File.separator+pn+".jar"), dataFolder, filter, pn);
		loadConfig(null);
	}
	
	private void reloadConfig(CommandSender sender) {
		Player p = null;
		if (sender instanceof Player) p = (Player)sender;
		if (p != null && !per.has(p, per_clear)) p.sendMessage(f.f(pn, "noPer", per_clear));
		else {
			if (loadConfig(sender)) sender.sendMessage(f.f(pn, "success", get(25)));
			else sender.sendMessage(f.f(pn, "fail", get(30)));
		}
	}
	
	/**
	 * 重新读取配置文件
	 * @param sender 可为null,用来指定异常信息的接收者
	 */
	private boolean loadConfig(CommandSender sender) {
		try {
			return con.loadConfig(pn);
		} catch (InvalidConfigurationException e) {
			if (sender == null) sender = server.getConsoleSender();
			if (sender != null) sender.sendMessage(e.getMessage());
			else server.getLogger().info(e.getMessage());
			return false;
		}
	}
	
	/**
	 * 从配置文件中读取数据
	 * @param config
	 */
	private void loadConfig0(FileConfiguration config) {
		per_clear = config.getString("per_clear");
	}

	private String get(int id) {
		return f.get(pn, id);
	}

	public String getPn() {
		return pn;
	}

	public Config getCon() {
		return con;
	}

	public Format getF() {
		return f;
	}

	public Per getPer() {
		return per;
	}
	
	public Eco getEco() {
		return eco;
	}

	public ServerManager getServerManager() {
		return serverManager;
	}

	public RedStone getRedStone() {
		return redStone;
	}

	public Crop getCrop() {
		return crop;
	}

	public String getMainPath() {
		return mainPath;
	}
}
