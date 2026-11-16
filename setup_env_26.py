"""Bootstrap Java 25 + Minecraft 26.3 + Fabric build dependencies for headless / Linux cloud builds.

Idempotent pure-stdlib setup script:
- Installs Microsoft OpenJDK 25 (with Adoptium / Corretto fallback) into ~/.local/jdks/jdk-25 if javac 25 is missing.
- Downloads Mojang 26.3.json, minecraft-26.3-client.jar, non-native Mojang libraries,
  Fabric Loader 0.20.1 (+ ASM / Sponge Mixin), and Fabric API 0.165.2+26.3 into ~/.cache/opticumeclient-mc26.3.
- Optionally clones reference sources (kdkdamls4-boop/minecraft-client-sources) when --with-refs is passed.

Usage:
    python3 setup_env_26.py [--with-refs] [--cache-dir DIR]
"""
import concurrent.futures
import hashlib
import json
import os
import shutil
import subprocess
import sys
import tarfile
import urllib.request

ROOT = os.path.dirname(os.path.abspath(__file__))
DEFAULT_CACHE = os.environ.get("OPTICUME_MC_CACHE") or os.path.expanduser("~/.cache/opticumeclient-mc26.3")
JDK_DIR = os.path.expanduser("~/.local/jdks/jdk-25")

MC_JSON_URL = "https://piston-meta.mojang.com/v1/packages/9f67e10ed013deeaad20ac3c800652765d493408/26.3.json"
MC_JSON_SHA1 = "9f67e10ed013deeaad20ac3c800652765d493408"
FABRIC_LOADER_VER = "0.20.1"
FABRIC_API_VER = "0.165.2+26.3"
FABRIC_API_URL = (
    f"https://maven.fabricmc.net/net/fabricmc/fabric-api/fabric-api/{FABRIC_API_VER}/fabric-api-{FABRIC_API_VER}.jar"
)
JDK_URLS = [
    "https://aka.ms/download-jdk/microsoft-jdk-25-linux-x64.tar.gz",
    "https://api.adoptium.net/v3/binary/latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse",
    "https://corretto.aws/downloads/latest/amazon-corretto-25-x64-linux-jdk.tar.gz",
]
REFS_REPO = "https://github.com/kdkdamls4-boop/minecraft-client-sources.git"


def _fetch_bytes(url):
    req = urllib.request.Request(url, headers={"User-Agent": "OpticumeClient-Setup/1.0"})
    with urllib.request.urlopen(req, timeout=60) as resp:
        return resp.read()


def _sha1_file(path):
    h = hashlib.sha1()
    with open(path, "rb") as f:
        while True:
            chunk = f.read(65536)
            if not chunk:
                break
            h.update(chunk)
    return h.hexdigest()


def _download_file(url, dest, sha1=None):
    if os.path.exists(dest) and os.path.getsize(dest) > 0:
        if not sha1 or _sha1_file(dest) == sha1:
            return "cached"
    os.makedirs(os.path.dirname(dest), exist_ok=True)
    data = _fetch_bytes(url)
    if sha1:
        actual = hashlib.sha1(data).hexdigest()
        if actual != sha1:
            raise RuntimeError(f"SHA1 mismatch for {url}: got {actual}, expected {sha1}")
    tmp = dest + ".tmp"
    with open(tmp, "wb") as f:
        f.write(data)
    os.replace(tmp, dest)
    return "downloaded"


def _is_javac_25(javac_path):
    if not javac_path or not os.path.exists(javac_path):
        return False
    try:
        res = subprocess.run([javac_path, "-version"], capture_output=True, text=True, timeout=10)
        out = (res.stdout + res.stderr).strip()
        return "javac 25" in out
    except Exception:
        return False


def ensure_jdk_25():
    if sys.platform == "win32":
        print("[JDK 25] Windows detected; skipping Linux JDK installation.")
        return None
    for candidate in (
        os.environ.get("OPTICUME_JAVAC"),
        os.path.join(os.environ.get("JAVA_HOME", ""), "bin", "javac"),
        os.path.join(JDK_DIR, "bin", "javac"),
        shutil.which("javac"),
    ):
        if _is_javac_25(candidate):
            print(f"[JDK 25] Ready: {candidate}")
            return candidate

    os.makedirs(os.path.dirname(JDK_DIR), exist_ok=True)
    archive = os.path.join("/tmp", "opticume-jdk25.tar.gz")
    last_err = None
    for url in JDK_URLS:
        try:
            print(f"[JDK 25] Downloading from {url}...")
            req = urllib.request.Request(url, headers={"User-Agent": "OpticumeClient-Setup/1.0"})
            with urllib.request.urlopen(req, timeout=180) as resp, open(archive, "wb") as out:
                shutil.copyfileobj(resp, out)
            break
        except Exception as exc:
            last_err = exc
            if os.path.exists(archive):
                os.remove(archive)
    else:
        raise RuntimeError(f"Failed to download JDK 25 from all mirrors: {last_err}")

    if os.path.exists(JDK_DIR):
        shutil.rmtree(JDK_DIR)
    os.makedirs(JDK_DIR, exist_ok=True)
    with tarfile.open(archive, "r:gz") as tar:
        members = []
        for m in tar.getmembers():
            parts = m.name.split("/", 1)
            if len(parts) == 2 and parts[1]:
                m.name = parts[1]
                members.append(m)
        tar.extractall(JDK_DIR, members=members)
    os.remove(archive)

    javac_bin = os.path.join(JDK_DIR, "bin", "javac")
    if not _is_javac_25(javac_bin):
        raise RuntimeError(f"Installed JDK at {JDK_DIR} did not provide javac 25")
    print(f"[JDK 25] Installed: {javac_bin}")
    return javac_bin


def ensure_mc_deps(cache_dir):
    libs = os.path.join(cache_dir, "libraries")
    meta = os.path.join(cache_dir, "meta", "net.minecraft")
    mods = os.path.join(cache_dir, "mods")
    os.makedirs(libs, exist_ok=True)
    os.makedirs(meta, exist_ok=True)
    os.makedirs(mods, exist_ok=True)

    mc_json_path = os.path.join(meta, "26.3.json")
    _download_file(MC_JSON_URL, mc_json_path, MC_JSON_SHA1)
    with open(mc_json_path, encoding="utf-8") as f:
        mc = json.load(f)

    tasks = []
    client_dl = mc["downloads"]["client"]
    tasks.append((
        client_dl["url"],
        os.path.join(libs, "com", "mojang", "minecraft", "26.3", "minecraft-26.3-client.jar"),
        client_dl["sha1"],
    ))

    for lib in mc["libraries"]:
        if "rules" in lib or "natives" in lib:
            continue
        art = lib.get("downloads", {}).get("artifact")
        if art:
            tasks.append((art["url"], os.path.join(libs, art["path"]), art.get("sha1")))

    loader_url = f"https://meta.fabricmc.net/v2/versions/loader/26.3/{FABRIC_LOADER_VER}"
    loader_meta = json.loads(_fetch_bytes(loader_url).decode("utf-8"))
    lg, la, lv = loader_meta["loader"]["maven"].split(":")
    loader_rel = f"{lg.replace('.', '/')}/{la}/{lv}/{la}-{lv}.jar"
    tasks.append(("https://maven.fabricmc.net/" + loader_rel, os.path.join(libs, loader_rel), None))

    for lib in loader_meta["launcherMeta"]["libraries"]["common"]:
        g, a, v = lib["name"].split(":")
        rel = f"{g.replace('.', '/')}/{a}/{v}/{a}-{v}.jar"
        base = lib.get("url", "https://maven.fabricmc.net/")
        if not base.endswith("/"):
            base += "/"
        tasks.append((base + rel, os.path.join(libs, rel), lib.get("sha1")))

    tasks.append((FABRIC_API_URL, os.path.join(mods, f"fabric-api-{FABRIC_API_VER}.jar"), None))

    counts = {"cached": 0, "downloaded": 0}
    with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
        futs = [pool.submit(_download_file, u, d, s) for u, d, s in tasks]
        for fut in concurrent.futures.as_completed(futs):
            counts[fut.result()] += 1
    print(
        f"[MC 26.3 + Fabric] {len(tasks)} artifacts verified in {cache_dir} "
        f"(downloaded={counts['downloaded']}, cached={counts['cached']})"
    )


def ensure_reference_sources():
    target = os.path.join(os.path.dirname(ROOT), "minecraft-client-sources")
    if os.path.isdir(os.path.join(target, ".git")):
        print(f"[Refs] Already present: {target}")
        return
    print(f"[Refs] Cloning {REFS_REPO} -> {target}...")
    subprocess.run(["git", "clone", "--depth", "1", REFS_REPO, target], check=True)


def main():
    argv = sys.argv[1:]
    cache_dir = DEFAULT_CACHE
    if "--cache-dir" in argv:
        cache_dir = os.path.abspath(argv[argv.index("--cache-dir") + 1])

    ensure_jdk_25()
    ensure_mc_deps(cache_dir)
    if "--with-refs" in argv:
        ensure_reference_sources()
    print("Cloud build environment setup complete.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
