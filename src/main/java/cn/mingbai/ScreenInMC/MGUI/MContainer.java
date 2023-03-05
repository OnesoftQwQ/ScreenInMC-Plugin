package cn.mingbai.ScreenInMC.MGUI;

import cn.mingbai.ScreenInMC.Main;
import cn.mingbai.ScreenInMC.Screen.Screen;
import cn.mingbai.ScreenInMC.Utils.ImageUtils;
import org.bukkit.scheduler.BukkitRunnable;

import java.awt.*;
import java.awt.font.FontRenderContext;
import java.awt.font.LineMetrics;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class MContainer extends MControl {
    public static final int minClickInterval = 100;
    private static final String crashedText = ":((( Crashed / ScreenInMC 2.0";
    public final String[] defaultFonts = {"PingFang SC", "Hiragino Sans GB", "Heiti SC", "Microsoft YaHei", "WenQuanYi Micro Hei", "SimSun"};
    private final Screen screen;
    protected MControl activeControl;
    BufferedImage image;
    List<Rectangle2D.Double> reRenderRectangles = Collections.synchronizedList(new ArrayList<>());
    private Graphics2D graphics;
    private FontRenderContext frc;
    private boolean canClick = true;
    private long clickTime = 0;
    private Object renderLock = new Object();
    private Object renderLock2 = new Object();
    private BukkitRunnable renderThread;
    private short reRenderCount = 0;
    private boolean rerender = false;

    public MContainer(Screen screen) {
        this.screen = screen;
        this.setWidth(screen.getWidth() * 128);
        this.setHeight(screen.getHeight() * 128);
        createImage();
    }

    public void load() {
        this.loaded = true;
        onLoad();
        addReRender(new Rectangle2D.Double(0, 0, getWidth(), getHeight()));
    }

    public void unload() {
        onUnload();
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (renderThread != null) {
            renderThread.cancel();
        }
        renderThread = new BukkitRunnable() {
            private boolean cancelRender = false;

            @Override
            public synchronized void cancel() throws IllegalStateException {
                cancelRender = true;
                super.cancel();
            }

            @Override
            public void run() {
                while (!cancelRender) {
                    long startTime = System.currentTimeMillis();
                    render();
                    for (Runnable i : getAllRenderTasks()) {
                        i.run();
                    }
                    long sleepTime = 50 - System.currentTimeMillis() + startTime;
                    if (sleepTime > 0) {
                        try {
                            Thread.sleep(sleepTime);
                        } catch (Exception e) {
                            return;
                        }
                    }
                }
            }
        };
        renderThread.runTaskAsynchronously(Main.thisPlugin());
    }

    public void crash() {
        reRenderCount = 10;
        reRender();
    }

    public Object getRenderLock() {
        return renderLock;
    }

    private synchronized void render() {
        if (rerender) {

            if (graphics == null) {
                return;
            }
            if (reRenderCount == 10) {
                reRenderCount++;
                for (MControl i : getChildControls()) {
                    removeChildControl(i);
                }
                graphics.setClip(0, 0, image.getWidth(), image.getHeight());
                graphics.setPaint(new Color(0x0078d7));
                graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
                graphics.setFont(new Font(graphics.getFont().getName(), Font.BOLD, 32));
                graphics.setPaint(new Color(0xffffff));
                LineMetrics metrics = graphics.getFont().getLineMetrics(crashedText, frc);
                graphics.drawString(crashedText, 0, metrics.getAscent());
                screen.sendView(ImageUtils.imageToMapColors(image));
                if (renderThread != null) {
                    renderThread.cancel();
                }
                return;
            }
            if (reRenderCount > 10) {
                return;
            }
            try {
                graphics.setPaint(new Color(0, 0, 0, 0));
                graphics.setStroke(new BasicStroke(1));
                graphics.setClip(0, 0, image.getWidth(), image.getHeight());
                graphics.clearRect(0, 0, image.getWidth(), image.getHeight());
                MRenderer renderer = new MRenderer(this, graphics, frc);
                renderer.setControl(this);
                onRender(renderer);
                if (reRenderCount < 10) {
                    reRenderCount = 0;
                }
            } catch (Exception e) {
                reRenderCount++;
                e.printStackTrace();
                createImage();
                reRender();
            }
            synchronized (renderLock2) {
                for (int i = 0; i < reRenderRectangles.size(); i++) {
                    int x = (int) reRenderRectangles.get(i).x;
                    int y = (int) reRenderRectangles.get(i).y;
                    int w = (int) reRenderRectangles.get(i).width;
                    int h = (int) reRenderRectangles.get(i).height;
                    if (x < 0) {
                        x = 0;
                    }
                    if (y < 0) {
                        y = 0;
                    }
                    if (w <= 0 || h <= 0) {
                        continue;
                    }
                    try {
                        if (x == 0 && y == 0 && w == (int) getWidth() && h == (int) getHeight()) {
                            if (loaded) {
                                screen.sendView(ImageUtils.imageToMapColors(image));
                            }
                        } else {
                            if (loaded) {
                                screen.sendView(ImageUtils.imageToMapColors(image.getSubimage(x, y, w, h)), x, y, w, h);
                            }
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
                reRenderRectangles.clear();
            }
            synchronized (renderLock) {
                renderLock.notifyAll();
            }
            rerender = false;
        }
    }

    @Override
    public void onUnload() {
        super.onUnload();
        loaded = false;
        if (renderThread != null) {
            renderThread.cancel();
        }
    }

    private void createImage() {
        if (graphics != null) {
            try {
                graphics.dispose();
            } catch (Exception e) {
            }
        }
        image = new BufferedImage((int) this.getWidth(), (int) this.getHeight(), BufferedImage.TYPE_INT_ARGB);
        graphics = (Graphics2D) image.getGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        frc = graphics.getFontRenderContext();
        Font font = null;
        for (String i : defaultFonts) {
            font = Font.getFont(i);
            if (font != null) {
                break;
            }
        }
        if (font == null) {
            font = graphics.getFont();
        }
        if (font != null) {
            Font newFont = new Font(font.getFontName(), font.getStyle(), 16);
            graphics.setFont(newFont);
        }
    }

    @Override
    public void onTextInput(String text) {
        super.onTextInput(text);
        if (activeControl != null) {
            activeControl.onTextInput(text);
        }
    }

    public Screen getScreen() {
        return screen;
    }

    @Override
    public void reRender() {
        rerender = true;
    }

    public void addReRender(Rectangle2D.Double rect) {
        synchronized (renderLock2) {
            for (Rectangle2D.Double i : reRenderRectangles) {
                if (i.equals(rect)) {
                    return;
                }
            }
            if (rect.width == 0 || rect.height == 0) {
                return;
            }
            reRenderRectangles.add((Rectangle2D.Double) rect.clone());
        }
    }

    public void addReRender(Rectangle2D.Double[] rect) {
        synchronized (renderLock2) {
            for (Rectangle2D.Double i : rect) {
                if (i.width == 0 || i.height == 0) {
                    continue;
                }
                reRenderRectangles.add((Rectangle2D.Double) i.clone());
            }
        }
    }

    public void inputText(String text) {
        onTextInput(text);
    }

    public void clickAt(int x, int y, ClickType type) {
        try {
            if (canClick && (System.currentTimeMillis() - clickTime) >= minClickInterval) {
                canClick = false;
                clickTime = System.currentTimeMillis();
                List<MControl> controls = getAllChildMControls();
                for (MControl i : controls) {
                    double left = i.getAbsoluteLeft();
                    double top = i.getAbsoluteTop();
                    if (i.isVisible() && left < x && (left + i.getWidth()) > x && top < y && (top + i.getHeight()) > y) {
                        i.onClick((int) (x - left), (int) (y - top), type);
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        canClick = true;
    }
}
