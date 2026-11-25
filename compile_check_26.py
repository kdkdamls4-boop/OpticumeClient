"""Compile check for Minecraft 26.3 (Mojang names, Java 25, no intermediary/remap).

Automatically synchronizes src/ and src_26/ (newest file wins) before compiling into build26/classes.
Usage: python compile_check_26.py [--src DIR] [--log FILE]
"""
import glob
import json
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
APPDATA = os.environ.get("APPDATA") or os.path.join(os.path.expanduser("~"), "AppData", "Roaming")
CACHE_DEFAULT = os.path.expanduser("~/.cache/opticumeclient-mc26.3")


def _has_mc_26_3(base_dir):
    return bool(base_dir) and os.path.isfile(
        os.path.join(base_dir, "libraries", "com", "mojang", "minecraft", "26.3", "minecraft-26.3-client.jar")
    )


def _resolve_base_dir():
    env_cache = os.environ.get("OPTICUME_MC_CACHE")
    if _has_mc_26_3(env_cache):
        return env_cache
    if sys.platform == "win32":
        cand = os.path.join(APPDATA, "ElyPrismLauncher")
        if _has_mc_26_3(cand):
            return cand
    if _has_mc_26_3(CACHE_DEFAULT):
        return CACHE_DEFAULT
    return env_cache or CACHE_DEFAULT


def _first_existing(candidates):
    for p in candidates:
        if p and os.path.exists(p):
            return p
    return candidates[0] if candidates else ""


def _is_javac_25(path):
    if not path or not os.path.exists(path):
        return False
    try:
        r = subprocess.run([path, "-version"], capture_output=True, text=True, timeout=10)
        return "javac 25" in (r.stdout + r.stderr)
    except Exception:
        return False


def _resolve_javac(base_dir):
    exe = "javac.exe" if sys.platform == "win32" else "javac"
    candidates = [
        os.environ.get("OPTICUME_JAVAC", ""),
        os.path.join(base_dir, "java", "java-runtime-epsilon", "bin", "javac.exe"),
        os.path.join(APPDATA, "ElyPrismLauncher", "java", "java-runtime-epsilon", "bin", "javac.exe") if sys.platform == "win32" else "",
        os.path.join(os.environ.get("JAVA_HOME", ""), "bin", exe) if os.environ.get("JAVA_HOME") else "",
        os.path.expanduser("~/.local/jdks/jdk-25/bin/javac"),
        shutil.which("javac") or "",
    ]
    for c in candidates:
        if _is_javac_25(c):
            return c
    return _first_existing(candidates)


LAUNCHER = _resolve_base_dir()
LIBS = os.path.join(LAUNCHER, "libraries")
META = os.path.join(LAUNCHER, "meta")
JAVAC = _resolve_javac(LAUNCHER)
MC_JAR = os.path.join(LIBS, "com", "mojang", "minecraft", "26.3", "minecraft-26.3-client.jar")
FABRIC_API_JAR = _first_existing([
    os.path.join(LAUNCHER, "instances", "OPTIMUSPRIME", "minecraft", "mods", "fabric-api-0.165.2+26.3.jar"),
    os.path.join(LAUNCHER, "mods", "fabric-api-0.165.2+26.3.jar"),
    *sorted(glob.glob(os.path.join(LAUNCHER, "instances", "OPTIMUSPRIME", "minecraft", "mods", "fabric-api-*+26.3.jar"))),
    *sorted(glob.glob(os.path.join(LAUNCHER, "mods", "fabric-api-*+26.3.jar"))),
])
FABRIC_LOADER = _first_existing([
    os.path.join(LIBS, "net", "fabricmc", "fabric-loader", "0.20.1", "fabric-loader-0.20.1.jar"),
    os.path.join(LIBS, "net", "fabricmc", "fabric-loader", "0.19.5", "fabric-loader-0.19.5.jar"),
    *sorted(glob.glob(os.path.join(LIBS, "net", "fabricmc", "fabric-loader", "*", "*.jar")), reverse=True),
])
MIXIN = _first_existing([
    os.path.join(LIBS, "net", "fabricmc", "sponge-mixin", "0.17.5+mixin.0.8.7", "sponge-mixin-0.17.5+mixin.0.8.7.jar"),
    *sorted(glob.glob(os.path.join(LIBS, "net", "fabricmc", "sponge-mixin", "*", "*.jar")), reverse=True),
])
JOML = os.path.join(LIBS, "org", "joml", "joml", "1.10.9", "joml-1.10.9.jar")
LWJGL_VER = "3.4.3"

BUILD = os.path.join(ROOT, "build26")
DEPS = os.path.join(BUILD, "deps")
CLASSES = os.path.join(BUILD, "classes")
SRC_DEFAULT = os.path.join(ROOT, "src_26", "main", "java")


def sync_src_dirs():
    """Keep src/ and src_26/ two-way synchronized based on newest modification time."""
    src_a = os.path.join(ROOT, "src")
    src_b = os.path.join(ROOT, "src_26")
    if not os.path.isdir(src_a) or not os.path.isdir(src_b):
        return
    for base_from, base_to in ((src_a, src_b), (src_b, src_a)):
        for r, _, files in os.walk(base_from):
            for f in files:
                p_from = os.path.join(r, f)
                rel = os.path.relpath(p_from, base_from)
                p_to = os.path.join(base_to, rel)
                if not os.path.exists(p_to) or os.path.getmtime(p_from) > os.path.getmtime(p_to) + 0.05:
                    os.makedirs(os.path.dirname(p_to), exist_ok=True)
                    shutil.copy2(p_from, p_to)


def extract_fabric_api_modules():
    """Extract jar-in-jar modules (META-INF/jars/*.jar) from fabric-api into build26/deps."""
    os.makedirs(DEPS, exist_ok=True)
    out = []
    with zipfile.ZipFile(FABRIC_API_JAR) as z:
        for n in z.namelist():
            if n.startswith("META-INF/jars/") and n.endswith(".jar"):
                target = os.path.join(DEPS, os.path.basename(n))
                if not os.path.exists(target) or os.path.getsize(target) != z.getinfo(n).file_size:
                    with open(target, "wb") as f:
                        f.write(z.read(n))
                out.append(target)
    return out


def maven_path(coord):
    parts = coord.split(":")
    if len(parts) != 3:  # skip classifier (native) artifacts
        return None
    g, a, v = parts
    return os.path.join(LIBS, g.replace(".", os.sep), a, v, f"{a}-{v}.jar")


def meta_libs():
    """Libraries from meta/net.minecraft/26.3.json (no natives / non-windows classifiers)."""
    with open(os.path.join(META, "net.minecraft", "26.3.json"), encoding="utf-8") as f:
        data = json.load(f)
    res = []
    for lib in data.get("libraries", []):
        if "rules" in lib or "natives" in lib:
            continue
        p = maven_path(lib["name"])
        if p and os.path.exists(p):
            res.append(p)
        elif "authlib" in lib.get("name", ""):
            # Fallback for ElyPrismLauncher custom authlib (use 10.0.77)
            ely = os.path.join(LIBS, "by", "ely", "authlib", "10.0.77-ely.2", "authlib-10.0.77-ely.2.jar")
            if os.path.exists(ely):
                res.append(ely)
    return res


def lwjgl_libs():
    res = []
    for ver in (LWJGL_VER, "3.3.3"):
        for p in glob.glob(os.path.join(LIBS, "org", "lwjgl", "*", ver, "*.jar")):
            if "natives" not in os.path.basename(p):
                res.append(p)
    return list(dict.fromkeys(res))


def asm_libs():
    return glob.glob(os.path.join(LIBS, "org", "ow2", "asm", "*", "9.10.1", "*.jar"))


def build_classpath():
    cp = [MC_JAR, FABRIC_API_JAR]
    cp += extract_fabric_api_modules()
    cp += [FABRIC_LOADER, MIXIN, JOML]
    cp += lwjgl_libs() + asm_libs() + meta_libs()
    cp = [p for p in dict.fromkeys(cp) if os.path.exists(p)]
    return cp


def compile_sources(java_files, out_dir=CLASSES, log_path=None, extra_cp=None):
    os.makedirs(out_dir, exist_ok=True)
    cp = build_classpath() + (extra_cp or [])
    args_file = os.path.join(BUILD, "javac_args.txt")
    with open(args_file, "w", encoding="utf-8") as f:
        f.write("-encoding\nUTF-8\n--release\n25\n-proc:none\n-Xmaxerrs\n100000\n-Xmaxwarns\n0\n")
        f.write("-d\n" + out_dir.replace("\\", "/") + "\n")
        f.write("-cp\n" + os.pathsep.join(cp).replace("\\", "/") + "\n")
        for s in java_files:
            f.write(os.path.abspath(s).replace("\\", "/") + "\n")
    res = subprocess.run([JAVAC, "@" + args_file], capture_output=True, text=True, encoding="utf-8", errors="replace")
    log_path = log_path or os.path.join(BUILD, "compile_errors.log")
    with open(log_path, "w", encoding="utf-8") as lf:
        lf.write(res.stderr)
    return res, len(cp)


def main():
    sync_src_dirs()
    src = SRC_DEFAULT
    log = None
    argv = sys.argv[1:]
    if "--src" in argv:
        src = os.path.abspath(argv[argv.index("--src") + 1])
    if "--log" in argv:
        log = os.path.abspath(argv[argv.index("--log") + 1])
    if not JAVAC or not os.path.exists(JAVAC):
        print(f"JDK 25 javac not found: {JAVAC}. Run 'python3 setup_env_26.py' on Linux/cloud.")
        return 2
    if not os.path.exists(MC_JAR) or not os.path.exists(FABRIC_API_JAR):
        print(f"MC 26.3 / Fabric dependencies not found under {LAUNCHER}. Run 'python3 setup_env_26.py'.")
        return 2
    os.makedirs(BUILD, exist_ok=True)
    java_files = glob.glob(os.path.join(src, "**", "*.java"), recursive=True)
    if not java_files:
        print("No Java sources found.")
        return 0
    print(f"Running javac (release 25) on {len(java_files)} sources...")
    res, ncp = compile_sources(java_files, log_path=log)
    print(f"Classpath jars: {ncp}. Javac return code: {res.returncode}")
    if res.returncode != 0:
        lines = res.stderr.splitlines()
        for line in lines[:25]:
            print(line)
        print(f"... {len(lines)} lines total (see {log or os.path.join(BUILD, 'compile_errors.log')})")
        return res.returncode
    print("SUCCESS: 0 compilation errors!")
    return 0


if __name__ == "__main__":
    sys.exit(main())
