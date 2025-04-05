package org.oni.oniGo;

import org.bukkit.ChatColor;

public enum OniType {
    YASHA("夜叉", ChatColor.RED + "一撃必殺。プレイヤーに暗闇効果を与える。足はすごく遅い", 1.0, 1),
    KISHA("鬼叉", ChatColor.GOLD + "突進を使って追い詰めることができる。夜叉と速度は一緒。2回攻撃で1キル。", 1.8, 2),
    ANSHA("闇叉", ChatColor.DARK_PURPLE + "一撃必殺。足がそこそこ速い。自由自在にワープができる。自身に暗闇の効果。", 1.2, 1),
    GETSUGA("月牙", ChatColor.BLUE + "探知能力を持つ鬼。最高クラスに足が速い。5回攻撃で1キル。", 1.5, 5);

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