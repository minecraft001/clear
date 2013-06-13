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
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;

public class Crop implements Listener{
	private Main main;
	private Server server;
	private Format f;
	private String pn;
	
	private HashList<String> ignoreWorlds;
	private int gridSize;
	private int checkInterval;
	private int ingameTipMinInterval, consoleTipMinInterval;
	
	private int taskId = -1;
	private ReSet reSet;
	private HashMap<String, HashMap<Integer,HashMap<Integer,Integer>>> countHash;//x,z,count
	private int max;
	private boolean drop;
	private boolean reset;
	private boolean ingameTip,consoleTip;
	private long lastInGameTip, lastConsoleTip;
	
	public Crop(Main main) {
		this.main = main;
		this.server = main.getServer();
		this.f = main.getF();
		this.pn = main.getPn();
		
		loadConfig(main.getCon().getConfig(pn));
		main.getServer().getPluginManager().registerEvents(this, main);
		reSet = new ReSet();
		reSet();
	}

	@org.bukkit.event.EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
	public void onBlockGrow(BlockGrowEvent e) {
		if (!crop(e.getBlock().getLocation())) e.setCancelled(true);
	}

	@org.bukkit.event.EventHandler(priority=EventPriority.LOWEST,ignoreCancelled=true)
	public void onBlockSpread(BlockSpreadEvent e) {
		int id = e.getSource().getTypeId();
		if (id == 39 || id == 40) {
			if (!crop(e.getSource().getLocation())) e.setCancelled(true);
		}
	}

	@org.bukkit.event.EventHandler(priority=EventPriority.LOW)
	public void reloadConfig(ReloadConfigEvent e) {
		if (e.getCallPlugin().equals(pn)) {
			loadConfig(e.getConfig());
			//���ü�ʱ��
			main.getServer().getScheduler().cancelTask(taskId);
			reSet();
		}
	}
	
	/**
	 * ���������¼�ʱ����
	 * @param l λ��
	 * @return false��ʾ��⵽�쳣
	 */
	private boolean crop(Location l) {
		String worldName = l.getWorld().getName();
		if (ignoreWorlds.has(worldName)) return true;
		int x = l.getBlockX()/gridSize;
		int z = l.getBlockZ()/gridSize;
		if (!countHash.get(worldName).containsKey(x)) countHash.get(worldName).put( x, new HashMap<Integer, Integer>());
		if (!countHash.get(worldName).get(x).containsKey(z)) countHash.get(worldName).get(x).put(z, 0);
		countHash.get(worldName).get(x).put(z, countHash.get(worldName).get(x).get(z)+1);
		if (countHash.get(worldName).get(x).get(z) >= max) {//�쳣
			if (reset) countHash.get(worldName).get(x).put(z, 0);//������ü�����
			
			String handle;
			if (drop) {
				l.getBlock().breakNaturally();
				handle = get(1886);
			}else {
				l.getBlock().setTypeId(0);
				handle = get(1885);
			}
			String result = f.f(pn, "cropTip", new Object[]{l.getWorld().getName(), l.getBlockX(), l.getBlockY(), l.getBlockZ(), handle});
			long now = System.currentTimeMillis();
			if (ingameTip && now-lastInGameTip > ingameTipMinInterval) {
				server.broadcastMessage(result);
				lastInGameTip = now;
			}
			if (consoleTip && now-lastConsoleTip > consoleTipMinInterval) {
				server.getConsoleSender().sendMessage(result);
				lastConsoleTip = now;
			}
			return false;
		}
		return true;
	}

	private void loadConfig(FileConfiguration config) {
		ignoreWorlds = new HashListImpl<String>();
		for (String s:config.getStringList("crop.ignoreWorlds")) ignoreWorlds.add(s);
		gridSize = config.getInt("crop.gridSize");
		checkInterval = config.getInt("crop.checkInterval");
		max = config.getInt("crop.max");
		drop = config.getBoolean("crop.drop");
		reset = config.getBoolean("crop.reset");
		ingameTip = config.getBoolean("crop.tip.ingame");
		consoleTip = config.getBoolean("crop.tip.console");
		ingameTipMinInterval = config.getInt("crop.tip.ingameTipMinInterval");
		consoleTipMinInterval = config.getInt("crop.tip.consoleTipMinInterval");
	}

	private void reSet() {
		countHash = new HashMap<String, HashMap<Integer,HashMap<Integer,Integer>>>();
		for (World w:server.getWorlds()) countHash.put(w.getName(), new HashMap<Integer, HashMap<Integer,Integer>>());
		taskId = main.getServer().getScheduler().runTaskLater(main, reSet, checkInterval*20).getTaskId();
	}
	
	private String get(int id) {
		return f.get(pn, id);
	}
	
	/**
	 * ���¼��
	 */
	class ReSet implements Runnable {
		@Override
		public void run() {
			reSet();
		}
	}
}
