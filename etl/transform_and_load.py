"""
ETL paso 2/2: Transforma el JSON exportado de Sheets y lo carga en PostgreSQL (Supabase).

Semántica: truncate-and-reload (no upsert) — es un corte único, no una sincronización
continua (ver plan de migración, sección "Migración de datos (ETL)").

Resuelve las filas hijas (que en Sheets se emparejan por el string "codigo_estudiante"
o "codigo_docente") contra los IDs surrogate (BIGSERIAL) generados al insertar
Estudiantes/Docentes. Las filas huérfanas (código que no existe en Estudiantes/Docentes)
se RECHAZAN explícitamente y se reportan — Sheets no tenía integridad referencial, Postgres
sí la exige.

Uso:
    cd etl
    export DATABASE_URL=postgresql://usuario:pass@host:5432/basededatos
    python transform_and_load.py            # carga real
    python transform_and_load.py --dry-run   # solo valida y reporta, no escribe nada
"""

import json
import os
import sys
import argparse

import bcrypt
import psycopg2
import psycopg2.extras

DATA_DIR = os.path.join(os.path.dirname(__file__), "data")

# Orden de truncado: hijos primero, padres al final (evita depender de CASCADE).
TABLAS_EN_ORDEN_DE_BORRADO = [
    "secciones_docente_estudiantes",
    "secciones_docente",
    "proyectos_finales",
    "avances_trabajo",
    "asistencia",
    "examenes",
    "cursos",
    "notas",
    "horarios",
    "eventos_calendario",
    "consulta_log",  # se trunca por prolijidad aunque no se cargue nada aquí (arranca vacía)
    "docentes",
    "estudiantes",
]


def cargar_json(nombre_hoja):
    ruta = os.path.join(DATA_DIR, f"{nombre_hoja}.json")
    if not os.path.exists(ruta):
        sys.exit(f"ERROR: falta {ruta}. Corre export_sheets.py primero.")
    with open(ruta, "r", encoding="utf-8") as f:
        return json.load(f)


def bcrypt_hash(texto: str) -> str:
    return bcrypt.hashpw(texto.encode("utf-8"), bcrypt.gensalt()).decode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--dry-run", action="store_true", help="Solo valida y reporta, no escribe en la base.")
    args = parser.parse_args()

    database_url = os.getenv("DATABASE_URL")
    if not database_url:
        sys.exit("ERROR: falta la variable de entorno DATABASE_URL.")

    estudiantes = cargar_json("Estudiantes")
    docentes = cargar_json("Docentes")
    horarios = cargar_json("Horarios")
    notas = cargar_json("Notas")
    cursos = cargar_json("Cursos")
    examenes = cargar_json("Examenes")
    asistencia = cargar_json("Asistencia")
    avances = cargar_json("Avances_Trabajo")
    proyectos = cargar_json("Proyectos_Finales")
    calendario = cargar_json("Calendario")
    secciones = cargar_json("Secciones_Docente")

    codigos_estudiantes_validos = {str(e["codigo"]) for e in estudiantes}
    codigos_docentes_validos = {str(d["codigo"]) for d in docentes}

    huerfanos = {"horarios": [], "notas": [], "cursos": [], "examenes": [], "asistencia": [],
                 "avances_trabajo": [], "proyectos_finales": [], "secciones_docente": [],
                 "secciones_docente_estudiantes": []}

    def es_valido_estudiante(fila, tabla):
        cod = str(fila.get("codigo_estudiante", ""))
        if cod not in codigos_estudiantes_validos:
            huerfanos[tabla].append(cod)
            return False
        return True

    horarios_validos = [f for f in horarios if es_valido_estudiante(f, "horarios")]
    notas_validas = [f for f in notas if es_valido_estudiante(f, "notas")]
    cursos_validos = [f for f in cursos if es_valido_estudiante(f, "cursos")]
    examenes_validos = [f for f in examenes if es_valido_estudiante(f, "examenes")]
    asistencia_valida = [f for f in asistencia if es_valido_estudiante(f, "asistencia")]
    avances_validos = [f for f in avances if es_valido_estudiante(f, "avances_trabajo")]
    proyectos_validos = [f for f in proyectos if es_valido_estudiante(f, "proyectos_finales")]

    secciones_validas = []
    for f in secciones:
        cod = str(f.get("codigo_docente", ""))
        if cod not in codigos_docentes_validos:
            huerfanos["secciones_docente"].append(cod)
            continue
        secciones_validas.append(f)

    print("=== Reporte de validación ===")
    print(f"Estudiantes: {len(estudiantes)}  Docentes: {len(docentes)}")
    for tabla, lista in huerfanos.items():
        if lista:
            print(f"[HUÉRFANOS] {tabla}: {len(lista)} filas rechazadas (códigos no encontrados): {sorted(set(lista))[:10]}{'...' if len(set(lista)) > 10 else ''}")

    if args.dry_run:
        print("\n--dry-run: no se escribió nada en la base de datos.")
        return

    conn = psycopg2.connect(database_url)
    conn.autocommit = False
    try:
        with conn.cursor() as cur:
            # 1. Truncar (orden hijo->padre) dentro de una sola transacción.
            for tabla in TABLAS_EN_ORDEN_DE_BORRADO:
                cur.execute(f"TRUNCATE TABLE {tabla} RESTART IDENTITY CASCADE")

            # 2. Estudiantes (password_hash = bcrypt(codigo), ver plan §Auth)
            id_por_codigo_estudiante = {}
            for e in estudiantes:
                codigo = str(e["codigo"])
                cur.execute(
                    """INSERT INTO estudiantes (codigo, nombre, carrera, ciclo, idioma_preferido, password_hash)
                       VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                    (codigo, e["nombre"], e["carrera"], str(e["ciclo"]),
                     e.get("idioma_preferido", "es") or "es", bcrypt_hash(codigo)),
                )
                id_por_codigo_estudiante[codigo] = cur.fetchone()[0]

            # 3. Docentes
            id_por_codigo_docente = {}
            for d in docentes:
                codigo = str(d["codigo"])
                cur.execute(
                    """INSERT INTO docentes (codigo, nombre, departamento, cursos_asignados, idioma_preferido, password_hash)
                       VALUES (%s, %s, %s, %s, %s, %s) RETURNING id""",
                    (codigo, d["nombre"], d["departamento"], d.get("cursos_asignados", ""),
                     d.get("idioma_preferido", "es") or "es", bcrypt_hash(codigo)),
                )
                id_por_codigo_docente[codigo] = cur.fetchone()[0]

            # 4. Horarios (columna origen "docente" -> destino "docente_nombre")
            for h in horarios_validos:
                cur.execute(
                    """INSERT INTO horarios (estudiante_id, curso, dia, hora_inicio, hora_fin, aula, docente_nombre)
                       VALUES (%s, %s, %s, %s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(h["codigo_estudiante"])], h["curso"], h["dia"],
                     h["hora_inicio"], h["hora_fin"], h["aula"], h.get("docente", "")),
                )

            # 5. Notas (columna origen "final" -> destino "final", palabra reservada en Java pero no en SQL)
            for n in notas_validas:
                cur.execute(
                    """INSERT INTO notas (estudiante_id, curso, parcial, final, promedio)
                       VALUES (%s, %s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(n["codigo_estudiante"])], n["curso"],
                     n.get("parcial") or None, n.get("final") or None, n.get("promedio") or None),
                )

            # 6. Cursos
            for c in cursos_validos:
                cur.execute(
                    """INSERT INTO cursos (estudiante_id, nombre_curso, creditos, estado)
                       VALUES (%s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(c["codigo_estudiante"])], c["nombre_curso"],
                     int(c["creditos"]), c["estado"]),
                )

            # 7. Examenes
            for ex in examenes_validos:
                cur.execute(
                    """INSERT INTO examenes (estudiante_id, curso, tipo, fecha, hora, aula)
                       VALUES (%s, %s, %s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(ex["codigo_estudiante"])], ex["curso"], ex["tipo"],
                     ex["fecha"], ex.get("hora") or None, ex.get("aula") or None),
                )

            # 8. Asistencia
            for a in asistencia_valida:
                cur.execute(
                    """INSERT INTO asistencia (estudiante_id, curso, fecha, estado)
                       VALUES (%s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(a["codigo_estudiante"])], a["curso"], a["fecha"], a["estado"]),
                )

            # 9. Avances_Trabajo
            for av in avances_validos:
                cur.execute(
                    """INSERT INTO avances_trabajo (estudiante_id, curso, entregable, fecha_entrega, estado, nota)
                       VALUES (%s, %s, %s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(av["codigo_estudiante"])], av["curso"], av["entregable"],
                     av.get("fecha_entrega") or None, av["estado"], av.get("nota") or None),
                )

            # 10. Proyectos_Finales
            for p in proyectos_validos:
                cur.execute(
                    """INSERT INTO proyectos_finales (estudiante_id, curso, titulo, grupo, fecha_sustentacion, nota)
                       VALUES (%s, %s, %s, %s, %s, %s)""",
                    (id_por_codigo_estudiante[str(p["codigo_estudiante"])], p["curso"], p["titulo"],
                     p.get("grupo") or None, p.get("fecha_sustentacion") or None, p.get("nota") or None),
                )

            # 11. Calendario -> eventos_calendario
            for ev in calendario:
                cur.execute(
                    """INSERT INTO eventos_calendario (fecha, evento, descripcion, aplica_a)
                       VALUES (%s, %s, %s, %s)""",
                    (ev["fecha"], ev["evento"], ev.get("descripcion", ""), ev["aplica_a"]),
                )

            # 12. Secciones_Docente + join secciones_docente_estudiantes
            #     (lista_estudiantes es un string JSON tipo '["E001","E002"]' en Sheets)
            for s in secciones_validas:
                cur.execute(
                    """INSERT INTO secciones_docente (docente_id, curso, seccion, horario)
                       VALUES (%s, %s, %s, %s) RETURNING id""",
                    (id_por_codigo_docente[str(s["codigo_docente"])], s["curso"], s["seccion"],
                     s.get("horario", "")),
                )
                seccion_id = cur.fetchone()[0]

                lista_raw = s.get("lista_estudiantes", "[]")
                try:
                    lista_codigos = json.loads(lista_raw) if isinstance(lista_raw, str) else (lista_raw or [])
                except json.JSONDecodeError:
                    lista_codigos = []
                    print(f"[AVISO] lista_estudiantes ilegible en sección {s['curso']}/{s['seccion']}: {lista_raw!r}")

                for codigo_est in lista_codigos:
                    codigo_est = str(codigo_est)
                    est_id = id_por_codigo_estudiante.get(codigo_est)
                    if est_id is None:
                        huerfanos["secciones_docente_estudiantes"].append(codigo_est)
                        continue
                    cur.execute(
                        """INSERT INTO secciones_docente_estudiantes (seccion_id, estudiante_id)
                           VALUES (%s, %s) ON CONFLICT DO NOTHING""",
                        (seccion_id, est_id),
                    )

        conn.commit()
        print("\n[OK] Carga completada y confirmada (commit).")

    except Exception:
        conn.rollback()
        print("\n[ERROR] Falló la carga — se hizo rollback completo. Ningún dato quedó a medias.")
        raise
    finally:
        conn.close()

    # === Verificación post-carga ===
    print("\n=== Verificación ===")
    conteos_esperados = {
        "estudiantes": len(estudiantes), "docentes": len(docentes),
        "horarios": len(horarios_validos), "notas": len(notas_validas),
        "cursos": len(cursos_validos), "examenes": len(examenes_validos),
        "asistencia": len(asistencia_valida), "avances_trabajo": len(avances_validos),
        "proyectos_finales": len(proyectos_validos), "eventos_calendario": len(calendario),
        "secciones_docente": len(secciones_validas),
    }
    conn = psycopg2.connect(database_url)
    with conn.cursor() as cur:
        for tabla, esperado in conteos_esperados.items():
            cur.execute(f"SELECT COUNT(*) FROM {tabla}")
            real = cur.fetchone()[0]
            marca = "OK" if real == esperado else "¡DISCREPANCIA!"
            print(f"  {tabla:<22} esperado={esperado:<6} real={real:<6} [{marca}]")
    conn.close()

    total_huerfanos = sum(len(v) for v in huerfanos.values())
    if total_huerfanos:
        print(f"\n[RESUMEN] {total_huerfanos} filas huérfanas rechazadas en total (ver detalle arriba).")
    print("\nCarga y verificación completas.")


if __name__ == "__main__":
    main()
