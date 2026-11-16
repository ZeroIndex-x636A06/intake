#!/usr/bin/env python3
"""Tiny driver: ui.py dump | tap "text" | tapxy x y | shot name | text "str" | back | swipe up/down | wait"""
import os, subprocess, sys, re, time, xml.etree.ElementTree as ET
A=os.environ.get("ADB", "/root/android-sdk/platform-tools/adb")
S=os.environ.get("EMU_SHOTS", "/tmp")
def sh(*a, t=120): return subprocess.run([A,*a],capture_output=True,text=True,timeout=t).stdout
def nodes():
    for _ in range(4):
        sh("shell","uiautomator","dump","/sdcard/u.xml",t=180)
        x=sh("exec-out","cat","/sdcard/u.xml")
        if x.strip().startswith("<?xml"): break
        time.sleep(3)
    out=[]
    for n in ET.fromstring(x).iter("node"):
        t=n.get("text") or n.get("content-desc") or ""
        m=re.findall(r"\d+",n.get("bounds"))
        if t and m:
            x1,y1,x2,y2=map(int,m); out.append((t,(x1+x2)//2,(y1+y2)//2,n.get("clickable")))
    return out
c=sys.argv[1]
if c=="dump":
    for t,x,y,k in nodes(): print(f"{x:5d} {y:5d} {t[:90]!r}")
elif c=="tap":
    want=sys.argv[2]; idx=int(sys.argv[3]) if len(sys.argv)>3 else 0
    ns=[n for n in nodes() if n[0]==want] or [n for n in nodes() if want.lower() in n[0].lower()]
    if not ns: print("NOT FOUND",want); sys.exit(1)
    t,x,y,_=ns[idx]; sh("shell","input","tap",str(x),str(y)); print("tapped",t,x,y)
elif c=="tapxy": sh("shell","input","tap",sys.argv[2],sys.argv[3])
elif c=="text": sh("shell","input","text",sys.argv[2].replace(" ","%s"))
elif c=="back": sh("shell","input","keyevent","4")
elif c=="swipe":
    y1,y2=(1800,600) if sys.argv[2]=="up" else (600,1800)
    sh("shell","input","swipe","540",str(y1),"540",str(y2),"600")
elif c=="shot":
    p=f"{S}/{sys.argv[2]}.png"
    open(p,"wb").write(subprocess.run([A,"exec-out","screencap","-p"],capture_output=True,timeout=120).stdout)
    from PIL import Image
    im=Image.open(p); im.resize((540,1200)).save(p); print(p)
elif c=="crash":
    print(sh("logcat","-d","-s","AndroidRuntime:E")[-3000:])
