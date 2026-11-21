import os, shutil, subprocess, sys

APKTOOL   = "apktool.jar"
SIGNER    = "uber-apk-signer.jar"
GADGET    = "libfrida-gadget.so"
INPUT_APK = "ff.apk"
OUT_DIR   = "ff_unpacked"
OUT_APK   = "ff_patched.apk"

def run(cmd):
    print(f"[*] {cmd}")
    r = subprocess.run(cmd, shell=True)
    if r.returncode != 0:
        print(f"[-] Failed: {cmd}")
        sys.exit(1)

print("[*] Step 1 — Decompile APK")
run(f"java -jar {APKTOOL} d {INPUT_APK} -o {OUT_DIR} -f")

print("[*] Step 2 — Copy Frida gadget")
lib_dir = os.path.join(OUT_DIR, "lib", "arm64-v8a")
os.makedirs(lib_dir, exist_ok=True)
shutil.copy(GADGET, os.path.join(lib_dir, "libfrida-gadget.so"))

print("[*] Step 3 — Patch smali to load gadget")
load_code = (
    "\n    const-string v0, \"frida-gadget\"\n"
    "    invoke-static {v0}, Ljava/lang/System;->loadLibrary(Ljava/lang/String;)V\n"
)
target = None
for root, dirs, files in os.walk(os.path.join(OUT_DIR, "smali")):
    for f in files:
        if any(x in f for x in ["MainApp","Application","SplashActivity","MainActivit"]):
            target = os.path.join(root, f); break
    if target: break
if not target:
    for root, dirs, files in os.walk(os.path.join(OUT_DIR, "smali")):
        for f in files:
            if "Activity" in f:
                target = os.path.join(root, f); break
        if target: break

print(f"[+] Patching: {target}")
with open(target, "r") as f: content = f.read()
marker = ".method public constructor <init>()V"
idx = content.find(marker)
if idx != -1:
    ins = content.find("\n", idx) + 1
    content = content[:ins] + load_code + content[ins:]
    with open(target, "w") as f: f.write(content)

print("[*] Step 4 — Recompile")
run(f"java -jar {APKTOOL} b {OUT_DIR} -o {OUT_APK}")

print("[*] Step 5 — Sign")
run(f"java -jar {SIGNER} -a {OUT_APK} --allowResign --overwrite")

print(f"\n[+] Done! Install with:  adb install {OUT_APK}")
