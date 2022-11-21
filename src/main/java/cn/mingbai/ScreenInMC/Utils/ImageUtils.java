package cn.mingbai.ScreenInMC.Utils;

import cn.mingbai.ScreenInMC.Natives.GPUDither;
import net.minecraft.world.level.material.MaterialColor;
import org.bukkit.Bukkit;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

public class ImageUtils {
    private static ColorPalette palette;
    private static int[] palette_;

    public static int[] getPalette() {
        return palette_;
    }
    public static float[] RGBToHSL(int r,int g,int b) {
        float H, S, L, var_Min, var_Max, del_Max, del_R, del_G, del_B;
        H = 0;
        var_Min = Math.min(r, Math.min(g, b));
        var_Max = Math.max(r, Math.max(g, b));
        del_Max = var_Max - var_Min;
        L = (var_Max + var_Min) / 2;
        if (del_Max == 0) {
            H = 0;
            S = 0;
        } else {
            if (L < 128) {
                S = 256 * del_Max / (var_Max + var_Min);
            } else {
                S = 256 * del_Max / (512 - var_Max - var_Min);
            }
            del_R = ((360 * (var_Max - r) / 6) + (360 * del_Max / 2))
                    / del_Max;
            del_G = ((360 * (var_Max - g) / 6) + (360 * del_Max / 2))
                    / del_Max;
            del_B = ((360 * (var_Max - b) / 6) + (360 * del_Max / 2))
                    / del_Max;
            if (r == var_Max) {
                H = del_B - del_G;
            } else if (g == var_Max) {
                H = 120 + del_R - del_B;
            } else if (b == var_Max) {
                H = 240 + del_G - del_R;
            }
            if (H < 0) {
                H += 360;
            }
            if (H >= 360) {
                H -= 360;
            }
            if (L >= 256) {
                L = 255;
            }
            if (S >= 256) {
                S = 255;
            }
        }
        return new float[]{H, S, L};
    }
    public static void initImageUtils() {
        try {
            List<Color> colors = new ArrayList<>();
            List<Integer> colors_ = new ArrayList<>();
            for (int i = 1; i < MaterialColor.MATERIAL_COLORS.length - 1; i++) {
                MaterialColor materialColor = MaterialColor.byId(i);
                if (materialColor == null || materialColor.equals(MaterialColor.NONE)) {
                    break;
                }
                for (int b = 0; b < 4; b++) {
                    Color color = new Color(materialColor.calculateRGBColor(MaterialColor.Brightness.byId(b)), true);
                    colors.add(color);
                    float[] hsv = RGBToHSL(color.getRed(),color.getGreen(),color.getBlue());
                    colors_.add((int)(hsv[0]));
                    colors_.add((int)(hsv[1]));
                    colors_.add((int)(hsv[2]));

                }
            }
            palette = new ColorPalette(colors.toArray(new Color[0]));
            palette_ = Utils.toPrimitive(colors_.toArray(new Integer[0]));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    public static byte[] imageToMapColorsWithGPU(Image image){
        long start = System.currentTimeMillis();
        BufferedImage img = imageToBufferedImage(image);
        int height = img.getHeight();
        int width = img.getWidth();
        int[] data = img.getRGB(0,0,width,height,null,0,width);
//        Bukkit.broadcastMessage("Time: "+(System.currentTimeMillis()-start));
        return GPUDither.dither(data,width,height);
    }
    public static boolean useGPU = true;
    public static byte[] imageToMapColors(Image image) {
        if(useGPU){
            return imageToMapColorsWithGPU(image);
        }
        long start = System.currentTimeMillis();
        BufferedImage img = imageToBufferedImage(image);
        int height = img.getHeight();
        int width = img.getWidth();
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                Color color = new Color(img.getRGB(x, y), true);
                if (color.getAlpha() != 255) {
                    continue;
                }
                VectorRGB current_color = new VectorRGB(img.getRGB(x, y));
                VectorRGB closest_match = palette.getClosestMatch(current_color);
                VectorRGB error = current_color.subtract(closest_match);

                img.setRGB(x, y, closest_match.toRGB());

                if (!(x == img.getWidth() - 1)) {
                    if (((img.getRGB(x + 1, y) >> 24) & 0xff) == 255) {
                        img.setRGB(x + 1, y,
                                ((new VectorRGB(img.getRGB(x + 1, y)).add(error.scalarMultiply((float) 7 / 16)))
                                        .clip(0, 255).toRGB()));
                    }
                    if (!(y == img.getHeight() - 1)) {
                        if (((img.getRGB(x + 1, y + 1) >> 24) & 0xff) == 255) {
                            img.setRGB(x + 1, y + 1,
                                    ((new VectorRGB(img.getRGB(x + 1, y + 1)).add(error.scalarMultiply((float) 1 / 16)))
                                            .clip(0, 255).toRGB()));
                        }
                    }
                }

                if (!(y == img.getHeight() - 1)) {
                    if (((img.getRGB(x, y + 1) >> 24) & 0xff) == 255) {
                        img.setRGB(x, y + 1,
                                ((new VectorRGB(img.getRGB(x, y + 1)).add(error.scalarMultiply((float) 3 / 16)))
                                        .clip(0, 255).toRGB()));
                    }
                    if (!(x == 0)) {
                        if (((img.getRGB(x - 1, y + 1) >> 24) & 0xff) == 255) {
                            img.setRGB(x - 1, y + 1, ((new VectorRGB(img.getRGB(x - 1, y + 1))
                                    .add(error.scalarMultiply(5 / 16)).clip(0, 255).toRGB())));
                        }

                    }
                }
            }
        }
        byte[] result = new byte[height * width];
        for (int y = 0; y < height; ++y) {
            for (int x = 0; x < width; ++x) {
                int color = img.getRGB(x, y);
                int alpha = (color >> 24) & 0xff;
                int indexInt;
                if (alpha == 255) {
                    indexInt = palette.getColorIndex(color) + 4;
                } else {
                    indexInt = 0;
                }
                result[y * width + x] = (byte) ((indexInt / 4) << 2 | (indexInt % 4) & 3);
            }
        }
        Bukkit.broadcastMessage("Time: "+(System.currentTimeMillis()-start));
        return result;
    }

    public static BufferedImage imageToBufferedImage(Image img) {
        if (img instanceof BufferedImage) {
            return (BufferedImage) img;
        }
        BufferedImage bufferedImage = new BufferedImage(img.getWidth(null), img.getHeight(null), BufferedImage.TYPE_INT_ARGB);
        Graphics2D graphics = bufferedImage.createGraphics();
        graphics.drawImage(img, 0, 0, null);
        graphics.dispose();
        return bufferedImage;
    }

    public static class VectorRGB {
        public int r;
        public int g;
        public int b;

        public VectorRGB(int r, int g, int b) {
            this.r = r;
            this.b = b;
            this.g = g;
        }

        public VectorRGB(Color color) {
            this.r = color.getRed();
            this.b = color.getBlue();
            this.g = color.getGreen();
        }

        public VectorRGB(int rgb) {
            Color color = new Color(rgb);
            this.r = color.getRed();
            this.b = color.getBlue();
            this.g = color.getGreen();
        }

        public int toRGB() {
            return new Color(r, g, b).getRGB();
        }

        public Color toColor() {
            return new Color(r, g, b, 255);
        }

        public VectorRGB subtract(VectorRGB other) {
            return new VectorRGB(this.r - other.r, this.g - other.g, this.b - other.b);
        }

        public VectorRGB add(VectorRGB other) {
            return new VectorRGB(this.r + other.r, this.g + other.g, this.b + other.b);
        }

        public int fastDifferenceTo(VectorRGB other) {
            VectorRGB difference = this.subtract(other);
            return Math.abs(difference.r) + Math.abs(difference.g) + Math.abs(difference.b);
        }

        public VectorRGB scalarMultiply(float scalar) {
            return new VectorRGB((int) (this.r * scalar), (int) (this.g * scalar), (int) (this.b * scalar));
        }

        public VectorRGB clip(int minimum, int maximum) {
            VectorRGB clipped = new VectorRGB(r, g, b);
            if (clipped.r > maximum) {
                clipped.r = maximum;
            } else if (clipped.r < minimum) {
                clipped.r = minimum;
            }

            if (clipped.g > maximum) {
                clipped.g = maximum;
            } else if (clipped.g < minimum) {
                clipped.g = minimum;
            }

            if (clipped.b > maximum) {
                clipped.b = maximum;
            } else if (clipped.b < minimum) {
                clipped.b = minimum;
            }

            return clipped;

        }
    }

    public static class ColorPalette {

        private VectorRGB[] colors;

        public ColorPalette(VectorRGB[] colors) {
            this.colors = colors;
        }

        public ColorPalette(Color[] colors) {
            this.colors = new VectorRGB[colors.length];

            for (int i = 0; i < colors.length; i++) {
                this.colors[i] = new VectorRGB(colors[i]);
            }
        }

        public VectorRGB getClosestMatch(VectorRGB color) {
            int minimum_index = 0;
            int minimum_difference = colors[0].fastDifferenceTo(color);

            for (int i = 1; i < colors.length; i++) {

                int current_difference = colors[i].fastDifferenceTo(color);

                if (current_difference < minimum_difference) {
                    minimum_difference = current_difference;
                    minimum_index = i;
                }
            }

            return colors[minimum_index];
        }

        public int getColorIndex(int color) {
            for (int i = 0; i < colors.length; i++) {
                Color c1 = colors[i].toColor();
                Color c2 = new Color(color, true);
                if (c1.equals(c2)) {
                    return i;
                }
            }
            return 0;
        }
    }
}
