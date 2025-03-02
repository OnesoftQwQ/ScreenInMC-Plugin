package cn.mingbai.ScreenInMC.BuiltInGUIs;

import cn.mingbai.ScreenInMC.Controller.EditGUI;
import cn.mingbai.ScreenInMC.Core;
import cn.mingbai.ScreenInMC.Main;
import cn.mingbai.ScreenInMC.Utils.ImageUtils.ImageUtils;
import cn.mingbai.ScreenInMC.Utils.ImmediatelyCancellableBukkitRunnable;
import cn.mingbai.ScreenInMC.Utils.Utils;
import cn.mingbai.ScreenInMC.VideoProcessor;
import org.bukkit.Material;

import java.awt.geom.Rectangle2D;
import java.net.URI;
import java.util.LinkedHashMap;

public class VideoPlayer extends Core {

    private boolean isPlaying;
    private VideoProcessor.DitheredVideo video;
    private ImmediatelyCancellableBukkitRunnable playRunnable = null;
    private boolean isPause = false;

    public VideoPlayer() {
        super("VideoPlayer");
    }

    public String getPath() {
        VideoPlayerStoredData data = ((VideoPlayerStoredData)getStoredData());
        return data.path;
    }

    public void setPath(String path) {
        VideoPlayerStoredData data = ((VideoPlayerStoredData)getStoredData());
        data.path = path;
    }

    public boolean isLoop() {
        VideoPlayerStoredData data = ((VideoPlayerStoredData)getStoredData());
        return data.loop;
    }

    public void setLoop(boolean loop) {
        VideoPlayerStoredData data = ((VideoPlayerStoredData)getStoredData());
        data.loop = loop;
    }
    public static class VideoPlayerStoredData implements StoredData{
        public String path=null;
        public boolean loop=false;
        public int frameRateLimit = Main.defaultFrameRateLimit;
        public int scaleMode = 1;

        @Override
        public StoredData clone() {
            VideoPlayerStoredData newData =new VideoPlayerStoredData();
            newData.path = path;
            newData.loop = loop;
            newData.frameRateLimit =frameRateLimit;
            return newData;
        }
    }


    @Override
    public StoredData createStoredData() {
        return new VideoPlayerStoredData();
    }

    @Override
    public void onCreate() {
        setLock=new Object();
        try {
            VideoPlayerStoredData data = ((VideoPlayerStoredData)getStoredData());
            if (data != null) {
                if (data.path != null) {
                    play();
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void stop() {
        if (isPlaying && playRunnable != null) {
            isPlaying = false;
            isPause = true;
            playRunnable.cancel();
            while (playRunnable != null) {
                try {
                    Thread.sleep(50);
                } catch (Exception e) {
                }
            }
        }
    }

    public void play() {
        stop();
        if (!isPlaying) {
            VideoPlayerStoredData data = ((VideoPlayerStoredData)getStoredData());
            if(data.path==null||data.path.length()==0) return;
            int fps = 20;
            int toWidth = getScreen().getWidth()*128;
            int toHeight = getScreen().getHeight()*128;
            if(setLock !=null&&data.frameRateLimit>=1&&data.frameRateLimit<=20) {
                synchronized (setLock) {
                    fps=data.frameRateLimit;
                }
            }
            final int scaleMode;
            synchronized (setLock) {
                scaleMode=data.scaleMode;
            }
            int needToWait = 1000/fps;
            try {
                video = VideoProcessor.readDitheredVideoWithPlugin(new URI(data.path), data.loop);
            } catch (Exception e) {
                e.printStackTrace();
                stop();
                return;
            }
            isPlaying = true;
            isPause = false;
            playRunnable = new ImmediatelyCancellableBukkitRunnable() {
                @Override
                public void run() {
                    getScreen().clearScreen();
                    while (isPlaying && !playRunnable.isCancelled()) {
                        if (isPause) {
                            try {
                                Thread.sleep(needToWait);
                                continue;
                            } catch (Exception e) {
                            }
                        }
                        long start = System.currentTimeMillis();
                        byte[] data = video.readAFrame();
                        if (data.length==0){
                            playRunnable = null;
                            isPlaying = false;
                            isPause = true;
                            video=null;
                            stop();
                            return;
                        }
                        if(!getScreen().canSleep()) {
                            Utils.Pair<byte[], Rectangle2D.Float> scaled = ImageUtils.scaleMapColorsAndGetPosition(data, scaleMode, video.getWidth(), video.getHeight(), toWidth, toHeight);
                            getScreen().sendView(scaled.getKey(), (int) scaled.getValue().x, (int) scaled.getValue().y, (int) scaled.getValue().width, (int) scaled.getValue().height);
                        }
                        long wait = needToWait - (System.currentTimeMillis() - start);
                        if (wait > 0) {
                            try {
                                Thread.sleep(wait);
                            } catch (Exception e) {
                            }
                        }
                    }
                    playRunnable = null;
                    isPlaying = false;
                    isPause = true;
                    video=null;
                }
            };
            playRunnable.runTaskAsynchronously(Main.thisPlugin());
        }
    }

    public void setPause(boolean isPause) {
        if(video==null) {this.isPause = true;return;}
        this.isPause = isPause;
    }

    @Override
    public void onUnload() {
        stop();
    }

    @Override
    public void onMouseClick(int x, int y, Utils.MouseClickType type) {

    }

    @Override
    public void onTextInput(String text) {

    }

    public boolean isPause() {
        if(video==null)return true;
        return isPause;
    }

    @Override
    public void addToEditGUI() {
        EditGUI.registerCoreInfo(new EditGUI.EditGUICoreInfo(
                "@controller-editor-cores-video-player-name",
                this,
                "@controller-editor-cores-video-player-details",
                "gold",
                Material.ITEM_FRAME,
                new LinkedHashMap(){
                    {
                        put("@controller-editor-cores-video-player-path",String.class);
                        put("@controller-editor-cores-scale-mode", ImageViewer.ScaleModeSettingsList.class);
                        put("@controller-editor-cores-video-player-loop",Boolean.class);
                        put("@controller-editor-cores-video-player-pause", Boolean.class);
                        put("@controller-editor-cores-frame-rate-limit", Integer.class);
                    }
                }));
    }
    @Override
    public Object getEditGUISettingValue(String name) {
        VideoPlayerStoredData data = (VideoPlayerStoredData)getStoredData();
        switch (name){
            case "@controller-editor-cores-frame-rate-limit":
                return (int)data.frameRateLimit;
            case "@controller-editor-cores-video-player-path":
                return (String)data.path;
            case "@controller-editor-cores-video-player-loop":
                return (boolean)data.loop;
            case "@controller-editor-cores-video-player-pause":
                return isPause();
            case "@controller-editor-cores-scale-mode":
                return data.scaleMode;
        }
        return null;
    }
    @Override
    public void setEditGUISettingValue(String name, Object value) {
        VideoPlayerStoredData data = (VideoPlayerStoredData)getStoredData();
        switch (name) {
            case "@controller-editor-cores-frame-rate-limit":
                int v = (int)value;
                if(v>=1&&v<=20){
                    if(setLock !=null) {
                        stop();
                        synchronized (setLock) {
                            data.frameRateLimit = v;
                        }
                        play();
                    }
                }
                break;
            case "@controller-editor-cores-video-player-path":
                if(setLock !=null) {
                    stop();
                    synchronized (setLock) {
                        data.path = (String) value;
                    }
                    play();
                }
                break;
            case "@controller-editor-cores-video-player-loop":
                if(setLock !=null) {
                    stop();
                    synchronized (setLock) {
                        data.loop = (boolean) value;
                    }
                    play();
                }
                break;
            case "@controller-editor-cores-video-player-pause":
                setPause((Boolean) value);
                if(video==null&&((Boolean)value)==false){
                    play();
                }
                break;
            case "@controller-editor-cores-scale-mode":
                if(setLock !=null) {
                    stop();
                    synchronized (setLock) {
                        data.scaleMode = (int) value;
                    }
                    play();
                }
                break;
        }
    }
    private Object setLock =null;
}
