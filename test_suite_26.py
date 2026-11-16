"""Test suite for OpticumeClient on Minecraft 26.3 (src_26, src, build26)."""
import glob
import json
import os
import re
import subprocess
import sys
import time
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
os.chdir(ROOT)
SRC = "src_26/main/java"
SRC_MIRROR = "src/main/java"
RES = "src_26/main/resources"
RES_MIRROR = "src/main/resources"
APPDATA = os.environ.get("APPDATA") or os.path.join(os.path.expanduser("~"), "AppData", "Roaming")
LAUNCHER = os.path.join(APPDATA, "ElyPrismLauncher")
MODS_DIR = os.path.join(LAUNCHER, "instances", "OPTIMUSPRIME", "minecraft", "mods")
JAR = os.path.join("build26", "libs", "opticumeclient-26.3.jar")
DEPLOYED = os.path.join(MODS_DIR, "opticumeclient-26.3.jar")


def _load_packaged(name):
    with zipfile.ZipFile(JAR) as z:
        return z.read(name).decode("utf-8")


def test_fabric_mod_json():
    print("[1/8] fabric.mod.json (source + packaged + icon)...")
    with open(f"{RES}/fabric.mod.json", encoding="utf-8") as f:
        raw = f.read()
        data = json.loads(raw.replace("${version}", "1.0.0"))
    assert data.get("schemaVersion") == 1
    assert data.get("id") == "opticumeclient", f"unexpected mod id: {data.get('id')}"
    assert data.get("name") == "OpticumeClient"
    assert "OpticumeClient Team" in data.get("authors", [])
    assert "dev.opticumeclient.OpticumeClient" in data["entrypoints"]["client"]
    assert "opticumeclient.mixins.json" in data["mixins"]
    icon_rel = data.get("icon")
    assert icon_rel == "assets/opticumeclient/icon.png", f"unexpected icon: {icon_rel}"
    assert os.path.exists(os.path.join(RES, icon_rel)), f"icon missing: {icon_rel}"
    assert "optimus" not in raw.lower(), "legacy 'optimus' string found in fabric.mod.json"
    if os.path.exists(JAR):
        d = json.loads(_load_packaged("fabric.mod.json"))["depends"]
        assert d.get("minecraft") == "~26.3", f"minecraft dep: {d.get('minecraft')}"
        assert "25" in d.get("java", ""), f"java dep: {d.get('java')}"
        assert "fabric-api" in d
    print("  PASSED")
    return True


def test_mixins_configuration():
    print("[2/8] Mixin config (packaged: JAVA_25, no refmap)...")
    with open(f"{RES}/opticumeclient.mixins.json", encoding="utf-8") as f:
        src = json.load(f)
    pkg = src.get("package", "dev.opticumeclient.mixin").replace(".", "/")
    assert pkg == "dev/opticumeclient/mixin", f"unexpected mixin package: {pkg}"
    mixins = src.get("client", []) + src.get("mixins", []) + src.get("server", [])
    assert len(mixins) == 26, f"expected 26 mixins, got {len(mixins)}"
    for req_mixin in ("GuiMixin", "AbstractButtonMixin", "AbstractSliderButtonMixin", "MinecraftAccessor"):
        assert req_mixin in mixins, f"missing required menu/button mixin: {req_mixin}"
    missing = [m for m in mixins if not os.path.exists(f"{SRC}/{pkg}/{m}.java")]
    assert not missing, f"declared mixin classes missing: {missing}"
    if os.path.exists(JAR):
        cfg = json.loads(_load_packaged("opticumeclient.mixins.json"))
        assert cfg.get("compatibilityLevel") == "JAVA_25"
        assert "refmap" not in cfg, "refmap must be removed"
        with zipfile.ZipFile(JAR) as z:
            assert not [n for n in z.namelist() if n.endswith(".refmap.json")], "refmap file in jar"
    print(f"  PASSED ({len(mixins)} mixins)")
    return True


def test_modules_registered():
    print("[3/8] Modules registered in ModuleManager...")
    path = f"{SRC}/dev/opticumeclient/client/modules/ModuleManager.java"
    assert os.path.exists(path), path
    content = open(path, encoding="utf-8").read()
    classes = set(re.findall(r"new\s+([A-Z][A-Za-z0-9_]*Module|[A-Z][A-Za-z0-9_]*Hud[A-Za-z0-9_]*)\s*\(", content))
    names = {os.path.basename(p)[:-5] for p in glob.glob(f"{SRC}/**/*.java", recursive=True)}
    missing = [c for c in classes if c not in names]
    assert not missing, f"registered but missing on disk: {missing}"
    assert len(classes) == 49, f"expected 49 modules, got {len(classes)}"
    required = (
        "OpticumeAuraModule", "TargetHudModule", "SprintModule", "NoSlowModule",
        "CrosshairModule", "CustomFontModule", "NoRenderModule", "FrameSyncModule",
        "VelocityModule", "NoPushModule", "NoJumpDelayModule", "MiddleClickPearlModule", "ZoomModule",
    )
    for req in required:
        assert req in classes, f"{req} not registered"
    print(f"  PASSED ({len(classes)} classes)")
    return True


def test_rebrand_and_no_legacy():
    print("[4/8] OpticumeClient rebrand & no legacy leftovers...")
    for s in ("compile_check_26.py", "deploy_26.py"):
        t = open(s, encoding="utf-8").read()
        assert "tinyremapper" not in t.lower().replace("no tinyremapper", ""), f"{s} references tinyremapper"
        assert "mappings-1.21" not in t
    assert "Fabric-Mapping-Namespace: official" in open("deploy_26.py", encoding="utf-8").read()
    assert not os.path.exists("src_26/main/java/dev/optimusprime"), "legacy dev/optimusprime package still exists"
    assert not os.path.exists("src_26/main/resources/assets/optimusprime"), "legacy assets/optimusprime still exists"
    assert not os.path.exists("src_26/main/resources/optimusprime.mixins.json"), "legacy optimusprime.mixins.json exists"

    for lang_file in ("en_us.json", "ru_ru.json"):
        lang_path = f"{RES}/assets/opticumeclient/lang/{lang_file}"
        assert os.path.exists(lang_path), f"missing lang file: {lang_path}"
        lang_data = json.load(open(lang_path, encoding="utf-8"))
        assert "key.opticumeclient.clickgui" in lang_data
        assert "OpticumeClient" in lang_data["key.opticumeclient.clickgui"]
        assert lang_data.get("category.opticumeclient") == "OpticumeClient"
        assert lang_data.get("opticumeclient.title") == "OpticumeClient"
    print("  PASSED")
    return True


def test_source_parity():
    print("[5/8] Source & resource parity between src/ and src_26/...")
    files_26 = {os.path.relpath(p, "src_26") for p in glob.glob("src_26/**/*", recursive=True) if os.path.isfile(p)}
    files_src = {os.path.relpath(p, "src") for p in glob.glob("src/**/*", recursive=True) if os.path.isfile(p)}
    assert files_26 == files_src, f"parity mismatch: 26-only={files_26 - files_src}, src-only={files_src - files_26}"
    java_26 = [p for p in files_26 if p.endswith(".java")]
    assert len(java_26) == 115, f"expected 115 java files, got {len(java_26)}"
    print(f"  PASSED ({len(files_26)} files, {len(java_26)} Java sources)")
    return True


def test_compilation():
    print("[6/8] Compiling src_26 against Minecraft 26.3 (javac --release 25)...")
    r = subprocess.run([sys.executable, "compile_check_26.py"], capture_output=True, text=True)
    if r.returncode != 0:
        print((r.stdout or r.stderr)[-1500:])
        return False
    print("  PASSED")
    return True


def test_jar_packaging():
    print("[7/8] Packaged jar manifest/classes/icon...")
    assert os.path.exists(JAR), f"{JAR} missing (run deploy_26.py)"
    with zipfile.ZipFile(JAR) as z:
        names = z.namelist()
        mf = z.read("META-INF/MANIFEST.MF").decode()
        assert "Implementation-Title: OpticumeClient" in mf
        assert "Fabric-Mapping-Namespace: official" in mf
        assert "Fabric-Minecraft-Version: 26.3" in mf
        assert "dev/opticumeclient/OpticumeClient.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeAltScreen.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeButtonStyle.class" in names
        assert "dev/opticumeclient/client/gui/SystemTheme.class" in names
        assert "dev/opticumeclient/client/gui/SystemModuleCard.class" in names
        assert "dev/opticumeclient/client/gui/SystemSearchModal.class" in names
        assert "assets/opticumeclient/icon.png" in names
        assert "assets/opticumeclient/textures/menu/icons/singleplayer.png" in names
        assert "assets/opticumeclient/textures/menu/wallpapers/wallpaper_1.png" in names
        assert "assets/opticumeclient/lang/en_us.json" in names
        assert "assets/opticumeclient/lang/ru_ru.json" in names
        # class file major version 69 = Java 25
        with z.open("dev/opticumeclient/OpticumeClient.class") as c:
            hdr = c.read(8)
        assert hdr[7] == 69, f"class major version {hdr[7]} != 69 (Java 25)"
    print("  PASSED")
    return True


def test_launcher_deployment():
    print("[8/8] Deployment to instance OPTIMUSPRIME...")
    if "--no-deploy" in sys.argv or (sys.platform != "win32" and not os.path.isdir(MODS_DIR)):
        print("  SKIPPED (headless / non-Windows environment; run deploy_26.py on Windows for OPTIMUSPRIME)")
        return True
    assert os.path.exists(DEPLOYED), f"{DEPLOYED} missing"
    assert os.path.getsize(DEPLOYED) == os.path.getsize(JAR), "deployed size != built size"
    stale = [f for f in os.listdir(MODS_DIR) if f.lower().startswith("optimusprime") and f.lower().endswith(".jar")]
    assert not stale, f"stale optimusprime jar still present in mods dir: {stale}"
    age = time.time() - os.path.getmtime(DEPLOYED)
    print(f"  PASSED (updated {age:.0f}s ago)")
    return True


def run_all():
    tests = [
        test_fabric_mod_json,
        test_mixins_configuration,
        test_modules_registered,
        test_rebrand_and_no_legacy,
        test_source_parity,
        test_compilation,
        test_jar_packaging,
        test_launcher_deployment,
    ]
    failed = 0
    for t in tests:
        try:
            if not t():
                failed += 1
                print(f"FAILED: {t.__name__}")
        except Exception as e:
            failed += 1
            print(f"FAILED: {t.__name__}: {e}")
    print(f"\n{len(tests) - failed}/{len(tests)} passed")
    return failed == 0


if __name__ == "__main__":
    sys.exit(0 if run_all() else 1)
