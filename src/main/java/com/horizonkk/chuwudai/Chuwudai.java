package com.horizonkk.chuwudai;

import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.command.BagCommand;
import com.horizonkk.chuwudai.data.BagDataManager;
import com.horizonkk.chuwudai.hook.PlaceholderAPI;
import com.horizonkk.chuwudai.listener.BagListener;
import com.horizonkk.chuwudai.listener.BagOperationRestrictionListener;
import com.horizonkk.chuwudai.listener.PillListener;
import com.horizonkk.chuwudai.listener.StonePickupListener;
import com.horizonkk.chuwudai.modbag.ModBagBridge;
import com.horizonkk.chuwudai.pill.PillDataManager;
import com.horizonkk.chuwudai.pill.PillManager;
import com.horizonkk.chuwudai.pill.PillUseEvent;
import com.horizonkk.chuwudai.Temporary.TempBoxListener;
import com.horizonkk.chuwudai.Temporary.TempBoxManager;
import com.horizonkk.chuwudai.utlis.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Chuwudai extends JavaPlugin {

    private static Chuwudai instance;
    private BagManager bagManager;
    private BagDataManager dataManager;
    private PillManager pillManager;
    private PillDataManager pillDataManager;
    private TempBoxManager tempBoxManager;
    private ModBagBridge modBagBridge;
    private PlaceholderAPI placeholderExpansion;
    private String activeStorageType;
    private com.horizonkk.chuwudai.storage.SharedStorage sharedStorage;
    public com.horizonkk.chuwudai.storage.SharedStorage getSharedStorage() { return sharedStorage; }

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置文件
        saveDefaultConfig();

        String storageType=getConfig().getString("storage.type","sqlite").toLowerCase(java.util.Locale.ROOT);
        activeStorageType=storageType;
        if (!java.util.Set.of("sqlite","yaml","mysql").contains(storageType)) throw new IllegalArgumentException("Unknown storage.type");
        if(storageType.equals("sqlite")) DatabaseManager.initialize(new java.io.File(getDataFolder(),getConfig().getString("storage.sqlite.file","bags.db")).getPath());
        if(storageType.equals("mysql")) sharedStorage=new com.horizonkk.chuwudai.storage.SharedStorage(this);
        // 初始化管理器
        this.dataManager = new BagDataManager();
        this.bagManager = new BagManager();
        this.pillDataManager = new PillDataManager();
        this.pillManager = new PillManager();
        this.tempBoxManager = new TempBoxManager();
        this.modBagBridge = new ModBagBridge(this);
        this.modBagBridge.register();

        // 注册命令
        getCommand("chuwudai").setExecutor(new BagCommand());

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new BagListener(), this);
        getServer().getPluginManager().registerEvents(new StonePickupListener(), this);
        // 添加新的监听器
        getServer().getPluginManager().registerEvents(new BagOperationRestrictionListener(), this);
        getServer().getPluginManager().registerEvents(new PillListener(), this);
        getServer().getPluginManager().registerEvents(new PillUseEvent(), this);
        getServer().getPluginManager().registerEvents(new TempBoxListener(), this);

        getServer().getPluginManager().registerEvents(new com.horizonkk.chuwudai.storage.StorageListener(this), this);

        // 注册 PlaceholderAPI 扩展
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            placeholderExpansion = new PlaceholderAPI(this);
            if (placeholderExpansion.register()) {
                placeholderExpansion.start();
                getLogger().info("PlaceholderAPI 扩展已注册，支持异步读取显示缓存！");
            } else {
                getLogger().warning("PlaceholderAPI 扩展注册失败！");
            }
        } else {
            getLogger().warning("PlaceholderAPI 未找到，变量功能将不可用！");
        }

        // 异步加载数据
        getServer().getScheduler().runTask(this, () -> {
            dataManager.loadAllBags();
            pillDataManager.loadAllPills();
            getLogger().info("储物袋和丹瓶插件已加载完成！");
        });
    }

    @Override
    public void onDisable() {
        if (placeholderExpansion != null) placeholderExpansion.close();
        if (tempBoxManager != null) tempBoxManager.close();
        if (modBagBridge != null) {
            try { modBagBridge.closeAll(); } catch(RuntimeException e) { getLogger().warning("界面关闭失败，继续保存容器: "+e.getMessage()); }
        }
        if (bagManager != null) {
            bagManager.flushAllDirtyBags();
        }
        if (sharedStorage != null) { sharedStorage.close(); return; }
        // 保存所有数据
        if(dataManager!=null) dataManager.saveAllBags();
        if(pillDataManager!=null) pillDataManager.saveAllPills();
        // 执行数据库优化 清理过期的数据
        if("sqlite".equals(activeStorageType)) DatabaseManager.vacuum();
        DatabaseManager.close();
        // 关闭数据库连接池
        if(dataManager!=null) dataManager.close();
        if(pillDataManager!=null) pillDataManager.close();
        getLogger().info("储物袋插件已安全关闭！");
    }

    public static Chuwudai getInstance() {
        return instance;
    }

    public BagManager getBagManager() {
        return bagManager;
    }

    public BagDataManager getDataManager() {
        return dataManager;
    }

    public PillManager getPillManager() {
        return pillManager;
    }

    public PillDataManager getPillDataManager() {
        return pillDataManager;
    }

    public TempBoxManager getTempBoxManager() {
        return tempBoxManager;
    }

    public ModBagBridge getModBagBridge() {
        return modBagBridge;
    }
}
