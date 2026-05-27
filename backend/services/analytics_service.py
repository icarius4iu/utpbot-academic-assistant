"""
Servicio de Analytics para el sistema UTPBot.
Procesa y agrupa las preguntas del FAQ_Log para el panel de administración.
Incluye métricas por día, categoría y rol de usuario.
"""

from collections import Counter, defaultdict
from typing import List, Dict
from datetime import datetime, timezone, timedelta
from services.sheets_service import sheets_service


class AnalyticsService:
    """
    Servicio para generar estadísticas y analytics del chatbot.
    """

    def obtener_faq_analytics(self) -> Dict:
        """
        Obtiene las preguntas más frecuentes agrupadas por categoría.

        Returns:
            Diccionario con total de consultas y categorías agrupadas
        """
        registros = sheets_service.obtener_faq_log()

        if not registros:
            return {"total_consultas": 0, "categorias": []}

        categoria_counter = Counter()
        preguntas_por_categoria = {}

        for registro in registros:
            categoria = registro.get("categoria", "general")
            pregunta = registro.get("pregunta", "")
            categoria_counter[categoria] += 1

            if categoria not in preguntas_por_categoria:
                preguntas_por_categoria[categoria] = []
            if len(preguntas_por_categoria[categoria]) < 5:
                preguntas_por_categoria[categoria].append(pregunta)

        categorias = []
        for categoria, cantidad in categoria_counter.most_common():
            categorias.append({
                "categoria": categoria,
                "cantidad": cantidad,
                "preguntas_ejemplo": preguntas_por_categoria.get(categoria, [])
            })

        return {
            "total_consultas": sum(categoria_counter.values()),
            "categorias": categorias
        }

    def obtener_preguntas_recientes(self, limite: int = 20) -> List[Dict]:
        """
        Obtiene las preguntas más recientes del FAQ_Log.

        Args:
            limite: Número máximo de registros a retornar

        Returns:
            Lista de las preguntas más recientes
        """
        registros = sheets_service.obtener_faq_log()
        recientes = registros[-limite:] if len(registros) > limite else registros
        return list(reversed(recientes))  # Las más recientes primero

    def obtener_overview(self) -> Dict:
        """
        Calcula estadísticas generales del sistema para el dashboard.

        Returns:
            Diccionario con métricas principales
        """
        registros = sheets_service.obtener_faq_log()
        hoy = datetime.now(timezone.utc).strftime("%Y-%m-%d")

        total = len(registros)
        consultas_hoy = 0
        usuarios_unicos = set()
        categoria_counter = Counter()
        rol_counter = Counter()

        for r in registros:
            fecha = r.get("fecha", "")
            if fecha.startswith(hoy):
                consultas_hoy += 1

            codigo = r.get("codigo_usuario", "")
            if codigo:
                usuarios_unicos.add(codigo)

            categoria = r.get("categoria", "general")
            categoria_counter[categoria] += 1

            rol = r.get("rol", "desconocido")
            rol_counter[rol] += 1

        categoria_top = categoria_counter.most_common(1)[0][0] if categoria_counter else "—"

        total_roles = total or 1
        return {
            "total_consultas": total,
            "consultas_hoy": consultas_hoy,
            "usuarios_activos": len(usuarios_unicos),
            "categoria_top": categoria_top,
            "porcentaje_estudiantes": round(rol_counter.get("estudiante", 0) / total_roles * 100, 1),
            "porcentaje_docentes": round(rol_counter.get("docente", 0) / total_roles * 100, 1),
        }

    def obtener_stats_por_dia(self, dias: int = 30) -> List[Dict]:
        """
        Calcula el número de consultas por día para los últimos N días.

        Args:
            dias: Número de días a incluir (por defecto 30)

        Returns:
            Lista de {fecha, cantidad} ordenada cronológicamente
        """
        registros = sheets_service.obtener_faq_log()
        ahora = datetime.now(timezone.utc)

        # Inicializar todos los días con cero
        dias_mapa = {}
        for i in range(dias - 1, -1, -1):
            fecha = (ahora - timedelta(days=i)).strftime("%Y-%m-%d")
            dias_mapa[fecha] = 0

        for r in registros:
            fecha_raw = r.get("fecha", "")
            if fecha_raw:
                try:
                    fecha = fecha_raw[:10]  # Solo YYYY-MM-DD
                    if fecha in dias_mapa:
                        dias_mapa[fecha] += 1
                except Exception:
                    pass

        return [{"fecha": f, "cantidad": c} for f, c in dias_mapa.items()]

    def obtener_stats_por_categoria(self) -> List[Dict]:
        """
        Calcula distribución de consultas por categoría.

        Returns:
            Lista de {categoria, cantidad, porcentaje}
        """
        registros = sheets_service.obtener_faq_log()
        total = len(registros) or 1
        counter = Counter(r.get("categoria", "general") for r in registros)

        return [
            {
                "categoria": cat,
                "cantidad": cnt,
                "porcentaje": round(cnt / total * 100, 1)
            }
            for cat, cnt in counter.most_common()
        ]

    def obtener_stats_por_rol(self) -> List[Dict]:
        """
        Calcula distribución de consultas por rol de usuario.

        Returns:
            Lista de {rol, cantidad}
        """
        registros = sheets_service.obtener_faq_log()
        counter = Counter(r.get("rol", "desconocido") for r in registros)

        return [
            {"rol": rol, "cantidad": cnt}
            for rol, cnt in counter.most_common()
        ]

    def obtener_dashboard_completo(self) -> Dict:
        """
        Retorna todas las métricas del dashboard en una sola llamada.
        Optimiza el número de lecturas a Google Sheets cargando los registros una vez.

        Returns:
            Diccionario completo con todas las métricas
        """
        registros = sheets_service.obtener_faq_log()
        ahora = datetime.now(timezone.utc)
        hoy = ahora.strftime("%Y-%m-%d")
        dias = 30

        # ── Overview ────────────────────────────────────────────────
        total = len(registros)
        consultas_hoy = 0
        usuarios_unicos = set()
        categoria_counter = Counter()
        rol_counter = Counter()

        for r in registros:
            fecha = r.get("fecha", "")
            if fecha.startswith(hoy):
                consultas_hoy += 1
            codigo = r.get("codigo_usuario", "")
            if codigo:
                usuarios_unicos.add(codigo)
            categoria_counter[r.get("categoria", "general")] += 1
            rol_counter[r.get("rol", "desconocido")] += 1

        categoria_top = categoria_counter.most_common(1)[0][0] if categoria_counter else "—"
        total_safe = total or 1

        overview = {
            "total_consultas": total,
            "consultas_hoy": consultas_hoy,
            "usuarios_activos": len(usuarios_unicos),
            "categoria_top": categoria_top,
            "porcentaje_estudiantes": round(rol_counter.get("estudiante", 0) / total_safe * 100, 1),
            "porcentaje_docentes": round(rol_counter.get("docente", 0) / total_safe * 100, 1),
        }

        # ── Por día (últimos 30 días) ────────────────────────────────
        dias_mapa = {}
        for i in range(dias - 1, -1, -1):
            f = (ahora - timedelta(days=i)).strftime("%Y-%m-%d")
            dias_mapa[f] = 0

        for r in registros:
            fecha_raw = r.get("fecha", "")
            if fecha_raw:
                fecha_key = fecha_raw[:10]
                if fecha_key in dias_mapa:
                    dias_mapa[fecha_key] += 1

        por_dia = [{"fecha": f, "cantidad": c} for f, c in dias_mapa.items()]

        # ── Por categoría ────────────────────────────────────────────
        por_categoria = [
            {"categoria": cat, "cantidad": cnt, "porcentaje": round(cnt / total_safe * 100, 1)}
            for cat, cnt in categoria_counter.most_common()
        ]

        # ── Por rol ──────────────────────────────────────────────────
        por_rol = [{"rol": r, "cantidad": c} for r, c in rol_counter.most_common()]

        # ── Recientes (últimos 20) ───────────────────────────────────
        recientes_raw = list(reversed(registros[-20:])) if len(registros) > 20 else list(reversed(registros))
        recientes = [
            {
                "fecha": r.get("fecha", ""),
                "codigo_usuario": r.get("codigo_usuario", ""),
                "rol": r.get("rol", ""),
                "pregunta": r.get("pregunta", "")[:120],  # Truncar para la tabla
                "categoria": r.get("categoria", "general"),
            }
            for r in recientes_raw
        ]

        return {
            "overview": overview,
            "por_dia": por_dia,
            "por_categoria": por_categoria,
            "por_rol": por_rol,
            "recientes": recientes,
        }


# Instancia global del servicio (singleton)
analytics_service = AnalyticsService()
