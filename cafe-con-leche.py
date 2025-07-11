compradores = []

stocks = [150, 180]

def comprar_entrada():
    nombre = ""
    funcion = 0 # 1 o 2 es valor válido
    while True:
        nombre = input("Ingrese el nombre del comprador: ")

        nombre_encontrado = False

        for comprador in compradores:
            if comprador["nombre"].lower() == nombre.lower():
                nombre_encontrado = True
                break
        if nombre_encontrado == False:
            break
        else:
            print("Ya hay una entrada para esta persona!")
    
    while True:
        try:
            funcion = int(input("Ingresar función a asistir [1: día viernes, 2: día sábado]: "))
        except:
            print("La función debe ser un número")
        else:
            if funcion != 1 and funcion != 2:
                print("Función no válida")
            else:
                if stocks[0] > 0 or stocks[1] > 0:
                    if funcion == 1:
                        if stocks[0] > 0:
                            stocks[0] -= 1
                            break
                        else:
                            print("No hay entradas para la función 1")
                    elif funcion == 2:
                        if stocks[1] > 0:
                            stocks[1] -= 1
                            break
                        else:
                            print("No hay entradas para la función 2")
                else:
                    print("No hay mas entradas. Por favor regrese otro día")
                    break
    
    nuevo_comprador = {
        "nombre": nombre,
        "funcion": funcion
    }

    compradores.append(nuevo_comprador)

def cambio_de_funcion(nombre):
    comprador_encontrado = None

    for comprador in compradores:
        if comprador["nombre"].lower() == nombre.lower():
            comprador_encontrado = comprador
            break
    
    if comprador_encontrado != None:
        cambiar_funcion = input("¿Desea cambiar la función? (Si/No): ")

        if cambiar_funcion == "Si":
            if comprador_encontrado["funcion"] == 1:
                if stocks[1] > 0:
                    comprador_encontrado["funcion"] = 2
                    stocks[0] += 1
                    stocks[1] -= 1
                else:
                    print("No hay stock para la función del sábado")
            elif comprador_encontrado["funcion"] == 2:
                if stocks[0] > 0:
                    comprador_encontrado["funcion"] = 1
                    stocks[0] -= 1
                    stocks[1] += 1
                else:
                    print("No hay stock para la función del viernes")
    else:
        print("Comprador no encontrado!")

def obtener_stock():
    print("Stock Disponible:")
    print("=============================")
    print(f"Viernes: {stocks[0]} entradas")
    print(f"Sábado: {stocks[1]} entradas")
    print("=============================")
    print(f"Total: {stocks[0] + stocks[1]} entradas")

while True:
    opcion = 0

    print("TOTEM AUTOATENCIÓN CAFECONLECHE")
    print("1.- Comprar entrada a Cats.")
    print("2.- Cambio de función.")
    print("3.- Mostrar stock de funciones.")
    print("4.- Salir.")

    try:
        opcion = int(input("Ingresar opción: "))
    except ValueError:
        print("La opción debe ser un número")
    else:
        if opcion == 4:
            print("Programa finalizado...")
            break
        elif opcion == 1:
            comprar_entrada()
        elif opcion == 2:
            nombre = input("Ingrese el nombre del comprador: ")
            cambio_de_funcion(nombre)
        elif opcion == 3:
            obtener_stock()
        else:
            print("Debe ingresar una opción válida")
