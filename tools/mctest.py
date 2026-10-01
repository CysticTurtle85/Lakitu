"""In-game test harness core. Framework file: the same in every CysticTurtle85 mod; change it in the
minecraft-multiloader-mods skill (assets/template/tools/mctest.py) and sync with scripts/new_mod.py sync.
A mod's tools/smoke_test.py defines the scene, in parts, and calls main([part, part, ...]).

For each target (a folder in targets/):
  1. Install the real loader server under $MOD_TEST_DIR/<target> (default D:/<mod id>-test/<target>) with the release
     jar from build/dist plus its Modrinth dependencies (GeckoLib when gradle.properties has geckolib=true, Fabric API on Fabric, and
     the target's `server_test_mods`). Flat world, Easy, RCON on, natural spawning off.
  2. Join with the dev client (gradlew :<target>:runTestClient, its own folder run/testclient). Input goes through the dev-only test
     driver mod (src/testdriver): 26.x reads input via SDL3, which ignores keys posted to a window without focus,
     and the harness must never take focus from the user. Screenshots use PrintWindow on the test client only.
  3. Run the scene, grade the checks, write tools/smoke/<target>.report.txt and screenshots.

Usage: python tools/smoke_test.py [--only part,part] <target> [<target> ...]

Test only what a change touches: each part (a function taking the Game) sets up its own state, so `--only flight`
runs the flight part alone. The whole scene is for releases and changes that touch everything. The printed summary
leaves out the RCON log; tools/smoke/<target>.report.txt has it.
"""
import json, os, pathlib, re, shutil, socket, struct, subprocess, sys, time, urllib.parse, urllib.request, zipfile

REPO = pathlib.Path(__file__).resolve().parent.parent
OUT = REPO / "tools" / "smoke"
WINDOW_PS1 = REPO / "tools" / "mcwindow.ps1"
JDK = {17: os.environ.get("JDK17", "C:/Program Files/Eclipse Adoptium/jdk-17.0.11.9-hotspot"),
       21: os.environ.get("JDK21", "C:/Program Files/Eclipse Adoptium/jdk-21.0.4.7-hotspot"),
       25: os.environ.get("JDK25", "C:/Users/Jayde/.gradle/jdks/jdk-25.0.4.1+1")}
ENV = dict(os.environ, JAVA_HOME=JDK[25], GRADLE_USER_HOME=os.environ.get("GRADLE_USER_HOME", "D:/gradle-home"))
FABRIC_LOADER = "0.19.5"


def properties(path):
    return dict(l.split("=", 1) for l in path.read_text().splitlines() if "=" in l and not l.lstrip().startswith("#"))


MOD = properties(REPO / "gradle.properties")
MOD_ID = MOD["mod_id"]
TEST = pathlib.Path(os.environ.get("MOD_TEST_DIR", f"D:/{MOD_ID}-test"))
UA = {"User-Agent": f"CysticTurtle85/{MOD_ID} (release testing)"}


def http_json(url):
    return json.load(urllib.request.urlopen(urllib.request.Request(url, headers=UA)))


def download(url, dest):
    dest.parent.mkdir(parents=True, exist_ok=True)
    with urllib.request.urlopen(urllib.request.Request(url, headers=UA)) as r, open(dest, "wb") as f:
        shutil.copyfileobj(r, f)


def modrinth_file(project, mc, loader):
    q = urllib.parse.urlencode({"game_versions": json.dumps([mc]), "loaders": json.dumps([loader])})
    v = http_json(f"https://api.modrinth.com/v2/project/{project}/version?{q}")[0]
    f = next(f for f in v["files"] if f["primary"])
    return f["url"], f["filename"]


class Rcon:
    def __init__(self, password="modtest", port=25575):
        self.s = socket.create_connection(("127.0.0.1", port), timeout=20)
        self.n = 0
        self._send(3, password)
        if self._recv()[0] == -1:
            raise RuntimeError("RCON auth failed")

    def _send(self, kind, body):
        self.n += 1
        data = struct.pack("<ii", self.n, kind) + body.encode() + b"\0\0"
        self.s.sendall(struct.pack("<i", len(data)) + data)

    def _read(self, n):
        buf = b""
        while len(buf) < n:
            chunk = self.s.recv(n - len(buf))
            if not chunk:
                raise ConnectionError("RCON closed")
            buf += chunk
        return buf

    def _recv(self):
        (length,) = struct.unpack("<i", self._read(4))
        data = self._read(length)
        rid, _ = struct.unpack("<ii", data[:8])
        return rid, data[8:-2].decode(errors="replace")

    def cmd(self, command):
        self._send(2, command)
        return self._recv()[1]


def wait_for(path, pattern, timeout, proc=None):
    deadline, rx = time.time() + timeout, re.compile(pattern)
    while time.time() < deadline:
        if path.exists() and rx.search(path.read_text(errors="replace")):
            return True
        if proc is not None and proc.poll() is not None:
            return False
        time.sleep(2)
    return False


def data_version(mc):
    """The client's data version, read from the Minecraft jar Loom cached (the same for every loader)."""
    jar = pathlib.Path(ENV["GRADLE_USER_HOME"]) / "caches" / "fabric-loom" / mc / "minecraft-client.jar"
    with zipfile.ZipFile(jar) as z:
        return json.loads(z.read("version.json"))["world_version"]


def install_server(target, p):
    """Installs the production server once per target; refreshes mods, config and world every run."""
    mc, loader, java = p["minecraft_version"], p["loader"], JDK[int(p["java_version"])] + "/bin/java.exe"
    d = TEST / target
    d.mkdir(parents=True, exist_ok=True)
    if loader == "fabric":
        launcher = d / "fabric-server-launch.jar"
        if not launcher.exists():
            installer = http_json("https://meta.fabricmc.net/v2/versions/installer")[0]["version"]
            download(f"https://meta.fabricmc.net/v2/versions/loader/{mc}/{FABRIC_LOADER}/{installer}/server/jar", launcher)
        cmd = [java, "-Xmx2G", "-jar", launcher.name, "nogui"]
    else:
        if loader == "neoforge":
            v = p["neoforge_version"]
            url = f"https://maven.neoforged.net/releases/net/neoforged/neoforge/{v}/neoforge-{v}-installer.jar"
            args = d / f"libraries/net/neoforged/neoforge/{v}/win_args.txt"
        else:
            v = f"{mc}-{p['forge_version']}"
            url = f"https://maven.minecraftforge.net/net/minecraftforge/forge/{v}/forge-{v}-installer.jar"
            args = d / f"libraries/net/minecraftforge/forge/{v}/win_args.txt"
        if not args.exists():
            download(url, d / "installer.jar")
            r = subprocess.run([java, "-jar", "installer.jar", "--installServer"], cwd=d, capture_output=True, text=True)
            if not args.exists():
                raise RuntimeError("server install failed:\n" + r.stdout[-2000:] + r.stderr[-2000:])
        cmd = [java, "-Xmx2G", "@" + str(args.relative_to(d)).replace("\\", "/"), "nogui"]

    mods = d / "mods"
    shutil.rmtree(mods, ignore_errors=True)
    mods.mkdir()
    jar = REPO / "build/dist" / f"{MOD_ID}-{loader}-{MOD['mod_version']}+{mc}.jar"
    shutil.copy(jar, mods / jar.name)
    installed = [jar.name]
    deps = (["geckolib"] if MOD.get("geckolib", "false").strip() == "true" else []) + (["fabric-api"] if loader == "fabric" else [])
    deps += [m.strip() for m in p.get("server_test_mods", "").split(",") if m.strip()]
    for project in deps:
        url, name = modrinth_file(project, mc, loader)
        download(url, mods / name)
        installed.append(name)

    shutil.rmtree(d / "world", ignore_errors=True)
    shutil.rmtree(d / "config", ignore_errors=True)
    (d / "logs" / "latest.log").unlink(missing_ok=True)
    (d / "eula.txt").write_text("eula=true\n")
    (d / "server.properties").write_text("\n".join([
        "server-ip=127.0.0.1", "white-list=false", "enforce-whitelist=false", "online-mode=false", "enforce-secure-profile=false",
        "enable-rcon=true", "rcon.password=modtest", "rcon.port=25575", "server-port=25565",
        "level-type=minecraft\\:flat", "generate-structures=false", "difficulty=easy", "spawn-protection=0",
        "view-distance=6", "simulation-distance=6", "sync-chunk-writes=false", f"motd={MOD['mod_name']} test", ""]))
    return d, cmd, installed


def prepare_client(target, mc):
    client = REPO / "targets" / target / "run" / "testclient"
    client.mkdir(parents=True, exist_ok=True)
    # Merge into the client's own options. Without a "version:" line the game treats the file as an ancient format
    # and rejects all of it, so a fresh file starts from the game's data version.
    options_file = client / "options.txt"
    options = {"version": str(data_version(mc))}
    if options_file.exists():
        options.update(l.split(":", 1) for l in options_file.read_text().splitlines() if ":" in l)
    options.update({
        "onboardAccessibility": "false", "skipMultiplayerWarning": "true", "joinedFirstServer": "true", "tutorialStep": "none",
        "renderDistance": "6", "simulationDistance": "6", "soundCategory_master": "0.0", "pauseOnLostFocus": "false",
        "narrator": "0", "guiScale": "2", "fullscreen": "false",
        # Chat stays on (the test driver reads its commands from it) but invisible, so it never covers a screenshot.
        "chatOpacity": "0.0", "textBackgroundOpacity": "0.0",
    })
    options_file.write_text("".join(f"{k}:{v}\n" for k, v in options.items()))
    (client / "logs" / "latest.log").unlink(missing_ok=True)
    (client / "config" / f"{MOD_ID}.json").unlink(missing_ok=True)  # always test the mod's defaults
    if "neoforge" in target:
        cfg = client / "config" / "neoforge-client.toml"
        cfg.parent.mkdir(parents=True, exist_ok=True)
        text = cfg.read_text() if cfg.exists() else ""
        cfg.write_text(re.sub(r"(?m)^showLoadWarnings = true$", "showLoadWarnings = false", text) if text else "showLoadWarnings = false\n")
    return client


def window(action, **kw):
    args = ["powershell", "-NoProfile", "-ExecutionPolicy", "Bypass", "-File", str(WINDOW_PS1), "-Action", action]
    for k, v in kw.items():
        args += [f"-{k}", str(v)]
    r = subprocess.run(args, capture_output=True, text=True)
    return (r.stdout + r.stderr).strip()


def stop_test_client():
    pid = window("pid")
    if pid.isdigit():
        subprocess.run(["taskkill", "/T", "/F", "/PID", pid], capture_output=True)


def free_test_ports():
    subprocess.run(["powershell", "-NoProfile", "-Command",
                    "Get-NetTCPConnection -LocalPort 25565,25575 -State Listen -ErrorAction SilentlyContinue | "
                    "ForEach-Object { Stop-Process -Id $_.OwningProcess -Force -ErrorAction SilentlyContinue }"],
                   capture_output=True)
    stop_test_client()
    time.sleep(3)


PROBLEM = re.compile(r"(Exception|ERROR|Couldn't parse|missing model|Missing texture|Unable to load model|Unknown item|"
                     + MOD_ID + r".*WARN)", re.I)
NOISE = re.compile(r"(Realms|realms|YggdrasilAuthentication|Failed to fetch user properties|Couldn't load profile|"
                   r"Failed to verify authentication|ProfileKeyPairManager|telemetry|[Nn]arrator|OpenAL|Signature is missing|"
                   r"Failed to fetch|Unable to create an account|authlib|minecraft\.net|Session|Failed to request|"
                   r"UnknownHostException|Couldn't connect to|Failed to retrieve profile key pair|Perflib|oshi|OSProcess|"
                   r"SystemReport|Win32Exception|No key layers|MinecraftClientHttpException|status=401)", re.I)


def problems(log):
    if not log.exists():
        return [f"(no log at {log})"]
    return [l[:300] for l in log.read_text(errors="replace").splitlines() if PROBLEM.search(l) and not NOISE.search(l)][:30]


def number(text):
    """The number at the end of an RCON reply ('... has the following entity data: 20.0f')."""
    m = re.search(r"(-?\d+(?:\.\d+)?(?:E-?\d+)?)[dfbsL]?\s*$", text.strip())
    return float(m.group(1)) if m else None


class Game:
    """What a scene works with: RCON, the test driver, screenshots, and the report's checks and values."""

    def __init__(self, target, props, rcon):
        self.target, self.props, self.rcon = target, props, rcon
        self.mc, self.loader = props["minecraft_version"], props["loader"]
        self.checks, self.values, self.log = {}, {}, []

    def run(self, command):
        reply = self.rcon.cmd(command)
        self.log.append((command, reply))
        return reply

    def passed(self, command):
        return "passed" in self.run(command).lower()

    def count(self, selector):
        self.run(f"execute store result score #n mctest if entity {selector}")
        m = re.search(r"has (\d+)", self.run("scoreboard players get #n mctest"))
        return int(m.group(1)) if m else -1

    def data(self, target, path):
        return number(self.run(f"data get entity {target} {path}"))

    def drive(self, command):
        """Sends a command to the dev-only test driver on the client (see src/testdriver)."""
        self.run(f'tellraw @a "mctest {command}"')

    def use(self, wait=1.5, hand="main"):
        """hand: main or off (e.g. throw what's in the off hand while the main hand holds a mount's item)."""
        self.drive("use" if hand == "main" else "use off")
        time.sleep(wait)

    def hold(self, key, seconds):
        """key: jump, sneak, forward, back, left or right."""
        self.drive(f"hold {key} {round(seconds * 20)}")
        time.sleep(seconds + 0.2)

    def view(self, camera):
        """camera: FIRST_PERSON, THIRD_PERSON_BACK or THIRD_PERSON_FRONT."""
        self.drive(f"view {camera}")
        time.sleep(2)

    def shot(self, name):
        path = OUT / f"{self.target}-{name}.png"
        window("shot", Out=path)
        return path

    def mc_at_least(self, version):
        """Whether this target's Minecraft version is at least `version` (numeric: 26.1 > 1.21.11)."""
        mine = [int(x) for x in self.mc.split(".")]
        other = [int(x) for x in version.split(".")]
        n = max(len(mine), len(other))
        return mine + [0] * (n - len(mine)) >= other + [0] * (n - len(other))

    def check(self, name, ok):
        self.checks[name] = bool(ok)
        return ok


def scene_parts(scene, only):
    """The parts to run: a scene is one function(game) or a list of them; `only` picks some by function name."""
    parts = list(scene) if isinstance(scene, (list, tuple)) else [scene]
    names = {part.__name__: part for part in parts}
    unknown = [n for n in only if n not in names]
    if unknown:
        raise SystemExit(f"unknown part(s): {', '.join(unknown)}; the parts are: {', '.join(names)}")
    return [part for part in parts if not only or part.__name__ in only]


def run_target(target, parts):
    free_test_ports()
    p = properties(REPO / "targets" / target / "gradle.properties")
    OUT.mkdir(exist_ok=True)
    report = {"target": target}
    server_dir, server_cmd, installed = install_server(target, p)
    report["server_mods"] = installed
    client_dir = prepare_client(target, p["minecraft_version"])
    server_out = OUT / f"{target}.server.out"
    client_out = OUT / f"{target}.client.out"
    server = subprocess.Popen(server_cmd, cwd=server_dir, stdout=open(server_out, "w"), stderr=subprocess.STDOUT, stdin=subprocess.DEVNULL)
    client = None
    try:
        if not wait_for(server_out, r"Done \(", 900, server):
            report["result"] = "FAIL: production server did not start"
            return report
        client = subprocess.Popen(["cmd", "/c", str(REPO / "gradlew.bat"), f":{target}:runTestClient", "--console=plain"],
                                  cwd=REPO, env=ENV, stdout=open(client_out, "w"), stderr=subprocess.STDOUT)
        client_log = client_dir / "logs" / "latest.log"
        deadline = time.time() + 900
        while not re.search(r"joined the game", server_out.read_text(errors="replace")):
            text = client_log.read_text(errors="replace") if client_log.exists() else ""
            broken = re.search(r"(Error loading mods|Loading errors encountered|Incompatible mods found|Crash report saved|"
                               r"requires .* which is missing|---- Minecraft Crash Report ----)", text)
            broken = broken or re.search(r"lost connection: (.*)", server_out.read_text(errors="replace"))
            if broken or client.poll() is not None or time.time() > deadline:
                report["result"] = "FAIL: client did not join" + (f" ({broken.group(1)})" if broken else "")
                return report
            time.sleep(3)
        time.sleep(12)

        game = Game(target, p, Rcon())
        game.run("scoreboard objectives add mctest dummy")
        # 26.x ignores spawn-monsters in server.properties (it's a game rule now); flat worlds spawn slimes.
        # Game rules are snake_case from 26.1 (camelCase before).
        game.run("gamerule spawn_mobs false" if game.mc_at_least("26.1") else "gamerule doMobSpawning false")
        game.run("kill @e[type=!minecraft:player]")
        game.run("time set noon")
        game.run("weather clear")
        try:
            for part in parts:
                part(game)
        finally:
            report.update(checks=game.checks, values=game.values, rcon=game.log)
            try:
                game.rcon.cmd("stop")
            except Exception:
                pass
        report["result"] = "PASS" if game.checks and all(game.checks.values()) else "CHECK FAILURES"
        return report
    finally:
        time.sleep(5)
        stop_test_client()
        for proc in (client, server):
            if proc is not None:
                try:
                    proc.wait(timeout=120)
                except subprocess.TimeoutExpired:
                    subprocess.run(["taskkill", "/T", "/F", "/PID", str(proc.pid)], capture_output=True)
        report["server_problems"] = problems(server_dir / "logs" / "latest.log")
        report["client_problems"] = problems(client_dir / "logs" / "latest.log")


def main(scene):
    args = sys.argv[1:]
    only = []
    if "--only" in args:
        i = args.index("--only")
        only = [n for n in args[i + 1].split(",") if n]
        del args[i:i + 2]
    parts = scene_parts(scene, only)
    for t in args:
        try:
            r = run_target(t, parts)
        except Exception as e:
            r = {"target": t, "result": f"ERROR: {e!r}"}
        summary = [f"===== {t}: {r.get('result')}  (parts: {', '.join(p.__name__ for p in parts)})",
                   "server mods: " + ", ".join(r.get("server_mods", [])),
                   "values: " + json.dumps(r.get("values", {})),
                   "checks:"] + [f"  {'ok  ' if v else 'FAIL'} {k}" for k, v in r.get("checks", {}).items()]
        problems_ = ["-- server problems:"] + r.get("server_problems", []) + ["-- client problems:"] + r.get("client_problems", [])
        rcon = [f"> {c}\n  {resp}" for c, resp in r.get("rcon", [])]
        OUT.mkdir(exist_ok=True)
        (OUT / f"{t}.report.txt").write_text("\n".join(summary + rcon + problems_), encoding="utf-8")
        print("\n".join(summary + problems_), flush=True)
