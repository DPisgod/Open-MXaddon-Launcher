package dev.mx.launcher;

import com.sun.jna.platform.win32.Crypt32Util;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

/** Saved Minecraft accounts, protected with the current Windows user's DPAPI key. */
final class AccountStore {
    private final Path home;
    private final List<Session> accounts = new ArrayList<>();
    private Exception loadFailure;

    AccountStore(Path home) {
        this.home = home;
        Path file = LauncherPaths.dataFile(home,"accounts.dat");
        if (!Files.exists(file)) return;
        try {
            byte[] plain = Crypt32Util.cryptUnprotectData(Files.readAllBytes(file));
            Map<String,Object> data=Json.parseObject(new String(plain, StandardCharsets.UTF_8));
            java.util.Arrays.fill(plain,(byte)0);
            Object rows = data.get("accounts");
            if (!(rows instanceof List<?> list)) throw new IOException("账号列表格式无效");
            for (Object row : list) {
                if (!(row instanceof Map<?,?> m)) continue;
                String name = String.valueOf(m.get("name"));
                Session account;
                if (Boolean.TRUE.equals(m.get("premium"))) {
                    String uuid = String.valueOf(m.get("uuid")), token = String.valueOf(m.get("accessToken"));
                    if (!uuid.matches("[a-fA-F0-9]{32}") || token.isBlank() || token.equals("null")) continue;
                    Object xuid = m.get("xuid");
                    account = Session.premium(name,uuid,token,xuid == null ? null : xuid.toString());
                } else account = Session.offline(Session.offlineName(name));
                accounts.removeIf(existing -> key(existing).equals(key(account)));
                accounts.add(account);
            }
            if(!Boolean.TRUE.equals(data.get("explicitAccounts"))){List<Session> migrated=accounts.stream().filter(account->account.premium||!account.name.equals("Player")).toList();if(migrated.size()!=accounts.size())save(migrated);}
        } catch (Exception e) { loadFailure = e; }
    }

    List<Session> available(Session current) {
        List<Session> result = new ArrayList<>(accounts);
        if (current != null && result.stream().noneMatch(a -> key(a).equals(key(current)))) result.add(current);
        return List.copyOf(result);
    }

    void remember(Session account) throws IOException {
        checkReadable();
        List<Session> updated = new ArrayList<>(accounts);
        int index = -1;
        for (int i=0;i<updated.size();i++) if (key(updated.get(i)).equals(key(account))) { index=i; break; }
        if (index<0) updated.add(account); else updated.set(index,account);
        save(updated);
    }

    void forget(Session account) throws IOException {
        checkReadable();
        List<Session> updated = new ArrayList<>(accounts);
        updated.removeIf(a -> key(a).equals(key(account)));
        save(updated);
    }

    private void checkReadable() throws IOException {
        if (loadFailure != null) throw new IOException("无法读取已保存的账号，原文件已保留。请使用创建它的 Windows 用户登录。",loadFailure);
    }

    private void save(List<Session> updated) throws IOException {
        Map<String,Object> data = Json.map();
        data.put("explicitAccounts",true);
        List<Object> rows = new ArrayList<>();
        for (Session account : updated) {
            Map<String,Object> row = Json.map();
            row.put("name",account.name); row.put("premium",account.premium);
            row.put("uuid",account.uuid); row.put("accessToken",account.accessToken); row.put("xuid",account.xuid);
            rows.add(row);
        }
        data.put("accounts",rows);
        byte[] encrypted;
        try { encrypted=Crypt32Util.cryptProtectData(Json.write(data).getBytes(StandardCharsets.UTF_8)); }
        catch (Exception e) { throw new IOException("无法保护账号列表",e); }
        Path file=LauncherPaths.dataFile(home,"accounts.dat");
        Path temp=Files.createTempFile(file.getParent(),"accounts-",".tmp");
        try {
            Files.write(temp,encrypted);
            try { Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp,file,StandardCopyOption.REPLACE_EXISTING); }
            accounts.clear(); accounts.addAll(updated);
        } finally { Files.deleteIfExists(temp); }
    }

    static String key(Session account) { return account.premium ? "premium:"+account.uuid.toLowerCase(Locale.ROOT) : "offline:"+account.name; }
}
