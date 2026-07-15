import 'package:flutter/material.dart';
import 'package:go_router/go_router.dart';

/// Contenedor con barra de navegación inferior y botón central de escaneo.
class ShellScreen extends StatelessWidget {
  const ShellScreen({super.key, required this.shell});

  final StatefulNavigationShell shell;

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: shell,
      floatingActionButton: FloatingActionButton(
        onPressed: () => context.push('/scan'),
        shape: const CircleBorder(),
        child: const Icon(Icons.camera_alt_rounded),
      ),
      floatingActionButtonLocation: FloatingActionButtonLocation.centerDocked,
      bottomNavigationBar: NavigationBar(
        selectedIndex: shell.currentIndex,
        onDestinationSelected: (i) =>
            shell.goBranch(i, initialLocation: i == shell.currentIndex),
        destinations: const [
          NavigationDestination(icon: Icon(Icons.home_rounded), label: 'Inicio'),
          NavigationDestination(icon: Icon(Icons.receipt_long_rounded), label: 'Historial'),
          NavigationDestination(icon: Icon(Icons.bar_chart_rounded), label: 'Estadísticas'),
          NavigationDestination(icon: Icon(Icons.person_rounded), label: 'Perfil'),
        ],
      ),
    );
  }
}
