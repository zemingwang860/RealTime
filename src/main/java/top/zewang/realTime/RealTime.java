package top.zewang.realtime;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RealTime extends JavaPlugin implements Listener, CommandExecutor {

    private BukkitTask timeSyncTask;
    private final Set<UUID> playersInBed = new HashSet<>();
    private boolean timeSynced = false;
    private long lastSyncTime = 0;
    private int lastTimeOfDay = -1;

    private static final long SYNC_INTERVAL = 20L;
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    private static final int[] TIME_BOUNDARIES = {0, 1000, 6000, 12000, 13000, 18000, 23000, 24000};
    private static final String[] TIME_MESSAGES = {
            "§e日出江花红胜火，春来江水绿如蓝。",
            "§e清风徐来，水波不兴。",
            "§e午时一枕清风梦，梦醒人间正午时。",
            "§c夕阳无限好，只是近黄昏。",
            "§8床前明月光，疑是地上霜。",
            "§8夜半钟声到客船。",
            "§e日出江花红胜火，春来江水绿如蓝。"
    };

    @Override
    public void onEnable() {
        // 注册事件和命令
        getServer().getPluginManager().registerEvents(this, this);
        getCommand("realtime").setExecutor(this);

        // 启动时间同步
        startTimeSync();

        // 设置世界规则
        for (World world : Bukkit.getWorlds()) {
            world.setGameRuleValue("doDaylightCycle", "false");
            world.setGameRuleValue("doInsomnia", "false");
            world.setGameRuleValue("doMobSpawning", "true");
            getLogger().info("世界 " + world.getName() + " 已禁用昼夜更替");
        }

        Bukkit.broadcastMessage("§a[RealTime] 服务器时间已与现实时间同步");
        Bukkit.broadcastMessage("§a[RealTime] 床已无法跳过夜晚，夜晚时间与真实世界同步");
        Bukkit.broadcastMessage("§a[RealTime] 每个时段将自动播报诗意时间");

        getLogger().info("RealTime 插件已启用 - 当前现实时间: " + getRealTimeString());
    }

    @Override
    public void onDisable() {
        if (timeSyncTask != null) {
            timeSyncTask.cancel();
            timeSyncTask = null;
        }
        playersInBed.clear();
        getLogger().info("RealTime 插件已禁用");
    }

    private void startTimeSync() {
        timeSyncTask = new BukkitRunnable() {
            @Override
            public void run() {
                syncTimeWithRealWorld();
            }
        }.runTaskTimer(this, 0L, SYNC_INTERVAL);
    }

    private void syncTimeWithRealWorld() {
        LocalTime realTime = LocalTime.now(ZoneId.systemDefault());
        long realSeconds = realTime.toSecondOfDay();
        long minecraftTime = (realSeconds * 24000L) / 86400L;

        for (World world : Bukkit.getWorlds()) {
            world.setFullTime(minecraftTime);
            if (world.hasStorm()) {
                world.setStorm(false);
                world.setThundering(false);
            }
        }

        int currentTimeOfDay = getTimeOfDayIndex(minecraftTime);
        if (lastTimeOfDay != -1 && currentTimeOfDay != lastTimeOfDay) {
            String poem = TIME_MESSAGES[currentTimeOfDay];
            Bukkit.broadcastMessage("§6[Mingze时间] " + poem);
            getLogger().info("时段变化: " + getTimeOfDayName(currentTimeOfDay) + " -> " + poem);
        }
        lastTimeOfDay = currentTimeOfDay;

        if (!timeSynced) {
            timeSynced = true;
            getLogger().info("首次时间同步完成: " + getRealTimeString() + " -> Minecraft时间: " + minecraftTime);
        }
        long now = System.currentTimeMillis();
        if (now - lastSyncTime >= 300000) {
            lastSyncTime = now;
            getLogger().info("时间同步中... 现实时间: " + getRealTimeString() + " | 游戏时间: " + minecraftTime);
        }
    }

    private int getTimeOfDayIndex(long time) {
        if (time < 1000) return 0;
        else if (time < 6000) return 1;
        else if (time < 12000) return 2;
        else if (time < 13000) return 3;
        else if (time < 18000) return 4;
        else if (time < 23000) return 5;
        else return 6;
    }

    private String getTimeOfDayName(int index) {
        String[] names = {"日出", "上午", "下午", "日落", "夜晚", "深夜", "黎明"};
        return names[index];
    }

    private String getRealTimeString() {
        return LocalTime.now(ZoneId.systemDefault()).format(TIME_FORMATTER);
    }

    // ---------- 命令处理 ----------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("realtime")) {
            // 检查权限
            if (!sender.hasPermission("realtime.use")) {
                sender.sendMessage("§c你没有权限使用此命令！");
                return true;
            }
            sender.sendMessage(getPluginStatus());
            return true;
        }
        return false;
    }

    private String getPluginStatus() {
        LocalTime realTime = LocalTime.now(ZoneId.systemDefault());
        long realSeconds = realTime.toSecondOfDay();
        long minecraftTime = (realSeconds * 24000L) / 86400L;
        boolean isNight = minecraftTime >= 12500 && minecraftTime < 23500;

        return "§6=== RealTime 插件状态 ===\n" +
                "§a现实时间: " + getRealTimeString() + "\n" +
                "§e游戏时间: " + minecraftTime + " (" + getTimeOfDayName(getTimeOfDayIndex(minecraftTime)) + ")\n" +
                "§7当前阶段: " + (isNight ? "§8夜晚" : "§e白天") + "\n" +
                "§7在线玩家: " + Bukkit.getOnlinePlayers().size() + "\n" +
                "§7睡觉玩家: " + playersInBed.size() + "\n" +
                "§7时间同步: " + (timeSynced ? "§a已同步" : "§c未同步");
    }

    // ---------- 事件监听 ----------
    @EventHandler
    public void onPlayerBedEnter(PlayerBedEnterEvent event) {
        if (event.isCancelled()) return;
        Player player = event.getPlayer();
        World world = player.getWorld();

        if (!"false".equals(world.getGameRuleValue("doDaylightCycle"))) return;

        long time = world.getFullTime() % 24000;
        boolean isNight = time >= 12500 && time < 23500;

        if (!isNight) {
            event.setCancelled(true);
            player.sendMessage("§c[RealTime] 现在不是夜晚，无法睡觉！");
            return;
        }

        playersInBed.add(player.getUniqueId());
        player.sendMessage("§a[RealTime] 你正在睡觉... 但无法跳过夜晚");
        player.sendMessage("§e当前现实时间: " + getRealTimeString());
        player.sendMessage("§7提示: 夜晚会持续到现实中的早晨");

        if (playersInBed.size() == 1) {
            Bukkit.broadcastMessage("§e[RealTime] " + player.getName() + " 正在睡觉...");
        } else {
            Bukkit.broadcastMessage("§e[RealTime] " + playersInBed.size() + " 名玩家正在睡觉...");
        }
    }

    @EventHandler
    public void onPlayerBedLeave(PlayerBedLeaveEvent event) {
        Player player = event.getPlayer();
        playersInBed.remove(player.getUniqueId());
        player.sendMessage("§a[RealTime] 你起床了，当前现实时间: " + getRealTimeString());
        if (playersInBed.isEmpty()) {
            Bukkit.broadcastMessage("§e[RealTime] 所有玩家都已起床");
        }
    }

    @EventHandler
    public void onPlayerQuit(PlayerQuitEvent event) {
        playersInBed.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onMonsterSpawn(CreatureSpawnEvent event) {
        // 抑制怪物游荡提示（实际由doDaylightCycle禁用已生效）
    }
}