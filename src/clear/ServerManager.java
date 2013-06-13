package clear;
import lib.Format;
import lib.HashList;
import lib.HashListImpl;
import lib.ReloadConfigEvent;
import lib.Util;

import org.bukkit.Server;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitScheduler;

public class ServerManager implements Listener{
	private Main main;
	private Server server;
	private BukkitScheduler scheduler;
	private Format f;
	private String pn;
	
	private int checkInterval;
	private boolean broadcast;
	
	private AutoTpsTip autoTpsTip;
	
	private HashList<Level> levelList;
	
	class Level {
		private double threshold;
		private String status;
		private String show;
		public Level(double threshold, String status, String show) {
			this.threshold = threshold;
			this.status = status;
			this.show = show;
		}
		public double getThreshold() {
			return threshold;
		}
		public String getStatus() {
			return status;
		}
		public String getShow() {
			return show;
		}
	}
	
	public ServerManager(Main main) {
		this.main = main;
		this.server = main.getServer();
		this.scheduler = main.getServer().getScheduler();
		this.f = main.getF();
		this.pn = main.getPn();
		loadConfig(main.getCon().getConfig(pn));
		main.getServer().getPluginManager().registerEvents(this, main);
		
		autoTpsTip = new AutoTpsTip();
		scheduler.scheduleSyncDelayedTask(main, autoTpsTip, checkInterval*20);
	}
	
	@org.bukkit.event.EventHandler(priority=EventPriority.LOW)
	public void reloadConfig(ReloadConfigEvent e) {
		if (e.getCallPlugin().equals(pn)) {
			loadConfig(e.getConfig());
		}
	}
	
	/**
	 * 获取当前服务器状态,0表示十分好,1表示良好,2表示很差,3表示未知
	 */
	public int getServerStatus() {
		double tps = (int) Util.getTps();
		if (tps != -1) {
			for (Level level:levelList) {
				if (tps >= level.getThreshold()) {
					return levelList.indexOf(level);
				}
			}
		}
		return 3;
	}
	
	private void loadConfig(FileConfiguration config) {
		checkInterval = config.getInt("tps.checkInterval");
		broadcast = config.getBoolean("tps.broadcast");
		
		levelList = new HashListImpl<Level>();
		double threshold;
		String status;
		String show;
		for (String s:new String[]{"good","fine","bad","unknown"}) {
			threshold = config.getDouble("tps.levels."+s+".threshold", 0.0d);
			status = Util.convert(config.getString("tps.levels."+s+".status"));
			show = Util.convert(config.getString("tps.levels."+s+".show"));
			Level level = new Level(threshold, status, show);
			levelList.add(level);
		}
	}
	
	class AutoTpsTip implements Runnable {
		private int preStatus = 3;
		
		@Override
		public void run() {
			int nowStatus = getServerStatus();
			if (broadcast) {
				if (preStatus == 3) preStatus = nowStatus;
				else if (nowStatus != preStatus) {
					String pre = levelList.get(preStatus).getStatus();
					String now = levelList.get(nowStatus).getStatus();
					String tip = levelList.get(nowStatus).getShow();
					server.broadcastMessage(f.f(pn, "tpsChange", new Object[]{pre,now}));
					if (!tip.isEmpty()) server.broadcastMessage(tip);
				}
				preStatus = nowStatus;
			}
			scheduler.scheduleSyncDelayedTask(main, autoTpsTip, checkInterval*20);
		}
	}
}
