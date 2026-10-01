"""Exports Blockbench projects to GeckoLib models; animations stay hand-made in assets/<mod>/animations/.
Framework file (minecraft-multiloader-mods skill, assets/template/tools/bbmodel_to_geo.py): the model list lives in
art/models.json, so this script is the same in every mod.

    python tools/bbmodel_to_geo.py

For every entry in art/models.json it reads a box-UV "GeckoLib Animated Model" .bbmodel (Blockbench 4.x or 5.x) and
writes
  src/main/resources/assets/<mod>/geo/entity/<name>.geo.json   (the build moves it to geckolib/models/ for GeckoLib 5)
  src/main/resources/assets/<mod>/textures/entity/<name>.png   (the project's first texture)
  src/main/resources/assets/<mod>/textures/entity/<name>_<variant>.png   (for "variants": {"rain": "lakitu_rain.png"},
      the project's texture with that name: a second look on the same UVs, chosen by the renderer)
Several models can come from one project by leaving groups out. Groups or cubes hidden in Blockbench (eye icon off)
or set not to export are left out too, so old parts can stay in the project for reference.

Every model gets an extra "root" bone around everything, for whole-model animations (bobbing, spinning).
Coordinates follow Blockbench's own Bedrock export: X is mirrored, and X/Y rotations are negated.
"""
import base64
import json
import shutil
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
MOD_ID = dict(l.split("=", 1) for l in (ROOT / "gradle.properties").read_text().splitlines() if "=" in l and not l.startswith("#"))["mod_id"]
ASSETS = ROOT / "src/main/resources/assets" / MOD_ID
# [{"source": "art/thing.bbmodel", "name": "thing", "skip_groups": [], "variants": {"suffix": "texture name"}}, ...]
MODELS = [(m["source"], m["name"], m.get("skip_groups", []), m.get("variants", {}))
          for m in json.loads((ROOT / "art/models.json").read_text(encoding="utf-8"))["models"]]


def mirror_point(p):
    return [-p[0], p[1], p[2]]


def mirror_rotation(r):
    return [-r[0], -r[1], r[2]]


def is_zero(v):
    return v is None or all(abs(x) < 1e-9 for x in v)


def clean(v):
    return [round(x, 5) + 0 for x in v]


def exported(node):
    return node.get("export") is not False and node.get("visibility") is not False


def cube_json(element):
    frm, to = element["from"], element["to"]
    cube = {
        "origin": clean([-to[0], frm[1], frm[2]]),
        "size": clean([to[i] - frm[i] for i in range(3)]),
        "uv": element.get("uv_offset") or [0, 0],
    }
    if element.get("inflate"):
        cube["inflate"] = element["inflate"]
    if element.get("mirror_uv"):
        cube["mirror"] = True
    if not is_zero(element.get("rotation")):
        cube["pivot"] = clean(mirror_point(element["origin"]))
        cube["rotation"] = clean(mirror_rotation(element["rotation"]))
    return cube


def export(model, skip_groups):
    elements = {e["uuid"]: e for e in model["elements"]}
    # Blockbench 5 keeps group properties in "groups" and only uuids + children in the outliner; 4.x inlines them.
    groups = {g["uuid"]: g for g in model.get("groups", [])}
    bones = [{"name": "root", "pivot": [0, 0, 0]}]
    root_cubes = []

    def walk(node, parent):
        if isinstance(node, str):
            element = elements.get(node)
            return [cube_json(element)] if element and exported(element) else []
        group = {**node, **groups.get(node["uuid"], {})}
        if group["name"] in skip_groups or not exported(group):
            return []
        bone = {"name": group["name"], "parent": parent, "pivot": clean(mirror_point(group["origin"]))}
        if not is_zero(group.get("rotation")):
            bone["rotation"] = clean(mirror_rotation(group["rotation"]))
        bones.append(bone)
        cubes = []
        for child in node.get("children", []):
            cubes += walk(child, group["name"])
        if cubes:
            bone["cubes"] = cubes
        return []

    for node in model["outliner"]:
        root_cubes += walk(node, "root")
    if root_cubes:
        bones[0]["cubes"] = root_cubes

    return {
        "format_version": "1.12.0",
        "minecraft:geometry": [{
            "description": {
                "identifier": "geometry.unknown",
                "texture_width": model["resolution"]["width"],
                "texture_height": model["resolution"]["height"],
                "visible_bounds_width": 4,
                "visible_bounds_height": 3.5,
                "visible_bounds_offset": [0, 1.25, 0],
            },
            "bones": bones,
        }],
    }


def write_texture(model, project, out, name=None):
    """The project's first texture (or the one called `name`): embedded in the .bbmodel, or a file next to it."""
    texture = model["textures"][0] if name is None else next((t for t in model["textures"] if t.get("name") == name), None)
    if texture is None:
        raise FileNotFoundError(f"no texture called {name} in {project.name}")
    source = texture.get("source", "")
    if source.startswith("data:image/png;base64,"):
        out.write_bytes(base64.b64decode(source.split(",", 1)[1]))
        return
    for candidate in (texture.get("relative_path"), texture.get("name")):
        if candidate and (project.parent / candidate).is_file():
            shutil.copy(project.parent / candidate, out)
            return
    raise FileNotFoundError(f"no texture found for {project.name}")


def cube_count(geo):
    return sum(len(b.get("cubes", [])) for b in geo["minecraft:geometry"][0]["bones"])


def main():
    (ASSETS / "geo/entity").mkdir(parents=True, exist_ok=True)
    (ASSETS / "textures/entity").mkdir(parents=True, exist_ok=True)
    for source, name, skip, variants in MODELS:
        project = ROOT / source
        model = json.loads(project.read_text(encoding="utf-8"))
        geo = export(model, set(skip))
        (ASSETS / f"geo/entity/{name}.geo.json").write_text(json.dumps(geo, indent=2) + "\n", encoding="utf-8")
        write_texture(model, project, ASSETS / f"textures/entity/{name}.png")
        for suffix, texture_name in variants.items():
            write_texture(model, project, ASSETS / f"textures/entity/{name}_{suffix}.png", texture_name)
        bones = [b["name"] for b in geo["minecraft:geometry"][0]["bones"]]
        print(f"{name}: {cube_count(geo)} cubes, bones {bones}" + (f", texture variants {list(variants)}" if variants else ""))


if __name__ == "__main__":
    main()
