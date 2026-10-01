"""Creates this mod's Modrinth project (as a draft) from the files prepared for it, then fills in the page:

    python tools/modrinth_project.py            # checks the account and prints what it would create
    python tools/modrinth_project.py --create   # creates the draft project, icon, page, recipe image and gallery

Reads the token from the MODRINTH_TOKEN environment variable (or the Windows user environment), never from a file,
and never prints it. Needs the scopes: USER_READ, CREATE_PROJECT, PROJECT_READ, PROJECT_WRITE, VERSION_CREATE.
Uses: gradle.properties (title, summary, links, license), src/main/resources/assets/<mod_id>/icon.png,
art/modrinth/page.md ({{RECIPE_IMAGE}} is replaced by the uploaded art/recipe/*.png), art/modrinth/gallery.json with
the images in media/, and art/modrinth/project.json (slug, categories, sides). Writes the new project id into
gradle.properties (modrinth_project) so `gradlew modrinth` can upload the versions. Upload versions afterwards, then
press "Submit for review" on the project page.
"""
import json
import os
import subprocess
import sys
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

REPO = Path(__file__).resolve().parent.parent
API = "https://api.modrinth.com"
UA = "CysticTurtle85/mod-publisher (github.com/CysticTurtle85)"


def token():
    t = os.environ.get("MODRINTH_TOKEN")
    if not t and os.name == "nt":
        t = subprocess.run(["powershell", "-NoProfile", "-Command",
                            "[Environment]::GetEnvironmentVariable('MODRINTH_TOKEN','User')"],
                           capture_output=True, text=True).stdout.strip()
    if not t:
        sys.exit("MODRINTH_TOKEN is not set (set it yourself; never paste it into a chat)")
    return t


def call(method, path, body=None, headers=None):
    req = urllib.request.Request(API + path, data=body, method=method,
                                 headers={"Authorization": TOKEN, "User-Agent": UA, **(headers or {})})
    try:
        with urllib.request.urlopen(req, timeout=120) as r:
            data = r.read()
            return json.loads(data) if data else None
    except urllib.error.HTTPError as e:
        sys.exit(f"{method} {path}: {e.code} {e.read().decode(errors='replace')[:500]}")


def props():
    return dict(l.split("=", 1) for l in (REPO / "gradle.properties").read_text().splitlines() if "=" in l and not l.startswith("#"))


def multipart(fields, files):
    boundary = uuid.uuid4().hex
    out = b""
    for name, value in fields.items():
        out += f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"\r\n\r\n{value}\r\n'.encode()
    for name, (filename, data, ctype) in files.items():
        out += (f'--{boundary}\r\nContent-Disposition: form-data; name="{name}"; filename="{filename}"\r\n'
                f'Content-Type: {ctype}\r\n\r\n').encode() + data + b"\r\n"
    return out + f"--{boundary}--\r\n".encode(), f"multipart/form-data; boundary={boundary}"


def main(create):
    p = props()
    mod_id = p["mod_id"]
    meta = json.loads((REPO / "art/modrinth/project.json").read_text(encoding="utf-8"))
    gallery = json.loads((REPO / "art/modrinth/gallery.json").read_text(encoding="utf-8"))["images"]
    body = (REPO / "art/modrinth/page.md").read_text(encoding="utf-8")
    recipe = next((REPO / "art/recipe").glob("*.png"), None)

    user = call("GET", "/v2/user")
    print("account:", user["username"])  # only the name: the rest includes email and payout details
    if user["username"].lower() != meta["owner"].lower():
        sys.exit(f"this token belongs to {user['username']}, not {meta['owner']}")
    taken = urllib.request.Request(f"{API}/v2/project/{meta['slug']}", headers={"User-Agent": UA})
    try:
        urllib.request.urlopen(taken, timeout=30)
        sys.exit(f"slug {meta['slug']} is already taken")
    except urllib.error.HTTPError as e:
        if e.code != 404:
            raise
    data = {
        "slug": meta["slug"], "title": p["mod_name"], "description": p["mod_description"], "body": body,
        "categories": meta["categories"], "additional_categories": meta.get("additional_categories", []),
        "client_side": meta["client_side"], "server_side": meta["server_side"], "project_type": "mod",
        "license_id": p["mod_license"], "source_url": p["mod_sources"], "issues_url": p["mod_issues"],
        "initial_versions": [], "is_draft": True,
    }
    print(json.dumps({k: v for k, v in data.items() if k != "body"}, indent=2))
    print(f"gallery: {len(gallery)} images, recipe image: {recipe and recipe.name}")
    if not create:
        print("dry run: add --create to create the draft project")
        return

    icon = REPO / f"src/main/resources/assets/{mod_id}/icon.png"
    form, ctype = multipart({"data": json.dumps(data)}, {"icon": ("icon.png", icon.read_bytes(), "image/png")})
    project = call("POST", "/v2/project", form, {"Content-Type": ctype})
    pid = project["id"]
    print("created draft project", pid, f"https://modrinth.com/mod/{meta['slug']}")

    if recipe:
        img = call("POST", f"/v3/image?ext=png&context=project&project_id={pid}", recipe.read_bytes(), {"Content-Type": "image/png"})
        body = body.replace("{{RECIPE_IMAGE}}", img["url"])
        call("PATCH", f"/v2/project/{pid}", json.dumps({"body": body}).encode(), {"Content-Type": "application/json"})
        print("recipe image:", img["url"])

    for n, g in enumerate(gallery):
        f = REPO / "media" / g["file"]
        q = urllib.parse.urlencode({"ext": f.suffix[1:], "featured": str(bool(g.get("featured"))).lower(),
                                    "title": g["title"], "description": g["description"], "ordering": n})
        call("POST", f"/v2/project/{pid}/gallery?{q}", f.read_bytes(), {"Content-Type": "image/png"})
        print("gallery:", g["title"])

    live = call("GET", f"/v2/project/{pid}")
    assert "Lakitu" in live["title"] and "{{RECIPE_IMAGE}}" not in live["body"], "page check failed"
    print("live body length:", len(live["body"]), "gallery:", len(live["gallery"]))
    gp = REPO / "gradle.properties"
    gp.write_text(gp.read_text().replace("modrinth_project=\n", f"modrinth_project={pid}\n"), encoding="utf-8")
    print("modrinth_project set in gradle.properties")


TOKEN = token()
if __name__ == "__main__":
    main("--create" in sys.argv)
