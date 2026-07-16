import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/repositories.dart';

final _waterProvider =
    FutureProvider.autoDispose((ref) => ref.watch(waterRepositoryProvider).today());

/// Registro de consumo de agua con meta diaria.
class WaterScreen extends ConsumerWidget {
  const WaterScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final water = ref.watch(_waterProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Consumo de agua')),
      body: water.when(
        loading: () => const Center(child: CircularProgressIndicator()),
        error: (_, __) => const Center(child: Text('No se pudo cargar')),
        data: (d) {
          final progress = (d.totalMl / d.goalMl).clamp(0.0, 1.0);
          final glasses = (d.totalMl / 250).floor();
          return ListView(padding: const EdgeInsets.all(24), children: [
            SizedBox(
              width: 180,
              height: 180,
              child: Stack(fit: StackFit.expand, children: [
                CircularProgressIndicator(
                  value: progress,
                  strokeWidth: 14,
                  strokeCap: StrokeCap.round,
                  color: Colors.blue,
                  backgroundColor: Colors.blue.withValues(alpha: .12),
                ),
                Center(
                  child: Column(mainAxisSize: MainAxisSize.min, children: [
                    Text('${(d.totalMl / 1000).toStringAsFixed(1)} L',
                        style: Theme.of(context)
                            .textTheme
                            .headlineMedium
                            ?.copyWith(fontWeight: FontWeight.bold)),
                    Text('de ${(d.goalMl / 1000).toStringAsFixed(1)} L'),
                    Text('${(progress * 100).round()}%',
                        style: const TextStyle(color: Colors.blue)),
                  ]),
                ),
              ]),
            ),
            const SizedBox(height: 32),
            Wrap(
              alignment: WrapAlignment.center,
              spacing: 8,
              children: List.generate(
                (d.goalMl / 250).ceil(),
                (i) => Icon(
                  i < glasses ? Icons.local_drink_rounded : Icons.local_drink_outlined,
                  color: i < glasses ? Colors.blue : Colors.blue.withValues(alpha: .3),
                  size: 32,
                ),
              ),
            ),
            const SizedBox(height: 32),
            FilledButton.icon(
              onPressed: () async {
                await ref.read(waterRepositoryProvider).add(250);
                ref.invalidate(_waterProvider);
              },
              icon: const Icon(Icons.add_rounded),
              label: const Text('Agregar 1 vaso (250 ml)'),
            ),
            const SizedBox(height: 12),
            OutlinedButton.icon(
              onPressed: () async {
                await ref.read(waterRepositoryProvider).add(500);
                ref.invalidate(_waterProvider);
              },
              icon: const Icon(Icons.add_rounded),
              label: const Text('Agregar botella (500 ml)'),
              style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
            ),
          ]);
        },
      ),
    );
  }
}
