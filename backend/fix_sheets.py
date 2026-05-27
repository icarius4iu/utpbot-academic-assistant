import os
import random
import time
import gspread
from google.oauth2.service_account import Credentials
from dotenv import load_dotenv

load_dotenv()
SCOPES = ["https://www.googleapis.com/auth/spreadsheets", "https://www.googleapis.com/auth/drive"]

creds_file = "credentials.json"
spreadsheet_id = os.getenv("SPREADSHEET_ID")
credentials = Credentials.from_service_account_file(creds_file, scopes=SCOPES)
client = gspread.authorize(credentials)
spreadsheet = client.open_by_key(spreadsheet_id)

random.seed(42)

# ========== REGLAS DE CICLOS =============
# Año actual aprox 2025/2026 (tomamos 2025 de base, cada año son 2 ciclos)
# U20 -> Entró en 2020 -> Ciclo 10
# U21 -> Entró en 2021 -> Ciclo 8
# U22 -> Entró en 2022 -> Ciclo 6
# U23 -> Entró en 2023 -> Ciclo 4
# U24 -> Entró en 2024 -> Ciclo 2
# U25 -> Entró en 2025 -> Ciclo 1

def gen_codigo_ciclo():
    año = random.choice([20, 21, 22, 23, 24, 25])
    codigo = f"U{año}{random.randint(100000, 999999)}"
    if año == 20: ciclo = random.choice([9, 10])
    elif año == 21: ciclo = random.choice([7, 8])
    elif año == 22: ciclo = random.choice([5, 6])
    elif año == 23: ciclo = random.choice([3, 4])
    elif año == 24: ciclo = random.choice([2])
    else: ciclo = 1
    return codigo, ciclo

nombres_pool = ["Andrés", "Valeria", "Fabricio", "Camila", "Sebastián", "Renato", "Lucía", "Diego", "Ana", "Carlos", "Mateo", "Estefany", "Jesús", "Fernanda", "Pedro", "Sofia", "Jorge", "Maria"]
apellidos_pool = ["Vargas", "López", "Rojas", "Chávez", "Pérez", "Castillo", "Mendoza", "García", "Flores", "Díaz", "Quispe", "Torres", "Ruiz", "Salazar", "Ramos"]
carreras_pool = ["Ingeniería de Sistemas", "Ingeniería Civil", "Arquitectura", "Derecho", "Psicología", "Medicina Humana", "Administración", "Diseño Gráfico", "Enfermería"]
cursos_por_carrera = {
    "Ingeniería de Sistemas": ["Algoritmos", "Base de Datos", "Ingeniería de Software", "Redes P2P", "IA"],
    "Ingeniería Civil": ["Estática", "Matemáticas III", "Materiales", "Mecánica de Suelos", "Hidráulica"],
    "Arquitectura": ["Taller de Diseño", "Geometría Descriptiva", "Urbanismo", "Estructuras I", "Historia del Arte"],
    "Derecho": ["Derecho Penal", "Derecho Civil", "Filosofía General", "Contratos", "Derecho Constitucional"],
    "Psicología": ["Neurociencia", "Psicología Clínica", "Pruebas Psicológicas", "Entrevista", "Desarrollo Humano"],
    "Medicina Humana": ["Anatomía Funcional", "Fisiología", "Biología", "Farmacología", "Semiología"],
    "Administración": ["Marketing", "Contabilidad", "Finanzas", "Costos y Presupuestos", "Recursos Humanos"],
    "Diseño Gráfico": ["Ilustración", "Tipografía", "Identidad Corporativa", "Fotografía", "Modelado 3D"],
    "Enfermería": ["Cuidados Básicos", "Salud Pública", "Nutrición", "Primeros Auxilios", "Epidemiología"]
}

estudiantes = []
codigos_estudiantes = []
# Generamos 50 estudiantes
for _ in range(50):
    cod, ciclo = gen_codigo_ciclo()
    nombre = f"{random.choice(nombres_pool)} {random.choice(apellidos_pool)} {random.choice(apellidos_pool)}"
    carrera = random.choice(carreras_pool)
    idioma = random.choice(["es", "es", "en"])
    estudiantes.append([cod, nombre, carrera, ciclo, idioma])
    codigos_estudiantes.append(cod)

docentes = [
    ["D2001", "Dr. Roberto Flores", "Ingeniería", "Algoritmos, Estructuras de Datos, Base de Datos", "es"],
    ["D2002", "Mg. Carmen Salinas", "Ciencias Básicas", "Cálculo I, Matemática Básica, Estática", "es"],
    ["D2003", "Lic. Jorge Medina", "Gestión", "Marketing, Finanzas, Contabilidad", "en"],
    ["D2004", "Dr. Eliana Castro", "Salud", "Anatomía, Fisiología, Nutrición", "es"],
    ["D2005", "Mg. Pablo Velez", "Humanidades", "Psicología Clínica, Neurociencia, Entrevista", "es"],
    ["D2006", "Arql. Valeria Ruiz", "Arquitectura", "Taller de Diseño, Geometría Descriptiva", "es"],
    ["D2007", "Abg. Diego Mendoza", "Derecho", "Derecho Penal, Derecho Civil, Contratos", "es"],
    ["D2008", "Lic. Sofia Rojas", "Arte", "Ilustración, Identidad Corporativa", "es"]
]

dias = ["Lunes", "Martes", "Miércoles", "Jueves", "Viernes"]
aulas = ["A0210", "B0105", "C0802", "A0501", "D0304", "E1102", "Torre C-1004", "B1202", "A0909"]
horarios_slots = [("08:00", "10:00"), ("10:15", "12:15"), ("13:00", "15:00"), ("15:30", "17:30"), ("18:00", "20:00")]

horarios_arr = []
notas_arr = []
cursos_arr = []
examenes_arr = []
asistencia_arr = []
avances_arr = []
proyectos_arr = []

for est in estudiantes:
    cod_est = est[0]
    carrera = est[2]
    # Asignamos de 3 a 5 cursos a cada estudiante
    n_cursos = random.randint(3, 5)
    cursos_alumno = random.sample(cursos_por_carrera[carrera], n_cursos)
    
    for c in cursos_alumno:
        # Cursos
        cursos_arr.append([cod_est, c, random.randint(3, 5), "en curso"])
        
        # Horarios
        docente = random.choice(docentes)[1]
        dia = random.choice(dias)
        h_ini, h_fin = random.choice(horarios_slots)
        aula = random.choice(aulas)
        horarios_arr.append([cod_est, c, dia, h_ini, h_fin, aula, docente])
        
        # Notas
        p1 = random.randint(7, 20)
        p2 = random.randint(7, 20)
        prom = round((p1 + p2) / 2, 1)
        notas_arr.append([cod_est, c, p1, p2, prom])
        
        # Examenes
        tipo_ex = random.choice(["Parcial", "Final", "Sustitutorio"])
        fecha_ex = f"2026-0{random.randint(5,7)}-{random.randint(10,25)}"
        examenes_arr.append([cod_est, c, tipo_ex, fecha_ex, h_ini, aula])
        
        # Asistencias (3 a 5 registros por estudiante por curso)
        for _ in range(random.randint(3, 5)):
            estado_asis = random.choices(["presente", "ausente", "tardanza"], weights=[80, 10, 10])[0]
            fecha_as = f"2026-03-{random.randint(10,28)}"
            asistencia_arr.append([cod_est, c, fecha_as, estado_asis])
        
        # Avances de trabajo (1 a 2 por curso)
        for i in range(1, random.randint(2, 3)):
            estado_av = random.choice(["entregado", "pendiente"])
            nota_av = random.randint(12, 20) if estado_av == "entregado" else ""
            avances_arr.append([cod_est, c, f"Entregable {i}", f"2026-04-{random.randint(5,30)}", estado_av, nota_av])
            
        # Proyectos Finales (1 por curso)
        titulos_proy = [f"Investigación en {c}", f"Aplicación de {c} en industria", f"Ensayo analítico sobre {c}"]
        proyectos_arr.append([cod_est, c, random.choice(titulos_proy), f"Grupo {random.randint(1,5)}", "2026-07-05", ""])

calendario = [
    ["2026-04-15", "Inicio Semana de Parciales", "Comienzo estricto de evaluaciones.", "todos"],
    ["2026-05-01", "Día del Trabajo", "Feriado de ley.", "todos"],
    ["2026-06-25", "Entrega de Notas", "Docentes deben subir notas.", "docentes"],
    ["2026-07-02", "Sustentaciones de Proyectos", "Semana oficial de exposiciones finales grupales.", "estudiantes"]
]

secciones_docentes = []
for doc in docentes:
    cod_doc = doc[0]
    cursos = doc[3].split(", ")
    for curso in cursos:
        # Tomamos 5 alumnos al azar para listar en su seccion
        lista = str(random.sample(codigos_estudiantes, 5)).replace("'", '"')
        dia_sec = random.choice(dias)
        aula_sec = random.choice(aulas)
        secciones_docentes.append([cod_doc, curso, "A-T1", lista, f"{dia_sec} 08:00 {aula_sec}"])

def update_sheet(name, headers, data):
    try:
        sh = spreadsheet.worksheet(name)
        sh.clear()
        sh.append_row(headers)
        if data:
            sh.append_rows(data)
        print(f"✅ Hoja '{name}' actualizada con {len(data)} registros.")
    except Exception as e:
        print(f"❌ Error en hoja '{name}': {e}")
        
    # Pequeño delay para no colapsar la cuota (403 quota exceeded) de Google si subimos todo de golpe
    time.sleep(1.5)

print("Iniciando inyección MASIVA de datos...")

update_sheet("Estudiantes", ["codigo", "nombre", "carrera", "ciclo", "idioma_preferido"], estudiantes)
update_sheet("Docentes", ["codigo", "nombre", "departamento", "cursos_asignados", "idioma_preferido"], docentes)
update_sheet("Cursos", ["codigo_estudiante", "nombre_curso", "creditos", "estado"], cursos_arr)
update_sheet("Horarios", ["codigo_estudiante", "curso", "dia", "hora_inicio", "hora_fin", "aula", "docente"], horarios_arr)
update_sheet("Notas", ["codigo_estudiante", "curso", "parcial", "final", "promedio"], notas_arr)
update_sheet("Examenes", ["codigo_estudiante", "curso", "tipo", "fecha", "hora", "aula"], examenes_arr)
update_sheet("Asistencia", ["codigo_estudiante", "curso", "fecha", "estado"], asistencia_arr)
update_sheet("Avances_Trabajo", ["codigo_estudiante", "curso", "entregable", "fecha_entrega", "estado", "nota"], avances_arr)
update_sheet("Proyectos_Finales", ["codigo_estudiante", "curso", "titulo", "grupo", "fecha_sustentacion", "nota"], proyectos_arr)
update_sheet("Calendario", ["fecha", "evento", "descripcion", "aplica_a"], calendario)
update_sheet("Secciones_Docente", ["codigo_docente", "curso", "seccion", "lista_estudiantes", "horario"], secciones_docentes)

print("¡Relleno masivo completado con absoluta precisión matemática de ciclos referenciados!")
