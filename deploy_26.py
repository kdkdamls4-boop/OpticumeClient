"""Package and deploy OPTIMUS PRIME for Minecraft 26.3 (Mojang/official names, Java 25).

No intermediary, no TinyRemapper: classes compiled against the official-named client jar
are packaged as-is. Old deploy_to_launcher.py is untouched.

Usage: python deploy_26.py [--no-deploy] [--skip-compile] [--classes DIR]
"""
import json
import os
import shutil
import subprocess
import sys
import zipfile

ROOT = os.path.dirname(os.path.abspath(__file__))
BUILD = os.path.join(ROOT, "build26")
CLASSES = os.path.join(BUILD, "classes")
RESOURCES = os.path.join(ROOT, "src_26", "main", "resources")
LIBS_OUT = os.path.join(BUILD, "libs")
JAR_NAME = "optimusprime-26.3.jar"
MODS_DIR = r"C:\Users\User\AppData\Roaming\ElyPrismLauncher\instances\OPTIMUSPRIME\minecraft\mods"
MOD_VERSION = "1.0.0+mc26.3"

MANIFEST = (
    "Manifest-Version: 1.0\r\n"
    "Implementation-Title: OpticumeClient\r\n"
    f"Implementation-Version: {MOD_VERSION}\r\n"
    "Fabric-Jar-Type: classes\r\n"
    "Fabric-Mapping-Namespace: official\r\n"
    "Fabric-Minecraft-Version: 26.3\r\n"
    "Fabric-Loader-Version: 0.19.5\r\n\r\n"
)


def transform_mod_json(text):
    data = json.loads(text.replace("${version}", MOD_VERSION))
    data["depends"] = {
        "fabricloader": ">=0.19.5",
        "minecraft": "~26.3",
        "java": ">=25",
        "fabric-api": "*",
    }
    return json.dumps(data, indent=2, ensure_ascii=False)


def transform_mixin_cfg(text):
    data = json.loads(text)
    data.pop("refmap", None)  # official names: mixin resolves targets directly
    data["compatibilityLevel"] = "JAVA_25"
    return json.dumps(data, indent=2, ensure_ascii=False)


def package(classes_dir, jar_path):
    os.makedirs(os.path.dirname(jar_path), exist_ok=True)
    if os.path.exists(jar_path):
        os.remove(jar_path)
    with zipfile.ZipFile(jar_path, "w", zipfile.ZIP_DEFLATED) as z:
        z.writestr("META-INF/MANIFEST.MF", MANIFEST)
        if os.path.isdir(classes_dir):
            for root, _, files in os.walk(classes_dir):
                for f in files:
                    p = os.path.join(root, f)
                    z.write(p, os.path.relpath(p, classes_dir).replace("\\", "/"))
        for root, _, files in os.walk(RESOURCES):
            for f in files:
                p = os.path.join(root, f)
                arc = os.path.relpath(p, RESOURCES).replace("\\", "/")
                if arc == "META-INF/MANIFEST.MF" or f.endswith(".refmap.json"):
                    continue
                if f == "fabric.mod.json":
                    with open(p, encoding="utf-8") as fh:
                        z.writestr(arc, transform_mod_json(fh.read()).encode("utf-8"))
                elif f.endswith(".mixins.json"):
                    with open(p, encoding="utf-8") as fh:
                        z.writestr(arc, transform_mixin_cfg(fh.read()).encode("utf-8"))
                else:
                    z.write(p, arc)
    return jar_path


def deploy():
    argv = sys.argv[1:]
    classes_dir = CLASSES
    if "--classes" in argv:
        classes_dir = os.path.abspath(argv[argv.index("--classes") + 1])
    if "--skip-compile" not in argv:
        print("Invoking compile_check_26.py...")
        res = subprocess.run([sys.executable, os.path.join(ROOT, "compile_check_26.py")], capture_output=True, text=True)
        if res.returncode != 0:
            print("Compilation check FAILED:")
            print(res.stdout or res.stderr)
            sys.exit(res.returncode)
        print("Compilation check PASSED (0 errors).")

    jar_path = package(classes_dir, os.path.join(LIBS_OUT, JAR_NAME))
    print(f"Built {jar_path} ({os.path.getsize(jar_path)} bytes)")

    if "--no-deploy" in argv:
        return
    if not os.path.isdir(MODS_DIR):
        print(f"Mods dir not found: {MODS_DIR}")
        sys.exit(1)
    # Warn about other jars with the same mod id (duplicates conflict in Fabric Loader)
    for f in os.listdir(MODS_DIR):
        if f.lower().startswith("optimusprime") and f.endswith(".jar") and f != JAR_NAME:
            print(f"WARNING: {f} also present in mods dir - duplicate mod id 'optimusprime' will conflict!")
    target = os.path.join(MODS_DIR, JAR_NAME)
    shutil.copy2(jar_path, target)
    print(f"DEPLOYED: {target}")


if __name__ == "__main__":
    deploy()

