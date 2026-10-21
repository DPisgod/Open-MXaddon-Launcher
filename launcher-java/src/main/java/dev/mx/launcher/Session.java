package dev.mx.launcher;

/**
 * 玩家会话：离线或正版。
 *
 * <p>正版会话持有 accessToken 与 uuid，启动时写入 java 命令行的
 * {@code --accessToken / --uuid}；离线账号使用固定 UUID 与占位令牌。
 */
public final class Session {

    /** 玩家名。 */
    public String name;
    /** 是否正版账号。 */
    public boolean premium;
    /** 正版 UUID（无横线，32 位 hex）；离线为 null。 */
    public String uuid;
    /** 正版访问令牌；离线为 null。 */
    public String accessToken;
    /** Xbox 用户 ID（用于刷新皮肤）；离线为 null。 */
    public String xuid;

    private Session(String name, boolean premium, String uuid, String accessToken, String xuid) {
        this.name = name;
        this.premium = premium;
        this.uuid = uuid;
        this.accessToken = accessToken;
        this.xuid = xuid;
    }

    public static Session offline(String name) {
        if (name == null || name.isBlank()) {
            name = "Player";
        }
        return new Session(name, false, null, null, null);
    }

    /** Chinese names are valid for local/offline play; never silently strip user input. */
    static String offlineName(String input) {
        String name=input==null?"":input.strip();
        if(name.isEmpty())throw new IllegalArgumentException("请输入离线游戏名称。");
        if(!name.matches("[\\p{IsHan}A-Za-z0-9_]+")||name.codePointCount(0,name.length())>16)
            throw new IllegalArgumentException("离线名称支持 1–16 个汉字、英文字母、数字或下划线。");
        return name;
    }

    public static Session premium(String name, String uuid, String accessToken, String xuid) {
        return new Session(name, true, uuid, accessToken, xuid);
    }

    /** 用于皮肤的 UUID，离线用固定 Steve。 */
    public String skinUuid() {
        return premium ? uuid : null;
    }
}
