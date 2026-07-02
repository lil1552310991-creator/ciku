import android.content.SharedPreferences;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.FrameLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.EditText;
import android.widget.Button;
import android.app.Activity;
import android.app.Dialog;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

String PREFS_NAME = "nono_config_v4";

// 全局配置
String gCloudUrl = "https://raw.githubusercontent.com/lil1552310991-creator/ciku/main";

// 添加新用户时的默认值
String gDefGroupId = "";
long gDefStepBase = 4000;
long gDefStepJitter = 2000;
long gDefMuteBase = 40;
long gDefMuteJitter = 20;
long gDefRevokeBase = 20;
long gDefRevokeJitter = 10;
String gDefImgPath = "";
String gDefCardName = "一条死狗";

// 每个目标用户独立的配置
class TargetRule {
    String uin;
    String groupId;      // 生效群号（0=所有群）
    boolean emoji;       // 表情轰炸
    boolean mute;        // 禁言
    boolean insult;      // 辱骂连招
    boolean image;       // 发送图片
    boolean revoke;      // 撤回消息
    boolean nick;        // 名片守护
    long muteBase;       // 禁言标准秒数
    long muteJitter;     // 禁言浮动±秒
    long revokeBase;     // 撤回标准秒数
    long revokeJitter;   // 撤回浮动±秒
    long stepBase;       // 连招标准延迟ms
    long stepJitter;     // 连招浮动±ms
    String imgPath;      // 图片路径
    String cardName;     // 强制名片内容
}

List gTargets = new ArrayList();

int[] EMOJI_IDS = new int[] {
    1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12,
    13, 14, 15, 16, 17, 18, 19, 20, 21, 22, 23
};
int EMOJI_TYPE = 1;

// ====== 词库系统（云端同步 + 本地缓存） ======
List gPreTextPool = new ArrayList();
List gPostTextPool = new ArrayList();
List gPreImgPool = new ArrayList();
List gPostImgPool = new ArrayList();

// 从本地 Ciku/ 目录加载词库到内存池
void loadCiku() {
    try {
        String base = getScriptPath() + "/Ciku/";
        String[] names = new String[]{"preText.txt", "postText.txt", "preImg.txt", "postImg.txt"};
        List[] pools = new List[]{gPreTextPool, gPostTextPool, gPreImgPool, gPostImgPool};
        for (int i = 0; i < names.length; i++) {
            pools[i].clear();
            String path = base + names[i];
            if (exists(path)) {
                String content = read(path);
                if (content != null && content.trim().length() > 0) {
                    String[] lines = content.split("\n");
                    for (int j = 0; j < lines.length; j++) {
                        String line = lines[j].trim();
                        if (line.length() > 0) pools[i].add(line);
                    }
                }
            }
        }
        log("词库加载完成 | preText:" + gPreTextPool.size() + " postText:" + gPostTextPool.size() + " preImg:" + gPreImgPool.size() + " postImg:" + gPostImgPool.size());
    } catch (Throwable t) {
        error("词库加载失败: " + t.getMessage());
    }
}

// 原子网络请求：从云端下载单个词库文件
String reqCloudFile(String cloudUrl, String fileName) {
    String output = "";
    HttpURLConnection conn = null;
    try {
        String fullUrl = cloudUrl;
        if (!fullUrl.endsWith("/")) fullUrl = fullUrl + "/";
        fullUrl = fullUrl + fileName;
        URL url = new URL(fullUrl);
        conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(5000);
        if (conn.getResponseCode() == 200) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                if (sb.length() > 0) sb.append("\n");
                sb.append(line);
            }
            br.close();
            output = sb.toString();
        } else {
            error("下载词库失败: " + fileName + " HTTP " + conn.getResponseCode());
        }
    } catch (Throwable t) {
        error("下载词库失败: " + fileName + " - " + t.getMessage());
    } finally {
        if (conn != null) conn.disconnect();
    }
    return output;
}

// 从云端同步全部词库到本地
void syncCikuFromCloud() {
    if (gCloudUrl == null || gCloudUrl.trim().length() == 0) {
        Toast("请先在控制台填写云端词库地址");
        return;
    }
    Toast("正在从云端同步词库...");
    final String cloudUrl = gCloudUrl.trim();
    new Thread(new Runnable() {
        public void run() {
            try {
                String base = getScriptPath() + "/Ciku/";
                File dir = new File(base);
                if (!dir.exists()) dir.mkdirs();
                String[] names = new String[]{"preText.txt", "postText.txt", "preImg.txt", "postImg.txt"};
                int total = 0;
                for (int i = 0; i < names.length; i++) {
                    String data = reqCloudFile(cloudUrl, names[i]);
                    if (data != null && data.trim().length() > 0) {
                        write(base + names[i], data);
                        total++;
                    }
                    sleep(150);
                }
                final int count = total;
                Activity act = getThreadActivity();
                if (act != null) {
                    act.runOnUiThread(new Runnable() {
                        public void run() {
                            loadCiku();
                            Toast("同步完成！成功下载 " + count + "/4 个词库 | 共 " + (gPreTextPool.size() + gPostTextPool.size() + gPreImgPool.size() + gPostImgPool.size()) + " 条");
                        }
                    });
                }
            } catch (Throwable t) {
                error("云端同步失败: " + t.getMessage());
            }
        }
    }).start();
}

// 词库兜底文本（所有池为空时使用）
String[] FALLBACK_DICT = new String[] {
    "词库未同步，请打开控制台点击「📥 同步云端词库」"
};

int triggerCount = 0;
// 创建默认的 TargetRule（使用全局默认值）
TargetRule makeDefaultRule(String uin) {
    TargetRule r = new TargetRule();
    r.uin = uin;
    r.groupId = "";
    r.emoji = true;
    r.mute = true;
    r.insult = true;
    r.image = true;
    r.revoke = true;
    r.nick = true;
    r.muteBase = gDefMuteBase;
    r.muteJitter = gDefMuteJitter;
    r.revokeBase = gDefRevokeBase;
    r.revokeJitter = gDefRevokeJitter;
    r.stepBase = gDefStepBase;
    r.stepJitter = gDefStepJitter;
    r.imgPath = gDefImgPath;
    r.cardName = gDefCardName;
    return r;
}

// ====== 配置读写 ======

void loadConfig() {
    try {
        SharedPreferences sp = context.getSharedPreferences(PREFS_NAME, 0);
        gCloudUrl = sp.getString("cloudUrl", "https://raw.githubusercontent.com/lil1552310991-creator/ciku/main");
        gDefGroupId = sp.getString("defGroupId", "0");
        gDefStepBase = sp.getLong("defStepBase", 4000);
        gDefStepJitter = sp.getLong("defStepJitter", 2000);
        gDefMuteBase = sp.getLong("defMuteBase", 40);
        gDefMuteJitter = sp.getLong("defMuteJitter", 20);
        gDefRevokeBase = sp.getLong("defRevokeBase", 20);
        gDefRevokeJitter = sp.getLong("defRevokeJitter", 10);
        gDefImgPath = sp.getString("defImgPath", "");
        gDefCardName = sp.getString("defCardName", "一条死狗");

        gTargets.clear();
        String raw = sp.getString("targets", "");
        if (raw != null && raw.length() > 0) {
            String[] rules = raw.split(";");
            for (int i = 0; i < rules.length; i++) {
                String[] parts = rules[i].split("\\|");
                // v4.2格式: uin|groupId|6开关|muteBase|muteJitter|revokeBase|revokeJitter|stepBase|stepJitter|imgPath|cardName (16字段)
                if (parts.length >= 16) {
                    TargetRule r = new TargetRule();
                    r.uin = parts[0];
                    r.groupId = parts[1].length() > 0 ? parts[1] : "0";
                    r.emoji = "1".equals(parts[2]);
                    r.mute = "1".equals(parts[3]);
                    r.insult = "1".equals(parts[4]);
                    r.image = "1".equals(parts[5]);
                    r.revoke = "1".equals(parts[6]);
                    r.nick = "1".equals(parts[7]);
                    r.muteBase = Long.parseLong(parts[8]);
                    r.muteJitter = Long.parseLong(parts[9]);
                    r.revokeBase = Long.parseLong(parts[10]);
                    r.revokeJitter = Long.parseLong(parts[11]);
                    r.stepBase = Long.parseLong(parts[12]);
                    r.stepJitter = Long.parseLong(parts[13]);
                    r.imgPath = parts[14];
                    r.cardName = parts[15].length() > 0 ? parts[15] : gDefCardName;
                    gTargets.add(r);
                } else if (parts.length >= 15) {
                    // 兼容v4.0/v4.1: 无groupId字段，默认"0"
                    TargetRule r = new TargetRule();
                    r.uin = parts[0];
                    r.groupId = "0";
                    r.emoji = "1".equals(parts[1]);
                    r.mute = "1".equals(parts[2]);
                    r.insult = "1".equals(parts[3]);
                    r.image = "1".equals(parts[4]);
                    r.revoke = "1".equals(parts[5]);
                    r.nick = "1".equals(parts[6]);
                    r.muteBase = Long.parseLong(parts[7]);
                    r.muteJitter = Long.parseLong(parts[8]);
                    r.revokeBase = Long.parseLong(parts[9]);
                    r.revokeJitter = Long.parseLong(parts[10]);
                    r.stepBase = Long.parseLong(parts[11]);
                    r.stepJitter = Long.parseLong(parts[12]);
                    r.imgPath = parts[13];
                    r.cardName = parts[14].length() > 0 ? parts[14] : gDefCardName;
                    gTargets.add(r);
                } else if (parts.length >= 11) {
                    // 兼容v3旧格式: uin|开关|muteMin|muteMax|revokeMin|revokeMax
                    TargetRule r = new TargetRule();
                    r.uin = parts[0];
                    r.emoji = "1".equals(parts[1]);
                    r.mute = "1".equals(parts[2]);
                    r.insult = "1".equals(parts[3]);
                    r.image = "1".equals(parts[4]);
                    r.revoke = "1".equals(parts[5]);
                    r.nick = "1".equals(parts[6]);
                    long oldMuteMin = Long.parseLong(parts[7]);
                    long oldMuteMax = Long.parseLong(parts[8]);
                    r.muteBase = (oldMuteMin + oldMuteMax) / 2;
                    r.muteJitter = (oldMuteMax - oldMuteMin) / 2;
                    long oldRevokeMin = Long.parseLong(parts[9]);
                    long oldRevokeMax = Long.parseLong(parts[10]);
                    r.revokeBase = (oldRevokeMin + oldRevokeMax) / 2;
                    r.revokeJitter = (oldRevokeMax - oldRevokeMin) / 2;
                    r.stepBase = gDefStepBase;
                    r.stepJitter = gDefStepJitter;
                    r.imgPath = gDefImgPath;
                    r.cardName = gDefCardName;
                    gTargets.add(r);
                } else if (parts.length >= 5) {
                    // 兼容更旧格式
                    TargetRule r = makeDefaultRule(parts[0]);
                    long oldMuteMin2 = Long.parseLong(parts[1]);
                    long oldMuteMax2 = Long.parseLong(parts[2]);
                    r.muteBase = (oldMuteMin2 + oldMuteMax2) / 2;
                    r.muteJitter = (oldMuteMax2 - oldMuteMin2) / 2;
                    long oldRevokeMin2 = Long.parseLong(parts[3]);
                    long oldRevokeMax2 = Long.parseLong(parts[4]);
                    r.revokeBase = (oldRevokeMin2 + oldRevokeMax2) / 2;
                    r.revokeJitter = (oldRevokeMax2 - oldRevokeMin2) / 2;
                    gTargets.add(r);
                }
            }
        }
        if (gTargets.size() == 0) {
            gTargets.add(makeDefaultRule("2024997386"));
            saveConfig();
        }
        triggerCount = sp.getInt("triggerCount", 0);
    } catch (Throwable t) {
        error("加载配置失败: " + t);
    }
}

void saveConfig() {
    try {
        SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, 0).edit();
        ed.putString("cloudUrl", gCloudUrl);
        ed.putString("defGroupId", gDefGroupId);
        ed.putLong("defStepBase", gDefStepBase);
        ed.putLong("defStepJitter", gDefStepJitter);
        ed.putLong("defMuteBase", gDefMuteBase);
        ed.putLong("defMuteJitter", gDefMuteJitter);
        ed.putLong("defRevokeBase", gDefRevokeBase);
        ed.putLong("defRevokeJitter", gDefRevokeJitter);
        ed.putString("defImgPath", gDefImgPath);
        ed.putString("defCardName", gDefCardName);

        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < gTargets.size(); i++) {
            TargetRule r = (TargetRule) gTargets.get(i);
            if (sb.length() > 0) sb.append(";");
            sb.append(r.uin).append("|")
              .append(r.groupId != null ? r.groupId : "0").append("|")
              .append(r.emoji ? "1" : "0").append("|")
              .append(r.mute ? "1" : "0").append("|")
              .append(r.insult ? "1" : "0").append("|")
              .append(r.image ? "1" : "0").append("|")
              .append(r.revoke ? "1" : "0").append("|")
              .append(r.nick ? "1" : "0").append("|")
              .append(r.muteBase).append("|")
              .append(r.muteJitter).append("|")
              .append(r.revokeBase).append("|")
              .append(r.revokeJitter).append("|")
              .append(r.stepBase).append("|")
              .append(r.stepJitter).append("|")
              .append(r.imgPath != null ? r.imgPath : "").append("|")
              .append(r.cardName != null ? r.cardName : "");
        }
        ed.putString("targets", sb.toString());
        ed.putInt("triggerCount", triggerCount);
        ed.commit();
    } catch (Throwable t) {
        error("保存配置失败: " + t);
    }
}

// ====== UI工具函数 ======

int dp(Object ctx, int d) {
    try { return (int)(d * ((android.content.Context)ctx).getResources().getDisplayMetrics().density + 0.5f); } catch (Throwable t) { return d; }
}

GradientDrawable roundRect(int color, int r) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(r);
    return d;
}

TextView makeLabel(Object ctx, String txt) {
    TextView tv = new TextView(ctx);
    tv.setText(txt); tv.setTextSize(13); tv.setTextColor(Color.BLACK);
    tv.setPadding(0, dp(ctx, 6), 0, dp(ctx, 2));
    return tv;
}

TextView makeSubTitle(Object ctx, String text, int color) {
    TextView tv = new TextView(ctx);
    tv.setText(text); tv.setTextSize(12);
    tv.setTextColor(color);
    tv.setPadding(0, dp(ctx, 15), 0, dp(ctx, 5));
    return tv;
}

EditText makeInput(Object ctx, String hint, String val) {
    EditText et = new EditText(ctx);
    et.setHint(hint); et.setText(val); et.setTextSize(13);
    et.setTextColor(Color.BLACK);
    et.setBackground(roundRect(Color.parseColor("#F7F8FA"), dp(ctx, 6)));
    et.setPadding(dp(ctx, 10), dp(ctx, 8), dp(ctx, 10), dp(ctx, 8));
    return et;
}

TextView makeActionBtn(Object ctx, String text, int textColor, int bgColor) {
    TextView tv = new TextView(ctx);
    tv.setText(text); tv.setTextSize(14); tv.setTextColor(textColor);
    tv.setGravity(17);
    tv.setBackground(roundRect(bgColor, dp(ctx, 8)));
    return tv;
}

TextView makeSwitchBtn(Object ctx, boolean on, int colorPrimary) {
    TextView tv = new TextView(ctx);
    tv.setText(on ? "✓" : "✗"); tv.setTextSize(13);
    tv.setTextColor(on ? Color.WHITE : Color.parseColor("#FF4444"));
    tv.setPadding(dp(ctx, 9), dp(ctx, 3), dp(ctx, 9), dp(ctx, 3));
    tv.setGravity(17);
    tv.setBackground(on ? roundRect(colorPrimary, dp(ctx, 20)) : roundRect(Color.parseColor("#EEEEEE"), dp(ctx, 20)));
    return tv;
}

void updateSwitch(TextView tv, boolean on, int colorPrimary) {
    tv.setText(on ? "✓" : "✗");
    tv.setTextColor(on ? Color.WHITE : Color.parseColor("#FF4444"));
    tv.setBackground(on ? roundRect(colorPrimary, dp(tv.getContext(), 20)) : roundRect(Color.parseColor("#EEEEEE"), dp(tv.getContext(), 20)));
}

long parseLong(String s, long def) {
    try { return Long.parseLong(s); } catch (Throwable t) { return def; }
}

// ====== 群员检索弹窗（简化版：列表多选） ======
void showMemberPicker(final EditText targetInput) {
    final Activity act = getThreadActivity();
    if (act == null) return;
    act.runOnUiThread(new Runnable() {
        public void run() {
            try {
                int blue = Color.parseColor("#3B71FE");
                Dialog d = new Dialog(act);
                d.requestWindowFeature(1);
                d.getWindow().setBackgroundDrawable(new ColorDrawable(0));

                LinearLayout card = new LinearLayout(act);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(roundRect(Color.WHITE, dp(act, 14)));
                card.setPadding(dp(act, 14), dp(act, 14), dp(act, 14), dp(act, 14));

                TextView title = new TextView(act);
                title.setText("从群聊选择成员（勾选=添加）"); title.setTextSize(14);
                title.setTextColor(Color.BLACK); title.getPaint().setFakeBoldText(true);
                card.addView(title);

                final ScrollView scrollView = new ScrollView(act);
                final LinearLayout memberList = new LinearLayout(act);
                memberList.setOrientation(LinearLayout.VERTICAL);
                scrollView.addView(memberList);
                LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(-1, dp(act, 320));
                card.addView(scrollView, lpScroll);

                final List selectedUins = new ArrayList();

                // 加载成员
                new Thread(new Runnable() {
                    public void run() {
                        final List allData = new ArrayList();
                        List allGroups = groups();
                        if (allGroups != null) {
                            for (int g = 0; g < allGroups.size(); g++) {
                                Map grp = (Map) allGroups.get(g);
                                String gid = (String) grp.get("group");
                                if (gid == null) continue;
                                List ml = members(gid, false);
                                if (ml == null) continue;
                                for (int i = 0; i < ml.size(); i++) {
                                    Object m = ml.get(i);
                                    String uin = getMemberUin(m);
                                    if (uin == null) continue;
                                    String nick = getDisplayNick(m);
                                    if (nick == null || nick.length() == 0) nick = uin;
                                    allData.add(nick + "|" + uin);
                                }
                            }
                        }
                        act.runOnUiThread(new Runnable() {
                            public void run() {
                                memberList.removeAllViews();
                                for (int i = 0; i < allData.size(); i++) {
                                    String[] parts = ((String) allData.get(i)).split("\\|");
                                    if (parts.length < 2) continue;
                                    final String uin = parts[1];
                                    String nick = parts[0];

                                    LinearLayout row = new LinearLayout(act);
                                    row.setOrientation(LinearLayout.HORIZONTAL);
                                    row.setGravity(Gravity.CENTER_VERTICAL);
                                    row.setPadding(0, dp(act, 3), 0, dp(act, 3));

                                    final TextView cb = makeSwitchBtn(act, false, blue);
                                    cb.setOnClickListener(new View.OnClickListener() {
                                        public void onClick(View v) {
                                            boolean now = !selectedUins.contains(uin);
                                            if (now) selectedUins.add(uin);
                                            else selectedUins.remove(uin);
                                            updateSwitch((TextView) v, now, blue);
                                        }
                                    });
                                    row.addView(cb);

                                    TextView lb = new TextView(act);
                                    lb.setText(" " + nick + " (" + uin + ")");
                                    lb.setTextSize(11); lb.setTextColor(Color.BLACK);
                                    row.addView(lb, new LinearLayout.LayoutParams(0, -2, 1));
                                    memberList.addView(row);
                                }
                            }
                        });
                    }
                }).start();

                // 确认
                TextView btnConfirm = makeActionBtn(act, "确认选择 (" + selectedUins.size() + "人)", Color.WHITE, blue);
                btnConfirm.setPadding(0, dp(act, 10), 0, dp(act, 10));
                LinearLayout.LayoutParams lpConfirm = new LinearLayout.LayoutParams(-1, -2);
                lpConfirm.topMargin = dp(act, 8);
                card.addView(btnConfirm, lpConfirm);
                btnConfirm.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < selectedUins.size(); i++) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append((String) selectedUins.get(i));
                        }
                        if (sb.length() > 0) {
                            targetInput.setText(sb.toString());
                            Toast("已选择 " + selectedUins.size() + " 人");
                        }
                        d.dismiss();
                    }
                });

                d.setContentView(card);
                d.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.9), dp(act, 420));
                d.show();
            } catch (Throwable t) {
                error("群员检索错误: " + t);
            }
        }
    });
}

// ====== 编辑目标用户弹窗 ======
void editTargetDialog(String editUin, Runnable onSaved) {
    // 找到目标用户
    TargetRule editRule = null;
    for (int i = 0; i < gTargets.size(); i++) {
        TargetRule r = (TargetRule) gTargets.get(i);
        if (r.uin.equals(editUin)) { editRule = r; break; }
    }
    if (editRule == null) return;
    final TargetRule fRule = editRule;
    final Activity act = getThreadActivity();
    if (act == null) return;

    act.runOnUiThread(new Runnable() {
        public void run() {
            try {
                int blue = Color.parseColor("#3B71FE");
                Dialog d = new Dialog(act);
                d.requestWindowFeature(1);
                d.getWindow().setBackgroundDrawable(new ColorDrawable(0));

                LinearLayout card = new LinearLayout(act);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(roundRect(Color.WHITE, dp(act, 14)));
                card.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));

                TextView title = new TextView(act);
                title.setText("编辑 " + fRule.uin); title.setTextSize(15);
                title.setTextColor(Color.BLACK); title.getPaint().setFakeBoldText(true);
                card.addView(title);

                card.addView(makeLabel(act, "生效群号 (留空=所有群)"));
                final EditText edGroup = makeInput(act, "留空=所有群", fRule.groupId);
                card.addView(edGroup);

                card.addView(makeLabel(act, "禁言配置 (秒) — 标准 ± 浮动"));
                LinearLayout row1 = new LinearLayout(act); row1.setOrientation(LinearLayout.HORIZONTAL);
                final EditText edMuteB = makeInput(act, "标准", String.valueOf(fRule.muteBase));
                final EditText edMuteJ = makeInput(act, "浮动±", String.valueOf(fRule.muteJitter));
                row1.addView(edMuteB, new LinearLayout.LayoutParams(0, -2, 1));
                row1.addView(edMuteJ, new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(row1);

                card.addView(makeLabel(act, "撤回延迟 (秒) — 标准 ± 浮动"));
                LinearLayout row2 = new LinearLayout(act); row2.setOrientation(LinearLayout.HORIZONTAL);
                final EditText edRevB = makeInput(act, "标准", String.valueOf(fRule.revokeBase));
                final EditText edRevJ = makeInput(act, "浮动±", String.valueOf(fRule.revokeJitter));
                row2.addView(edRevB, new LinearLayout.LayoutParams(0, -2, 1));
                row2.addView(edRevJ, new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(row2);

                card.addView(makeLabel(act, "连招间隔 (毫秒) — 标准 ± 浮动"));
                LinearLayout row3 = new LinearLayout(act); row3.setOrientation(LinearLayout.HORIZONTAL);
                final EditText edStepB = makeInput(act, "标准", String.valueOf(fRule.stepBase));
                final EditText edStepJ = makeInput(act, "浮动±", String.valueOf(fRule.stepJitter));
                row3.addView(edStepB, new LinearLayout.LayoutParams(0, -2, 1));
                row3.addView(edStepJ, new LinearLayout.LayoutParams(0, -2, 1));
                card.addView(row3);

                card.addView(makeLabel(act, "图片路径"));
                final EditText edImg = makeInput(act, "留空不发图", fRule.imgPath != null ? fRule.imgPath : "");
                card.addView(edImg);

                card.addView(makeLabel(act, "群昵称锁定"));
                final EditText edCard = makeInput(act, "强制名片", fRule.cardName);
                card.addView(edCard);

                // 保存按钮
                TextView btnSave = makeActionBtn(act, "保存修改", Color.WHITE, blue);
                btnSave.setPadding(0, dp(act, 10), 0, dp(act, 10));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                lp.topMargin = dp(act, 12);
                card.addView(btnSave, lp);
                btnSave.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        fRule.groupId = edGroup.getText().toString().trim();
                        if (fRule.groupId.length() == 0) fRule.groupId = "0";
                        fRule.muteBase = parseLong(edMuteB.getText().toString().trim(), 40);
                        fRule.muteJitter = parseLong(edMuteJ.getText().toString().trim(), 20);
                        fRule.revokeBase = parseLong(edRevB.getText().toString().trim(), 20);
                        fRule.revokeJitter = parseLong(edRevJ.getText().toString().trim(), 10);
                        fRule.stepBase = parseLong(edStepB.getText().toString().trim(), 4000);
                        fRule.stepJitter = parseLong(edStepJ.getText().toString().trim(), 2000);
                        fRule.imgPath = edImg.getText().toString().trim();
                        fRule.cardName = edCard.getText().toString().trim();
                        if (fRule.cardName.length() == 0) fRule.cardName = gDefCardName;
                        if (onSaved != null) onSaved.run();
                        Toast("已保存 " + fRule.uin);
                        d.dismiss();
                    }
                });

                d.setContentView(card);
                d.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.85), -2);
                d.show();
            } catch (Throwable t) {
                error("编辑弹窗错误: " + t);
            }
        }
    });
}

// ====== 控制台 ======

void showConsole(String a, String b, int c) {
    Object act = getThreadActivity();
    if (act == null) return;
    final String actStr = String.valueOf(System.currentTimeMillis());
    act.runOnUiThread(new Runnable() {
        public void run() {
            try {
                int blue = Color.parseColor("#3B71FE");
                int bg = Color.parseColor("#FFFFFF");
                int grey = Color.parseColor("#F7F8FA");
                int red = Color.parseColor("#FF5555");

                Dialog d = new Dialog(act);
                d.requestWindowFeature(1);
                d.getWindow().setBackgroundDrawable(new ColorDrawable(0));

                FrameLayout outer = new FrameLayout(act);
                int m = dp(act, 16); outer.setPadding(m, m, m, m);

                LinearLayout card = new LinearLayout(act);
                card.setOrientation(LinearLayout.VERTICAL);
                card.setBackground(roundRect(bg, dp(act, 14)));
                card.setPadding(dp(act, 16), dp(act, 16), dp(act, 16), dp(act, 16));

                ScrollView scroll = new ScrollView(act);
                scroll.addView(card);
                outer.addView(scroll);

                // 标题
                TextView title = new TextView(act);
                title.setText("野狗阉割台 v4.3"); title.setTextSize(17);
                title.setTextColor(Color.BLACK); title.getPaint().setFakeBoldText(true);
                card.addView(title);

                // === 云端词库 ===
                card.addView(makeSubTitle(act, "云端词库", blue));
                EditText inCloudUrl = makeInput(act, "词库地址", gCloudUrl);
                card.addView(inCloudUrl);
                TextView btnSync = makeActionBtn(act, "同步云端词库", Color.WHITE, Color.parseColor("#FF6600"));
                btnSync.setPadding(0, dp(act, 8), 0, dp(act, 8));
                LinearLayout.LayoutParams lpSync2 = new LinearLayout.LayoutParams(-1, -2);
                lpSync2.topMargin = dp(act, 4);
                card.addView(btnSync, lpSync2);
                btnSync.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        gCloudUrl = inCloudUrl.getText().toString().trim();
                        if (gCloudUrl.length() == 0) { Toast("请先填写云端词库地址"); return; }
                        syncCikuFromCloud();
                    }
                });
                TextView cikuInfo = new TextView(act);
                cikuInfo.setText("词库: 文前" + gPreTextPool.size() + " 文后" + gPostTextPool.size() + " 图前" + gPreImgPool.size() + " 图后" + gPostImgPool.size());
                cikuInfo.setTextSize(10); cikuInfo.setTextColor(Color.GRAY);
                cikuInfo.setPadding(0, dp(act, 2), 0, 0);
                card.addView(cikuInfo);

                // === 添加目标用户（折叠面板） ===
                final TextView btnToggleAdd = makeActionBtn(act, "＋ 添加目标用户", Color.WHITE, Color.parseColor("#888888"));
                btnToggleAdd.setPadding(0, dp(act, 8), 0, dp(act, 8));
                LinearLayout.LayoutParams lpTog = new LinearLayout.LayoutParams(-1, -2);
                lpTog.topMargin = dp(act, 6);
                card.addView(btnToggleAdd, lpTog);

                final LinearLayout addPanel = new LinearLayout(act);
                addPanel.setOrientation(LinearLayout.VERTICAL);
                addPanel.setVisibility(View.GONE);
                card.addView(addPanel);

                addPanel.addView(makeSubTitle(act, "添加目标用户", Color.GRAY));

                // 生效群号
                addPanel.addView(makeLabel(act, "生效群号"));
                EditText inGroupId = makeInput(act, "留空 = 所有群生效", gDefGroupId);
                addPanel.addView(inGroupId);
                TextView grpHint = new TextView(act);
                grpHint.setText("  留空对所有群生效，填具体群号仅该群生效");
                grpHint.setTextSize(10); grpHint.setTextColor(Color.GRAY);
                addPanel.addView(grpHint);

                // 目标QQ号（支持逗号分隔批量）
                addPanel.addView(makeLabel(act, "目标QQ号 (逗号分隔可批量添加)"));
                EditText inUin = makeInput(act, "多个QQ用逗号分隔，如: 123,456,789", "");
                addPanel.addView(inUin);

                // 群员检索
                TextView btnMemberSearch = makeActionBtn(act, "🔍 从当前群检索群员", Color.WHITE, Color.parseColor("#666666"));
                btnMemberSearch.setPadding(0, dp(act, 6), 0, dp(act, 6));
                LinearLayout.LayoutParams lpSearch = new LinearLayout.LayoutParams(-1, -2);
                lpSearch.topMargin = dp(act, 4);
                addPanel.addView(btnMemberSearch, lpSearch);
                btnMemberSearch.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        showMemberPicker(inUin);
                    }
                });

                addPanel.addView(makeLabel(act, "禁言配置 (秒) — 标准 ± 浮动"));
                LinearLayout addMute = new LinearLayout(act);
                addMute.setOrientation(LinearLayout.HORIZONTAL);
                EditText inMuteBase = makeInput(act, "标准 " + gDefMuteBase, String.valueOf(gDefMuteBase));
                EditText inMuteJitter = makeInput(act, "浮动 ±" + gDefMuteJitter, String.valueOf(gDefMuteJitter));
                addMute.addView(inMuteBase, new LinearLayout.LayoutParams(0, -2, 1));
                addMute.addView(inMuteJitter, new LinearLayout.LayoutParams(0, -2, 1));
                addPanel.addView(addMute);

                addPanel.addView(makeLabel(act, "撤回延迟 (秒) — 标准 ± 浮动"));
                LinearLayout addRevoke = new LinearLayout(act);
                addRevoke.setOrientation(LinearLayout.HORIZONTAL);
                EditText inRevokeBase = makeInput(act, "标准 " + gDefRevokeBase, String.valueOf(gDefRevokeBase));
                EditText inRevokeJitter = makeInput(act, "浮动 ±" + gDefRevokeJitter, String.valueOf(gDefRevokeJitter));
                addRevoke.addView(inRevokeBase, new LinearLayout.LayoutParams(0, -2, 1));
                addRevoke.addView(inRevokeJitter, new LinearLayout.LayoutParams(0, -2, 1));
                addPanel.addView(addRevoke);

                addPanel.addView(makeLabel(act, "连招间隔 (毫秒) — 标准 ± 浮动"));
                LinearLayout addStep = new LinearLayout(act);
                addStep.setOrientation(LinearLayout.HORIZONTAL);
                EditText inStepBase = makeInput(act, "标准 " + gDefStepBase, String.valueOf(gDefStepBase));
                EditText inStepJitter = makeInput(act, "浮动 ±" + gDefStepJitter, String.valueOf(gDefStepJitter));
                addStep.addView(inStepBase, new LinearLayout.LayoutParams(0, -2, 1));
                addStep.addView(inStepJitter, new LinearLayout.LayoutParams(0, -2, 1));
                addPanel.addView(addStep);

                addPanel.addView(makeLabel(act, "图片路径 (留空不发送图片)"));
                EditText inImgPath2 = makeInput(act, "图片路径", gDefImgPath);
                addPanel.addView(inImgPath2);

                addPanel.addView(makeLabel(act, "群昵称锁定 (名片守护用)"));
                EditText inCardName = makeInput(act, "强制名片内容", gDefCardName);
                addPanel.addView(inCardName);

                addPanel.addView(makeLabel(act, "功能开关"));
                String[] addSwNames = new String[]{"表情", "禁言", "辱骂", "发图", "撤回", "名片"};
                final boolean[] addSw = new boolean[]{true, true, true, true, true, true};

                LinearLayout addSwRow1 = new LinearLayout(act);
                addSwRow1.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 0; i < 3; i++) {
                    final int idx = i;
                    LinearLayout swItem = new LinearLayout(act);
                    swItem.setOrientation(LinearLayout.HORIZONTAL);
                    swItem.setGravity(Gravity.CENTER_VERTICAL);
                    TextView swBtn = makeSwitchBtn(act, addSw[i], blue);
                    swBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            addSw[idx] = !addSw[idx];
                            updateSwitch((TextView) v, addSw[idx], blue);
                        }
                    });
                    TextView lb = new TextView(act);
                    lb.setText(" " + addSwNames[i]); lb.setTextSize(10); lb.setTextColor(Color.BLACK);
                    swItem.addView(swBtn); swItem.addView(lb);
                    addSwRow1.addView(swItem, new LinearLayout.LayoutParams(0, -2, 1));
                }
                addPanel.addView(addSwRow1);

                LinearLayout addSwRow2 = new LinearLayout(act);
                addSwRow2.setOrientation(LinearLayout.HORIZONTAL);
                for (int i = 3; i < 6; i++) {
                    final int idx = i;
                    LinearLayout swItem = new LinearLayout(act);
                    swItem.setOrientation(LinearLayout.HORIZONTAL);
                    swItem.setGravity(Gravity.CENTER_VERTICAL);
                    TextView swBtn = makeSwitchBtn(act, addSw[i], blue);
                    swBtn.setOnClickListener(new View.OnClickListener() {
                        public void onClick(View v) {
                            addSw[idx] = !addSw[idx];
                            updateSwitch((TextView) v, addSw[idx], blue);
                        }
                    });
                    TextView lb = new TextView(act);
                    lb.setText(" " + addSwNames[i]); lb.setTextSize(10); lb.setTextColor(Color.BLACK);
                    swItem.addView(swBtn); swItem.addView(lb);
                    addSwRow2.addView(swItem, new LinearLayout.LayoutParams(0, -2, 1));
                }
                addPanel.addView(addSwRow2);

                // 添加按钮
                TextView btnAdd = makeActionBtn(act, "确认添加", Color.WHITE, blue);
                btnAdd.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String uinRaw = inUin.getText().toString().trim();
                        if (uinRaw.length() == 0) { Toast("QQ号不能为空"); return; }
                        // 按逗号分割，支持批量
                        String[] uinArr = uinRaw.split(",");
                        int added = 0;
                        String grpId = inGroupId.getText().toString().trim();
                        for (int k = 0; k < uinArr.length; k++) {
                            String u = uinArr[k].trim();
                            if (u.length() == 0) continue;
                            // 检查重复
                            boolean dup = false;
                            for (int i = 0; i < gTargets.size(); i++) {
                                if (((TargetRule) gTargets.get(i)).uin.equals(u)) { dup = true; break; }
                            }
                            if (dup) continue;
                            TargetRule r = new TargetRule();
                            r.uin = u;
                            r.groupId = grpId;
                            r.emoji = addSw[0]; r.mute = addSw[1]; r.insult = addSw[2];
                            r.image = addSw[3]; r.revoke = addSw[4]; r.nick = addSw[5];
                            r.muteBase = parseLong(inMuteBase.getText().toString().trim(), gDefMuteBase);
                            r.muteJitter = parseLong(inMuteJitter.getText().toString().trim(), gDefMuteJitter);
                            r.revokeBase = parseLong(inRevokeBase.getText().toString().trim(), gDefRevokeBase);
                            r.revokeJitter = parseLong(inRevokeJitter.getText().toString().trim(), gDefRevokeJitter);
                            r.stepBase = parseLong(inStepBase.getText().toString().trim(), gDefStepBase);
                            r.stepJitter = parseLong(inStepJitter.getText().toString().trim(), gDefStepJitter);
                            r.imgPath = inImgPath2.getText().toString().trim();
                            r.cardName = inCardName.getText().toString().trim();
                            if (r.cardName.length() == 0) r.cardName = gDefCardName;
                            gTargets.add(r);
                            added++;
                        }
                        inUin.setText("");
                        refreshTargets2.run();
                        addPanel.setVisibility(View.GONE);
                        btnToggleAdd.setText("＋ 添加目标用户");
                        Toast("已添加 " + added + " 人");
                    }
                });
                LinearLayout.LayoutParams lpAdd = new LinearLayout.LayoutParams(-1, dp(act, 36));
                lpAdd.topMargin = dp(act, 8);
                addPanel.addView(btnAdd, lpAdd);

                // 折叠面板切换逻辑
                btnToggleAdd.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        if (addPanel.getVisibility() == View.GONE) {
                            addPanel.setVisibility(View.VISIBLE);
                            btnToggleAdd.setText("－ 收起添加面板");
                            btnToggleAdd.setBackground(roundRect(blue, dp(act, 8)));
                            btnToggleAdd.setTextColor(Color.WHITE);
                        } else {
                            addPanel.setVisibility(View.GONE);
                            btnToggleAdd.setText("＋ 添加目标用户");
                            btnToggleAdd.setBackground(roundRect(Color.parseColor("#888888"), dp(act, 8)));
                            btnToggleAdd.setTextColor(Color.WHITE);
                        }
                    }
                });

                // === 目标用户列表 ===
                card.addView(makeSubTitle(act, "目标用户列表 (共 " + gTargets.size() + " 人)", red));

                final LinearLayout targetList = new LinearLayout(act);
                targetList.setOrientation(LinearLayout.VERTICAL);
                card.addView(targetList);

                Runnable refreshTargets2 = new Runnable() {
                    public void run() {
                        targetList.removeAllViews();
                        if (gTargets.size() == 0) {
                            TextView empty = new TextView(act);
                            empty.setText("暂无目标用户"); empty.setTextColor(Color.LTGRAY); empty.setTextSize(12);
                            targetList.addView(empty);
                            return;
                        }
                        for (int i = 0; i < gTargets.size(); i++) {
                            TargetRule r = (TargetRule) gTargets.get(i);
                            final String delUin = r.uin;

                            LinearLayout item = new LinearLayout(act);
                            item.setOrientation(LinearLayout.VERTICAL);
                            item.setBackground(roundRect(grey, dp(act, 7)));
                            item.setPadding(dp(act, 8), dp(act, 6), dp(act, 8), dp(act, 6));

                            // QQ号 + 删除
                            LinearLayout row1 = new LinearLayout(act);
                            row1.setOrientation(LinearLayout.HORIZONTAL);
                            row1.setGravity(Gravity.CENTER_VERTICAL);
                            String grpInfo = (r.groupId == null || r.groupId.length() == 0 || r.groupId.equals("0")) ? "全群" : "群" + r.groupId;
                            TextView info = new TextView(act);
                            info.setText("QQ:" + r.uin + " [" + grpInfo + "] 禁" + r.muteBase + "±" + r.muteJitter + "s 撤" + r.revokeBase + "±" + r.revokeJitter + "s");
                            info.setTextSize(10); info.setTextColor(Color.BLACK);

                            TextView editBtn = new TextView(act);
                            editBtn.setText("编辑"); editBtn.setTextColor(blue); editBtn.setTextSize(11);
                            editBtn.setPadding(dp(act, 6), 0, dp(act, 6), 0);
                            editBtn.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    editTargetDialog(delUin, refreshTargets2);
                                }
                            });

                            TextView del = new TextView(act);
                            del.setText("删除"); del.setTextColor(red); del.setTextSize(11);
                            del.setPadding(dp(act, 6), 0, 0, 0);
                            del.setOnClickListener(new View.OnClickListener() {
                                public void onClick(View v) {
                                    for (int j = 0; j < gTargets.size(); j++) {
                                        if (((TargetRule) gTargets.get(j)).uin.equals(delUin)) {
                                            gTargets.remove(j);
                                            break;
                                        }
                                    }
                                    refreshTargets2.run();
                                }
                            });
                            row1.addView(info, new LinearLayout.LayoutParams(0, -2, 1));
                            row1.addView(editBtn);
                            row1.addView(del);
                            item.addView(row1);

                            // 开关
                            LinearLayout row2 = new LinearLayout(act);
                            row2.setOrientation(LinearLayout.HORIZONTAL);
                            row2.setPadding(0, dp(act, 3), 0, 0);
                            String[] swNames = new String[]{"表情", "禁言", "辱骂", "发图", "撤回", "名片"};
                            final boolean[] swVals = new boolean[]{r.emoji, r.mute, r.insult, r.image, r.revoke, r.nick};
                            for (int s = 0; s < swNames.length; s++) {
                                final int sIdx = s;
                                LinearLayout swItem = new LinearLayout(act);
                                swItem.setOrientation(LinearLayout.HORIZONTAL);
                                swItem.setGravity(Gravity.CENTER_VERTICAL);
                                TextView swBtn = makeSwitchBtn(act, swVals[s], blue);
                                swBtn.setOnClickListener(new View.OnClickListener() {
                                    public void onClick(View v) {
                                        swVals[sIdx] = !swVals[sIdx];
                                        TargetRule tr = (TargetRule) gTargets.get(i);
                                        if (sIdx == 0) tr.emoji = swVals[sIdx];
                                        else if (sIdx == 1) tr.mute = swVals[sIdx];
                                        else if (sIdx == 2) tr.insult = swVals[sIdx];
                                        else if (sIdx == 3) tr.image = swVals[sIdx];
                                        else if (sIdx == 4) tr.revoke = swVals[sIdx];
                                        else if (sIdx == 5) tr.nick = swVals[sIdx];
                                        updateSwitch((TextView) v, swVals[sIdx], blue);
                                    }
                                });
                                TextView lb = new TextView(act);
                                lb.setText(swNames[s]); lb.setTextSize(9); lb.setTextColor(Color.GRAY);
                                lb.setPadding(dp(act, 1), 0, dp(act, 4), 0);
                                swItem.addView(swBtn); swItem.addView(lb);
                                row2.addView(swItem);
                            }
                            item.addView(row2);

                            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
                            lp.bottomMargin = dp(act, 5);
                            targetList.addView(item, lp);
                        }
                    }
                };
                refreshTargets2.run();

                // === 保存 / 关闭 ===
                LinearLayout btnRow = new LinearLayout(act);
                btnRow.setOrientation(LinearLayout.HORIZONTAL);
                btnRow.setPadding(0, dp(act, 16), 0, 0);

                TextView btnSave = makeActionBtn(act, "保存并应用", Color.WHITE, blue);
                TextView btnClose = makeActionBtn(act, "关闭", Color.parseColor("#666666"), Color.parseColor("#EEEEEE"));

                btnSave.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        gCloudUrl = inCloudUrl.getText().toString().trim();
                        gDefGroupId = inGroupId.getText().toString().trim();
                        if (gDefGroupId.length() == 0) gDefGroupId = "0";
                        gDefStepBase = parseLong(inStepBase.getText().toString().trim(), 4000);
                        gDefStepJitter = parseLong(inStepJitter.getText().toString().trim(), 2000);
                        gDefMuteBase = parseLong(inMuteBase.getText().toString().trim(), 40);
                        gDefMuteJitter = parseLong(inMuteJitter.getText().toString().trim(), 20);
                        gDefRevokeBase = parseLong(inRevokeBase.getText().toString().trim(), 20);
                        gDefRevokeJitter = parseLong(inRevokeJitter.getText().toString().trim(), 10);
                        gDefImgPath = inImgPath2.getText().toString().trim();
                        gDefCardName = inCardName.getText().toString().trim();
                        if (gDefCardName.length() == 0) gDefCardName = "一条死狗";
                        saveConfig();
                        loadConfig();
                        loadCiku();
                        if (hasAnyNick() && (nickThread == null || !nickThread.isAlive())) startNickThread();
                        Toast("已保存 | 目标:" + gTargets.size() + "人");
                        d.dismiss();
                    }
                });

                btnClose.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) { d.dismiss(); }
                });

                btnRow.addView(btnSave, new LinearLayout.LayoutParams(0, dp(act, 38), 1));
                LinearLayout.LayoutParams lpClose = new LinearLayout.LayoutParams(0, dp(act, 38), 1);
                lpClose.leftMargin = dp(act, 10);
                btnRow.addView(btnClose, lpClose);
                card.addView(btnRow);

                d.setContentView(outer);
                d.getWindow().setLayout((int)(((android.content.Context)act).getResources().getDisplayMetrics().widthPixels * 0.9), -2);
                d.show();
            } catch (Throwable t) {
                error("控制台错误: " + t);
            }
        }
    });
}

// ====== 名片守护 ======

Thread nickThread = null;

boolean hasAnyNick() {
    for (int i = 0; i < gTargets.size(); i++) {
        if (((TargetRule) gTargets.get(i)).nick) return true;
    }
    return false;
}

void startNickThread() {
    nickThread = new Thread(new Runnable() {
        public void run() {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    fixNickIfNeeded();
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {}
            }
        }
    });
    nickThread.start();
}

void main() {
    loadConfig();
    loadCiku();
    if (hasAnyNick() && (nickThread == null || !nickThread.isAlive())) {
        startNickThread();
    }
    log("野狗阉割台已上膛 v4.2 | 触发:" + triggerCount + " | 目标:" + gTargets.size() + "人 | 词库:" + (gPreTextPool.size() + gPostTextPool.size() + gPreImgPool.size() + gPostImgPool.size()) + "条");
    // 词库未同步提示
    if (gPreTextPool.size() + gPostTextPool.size() + gPreImgPool.size() + gPostImgPool.size() == 0) {
        if (gCloudUrl == null || gCloudUrl.trim().length() == 0) {
            Toast("词库未同步！请在控制台填写云端词库地址后同步");
        } else {
            Toast("词库为空！请打开控制台点击「同步云端词库」");
        }
    }
}

void fixNickIfNeeded() {
    try {
        // 收集所有需要处理名片的目标uin
        List nickTargets = new ArrayList();
        for (int i = 0; i < gTargets.size(); i++) {
            TargetRule r = (TargetRule) gTargets.get(i);
            if (r.nick) nickTargets.add(r.uin);
        }
        if (nickTargets.size() == 0) return;

        // 收集所有目标用户的群号，有"0"则遍历全群
        List groupIds = new ArrayList();
        boolean hasAll = false;
        for (int k = 0; k < gTargets.size(); k++) {
            TargetRule tr = (TargetRule) gTargets.get(k);
            if (!tr.nick) continue;
            String gid = tr.groupId;
            if (gid == null || gid.length() == 0 || gid.equals("0")) { hasAll = true; break; }  // 空或0=全群
            if (!groupIds.contains(gid)) groupIds.add(gid);
        }
        if (hasAll || groupIds.size() == 0) {
            groupIds.clear();
            List allGroups = groups();
            if (allGroups != null) {
                for (int g = 0; g < allGroups.size(); g++) {
                    Map grp = (Map) allGroups.get(g);
                    String gid = (String) grp.get("group");
                    if (gid != null) groupIds.add(gid);
                }
            }
        }

        for (int g = 0; g < groupIds.size(); g++) {
            String gid = (String) groupIds.get(g);
            List memberList = members(gid, false);
            if (memberList == null || memberList.size() == 0) continue;
            for (int i = 0; i < memberList.size(); i++) {
                Object member = memberList.get(i);
                String uin = getMemberUin(member);
                boolean found = false;
                for (int j = 0; j < nickTargets.size(); j++) {
                    if (((String) nickTargets.get(j)).equals(uin)) { found = true; break; }
                }
                if (!found) continue;
                String nick = getMemberNick(member);
                if (nick == null) nick = "";
                // 查找该目标用户的强制名片
                String targetCard = gDefCardName;
                for (int k = 0; k < gTargets.size(); k++) {
                    TargetRule tr = (TargetRule) gTargets.get(k);
                    if (tr.nick && tr.uin.equals(uin)) {
                        targetCard = tr.cardName;
                        break;
                    }
                }
                if (!nick.equals(targetCard)) setCard(gid, uin, targetCard);
                break;
            }
        }
    } catch (Throwable t) {}
}

String getMemberUin(Object m) {
    try { return String.valueOf(m.getClass().getMethod("getMemberuin").invoke(m)); } catch (Throwable t) {}
    try { return String.valueOf(m.getClass().getField("memberuin").get(m)); } catch (Throwable t) {}
    return null;
}

String getMemberNick(Object m) {
    // 优先使用 nickInfo 对象获取群名片（新版 API）
    try {
        Object nickInfo = m.getClass().getField("nickInfo").get(m);
        if (nickInfo != null) {
            try {
                Object v = nickInfo.getClass().getField("troopnick").get(nickInfo);
                String s = (v == null) ? "" : String.valueOf(v);
                if (s.length() > 0) return s;
            } catch (Throwable t) {}
            try {
                Object v = nickInfo.getClass().getMethod("getTroopnick").invoke(nickInfo);
                String s = (v == null) ? "" : String.valueOf(v);
                if (s.length() > 0) return s;
            } catch (Throwable t) {}
        }
    } catch (Throwable t) {}
    // 回退到旧版 API
    try { Object v = m.getClass().getMethod("getTroopnick").invoke(m); return v == null ? "" : String.valueOf(v); } catch (Throwable t) {}
    try { Object v = m.getClass().getField("troopnick").get(m); return v == null ? "" : String.valueOf(v); } catch (Throwable t) {}
    return "";
}

// 获取成员的 QQ 昵称（非群名片，是账号原始昵称）
String getMemberQQNick(Object m) {
    // 优先从 nickInfo 获取
    try {
        Object nickInfo = m.getClass().getField("nickInfo").get(m);
        if (nickInfo != null) {
            try {
                Object v = nickInfo.getClass().getField("nick").get(nickInfo);
                if (v != null) { String s = String.valueOf(v); if (s.length() > 0) return s; }
            } catch (Throwable t) {}
            try {
                Object v = nickInfo.getClass().getMethod("getNick").invoke(nickInfo);
                if (v != null) { String s = String.valueOf(v); if (s.length() > 0) return s; }
            } catch (Throwable t) {}
        }
    } catch (Throwable t) {}
    // 尝试 autoremark（自动备注）
    try {
        Object v = m.getClass().getField("autoremark").get(m);
        if (v != null) { String s = String.valueOf(v); if (s.length() > 0) return s; }
    } catch (Throwable t) {}
    // 最后尝试 friendnick
    try {
        Object v = m.getClass().getField("friendnick").get(m);
        if (v != null) { String s = String.valueOf(v); if (s.length() > 0) return s; }
    } catch (Throwable t) {}
    return "";
}

// 获取成员的展示昵称：群名片优先，没有则取 QQ 昵称
String getDisplayNick(Object m) {
    String troopNick = getMemberNick(m);
    if (troopNick != null && troopNick.length() > 0) return troopNick;
    String qqNick = getMemberQQNick(m);
    if (qqNick != null && qqNick.length() > 0) return qqNick;
    return "";
}

// 根据 QQ 号查找成员的展示昵称（遍历群成员列表）
String lookupDisplayName(String uin) {
    try {
        List allGroups = groups();
        if (allGroups != null) {
            for (int g = 0; g < allGroups.size(); g++) {
                Map grp = (Map) allGroups.get(g);
                String gid = (String) grp.get("group");
                if (gid == null) continue;
                List memberList = members(gid, false);
                if (memberList == null || memberList.size() == 0) continue;
                for (int i = 0; i < memberList.size(); i++) {
                    Object member = memberList.get(i);
                    String muin = getMemberUin(member);
                    if (uin.equals(muin)) {
                        String nick = getDisplayNick(member);
                        if (nick != null && nick.length() > 0) return nick;
                        return uin;
                    }
                }
            }
        }
    } catch (Throwable t) {}
    return uin;
}

// 从词库池中随机抽取一条，池为空则用兜底词典
String getRandomCiku(List pool) {
    if (pool != null && pool.size() > 0) {
        return (String) pool.get((int)(Math.random() * pool.size()));
    }
    return FALLBACK_DICT[(int)(Math.random() * FALLBACK_DICT.length)];
}

// ====== 消息处理 ======

void onMsg(Object msg) {
    if (msg.chatType != 2) return;

    String sender = msg.senderUin;
    TargetRule rule = null;
    for (int i = 0; i < gTargets.size(); i++) {
        TargetRule r = (TargetRule) gTargets.get(i);
        if (r.uin.equals(sender)) {
            // 检查个人生效群号
            String rg = r.groupId;
            if (rg == null) rg = "";
            if (rg.length() > 0 && !rg.equals("0") && !rg.equals(msg.peerUid)) return;
            rule = r; break;
        }
    }
    if (rule == null) return;

    String groupId = msg.peerUid;
    boolean isImage = checkIsImage(msg);

    // 表情轰炸（独立开关）
    if (rule.emoji) {
        for (int i = 0; i < EMOJI_IDS.length; i++) {
            emoji(msg.msg, EMOJI_IDS[i], EMOJI_TYPE);
        }
    }

    // 禁言（独立开关）
    long muteSec = 0;
    if (rule.mute) {
        muteSec = rule.muteBase - rule.muteJitter + (long)(Math.random() * (rule.muteJitter * 2 + 1));
        if (muteSec < 1) muteSec = 1;
        mute(groupId, sender, muteSec);
    }

    // 撤回消息ID获取（独立开关）
    long targetMsgId = 0;
    if (rule.revoke) targetMsgId = getMsgId(msg);

    // 辱骂、发图、撤回需要新线程异步执行
    if (rule.insult || rule.image || rule.revoke) {
        final TargetRule fRule = rule;
        final Object fContact = msg.Contact;
        final String fSender = sender;
        final String fGroupId = groupId;
        final boolean fIsImage = isImage;
        final long fMsgId = targetMsgId;
        final long fMuteSec = muteSec;

        new Thread(new Runnable() {
            public void run() {
                // 辱骂连招（独立开关）
                if (fRule.insult) {
                    sleep(fRule.stepBase - fRule.stepJitter + (long)(Math.random() * (fRule.stepJitter * 2 + 1)));
                    if (fIsImage) {
                        send(fContact, "[atUin=" + fSender + "] " + getRandomCiku(gPreImgPool));
                    } else {
                        send(fContact, "[atUin=" + fSender + "] " + getRandomCiku(gPreTextPool));
                    }

                    sleep(fRule.stepBase - fRule.stepJitter + (long)(Math.random() * (fRule.stepJitter * 2 + 1)));
                    if (fIsImage) {
                        send(fContact, "[atUin=" + fSender + "] " + getRandomCiku(gPostImgPool));
                    } else {
                        send(fContact, "[atUin=" + fSender + "] " + getRandomCiku(gPostTextPool));
                    }
                }

                // 发送图片（独立开关）
                if (fRule.image && fRule.imgPath != null && fRule.imgPath.length() > 0 && exists(fRule.imgPath)) {
                    send(fContact, "[pic=" + fRule.imgPath + "]");
                }

                // 撤回消息（独立开关）
                if (fRule.revoke && fMsgId > 0) {
                    long ds = fRule.revokeBase - fRule.revokeJitter + (long)(Math.random() * (fRule.revokeJitter * 2 + 1));
                    if (ds < 1) ds = 1;
                    sleep(ds * 1000);
                    recall(fContact, new Long(fMsgId));
                }

                triggerCount = triggerCount + 1;
                try {
                    SharedPreferences.Editor ed = context.getSharedPreferences(PREFS_NAME, 0).edit();
                    ed.putInt("triggerCount", triggerCount);
                    ed.commit();
                } catch (Throwable t) {}
                log("第" + triggerCount + "次触发 | " + fSender + " | 缝嘴" + fMuteSec + "s" + (fMsgId > 0 ? " | 撤回OK" : ""));
            }
        }).start();
    }
}

boolean checkIsImage(Object msg) {
    try {
        java.lang.reflect.Field f = msg.getClass().getField("isImg");
        Object v = f.get(msg);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
    } catch (Throwable t) {}
    try {
        java.lang.reflect.Method m = msg.getClass().getMethod("isImg");
        Object v = m.invoke(msg);
        if (v instanceof Boolean) return ((Boolean) v).booleanValue();
    } catch (Throwable t) {}
    try {
        int tp = 0;
        try {
            tp = ((Integer) msg.getClass().getField("msgType").get(msg)).intValue();
        } catch (Throwable tt) {
            tp = ((Integer) msg.getClass().getMethod("getMsgType").invoke(msg)).intValue();
        }
        if (tp == 3 || tp == 7) return true;
        if (tp == 1) {
            String c = (String) msg.getClass().getField("Content").get(msg);
            if (c != null && (c.startsWith("/") || c.startsWith("http"))) return true;
        }
    } catch (Throwable t) {}
    return false;
}

long getMsgId(Object msg) {
    Object raw = null;
    try { raw = msg.msg; } catch (Throwable t) {}
    if (raw == null) raw = msg;

    String[] names = new String[]{"msgId", "shmsgseq", "uniseq", "msgSeq", "msgUid"};
    for (int i = 0; i < names.length; i++) {
        try {
            java.lang.reflect.Field f = raw.getClass().getDeclaredField(names[i]);
            f.setAccessible(true);
            long v = 0;
            if (f.getType() == Long.class) {
                Long lv = (Long) f.get(raw);
                if (lv != null) v = lv.longValue();
            } else {
                v = f.getLong(raw);
            }
            if (v > 0) return v;
        } catch (Throwable t) {}
    }

    String[] methods = new String[]{"getId", "getMsgId", "getShmsgseq"};
    for (int i = 0; i < methods.length; i++) {
        try {
            java.lang.reflect.Method m = raw.getClass().getMethod(methods[i]);
            Object ret = m.invoke(raw);
            if (ret instanceof Long) { long v = ((Long) ret).longValue(); if (v > 0) return v; }
            if (ret instanceof Integer) { long v = ((Integer) ret).longValue(); if (v > 0) return v; }
        } catch (Throwable t) {}
    }
    return -1;
}

void onUnload() {
    if (nickThread != null) {
        nickThread.interrupt();
        nickThread = null;
    }
}

addItem("⚙ 野狗阉割台控制台", "showConsole");
addMsgItem("手动糊表情", "manualEmoji");

void manualEmoji(Object msgRecord) {
    for (int i = 0; i < EMOJI_IDS.length; i++) {
        emoji(msgRecord, EMOJI_IDS[i], EMOJI_TYPE);
    }
}

main();
