package kr.xraydetect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

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
                    // String 형태의 SHA-1 해시를 byte[] 배열로 변환
                    byte[] hashBytes = new byte[sha1.length() / 2];
                    for (int i = 0; i < hashBytes.length; i++) {
                        int index = i * 2;
                        int val = Integer.parseInt(sha1.substring(index, index + 2), 16);
                        hashBytes[i] = (byte) val;
                    }
                    player.setResourcePack(url, hashBytes, prompt, force);
                } else {
                    player.setResourcePack(url, new byte[0], prompt, force);
                }
            } catch (Exception e) {
                plugin.getLogger().warning("리소스팩 전송 실패: " + e.getMessage());
            }
        }, 40L); // 2초 후 전송
    }
}
