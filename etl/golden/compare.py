"""
Compara dos carpetas de fixtures capturadas con capture.py (una del backend Python,
otra del backend Quarkus) y reporta diffs campo a campo.

Uso:
    python compare.py fixtures/python fixtures/quarkus

Reglas de comparacion:
- "status" debe coincidir siempre.
- En el body, el campo "token" se ignora (cambia en cada login por diseno).
- "version" en /health y / se ignora (bump intencional documentado en
  PARITY_REPORT.md -- ver backend-quarkus/PARITY_REPORT.md).
- "webhook_url" en telegram_status se ignora si difiere solo por la URL base del
  entorno (localhost:8000 vs localhost:8081, por ejemplo).
- Todo lo demas debe coincidir exactamente o se reporta como discrepancia.
"""

import json
import os
import sys

IGNORAR_CLAVES_GLOBAL = {"token"}
IGNORAR_ARCHIVO_CLAVE = {
    "health": {"version"},
    "root": {"version", "descripcion", "documentacion"},
}


def cargar(directorio, nombre):
    ruta = os.path.join(directorio, nombre + ".json")
    if not os.path.exists(ruta):
        return None
    with open(ruta, "r", encoding="utf-8") as f:
        return json.load(f)


def limpiar(obj, ignorar_claves):
    if isinstance(obj, dict):
        return {k: limpiar(v, ignorar_claves) for k, v in obj.items() if k not in ignorar_claves}
    if isinstance(obj, list):
        return [limpiar(x, ignorar_claves) for x in obj]
    return obj


def comparar_archivo(nombre, dir_a, dir_b):
    a = cargar(dir_a, nombre)
    b = cargar(dir_b, nombre)

    if a is None and b is None:
        return None
    if a is None:
        return "FALTA en " + dir_a
    if b is None:
        return "FALTA en " + dir_b

    ignorar = IGNORAR_CLAVES_GLOBAL | IGNORAR_ARCHIVO_CLAVE.get(nombre, set())

    if a.get("status") != b.get("status"):
        return "STATUS distinto: {} vs {}".format(a.get("status"), b.get("status"))

    body_a = limpiar(a.get("body"), ignorar)
    body_b = limpiar(b.get("body"), ignorar)

    if body_a != body_b:
        return "BODY distinto:\n  A={}\n  B={}".format(
            json.dumps(body_a, ensure_ascii=False)[:300],
            json.dumps(body_b, ensure_ascii=False)[:300])

    return None


def main():
    if len(sys.argv) != 3:
        print("Uso: python compare.py <dir_python> <dir_quarkus>")
        sys.exit(1)

    dir_a, dir_b = sys.argv[1], sys.argv[2]
    nombres = set()
    for d in (dir_a, dir_b):
        if os.path.isdir(d):
            nombres.update(f[:-5] for f in os.listdir(d) if f.endswith(".json"))

    if not nombres:
        print("No se encontraron fixtures en ninguno de los dos directorios.")
        sys.exit(1)

    fallos = 0
    for nombre in sorted(nombres):
        resultado = comparar_archivo(nombre, dir_a, dir_b)
        if resultado is None:
            print("[OK]   " + nombre)
        else:
            fallos += 1
            print("[DIFF] " + nombre + ": " + resultado)

    print("")
    if fallos == 0:
        print("Todas las fixtures coinciden ({} casos).".format(len(nombres)))
    else:
        print("{} de {} casos tienen diferencias -- revisar arriba.".format(fallos, len(nombres)))
        sys.exit(1)


if __name__ == "__main__":
    main()
