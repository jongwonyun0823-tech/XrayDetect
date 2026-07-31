package kr.xraydetect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerResourcePackStatusEvent;

/**
 * 서버 지정 리소스팩을 강제하고, 플레이어가 이를 거부/실패했는지 감지한다.
 *
 * 원리: 서버가 지정한 리소스팩을 강제로 적용시키면, 플레이어는 자기만의
 * x-ray 텍스처팩을 동시에 사용할 수 없다(바닐라 클라이언트는 팩이 하나만 활성화됨).
 * 따라서 "거부(DECLINED)" 또는 "다운로드 실패" 이벤트가 곧 의심 신호가 된다.
 *
 * 주의: config.yml의 resource-pack-url / resource-pack-sha1 을 실제 값으로 채워야 동작한다.
 */
public class ResourcePackListener implements Listener {

    private final XrayDetectPlugin plugin;
    private final SuspicionManager suspicionManager;

    public ResourcePackListener(XrayDetectPlugin plugin, SuspicionManager suspicionManager) {
        this.plugin = plugin;
        this.suspicionManager = suspicionManager;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String url = plugin.getConfig().getString("resource-pack-url", "");
        if (url == null || url.isEmpty()) return; // 설정 안 했으면 이 기능 자체를 건너뜀

        String sha1 = plugin.getConfig().getString("resource-pack-sha1", "");
        String prompt = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("resource-pack-prompt",
                        "&e서버 이용을 위해 리소스팩 적용이 필요합니다."));
        boolean force = plugin.getConfig().getBoolean("resource-pack-force", true);

        Player player = event.getPlayer();

        // 몇 틱 뒤에 전송 (접속 직후 바로 보내면 씹히는 경우가 있어 안정성 확보)
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            try {
                if (sha1 != null && !sha1.isEmpty()) {
                    player.setResourcePack(url, sha1, prompt, force);
                } else {
                    player.setResourcePack(url, prompt, force);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("리소스팩 전송 실패: " + e.getMessage());
            }
        }, 40L); // 2초 후 전송
    }

    @EventHandler
    public void onStatus(PlayerResourcePackStatusEvent event) {
        Player player = event.getPlayer();
        PlayerResourcePackStatusEvent.Status status = event.getStatus();

        boolean suspicious = status == PlayerResourcePackStatusEvent.Status.DECLINED
                || status == PlayerResourcePackStatusEvent.Status.FAILED_DOWNLOAD;

        if (!suspicious) return;

        // 리소스팩 거부/실패는 데이터 절약, 저사양 기기, 네트워크 문제 등
        // x-ray와 무관한 이유가 훨씬 흔하다. 그래서 의심 점수에 합산하거나
        // 관리자에게 알림을 보내지 않고, 필요 시 확인할 수 있도록 로그만 남긴다.
        suspicionManager.logResourcePackIssue(player, status.name());

        boolean kickOnDecline = plugin.getConfig().getBoolean("kick-on-decline", false);
        if (kickOnDecline) {
            player.kickPlayer(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("kick-message",
                            "&c서버 리소스팩을 적용해야 게임을 이용할 수 있습니다.")));
        }
    }
}
