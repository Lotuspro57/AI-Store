package com.nuruzzaman.multiai;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.ValueCallback;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.PopupMenu;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.webkit.WebViewFeature;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Multi AI for Android: a home of AI apps. Each app opens full screen in its own web view and keeps
 * running in the background. The four Claude accounts each get their own separate login storage
 * (a "profile") when the phone's Android System WebView supports it.
 */
public class MainActivity extends Activity {

    static class Svc {
        String id;
        String name;
        String url;
        boolean external;
        String profile; // null = shared login with the other apps
        boolean claude;
    }

    private static final int REQ_FILE = 71;

    private final List<Svc> services = new ArrayList<Svc>();
    private final Map<String, WebView> views = new LinkedHashMap<String, WebView>();
    private String active = null;

    private FrameLayout content;
    private LinearLayout chips;
    private HorizontalScrollView chipScroll;
    private LinearLayout topBar;
    private View homeView;
    private TextView backBtn;
    private TextView moreBtn;

    private boolean dark;
    private int cBg, cSurface, cText, cMuted, cLine, cAccent;
    private boolean profilesOk = false;
    private ValueCallback<Uri[]> fileCb;

    /* ------------------------------------------------------------ setup */

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        pickColors();
        try {
            profilesOk = WebViewFeature.isFeatureSupported("MULTI_PROFILE");
        } catch (Throwable t) {
            profilesOk = false;
        }
        loadServices();
        buildUi();
        showHome();
    }

    private void pickColors() {
        dark = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
        cAccent = Color.parseColor("#4338CA");
        if (dark) {
            cBg = Color.parseColor("#171614");
            cSurface = Color.parseColor("#211F1C");
            cText = Color.parseColor("#F1EEE8");
            cMuted = Color.parseColor("#A8A299");
            cLine = Color.parseColor("#37342F");
        } else {
            cBg = Color.parseColor("#F4F2EE");
            cSurface = Color.parseColor("#FFFFFF");
            cText = Color.parseColor("#1D1C1A");
            cMuted = Color.parseColor("#5E5A53");
            cLine = Color.parseColor("#E3DFD6");
        }
        getWindow().setStatusBarColor(cSurface);
        getWindow().setNavigationBarColor(cBg);
        View decor = getWindow().getDecorView();
        int flags = decor.getSystemUiVisibility();
        if (dark) {
            flags &= ~View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        } else {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }

    private int dp(int v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, getResources().getDisplayMetrics()));
    }

    private SharedPreferences prefs() {
        return getSharedPreferences("multiai", MODE_PRIVATE);
    }

    private Svc svc(String id, String name, String url, boolean external, String profile, boolean claude) {
        Svc s = new Svc();
        s.id = id;
        s.name = name;
        s.url = url;
        s.external = external;
        s.profile = profile;
        s.claude = claude;
        return s;
    }

    private void loadServices() {
        services.clear();
        String saved = prefs().getString("services", null);
        if (saved != null) {
            try {
                JSONArray arr = new JSONArray(saved);
                for (int i = 0; i < arr.length(); i++) {
                    JSONObject o = arr.getJSONObject(i);
                    String profile = o.isNull("profile") ? null : o.optString("profile", null);
                    services.add(svc(o.getString("id"), o.getString("name"), o.getString("url"),
                            o.optBoolean("external", false), profile, o.optBoolean("claude", false)));
                }
            } catch (Exception e) {
                services.clear();
            }
        }
        if (services.isEmpty()) {
            services.add(svc("claude1", "Claude 1", "https://claude.ai/", false, "acct0", true));
            services.add(svc("claude2", "Claude 2", "https://claude.ai/", false, "acct1", true));
            services.add(svc("claude3", "Claude 3", "https://claude.ai/", false, "acct2", true));
            services.add(svc("claude4", "Claude 4", "https://claude.ai/", false, "acct3", true));
            services.add(svc("chatgpt", "ChatGPT", "https://chatgpt.com/", false, null, false));
            services.add(svc("gemini", "Gemini", "https://gemini.google.com/", true, null, false));
            services.add(svc("grok", "Grok", "https://grok.com/", false, null, false));
            services.add(svc("deepseek", "DeepSeek", "https://chat.deepseek.com/", false, null, false));
            services.add(svc("perplexity", "Perplexity", "https://www.perplexity.ai/", false, null, false));
            services.add(svc("copilot", "Copilot", "https://copilot.microsoft.com/", false, null, false));
            services.add(svc("notebooklm", "NotebookLM", "https://notebooklm.google.com/", true, null, false));
            services.add(svc("mistral", "Mistral", "https://chat.mistral.ai/", false, null, false));
            services.add(svc("manus", "Manus", "https://manus.im/", false, null, false));
        }
    }

    private void saveServices() {
        try {
            JSONArray arr = new JSONArray();
            for (Svc s : services) {
                JSONObject o = new JSONObject();
                o.put("id", s.id);
                o.put("name", s.name);
                o.put("url", s.url);
                o.put("external", s.external);
                o.put("profile", s.profile == null ? JSONObject.NULL : s.profile);
                o.put("claude", s.claude);
                arr.put(o);
            }
            prefs().edit().putString("services", arr.toString()).apply();
        } catch (Exception e) {
            // ignore
        }
    }

    private Svc find(String id) {
        for (Svc s : services) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    /* ------------------------------------------------------------ ui */

    private TextView textBtn(String label, View.OnClickListener l) {
        TextView t = new TextView(this);
        t.setText(label);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTextColor(cText);
        t.setGravity(Gravity.CENTER);
        t.setMinWidth(dp(44));
        t.setMinHeight(dp(44));
        t.setPadding(dp(6), 0, dp(6), 0);
        t.setOnClickListener(l);
        return t;
    }

    private void buildUi() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(cBg);

        topBar = new LinearLayout(this);
        topBar.setOrientation(LinearLayout.HORIZONTAL);
        topBar.setGravity(Gravity.CENTER_VERTICAL);
        topBar.setBackgroundColor(cSurface);
        topBar.setPadding(dp(4), 0, dp(4), 0);

        TextView homeBtn = textBtn("⌂", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showHome();
            }
        });
        topBar.addView(homeBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        chipScroll = new HorizontalScrollView(this);
        chipScroll.setHorizontalScrollBarEnabled(false);
        chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setGravity(Gravity.CENTER_VERTICAL);
        chipScroll.addView(chips, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT));
        topBar.addView(chipScroll, new LinearLayout.LayoutParams(0, dp(52), 1f));

        backBtn = textBtn("◀", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                goBack();
            }
        });
        topBar.addView(backBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        moreBtn = textBtn("⋮", new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showMoreMenu(v);
            }
        });
        topBar.addView(moreBtn, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(52)));

        root.addView(topBar, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52)));

        View line = new View(this);
        line.setBackgroundColor(cLine);
        root.addView(line, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1));

        content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f));

        setContentView(root);
    }

    private GradientDrawable round(int fill, int stroke, int radiusDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(1, stroke);
        return g;
    }

    private String host(String url) {
        try {
            String h = Uri.parse(url).getHost();
            if (h == null) return url;
            return h.startsWith("www.") ? h.substring(4) : h;
        } catch (Exception e) {
            return url;
        }
    }

    private void refreshChips() {
        chips.removeAllViews();
        for (final String id : views.keySet()) {
            final Svc s = find(id);
            if (s == null) continue;
            LinearLayout chip = new LinearLayout(this);
            chip.setOrientation(LinearLayout.HORIZONTAL);
            chip.setGravity(Gravity.CENTER_VERTICAL);
            boolean on = id.equals(active);
            chip.setBackground(round(on ? cAccent : cBg, on ? 0 : cLine, 18));
            chip.setPadding(dp(12), 0, dp(4), 0);

            TextView name = new TextView(this);
            name.setText(s.name);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 13);
            name.setTextColor(on ? Color.WHITE : cText);
            name.setSingleLine(true);
            name.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showApp(id);
                }
            });
            chip.addView(name, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));
            ((LinearLayout.LayoutParams) name.getLayoutParams()).gravity = Gravity.CENTER_VERTICAL;
            name.setGravity(Gravity.CENTER_VERTICAL);

            TextView x = new TextView(this);
            x.setText("✕");
            x.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            x.setTextColor(on ? Color.WHITE : cMuted);
            x.setGravity(Gravity.CENTER);
            x.setPadding(dp(8), 0, dp(8), 0);
            x.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    closeApp(id);
                }
            });
            chip.addView(x, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34)));

            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, dp(34));
            lp.setMargins(dp(3), 0, dp(3), 0);
            chips.addView(chip, lp);
        }
        backBtn.setVisibility(active != null ? View.VISIBLE : View.GONE);
        moreBtn.setVisibility(active != null ? View.VISIBLE : View.GONE);
    }

    /* ------------------------------------------------------------ home */

    private void showHome() {
        active = null;
        for (WebView w : views.values()) w.setVisibility(View.GONE);
        if (homeView != null) content.removeView(homeView);
        homeView = buildHome();
        content.addView(homeView, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        refreshChips();
    }

    private View buildHome() {
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(cBg);
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setPadding(dp(14), dp(14), dp(14), dp(28));
        sv.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        // banner
        LinearLayout banner = new LinearLayout(this);
        banner.setOrientation(LinearLayout.VERTICAL);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{cAccent, Color.parseColor("#0F172A")});
        bg.setCornerRadius(dp(14));
        banner.setBackground(bg);
        banner.setPadding(dp(16), dp(14), dp(16), dp(14));
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        TextView hi = new TextView(this);
        hi.setText(hour < 12 ? "Good morning" : hour < 18 ? "Good afternoon" : "Good evening");
        hi.setTextColor(Color.WHITE);
        hi.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20);
        hi.setTypeface(Typeface.DEFAULT_BOLD);
        banner.addView(hi);
        TextView sub = new TextView(this);
        sub.setText("All your AI apps in one place. Logins are saved and open apps keep running.");
        sub.setTextColor(Color.parseColor("#E6E6F5"));
        sub.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12.5f);
        banner.addView(sub);
        TextView by = new TextView(this);
        by.setText("DESIGNED BY NURUZZAMAN");
        by.setTextColor(Color.WHITE);
        by.setTextSize(TypedValue.COMPLEX_UNIT_SP, 10.5f);
        by.setLetterSpacing(0.12f);
        by.setPadding(0, dp(8), 0, 0);
        banner.addView(by);
        col.addView(banner, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        if (!views.isEmpty()) {
            col.addView(sectionTitle("CONTINUE"));
            List<Svc> running = new ArrayList<Svc>();
            for (String id : views.keySet()) {
                Svc s = find(id);
                if (s != null) running.add(s);
            }
            addGrid(col, running);
        }

        List<Svc> claudes = new ArrayList<Svc>();
        List<Svc> others = new ArrayList<Svc>();
        for (Svc s : services) {
            if (s.claude) claudes.add(s);
            else others.add(s);
        }
        col.addView(sectionTitle("CLAUDE ACCOUNTS"));
        addGrid(col, claudes);
        if (!profilesOk) {
            TextView warn = new TextView(this);
            warn.setText("Your phone's Android System WebView is too old to keep the 4 Claude logins separate. Update \"Android System WebView\" in Google Play, then reopen this app.");
            warn.setTextColor(Color.parseColor("#B45309"));
            warn.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
            warn.setPadding(dp(4), dp(8), dp(4), 0);
            col.addView(warn);
        }
        col.addView(sectionTitle("MORE AI APPS"));
        addGrid(col, others);

        TextView add = new TextView(this);
        add.setText("+ Add an app");
        add.setGravity(Gravity.CENTER);
        add.setTextColor(cMuted);
        add.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        add.setBackground(round(cSurface, cLine, 14));
        add.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editDialog(null);
            }
        });
        LinearLayout.LayoutParams alp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(52));
        alp.topMargin = dp(10);
        col.addView(add, alp);

        TextView tip = new TextView(this);
        tip.setText("Tip: press and hold an app for more options.");
        tip.setTextColor(cMuted);
        tip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 12);
        tip.setGravity(Gravity.CENTER);
        tip.setPadding(0, dp(18), 0, 0);
        col.addView(tip);
        return sv;
    }

    private TextView sectionTitle(String t) {
        TextView v = new TextView(this);
        v.setText(t);
        v.setTextColor(cMuted);
        v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        v.setTypeface(Typeface.DEFAULT_BOLD);
        v.setLetterSpacing(0.1f);
        v.setPadding(dp(4), dp(20), 0, dp(8));
        return v;
    }

    private void addGrid(LinearLayout parent, List<Svc> list) {
        for (int i = 0; i < list.size(); i += 2) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            for (int k = 0; k < 2; k++) {
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(72), 1f);
                lp.setMargins(k == 0 ? 0 : dp(5), dp(5), k == 0 ? dp(5) : 0, dp(5));
                if (i + k < list.size()) {
                    row.addView(card(list.get(i + k)), lp);
                } else {
                    View spacer = new View(this);
                    row.addView(spacer, lp);
                }
            }
            parent.addView(row, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        }
    }

    private View card(final Svc s) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.HORIZONTAL);
        c.setGravity(Gravity.CENTER_VERTICAL);
        c.setBackground(round(cSurface, views.containsKey(s.id) ? cAccent : cLine, 14));
        c.setPadding(dp(12), 0, dp(10), 0);

        TextView letter = new TextView(this);
        letter.setText(s.name.length() > 0 ? s.name.substring(0, 1).toUpperCase() : "?");
        letter.setGravity(Gravity.CENTER);
        letter.setTextColor(Color.WHITE);
        letter.setTypeface(Typeface.DEFAULT_BOLD);
        letter.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16);
        letter.setBackground(round(cAccent, 0, 11));
        c.addView(letter, new LinearLayout.LayoutParams(dp(38), dp(38)));

        LinearLayout txt = new LinearLayout(this);
        txt.setOrientation(LinearLayout.VERTICAL);
        txt.setPadding(dp(10), 0, 0, 0);
        TextView name = new TextView(this);
        name.setText(s.name);
        name.setTextColor(cText);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        name.setSingleLine(true);
        txt.addView(name);
        TextView note = new TextView(this);
        note.setText(views.containsKey(s.id) ? "Running" : s.external ? "Opens in browser" : host(s.url));
        note.setTextColor(views.containsKey(s.id) ? Color.parseColor("#15803D") : cMuted);
        note.setTextSize(TypedValue.COMPLEX_UNIT_SP, 11.5f);
        note.setSingleLine(true);
        txt.addView(note);
        c.addView(txt, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        c.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openApp(s);
            }
        });
        c.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                appMenu(s);
                return true;
            }
        });
        return c;
    }

    private void appMenu(final Svc s) {
        final List<String> items = new ArrayList<String>();
        items.add("Open");
        if (views.containsKey(s.id)) items.add("Close (login stays saved)");
        items.add("Open in my browser");
        items.add("Rename / edit");
        if (!s.claude) items.add("Delete");
        new AlertDialog.Builder(this)
                .setTitle(s.name)
                .setItems(items.toArray(new String[0]), new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String pick = items.get(which);
                        if (pick.equals("Open")) openApp(s);
                        else if (pick.startsWith("Close")) {
                            closeApp(s.id);
                        } else if (pick.equals("Open in my browser")) openInBrowser(s.url);
                        else if (pick.equals("Rename / edit")) editDialog(s);
                        else if (pick.equals("Delete")) {
                            closeApp(s.id);
                            services.remove(s);
                            saveServices();
                            showHome();
                        }
                    }
                })
                .show();
    }

    private void editDialog(final Svc existing) {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), dp(8), dp(20), 0);
        final EditText name = new EditText(this);
        name.setHint("Name");
        name.setSingleLine(true);
        final EditText url = new EditText(this);
        url.setHint("Address, for example https://chat.example.com");
        url.setSingleLine(true);
        url.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        final CheckBox ext = new CheckBox(this);
        ext.setText("Open in my normal browser (for sites that block sign-in here)");
        if (existing != null) {
            name.setText(existing.name);
            url.setText(existing.url);
            ext.setChecked(existing.external);
        }
        box.addView(name);
        box.addView(url);
        box.addView(ext);
        new AlertDialog.Builder(this)
                .setTitle(existing == null ? "Add an app" : "Edit app")
                .setView(box)
                .setNegativeButton("Cancel", null)
                .setPositiveButton("Save", new android.content.DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(android.content.DialogInterface d, int which) {
                        String n = name.getText().toString().trim();
                        String u = url.getText().toString().trim();
                        if (n.length() == 0 || u.length() == 0) return;
                        if (!u.startsWith("http://") && !u.startsWith("https://")) u = "https://" + u;
                        if (existing == null) {
                            services.add(svc("c" + System.currentTimeMillis(), n, u, ext.isChecked(), null, false));
                        } else {
                            boolean changedUrl = !existing.url.equals(u);
                            existing.name = n;
                            existing.url = u;
                            existing.external = ext.isChecked();
                            if (changedUrl) closeApp(existing.id);
                        }
                        saveServices();
                        showHome();
                    }
                })
                .show();
    }

    /* ------------------------------------------------------------ apps */

    private void openInBrowser(String url) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(url)));
        } catch (Exception e) {
            Toast.makeText(this, "No browser found", Toast.LENGTH_SHORT).show();
        }
    }

    private void openApp(Svc s) {
        if (s.external) {
            Toast.makeText(this, s.name + " opens in your browser (it blocks sign-in inside apps)", Toast.LENGTH_SHORT).show();
            openInBrowser(s.url);
            return;
        }
        if (!views.containsKey(s.id)) {
            WebView w = createWebView(s);
            views.put(s.id, w);
            content.addView(w, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
            w.loadUrl(s.url);
        }
        showApp(s.id);
    }

    private void showApp(String id) {
        if (!views.containsKey(id)) return;
        active = id;
        if (homeView != null) {
            content.removeView(homeView);
            homeView = null;
        }
        for (Map.Entry<String, WebView> e : views.entrySet()) {
            e.getValue().setVisibility(e.getKey().equals(id) ? View.VISIBLE : View.GONE);
        }
        refreshChips();
        views.get(id).requestFocus();
    }

    private void closeApp(String id) {
        WebView w = views.remove(id);
        if (w != null) {
            content.removeView(w);
            w.destroy();
        }
        if (id.equals(active)) {
            showHome();
        } else {
            if (active == null) showHome();
            else refreshChips();
        }
    }

    private void goBack() {
        if (active == null) return;
        WebView w = views.get(active);
        if (w != null && w.canGoBack()) w.goBack();
        else showHome();
    }

    private void showMoreMenu(View anchor) {
        if (active == null) return;
        final WebView w = views.get(active);
        final Svc s = find(active);
        if (w == null || s == null) return;
        PopupMenu m = new PopupMenu(this, anchor);
        m.getMenu().add(0, 1, 0, "Forward");
        m.getMenu().add(0, 2, 1, "Reload");
        m.getMenu().add(0, 3, 2, "Open in my browser");
        m.getMenu().add(0, 4, 3, "Zoom in");
        m.getMenu().add(0, 5, 4, "Zoom out");
        m.getMenu().add(0, 6, 5, "Close this app");
        m.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(android.view.MenuItem item) {
                switch (item.getItemId()) {
                    case 1:
                        if (w.canGoForward()) w.goForward();
                        break;
                    case 2:
                        w.reload();
                        break;
                    case 3:
                        String u = w.getUrl();
                        openInBrowser(u != null ? u : s.url);
                        break;
                    case 4:
                        w.getSettings().setTextZoom(Math.min(220, w.getSettings().getTextZoom() + 10));
                        break;
                    case 5:
                        w.getSettings().setTextZoom(Math.max(60, w.getSettings().getTextZoom() - 10));
                        break;
                    case 6:
                        closeApp(s.id);
                        break;
                    default:
                        break;
                }
                return true;
            }
        });
        m.show();
    }

    private WebView createWebView(Svc s) {
        WebView w = new WebView(this);
        // Separate login storage for this app, when the phone supports it. Must happen before loading.
        if (s.profile != null && profilesOk && !"claude1".equals(s.id)) {
            try {
                Class<?> storeCls = Class.forName("androidx.webkit.ProfileStore");
                Object store = storeCls.getMethod("getInstance").invoke(null);
                storeCls.getMethod("getOrCreateProfile", String.class).invoke(store, s.profile);
            } catch (Throwable t) {
                // the profile is created by setProfile if this step is not needed
            }
            try {
                Class<?> compat = Class.forName("androidx.webkit.WebViewCompat");
                compat.getMethod("setProfile", WebView.class, String.class).invoke(null, w, s.profile);
            } catch (Throwable t) {
                Toast.makeText(this, "Could not keep " + s.name + " separate on this phone", Toast.LENGTH_LONG).show();
            }
        }
        WebSettings st = w.getSettings();
        st.setJavaScriptEnabled(true);
        st.setDomStorageEnabled(true);
        st.setDatabaseEnabled(true);
        st.setLoadWithOverviewMode(true);
        st.setSupportMultipleWindows(true);
        st.setMediaPlaybackRequiresUserGesture(true);
        st.setBuiltInZoomControls(true);
        st.setDisplayZoomControls(false);
        st.setAllowFileAccess(false);
        st.setAllowContentAccess(true);
        st.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        try {
            CookieManager.getInstance().setAcceptCookie(true);
            CookieManager.getInstance().setAcceptThirdPartyCookies(w, true);
        } catch (Throwable t) {
            // ignore
        }
        w.setBackgroundColor(cBg);
        w.setWebViewClient(new WebViewClient() {
            @Override
            public void onPageFinished(WebView v, String url) {
                Toast.makeText(MainActivity.this, "Loaded: " + v.getTitle() + " | " + url, Toast.LENGTH_SHORT).show();
            }
            private void fail(WebView v, String what) {
                String h = "<html><body style='background:#171614;color:#eee;font-family:sans-serif;padding:24px'>"
                    + "<h3>This page could not load</h3><p>" + what + "</p>"
                    + "<p>Check your internet, then tap the three dots and choose Reload.</p></body></html>";
                v.loadDataWithBaseURL(null, h, "text/html", "utf-8", null);
            }
            @Override
            public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                if (r.isForMainFrame()) fail(v, "Error: " + e.getDescription() + " (" + e.getErrorCode() + ")<br>" + r.getUrl());
            }
            @Override
            public void onReceivedHttpError(WebView v, WebResourceRequest r, android.webkit.WebResourceResponse e) {
                if (r.isForMainFrame() && e.getStatusCode() >= 400 && e.getStatusCode() != 403)
                    fail(v, "Server answered " + e.getStatusCode() + "<br>" + r.getUrl());
            }
            @Override
            public void onReceivedSslError(WebView v, android.webkit.SslErrorHandler h, android.net.http.SslError e) {
                h.cancel();
                fail(v, "Secure connection failed. Check the phone's date and time.");
            }
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String u = request.getUrl().toString();
                if (u.startsWith("http://") || u.startsWith("https://")) return false;
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
                } catch (Exception e) {
                    // nothing can open it
                }
                return true;
            }
        });
        w.setWebChromeClient(new WebChromeClient() {
            @Override
            public boolean onCreateWindow(WebView view, boolean isDialog, boolean isUserGesture, android.os.Message resultMsg) {
                // login pages often open a pop-up; load it in the same view instead
                final WebView target = view;
                WebView tmp = new WebView(MainActivity.this);
                tmp.setWebViewClient(new WebViewClient() {
                    @Override
                    public boolean shouldOverrideUrlLoading(WebView v, WebResourceRequest r) {
                        target.loadUrl(r.getUrl().toString());
                        return true;
                    }
                });
                WebView.WebViewTransport t = (WebView.WebViewTransport) resultMsg.obj;
                t.setWebView(tmp);
                resultMsg.sendToTarget();
                return true;
            }

            @Override
            public boolean onShowFileChooser(WebView webView, ValueCallback<Uri[]> callback, FileChooserParams params) {
                if (fileCb != null) fileCb.onReceiveValue(null);
                fileCb = callback;
                try {
                    startActivityForResult(params.createIntent(), REQ_FILE);
                } catch (Exception e) {
                    fileCb = null;
                    return false;
                }
                return true;
            }
        });
        return w;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQ_FILE) {
            if (fileCb != null) {
                fileCb.onReceiveValue(WebChromeClient.FileChooserParams.parseResult(resultCode, data));
                fileCb = null;
            }
            return;
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onBackPressed() {
        if (active != null) {
            goBack();
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        try {
            CookieManager.getInstance().flush();
        } catch (Throwable t) {
            // ignore
        }
    }
}
