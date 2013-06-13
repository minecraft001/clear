package clear;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

import lib.Format;
import lib.HashList;
import lib.HashListImpl;
import lib.ReloadConfigEvent;
import lib.Util;

import org.bukkit.Server;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Monster;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

public class Clear implements Listener{
	private static final int DELAY_SHOW = 35;
	private static final int CHEST_ID = 54;
	
	private Random r;
	private Main main;
	private Server server;
	private Format f;
	private String pn;
	private ServerManager serverManager;

	private HashList<String> ignoreWorlds;
	private int checkInterval;
	private int startClearEntitys;
	private HashList<Level> levelList;
	private int gridSize;
	private HashList<Integer> clearList, remainDropItems;
	private HashList<Short> clearMonsterList;
	private int maxPerGrid;
	private int animalEggChance;
	private HashList<Integer> clearTypes;
	private ClearTimer clearTimer;
	private HashList<Integer> airBlocks;//检测方块时被当作空气的id列表
	
	class Level {
		private String show;
		private boolean entity;
		private boolean monster;
		private boolean animal;
		public Level(String show, boolean entity, boolean monster, boolean animal) {
			this.show = show;
			this.entity = entity;
			this.monster = monster;
			this.animal = animal;
		}
		public String getShow() {
			return show;
		}
		public boolean isEntity() {
			return entity;
		}
		public boolean isMonster() {
			return monster;
		}
		public boolean isAnimal() {
			return animal;
		}
	}
	
	public Clear(Main main) {
		this.r = new Random();
		this.main = main;
		this.server = main.getServer();
		this.f = main.getF();
		this.pn = main.getPn();
		this.serverManager = main.getServerManager();
		
		clearTimer = new ClearTimer();
		loadConfig(main.getCon().getConfig(pn));
		main.getServer().getScheduler().scheduleSyncDelayedTask(main, clearTimer, checkInterval*20);
		main.getServer().getPluginManager().registerEvents(this, main);
	}
	
	@org.bukkit.event.EventHandler(priority=EventPriority.LOW)
	public void reloadConfig(ReloadConfigEvent e) {
		if (e.getCallPlugin().equals(pn)) {
			loadConfig(e.getConfig());
		}
	}
	
	private void loadConfig(FileConfiguration config) {
		ignoreWorlds = new HashListImpl<String>();
		for (String s:config.getStringList("clear.ignoreWorlds")) ignoreWorlds.add(s);
		checkInterval = config.getInt("clear.checkInterval");
		startClearEntitys = config.getInt("clear.startClearEntitys");
		levelList = new HashListImpl<Level>();
		String show;
		boolean entity;
		boolean monster;
		boolean animal;
		for (String s:new String[]{"good","fine","bad","unknown"}) {
			show = Util.convert(config.getString("clear.clear."+s+".show"));
			entity = config.getBoolean("clear.clear."+s+".entity");
			monster = config.getBoolean("clear.clear."+s+".monster");
			animal = config.getBoolean("clear.clear."+s+".animal");
			Level level = new Level(show, entity, monster, animal);
			levelList.add(level);
		}
		
		clearList = new HashListImpl<Integer>();
		for (int i:config.getIntegerList("clear.entity.clear")) clearList.add(i);
		remainDropItems = new HashListImpl<Integer>();
		for (int i:config.getIntegerList("clear.entity.remainDropItems")) remainDropItems.add(i);
				
		clearMonsterList = new HashListImpl<Short>();
		for (int i:config.getIntegerList("clear.monster.clear")) clearMonsterList.add((short)i);
	
		gridSize = config.getInt("clear.animal.gridSize");	
		maxPerGrid = config.getInt("clear.animal.maxPerGrid");
		animalEggChance = config.getInt("clear.animal.animalEggChance");
		clearTypes = new HashListImpl<Integer>();
		for (int i:config.getIntegerList("clear.animal.clearTypes")) clearTypes.add(i);
		airBlocks = new HashListImpl<Integer>();
		for (int i:config.getIntegerList("clear.animal.airBlocks")) airBlocks.add(i);
	}

	/**
	 * 显示游戏中实体数量
	 * @param sender 命令发出者,为null表示全服提示
	 */
	public void info(CommandSender sender) {
		HashMap<String,Integer> entityHash = new HashMap<String,Integer>();
		int total = 0;
		List<Entity> list;
		for (World w:server.getWorlds()) {
			list = w.getEntities();
			total += list.size();
			for (Entity e:list) {
				if (!entityHash.containsKey(e.getType().getName())) entityHash.put(e.getType().getName(), 0);
				entityHash.put(e.getType().getName(), entityHash.get(e.getType().getName())+1);
			}
		}
		sender.sendMessage(f.f(pn, "success", get(1200)));
		for (String s:entityHash.keySet()) sender.sendMessage(f.f(pn, "broadcastInfo", new Object[]{s,entityHash.get(s)}));
		sender.sendMessage(f.f(pn, "clearInfo2", total));
	}

	/**
	 * 表示清理服务器
	 * @param force true表示强制清理
	 * @param clearLevel 清理等级,-1表示不指定
	 */
	public void clear(boolean force, int clearLevel) {
		//服务器状态检测
		if (!force && serverManager.getServerStatus() == 0) return;
		//实体数量检测
		HashMap<String,Integer> startHash = new HashMap<String,Integer>();
		int startTotal = 0;
		List<Entity> list;
		for (World w:server.getWorlds()) {
			list = w.getEntities();
			startTotal += list.size();
			for (Entity e:list) {
				if (!startHash.containsKey(e.getType().getName())) startHash.put(e.getType().getName(), 0);
				startHash.put(e.getType().getName(),startHash.get(e.getType().getName())+1);
			}
		}
		if (!force && startTotal < startClearEntitys) return;
		//clear
		if (clearLevel == -1) clearLevel = serverManager.getServerStatus();
		else if (clearLevel < 0) clearLevel = 0;
		else if (clearLevel > 3) clearLevel = 3;
		
		server.broadcastMessage(f.f(pn, "success", get(1205)));
		server.broadcastMessage(f.f(pn, "clearLevel", levelList.get(clearLevel).getShow()));
		//检测清除无用实体
		if (levelList.get(clearLevel).isEntity()) {
			server.broadcastMessage(get(1295)+get(1890));
			for (World w:server.getWorlds()) {
				if (ignoreWorlds.has(w.getName())) continue;
				Iterator<Entity> it = w.getEntities().iterator();
				Entity e;
				int id;
				Item item;
				ItemStack is;
				int itemId;
				while (it.hasNext()) {
					e = it.next();
					try {
						id = (int)e.getType().getTypeId();
						if (id == 1) {//掉落的物体
							item = (Item) e;
							is = item.getItemStack();
							itemId = is.getTypeId();
							if (!remainDropItems.has(itemId)) {//非贵重不需要清除的物品
								e.remove();
								it.remove();
							}
						}else if (clearList.has(id)) {//直接清理的实体
							e.remove();
							it.remove();
						}
					} catch (Exception e1) {
					}
				}
			}
		}else server.broadcastMessage(get(1295)+get(1891));
		
		//检测清除怪物
		if (levelList.get(clearLevel).isMonster()) {
			server.broadcastMessage(get(1305)+get(1890));
			for (World w:server.getWorlds()) {
				if (ignoreWorlds.has(w.getName())) continue;
				Iterator<Monster> it = w.getEntitiesByClass(Monster.class).iterator();
				Monster mon;
				while (it.hasNext()) {
					mon = it.next();
					if (clearMonsterList.has(mon.getType().getTypeId())) {
						mon.remove();
						it.remove();
					}
				}
			}
		}else server.broadcastMessage(get(1305)+get(1891));
		
		//检测及清除动物
		if (levelList.get(clearLevel).isAnimal()) {
			server.broadcastMessage(get(1310)+get(1890));
			
			HashMap<Integer,HashMap<Integer,Integer>> amountHash = new HashMap<Integer, HashMap<Integer,Integer>>();//x,z,amount
			int x,z,id,current;
			for (World w:server.getWorlds()) {
				if (ignoreWorlds.has(w.getName())) continue;
				Iterator<Animals> it = w.getEntitiesByClass(Animals.class).iterator();
				Animals animals;
				while (it.hasNext()) {
					animals = it.next();
					id = animals.getType().getTypeId();
					if (clearTypes.has(id)) {
						x = animals.getLocation().getBlockX()/gridSize;
						z = animals.getLocation().getBlockZ()/gridSize;
						if (!amountHash.containsKey(x)) amountHash.put(x, new HashMap<Integer,Integer>());
						if (!amountHash.get(x).containsKey(z)) amountHash.get(x).put(z, 0);
						if ((current = amountHash.get(x).get(z)) >= maxPerGrid) {
							animals.remove();
							it.remove();
							if (r.nextInt(100) < animalEggChance) checkGenerateChest(w,x,z,id);
						}else amountHash.get(x).put(z, current+1);
					}
				}
			}
		}else server.broadcastMessage(get(1310)+get(1891));
		//delay show
		server.getScheduler().scheduleSyncDelayedTask(main, new DelayShow(startHash, startTotal), DELAY_SHOW);
	}
	
	/**
	 * 检测生成箱子及在里面放一个相应的怪物蛋
	 * @param w 世界
	 * @param x 除listSize后的x坐标
	 * @param z 除listSize后的z坐标
	 * @param id 怪物蛋的伤害值
	 */
	private void checkGenerateChest(World w, int x, int z, int id) {
		int xx,zz;
		if (x > 0) xx = x*gridSize+gridSize/2;
		else xx = x*gridSize-gridSize/2;
		if (z > 0) zz = z*gridSize+gridSize/2;
		else zz = z*gridSize-gridSize/2;
		for (int yy = 254; yy > 0;yy--) {
			if (!airBlocks.has(w.getBlockAt(xx, yy, zz).getTypeId())) {
				if (w.getBlockAt(xx, yy, zz).getTypeId() != CHEST_ID) {
					yy ++;
					w.getBlockAt(xx, yy, zz).setTypeId(CHEST_ID);
				}
				Chest chest = (Chest) w.getBlockAt(xx, yy, zz).getState();
				Inventory inventory = chest.getBlockInventory();
				inventory.addItem(new ItemStack(383, 1, (short)id));
				return;
			}
		}
	}
	
	private String get(int id) {
		return f.get(pn, id);
	}
	
	class ClearTimer implements Runnable {
		@Override
		public void run() {
			clear(false, -1);
			main.getServer().getScheduler().scheduleSyncDelayedTask(main, clearTimer, checkInterval*20);
		}
	}
	
	class DelayShow implements Runnable {
		HashMap<String,Integer> startHash;
		int startTotal;
		
		public DelayShow(HashMap<String,Integer> startHash, int startTotal) {
			this.startHash = startHash;
			this.startTotal = startTotal;
		}
		@Override
		public void run() {
			HashMap<String,Integer> endHash = new HashMap<String,Integer>();
			//end
			int endTotal = 0;
			List<Entity> list2;
			for (World w:server.getWorlds()) {
				list2 = w.getEntities();
				endTotal += list2.size();
				for (Entity e:list2) {
					if (!endHash.containsKey(e.getType().getName())) endHash.put(e.getType().getName(), 0);
					endHash.put(e.getType().getName(),endHash.get(e.getType().getName())+1);
				}
			}
			//show
			server.broadcastMessage(f.f(pn, "success", get(1210)));
			int end;
			for (String s:startHash.keySet()) {
				if (endHash.containsKey(s)) end = endHash.get(s);
				else end = 0;
				server.broadcastMessage(f.f(pn, "clearInfo", new Object[]{s,startHash.get(s),end}));
				endHash.remove(s);
			}
			for (String s:endHash.keySet()) {
				server.broadcastMessage(f.f(pn, "clearInfo", new Object[]{s,0,endHash.get(s)}));
			}
			server.broadcastMessage(f.f(pn, "clearInfo3", new Object[]{startTotal,endTotal}));
		}
	}
}
