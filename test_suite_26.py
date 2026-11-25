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
    assert len(mixins) >= 25, f"expected at least 25 mixins, got {len(mixins)}"
    for req_mixin in (
        "GuiMixin",
        "AbstractButtonMixin",
        "AbstractSliderButtonMixin",
        "MinecraftAccessor",
        "GuiGraphicsExtractorMixin",
        "LightmapRenderStateExtractorMixin",
        "GameRendererMixin",
        "LiquidBlockMixin",
        "PlayerTabOverlayMixin",
    ):
        assert req_mixin in mixins, f"missing required mixin: {req_mixin}"
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
    print("[3/8] Modules registered in ModuleManager & M9 subsystems...")
    path = f"{SRC}/dev/opticumeclient/client/modules/ModuleManager.java"
    assert os.path.exists(path), path
    content = open(path, encoding="utf-8").read()
    classes = set(re.findall(r"new\s+([A-Z][A-Za-z0-9_]*Module|[A-Z][A-Za-z0-9_]*Hud[A-Za-z0-9_]*)\s*\(", content))
    names = {os.path.basename(p)[:-5] for p in glob.glob(f"{SRC}/**/*.java", recursive=True)}
    missing = [c for c in classes if c not in names]
    assert not missing, f"registered but missing on disk: {missing}"
    assert len(classes) >= 116, f"expected >= 116 modules, got {len(classes)}"
    required = (
        "OpticumeAuraModule", "TargetHudModule", "SprintModule", "NoSlowModule",
        "CrosshairModule", "CustomFontModule", "NoRenderModule", "FrameSyncModule",
        "VelocityModule", "NoPushModule", "NoJumpDelayModule", "MiddleClickPearlModule",
        "ZoomModule", "TargetEspModule", "PredictionsModule", "AspectRatioModule",
        "InterpolateF5Module", "GuiMoveModule",
        "AutoSwapModule", "AntiBotModule", "NoFriendDamageModule",
        "AutoToolModule", "ItemScrollerModule",
        "NoWebModule", "ElytraHelperModule", "BlockOverlayModule",
        "ParticlesModule", "SwingAnimationsModule", "DamageTintModule", "SeeInvisiblesModule",
        "GlowEspModule", "ChinaHatModule", "HandsModule", "AmbienceModule",
        "VideoOptimizerModule", "AssistModule", "AutoBuyModule", "BotsModule",
        "CooldownsHud", "BindsHud", "InventoryHud", "AutoBuyReportHud",
        # M12 Combat
        "AutoCrystalModule", "AutoAnchorModule", "MaceHelperModule", "BackTrackModule",
        "HitBoxesModule", "AutoPotionModule", "TargetPearlModule", "BowAimBotModule",
        # M13 Movement & QoL
        "JesusModule", "SpiderModule", "NoFallModule", "AirStuckModule",
        "SpeedModule", "SuperFireworkModule", "FreeCamModule", "FreeLookModule",
        "AutoAcceptModule", "LockSlotModule", "NoInteractModule",
        "ServerRPSpoofModule", "AuctionHelperModule",
        # M14 Privacy & Safety
        "NameProtectModule", "PanicModule", "CaptureBypassModule",
        # M15 World Visuals & Timers
        "ObjectInfoModule", "BlockESPModule", "TntTimerModule", "PotsTimerModule",
        "FireworkESPModule", "ShulkerPreviewModule", "ItemPhysicsModule",
        "MotionBlurModule", "BetterTabModule",
    )
    for req in required:
        assert req in classes, f"{req} not registered"
    for req_w3 in (
        "DiscordRpcModule",
        "ServerKeybindsHud",
        "SchedulesHud",
        "CustomHotbarHud",
        "MediaPlayerHud",
        "ProfilerHud",
    ):
        assert req_w3 in classes, f"Wave 3 module {req_w3} not registered"
    for removed_mod in (
        "ChunkAnimatorModule", "FastMathModule", "ChestStealerModule", "HitColorModule",
        "OldAnimationsModule", "WorldParticlesModule", "ChamsModule",
        "NoHurtCamModule", "KillAuraModule", "AtmosphereModule",
    ):
        assert removed_mod not in names, f"removed module {removed_mod} still exists on disk"

    # Verify M9 TargetESP subsystem (16 Java classes, 35 shaders, 12 PNGs)
    targetesp_java = glob.glob(f"{SRC}/dev/opticumeclient/client/modules/visuals/targetesp/*.java")
    targetesp_shaders = [
        p for p in glob.glob(f"{RES}/assets/opticumeclient/shaders/core/targetesp/**/*", recursive=True)
        if os.path.isfile(p)
    ]
    targetesp_pngs = [
        p for p in glob.glob(f"{RES}/assets/opticumeclient/targetesp/**/*.png", recursive=True)
        if os.path.isfile(p)
    ]
    assert len(targetesp_java) == 16, f"expected 16 targetesp Java classes, got {len(targetesp_java)}"
    assert len(targetesp_shaders) == 35, f"expected 35 targetesp shaders, got {len(targetesp_shaders)}"
    assert len(targetesp_pngs) == 12, f"expected 12 targetesp PNGs, got {len(targetesp_pngs)}"

    # Verify M12 & Rotation 2.0 Combat Engine
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/combat/RotationManager.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/combat/CombatRotationEngine.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/modules/combat/CrystalDamageUtil.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/modules/combat/AutoSwapWheelScreen.java")

    # Verify M14 & M15 Wave 2 subsystems
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/util/Win32WindowBridge.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/command/CommandDataStore.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/command/CommandFix.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/command/FakePlayerManager.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/command/ModuleProfiler.java")
    for cmd_file in (
        "PrefixCommand", "MacroCommand", "StaffCommand", "WayCommand",
        "VClipCommand", "HClipCommand", "FakePlayerCommand", "BlockEspCommand", "ProfilerCommand",
    ):
        assert os.path.exists(f"{SRC}/dev/opticumeclient/client/command/impl/{cmd_file}.java"), f"missing command {cmd_file}"
    atmo_renderer = open(f"{SRC}/dev/opticumeclient/client/modules/visuals/atmosphere/AtmosphereRenderer.java", encoding="utf-8").read()
    assert "UBO_SIZE = 368" in atmo_renderer, "AtmosphereRenderer must use 368-byte UBO"
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/modules/visuals/killeffect/DeathPiece.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/modules/visuals/killeffect/DeathPoseFactory.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/modules/visuals/ambience/MotionBlurRenderer.java")
    assert os.path.exists(f"{RES}/assets/opticumeclient/shaders/core/ambience/motion_blur.fsh")

    # Verify MSDF font renderer & shaders
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/render/font/OpticumeFonts.java")
    assert os.path.exists(f"{SRC}/dev/opticumeclient/client/render/font/MsdfFontFace.java")
    assert os.path.exists(f"{RES}/assets/opticumeclient/shaders/core/msdf_text.vsh")
    assert os.path.exists(f"{RES}/assets/opticumeclient/shaders/core/msdf_text.fsh")
    print(f"  PASSED ({len(classes)} modules, 16 TargetESP classes, 35 shaders, 12 PNGs, MSDF active)")
    return True


def test_rebrand_and_no_legacy():
    print("[4/8] OpticumeClient rebrand, zero legacy GL & no hands_fullscreen...")
    for s in ("compile_check_26.py", "deploy_26.py"):
        t = open(s, encoding="utf-8").read()
        assert "tinyremapper" not in t.lower().replace("no tinyremapper", ""), f"{s} references tinyremapper"
        assert "mappings-1.21" not in t
    assert "Fabric-Mapping-Namespace: official" in open("deploy_26.py", encoding="utf-8").read()
    assert not os.path.exists("src_26/main/java/dev/optimusprime"), "legacy dev/optimusprime package still exists"
    assert not os.path.exists("src_26/main/resources/assets/optimusprime"), "legacy assets/optimusprime still exists"
    assert not os.path.exists("src_26/main/resources/optimusprime.mixins.json"), "legacy optimusprime.mixins.json exists"

    for jf in glob.glob(f"{SRC}/**/*.java", recursive=True):
        txt = open(jf, encoding="utf-8").read()
        for legacy_gl in ("GL11", "glBegin", "glEnd", "GlStateManager"):
            assert legacy_gl not in txt, f"legacy OpenGL symbol {legacy_gl} found in {jf}"
        assert "hands_fullscreen" not in txt, f"hands_fullscreen found in {jf}"

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
    assert len(java_26) >= 332, f"expected >= 332 java files, got {len(java_26)}"
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
    print("[7/8] Packaged jar manifest/classes/icon/shaders...")
    assert os.path.exists(JAR), f"{JAR} missing (run deploy_26.py)"
    with zipfile.ZipFile(JAR) as z:
        names = z.namelist()
        mf = z.read("META-INF/MANIFEST.MF").decode()
        assert "Implementation-Title: OpticumeClient" in mf
        assert "Fabric-Mapping-Namespace: official" in mf
        assert "Fabric-Minecraft-Version: 26.3" in mf
        assert "dev/opticumeclient/OpticumeClient.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeMainMenuScreen.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeGalleryScreen.class" in names
        assert "dev/opticumeclient/client/ui/menu/CustomWallpaperStore.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeAltScreen.class" in names
        assert "dev/opticumeclient/client/ui/menu/OpticumeButtonStyle.class" in names
        assert "dev/opticumeclient/client/gui/SystemTheme.class" in names
        assert "dev/opticumeclient/client/gui/SystemModuleCard.class" in names
        assert "dev/opticumeclient/client/gui/SystemSearchModal.class" in names
        assert "dev/opticumeclient/client/render/font/OpticumeFonts.class" in names
        assert "dev/opticumeclient/client/render/font/MsdfFontFace.class" in names
        assert "dev/opticumeclient/client/combat/RotationManager.class" in names
        assert "dev/opticumeclient/client/combat/CombatRotationEngine.class" in names
        assert "dev/opticumeclient/client/modules/combat/AutoCrystalModule.class" in names
        assert "dev/opticumeclient/client/modules/other/AuctionHelperModule.class" in names
        assert "dev/opticumeclient/client/modules/other/CaptureBypassModule.class" in names
        assert "dev/opticumeclient/client/modules/visuals/killeffect/DeathPiece.class" in names
        assert "dev/opticumeclient/client/modules/visuals/BetterTabModule.class" in names
        assert "dev/opticumeclient/client/hud/MediaPlayerHud.class" in names
        assert "dev/opticumeclient/client/hud/CustomHotbarHud.class" in names
        assert "dev/opticumeclient/client/hud/ProfilerHud.class" in names
        assert "dev/opticumeclient/client/modules/other/DiscordRpcModule.class" in names
        assert "dev/opticumeclient/client/services/media/SmtcBridge.class" in names
        assert "dev/opticumeclient/client/services/network/ProxyManager.class" in names
        assert "dev/opticumeclient/client/services/auth/MicrosoftOAuthService.class" in names
        assert "dev/opticumeclient/client/combat/neuro/GruRotationModel.class" in names
        assert "dev/opticumeclient/client/gui/ViewModelEditorScreen.class" in names
        assert "dev/opticumeclient/client/script/ScriptManager.class" in names
        assert "assets/opticumeclient/shaders/core/ambience/motion_blur.fsh" in names
        assert "dev/opticumeclient/client/modules/visuals/targetesp/TargetEspRenderer.class" in names
        assert "assets/opticumeclient/shaders/core/targetesp/target_fullscreen.vsh" in names
        assert "assets/opticumeclient/targetesp/textures/targetesp/zenith_ghost.png" in names
        assert "dev/opticumeclient/client/render/RoundedTextureRenderState.class" in names
        assert "dev/opticumeclient/client/ui/menu/MenuLayoutPreferences.class" in names
        assert "dev/opticumeclient/client/ui/menu/MenuMusicWidget.class" in names
        assert "assets/opticumeclient/shaders/core/ui_rounded_texture.vsh" in names
        assert "assets/opticumeclient/shaders/core/ui_rounded_texture.fsh" in names
        assert "assets/opticumeclient/icon.png" in names
        assert "assets/opticumeclient/textures/menu/icons/singleplayer.png" in names
        for wp_idx in range(1, 21):
            assert f"assets/opticumeclient/textures/menu/wallpapers/wallpaper_{wp_idx}.png" in names, f"missing wallpaper_{wp_idx}.png"
            assert f"assets/opticumeclient/textures/menu/wallpapers/wallpaper_{wp_idx}_preview.png" in names, f"missing wallpaper_{wp_idx}_preview.png"
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
