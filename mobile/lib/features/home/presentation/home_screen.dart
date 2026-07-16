import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../core/models/nutrition.dart';
import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

final _todayProvider = FutureProvider.autoDispose((ref) =>
    ref.watch(mealRepositoryProvider).dayHistory(DateTime.now()));
final _waterTodayProvider =
    FutureProvider.autoDispose((ref) => ref.watch(waterRepositoryProvider).today());
final _profileProvider =
    FutureProvider.autoDispose((ref) => ref.watch(profileRepositoryProvider).get());

/// Dashboard principal: calorías restantes, macros y registro del día.
class HomeScreen extends ConsumerWidget {
  const HomeScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final today = ref.watch(_todayProvider);
    final profile = ref.watch(_profileProvider);
    final name = profile.valueOrNull?['name'] as String? ?? '';

    return Scaffold(
      appBar: AppBar(
        title: Text('Hola${name.isEmpty ? '' : ', $name'} 👋'),
        actions: [
          IconButton(
              onPressed: () => context.push('/premium'),
              icon: const Icon(Icons.workspace_premium_rounded)),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async {
          ref.invalidate(_todayProvider);
          ref.invalidate(_waterTodayProvider);
        },
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            today.when(
              loading: () => const _CaloriesCard(consumed: 0, target: null, totals: Nutrition()),
              error: (_, __) => const _ErrorCard(),
              data: (d) => _CaloriesCard(
                  consumed: d.totalCalories, target: d.targetCalories, totals: d.totals),
            ),
            const SizedBox(height: 16),
            Row(children: [
              Expanded(child: _ShortcutCard(
                icon: Icons.water_drop_rounded,
                color: Colors.blue,
                title: 'Agua',
                subtitle: ref.watch(_waterTodayProvider).valueOrNull is ({int goalMl, int totalMl})
                    ? '${(ref.watch(_waterTodayProvider).value!.totalMl / 1000).toStringAsFixed(1)} L'
                    : '—',
                onTap: () => context.push('/water'),
              )),
              const SizedBox(width: 12),
              Expanded(child: _ShortcutCard(
                icon: Icons.chat_bubble_rounded,
                color: AppColors.purple,
                title: 'Chat IA',
                subtitle: 'Pregúntame',
                onTap: () => context.push('/chat'),
              )),
              const SizedBox(width: 12),
              Expanded(child: _ShortcutCard(
                icon: Icons.qr_code_scanner_rounded,
                color: AppColors.green,
                title: 'Escáner',
                subtitle: 'Códigos',
                onTap: () => context.push('/barcode'),
              )),
            ]),
            const SizedBox(height: 24),
            Text('Registro de hoy',
                style: Theme.of(context)
                    .textTheme
                    .titleMedium
                    ?.copyWith(fontWeight: FontWeight.bold)),
            const SizedBox(height: 8),
            ...today.when(
              loading: () => [const Center(child: Padding(
                  padding: EdgeInsets.all(24), child: CircularProgressIndicator()))],
              error: (_, __) => [const SizedBox.shrink()],
              data: (d) => d.meals.isEmpty
                  ? [
                      Card(
                        child: Padding(
                          padding: const EdgeInsets.all(24),
                          child: Column(children: [
                            const Icon(Icons.restaurant_rounded, size: 40),
                            const SizedBox(height: 8),
                            const Text('Aún no registras comidas hoy'),
                            TextButton(
                                onPressed: () => context.push('/scan'),
                                child: const Text('Escanear mi primera comida')),
                          ]),
                        ),
                      )
                    ]
                  : d.meals
                      .map((m) => Card(
                            child: ListTile(
                              leading: CircleAvatar(
                                backgroundColor: AppColors.green.withValues(alpha: .15),
                                child: const Icon(Icons.restaurant_rounded,
                                    color: AppColors.green),
                              ),
                              title: Text(m.name),
                              subtitle: Text(m.mealType.label),
                              trailing: Text('${m.total.calories.round()} kcal',
                                  style: const TextStyle(fontWeight: FontWeight.bold)),
                            ),
                          ))
                      .toList(),
            ),
            const SizedBox(height: 80),
          ],
        ),
      ),
    );
  }
}

class _CaloriesCard extends StatelessWidget {
  const _CaloriesCard({required this.consumed, required this.target, required this.totals});

  final double consumed;
  final int? target;
  final Nutrition totals;

  @override
  Widget build(BuildContext context) {
    final goal = target ?? 2200;
    final remaining = (goal - consumed).clamp(0, goal.toDouble());
    final progress = (consumed / goal).clamp(0.0, 1.0);
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(children: [
          Row(children: [
            Expanded(
              child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
                Text('Calorías restantes',
                    style: Theme.of(context).textTheme.bodyMedium),
                Text('${remaining.round()}',
                    style: Theme.of(context)
                        .textTheme
                        .displaySmall
                        ?.copyWith(fontWeight: FontWeight.bold)),
                Text('de $goal kcal', style: Theme.of(context).textTheme.bodySmall),
              ]),
            ),
            SizedBox(
              width: 88,
              height: 88,
              child: Stack(fit: StackFit.expand, children: [
                CircularProgressIndicator(
                  value: progress,
                  strokeWidth: 9,
                  strokeCap: StrokeCap.round,
                  color: AppColors.green,
                  backgroundColor: AppColors.green.withValues(alpha: .15),
                ),
                Center(
                    child: Text('${(progress * 100).round()}%',
                        style: const TextStyle(fontWeight: FontWeight.bold))),
              ]),
            ),
          ]),
          const SizedBox(height: 16),
          Row(children: [
            _Macro('Proteínas', totals.proteinG, AppColors.protein),
            _Macro('Carbohidratos', totals.carbsG, AppColors.carbs),
            _Macro('Grasas', totals.fatG, AppColors.fat),
          ]),
        ]),
      ),
    );
  }
}

class _Macro extends StatelessWidget {
  const _Macro(this.label, this.grams, this.color);
  final String label;
  final double grams;
  final Color color;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Column(children: [
        Text('${grams.round()} g',
            style: TextStyle(fontWeight: FontWeight.bold, color: color)),
        Text(label,
            style: Theme.of(context).textTheme.bodySmall, textAlign: TextAlign.center),
      ]),
    );
  }
}

class _ShortcutCard extends StatelessWidget {
  const _ShortcutCard({
    required this.icon,
    required this.color,
    required this.title,
    required this.subtitle,
    required this.onTap,
  });

  final IconData icon;
  final Color color;
  final String title, subtitle;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: InkWell(
        borderRadius: BorderRadius.circular(20),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(children: [
            Icon(icon, color: color),
            const SizedBox(height: 6),
            Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 13)),
            Text(subtitle,
                style: Theme.of(context).textTheme.bodySmall,
                overflow: TextOverflow.ellipsis),
          ]),
        ),
      ),
    );
  }
}

class _ErrorCard extends StatelessWidget {
  const _ErrorCard();

  @override
  Widget build(BuildContext context) {
    return const Card(
      child: Padding(
        padding: EdgeInsets.all(24),
        child: Text('No se pudo cargar tu resumen. Desliza para reintentar.'),
      ),
    );
  }
}
