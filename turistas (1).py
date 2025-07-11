turistas = {
    "001": ["John Doe", "Estados Unidos", "12-01-2024"], 
    "002": ["Emily Smith", "Estados Unidos", "23-03-2024"], 
    "012": ["Julian Martinez", "Argentina", "19-09-2023"], 
    "014": ["Agustin Morales", "Argentina", "28-03-2024"], 
    "005": ["Carlos Garcia", "Mexico", "10-05-2024"], 
    "006": ["Maria Lopez", "Mexico", "08-12-2023"], 
    "007": ["Joao Silva", "Brasil", "20-06-2024"], 
    "003": ["Michael Brown", "Estados Unidos", "05-07-2023"], 
    "004": ["Jessica Davis", "Estados Unidos", "15-11-2024"], 
    "008": ["Ana Santos", "Brasil", "03-10-2023"], 
    "010": ["Martin Fernandez", "Argentina", "13-02-2023"], 
    "011": ["Sofia Gomez", "Argentina", "07-04-2024"], 
}

def turistas_por_pais(pais):
    valores = turistas.values()
    
    encontrados = []

    for valor in valores:
        pais_valor = valor[1]

        if pais_valor.lower() == pais.lower():
            encontrados.append(valor[0])

    print(encontrados)

def turistas_por_mes(mes):
    valores = turistas.values()

    contador_encontrados = 0

    for valor in valores:
        fecha = valor[2] # 12-02-2024
        diaMesAnio = fecha.split("-") # ["12", "02", "2024"]
        mes_valor = int(diaMesAnio[1])

        if mes == mes_valor:
            contador_encontrados += 1

    return (contador_encontrados / len(valores)) * 100

def eliminar_turista():
    nombre = input("Ingrese el nombre del turista a eliminar: ")

    claves = turistas.keys()

    clave_encontrado = None

    for clave in claves:
        turista = turistas[clave]

        nombre_turista = turista[0]

        if nombre_turista.lower() == nombre.lower():
            clave_encontrado = clave

    if clave_encontrado == None:
        print("Turista no encontrado. No se pudo eliminar")
    else:
        del turistas[clave_encontrado]
        print("Turista eliminado con éxito")

while True:
    print("*** MENU PRINCIPAL ***")
    print("1.- Turistas por país.")
    print("2.- Turista por mes.")
    print("3.- Eliminar turista.")
    print("4.- Salir")

    opcion = 0

    try:
        opcion = int(input("Ingrese opción: "))
    except ValueError:
        print("La opción debe ser un número")
    else:
        if opcion == 1:
            pais = input("Ingrese el país a buscar: ")
            turistas_por_pais(pais)
        elif opcion == 2:
            while True:
                try:
                    mes = int(input("Ingrese el mes a buscar (1-12): "))

                    if 1 <= mes <= 12:
                        porcentaje = turistas_por_mes(mes)

                        print(f"El porcentaje de turistas que viajan en el mes {mes} es: {porcentaje:.1f}%")
                        break
                    else:
                        print("El mes debe ser entre 1 y 12")
                except ValueError:
                    print("El mes debe ser un número")
        elif opcion == 3:
            eliminar_turista()
        elif opcion == 4:
            print("Programa finalizado")
            break
        else:
            print("Ingrese una opción válida")

