package clear;
import java.util.HashMap;
import lib.Format;
import lib.HashList;
import lib.HashListImpl;
import lib.ReloadConfigEvent;

import org.bukkit.Location;
import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockRedstoneEvent;

public class RedStone implements Listener{
	private static final int CHECK_INTERVAL = 200;//更新服务器状态间隔
	private Main main;
	private Server server;
	private Format f;
	private String pn;
	private ServerManager serverManager;

	private HashList<String> ignoreWorlds;
	private int checkInterval;
	private int gridSize;
	private boolean drop;
	private HashList<Integer> removeBlocks;
	private boolean reset;
	private int goodTipTimes;
	private int goodRemoveTimes;
	private int fineTipTimes;
	private int fineRemoveTimes;
	private int badTipTimes;
	private int badRemoveTimes;
	private int unknownTipTimes;
	private int unknownRemoveTimes;
	private boolean ingameTip,consoleTip;
	private int ingameTipMinInterval, consoleTipMinInterval;
	
	private int taskId;
	private ReSet reSet;
	private HashMap<String, HashMap<Integer,HashMap<Integer,Integer>>> countHash;//world,x,z,count
	private int nowTipTimes,nowRemoveTimes;
	private long lastInGameTip, lastConsoleTip;
	
	public RedStone(Main main) {
		this.main = main;
		this.server = main.getServer();
		this.f = main.getF();
		this.pn = main.getPn();
		this.serverManager = main.getServerManager();

		loadConfig(main.getCon().getConfig(pn));
		main.getServer().getPluginManager().registerEvents(this, main);
		check();
		main.getServer().getScheduler().scheduleSyncRepeatingTask(main, new Runnable(){
			@Override
			public void run() {
				check();
			}
		}, CHECK_INTERVAL, CHECK_INTERVAL);
		reSet = new ReSet();
		reSet();
	}

	@org.bukkit.event.EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=false)
	public void blockRedstone(BlockRedstoneEvent e) {
		redStone(e.getBlock().getLocation());
	}

	@org.bukkit.event.EventHandler(priority=EventPriority.LOW)
	public void reloadConfig(ReloadConfigEvent e) {
		if (e.getCallPlugin().equals(pn)) {
			loadConfig(e.getConfig());
			//重置计时器
			main.getServer().getScheduler().cancelTask(taskId);
			reSet();
		}
	}
	
	/**
	 * 发生红石事件时调用
	 * @param l 位置
	 */
	private void redStone(Location l) {
		String worldName = l.getWorld().getName();
		if (ignoreWorlds.has(worldName)) return;
		
		int x = l.getBlockX()/gridSize;
		int z = l.getBlockZ()/gridSize;
		if (!countHash.get(worldName).containsKey(x)) countHash.get(worldName).put( x, new HashMap<Integer, Integer>());
		if (!countHash.get(worldName).get(x).containsKey(z)) countHash.get(worldName).get(x).put(z, 0);
		countHash.get(worldName).get(x).put(z, countHash.get(worldName).get(x).get(z)+1);
		String result;
		if (countHash.get(worldName).get(x).get(z) == nowTipTimes) {//频率异常
			result = f.f(pn, "redStoneTip", new Object[]{l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()});
		}else if (countHash.get(worldName).get(x).get(z) >= nowRemoveTimes) {//频率超标
			if (reset) countHash.get(worldName).get(x).put(z, 0);//检测重置计数器
			
			if (removeBlocks.has(l.getBlock().getTypeId())) {
				String handle;
				if (drop) {
					l.getBlock().breakNaturally();
					handle = get(1886);
				}else {
					l.getBlock().setTypeId(0);
					handle = get(1885);
				}
				result = f.f(pn, "redStoneTip3", new Object[]{l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), handle});
			}else {
				result = f.f(pn, "redStoneTip2", new Object[]{l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ()});
			}
		}else return;
		long now = System.currentTimeMillis();
		if (ingameTip && now-lastInGameTip > ingameTipMinInterval) {
			server.broadcastMessage(result);
			lastInGameTip = now;
		}
		if (consoleTip && now-lastConsoleTip > consoleTipMinInterval) {
			server.getConsoleSender().sendMessage(result);
			lastConsoleTip = now;
		}
	}

	/**
	 * 检测更新服务器状态
	 */
	private void check() {
		switch (serverManager.getServerStatus()) {
		case 0://good
			nowTipTimes = goodTipTimes;
			nowRemoveTimes = goodRemoveTimes;
			break;
		case 1://fine
			nowTipTimes = fineTipTimes;
			nowRemoveTimes = fineRemoveTimes;
			break;
		case 2://bad
			nowTipTimes = badTipTimes;
			nowRemoveTimes = badRemoveTimes;
			break;
		case 3://unknown
			nowTipTimes = unknownTipTimes;
			nowRemoveTimes = unknownRemoveTimes;
			break;
		}
	}

	private void loadConfig(FileConfiguration config) {
		ignoreWorlds = new HashListImpl<String>();
		for (String s:config.getStringList("redstone.ignoreWorlds")) ignoreWorlds.add(s);
		checkInterval = config.getInt("redstone.checkInterval");
		gridSize = config.getInt("redstone.gridSize");
		drop = config.getBoolean("redstone.drop");
		removeBlocks = new HashListImpl<Integer>();
		for (int i:config.getIntegerList("redstone.removeBlocks")) removeBlocks.add(i);
		reset = config.getBoolean("redstone.reset");
		goodTipTimes = config.getInt("redstone.times.good.tipTimes")*checkInterval;
		goodRemoveTimes = config.getInt("redstone.times.good.removeTimes")*checkInterval;
		fineTipTimes = config.getInt("redstone.times.fine.tipTimes")*checkInterval;
		fineRemoveTimes = config.getInt("redstone.times.fine.removeTimes")*checkInterval;
		badTipTimes = config.getInt("redstone.times.bad.tipTimes")*checkInterval;
		badRemoveTimes = config.getInt("redstone.times.bad.removeTimes")*checkInterval;
		unknownTipTimes = config.getInt("redstone.times.unknown.tipTimes")*checkInterval;
		unknownRemoveTimes = config.getInt("redstone.times.unknown.removeTimes")*checkInterval;
		ingameTip = config.getBoolean("redstone.tip.ingame");
		consoleTip = config.getBoolean("redstone.tip.console");
		ingameTipMinInterval = config.getInt("redstone.tip.ingameTipMinInterval");
		consoleTipMinInterval = config.getInt("redstone.tip.consoleTipMinInterval");
	}

	private String get(int id) {
		return f.get(pn, id);
	}
	
	private void reSet() {
		countHash = new HashMap<String, HashMap<Integer,HashMap<Integer,Integer>>>();
		for (World w:server.getWorlds()) countHash.put(w.getName(), new HashMap<Integer, HashMap<Integer,Integer>>());
		taskId = main.getServer().getScheduler().runTaskLater(main, reSet, checkInterval*20).getTaskId();
	}
	
	/**
	 * 重新检测
	 */
	class ReSet implements Runnable {
		@Override
		public void run() {
			reSet();
		}
	}
}
