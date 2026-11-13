"""Test suite for the Minecraft 26.3 pipeline (src_26, build26). Old test_suite.py is untouched."""
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
RES = "src_26/main/resources"
JAR = r"build26\libs\optimusprime-26.3.jar"
DEPLOYED = r"C:\Users\User\AppData\Roaming\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods\optimusprime-26.3.jar"


def _load_packaged(name):
    with zipfile.ZipFile(JAR) as z:
        return z.read(name).decode("utf-8")


def test_fabric_mod_json():
    print("[1/7] fabric.mod.json (source + packaged)...")
    with open(f"{RES}/fabric.mod.json", encoding="utf-8") as f:
        data = json.loads(f.read().replace("${version}", "1.0.0"))
    assert data.get("schemaVersion") == 1
    assert data.get("id") == "optimusprime"
    assert "dev.optimusprime.OptimusPrimeClient" in data["entrypoints"]["client"]
    assert "optimusprime.mixins.json" in data["mixins"]
    if os.path.exists(JAR):
        d = json.loads(_load_packaged("fabric.mod.json"))["depends"]
        assert d.get("minecraft") == "~26.3", f"minecraft dep: {d.get('minecraft')}"
        assert "25" in d.get("java", ""), f"java dep: {d.get('java')}"
        assert "fabric-api" in d
    print("  PASSED")
    return True


def test_mixins_configuration():
    print("[2/7] Mixin config (packaged: JAVA_25, no refmap)...")
    with open(f"{RES}/optimusprime.mixins.json", encoding="utf-8") as f:
        src = json.load(f)
    pkg = src.get("package", "dev.optimusprime.mixin").replace(".", "/")
    mixins = src.get("client", []) + src.get("mixins", []) + src.get("server", [])
    assert mixins, "mixin list empty"
    missing = [m for m in mixins if not os.path.exists(f"{SRC}/{pkg}/{m}.java")]
    assert not missing, f"declared mixin classes missing: {missing}"
    if os.path.exists(JAR):
        cfg = json.loads(_load_packaged("optimusprime.mixins.json"))
        assert cfg.get("compatibilityLevel") == "JAVA_25"
        assert "refmap" not in cfg, "refmap must be removed"
        with zipfile.ZipFile(JAR) as z:
            assert not [n for n in z.namelist() if n.endswith(".refmap.json")], "refmap file in jar"
    print(f"  PASSED ({len(mixins)} mixins)")
    return True


def test_modules_registered():
    print("[3/7] Modules registered in ModuleManager...")
    path = f"{SRC}/dev/optimusprime/client/modules/ModuleManager.java"
    assert os.path.exists(path), path
    content = open(path, encoding="utf-8").read()
    classes = set(re.findall(r"new\s+([A-Z][A-Za-z0-9_]*Module|[A-Z][A-Za-z0-9_]*Hud[A-Za-z0-9_]*)\s*\(", content))
    names = {os.path.basename(p)[:-5] for p in glob.glob(f"{SRC}/**/*.java", recursive=True)}
    missing = [c for c in classes if c not in names]
    assert not missing, f"registered but missing on disk: {missing}"
    for req in ("TargetHudModule", "SprintModule", "NoSlowModule", "CrosshairModule", "CustomFontModule"):
        assert req in classes, f"{req} not registered"
    print(f"  PASSED ({len(classes)} classes)")
    return True


def test_no_legacy_references():
    print("[4/7] No intermediary/TinyRemapper leftovers in 26 scripts...")
    for s in ("compile_check_26.py", "deploy_26.py"):
        t = open(s, encoding="utf-8").read()
        assert "tinyremapper" not in t.lower().replace("no tinyremapper", ""), f"{s} references tinyremapper"
        assert "mappings-1.21" not in t
    t = open("deploy_26.py", encoding="utf-8").read()
    assert "Fabric-Mapping-Namespace: official" in t
    print("  PASSED")
    return True


def test_compilation():
    print("[5/7] Compiling src_26 against Minecraft 26.3 (javac --release 25)...")
    r = subprocess.run([sys.executable, "compile_check_26.py"], capture_output=True, text=True)
    if r.returncode != 0:
        print(r.stdout[-1500:])
        return False
    print("  PASSED")
    return True


def test_jar_packaging():
    print("[6/7] Packaged jar manifest/classes...")
    assert os.path.exists(JAR), f"{JAR} missing (run deploy_26.py)"
    with zipfile.ZipFile(JAR) as z:
        names = z.namelist()
        mf = z.read("META-INF/MANIFEST.MF").decode()
        assert "Fabric-Mapping-Namespace: official" in mf
        assert "Fabric-Minecraft-Version: 26.3" in mf
        assert "dev/optimusprime/OptimusPrimeClient.class" in names
        # class file major version 69 = Java 25
        with z.open("dev/optimusprime/OptimusPrimeClient.class") as c:
            hdr = c.read(8)
        assert hdr[7] == 69, f"class major version {hdr[7]} != 69 (Java 25)"
    print("  PASSED")
    return True


def test_launcher_deployment():
    print("[7/7] Deployment to instance OPTIMUSPRIME...")
    assert os.path.exists(DEPLOYED), f"{DEPLOYED} missing"
    assert os.path.getsize(DEPLOYED) == os.path.getsize(JAR), "deployed size != built size"
    age = time.time() - os.path.getmtime(DEPLOYED)
    print(f"  PASSED (updated {age:.0f}s ago)")
    return True


def run_all():
    tests = [test_fabric_mod_json, test_mixins_configuration, test_modules_registered,
             test_no_legacy_references, test_compilation, test_jar_packaging, test_launcher_deployment]
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

