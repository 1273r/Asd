package com.example.robloxphysics.client;

import com.example.robloxphysics.lua.LogBuffer;
import com.google.gson.JsonObject;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import org.lwjgl.glfw.GLFW;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Roblox-style Developer Console (F9): Client / Server log with level filters and search, and a command
 * line that runs Lua in the selected context.
 */
public class DevConsoleScreen extends Screen {
    private static final int BG = 0xE0191919, BAR = 0xFF232527, BORDER = 0xFF3C3F41, TEXT = 0xFFDDDDDD, DIM = 0xFF8A8A8A;
    private static final int[] LEVEL_COLORS = {0xFFFFFFFF, 0xFF7FB2FF, 0xFFFFA64D, 0xFFFF5555};
    private static final String[] LEVEL_NAMES = {"Output", "Information", "Warning", "Error"};

    private static boolean serverTab;
    private static final boolean[] filters = {true, true, true, true};
    private static final List<String> history = new ArrayList<>();

    private int x0, y0, w, h;
    private EditBox command, search;
    private int historyIndex = -1;
    private double scroll; // lines scrolled up from the bottom
    private long builtVersion = -1;
    private int builtWidth = -1;
    private String builtSearch = "";
    private boolean builtServer;
    private int builtFilters = -1;
    private final List<FormattedCharSequence> rendered = new ArrayList<>();
    private final List<Integer> renderedColors = new ArrayList<>();
    private final SimpleDateFormat time = new SimpleDateFormat("HH:mm:ss.SSS", Locale.ROOT);

    public DevConsoleScreen() {
        super(Component.literal("Developer Console"));
    }

    private LogBuffer log() { return serverTab ? ClientScripting.SERVER_LOG : ClientScripting.CLIENT_LOG; }

    @Override
    protected void init() {
        w = Math.min(width - 16, 720);
        h = height - 16;
        x0 = (width - w) / 2;
        y0 = 8;
        search = new EditBox(font, x0 + w - 150, y0 + 26, 144, 14, Component.literal("Search"));
        search.setHint(Component.literal("Search").withStyle(s -> s.withColor(DIM)));
        search.setMaxLength(200);
        addRenderableWidget(search);
        command = new EditBox(font, x0 + 16, y0 + h - 20, w - 22, 14, Component.literal("Command"));
        command.setMaxLength(100000);
        command.setBordered(false);
        addRenderableWidget(command);
        setInitialFocus(command);
        if (serverTab) subscribe(true);
    }

    @Override
    public boolean isPauseScreen() { return false; }

    @Override
    public void renderBackground(GuiGraphics g, int mx, int my, float pt) {}

    @Override
    public void removed() {
        if (serverTab) subscribe(false);
    }

    private void subscribe(boolean on) {
        JsonObject o = new JsonObject();
        o.addProperty("t", "subscribeLog");
        o.addProperty("on", on);
        ClientScripting.send(o);
    }

    // ------------------------------------------------------------------ layout helpers

    private int logTop() { return y0 + 46; }

    private int logBottom() { return y0 + h - 26; }

    private int lineHeight() { return font.lineHeight + 1; }

    private int visibleLines() { return Math.max(1, (logBottom() - logTop()) / lineHeight()); }

    private int[] tabRect(int i) { return new int[]{x0 + 6 + i * 62, y0 + 24, 58, 18}; }

    private int[] filterRect(int i) {
        int x = x0 + 136;
        for (int j = 0; j < i; j++) x += font.width(LEVEL_NAMES[j]) + 20;
        return new int[]{x, y0 + 27, font.width(LEVEL_NAMES[i]) + 14, 12};
    }

    private int[] clearRect() { return new int[]{x0 + w - 200, y0 + 26, 44, 14}; }

    private int[] closeRect() { return new int[]{x0 + w - 18, y0 + 4, 14, 14}; }

    private static boolean in(int[] r, double mx, double my) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    // ------------------------------------------------------------------ log text

    private void rebuild() {
        LogBuffer log = log();
        int fl = (filters[0] ? 1 : 0) | (filters[1] ? 2 : 0) | (filters[2] ? 4 : 0) | (filters[3] ? 8 : 0);
        String q = search.getValue().toLowerCase(Locale.ROOT);
        int width = w - 16;
        if (log.version() == builtVersion && width == builtWidth && q.equals(builtSearch) && serverTab == builtServer && fl == builtFilters) return;
        builtVersion = log.version();
        builtWidth = width;
        builtSearch = q;
        builtServer = serverTab;
        builtFilters = fl;
        rendered.clear();
        renderedColors.clear();
        for (LogBuffer.Line l : log.snapshot()) {
            int lvl = Math.max(0, Math.min(3, l.level()));
            if (!filters[lvl]) continue;
            if (!q.isEmpty() && !l.text().toLowerCase(Locale.ROOT).contains(q)) continue;
            String text = time.format(new Date(l.time())) + "  " + l.text();
            for (FormattedCharSequence s : font.split(Component.literal(text), width)) {
                rendered.add(s);
                renderedColors.add(LEVEL_COLORS[lvl]);
            }
        }
        scroll = Math.min(scroll, Math.max(0, rendered.size() - visibleLines()));
    }

    // ------------------------------------------------------------------ rendering

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        rebuild();
        g.fill(x0, y0, x0 + w, y0 + h, BG);
        g.renderOutline(x0, y0, w, h, BORDER);
        // title bar
        g.fill(x0 + 1, y0 + 1, x0 + w - 1, y0 + 20, BAR);
        g.drawString(font, "Developer Console", x0 + 8, y0 + 6, TEXT, false);
        int[] cr = closeRect();
        g.drawString(font, "✕", cr[0] + 3, cr[1] + 3, in(cr, mx, my) ? 0xFFFF6666 : TEXT, false);
        // Client / Server tabs
        String[] tabs = {"Client", "Server"};
        for (int i = 0; i < 2; i++) {
            int[] r = tabRect(i);
            boolean sel = (i == 1) == serverTab;
            g.fill(r[0], r[1], r[0] + r[2], r[1] + r[3], sel ? 0xFF3A3D41 : (in(r, mx, my) ? 0xFF2C2F33 : BAR));
            if (sel) g.fill(r[0], r[1] + r[3] - 2, r[0] + r[2], r[1] + r[3], 0xFF00A2FF);
            g.drawCenteredString(font, tabs[i], r[0] + r[2] / 2, r[1] + 5, sel ? 0xFFFFFFFF : DIM);
        }
        // level filters
        for (int i = 0; i < 4; i++) {
            int[] r = filterRect(i);
            g.renderOutline(r[0], r[1] + 1, 10, 10, DIM);
            if (filters[i]) g.fill(r[0] + 2, r[1] + 3, r[0] + 8, r[1] + 9, LEVEL_COLORS[i]);
            g.drawString(font, LEVEL_NAMES[i], r[0] + 13, r[1] + 2, TEXT, false);
        }
        int[] cl = clearRect();
        g.fill(cl[0], cl[1], cl[0] + cl[2], cl[1] + cl[3], in(cl, mx, my) ? 0xFF3A3D41 : BAR);
        g.drawCenteredString(font, "Clear", cl[0] + cl[2] / 2, cl[1] + 3, TEXT);

        // log area
        int top = logTop(), bottom = logBottom();
        g.fill(x0 + 4, top - 2, x0 + w - 4, bottom + 2, 0x60000000);
        String notice = notice();
        if (notice != null) {
            g.drawCenteredString(font, notice, x0 + w / 2, (top + bottom) / 2, DIM);
        } else {
            int vis = visibleLines();
            int end = rendered.size() - (int) scroll;
            int start = Math.max(0, end - vis);
            int y = bottom - (end - start) * lineHeight();
            g.enableScissor(x0 + 4, top, x0 + w - 4, bottom);
            for (int i = start; i < end; i++) {
                g.drawString(font, rendered.get(i), x0 + 8, y, renderedColors.get(i), false);
                y += lineHeight();
            }
            g.disableScissor();
            if (rendered.size() > vis) { // scrollbar
                int track = bottom - top;
                int thumb = Math.max(10, track * vis / rendered.size());
                int pos = (int) ((track - thumb) * (1 - scroll / Math.max(1, rendered.size() - vis)));
                g.fill(x0 + w - 7, top + pos, x0 + w - 5, top + pos + thumb, 0xFF6A6D70);
            }
        }

        // command line
        g.fill(x0 + 4, y0 + h - 23, x0 + w - 4, y0 + h - 4, 0xFF101010);
        g.renderOutline(x0 + 4, y0 + h - 23, w - 8, 19, command.isFocused() ? 0xFF00A2FF : BORDER);
        g.drawString(font, ">", x0 + 8, y0 + h - 18, 0xFF00A2FF, false);
        if (command.getValue().isEmpty() && !command.isFocused()) {
            g.drawString(font, "Run " + (serverTab ? "server" : "client") + " Lua...", x0 + 16, y0 + h - 18, DIM, false);
        }
        super.render(g, mx, my, pt);
    }

    private String notice() {
        if (!serverTab) return null;
        if (!ClientScripting.serverHasMod()) return "This server doesn't have Roblox Physics installed.";
        if (Boolean.FALSE.equals(ClientScripting.serverDeveloper)) return "The server log is only available to operators.";
        return null;
    }

    // ------------------------------------------------------------------ input

    @Override
    public boolean mouseClicked(double mx, double my, int button) {
        if (in(closeRect(), mx, my)) {
            onClose();
            return true;
        }
        for (int i = 0; i < 2; i++) {
            if (in(tabRect(i), mx, my)) {
                boolean server = i == 1;
                if (server != serverTab) {
                    serverTab = server;
                    subscribe(server);
                    scroll = 0;
                }
                return true;
            }
        }
        for (int i = 0; i < 4; i++) {
            if (in(filterRect(i), mx, my)) {
                filters[i] = !filters[i];
                return true;
            }
        }
        if (in(clearRect(), mx, my)) {
            log().clear();
            scroll = 0;
            return true;
        }
        return super.mouseClicked(mx, my, button);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double dx, double dy) {
        scroll = Math.max(0, Math.min(Math.max(0, rendered.size() - visibleLines()), scroll + dy * 3));
        return true;
    }

    @Override
    public boolean keyPressed(int key, int scan, int mods) {
        if (ClientKeys.DEV_CONSOLE.matches(key, scan) && !search.isFocused()) {
            onClose();
            return true;
        }
        if (command.isFocused()) {
            if (key == GLFW.GLFW_KEY_ENTER || key == GLFW.GLFW_KEY_KP_ENTER) {
                run(command.getValue());
                command.setValue("");
                historyIndex = -1;
                return true;
            }
            if (key == GLFW.GLFW_KEY_UP && !history.isEmpty()) {
                historyIndex = historyIndex < 0 ? history.size() - 1 : Math.max(0, historyIndex - 1);
                command.setValue(history.get(historyIndex));
                return true;
            }
            if (key == GLFW.GLFW_KEY_DOWN && historyIndex >= 0) {
                historyIndex++;
                if (historyIndex >= history.size()) {
                    historyIndex = -1;
                    command.setValue("");
                } else {
                    command.setValue(history.get(historyIndex));
                }
                return true;
            }
        }
        if (key == GLFW.GLFW_KEY_PAGE_UP) return mouseScrolled(0, 0, 0, visibleLines() / 3.0);
        if (key == GLFW.GLFW_KEY_PAGE_DOWN) return mouseScrolled(0, 0, 0, -visibleLines() / 3.0);
        return super.keyPressed(key, scan, mods);
    }

    private void run(String code) {
        if (code.isBlank()) return;
        if (history.isEmpty() || !history.get(history.size() - 1).equals(code)) history.add(code);
        scroll = 0;
        if (serverTab) {
            JsonObject o = new JsonObject();
            o.addProperty("t", "exec");
            o.addProperty("code", code);
            if (!ClientScripting.send(o)) {
                ClientScripting.SERVER_LOG.add(LogBuffer.ERROR, "This server doesn't have Roblox Physics installed.");
            }
            return;
        }
        ClientDataModel dm = ClientScripting.dm();
        String t = code.trim();
        if (t.equalsIgnoreCase("reset")) {
            ClientScripting.CLIENT_LOG.add(LogBuffer.INFO, "> reset");
            ClientScripting.restart();
            return;
        }
        if (dm == null) return;
        if (t.equalsIgnoreCase("reload")) {
            dm.log.add(LogBuffer.INFO, "> reload");
            dm.reloadScripts();
            return;
        }
        dm.log.add(LogBuffer.INFO, "> " + (code.length() > 300 ? code.substring(0, 300) + "..." : code));
        dm.rt.execute(code, "Client");
    }
}
