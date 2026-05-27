"""
Servicio de Google Sheets para el sistema UTPBot.
Maneja toda la conexión y operaciones CRUD con Google Spreadsheets.
Diseñado con una interfaz abstracta para facilitar migración futura a PostgreSQL.
"""

import os
import json
import gspread
from google.oauth2.service_account import Credentials
from dotenv import load_dotenv
from typing import List, Dict, Optional

load_dotenv()

# Alcances necesarios para Google Sheets API
SCOPES = [
    "https://www.googleapis.com/auth/spreadsheets",
    "https://www.googleapis.com/auth/drive"
]

# =====================================================================
# DATOS DEMO — usados como fallback cuando Google Sheets no está
# configurado o no se puede conectar.
# =====================================================================
DEMO_DATOS_ESTUDIANTE = {
    "E001": {
        "info_personal": {"codigo": "E001", "nombre": "Ana García López", "carrera": "Ingeniería de Sistemas", "ciclo": "5", "idioma_preferido": "es"},
        "carrera": "Ingeniería de Sistemas",
        "ciclo": "5",
        "idioma_preferido": "es",
        "horarios": [
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "dia": "Lunes", "hora_inicio": "08:00", "hora_fin": "10:00", "aula": "A-301", "docente": "Dr. Roberto Flores"},
            {"codigo_estudiante": "E001", "curso": "Base de Datos", "dia": "Miércoles", "hora_inicio": "10:00", "hora_fin": "12:00", "aula": "B-205", "docente": "Mg. Luis Paredes"},
            {"codigo_estudiante": "E001", "curso": "Inglés Técnico", "dia": "Viernes", "hora_inicio": "14:00", "hora_fin": "16:00", "aula": "C-102", "docente": "Lic. María Quispe"},
        ],
        "notas": [
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "parcial": 15, "final": 17, "promedio": 16},
            {"codigo_estudiante": "E001", "curso": "Base de Datos", "parcial": 13, "final": 14, "promedio": 13.5},
            {"codigo_estudiante": "E001", "curso": "Inglés Técnico", "parcial": 18, "final": 19, "promedio": 18.5},
        ],
        "cursos": [
            {"codigo_estudiante": "E001", "nombre_curso": "Algoritmos", "creditos": 4, "estado": "en curso"},
            {"codigo_estudiante": "E001", "nombre_curso": "Base de Datos", "creditos": 3, "estado": "en curso"},
            {"codigo_estudiante": "E001", "nombre_curso": "Inglés Técnico", "creditos": 2, "estado": "en curso"},
        ],
        "examenes": [
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "tipo": "parcial", "fecha": "2025-04-15", "hora": "08:00", "aula": "A-301"},
            {"codigo_estudiante": "E001", "curso": "Base de Datos", "tipo": "parcial", "fecha": "2025-04-16", "hora": "10:00", "aula": "B-205"},
        ],
        "asistencia": [
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "fecha": "2025-03-03", "estado": "presente"},
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "fecha": "2025-03-10", "estado": "tardanza"},
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "fecha": "2025-03-17", "estado": "presente"},
            {"codigo_estudiante": "E001", "curso": "Base de Datos", "fecha": "2025-03-04", "estado": "presente"},
            {"codigo_estudiante": "E001", "curso": "Base de Datos", "fecha": "2025-03-11", "estado": "ausente"},
        ],
        "avances_trabajo": [
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "entregable": "Práctica 1", "fecha_entrega": "2025-03-20", "estado": "entregado", "nota": 16},
            {"codigo_estudiante": "E001", "curso": "Base de Datos", "entregable": "Proyecto BD", "fecha_entrega": "2025-04-10", "estado": "pendiente", "nota": ""},
        ],
        "proyectos_finales": [
            {"codigo_estudiante": "E001", "curso": "Algoritmos", "titulo": "Sistema de Gestión de Inventarios", "grupo": "G3", "fecha_sustentacion": "2025-07-05", "nota": ""},
        ],
        "calendario": [
            {"fecha": "2025-04-15", "evento": "Exámenes Parciales", "descripcion": "Semana de exámenes parciales", "aplica_a": "todos"},
            {"fecha": "2025-05-01", "evento": "Día del Trabajo", "descripcion": "Feriado nacional", "aplica_a": "todos"},
            {"fecha": "2025-06-30", "evento": "Fin de Ciclo", "descripcion": "Último día de clases", "aplica_a": "estudiantes"},
        ],
    },
    "E002": {
        "info_personal": {"codigo": "E002", "nombre": "Carlos Mendoza Ríos", "carrera": "Administración de Empresas", "ciclo": "3", "idioma_preferido": "es"},
        "carrera": "Administración de Empresas", "ciclo": "3", "idioma_preferido": "es",
        "horarios": [
            {"codigo_estudiante": "E002", "curso": "Contabilidad", "dia": "Martes", "hora_inicio": "09:00", "hora_fin": "11:00", "aula": "D-101", "docente": "Mg. Pedro Ruiz"},
            {"codigo_estudiante": "E002", "curso": "Marketing", "dia": "Jueves", "hora_inicio": "13:00", "hora_fin": "15:00", "aula": "D-205", "docente": "Lic. Sandra Mora"},
        ],
        "notas": [
            {"codigo_estudiante": "E002", "curso": "Contabilidad", "parcial": 12, "final": 13, "promedio": 12.5},
            {"codigo_estudiante": "E002", "curso": "Marketing", "parcial": 16, "final": 17, "promedio": 16.5},
        ],
        "cursos": [
            {"codigo_estudiante": "E002", "nombre_curso": "Contabilidad", "creditos": 3, "estado": "en curso"},
            {"codigo_estudiante": "E002", "nombre_curso": "Marketing", "creditos": 3, "estado": "en curso"},
        ],
        "examenes": [
            {"codigo_estudiante": "E002", "curso": "Contabilidad", "tipo": "parcial", "fecha": "2025-04-15", "hora": "09:00", "aula": "D-101"},
        ],
        "asistencia": [
            {"codigo_estudiante": "E002", "curso": "Contabilidad", "fecha": "2025-03-04", "estado": "presente"},
            {"codigo_estudiante": "E002", "curso": "Marketing", "fecha": "2025-03-06", "estado": "ausente"},
        ],
        "avances_trabajo": [],
        "proyectos_finales": [],
        "calendario": [
            {"fecha": "2025-04-15", "evento": "Exámenes Parciales", "descripcion": "Semana de exámenes parciales", "aplica_a": "todos"},
        ],
    },
}

DEMO_DATOS_DOCENTE = {
    "D001": {
        "info_personal": {"codigo": "D001", "nombre": "Dr. Roberto Flores", "departamento": "Ingeniería", "cursos_asignados": "Algoritmos, Estructura de Datos"},
        "departamento": "Ingeniería",
        "cursos_asignados": "Algoritmos, Estructura de Datos",
        "idioma_preferido": "es",
        "secciones": [
            {"codigo_docente": "D001", "curso": "Algoritmos", "seccion": "A", "lista_estudiantes": '["E001","E002"]', "horario": "Lunes 08:00-10:00 Aula A-301"},
        ],
        "datos_estudiantes": [
            {
                "curso": "Algoritmos", "seccion": "A", "horario": "Lunes 08:00-10:00",
                "estudiantes": [
                    {"codigo": "E001", "nombre": "Ana García López",
                     "notas": [{"curso": "Algoritmos", "parcial": 15, "final": 17, "promedio": 16}],
                     "asistencia": [{"fecha": "2025-03-03", "estado": "presente"}, {"fecha": "2025-03-10", "estado": "tardanza"}]},
                    {"codigo": "E002", "nombre": "Carlos Mendoza Ríos",
                     "notas": [{"curso": "Algoritmos", "parcial": 12, "final": 0, "promedio": 0}],
                     "asistencia": [{"fecha": "2025-03-03", "estado": "ausente"}, {"fecha": "2025-03-10", "estado": "presente"}]},
                ]
            }
        ],
        "calendario": [
            {"fecha": "2025-04-15", "evento": "Entrega de notas parciales", "descripcion": "Fecha límite registro de notas", "aplica_a": "docentes"},
            {"fecha": "2025-06-30", "evento": "Cierre de ciclo", "descripcion": "Fecha límite notas finales", "aplica_a": "docentes"},
        ],
    },
    "D002": {
        "info_personal": {"codigo": "D002", "nombre": "Mg. Carmen Salinas", "departamento": "Ciencias Básicas", "cursos_asignados": "Cálculo I, Estadística"},
        "departamento": "Ciencias Básicas",
        "cursos_asignados": "Cálculo I, Estadística",
        "idioma_preferido": "es",
        "secciones": [],
        "datos_estudiantes": [],
        "calendario": [],
    },
}


class SheetsService:
    """
    Servicio para interactuar con Google Sheets.
    Encapsula todas las operaciones de lectura/escritura al spreadsheet.
    """

    def __init__(self):
        """Inicializa la conexión con Google Sheets usando credenciales de servicio."""
        self._client = None
        self._spreadsheet = None

    def _conectar(self):
        """Establece conexión con Google Sheets si no está conectado."""
        if self._client is None:
            creds_file = os.getenv("GOOGLE_CREDENTIALS_FILE", "credentials.json")
            spreadsheet_id = os.getenv("SPREADSHEET_ID", "")

            if not os.path.exists(creds_file):
                raise FileNotFoundError(
                    f"No se encontró el archivo de credenciales: {creds_file}. "
                    "Descarga el archivo JSON de tu cuenta de servicio de Google Cloud."
                )

            credentials = Credentials.from_service_account_file(creds_file, scopes=SCOPES)
            self._client = gspread.authorize(credentials)

            if spreadsheet_id:
                self._spreadsheet = self._client.open_by_key(spreadsheet_id)
            else:
                raise ValueError(
                    "SPREADSHEET_ID no está configurado en el archivo .env"
                )

    def _obtener_hoja(self, nombre_hoja: str) -> gspread.Worksheet:
        """Obtiene una hoja específica del spreadsheet."""
        self._conectar()
        try:
            return self._spreadsheet.worksheet(nombre_hoja)
        except gspread.exceptions.WorksheetNotFound:
            raise ValueError(f"La hoja '{nombre_hoja}' no existe en el spreadsheet.")

    def _obtener_todos_registros(self, nombre_hoja: str) -> List[Dict]:
        """Obtiene todos los registros de una hoja como lista de diccionarios."""
        hoja = self._obtener_hoja(nombre_hoja)
        return hoja.get_all_records()

    # ===================== AUTENTICACIÓN =====================

    def buscar_estudiante(self, codigo: str) -> Optional[Dict]:
        """
        Busca un estudiante por su código institucional.
        
        Args:
            codigo: Código del estudiante
            
        Returns:
            Diccionario con datos del estudiante o None si no existe
        """
        registros = self._obtener_todos_registros("Estudiantes")
        for registro in registros:
            if str(registro.get("codigo", "")) == str(codigo):
                return registro
        return None

    def buscar_docente(self, codigo: str) -> Optional[Dict]:
        """
        Busca un docente por su código institucional.
        
        Args:
            codigo: Código del docente
            
        Returns:
            Diccionario con datos del docente o None si no existe
        """
        registros = self._obtener_todos_registros("Docentes")
        for registro in registros:
            if str(registro.get("codigo", "")) == str(codigo):
                return registro
        return None

    # ===================== DATOS ACADÉMICOS DEL ESTUDIANTE =====================

    def obtener_horarios(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene los horarios de un estudiante."""
        registros = self._obtener_todos_registros("Horarios")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_notas(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene las notas de un estudiante."""
        registros = self._obtener_todos_registros("Notas")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_cursos(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene los cursos matriculados de un estudiante."""
        registros = self._obtener_todos_registros("Cursos")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_examenes(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene los exámenes programados de un estudiante."""
        registros = self._obtener_todos_registros("Examenes")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_asistencia(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene el registro de asistencia de un estudiante."""
        registros = self._obtener_todos_registros("Asistencia")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_avances(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene los avances de trabajos de un estudiante."""
        registros = self._obtener_todos_registros("Avances_Trabajo")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_proyectos(self, codigo_estudiante: str) -> List[Dict]:
        """Obtiene los proyectos finales de un estudiante."""
        registros = self._obtener_todos_registros("Proyectos_Finales")
        return [r for r in registros if str(r.get("codigo_estudiante", "")) == str(codigo_estudiante)]

    def obtener_calendario(self, aplica_a: str = "todos") -> List[Dict]:
        """
        Obtiene eventos del calendario académico.
        
        Args:
            aplica_a: Filtro ('todos', 'estudiantes', 'docentes')
        """
        registros = self._obtener_todos_registros("Calendario")
        return [
            r for r in registros
            if r.get("aplica_a", "todos") in ["todos", aplica_a]
        ]

    # ===================== DATOS DEL DOCENTE =====================

    def obtener_secciones_docente(self, codigo_docente: str) -> List[Dict]:
        """Obtiene las secciones asignadas a un docente."""
        registros = self._obtener_todos_registros("Secciones_Docente")
        return [r for r in registros if str(r.get("codigo_docente", "")) == str(codigo_docente)]

    def obtener_datos_estudiantes_seccion(self, codigo_docente: str) -> List[Dict]:
        """
        Obtiene datos completos de los estudiantes de todas las secciones del docente.
        Incluye asistencia y notas de cada estudiante.
        """
        secciones = self.obtener_secciones_docente(codigo_docente)
        resultado = []

        for seccion in secciones:
            # Parsear la lista de estudiantes (puede ser JSON string o texto)
            lista_raw = seccion.get("lista_estudiantes", "[]")
            try:
                if isinstance(lista_raw, str):
                    lista_estudiantes = json.loads(lista_raw)
                else:
                    lista_estudiantes = lista_raw
            except json.JSONDecodeError:
                lista_estudiantes = []

            estudiantes_data = []
            for codigo_est in lista_estudiantes:
                estudiante_info = self.buscar_estudiante(str(codigo_est))
                notas = self.obtener_notas(str(codigo_est))
                asistencia = self.obtener_asistencia(str(codigo_est))

                # Filtrar solo las notas y asistencia del curso de esta sección
                curso_seccion = seccion.get("curso", "")
                notas_curso = [n for n in notas if n.get("curso") == curso_seccion]
                asistencia_curso = [a for a in asistencia if a.get("curso") == curso_seccion]

                estudiantes_data.append({
                    "codigo": str(codigo_est),
                    "nombre": estudiante_info.get("nombre", "Desconocido") if estudiante_info else "Desconocido",
                    "notas": notas_curso,
                    "asistencia": asistencia_curso
                })

            resultado.append({
                "curso": seccion.get("curso", ""),
                "seccion": seccion.get("seccion", ""),
                "horario": seccion.get("horario", ""),
                "estudiantes": estudiantes_data
            })

        return resultado

    # ===================== RECOPILAR DATOS COMPLETOS =====================

    def recopilar_datos_estudiante(self, codigo: str) -> Dict:
        """
        Recopila TODOS los datos académicos de un estudiante.
        Intenta Google Sheets primero; si falla, usa datos demo.
        """
        try:
            estudiante = self.buscar_estudiante(codigo)
            if estudiante:
                return {
                    "info_personal": estudiante,
                    "carrera": estudiante.get("carrera", ""),
                    "ciclo": estudiante.get("ciclo", ""),
                    "idioma_preferido": estudiante.get("idioma_preferido", "es"),
                    "horarios": self.obtener_horarios(codigo),
                    "notas": self.obtener_notas(codigo),
                    "cursos": self.obtener_cursos(codigo),
                    "examenes": self.obtener_examenes(codigo),
                    "asistencia": self.obtener_asistencia(codigo),
                    "avances_trabajo": self.obtener_avances(codigo),
                    "proyectos_finales": self.obtener_proyectos(codigo),
                    "calendario": self.obtener_calendario("estudiantes")
                }
        except Exception as e:
            print(f"⚠️ Sheets no disponible, usando datos demo para {codigo}: {e}")

        return DEMO_DATOS_ESTUDIANTE.get(codigo, {})

    def recopilar_datos_docente(self, codigo: str) -> Dict:
        """
        Recopila TODOS los datos relevantes para un docente.
        """
        try:
            docente = self.buscar_docente(codigo)
            if docente:
                return {
                    "info_personal": docente,
                    "departamento": docente.get("departamento", ""),
                    "cursos_asignados": docente.get("cursos_asignados", ""),
                    "idioma_preferido": docente.get("idioma_preferido", "es"),
                    "secciones": self.obtener_secciones_docente(codigo),
                    "datos_estudiantes": self.obtener_datos_estudiantes_seccion(codigo),
                    "calendario": self.obtener_calendario("docentes")
                }
        except Exception as e:
            print(f"⚠️ Sheets no disponible, usando datos demo para {codigo}: {e}")

        return DEMO_DATOS_DOCENTE.get(codigo, {})

    # ===================== OPERACIONES DE ESCRITURA =====================

    def registrar_faq(self, fecha: str, codigo_usuario: str, rol: str, 
                      pregunta: str, categoria: str):
        """Registra una pregunta en el FAQ_Log para analytics."""
        try:
            hoja = self._obtener_hoja("FAQ_Log")
            hoja.append_row([fecha, codigo_usuario, rol, pregunta, categoria])
        except Exception as e:
            # No interrumpir el flujo si falla el logging
            print(f"⚠️ Error al registrar FAQ: {e}")

    def actualizar_celda(self, nombre_hoja: str, fila_id: str, 
                         columna: str, nuevo_valor: str) -> bool:
        """
        Actualiza una celda específica en Google Sheets.
        
        Args:
            nombre_hoja: Nombre de la hoja
            fila_id: Valor de la primera columna para identificar la fila
            columna: Nombre de la columna a actualizar
            nuevo_valor: Nuevo valor para la celda
            
        Returns:
            True si se actualizó correctamente, False si no se encontró
        """
        hoja = self._obtener_hoja(nombre_hoja)
        registros = hoja.get_all_records()
        headers = hoja.row_values(1)

        if columna not in headers:
            raise ValueError(f"La columna '{columna}' no existe en la hoja '{nombre_hoja}'.")

        col_index = headers.index(columna) + 1  # gspread usa 1-indexado

        # Buscar la fila por el valor de la primera columna
        for i, registro in enumerate(registros):
            primera_columna = str(list(registro.values())[0])
            if primera_columna == str(fila_id):
                fila_index = i + 2  # +1 por header, +1 por 1-indexado
                hoja.update_cell(fila_index, col_index, nuevo_valor)
                return True

        return False

    def obtener_faq_log(self) -> List[Dict]:
        """Obtiene todos los registros del FAQ_Log."""
        try:
            return self._obtener_todos_registros("FAQ_Log")
        except Exception:
            return []


# Instancia global del servicio (singleton)
sheets_service = SheetsService()
