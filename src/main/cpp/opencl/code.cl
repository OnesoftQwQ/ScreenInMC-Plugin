void RGBToHSL(int r,int g,int b, double &H, double &S, double &L)
{
    r = r / 255;
    g = g / 255;
    b = b / 255;
    double M = max(max(r, g), b);
    double m = min(min(r, g), b);
    double d = M - m;
    if (d == 0) H = 0;
    else if (M == r)
    {
        H = ((g - b) / d);
        H = H + ((int)H % 6);
    }
    else if (M == g) H = (b - r) / d + 2;
    else H = (r - g) / d + 4;
    H *= 60;
    if (H < 0) H += 360;
    L = (M + m) / 2;
    if (d == 0)
        S = 0;
    else
        S = d / (1 - abs(2 * L - 1)); 
    S = S * 255;
    L = L * 255;
}
int* intToRgba(int rgb) {
    int result[4];
    result[0] = (rgb >> 16) & 0xff;
    result[1] = (rgb >>  8) & 0xff;
    result[2] = (rgb  ) & 0xff;
    result[3] = (rgb >> 24) & 0xff;
    return result;
}
int rgbToInt(int r,int g,int b) {
    if(r>255) {
        r=255;
        
    }
    if(g>255) {
        g=255;
    }
    if(b>255) {
        b=255;
    }
    if(r<0) {
        r=0;
    }
    if(g<0) {
        g=0;
    }
    if(b<0) {
        b=0;
    }
    return 0xFF000000 | ((r << 16) & 0x00FF0000) | ((g << 8) & 0x0000FF00) | (b & 0x000000FF);
}
int* getNearlyColors(__global int *palette,int colorCount,int r,int g,int b) {
    int* color = intToRgba(palette[0]);
    int cr=r-color[0];
    int cg=g-color[1];
    int cb=b-color[2];
    int min = abs(cr)+abs(cg)+abs(cb);
    int minIndex = 0;
    int mr = color[0];
    int mg = color[1];
    int mb = color[2];

    for (int i=1;i<colorCount;i++) {
        color = intToRgba(palette[i]);
        int pr = color[0];
        int pg = color[1];
        int pb = color[2];
        int temp = abs(r-pr)+abs(g-pg)+abs(b-pb);
        if(temp<min) {
            min = temp;
            minIndex = i;
            mr = pr;
            mg = pg;
            mb = pb;
            cr = r-mr;
            cg = r-mg;
            cb = r-mb;
        }
    }
    int result[8];
    result[0]=min;
    result[1]=minIndex+4;
    result[2]=mr;
    result[3]=mg;
    result[4]=mb;
    result[5]=cr;
    result[6]=cg;
    result[7]=cb;
    return result;
}
__kernel void dither(__global int *colors,__global int *palette,__global *settings,__global char *result) {
    int gid = get_global_id(0);
    int width = settings[0];
    int height = settings[1];
    int colorCount = settings[2];
    int size = width*height;
    int* rgba = intToRgba(colors[gid]);
    if(rgba[3]!=255) {
        result[gid]=0;
        return;
    }
    int* near = getNearlyColor(palette,colorCount,rgba[0],rgba[1],rgba[2]);
    colors[gid] = rgbToInt(near[2],near[3],near[4]);
    result[gid] = (char)((near[1] / 4) << 2 | (near[1] % 4) & 3);
    int x = gid%width;
    int y = gid/width;
    if(!(x == width)-1) {
        int index = width*y+x+1;
        int* rgba_ = intToRgba(colors[index]);
        if(rgba_[3]==255) {
            colors[index]=rgbToInt(rgba_[0]+near[5]*7/16,rgba_[1]+near[6]*7/16,rgba_[2]+near[7]*7/16);
        }
        if(!(y == height)-1) {
            int index_ = width*(y+1)+x+1;
            int* rgba__ = intToRgba(colors[index_]);
            if(rgba[3]==255) {
                colors[index_]=rgbToInt(rgba__[0]+near[5]*1/16,rgba__[1]+near[6]*1/16,rgba__[2]+near[7]*1/16);
            }
        }
    }
    if(!(y == height)-1) {
        int index = width*(y+1)+x;
        int* rgba_ = intToRgba(colors[index]);
        if(rgba_[3]==255) {
            colors[index]=rgbToInt(rgba_[0]+near[5]*7/16,rgba_[1]+near[6]*7/16,rgba_[2]+near[7]*3/16);
        }
        if(x != 0) {
            int index_ = width*(y+1)+x-1;
            int* rgba__ = intToRgba(colors[index_]);
            if(rgba[3]==255) {
                colors[index_]=rgbToInt(rgba__[0]+near[5]*1/16,rgba__[1]+near[6]*1/16,rgba__[2]+near[7]*5/16);
            }
        }
    }
}