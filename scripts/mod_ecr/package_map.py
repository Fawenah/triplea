"""Package the standalone map and verify every ZIP entry's CRC."""

from pathlib import Path
import zipfile

ROOT = Path(__file__).resolve().parents[2]
MAP = ROOT / "custom_maps/global_1940_mod_ecr"
if not (MAP / "unit-spec.json").exists() and (MAP / "global_1940_mod_ecr/unit-spec.json").exists():
    MAP = MAP / "global_1940_mod_ecr"
OUTPUT = ROOT / "build/distributions/global_1940_mod_ecr-0.1.0.zip"


def main():
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(OUTPUT, "w", compression=zipfile.ZIP_DEFLATED) as archive:
        for path in sorted(MAP.rglob("*")):
            if path.is_file() and path.name != ".gitignore":
                archive.write(path, path.relative_to(MAP.parent).as_posix())
    with zipfile.ZipFile(OUTPUT) as archive:
        damaged = archive.testzip()
        if damaged:
            raise SystemExit(f"ZIP verification failed: {damaged}")
        print(f"Packaged and verified {len(archive.infolist())} files: {OUTPUT}")


if __name__ == "__main__":
    main()
