package kr.xraydetect;

import org.bukkit.plugin.java.JavaPlugin;

public class XrayDetectPlugin extends JavaPlugin {

    private static XrayDetectPlugin instance;
    private SuspicionManager suspicionManager;

    @Override
    public void onEnable() {
        instance = this;

        // 기본 config.yml이 없으면 jar 안의 기본값을 복사
        saveDefaultConfig();

        this.suspicionManager = new SuspicionManager(this);

        getServer().getPluginManager().registerEvents(new XrayListener(this, suspicionManager), this);
        getServer().getPluginManager().registerEvents(new ResourcePackListener(this, suspicionManager), this);

        // 일정 주기로 의심 점수를 서서히 감소시킴 (오탐 방지 + 메모리 정리)
        int decayIntervalTicks = getConfig().getInt("decay-interval-minutes", 10) * 60 * 20;
        getServer().getScheduler().runTaskTimer(this, suspicionManager::decayAll,
                decayIntervalTicks, decayIntervalTicks);

        getLogger().info("XrayDetect 플러그인이 활성화되었습니다.");
    }

    @Override
    public void onDisable() {
        getLogger().info("XrayDetect 플러그인이 비활성화되었습니다.");
    }

    public static XrayDetectPlugin getInstance() {
        return instance;
    }
}
