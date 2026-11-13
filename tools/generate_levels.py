"""Original 32x26 pixel art authoring. Generated Kotlin is the runtime level data."""
from pathlib import Path
import math
W,H=32,26
PALETTE={'.':'#09162D','r':'#FF637F','p':'#FF9BCB','o':'#FFAD68','y':'#FFE27A','g':'#83E7A4','t':'#50DDE5','b':'#76A5FF','v':'#B39AFF','w':'#EFF5FD','k':'#354B7D'}
patterns=[]

def art(name):
    a=[['.']*W for _ in range(H)]
    patterns.append((name,a));return a

def rect(a,x,y,w,h,c):
    for j in range(max(0,y),min(H,y+h)):
        for i in range(max(0,x),min(W,x+w)): a[j][i]=c

def ellipse(a,cx,cy,rx,ry,c):
    for y in range(H):
        for x in range(W):
            if ((x-cx)/rx)**2+((y-cy)/ry)**2<=1:a[y][x]=c

def poly(a,points,c):
    for y in range(H):
        for x in range(W):
            inside=False
            for p,q in zip(points,points[1:]+points[:1]):
                if (p[1]>y)!=(q[1]>y) and x<(q[0]-p[0])*(y-p[1])/(q[1]-p[1])+p[0]:inside=not inside
            if inside:a[y][x]=c

def line(a,x0,y0,x1,y1,c,width=1):
    n=max(abs(x1-x0),abs(y1-y0),1)
    for i in range(n+1):rect(a,round(x0+(x1-x0)*i/n),round(y0+(y1-y0)*i/n),width,width,c)

a=art('桃心礼物')
for y in range(23):
    for x in range(32):
        u=(x-15.5)/12;v=(13-y)/10
        if (u*u+v*v-1)**3-u*u*v**3<=0:a[y][x]='r' if x+y>20 else 'p'
rect(a,8,6,3,2,'w');rect(a,10,5,3,1,'w');rect(a,23,8,2,3,'o')
a=art('金色星愿');pts=[]
for i in range(10):
    ang=-math.pi/2+i*math.pi/5;r=12 if i%2==0 else 5.6
    pts.append((15.5+math.cos(ang)*r,13+math.sin(ang)*r))
poly(a,pts,'y');poly(a,[(16,2),(16,15),(26,21),(20,14)],'o');rect(a,12,12,2,2,'k');rect(a,19,12,2,2,'k');rect(a,15,17,3,1,'r')
a=art('蝶舞花间')
for cx in (8,23):
    ellipse(a,cx,8,7,7,'v');ellipse(a,cx,8,4,4,'p');ellipse(a,cx,19,6,5,'b');ellipse(a,cx,19,3,3,'t')
rect(a,15,7,2,16,'y');line(a,15,6,11,2,'w');line(a,16,6,20,2,'w')
a=art('向阳花');rect(a,15,12,2,13,'g');ellipse(a,11,20,5,2,'g');ellipse(a,21,23,4,2,'t')
for i in range(8):
    t=i*math.pi/4;ellipse(a,16+7*math.cos(t),10+7*math.sin(t),4,4,'o' if i%2 else 'y')
ellipse(a,16,10,5,5,'y');rect(a,13,9,2,2,'k');rect(a,18,9,2,2,'k');rect(a,15,13,3,1,'r')
a=art('星夜小猫');poly(a,[(5,12),(4,2),(13,7),(20,7),(27,2),(27,18),(23,24),(10,24),(5,19)],'o');poly(a,[(6,4),(7,11),(11,8)],'p');poly(a,[(25,4),(21,8),(25,11)],'p');ellipse(a,16,17,9,6,'y');rect(a,9,12,3,3,'k');rect(a,21,12,3,3,'k');rect(a,15,17,3,2,'r');line(a,2,17,9,18,'w');line(a,23,18,30,17,'w')
a=art('奶油小狗');ellipse(a,16,14,10,10,'y');ellipse(a,6,11,4,8,'o');ellipse(a,26,11,4,8,'o');ellipse(a,21,11,4,5,'p');rect(a,10,10,2,3,'k');rect(a,21,10,2,3,'k');ellipse(a,16,17,4,3,'w');rect(a,15,15,3,2,'k');rect(a,15,20,3,3,'r')
a=art('晴天笑脸');ellipse(a,16,13,12,12,'o');ellipse(a,16,12,11,11,'y');rect(a,10,8,3,4,'k');rect(a,20,8,3,4,'k');line(a,9,16,12,20,'r',2);line(a,12,20,20,20,'r',2);line(a,20,20,24,16,'r',2);rect(a,6,14,3,2,'p');rect(a,25,14,3,2,'p')
a=art('云上彩虹')
for y in range(22):
    for x in range(32):
        r=math.hypot(x-15.5,y-21)
        if 7<=r<=15 and y<=21:a[y][x]=['v','b','t','g','y','o','r','p'][min(7,int(r-7))]
for cx in (4,8,23,27):ellipse(a,cx,22,4,3,'w')
a=art('棱镜宝石');poly(a,[(9,3),(23,3),(30,11),(16,25),(2,11)],'b');poly(a,[(9,3),(16,3),(12,11),(2,11)],'t');poly(a,[(16,3),(23,3),(30,11),(20,11)],'v');poly(a,[(12,11),(20,11),(16,25)],'p');line(a,2,11,29,11,'w');line(a,12,11,16,4,'w')
a=art('糖果城堡');rect(a,5,10,22,14,'v');rect(a,3,7,6,17,'b');rect(a,23,7,6,17,'b');rect(a,12,6,8,18,'p')
for x in (3,7,12,16,23,27):rect(a,x,4,2,4,'y')
rect(a,14,17,4,7,'k')
for x in (5,15,25):rect(a,x,10,2,4,'w')
line(a,16,0,16,5,'w');poly(a,[(17,0),(23,2),(17,3)],'r')
a=art('冬日圣诞树');rect(a,14,21,4,5,'o')
for y in range(3,23):
    half=2+(y//6)*3+(y%6)//2
    rect(a,16-half,y,half*2+1,1,'g' if y%6<3 else 't')
for x,y in [(13,8),(20,12),(9,18),(18,19),(15,15),(22,21)]:rect(a,x,y,2,2,'r')
poly(a,[(16,0),(18,3),(21,3),(18,5),(18,7),(16,5),(13,7),(14,4),(12,3),(15,3)],'y')
a=art('启航火箭');poly(a,[(10,10),(10,20),(3,24),(5,16)],'r');poly(a,[(22,10),(22,20),(29,24),(27,16)],'r');ellipse(a,16,12,6,11,'w');poly(a,[(10,6),(16,0),(22,6)],'r');ellipse(a,16,11,4,4,'b');ellipse(a,16,10,2,2,'t');poly(a,[(12,21),(16,26),(20,21)],'o');rect(a,15,21,2,4,'y')
a=art('土星来信');ellipse(a,16,13,10,10,'v');ellipse(a,14,11,8,8,'p')
for y in range(H):
    for x in range(W):
        t=((x-16)/16)**2+((y-13+.35*(x-16))/3)**2
        if .55<t<1.2:a[y][x]='y' if y<14 else 'o'
rect(a,11,7,3,2,'w');rect(a,19,18,3,2,'b')
a=art('珊瑚小鱼');poly(a,[(22,12),(31,5),(29,20)],'o');ellipse(a,13,13,11,8,'t');ellipse(a,9,12,6,6,'b');poly(a,[(10,7),(15,1),(19,7)],'v');poly(a,[(10,19),(18,24),(20,18)],'p');rect(a,6,10,3,3,'w');rect(a,6,11,2,2,'k');line(a,16,8,18,18,'y',2)
a=art('月光猫头鹰');ellipse(a,16,15,10,10,'v');poly(a,[(7,12),(5,2),(14,7),(21,7),(27,2),(25,13)],'v')
for x in (11,21):ellipse(a,x,12,6,6,'w');ellipse(a,x,12,3,3,'b');ellipse(a,x,12,1,2,'k')
poly(a,[(14,16),(18,16),(16,20)],'y');line(a,10,23,12,25,'o',2);line(a,21,23,23,25,'o',2)
a=art('蘑菇森林');rect(a,11,12,11,13,'y');ellipse(a,16,11,14,10,'r');rect(a,2,12,29,3,'p');rect(a,13,17,2,3,'k');rect(a,20,17,2,3,'k');rect(a,7,7,4,3,'w');rect(a,18,3,4,3,'w');rect(a,23,8,4,3,'w')
a=art('森林小狐');poly(a,[(4,1),(13,7),(20,7),(28,1),(26,17),(16,25),(6,17)],'o');poly(a,[(5,3),(8,12),(12,8)],'r');poly(a,[(26,3),(21,8),(24,12)],'r');poly(a,[(7,13),(16,19),(25,13),(23,20),(16,25),(9,20)],'w');rect(a,9,12,3,2,'k');rect(a,21,12,3,2,'k');rect(a,15,20,3,2,'k')
a=art('海龟漫游');ellipse(a,16,13,10,9,'t');ellipse(a,16,13,8,7,'g');ellipse(a,16,13,4,4,'y')
for x,y in [(5,6),(26,6),(5,21),(26,21)]:ellipse(a,x,y,4,3,'g')
ellipse(a,16,2,4,3,'g');rect(a,14,1,1,1,'k');rect(a,18,1,1,1,'k');line(a,9,13,23,13,'t');line(a,16,6,16,20,'t')
a=art('星冠奖杯');rect(a,8,3,17,3,'y');poly(a,[(9,5),(24,5),(22,16),(17,20),(11,16)],'o');rect(a,14,18,5,5,'y');rect(a,9,23,15,2,'y');line(a,7,5,2,5,'y',2);line(a,2,5,4,13,'y',2);line(a,4,13,11,16,'y',2);line(a,25,5,29,5,'y',2);line(a,29,5,27,13,'y',2);line(a,22,16,27,13,'y',2);rect(a,13,7,2,7,'w')
a=art('冰晶雪花')
for i in range(6):
    angle=i*math.pi/3;dx,dy=math.cos(angle),math.sin(angle)
    line(a,16,13,round(16+12*dx),round(13+12*dy),'t',2)
    for r in (6,9):
        for side in (-1,1):
            t=angle+side*math.pi/3
            line(a,round(16+r*dx),round(13+r*dy),round(16+r*dx-4*math.cos(t)),round(13+r*dy-4*math.sin(t)),'w')
ellipse(a,16,13,3,3,'b')
a=art('蜜橘时光');ellipse(a,16,15,11,10,'o');ellipse(a,13,13,8,8,'y');rect(a,15,2,2,5,'g');ellipse(a,22,3,6,2,'g');rect(a,9,10,2,4,'w');rect(a,13,8,2,2,'w');rect(a,17,19,3,1,'r')
a=art('双子樱桃');line(a,10,13,17,2,'g',2);line(a,23,14,17,2,'g',2);ellipse(a,23,3,6,2,'g');ellipse(a,9,18,7,7,'r');ellipse(a,24,18,7,7,'p');rect(a,5,14,3,2,'w');rect(a,20,14,3,2,'w');rect(a,10,22,3,2,'o')
a=art('六角光谱')
for y in range(H):
    for x in range(W):
        dx,dy=abs(x-16),abs(y-13)
        if dx<13 and dy<12 and dx+dy*.55<17:a[y][x]='vbtgyor'[(int(math.hypot(dx,dy))//2)%7]
rect(a,15,11,3,4,'w')
a=art('太极流光');ellipse(a,16,13,12,12,'w')
for y in range(H):
    for x in range(W):
        if a[y][x]!='.' and x>=16:a[y][x]='b'
ellipse(a,16,7,6,6,'w');ellipse(a,16,19,6,6,'b');ellipse(a,16,7,2,2,'p');ellipse(a,16,19,2,2,'y')
a=art('跳动音符');rect(a,9,5,3,15,'v');rect(a,23,2,3,16,'v');poly(a,[(9,5),(26,1),(26,6),(9,10)],'p');ellipse(a,7,21,5,4,'b');ellipse(a,21,19,5,4,'t');rect(a,11,6,2,2,'w');rect(a,24,3,1,2,'w')
a=art('彩色小屋');rect(a,6,12,21,13,'y');poly(a,[(2,13),(16,0),(31,13)],'r');poly(a,[(7,10),(16,2),(25,10)],'p');rect(a,9,15,5,5,'b');rect(a,21,15,4,5,'t');rect(a,16,18,4,7,'o');line(a,11,15,11,19,'w');rect(a,23,2,3,7,'v')
a=art('霓虹数字');digits=['111','101','101','101','111'];five=['111','100','111','001','111']
for bitmap,x,col in [(digits,5,'t'),(five,18,'p')]:
    for y,row in enumerate(bitmap):
        for j,v in enumerate(row):
            if v=='1':rect(a,x+j*3,4+y*4,3,3,col)
rect(a,10,24,12,1,'y')
a=art('对称迷宫')
for k,col in [(0,'t'),(1,'b'),(2,'v'),(3,'p')]:
    x=2+k*4;y=1+k*3;w=28-k*8;h=24-k*6
    line(a,x,y,x+w-1,y,col,2);line(a,x,y,x,y+h-1,col,2);line(a,x+w-2,y,x+w-2,y+h-1,col,2)
    line(a,x,y+h-2,12,y+h-2,col,2);line(a,20,y+h-2,x+w-1,y+h-2,col,2)
rect(a,14,11,4,4,'y')
a=art('环形星门')
for y in range(H):
    for x in range(W):
        d=math.hypot((x-16)*.85,y-13)
        if 8<d<12:a[y][x]='bvtp'[(x//5+y//4)%4]
poly(a,[(16,7),(18,11),(23,13),(18,15),(16,19),(14,15),(9,13),(14,11)],'y')
rect(a,0,12,6,3,'.');rect(a,27,12,5,3,'.')
a=art('千层万花筒')
for y in range(H):
    for x in range(W):
        d=abs(x-16)+abs(y-13)
        if d<=18 and (d//3)%2==0:a[y][x]='rpygtbv'[(d//3+(x+y)%2)%7]
rect(a,14,11,5,5,'w');rect(a,15,12,3,3,'p')
assert len(patterns)==30
out=['package com.kimizha.minigame.core','', '// Generated by tools/generate_levels.py; each character is one colored pixel.', 'data class PixelPattern(val name: String, val rows: List<String>)','object LevelPatterns {','    val all = listOf(']
for index,(name,a) in enumerate(patterns):
    out.append('        PixelPattern("'+name+'", listOf(')
    out.extend('            "'+''.join(row)+'"'+(',' if y<H-1 else '') for y,row in enumerate(a))
    out.append('        ))'+(',' if index<29 else ''))
out+=['    )','}']
Path('app/src/main/java/com/kimizha/minigame/core/LevelPatterns.kt').write_text('\n'.join(out)+'\n')
# Reviewable, original level atlas; generated from the same data as the Android game.
svg=['<svg xmlns="http://www.w3.org/2000/svg" width="1500" height="1800" viewBox="0 0 1500 1800"><rect width="1500" height="1800" fill="#09162D"/>']
for i,(name,a) in enumerate(patterns):
    bx=(i%5)*300;by=(i//5)*300
    svg.append(f'<text x="{bx+15}" y="{by+24}" fill="white" font-size="15">{i+1:02} {name}</text>')
    for color, value in PALETTE.items():
        if color=='.':continue
        parts=[]
        for y,row in enumerate(a):
            for x,c in enumerate(row):
                if c==color:parts.append(f'M{bx+21+x*8},{by+43+y*8}h7v7h-7z')
        if parts:svg.append(f'<path fill="{value}" d="{"".join(parts)}"/>')
svg.append('</svg>');Path('docs/level-atlas.svg').write_text('\n'.join(svg))
print('Generated',len(patterns),'patterns;',sum(sum(c!='.' for row in a for c in row) for _,a in patterns),'pixels')
