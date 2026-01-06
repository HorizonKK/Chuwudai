package com.horizonkk.chuwudai;

import com.horizonkk.chuwudai.bag.BagManager;
import com.horizonkk.chuwudai.command.BagCommand;
import com.horizonkk.chuwudai.data.BagDataManager;
import com.horizonkk.chuwudai.listener.BagListener;
import com.horizonkk.chuwudai.listener.BagOperationRestrictionListener;
import com.horizonkk.chuwudai.listener.StonePickupListener;
import org.bukkit.plugin.java.JavaPlugin;

public final class Chuwudai extends JavaPlugin {

    private static Chuwudai instance;
    private BagManager bagManager;
    private BagDataManager dataManager;

    @Override
    public void onEnable() {
        instance = this;

        // 保存默认配置文件
        saveDefaultConfig();

        // 初始化管理器
        this.dataManager = new BagDataManager();
        this.bagManager = new BagManager();

        // 注册命令
        getCommand("chuwudai").setExecutor(new BagCommand());

        // 注册事件监听器
        getServer().getPluginManager().registerEvents(new BagListener(), this);
        getServer().getPluginManager().registerEvents(new StonePickupListener(), this);
        // 添加新的监听器
        getServer().getPluginManager().registerEvents(new BagOperationRestrictionListener(), this);

        // 异步加载数据
        getServer().getScheduler().runTaskAsynchronously(this, () -> {
            dataManager.loadAllBags();
            getLogger().info("储物袋插件已加载完成！");
        });
    }

    @Override
    public void onDisable() {
        // 保存所有数据
        dataManager.saveAllBags();
        // 关闭数据库连接池
        dataManager.close();
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
}