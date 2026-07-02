// 生成一行开关（省代码）
void addSwitchBar(LinearLayout parent, String[] names, boolean[] vals, int from, int to, Object act, int blue) {
    LinearLayout row = new LinearLayout(act);
    row.setOrientation(LinearLayout.HORIZONTAL);
    for (int i = from; i < to; i++) {
        final int idx = i;
        LinearLayout sw = new LinearLayout(act);
        sw.setOrientation(LinearLayout.HORIZONTAL);
        sw.setGravity(Gravity.CENTER_VERTICAL);
        TextView btn = makeSwitchBtn(act, vals[i], blue);
        btn.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { vals[idx] = !vals[idx]; updateSwitch((TextView) v, vals[idx], blue); }
        });
        TextView lb = new TextView(act);
        lb.setText(" " + names[i]); lb.setTextSize(10); lb.setTextColor(Color.BLACK);
        sw.addView(btn); sw.addView(lb);
        row.addView(sw, new LinearLayout.LayoutParams(0, -2, 1));
    }
    parent.addView(row);
}

// ====== 群员检索弹窗 ======
void showMemberPicker(String groupUin, EditText targetInput) {
    final Activity act = getThreadActivity();
    if (act == null) return;
    final String fGroupUin = (groupUin != null) ? groupUin : "";
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

                // 非群聊提示
                if (fGroupUin.length() == 0 || fGroupUin.equals("0")) {
                    TextView warn = new TextView(act);
                    warn.setText("⚠️ 请在群聊中打开此功能\n私聊无法获取群成员列表"); warn.setTextSize(13);
                    warn.setTextColor(Color.parseColor("#FF6600")); warn.setGravity(Gravity.CENTER);
                    warn.setPadding(0, dp(act, 20), 0, dp(act, 20));
                    card.addView(warn);
                    TextView cls = makeActionBtn(act, "关闭", Color.WHITE, blue);
                    cls.setOnClickListener(new View.OnClickListener() { public void onClick(View v) { d.dismiss(); }});
                    card.addView(cls);
                    d.setContentView(card);
                    d.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.85), -2);
                    d.show();
                    return;
                }

                TextView title = new TextView(act);
                title.setText("群成员选择（仅本群成员）"); title.setTextSize(14);
                title.setTextColor(Color.BLACK); title.getPaint().setFakeBoldText(true);
                card.addView(title);

                // 搜索栏：输入框 + 按钮同一行
                LinearLayout searchBar = new LinearLayout(act);
                searchBar.setOrientation(LinearLayout.HORIZONTAL);
                final EditText searchEt = makeInput(act, "输入QQ/昵称搜索...", "");
                searchEt.setSingleLine(true);
                searchBar.addView(searchEt, new LinearLayout.LayoutParams(0, -2, 1));
                TextView doSearchBtn = new TextView(act);
                doSearchBtn.setText("搜索"); doSearchBtn.setTextColor(Color.WHITE); doSearchBtn.setTextSize(12);
                doSearchBtn.setBackground(roundRect(blue, dp(act, 6)));
                doSearchBtn.setPadding(dp(act, 10), dp(act, 8), dp(act, 10), dp(act, 8));
                doSearchBtn.setGravity(Gravity.CENTER);
                searchBar.addView(doSearchBtn);
                card.addView(searchBar);

                // 加载提示
                final TextView loadHint = new TextView(act);
                loadHint.setText("正在加载..."); loadHint.setTextSize(11);
                loadHint.setTextColor(Color.GRAY); loadHint.setPadding(0, dp(act, 3), 0, dp(act, 3));
                card.addView(loadHint);

                // 成员列表
                final ScrollView scrollView = new ScrollView(act);
                final LinearLayout memberList = new LinearLayout(act);
                memberList.setOrientation(LinearLayout.VERTICAL);
                scrollView.addView(memberList);
                LinearLayout.LayoutParams lpScroll = new LinearLayout.LayoutParams(-1, dp(act, 260));
                card.addView(scrollView, lpScroll);

                final List selectedUins = new ArrayList();
                final List allMembersData = new ArrayList();
                final int PAGE_SIZE = 80;

                // 后台加载当前群成员
                new Thread(new Runnable() {
                    public void run() {
                        allMembersData.clear();
                        // 找到正确的群号：遍历群列表匹配 UID 或群号
                        String realGid = fGroupUin;
                        List allGroups = groups();
                        if (allGroups != null && fGroupUin.length() > 0 && !fGroupUin.equals("0")) {
                            for (int g = 0; g < allGroups.size(); g++) {
                                Map grp = (Map) allGroups.get(g);
                                String gid = (String) grp.get("group");
                                if (gid == null) continue;
                                // 直接匹配群号，或匹配群信息中的UID
                                if (fGroupUin.equals(gid)) { realGid = gid; break; }
                            }
                        }
                        List ml = members(realGid, false);
                        if (ml != null) {
                            for (int i = 0; i < ml.size(); i++) {
                                Object m = ml.get(i);
                                String uin = getMemberUin(m);
                                if (uin == null) continue;
                                String nick = getDisplayNick(m);
                                if (nick == null || nick.length() == 0) nick = uin;
                                allMembersData.add(nick + "|" + uin);
                            }
                        }
                        act.runOnUiThread(new Runnable() {
                            public void run() {
                                loadHint.setText("共 " + allMembersData.size() + " 人（仅本群）");
                                renderMemberRows(memberList, allMembersData, selectedUins, "", act, blue, 0, PAGE_SIZE);
                            }
                        });
                    }
                }).start();

                // 搜索
                doSearchBtn.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        String kw = searchEt.getText().toString().trim();
                        renderMemberRows(memberList, allMembersData, selectedUins, kw, act, blue, 0, PAGE_SIZE);
                    }
                });

                // 确认按钮
                final TextView btnConfirm2 = new TextView(act);
                btnConfirm2.setText("确认选择"); btnConfirm2.setTextSize(15);
                btnConfirm2.setTextColor(Color.WHITE); btnConfirm2.setGravity(Gravity.CENTER);
                btnConfirm2.setBackground(roundRect(blue, dp(act, 8)));
                btnConfirm2.setPadding(0, dp(act, 14), 0, dp(act, 14));
                LinearLayout.LayoutParams lpConfirm = new LinearLayout.LayoutParams(-1, dp(act, 46));
                lpConfirm.topMargin = dp(act, 10);
                card.addView(btnConfirm2, lpConfirm);
                btnConfirm2.setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        StringBuilder sb = new StringBuilder();
                        for (int i = 0; i < selectedUins.size(); i++) {
                            if (sb.length() > 0) sb.append(",");
                            sb.append((String) selectedUins.get(i));
                        }
                        if (sb.length() > 0) {
                            targetInput.setText(sb.toString());
                        }
                        Toast("已选择 " + selectedUins.size() + " 人");
                        d.dismiss();
                    }
                });

                d.setContentView(card);
                d.getWindow().setLayout((int)(act.getResources().getDisplayMetrics().widthPixels * 0.9), dp(act, 440));
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

