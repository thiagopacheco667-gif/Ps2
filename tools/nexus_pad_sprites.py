import math, os, sys
from PIL import Image, ImageDraw, ImageFilter, ImageFont, ImageChops
OUT=sys.argv[1]
K=4      # final scale vs old sprite
SS=4     # supersampling
FONT='/usr/share/fonts/opentype/inter/Inter-Bold.otf'
if not os.path.exists(FONT):
    import glob; FONT=[f for f in glob.glob('/usr/share/fonts/**/*nter*Bold*',recursive=True)][0]

BASE=(22,27,40)        # glass fill (from #141A27)
PRESS=(130,87,255)     # Nexus violet
SYM={'cross':(124,170,255),'circle':(255,96,116),'square':(232,140,255),'triangle':(62,214,181)}

def canvas(w,h): return Image.new('RGBA',(w,h),(0,0,0,0))

def body(mask, pressed, alpha=205):
    """Glass body from a shape mask: fill + vertical sheen + rim light + soft inner shadow."""
    w,h=mask.size
    fill=PRESS if pressed else BASE
    a=235 if pressed else alpha
    img=Image.new('RGBA',(w,h),fill+(0,))
    img.putalpha(mask.point(lambda v: v*a//255))
    # vertical sheen (lighter on top)
    grad=Image.new('L',(1,h))
    for y in range(h): grad.putpixel((0,y), int((1-y/h)**1.6*(70 if pressed else 46)))
    grad=grad.resize((w,h))
    sheen=Image.new('RGBA',(w,h),(255,255,255,0)); sheen.putalpha(ImageChops.multiply(grad,mask))
    img=Image.alpha_composite(img,sheen)
    # rim: mask minus eroded mask, brighter at top
    rimw=max(2,int(min(w,h)*0.035))
    inner=mask.filter(ImageFilter.MinFilter(rimw*2+1))
    rim=ImageChops.subtract(mask,inner)
    rg=Image.new('L',(1,h))
    for y in range(h): rg.putpixel((0,y), int(150 - 105*(y/h)) if not pressed else int(230-80*(y/h)))
    rg=rg.resize((w,h))
    rimimg=Image.new('RGBA',(w,h),(255,255,255,0)); rimimg.putalpha(ImageChops.multiply(rim,rg))
    img=Image.alpha_composite(img,rimimg)
    return img

def finish(img, size):
    return img.resize(size, Image.LANCZOS)

def label(img, text, pressed, rel=0.38, dy=0):
    w,h=img.size
    d=ImageDraw.Draw(img)
    f=ImageFont.truetype(FONT, int(min(w,h)*rel))
    bb=d.textbbox((0,0),text,font=f)
    tw,th=bb[2]-bb[0],bb[3]-bb[1]
    d.text(((w-tw)/2-bb[0],(h-th)/2-bb[1]+dy*h),text,font=f,fill=(255,255,255,255) if pressed else (232,234,245,235))

def save(img,name,old):
    W,H=old
    finish(img,(W*K,H*K)).save(os.path.join(OUT,name+'.png'),optimize=True)

def rr_mask(w,h,r,shape=None):
    m=Image.new('L',(w,h),0); d=ImageDraw.Draw(m)
    if shape: d.polygon(shape,fill=255)
    else: d.rounded_rectangle([0,0,w-1,h-1],radius=r,fill=255)
    return m

def round_poly(points, r, w, h):
    m=Image.new('L',(w,h),0); d=ImageDraw.Draw(m); d.polygon(points,fill=255)
    # round corners: blur then threshold
    m=m.filter(ImageFilter.GaussianBlur(r)).point(lambda v: 255 if v>128 else 0)
    return m.filter(ImageFilter.GaussianBlur(1.2))

S=lambda v: v*K*SS

# face buttons 57x57 (pressed 60x60)
def face(kind, pressed, old):
    W,H=old; w,h=S(W),S(H)
    m=Image.new('L',(w,h),0); ImageDraw.Draw(m).ellipse([w*0.03,h*0.03,w*0.97,h*0.97],fill=255)
    img=body(m,pressed)
    d=ImageDraw.Draw(img); c=SYM[kind]; col=(255,255,255,255) if pressed else c+(255,)
    lw=int(w*0.075); cx,cy=w/2,h/2; r=w*0.24
    if kind=='circle': d.ellipse([cx-r,cy-r,cx+r,cy+r],outline=col,width=lw)
    elif kind=='square': d.rounded_rectangle([cx-r*0.92,cy-r*0.92,cx+r*0.92,cy+r*0.92],radius=lw*0.4,outline=col,width=lw)
    elif kind=='cross':
        for a in (45,135):
            dx,dy=math.cos(math.radians(a))*r*1.12,math.sin(math.radians(a))*r*1.12
            d.line([cx-dx,cy-dy,cx+dx,cy+dy],fill=col,width=lw)
        for p in [(cx-r*0.79,cy-r*0.79),(cx+r*0.79,cy+r*0.79),(cx+r*0.79,cy-r*0.79),(cx-r*0.79,cy+r*0.79)]:
            d.ellipse([p[0]-lw/2,p[1]-lw/2,p[0]+lw/2,p[1]+lw/2],fill=col)
    elif kind=='triangle':
        pts=[(cx,cy-r*1.05),(cx+r*1.05,cy+r*0.72),(cx-r*1.05,cy+r*0.72)]
        d.line(pts+[pts[0],pts[1]],fill=col,width=lw,joint='curve')
    return img

for kind in ('cross','circle','square','triangle'):
    save(face(kind,False,(57,57)),'pad_'+kind,(57,57))
for kind in ('cross','triangle'):
    save(face(kind,True,(60,60)),'pad_'+kind+'_pressed',(60,60))

# shoulders L1/R1 69x40, L2/R2 71x63
for name,(W,H) in {'l1':(69,40),'r1':(69,40)}.items():
    for p in (False,True):
        w,h=S(W),S(H); m=Image.new('L',(w,h),0); ImageDraw.Draw(m).rounded_rectangle([w*0.02,h*0.04,w*0.98,h*0.96],radius=int(h*0.42),fill=255)
        img=body(m,p); label(img,name.upper(),p,rel=0.42)
        save(img,'pad_'+name+('_pressed' if p else ''),(W,H))
for name,(W,H) in {'l2':(71,63),'r2':(71,63)}.items():
    for p in (False,True):
        w,h=S(W),S(H)
        # trigger: rounded rect with a taller rounded top
        m=Image.new('L',(w,h),0); d=ImageDraw.Draw(m)
        d.rounded_rectangle([0,h*0.12,w-1,h-1],radius=int(h*0.28),fill=255)
        d.ellipse([w*0.02,0,w*0.98,h*0.55],fill=255)
        img=body(m,p); label(img,name.upper(),p,rel=0.36,dy=0.06)
        save(img,'pad_'+name+('_pressed' if p else ''),(W,H))
# L3/R3 70 (pressed 64)
for name in ('l3','r3'):
    for p,(W,H) in ((False,(70,70)),(True,(64,64))):
        w,h=S(W),S(H); m=Image.new('L',(w,h),0); ImageDraw.Draw(m).ellipse([w*0.04,h*0.04,w*0.96,h*0.96],fill=255)
        img=body(m,p); label(img,name.upper(),p,rel=0.3)
        save(img,'pad_'+name+('_pressed' if p else ''),(W,H))
# select 42x27 (pill), start 39x26 (play triangle shape)
for p in (False,True):
    W,H=42,27; w,h=S(W),S(H); m=rr_mask(w,h,int(h*0.5))
    img=body(m,p); d=ImageDraw.Draw(img)
    # select glyph: two short bars
    col=(255,255,255,255) if p else (232,234,245,235); bw=w*0.34; bh=h*0.11
    for yy in (0.38,0.62):
        d.rounded_rectangle([w/2-bw/2,h*yy-bh/2,w/2+bw/2,h*yy+bh/2],radius=bh/2,fill=col)
    save(img,'pad_select'+('_pressed' if p else ''),(W,H))
    W,H=39,26; w,h=S(W),S(H); m=rr_mask(w,h,int(h*0.5))
    img=body(m,p); d=ImageDraw.Draw(img)
    tri=[(w*0.42,h*0.28),(w*0.62,h*0.5),(w*0.42,h*0.72)]
    d.polygon(tri,fill=col)
    save(img,'pad_start'+('_pressed' if p else ''),(W,H))
# dpad arms: left 53x44 (points right, to the centre), up 45x52 (points down)
for p in (False,True):
    W,H=53,44; w,h=S(W),S(H)
    pts=[(w*0.07,h*0.08),(w*0.68,h*0.08),(w*0.93,h*0.5),(w*0.68,h*0.92),(w*0.07,h*0.92)]
    m=round_poly(pts,int(h*0.10),w,h); img=body(m,p)
    d=ImageDraw.Draw(img); col=(255,255,255,255) if p else (232,234,245,200)
    d.polygon([(w*0.22,h*0.5),(w*0.38,h*0.34),(w*0.38,h*0.66)],fill=col)
    left=img
    save(left,'pad_dpad_left'+('_pressed' if p else ''),(W,H))
    save(left.transpose(Image.FLIP_LEFT_RIGHT),'pad_dpad_right'+('_pressed' if p else ''),(W,H))
    W,H=45,52; w,h=S(W),S(H)
    pts=[(w*0.08,h*0.07),(w*0.92,h*0.07),(w*0.92,h*0.68),(w*0.5,h*0.93),(w*0.08,h*0.68)]
    m=round_poly(pts,int(w*0.10),w,h); img=body(m,p)
    d=ImageDraw.Draw(img)
    d.polygon([(w*0.5,h*0.22),(w*0.34,h*0.38),(w*0.66,h*0.38)],fill=col)
    save(img,'pad_dpad_up'+('_pressed' if p else ''),(W,H))
# stick base 133, thumb 101
W=133; w=S(W)
m=Image.new('L',(w,w),0); ImageDraw.Draw(m).ellipse([w*0.02,w*0.02,w*0.98,w*0.98],fill=255)
img=body(m,False,alpha=150)
d=ImageDraw.Draw(img)
for rr,a in ((0.30,40),(0.42,26)):
    d.ellipse([w/2-w*rr,w/2-w*rr,w/2+w*rr,w/2+w*rr],outline=(255,255,255,a),width=max(2,w//160))
save(img,'pad_stick_base',(W,W))
W=101; w=S(W)
m=Image.new('L',(w,w),0); ImageDraw.Draw(m).ellipse([w*0.04,w*0.04,w*0.96,w*0.96],fill=255)
img=body(m,False,alpha=235)
# concave grip: radial darker centre + ring
d=ImageDraw.Draw(img)
d.ellipse([w*0.22,w*0.22,w*0.78,w*0.78],outline=(255,255,255,45),width=max(2,w//70))
d.ellipse([w*0.30,w*0.30,w*0.70,w*0.70],fill=(130,87,255,38))
save(img,'pad_thumb',(W,W))
print('done')
