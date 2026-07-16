import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/network/repositories.dart';
import '../../../core/session/session_controller.dart';
import '../../../core/theme/app_theme.dart';

final _profileProvider =
    FutureProvider.autoDispose((ref) => ref.watch(profileRepositoryProvider).get());

class ProfileScreen extends ConsumerWidget {
  const ProfileScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final profile = ref.watch(_profileProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Mi perfil')),
      body: profile.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('No se pudo cargar el perfil')),
        data: (p) {
          String goalLabel(String? g) => switch (g) {
                'LOSE_WEIGHT' => 'Perder peso',
                'MAINTAIN' => 'Mantener peso',
                'GAIN_MUSCLE' => 'Ganar músculo',
                _ => '—',
              };
          return ListView(padding: const EdgeInsets.all(16), children: [
            Center(
              child: CircleAvatar(
                radius: 44,
                backgroundColor: AppColors.purple.withValues(alpha: .15),
                child: const Icon(Icons.person_rounded,
                    size: 48, color: AppColors.purple),
              ),
            ),
            const SizedBox(height: 8),
            Center(
              child: Text(p['name'] ?? '',
                  style: Theme.of(context)
                      .textTheme
                      .titleLarge
                      ?.copyWith(fontWeight: FontWeight.bold)),
            ),
            Center(child: Text(p['email'] ?? '')),
            if (p['premium'] == true)
              const Center(
                child: Chip(
                  avatar: Icon(Icons.workspace_premium_rounded,
                      color: Colors.amber, size: 18),
                  label: Text('Premium'),
                ),
              ),
            const SizedBox(height: 16),
            Card(
              child: Column(children: [
                _tile('Objetivo', goalLabel(p['goal'])),
                _tile('Peso actual', '${p['weightKg'] ?? '—'} kg'),
                _tile('Peso objetivo', '${p['targetWeightKg'] ?? '—'} kg'),
                _tile('IMC', '${p['bmi'] ?? '—'}'),
                _tile('Calorías objetivo', '${p['targetCalories'] ?? '—'} kcal'),
              ]),
            ),
            const SizedBox(height: 16),
            Card(
              child: Column(children: [
                ListTile(
                  leading: const Icon(Icons.workspace_premium_rounded),
                  title: const Text('NutriScan Premium'),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => context.push('/premium'),
                ),
                ListTile(
                  leading: const Icon(Icons.edit_rounded),
                  title: const Text('Editar datos y objetivos'),
                  trailing: const Icon(Icons.chevron_right_rounded),
                  onTap: () => context.push('/setup'),
                ),
                ListTile(
                  leading: const Icon(Icons.logout_rounded, color: Colors.red),
                  title: const Text('Cerrar sesión',
                      style: TextStyle(color: Colors.red)),
                  onTap: () => ref.read(sessionProvider.notifier).signOut(),
                ),
              ]),
            ),
            const SizedBox(height: 80),
          ]);
        },
      ),
    );
  }

  Widget _tile(String label, String value) => ListTile(
        dense: true,
        title: Text(label),
        trailing: Text(value, style: const TextStyle(fontWeight: FontWeight.w600)),
      );
}
