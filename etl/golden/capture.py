"""
Captura fixtures "golden" de un backend (Python original o Quarkus nuevo) corriendo
el mismo conjunto fijo de casos de prueba y guardando cada respuesta como JSON.

Disenado para correr DOS VECES: una vez con --backend python contra el backend
original, otra con --backend quarkus contra el nuevo -- y despues comparar los
resultados con compare.py. Ver el proceso completo documentado en
PARITY_REPORT.md (backend-quarkus/) y en el plan de migracion, seccion
"Estrategia de corte (rollout)".
"""

import argparse
import json
import os
import sys

import requests


def login_python(base_url, codigo, password):
    resp = requests.post(base_url + "/auth/login", json={"codigo": codigo, "password": password})
    resp.raise_for_status()
    return resp.json()["token"]


def login_quarkus(base_url, firebase_emulator_url, codigo, password):
    resp = requests.post(base_url + "/auth/login", json={"codigo": codigo, "password": password})
    resp.raise_for_status()
    custom_token = resp.json()["token"]

    exchange = requests.post(
        firebase_emulator_url + "/identitytoolkit.googleapis.com/v1/accounts:signInWithCustomToken",
        params={"key": "fake-api-key"},
        json={"token": custom_token, "returnSecureToken": True},
    )
    exchange.raise_for_status()
    return exchange.json()["idToken"]


def capturar(base_url, path, method="GET", headers=None, json_body=None):
    resp = requests.request(method, base_url + path, headers=headers or {}, json=json_body)
    try:
        body = resp.json()
    except ValueError:
        body = resp.text
    return {"status": resp.status_code, "body": body}


def guardar(out_dir, nombre, resultado):
    os.makedirs(out_dir, exist_ok=True)
    ruta = os.path.join(out_dir, nombre + ".json")
    with open(ruta, "w", encoding="utf-8") as f:
        json.dump(resultado, f, ensure_ascii=False, indent=2)
    print("[OK] " + nombre + ": " + str(resultado["status"]) + " -> " + ruta)


def main():
    p = argparse.ArgumentParser()
    p.add_argument("--backend", choices=["python", "quarkus"], required=True)
    p.add_argument("--base-url", required=True)
    p.add_argument("--firebase-emulator-url", default="http://localhost:9099")
    p.add_argument("--admin-codigo", default="admin")
    p.add_argument("--admin-password", required=True)
    p.add_argument("--estudiante-codigo", default="E001")
    p.add_argument("--estudiante-password", default="E001")
    p.add_argument("--docente-codigo", default="D001")
    p.add_argument("--docente-password", default="D001")
    p.add_argument("--out", required=True)
    args = p.parse_args()

    def login(codigo, password):
        if args.backend == "python":
            return login_python(args.base_url, codigo, password)
        return login_quarkus(args.base_url, args.firebase_emulator_url, codigo, password)

    guardar(args.out, "health", capturar(args.base_url, "/health"))
    guardar(args.out, "root", capturar(args.base_url, "/"))
    guardar(args.out, "auth_login_password_incorrecta", capturar(
        args.base_url, "/auth/login", "POST",
        json_body={"codigo": args.estudiante_codigo, "password": "password_a_proposito_incorrecta"}))
    guardar(args.out, "auth_login_codigo_desconocido", capturar(
        args.base_url, "/auth/login", "POST",
        json_body={"codigo": "CODIGO_QUE_NO_EXISTE_999", "password": "x"}))

    for rol, codigo, password in [
        ("estudiante", args.estudiante_codigo, args.estudiante_password),
        ("docente", args.docente_codigo, args.docente_password),
        ("admin", args.admin_codigo, args.admin_password),
    ]:
        resp = requests.post(args.base_url + "/auth/login", json={"codigo": codigo, "password": password})
        body = resp.json() if resp.ok else resp.text
        if resp.ok and isinstance(body, dict) and "token" in body:
            body = dict(body)
            body["token"] = "<omitido a proposito, cambia en cada request>"
        guardar(args.out, "auth_login_" + rol, {"status": resp.status_code, "body": body})

    try:
        admin_token = login(args.admin_codigo, args.admin_password)
        docente_token = login(args.docente_codigo, args.docente_password)
    except Exception as e:
        print("[ERROR] No se pudo obtener token para casos autenticados: " + str(e), file=sys.stderr)
        print("Los casos que requieren auth se omiten. Los de arriba ya se guardaron.")
        return

    admin_headers = {"Authorization": "Bearer " + admin_token}
    docente_headers = {"Authorization": "Bearer " + docente_token}

    for nombre, path in [
        ("admin_dashboard", "/admin/dashboard"),
        ("admin_stats_overview", "/admin/stats/overview"),
        ("admin_stats_by_day", "/admin/stats/by-day?dias=30"),
        ("admin_stats_by_category", "/admin/stats/by-category"),
        ("admin_stats_by_role", "/admin/stats/by-role"),
        ("admin_recent_logs", "/admin/recent-logs?limite=20"),
        ("admin_faq_analytics", "/admin/faq-analytics"),
        ("telegram_status", "/telegram/status"),
    ]:
        guardar(args.out, nombre, capturar(args.base_url, path, headers=admin_headers))

    guardar(args.out, "docente_resumen", capturar(
        args.base_url, "/docente/resumen/" + args.docente_codigo, headers=docente_headers))
    guardar(args.out, "docente_seccion", capturar(
        args.base_url, "/docente/seccion/" + args.docente_codigo, headers=docente_headers))
    guardar(args.out, "docente_resumen_otro_codigo_403", capturar(
        args.base_url, "/docente/resumen/CODIGO_QUE_NO_ES_EL_PROPIO", headers=docente_headers))

    print("\nCaptura completa. Los casos de /chat, /transcribe y agendado NO se "
          "incluyen aqui -- son no-deterministicos (dependen de Gemini) y se evaluan "
          "cualitativamente, no por diff exacto. Ver PARITY_REPORT.md.")


if __name__ == "__main__":
    main()
