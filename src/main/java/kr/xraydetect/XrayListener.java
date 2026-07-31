package kr.xraydetect;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

import java.util.Map;

/**
 * 광석이 깨질 때, 깨지기 "직전" 기준으로 주변 6면이 모두 막혀 있었는지 확인한다.
 * 노출면이 하나도 없는 상태에서 바로 광석을 캤다면 -> 벽 너머 광물을 미리 보고 캔 것으로 의심.
 * 연산량이 매우 적어(인접 6블록 확인) 렉 걱정 없이 사용 가능.
 */
public class XrayListener implements Listener {

    private final XrayDetectPlugin plugin;
    private final SuspicionManager suspicionManager;

    private static final BlockFace[] FACES = {
            BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH,
            BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST
    };

    public XrayListener(XrayDetectPlugin plugin, SuspicionManager suspicionManager) {
        this.plugin = plugin;
        this.suspicionManager = suspicionManager;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        // 크리에이티브/운영자는 검사하지 않음 (오탐 방지)
        if (player.getGameMode().name().equals("CREATIVE")) return;
        if (player.hasPermission("xraydetect.bypass")) return;

        Block block = event.getBlock();
        Material type = block.getType();

        ConfigurationSection ores = plugin.getConfig().getConfigurationSection("ore-weights");
        if (ores == null) return;

        String key = type.name();
        if (!ores.contains(key)) return; // 감시 대상 광석이 아니면 무시

        int baseWeight = ores.getInt(key);
        boolean fullyHidden = isFullyHidden(block);

        // 실제 점수 계산(연속 패턴 확인 포함)은 SuspicionManager가 담당
        suspicionManager.reportOreBreak(player, key, baseWeight, fullyHidden);
    }

    /**
     * 6면(위/아래/동/서/남/북)이 전부 비공기(막힌) 상태인지 확인.
     * true면 "정상적인 시야로는 발견할 수 없었던 광석"이라는 뜻.
     */
    private boolean isFullyHidden(Block block) {
        for (BlockFace face : FACES) {
            Material relType = block.getRelative(face).getType();
            if (isAirLike(relType)) {
                return false; // 한 면이라도 뚫려 있었다면 정상적으로 발견 가능했음
            }
        }
        return true;
    }

    private boolean isAirLike(Material mat) {
        return mat == Material.AIR || mat == Material.CAVE_AIR || mat == Material.VOID_AIR
                || mat == Material.WATER || mat == Material.LAVA;
    }
}
