import 'package:fl_chart/fl_chart.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

final _rangeProvider = StateProvider<String>((_) => 'week');
final _statsProvider = FutureProvider.autoDispose((ref) {
  final range = ref.watch(_rangeProvider);
  return ref.watch(statsRepositoryProvider).stats(range);
});

/// Estadísticas: peso, IMC, calorías y macros con gráficos.
class StatsScreen extends ConsumerWidget {
  const StatsScreen({super.key});

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final range = ref.watch(_rangeProvider);
    final stats = ref.watch(_statsProvider);

    return Scaffold(
      appBar: AppBar(title: const Text('Estadísticas')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        SegmentedButton<String>(
          segments: const [
            ButtonSegment(value: 'week', label: Text('Semana')),
            ButtonSegment(value: 'month', label: Text('Mes')),
            ButtonSegment(value: 'year', label: Text('Año')),
          ],
          selected: {range},
          onSelectionChanged: (s) =>
              ref.read(_rangeProvider.notifier).state = s.first,
        ),
        const SizedBox(height: 16),
        stats.when(
          loading: () => const Padding(
              padding: EdgeInsets.all(48),
              child: Center(child: CircularProgressIndicator())),
          error: (_, __) =>
              const Center(child: Text('No se pudieron cargar las estadísticas')),
          data: (d) => Column(children: [
            Row(children: [
              _StatTile('Peso', _fmt(d['currentWeightKg'], 'kg')),
              const SizedBox(width: 12),
              _StatTile('IMC', _fmt(d['bmi'], '')),
              const SizedBox(width: 12),
              _StatTile('Promedio', _fmt(d['avgCalories'], 'kcal')),
            ]),
            const SizedBox(height: 16),
            _CaloriesChart(series: (d['calorieSeries'] as List?) ?? []),
            const SizedBox(height: 16),
            _WeightCard(
              series: (d['weightSeries'] as List?) ?? [],
              onLogWeight: (kg) async {
                await ref.read(statsRepositoryProvider).logWeight(kg);
                ref.invalidate(_statsProvider);
              },
            ),
          ]),
        ),
        const SizedBox(height: 80),
      ]),
    );
  }

  static String _fmt(dynamic v, String unit) =>
      v == null ? '—' : '${(v as num).toStringAsFixed(unit == 'kcal' ? 0 : 1)} $unit'.trim();
}

class _StatTile extends StatelessWidget {
  const _StatTile(this.label, this.value);
  final String label, value;

  @override
  Widget build(BuildContext context) {
    return Expanded(
      child: Card(
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(children: [
            Text(value,
                style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
            Text(label, style: Theme.of(context).textTheme.bodySmall),
          ]),
        ),
      ),
    );
  }
}

class _CaloriesChart extends StatelessWidget {
  const _CaloriesChart({required this.series});
  final List<dynamic> series;

  @override
  Widget build(BuildContext context) {
    if (series.isEmpty) {
      return const Card(
          child: Padding(
              padding: EdgeInsets.all(32),
              child: Center(child: Text('Registra comidas para ver tus gráficos'))));
    }
    final bars = <BarChartGroupData>[];
    for (var i = 0; i < series.length; i++) {
      final cal = ((series[i]['calories'] ?? 0) as num).toDouble();
      bars.add(BarChartGroupData(x: i, barRods: [
        BarChartRodData(
          toY: cal,
          width: 14,
          borderRadius: BorderRadius.circular(4),
          color: AppColors.green,
        ),
      ]));
    }
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Text('Calorías por día',
              style: Theme.of(context)
                  .textTheme
                  .titleSmall
                  ?.copyWith(fontWeight: FontWeight.bold)),
          const SizedBox(height: 16),
          SizedBox(
            height: 180,
            child: BarChart(BarChartData(
              barGroups: bars,
              gridData: const FlGridData(show: false),
              borderData: FlBorderData(show: false),
              titlesData: FlTitlesData(
                leftTitles: const AxisTitles(
                    sideTitles: SideTitles(showTitles: true, reservedSize: 40)),
                topTitles: const AxisTitles(),
                rightTitles: const AxisTitles(),
                bottomTitles: AxisTitles(
                  sideTitles: SideTitles(
                    showTitles: true,
                    getTitlesWidget: (v, _) {
                      final i = v.toInt();
                      if (i < 0 || i >= series.length) return const SizedBox.shrink();
                      final date = series[i]['date'] as String? ?? '';
                      return Text(date.length >= 10 ? date.substring(8) : '',
                          style: const TextStyle(fontSize: 10));
                    },
                  ),
                ),
              ),
            )),
          ),
        ]),
      ),
    );
  }
}

class _WeightCard extends StatelessWidget {
  const _WeightCard({required this.series, required this.onLogWeight});
  final List<dynamic> series;
  final Future<void> Function(double) onLogWeight;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
          Row(children: [
            Expanded(
              child: Text('Peso',
                  style: Theme.of(context)
                      .textTheme
                      .titleSmall
                      ?.copyWith(fontWeight: FontWeight.bold)),
            ),
            TextButton.icon(
              onPressed: () async {
                final controller = TextEditingController();
                final kg = await showDialog<double>(
                  context: context,
                  builder: (ctx) => AlertDialog(
                    title: const Text('Registrar peso de hoy'),
                    content: TextField(
                      controller: controller,
                      keyboardType: TextInputType.number,
                      decoration: const InputDecoration(labelText: 'Peso (kg)'),
                    ),
                    actions: [
                      TextButton(
                          onPressed: () => Navigator.pop(ctx),
                          child: const Text('Cancelar')),
                      FilledButton(
                          onPressed: () =>
                              Navigator.pop(ctx, double.tryParse(controller.text)),
                          child: const Text('Guardar')),
                    ],
                  ),
                );
                if (kg != null) await onLogWeight(kg);
              },
              icon: const Icon(Icons.add_rounded),
              label: const Text('Registrar'),
            ),
          ]),
          if (series.length >= 2)
            SizedBox(
              height: 140,
              child: LineChart(LineChartData(
                gridData: const FlGridData(show: false),
                borderData: FlBorderData(show: false),
                titlesData: const FlTitlesData(show: false),
                lineBarsData: [
                  LineChartBarData(
                    spots: [
                      for (var i = 0; i < series.length; i++)
                        FlSpot(i.toDouble(),
                            ((series[i]['weightKg'] ?? 0) as num).toDouble()),
                    ],
                    isCurved: true,
                    color: AppColors.purple,
                    barWidth: 3,
                    dotData: const FlDotData(show: false),
                    belowBarData: BarAreaData(
                        show: true, color: AppColors.purple.withValues(alpha: .1)),
                  ),
                ],
              )),
            )
          else
            const Padding(
              padding: EdgeInsets.symmetric(vertical: 16),
              child: Text('Registra tu peso varios días para ver la tendencia'),
            ),
        ]),
      ),
    );
  }
}
