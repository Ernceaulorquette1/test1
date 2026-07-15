import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:intl/intl.dart';

import '../../../core/models/nutrition.dart';
import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

final _selectedDayProvider = StateProvider<DateTime>((_) => DateTime.now());
final _dayHistoryProvider = FutureProvider.autoDispose((ref) {
  final day = ref.watch(_selectedDayProvider);
  return ref.watch(mealRepositoryProvider).dayHistory(day);
});

/// Historial diario agrupado por desayuno / almuerzo / cena / snacks.
class HistoryScreen extends ConsumerWidget {
  const HistoryScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final day = ref.watch(_selectedDayProvider);
    final history = ref.watch(_dayHistoryProvider);
    final isToday = DateUtils.isSameDay(day, DateTime.now());

    return Scaffold(
      appBar: AppBar(
        title: const Text('Historial'),
        leading: IconButton(
          icon: const Icon(Icons.chevron_left_rounded),
          onPressed: () => ref.read(_selectedDayProvider.notifier).state =
              day.subtract(const Duration(days: 1)),
        ),
        actions: [
          IconButton(
            icon: const Icon(Icons.chevron_right_rounded),
            onPressed: isToday
                ? null
                : () => ref.read(_selectedDayProvider.notifier).state =
                    day.add(const Duration(days: 1)),
          ),
        ],
      ),
      body: history.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('No se pudo cargar el historial')),
        data: (d) {
          final byType = <MealType, List<Meal>>{};
          for (final m in d.meals) {
            byType.putIfAbsent(m.mealType, () => []).add(m);
          }
          return ListView(padding: const EdgeInsets.all(16), children: [
            Text(
              isToday
                  ? 'Hoy, ${DateFormat('d MMMM').format(day)}'
                  : DateFormat('EEEE d MMMM').format(day),
              textAlign: TextAlign.center,
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold),
            ),
            const SizedBox(height: 12),
            Card(
              child: ListTile(
                title: const Text('Calorías del día'),
                trailing: Text(
                  '${d.totalCalories.round()}${d.targetCalories != null ? ' / ${d.targetCalories}' : ''} kcal',
                  style: const TextStyle(
                      fontWeight: FontWeight.bold, color: AppColors.green, fontSize: 16),
                ),
              ),
            ),
            const SizedBox(height: 8),
            for (final type in MealType.values) ...[
              if (byType[type]?.isNotEmpty ?? false) ...[
                Padding(
                  padding: const EdgeInsets.only(top: 12, bottom: 4),
                  child: Text(type.label,
                      style: Theme.of(context)
                          .textTheme
                          .titleSmall
                          ?.copyWith(fontWeight: FontWeight.bold)),
                ),
                ...byType[type]!.map((m) => Dismissible(
                      key: ValueKey(m.id),
                      direction: DismissDirection.endToStart,
                      background: Container(
                        alignment: Alignment.centerRight,
                        padding: const EdgeInsets.only(right: 24),
                        color: Colors.red,
                        child: const Icon(Icons.delete_rounded, color: Colors.white),
                      ),
                      onDismissed: (_) async {
                        await ref.read(mealRepositoryProvider).deleteMeal(m.id);
                        ref.invalidate(_dayHistoryProvider);
                      },
                      child: Card(
                        child: ListTile(
                          title: Text(m.name),
                          subtitle: Text(DateFormat('HH:mm').format(m.eatenAt.toLocal())),
                          trailing: Text('${m.total.calories.round()} kcal',
                              style: const TextStyle(fontWeight: FontWeight.bold)),
                        ),
                      ),
                    )),
              ],
            ],
            if (d.meals.isEmpty)
              const Padding(
                padding: EdgeInsets.all(48),
                child: Center(child: Text('Sin comidas registradas este día')),
              ),
            const SizedBox(height: 80),
          ]);
        },
      ),
    );
  }
}
