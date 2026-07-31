package kr.xraydetect;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.SimpleDateFormat;
import java.util.ArrayDeque;
import java.util.Date;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 플레이어별 X-ray 의심 점수를 관리한다.
 * 무거운 연산을 피하기 위해 메모리(HashMap)에서만 점수를 계산하고,
 * 알림 발생 시에만 비동기로 로그 파일에 기록한다.
 */
public class SuspicionManager {

    private final XrayDetectPlugin plugin;
    private final Map<UUID, Integer> scores = new ConcurrentHashMap<>();
    // 최근 "완전히 막힌 광석" 채굴 시각들을 저장 -> 연속 패턴인지 판단하는 데 사용
    private final Map<UUID, Deque<Long>> hiddenOreTimestamps = new ConcurrentHashMap<>();
    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    public SuspicionManager(XrayDetectPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 광석 채굴 이벤트를 처리한다.
     *
     * 정상 플레이어도 "가끔" 막힌 광석을 캘 수 있으므로, 완전히 막힌 광석을
     * 짧은 시간 안에 "연속으로 여러 번" 캤을 때만 실질적인 의심 점수를 준다.
     * 노출된 광석(정상적으로 발견 가능했던 것)은 점수를 주지 않는다.
     */
    public void reportOreBreak(Player player, String oreName, int baseWeight, boolean fullyHidden) {
        UUID uuid = player.getUniqueId();

        int amount = fullyHidden ? evaluateHiddenStreak(uuid, baseWeight) : 0;

        if (amount <= 0) {
            // 점수는 안 주더라도 기록은 남겨서 나중에 필요하면 관리자가 확인 가능
            logToFile(player, oreName, 0, scores.getOrDefault(uuid, 0), fullyHidden);
            return;
        }

        int newScore = scores.merge(uuid, amount, Integer::sum);
        int threshold = plugin.getConfig().getInt("threshold", 100);

        if (newScore >= threshold) {
            alert(player, newScore);
            scores.put(uuid, newScore / 2);
        }

        logToFile(player, oreName, amount, newScore, fullyHidden);
    }

    /**
     * 완전히 막힌 광석 채굴 시각을 기록하고, 설정된 시간(window) 안에
     * 설정된 횟수(min-hidden-streak) 이상 반복됐을 때만 점수를 반환한다.
     * 조건을 못 채우면 0을 반환해 정상 플레이어의 우연한 채굴을 걸러낸다.
     */
    private int evaluateHiddenStreak(UUID uuid, int baseWeight) {
        long now = System.currentTimeMillis();
        long windowMillis = plugin.getConfig().getInt("streak-window-seconds", 60) * 1000L;
        int minStreak = plugin.getConfig().getInt("min-hidden-streak", 3);
        int hiddenMultiplier = plugin.getConfig().getInt("hidden-multiplier", 3);

        Deque<Long> timestamps = hiddenOreTimestamps.computeIfAbsent(uuid, k -> new ArrayDeque<>());
        timestamps.addLast(now);

        // window 밖의 오래된 기록 제거
        while (!timestamps.isEmpty() && now - timestamps.peekFirst() > windowMillis) {
            timestamps.pollFirst();
        }

        if (timestamps.size() < minStreak) {
            return 0; // 아직 "우연"의 범주 - 점수 없음
        }

        return baseWeight * hiddenMultiplier;
    }

    /**
     * 리소스팩 거부/실패는 x-ray와 무관한 이유(데이터 절약, 저사양, 다운로드 오류 등)가
     * 훨씬 흔하므로, 절대 자동으로 의심 점수에 합산하거나 알림을 보내지 않는다.
     * 관리자가 나중에 필요하면 직접 확인할 수 있도록 로그만 남긴다.
     */
    public void logResourcePackIssue(Player player, String status) {
        logToFile(player, "리소스팩_" + status, 0, scores.getOrDefault(player.getUniqueId(), 0), false);
    }

    private void alert(Player player, int score) {
        String rawMsg = plugin.getConfig().getString("alert-message",
                "&c[XrayDetect] &f{player}님이 X-ray 사용이 의심됩니다. (의심 점수: {score})");
        String msg = ChatColor.translateAlternateColorCodes('&',
                rawMsg.replace("{player}", player.getName()).replace("{score}", String.valueOf(score)));

        // 권한을 가진 관리자에게만 전송
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (online.hasPermission("xraydetect.alert")) {
                online.sendMessage(msg);
            }
        }
        // 콘솔에도 남김
        Bukkit.getConsoleSender().sendMessage(msg);
        plugin.getLogger().warning(ChatColor.stripColor(msg));
    }

    private void logToFile(Player player, String oreName, int amount, int totalScore, boolean fullyHidden) {
        // 비동기로 실행하여 메인 스레드(틱)에 영향 없게 함
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                Path folder = plugin.getDataFolder().toPath();
                if (!Files.exists(folder)) {
                    Files.createDirectories(folder);
                }
                Path logFile = folder.resolve(plugin.getConfig().getString("log-file", "xray_log.txt"));

                String line = String.format("[%s] %s 가 %s 채굴 (노출면 없음: %s, +%d점, 누적 %d점)%n",
                        dateFormat.format(new Date()), player.getName(), oreName, fullyHidden, amount, totalScore);

                try (FileWriter writer = new FileWriter(logFile.toFile(), true)) {
                    writer.write(line);
                }
            } catch (IOException e) {
                plugin.getLogger().warning("로그 파일 기록 실패: " + e.getMessage());
            }
        });
    }

    /**
     * 주기적으로 모든 플레이어의 점수를 감소시켜 오탐(정상 채굴)을 걸러낸다.
     */
    public void decayAll() {
        int decayAmount = plugin.getConfig().getInt("decay-amount", 10);
        scores.replaceAll((uuid, score) -> Math.max(0, score - decayAmount));
        scores.values().removeIf(score -> score <= 0);
    }

    public int getScore(UUID uuid) {
        return scores.getOrDefault(uuid, 0);
    }
}
