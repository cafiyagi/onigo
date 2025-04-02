package org.oni.oniGo;

import org.bukkit.ChatColor;

public enum OniType {
    YASHA("夜叉", ChatColor.RED + "通常の鬼。プレイヤーに暗闇効果を与える。", 1.0, 1),
    KISHA("鬼叉", ChatColor.GOLD + "最高速度で歩く鬼。2回攻撃で1キル。", 1.8, 2),
    ANSHA("闇叉", ChatColor.DARK_PURPLE + "常に暗闇効果を持つが高速な鬼。", 1.2, 1),
    GETSUGA("月牙", ChatColor.BLUE + "チェスト探知能力を持つ鬼。5回攻撃で1キル。", 1.5, 5);

    private final String displayName;
    private final String description;
    private final double speedMultiplier;
    private final int hitsToKill;

    OniType(String displayName, String description, double speedMultiplier, int hitsToKill) {
        this.displayName = displayName;
        this.description = description;
        this.speedMultiplier = speedMultiplier;
        this.hitsToKill = hitsToKill;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }

    public double getSpeedMultiplier() {
        return speedMultiplier;
    }

    public int getHitsToKill() {
        return hitsToKill;
    }
}