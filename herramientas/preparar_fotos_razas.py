"""
Prepara las fotos de referencia de cada raza para la app.

1. Guarda en la carpeta  fotos-razas/  una foto por raza, con el id de la raza como nombre
   (por ejemplo  holstein.jpg,  brahman_rojo.png). La lista de ids está en fotos-razas/LEEME.md.
2. Anota el autor y la licencia de cada foto en  fotos-razas/creditos.csv  (id,autor,licencia,fuente).
3. Ejecuta:   python herramientas/preparar_fotos_razas.py

El script endereza la foto según su EXIF, la reduce a 1024 px, la guarda como JPEG liviano
en android/app/src/main/assets/razas/ y genera creditos.json para mostrarlos en la app.
Requiere Pillow:  pip install pillow
"""
import csv
import json
import sys
from pathlib import Path

try:
    from PIL import Image, ImageOps
except ImportError:
    sys.exit("Falta Pillow. Instálalo con:  pip install pillow")

RAIZ = Path(__file__).resolve().parent.parent
ENTRADA = RAIZ / "fotos-razas"
SALIDA = RAIZ / "android" / "app" / "src" / "main" / "assets" / "razas"
CATALOGO = RAIZ / "backend" / "data" / "razas.json"
LADO_MAXIMO = 1024
EXTENSIONES = {".jpg", ".jpeg", ".png", ".webp"}


def main() -> None:
    razas = {r["id"]: r["nombre"] for r in json.loads(CATALOGO.read_text(encoding="utf-8"))}
    SALIDA.mkdir(parents=True, exist_ok=True)

    procesadas, desconocidas = [], []
    for archivo in sorted(ENTRADA.iterdir()):
        if archivo.suffix.lower() not in EXTENSIONES:
            continue
        rid = archivo.stem.lower()
        if rid not in razas:
            desconocidas.append(archivo.name)
            continue
        with Image.open(archivo) as img:
            img = ImageOps.exif_transpose(img).convert("RGB")
            img.thumbnail((LADO_MAXIMO, LADO_MAXIMO))
            destino = SALIDA / f"{rid}.jpg"
            img.save(destino, "JPEG", quality=82, optimize=True, progressive=True)
        procesadas.append(rid)
        print(f"ok  {rid:<16} {destino.stat().st_size // 1024} KB")

    creditos = {}
    ruta_csv = ENTRADA / "creditos.csv"
    if ruta_csv.exists():
        with ruta_csv.open(encoding="utf-8") as f:
            for fila in csv.DictReader(f):
                rid = (fila.get("id") or "").strip().lower()
                if rid in razas:
                    partes = [f"Foto: {fila.get('autor', '').strip()}", fila.get("licencia", "").strip(), fila.get("fuente", "").strip()]
                    creditos[rid] = " · ".join(p for p in partes if p and p != "Foto: ")
    (SALIDA / "creditos.json").write_text(json.dumps(creditos, ensure_ascii=False, indent=2), encoding="utf-8")

    faltan = [f"{rid} ({nombre})" for rid, nombre in razas.items() if rid not in procesadas and not (SALIDA / f"{rid}.jpg").exists()]
    sin_credito = [rid for rid in procesadas if rid not in creditos]
    print(f"\nProcesadas: {len(procesadas)}")
    if desconocidas:
        print("Nombres que no coinciden con ninguna raza:", ", ".join(desconocidas))
    if sin_credito:
        print("Sin crédito en creditos.csv:", ", ".join(sin_credito))
    if faltan:
        print(f"Razas aún sin foto ({len(faltan)}):", ", ".join(faltan))


if __name__ == "__main__":
    main()
