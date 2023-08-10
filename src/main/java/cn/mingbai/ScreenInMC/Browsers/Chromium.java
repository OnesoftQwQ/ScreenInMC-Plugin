package cn.mingbai.ScreenInMC.Browsers;

import cn.mingbai.ScreenInMC.BuiltInGUIs.WebBrowser;
import cn.mingbai.ScreenInMC.Main;
import cn.mingbai.ScreenInMC.Screen.Screen;
import cn.mingbai.ScreenInMC.Utils.FileUtils;
import cn.mingbai.ScreenInMC.Utils.LangUtils;
import cn.mingbai.ScreenInMC.Utils.Utils;
import com.google.common.collect.Maps;
import com.google.gson.Gson;
import com.google.gson.internal.LinkedTreeMap;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitRunnable;
import org.cef.browser.CefBrowser;
import org.cef.browser.CefFrame;
import org.cef.browser.CefMessageRouter;
import org.cef.callback.CefCallback;
import org.cef.callback.CefQueryCallback;
import org.cef.callback.CefSchemeHandlerFactory;
import org.cef.callback.CefSchemeRegistrar;
import org.cef.handler.CefLoadHandler;
import org.cef.handler.CefMessageRouterHandlerAdapter;
import org.cef.handler.CefResourceHandler;
import org.cef.handler.CefResourceHandlerAdapter;
import org.cef.misc.IntRef;
import org.cef.misc.StringRef;
import org.cef.network.CefRequest;
import org.cef.network.CefResponse;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintStream;
import java.net.URI;
import java.net.URL;
import java.net.URLConnection;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.logging.Logger;

public class Chromium extends Browser {
    public static final String[] CHROMIUM_LIBRARIES;

    static {
        Utils.Pair<String, String> system = Utils.getSystem();
        String systemName = system.getKey() + "-" + system.getValue();
        CHROMIUM_LIBRARIES = new String[]{
                "gluegen-rt-natives-" + systemName + ".jar",
                "gluegen-rt.jar",
                "jcef-tests.jar",
                "jcef.jar",
                "jogl-all-natives-" + systemName + ".jar",
                "jogl-all.jar"
        };
    }

    public Chromium() {
        super("Chromium");
    }

    @Override
    public void installCore() {
        Logger logger = Main.getPluginLogger();
        logger.info(LangUtils.getText("start-download-chromium-core"));
        int type = Main.getConfiguration().getInt("download-browser-core.jcef-download-url.type");
        String downloadUrl = "";
        String httpProxyUrl = Main.getConfiguration().getString("download-browser-core.http-proxy");
        switch (type) {
            case 0:
                downloadUrl = Main.getConfiguration().getString("download-browser-core.jcef-download-url.url");
                break;
            case 1:
                //https://github.com/jcefmaven/jcefbuild
                String githubProxyUrl = Main.getConfiguration().getString("download-browser-core.github-proxy");
                String repoUrl = Main.getConfiguration().getString("download-browser-core.jcef-download-url.url");
                String version = Main.getConfiguration().getString("download-browser-core.jcef-download-url.version");
                String systemType = Main.getConfiguration().getString("download-browser-core.jcef-download-url.system-type");
                String systemName;
                String systemArch;
                if (systemType.length() == 0) {
                    Utils.Pair<String, String> systemNameAndArch = Utils.getSystem();
                    systemName = systemNameAndArch.getKey();
                    systemArch = systemNameAndArch.getValue();
                } else {
                    String[] systemNameAndArch = systemType.split("-", 1);
                    systemName = systemNameAndArch[0];
                    systemArch = systemNameAndArch[1];
                }
                if (systemName == null) {
                    throw new RuntimeException("Current system is not supported: " + systemName + ".");
                }
                if (systemArch == null) {
                    throw new RuntimeException("Current arch is not supported: " + systemArch + ".");
                }
                if (version.length() == 0) {
                    String repoReleasesUrl = repoUrl.replace("github.com", "api.github.com/repos") + "/releases";
                    String repoReleasesString = FileUtils.getString(repoReleasesUrl, httpProxyUrl);
                    Gson gson = new Gson();
                    FileUtils.GithubReleasesObject[] repoReleasesObject = gson.fromJson(repoReleasesString, FileUtils.GithubReleasesObject[].class);
                    if (repoReleasesObject.length == 0) {
                        throw new RuntimeException("Get Github Repositories TagName failed.");
                    }
                    version = repoReleasesObject[0].tag_name;
                }
                downloadUrl = repoUrl + "/releases/download/" + version + "/" + systemName + "-" + systemArch + ".tar.gz";
                if (githubProxyUrl.length() != 0) {
                    downloadUrl = githubProxyUrl.replace("%URL%", downloadUrl);
                }
                break;
        }
        String progressText1 = LangUtils.getText("download-progress");
        String progressText2 = LangUtils.getText("decompress-progress");
        String path1 = Main.PluginFilesPath + "Temp/jcef.tar.gz";
        String path2 = Main.PluginFilesPath + "Chromium/";
        FileUtils.downloadFile(downloadUrl, path1, httpProxyUrl, new Function<Utils.Pair<Long, Long>, Void>() {
            long count = 0;

            @Override
            public Void apply(Utils.Pair<Long, Long> progress) {
                count++;
                if (count % 10 == 0) {
                    logger.info(progressText1.replace("%%", String.format("%.2f", (float) ((double) progress.getValue() /
                            (double) progress.getKey() * 100.0)) + "%"));
                }
                return null;
            }
        });
        logger.info(LangUtils.getText("download-success"));
        FileUtils.decompressTarGz(path1, path2, new Function<String, Void>() {
            @Override
            public Void apply(String name) {
                logger.info(progressText2.replace("%%", name));
                return null;
            }
        });
        logger.info(LangUtils.getText("decompress-success"));

    }

    @Override
    public void loadCore() {
        if (getCoreState() == LOADED) {
            return;
        }
        try {
            Utils.loadLibrariesLoader();
            for (int i = 0; i < CHROMIUM_LIBRARIES.length; i++) {
                URL url = new File(Main.PluginFilesPath + "Chromium/bin/" + CHROMIUM_LIBRARIES[i]).toURI().toURL();
                Utils.loadJar(url);
            }
            Chromium_.load();
        } catch (Exception e) {
            e.printStackTrace();
            throw new RuntimeException(e);
        }
    }

    @Override
    public int getCoreState() {
        if (new File(Main.PluginFilesPath + "Chromium/bin").exists()) {
            try {
                Chromium_.getState();
            } catch (Throwable e) {
                return INSTALLED_NOT_LOADED;
            }
            if (Chromium_.app == null) {
                return INSTALLED_NOT_LOADED;
            }
            return LOADED;
        } else {
            return NOT_INSTALLED;
        }
    }

    @Override
    public void createBrowser(Screen screen, int width, int height,String defaultURI) {
        Chromium_.createBrowser(screen, width, height,defaultURI);
    }

    @Override
    public void executeJavascript(Screen screen, String script) {
        Chromium_.executeJavascript(screen, script);
    }

    @Override
    public void destroyBrowser(Screen screen) {
        Chromium_.destroyBrowser(screen);
    }

    @Override
    public void clickAt(Screen screen, int x, int y, Utils.MouseClickType type) {
        Chromium_.clickAt(screen, x, y, type);
    }

    @Override
    public void inputText(Screen screen, String text) {
        Chromium_.inputText(screen, text);
    }

    @Override
    public Utils.Pair<Utils.Pair<Integer, Integer>, int[]> onRender(Screen screen) {
        return Chromium_.onRender(screen);
    }

    @Override
    public void unloadCore() {
        Chromium_.unload();
    }

    @Override
    public void openURL(Screen screen, String url) {
        Chromium_.openURL(screen, url);
    }

    @Override
    public void refreshPage(Screen screen) {
        Chromium_.refreshPage(screen);
    }

    @Override
    public String getNowURL(Screen screen) {
        return Chromium_.getNowURL(screen);
    }

    @Override
    public boolean isInDeveloperMode(Screen screen) {
        return Chromium_.isInDeveloperMode(screen);
    }

    @Override
    public void setDeveloperMode(Screen screen, boolean enable) {
        Chromium_.setDeveloperMode(screen,enable);
    }

    private static class Chromium_ {
        private static org.cef.CefApp app;
        private static org.cef.CefClient client;
        private static Map<Screen, org.cef.browser.ScreenInMCChromiumBrowser> clients = new HashMap<>();
        public static org.cef.CefApp.CefAppState getState(){
            return ChromiumLibrariesLoader.getState();
        }

        public static void openURL(Screen screen, String url) {
            org.cef.browser.CefBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.loadURL(url);
            }
        }

        public static void refreshPage(Screen screen) {
            org.cef.browser.CefBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.reload();
            }
        }

        private static void load() {
            Utils.Pair<String, String> system = Utils.getSystem();
            String systemName = "";
            switch (system.getKey()) {
                case "windows":
                    systemName = "win";
                    break;
                case "linux":
                    systemName = "linux";
                    break;
            }
            switch (system.getValue()) {
                case "amd64":
                case "arm64":
                    systemName += "64";
                    break;
                case "i386":
                case "arm":
                    systemName += "32";
                    break;
            }
            System.setProperty("java.library.path", System.getProperty("java.library.path") + ";" +
                    new File(Main.PluginFilesPath + "Chromium/bin/lib/" + systemName + "/").getAbsolutePath());
            cn.mingbai.ScreenInMC.Browsers.ChromiumLibrariesLoader.load(Main.PluginFilesPath, systemName, Utils.getLibraryPrefix(system.getKey()));
            org.cef.CefSettings settings = new org.cef.CefSettings();

            settings.windowless_rendering_enabled = true;
            List<String> argsList = new ArrayList<>();
            if(!Main.getConfiguration().getBoolean("jcef-transparent")){
                settings.background_color= settings.new ColorType(255,255,255,255);
                argsList.add("--transparent-painting-disabled");
                argsList.add("--off-screen-rendering-enabled");
                argsList.add("--off-screen-frame-rate=20");
                argsList.add("--universal-access-from-file-urls-allowed");
            }
            argsList.addAll(Arrays.asList(Main.getConfiguration().getString("jcef-extra-args").split(" ")));
            String[] args = argsList.toArray(new String[0]);
            try {
                org.cef.CefApp.addAppHandler(new org.cef.handler.CefAppHandlerAdapter(args) {
                    @Override
                    public void stateHasChanged(org.cef.CefApp.CefAppState state) {
                        if (state == org.cef.CefApp.CefAppState.TERMINATED) {
                            app = null;
                            client = null;
                        }
                    }

                    @Override
                    public void onRegisterCustomSchemes(CefSchemeRegistrar registrar) {
                        registrar.addCustomScheme("screen",false, true, true, false, false, true, true);
                    }

                });
            } catch (Exception e) {
            }
//            try {
//                Field field = org.cef.CefApp.class.getDeclaredField("state_");
//                field.setAccessible(true);
//                field.set(null, org.cef.CefApp.CefAppState.NONE);
//            } catch (Exception e) {
//                e.printStackTrace();
//            }

            try {
                app = org.cef.CefApp.getInstance(args, settings);
                app = ChromiumLibrariesLoader.getApp();
            } catch (Exception e) {
                app = ChromiumLibrariesLoader.getApp();
            }

            client = app.createClient();

            app.registerSchemeHandlerFactory("screen", "local", new CefSchemeHandlerFactory() {
                @Override
                public CefResourceHandler create(CefBrowser browser, CefFrame frame, String schemeName, CefRequest request) {
                    return new CefResourceHandlerAdapter() {
                        URI uri;
                        byte[] data = new byte[0];
                        int read = 0;
                        int remain;
                        @Override
                        public boolean processRequest(CefRequest request, CefCallback callback) {
                            if(request.getMethod().equalsIgnoreCase("get")){
                                request.setHeaderByName("Accept","*/*",true);
                                try {
                                    uri = new URI(request.getURL());
                                    data = Utils.getDataFromURI(uri,true);
                                }catch (Throwable e){
                                    uri=null;
                                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                                    PrintStream ps = new PrintStream(outputStream);
                                    e.printStackTrace(ps);
                                    String message = outputStream.toString();
                                    ps.close();
                                    data = ("<html><body style=\"background-color: white;\">"+message.replace("\n","<br>")+"</body></html>").getBytes(StandardCharsets.US_ASCII);
                                }
                                read=0;
                                remain=data.length;
                                callback.Continue();
                                return true;
                            }
                            callback.cancel();
                            return false;
                        }

                        @Override
                        public void getResponseHeaders(CefResponse response, IntRef responseLength, StringRef redirectUrl) {

                            if(uri==null){
                                response.setMimeType("text/html");
                                response.setHeaderByName("Content-Type","text/html; charset=utf-8",true);
                                response.setStatus(500);
                                response.setStatusText("Internal Server Error");
                            }else {
                                response.setMimeType(URLConnection.getFileNameMap().getContentTypeFor(uri.getRawPath()));
                                response.setStatus(200);
                                response.setStatusText("OK");
                            }
                            response.setError(CefLoadHandler.ErrorCode.ERR_NONE);

                            responseLength.set(data.length);
                        }

                        @Override
                        public boolean readResponse(byte[] dataOut, int bytesToRead, IntRef bytesRead, CefCallback callback) {
                            int bytesToCopy = Math.min(bytesToRead, remain);
                            System.arraycopy(data, read, dataOut, 0, bytesToCopy);
                            this.read += bytesToCopy;
                            this.remain -= bytesToCopy;
                            bytesRead.set(bytesToCopy);
                            if(bytesToCopy==0){
                                return false;
                            }
                            return true;
                        }

                        @Override
                        public void cancel() {
                        }
                    };
                }
            });

            client.addRequestHandler(new org.cef.handler.CefRequestHandlerAdapter() {
                @Override
                public boolean onOpenURLFromTab(org.cef.browser.CefBrowser browser, org.cef.browser.CefFrame frame, String target_url, boolean user_gesture) {
                    browser.loadURL(target_url);
                    return true;
                }
            });
            client.addLifeSpanHandler(new org.cef.handler.CefLifeSpanHandlerAdapter() {
                @Override
                public boolean onBeforePopup(org.cef.browser.CefBrowser browser, org.cef.browser.CefFrame frame, String target_url, String target_frame_name) {
                    browser.loadURL(target_url);
                    return true;
                }
            });
            CefMessageRouter.CefMessageRouterConfig messageConfig=new CefMessageRouter.CefMessageRouterConfig("ScreenInMC","ScreenInMCCancel");
            CefMessageRouter message=CefMessageRouter.create(messageConfig);
            message.addHandler(new CefMessageRouterHandlerAdapter() {
                @Override
                public boolean onQuery(CefBrowser browser, CefFrame frame, long queryId, String request, boolean persistent, CefQueryCallback callback) {
                    if(browser.getURL().startsWith("screen://")){
                        for(Screen screen:clients.keySet()){
                            if(browser.equals(clients.get(screen))){
                                LinkedTreeMap map;
                                try{
                                    map = Main.getGson().fromJson(request, LinkedTreeMap.class);
                                }catch (Exception e){
                                    LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                    messageMap.put("error", "The requested content could not be parsed. " +
                                            "It should be in JSON format. \n"+e.getMessage());
                                    callback.failure(-3,Main.getGson().toJson(messageMap));
                                    return true;
                                }

                                if(map.containsKey("action")){
                                    try {
                                        switch ((String) map.get("action")) {
                                            case "run_command":
                                                if(map.containsKey("command")){
                                                    try {
                                                        String command = (String) map.get("command");
                                                        final Object[] success = new Object[]{true,null};
                                                        BukkitRunnable runnable = new BukkitRunnable() {

                                                            @Override
                                                            public void run() {
                                                                try{
                                                                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                                                                }catch (Exception e){
                                                                    success[0] = false;
                                                                    success[1] =e;
                                                                }
                                                            }
                                                        };
                                                        runnable.runTask(Main.thisPlugin());
                                                        if((Boolean) success[0]){
                                                            LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                                            messageMap.put("result",0);
                                                            callback.success(Main.getGson().toJson(messageMap));
                                                        }else if(success[1]!=null){
                                                            throw (Exception) success[1];
                                                        }
                                                    }catch (Exception e){
                                                        LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                                        messageMap.put("error", "Command dispatch error. "+e.getMessage());
                                                        callback.failure(-7,Main.getGson().toJson(messageMap));
                                                    }
                                                }else{
                                                    LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                                    messageMap.put("error", "Command key not found.");
                                                    callback.failure(-6,Main.getGson().toJson(messageMap));
                                                }
                                                break;
                                            case "listen_redstone_input":
                                                if(map.containsKey("id")){
                                                    int id=-1;
                                                    try {
                                                        id = ((Number)map.get("id")).intValue();
                                                    }catch (Exception e){
                                                    }
                                                    if(id>=1&&id<=54){
                                                        RedstoneInputCallback cb = addRedstoneInputListener(new RedstoneInputCallback(screen,id) {
                                                            @Override
                                                            public void onInput(int value) {
                                                                LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                                                messageMap.put("type", "redstone_input");
                                                                messageMap.put("id", getID());
                                                                messageMap.put("value",value);
                                                                messageMap.put("callback_id",getCallbackID());
                                                                browser.executeJavaScript("window.postMessage("+Main.getGson().toJson(messageMap)+")","",0);
                                                            }
                                                        });
                                                        LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                                        messageMap.put("result",0);
                                                        messageMap.put("callback_id",cb.getCallbackID());
                                                        callback.success(Main.getGson().toJson(messageMap));

                                                        break;
                                                    }
                                                }
                                                LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                                messageMap.put("error", "id key not found or is wrong. Should be in 1-54.");
                                                callback.failure(-8,Main.getGson().toJson(messageMap));
                                                break;
                                            case "stop_listen_redstone_input":
                                                if(map.containsKey("callback_id")){
                                                    long id=-1;
                                                    try {
                                                        id = ((Number)map.get("callback_id")).longValue();
                                                    }catch (Exception e){
                                                    }
                                                    if(id!=-1){
                                                        removeRedstoneInputListener(id);
                                                        messageMap = Maps.newLinkedHashMap();
                                                        messageMap.put("result",0);
                                                        callback.success(Main.getGson().toJson(messageMap));
                                                        break;
                                                    }
                                                }
                                                messageMap = Maps.newLinkedHashMap();
                                                messageMap.put("error", "callback_id key not found or is wrong.");
                                                callback.failure(-9,Main.getGson().toJson(messageMap));
                                                break;
                                            case "redstone_output":
                                                if(map.containsKey("id")&&map.containsKey("value")){
                                                    int id=-1;
                                                    int value = -1;
                                                    try {
                                                        id = ((Number)map.get("id")).intValue();
                                                        value = ((Number)map.get("value")).intValue();
                                                    }catch (Exception e){
                                                    }
                                                    if(id>=1&&id<=54&&value>=0&&value<=15){
                                                        if(screen.getCore() instanceof WebBrowser){
                                                            WebBrowser webBrowser = (WebBrowser) screen.getCore();
                                                            webBrowser.redstoneOutput(id,value);
                                                        }
                                                        messageMap = Maps.newLinkedHashMap();
                                                        messageMap.put("result",0);
                                                        callback.success(Main.getGson().toJson(messageMap));
                                                        break;
                                                    }
                                                }
                                                messageMap = Maps.newLinkedHashMap();
                                                messageMap.put("error", "id, value key not found or is wrong. id should be in 1-54, value should be in 0-15.");
                                                callback.failure(-10,Main.getGson().toJson(messageMap));
                                                break;
                                            default:
                                                messageMap = Maps.newLinkedHashMap();
                                                messageMap.put("error", "Unknown action: "+map.get("action"));
                                                callback.failure(-5,Main.getGson().toJson(messageMap));
                                        }


                                        return true;
                                    }catch (Exception e){
                                        LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                        messageMap.put("error", "Unknown error: "+e.getMessage());
                                        callback.failure(-999,Main.getGson().toJson(messageMap));
                                    }
                                }else{
                                    LinkedHashMap messageMap = Maps.newLinkedHashMap();
                                    messageMap.put("error", "Action key not found.");
                                    callback.failure(-4,Main.getGson().toJson(messageMap));
                                    return true;
                                }
                            }
                        }
                        LinkedHashMap messageMap = Maps.newLinkedHashMap();
                        messageMap.put("error", "The corresponding screen for this webpage cannot be found. " +
                                "Perhaps this screen has already been removed.");
                        callback.failure(-2,Main.getGson().toJson(messageMap));
                    }else{
                        LinkedHashMap messageMap = Maps.newLinkedHashMap();
                        messageMap.put("error","You don't have permission to call this API. " +
                                "Please place the webpages in the plugins/ScreenInMC/Files/ directory, " +
                                "and open it using screen://local/(file-name).");
                        callback.failure(-1,Main.getGson().toJson(messageMap));
                    }
                    return true;
                }

                @Override
                public void onQueryCanceled(CefBrowser browser, CefFrame frame, long queryId) {
                    super.onQueryCanceled(browser, frame, queryId);
                }

                @Override
                public void setNativeRef(String identifier, long nativeRef) {
                    super.setNativeRef(identifier, nativeRef);
                }

                @Override
                public long getNativeRef(String identifier) {
                    return super.getNativeRef(identifier);
                }
            },true);
            client.addMessageRouter(message);
        }


        private static void createBrowser(Screen screen, int width, int height,String defaultURI) {
            if (client != null && app != null) {

                org.cef.browser.ScreenInMCChromiumBrowser browser = new org.cef.browser.ScreenInMCChromiumBrowser(client, defaultURI,
                        Main.getConfiguration().getBoolean("jcef-transparent"));
                browser.createImmediately();
                browser.setSize(width, height);
                synchronized (clients) {
                    clients.put(screen, browser);
                }
            }
        }

        private static void executeJavascript(Screen screen, String script) {
            org.cef.browser.CefBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.executeJavaScript(script, browser.getURL(), 0);
            }
        }

        private static void destroyBrowser(Screen screen) {
            org.cef.browser.CefBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.close(true);
                synchronized (clients){
                    clients.remove(browser);
                }
            }
            synchronized (Browser.getRedstoneInputListenersMap()) {
                for (Screen i : Browser.getRedstoneInputListenersMap().keySet()) {
                    if (i.equals(screen)) {
                        Browser.getRedstoneInputListenersMap().remove(screen);
                    }
                }
            }
        }

        private static void clickAt(Screen screen, int x, int y, Utils.MouseClickType type) {
            org.cef.browser.ScreenInMCChromiumBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.clickAt(x, y, type.getCode());
            }
        }

        private static void inputText(Screen screen, String text) {
            org.cef.browser.ScreenInMCChromiumBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.inputText(text);
            }
        }

        private static Utils.Pair<Utils.Pair<Integer, Integer>, int[]> onRender(Screen screen) {
            org.cef.browser.ScreenInMCChromiumBrowser browser = clients.get(screen);
            if (browser != null) {
                Utils.Pair image;
                try {
                    synchronized (browser) {
                        int[] size = browser.getImageSize();
                        int[] newImage = new int[size[0] * size[1]];
                        byte[] imageData =  browser.getImageData();
                        for (int i = 0; i < newImage.length; i++) {
                            newImage[i] =imageData[i * 4] & 0xFF |
                                    (imageData[i * 4 + 1] & 0xFF) << 8 |
                                    (imageData[i * 4 + 2] & 0xFF) << 16 |
                                    (imageData[i * 4 + 3] & 0xFF) << 24;
                        }
                        image = new Utils.Pair<>(new Utils.Pair<>(size[0], size[1]), newImage);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                    image = new Utils.Pair(new Utils.Pair<>(0, 0), new int[0]);
                }
                return image;
            }
            return null;
        }

        private static void unload() {
            try {
                client.dispose();
            } catch (Exception e) {
            }
//            try {
//                app.dispose();
//            }catch (Exception e){}
        }
        private static String getNowURL(Screen screen) {
            org.cef.browser.ScreenInMCChromiumBrowser browser = clients.get(screen);
            if (browser != null) {
                return browser.getURL();
            }
            return "";
        }

        public static boolean isInDeveloperMode(Screen screen) {
            org.cef.browser.ScreenInMCChromiumBrowser browser = clients.get(screen);
            if (browser != null) {
                return browser.isInDevTools();
            }
            return false;
        }

        public static void setDeveloperMode(Screen screen, boolean enable) {
            org.cef.browser.ScreenInMCChromiumBrowser browser = clients.get(screen);
            if (browser != null) {
                browser.setDevTools(enable);
            }
        }
    }
}
