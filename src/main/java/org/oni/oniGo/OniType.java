package org.oni.oniGo;

public enum OniType {
    YASHA("夜叉", "通常の鬼。チェスト探知とテレポートが使える。"),
    KISHA("鬼叉", "歩く速度が最高速。2回攻撃して1キル。「突進」「停止」「金棒」のスキルを持つ。"),
    ANSHA("闇叉", "プレイヤーの1.2倍の速度。一撃キル。常に暗闇2の状態。「暗転」「転生」「逃亡不可」のスキルを持つ。"),
    GETUGA("月牙", "プレイヤーの1.5倍の速度。5回攻撃でキル。30秒ごとにプレイヤーの位置を検知。「月切り」「三日月」「殺月」のスキルを持つ。");

    private final String displayName;
    private final String description;

    OniType(String displayName, String description) {
        this.displayName = displayName;
        this.description = description;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getDescription() {
        return description;
    }
}