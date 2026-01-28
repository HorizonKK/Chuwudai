package com.horizonkk.chuwudai;

import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.command.BagCommand;
import com.horizonkk.chuwudai.data.BagDataManager;
import com.horizonkk.chuwudai.listener.BagListener;
import com.horizonkk.chuwudai.listener.BagOperationRestrictionListener;
import com.horizonkk.chuwudai.listener.PillListener;
import com.horizonkk.chuwudai.listener.StonePickupListener;
import com.horizonkk.chuwudai.pill.PillDataManager;
import com.horizonkk.chuwudai.pill.PillManager;
import com.horizonkk.chuwudai.utlis.DatabaseManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class Chuwudai extends JavaPlugin {

    private static Chuwudai instance;
    private BagManager bagManager;
    private BagDataManager dataManager;
    private PillManager pillManager;
    private PillDataManager pillDataManager;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置文件
        saveDefaultConfig();

        // 初始化管理器
        this.dataManager = new BagDataManager();
        this.bagManager = new BagManager();
        this.pillDataManager = new PillDataManager();
        this.pillManager = new PillManager();

        // 注册命令
        getCommand("chuwudai").setExecutor(new BagCommand());

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new BagListener(), this);
        getServer().getPluginManager().registerEvents(new StonePickupListener(), this);
        // 添加新的监听器
        getServer().getPluginManager().registerEvents(new BagOperationRestrictionListener(), this);
        getServer().getPluginManager().registerEvents(new PillListener(), this);

        // 异步加载数据
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            dataManager.loadAllBags();
            pillDataManager.loadAllPills();
            getLogger().info("储物袋和丹瓶插件已加载完成！");
        });
    }

    @Override
    public void onDisable() {
        // 保存所有数据
        dataManager.saveAllBags();
        pillDataManager.saveAllPills();
        // 执行数据库优化 清理过期的数据
        DatabaseManager.vacuum();
        // 关闭数据库连接池
        dataManager.close();
        pillDataManager.close();
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
}