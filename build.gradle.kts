// Framework file: the same in every CysticTurtle85 mod. Change it in the minecraft-multiloader-mods skill
// (assets/template/build.gradle.kts) and sync it into mods with scripts/new_mod.py sync <mod folder>.
import com.modrinth.minotaur.ModrinthExtension
import net.fabricmc.loom.api.LoomGradleExtensionAPI
import net.neoforged.moddevgradle.dsl.NeoForgeExtension
import net.neoforged.moddevgradle.legacyforge.dsl.LegacyForgeExtension

plugins {
    id("net.fabricmc.fabric-loom-remap") version "1.18.2" apply false // Fabric, obfuscated Minecraft (<= 1.21.11)
    id("net.fabricmc.fabric-loom") version "1.18.2" apply false       // Fabric, unobfuscated Minecraft (26.x)
    id("net.neoforged.moddev") version "2.0.147" apply false
    id("net.neoforged.moddev.legacyforge") version "2.0.147" apply false
    id("com.modrinth.minotaur") version "2.10.0" apply false
}

/**
 * One source tree serves every target. Lines between `//#if <condition>` and `//#endif`
 * (with optional `//#elif` / `//#else`) are only kept when the condition holds for the
 * target being built. Conditions compare the Minecraft version (`MC >= 1.21.5`) or test
 * the loader (`FABRIC`, `NEOFORGE`, `FORGE`), joined with `&&` / `||` and negated with `!`.
 * The same syntax works in .json/.toml resources, where directive lines are dropped.
 */
class Preprocessor(private val vars: Map<String, String>, private val keepLineNumbers: Boolean) {
    private class Block(val outerActive: Boolean, var active: Boolean, var matched: Boolean)

    private val blocks = ArrayDeque<Block>()
    private val active get() = blocks.lastOrNull()?.active ?: true

    fun line(text: String): String? {
        val directive = DIRECTIVE.find(text) ?: return if (active) text else blank()
        val (kind, condition) = directive.destructured
        when (kind) {
            "if" -> active.let { outer -> (outer && eval(condition)).let { blocks.addLast(Block(outer, it, it)) } }
            "elif" -> blocks.last().apply { active = outerActive && !matched && eval(condition); matched = matched || active }
            "else" -> blocks.last().apply { active = outerActive && !matched; matched = true }
            "endif" -> blocks.removeLast()
        }
        return blank()
    }

    private fun blank() = if (keepLineNumbers) "" else null

    private fun eval(condition: String) = condition.split("||").any { alt -> alt.split("&&").all { atom(it.trim()) } }

    private fun atom(atom: String): Boolean {
        if (atom.startsWith("!")) return !atom(atom.substring(1).trim())
        COMPARISON.matchEntire(atom)?.let {
            val (name, op, version) = it.destructured
            val c = compareVersions(vars[name] ?: error("Unknown preprocessor variable '$name'"), version)
            return when (op) { ">=" -> c >= 0; "<=" -> c <= 0; ">" -> c > 0; "<" -> c < 0; "==" -> c == 0; else -> c != 0 }
        }
        return vars[atom]?.toBooleanStrict() ?: error("Unknown preprocessor flag '$atom'")
    }

    companion object {
        val DIRECTIVE = Regex("""^\s*//\s*#(if|elif|else|endif)\b\s*(.*?)\s*$""")
        val COMPARISON = Regex("""^(\w+)\s*(>=|<=|==|!=|>|<)\s*([\d.]+)$""")
    }
}

fun compareVersions(a: String, b: String): Int {
    val x = a.split('.').map(String::toInt)
    val y = b.split('.').map(String::toInt)
    for (i in 0 until maxOf(x.size, y.size)) {
        val diff = x.getOrElse(i) { 0 } - y.getOrElse(i) { 0 }
        if (diff != 0) return diff
    }
    return 0
}

/** A comma-separated property as a list, empty when the property isn't set. */
fun Project.listProperty(name: String): List<String> =
    (findProperty(name) as String?).orEmpty().split(',').map(String::trim).filter(String::isNotEmpty)

val modId = property("mod_id") as String
val modVersion = property("mod_version") as String
val modrinthProject = (findProperty("modrinth_project") as String?).orEmpty()
// `geckolib=true` in gradle.properties: the mod renders with GeckoLib (dependency, metadata and Modrinth dependency).
val usesGeckoLib = (findProperty("geckolib") as String?).toBoolean()

subprojects {
    val loader = property("loader") as String
    val mc = property("minecraft_version") as String
    val gameVersions = (property("game_versions") as String).split(',').map(String::trim)
    val javaVersion = (property("java_version") as String).toInt()
    val unobfuscated = compareVersions(mc, "26") >= 0
    val loaderName = mapOf("fabric" to "Fabric", "neoforge" to "NeoForge", "forge" to "Forge").getValue(loader)

    version = "$modVersion+$mc"
    group = property("maven_group") as String

    apply(plugin = when (loader) {
        "fabric" -> if (unobfuscated) "net.fabricmc.fabric-loom" else "net.fabricmc.fabric-loom-remap"
        "neoforge" -> "net.neoforged.moddev"
        "forge" -> "net.neoforged.moddev.legacyforge"
        else -> error("Unknown loader '$loader' in ${project.name}")
    })
    apply(plugin = "com.modrinth.minotaur")

    extensions.configure<BasePluginExtension> { archivesName = "$modId-$loader" }
    extensions.configure<JavaPluginExtension> { toolchain.languageVersion = JavaLanguageVersion.of(javaVersion) }
    tasks.withType<JavaCompile>().configureEach { options.encoding = "UTF-8"; options.release = javaVersion }

    repositories {
        maven("https://dl.cloudsmith.io/public/geckolib3/geckolib/maven/") {
            name = "GeckoLib"
            content { includeGroup("com.geckolib"); includeGroupByRegex("software\\.bernie.*"); includeGroup("com.eliotlash.mclib") }
        }
        maven("https://api.modrinth.com/maven") { name = "Modrinth"; content { includeGroup("maven.modrinth") } }
    }

    // --- Sources: preprocess the shared tree + this loader's tree for this target ---------------
    val flags = mapOf(
        "MC" to mc,
        "FABRIC" to (loader == "fabric").toString(),
        "NEOFORGE" to (loader == "neoforge").toString(),
        "FORGE" to (loader == "forge").toString(),
        "GECKOLIB" to usesGeckoLib.toString(),
    )
    val templateProps = mapOf(
        "version" to version.toString(),
        "mod_id" to modId,
        "mod_name" to rootProject.property("mod_name"),
        "description" to rootProject.property("mod_description"),
        "authors" to rootProject.property("mod_authors"),
        "license" to rootProject.property("mod_license"),
        "homepage" to rootProject.property("mod_homepage"),
        "sources" to rootProject.property("mod_sources"),
        "issues" to rootProject.property("mod_issues"),
        "java_version" to javaVersion.toString(),
        "minecraft_range_fabric" to ">=${gameVersions.first()} <=${gameVersions.last()}",
        "minecraft_range_maven" to "[${gameVersions.first()},${gameVersions.last()}]",
        "loader_min" to (findProperty("loader_min") ?: "0").toString(),
    )
    val preprocessJava = tasks.register<Sync>("preprocessJava") {
        inputs.properties(flags)
        // The filter below lives in this script: rerun when the script changes.
        inputs.file(rootProject.file("build.gradle.kts"))
        from(rootProject.file("src/main/java"), rootProject.file("src/$loader/java"))
        into(layout.buildDirectory.dir("preprocessed/java"))
        eachFile {
            val pp = Preprocessor(flags, keepLineNumbers = true)
            // GeckoLib for 26.x moved from software.bernie.geckolib to com.geckolib; sources use the old name. Sources also
            // use 1.21.11+'s Identifier: older versions call it ResourceLocation, and before 1.21 it's built with `new`.
            filter { line: String -> pp.line(line)?.let { if (unobfuscated) it.replace("software.bernie.geckolib.", "com.geckolib.") else it }?.let { renameIdentifier(geckolibImports(it, mc), mc) } }
        }
    }
    val preprocessResources = tasks.register<Sync>("preprocessResources") {
        inputs.properties(flags + templateProps)
        from(rootProject.file("src/main/resources"), rootProject.file("src/$loader/resources"))
        into(layout.buildDirectory.dir("preprocessed/resources"))
        filesMatching(listOf("**/*.json", "**/*.toml", "**/*.mcmeta")) {
            val pp = Preprocessor(flags, keepLineNumbers = false)
            filter { line: String -> pp.line(line) }
        }
        filesMatching(listOf("fabric.mod.json", "META-INF/*.toml")) { expand(templateProps) }
        eachFile {
            // Minecraft 1.21 renamed data folders to singular (recipes -> recipe, tags/items -> tags/item).
            if (compareVersions(mc, "1.21") < 0) path = path.replace("/recipe/", "/recipes/").replace("/tags/item/", "/tags/items/")
            // GeckoLib 5 (Minecraft 1.21.5+) loads models and animations from assets/<mod>/geckolib/.
            if (compareVersions(mc, "1.21.5") >= 0) path = path
                .replace("assets/$modId/geo/", "assets/$modId/geckolib/models/")
                .replace("assets/$modId/animations/", "assets/$modId/geckolib/animations/")
        }
    }
    extensions.configure<SourceSetContainer> {
        named("main") {
            java.setSrcDirs(listOf(preprocessJava))
            resources.setSrcDirs(listOf(preprocessResources))
        }
    }
    val sourceSets = the<SourceSetContainer>()
    val mainSourceSet = sourceSets["main"]
    // Dev-only mod that lets tools/smoke_test.py drive the client (see TestDriver). Only the runClient run loads it;
    // it is never part of a release jar.
    // Preprocessed like the main code, so one driver serves every Minecraft version.
    val preprocessTestDriver = tasks.register<Sync>("preprocessTestDriver") {
        inputs.properties(flags)
        inputs.file(rootProject.file("build.gradle.kts"))
        from(rootProject.file("src/testdriver/common/java"), rootProject.file("src/testdriver/$loader/java"))
        into(layout.buildDirectory.dir("preprocessed/testdriver"))
        eachFile {
            val pp = Preprocessor(flags, keepLineNumbers = true)
            filter { line: String -> pp.line(line)?.let { renameIdentifier(it, mc) } }
        }
    }
    val testDriver = sourceSets.create("testdriver") {
        java.setSrcDirs(listOf(preprocessTestDriver))
        resources.setSrcDirs(listOf(rootProject.file("src/testdriver/$loader/resources")))
        compileClasspath += mainSourceSet.compileClasspath + mainSourceSet.output
        runtimeClasspath += mainSourceSet.runtimeClasspath + mainSourceSet.output
    }

    // --- Loader toolchains -------------------------------------------------------------------
    val geckolib = if (!usesGeckoLib) null
    else if (unobfuscated) "com.geckolib:geckolib-$loader-$mc:${property("geckolib_version")}"
    else "software.bernie.geckolib:geckolib-$loader-$mc:${property("geckolib_version")}"

    // Extra mods for dev runs only (maven.modrinth:<project>:<version>, comma-separated).
    val testMods = listProperty("test_mods")
    // runClient is for playing (run/client). runTestClient is the test harness's (tools/mctest.py): its own folder
    // (run/testclient, so tests never touch the author's options, worlds or config), it joins the test server on this
    // machine, and its name in the command line is how tools/mcwindow.ps1 finds it and only it.
    val joinArgs = listOf("--quickPlayMultiplayer", "127.0.0.1:25565")

    val releaseJar: TaskProvider<out AbstractArchiveTask> = when (loader) {
        "fabric" -> {
            val loom = extensions.getByType<LoomGradleExtensionAPI>()
            val impl = if (unobfuscated) "implementation" else "modImplementation"
            dependencies {
                "minecraft"("com.mojang:minecraft:$mc")
                if (!unobfuscated) "mappings"(loom.officialMojangMappings())
                add(impl, "net.fabricmc:fabric-loader:${rootProject.property("fabric_loader_version")}")
                add(impl, "net.fabricmc.fabric-api:fabric-api:${property("fabric_api_version")}")
                geckolib?.let { add(impl, it) }
            }
            // Loom drops the libraries a mod bundles inside its jar when it adds it to the dev classpath,
            // so test mods go into the client's mods folder instead, as in a real game.
            val testModJars = configurations.create("testModJars") { isTransitive = false }
            testMods.forEach { dependencies.add(testModJars.name, it) }
            for ((run, dir) in listOf("runClient" to "run/client", "runTestClient" to "run/testclient")) {
                val copy = tasks.register<Sync>("copyTestModsFor${run.removePrefix("run")}") { from(testModJars); into("$dir/mods") }
                tasks.matching { it.name == run }.configureEach { dependsOn(copy) }
            }
            loom.mods.register(modId) { sourceSet(mainSourceSet) }
            loom.mods.register("${modId}_testdriver") { sourceSet(testDriver) }
            loom.runs.named("client") { runDir("run/client"); source(testDriver) }
            loom.runs.register("testClient") { client(); runDir("run/testclient"); source(testDriver); programArgs(joinArgs) }
            loom.runs.named("server") { runDir("run/server") }
            tasks.named<AbstractArchiveTask>(if (unobfuscated) "jar" else "remapJar")
        }
        "neoforge" -> {
            extensions.configure<NeoForgeExtension> {
                version = property("neoforge_version") as String
                mods.register("${modId}_testdriver") { sourceSet(testDriver) }
                runs {
                    register("client") {
                        client()
                        gameDirectory.set(file("run/client"))
                        sourceSet.set(testDriver)
                    }
                    register("testClient") {
                        client()
                        gameDirectory.set(file("run/testclient"))
                        sourceSet.set(testDriver)
                        programArguments.addAll(joinArgs)
                    }
                    register("server") { server(); gameDirectory.set(file("run/server")); programArgument("--nogui") }
                }
                mods { register(modId) { sourceSet(mainSourceSet) } }
            }
            dependencies {
                geckolib?.let { "implementation"(it) }
                testMods.forEach { "runtimeOnly"(it) }
            }
            tasks.named<AbstractArchiveTask>("jar")
        }
        else -> {
            extensions.configure<LegacyForgeExtension> {
                version = "$mc-${property("forge_version")}"
                runs {
                    register("client") {
                        client()
                        gameDirectory.set(file("run/client"))
                        sourceSet.set(testDriver)
                    }
                    register("testClient") {
                        client()
                        gameDirectory.set(file("run/testclient"))
                        sourceSet.set(testDriver)
                        programArguments.addAll(joinArgs)
                    }
                    register("server") { server(); gameDirectory.set(file("run/server")); programArgument("--nogui") }
                }
                mods {
                    register(modId) { sourceSet(the<SourceSetContainer>()["main"]) }
                    register("${modId}_testdriver") { sourceSet(testDriver) }
                }
            }
            // Forge has no Mojang names at runtime: mixins need a refmap (the "refmap" key in the mixin config is
            // //#if FORGE) and the config named in the jar manifest. Only for mods that have mixins.
            val mixinConfig = rootProject.file("src/main/resources/$modId.mixins.json")
            if (mixinConfig.exists()) {
                extensions.configure<net.neoforged.moddevgradle.legacyforge.dsl.MixinExtension> {
                    add(the<SourceSetContainer>()["main"], "$modId.refmap.json")
                    config("$modId.mixins.json")
                }
                dependencies { "annotationProcessor"("org.spongepowered:mixin:0.8.5:processor") }
                tasks.named<Jar>("jar") { manifest.attributes("MixinConfigs" to "$modId.mixins.json") }
            }
            dependencies {
                geckolib?.let { "modImplementation"(it) }
                testMods.forEach { "modRuntimeOnly"(it) }
            }
            tasks.named<AbstractArchiveTask>("reobfJar")
        }
    }

    // Copies the jar players install into build/dist at the repo root.
    tasks.register<Copy>("dist") {
        from(releaseJar)
        into(rootProject.layout.buildDirectory.dir("dist"))
    }

    // --- Modrinth ------------------------------------------------------------------------------
    if (modrinthProject.isNotEmpty()) extensions.configure<ModrinthExtension> {
        token = providers.environmentVariable("MODRINTH_TOKEN")
        // `-PmodrinthDryRun` prints what would be uploaded without publishing anything.
        debugMode = rootProject.hasProperty("modrinthDryRun")
        projectId = modrinthProject
        versionNumber = "$modVersion+$mc-$loader"
        versionName = "${rootProject.property("mod_name")} $modVersion ($loaderName $mc)"
        // release, beta or alpha (gradle.properties `release_type`, default release).
        versionType = (rootProject.findProperty("release_type") as String?) ?: "release"
        uploadFile.set(releaseJar)
        this.gameVersions.addAll(gameVersions)
        loaders.addAll(if (loader == "fabric") listOf("fabric", "quilt") else listOf(loader))
        changelog = rootProject.file("CHANGELOG.md").readText().substringAfter("\n## ").substringAfter('\n').substringBefore("\n## ").trim()
        dependencies {
            if (usesGeckoLib) required.project("geckolib")
            if (loader == "fabric") required.project("fabric-api")
            listProperty("optional_mods").forEach { optional.project(it) }
        }
    }
    // Minotaur uploads through Modrinth's v2 API, which has no environment field: set each new version's environment
    // (what the page shows as "Client and server" etc.) through v3 right after its upload, from gradle.properties
    // `modrinth_environment` (values and how to choose: the skill's references/modrinth.md). Missing: stop before uploading.
    if (modrinthProject.isNotEmpty()) tasks.named<com.modrinth.minotaur.TaskModrinthUpload>("modrinth") {
        val environment = rootProject.findProperty("modrinth_environment") as String?
        doFirst { requireNotNull(environment) { "Set modrinth_environment in gradle.properties (e.g. client_and_server)" } }
        doLast {
            val id = uploadInfo?.id ?: return@doLast // dry run: nothing was uploaded
            val request = java.net.http.HttpRequest.newBuilder(java.net.URI("https://api.modrinth.com/v3/version/$id"))
                .header("Authorization", System.getenv("MODRINTH_TOKEN"))
                .header("Content-Type", "application/json")
                .header("User-Agent", "CysticTurtle85/minecraft-multiloader-mods")
                .method("PATCH", java.net.http.HttpRequest.BodyPublishers.ofString("{\"environment\":\"$environment\"}"))
                .build()
            val response = java.net.http.HttpClient.newHttpClient().send(request, java.net.http.HttpResponse.BodyHandlers.ofString())
            check(response.statusCode() == 204) { "Setting the environment of Modrinth version $id failed: ${response.statusCode()} ${response.body()}" }
            logger.lifecycle("Modrinth version $id: environment $environment")
        }
    }
}

tasks.register("distAll") { dependsOn(subprojects.map { it.tasks.named("dist") }) }

/** Sources are written against 1.21.11+'s `Identifier`; older versions call it `ResourceLocation` (constructed with `new` before 1.21). */
fun renameIdentifier(line: String, mc: String): String {
    // JSpecify's @Nullable comes with 1.21.11+; older versions have JetBrains' (also usable on types).
    if (compareVersions(mc, "1.21.11") < 0 && line.startsWith("import org.jspecify.annotations.Nullable;"))
        return "import org.jetbrains.annotations.Nullable;"
    // EntitySpawnReason was MobSpawnType before 1.21.2 (same constants).
    if (compareVersions(mc, "1.21.2") < 0 && line.contains("EntitySpawnReason"))
        return renameIdentifier(line.replace("EntitySpawnReason", "MobSpawnType"), mc)
    if (compareVersions(mc, "1.21.11") >= 0 || !line.contains("Identifier")) return line
    var out = line.replace(Regex("""\bIdentifier\b"""), "ResourceLocation")
    if (compareVersions(mc, "1.21") < 0)
        out = out.replace("ResourceLocation.fromNamespaceAndPath(", "new ResourceLocation(").replace("ResourceLocation.withDefaultNamespace(", "new ResourceLocation(")
    return out
}

/**
 * Sources import GeckoLib 5.4+'s class names; older GeckoLib versions kept some classes elsewhere (checked against the
 * jars: 5.1-5.3 on 1.21.5-1.21.10, 4.x on 1.21-1.21.4, 4.x with `core.*` packages on 1.20.1).
 */
fun geckolibImports(line: String, mc: String): String {
    if (!line.startsWith("import software.bernie.geckolib.") || compareVersions(mc, "1.21.11") >= 0) return line
    val gl = "software.bernie.geckolib."
    var out = line.replace("${gl}cache.model.", "${gl}cache.object.")
        .replace("${gl}animation.object.PlayState", "${gl}animation.PlayState")
    if (compareVersions(mc, "1.21.5") >= 0)
        return out.replace("${gl}animation.AnimationController", "${gl}animatable.processing.AnimationController")
    out = out.replace("${gl}animatable.manager.AnimatableManager", "${gl}animation.AnimatableManager")
    if (compareVersions(mc, "1.21") >= 0) return out
    return out.replace("${gl}animation.AnimationController", "${gl}core.animation.AnimationController")
        .replace("${gl}animation.AnimatableManager", "${gl}core.animation.AnimatableManager")
        .replace("${gl}animation.RawAnimation", "${gl}core.animation.RawAnimation")
        .replace("${gl}animation.PlayState", "${gl}core.object.PlayState")
        .replace("${gl}animatable.instance.AnimatableInstanceCache", "${gl}core.animatable.instance.AnimatableInstanceCache")
        .replace("${gl}animatable.GeoAnimatable;", "${gl}core.animatable.GeoAnimatable;")
        .replace("${gl}constant.dataticket.DataTicket;", "${gl}core.object.DataTicket;")
}
