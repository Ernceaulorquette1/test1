import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/models/nutrition.dart';
import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

/// Resultado del análisis IA: nutrición estimada y guardado en el historial.
class ScanResultScreen extends ConsumerStatefulWidget {
  const ScanResultScreen({super.key, required this.analysis, required this.image});

  final FoodAnalysis analysis;
  final File image;

  @override
  ConsumerState<ScanResultScreen> createState() => _ScanResultScreenState();
}

class _ScanResultScreenState extends ConsumerState<ScanResultScreen> {
  MealType _type = _suggestType();
  bool _saving = false;

  static MealType _suggestType() {
    final h = DateTime.now().hour;
    if (h < 11) return MealType.breakfast;
    if (h < 16) return MealType.lunch;
    if (h < 21) return MealType.dinner;
    return MealType.snack;
  }

  Future<void> _save() async {
    setState(() => _saving = true);
    try {
      await ref.read(mealRepositoryProvider).saveMeal(
            type: _type,
            name: widget.analysis.dishName,
            portion: widget.analysis.portion,
            total: widget.analysis.total,
            items: widget.analysis.items,
          );
      if (mounted) Navigator.of(context)..pop()..pop();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('No se pudo guardar la comida')));
        setState(() => _saving = false);
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final a = widget.analysis;
    return Scaffold(
      appBar: AppBar(title: const Text('Resultado')),
      body: ListView(padding: const EdgeInsets.all(16), children: [
        ClipRRect(
          borderRadius: BorderRadius.circular(24),
          child: Image.file(widget.image, height: 200, fit: BoxFit.cover),
        ),
        const SizedBox(height: 16),
        Text(a.dishName,
            style: Theme.of(context)
                .textTheme
                .titleLarge
                ?.copyWith(fontWeight: FontWeight.bold)),
        if (a.portion.isNotEmpty)
          Text(a.portion, style: Theme.of(context).textTheme.bodyMedium),
        const SizedBox(height: 16),
        Card(
          child: Padding(
            padding: const EdgeInsets.all(20),
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text('Calorías', style: Theme.of(context).textTheme.bodyMedium),
              Text('${a.total.calories.round()} kcal',
                  style: Theme.of(context)
                      .textTheme
                      .displaySmall
                      ?.copyWith(fontWeight: FontWeight.bold, color: AppColors.green)),
              const Divider(height: 24),
              _row('Proteínas', a.total.proteinG, 'g', AppColors.protein),
              _row('Carbohidratos', a.total.carbsG, 'g', AppColors.carbs),
              _row('Grasas', a.total.fatG, 'g', AppColors.fat),
              _row('Fibra', a.total.fiberG, 'g', Colors.brown),
              _row('Azúcar', a.total.sugarG, 'g', Colors.pink),
              _row('Sodio', a.total.sodiumMg, 'mg', Colors.blueGrey),
            ]),
          ),
        ),
        if (a.items.isNotEmpty) ...[
          const SizedBox(height: 16),
          Text('Ingredientes detectados',
              style: Theme.of(context)
                  .textTheme
                  .titleMedium
                  ?.copyWith(fontWeight: FontWeight.bold)),
          ...a.items.map((i) => ListTile(
                dense: true,
                leading: const Icon(Icons.check_circle_rounded, color: AppColors.green),
                title: Text(i.name),
                subtitle: Text(i.quantity),
                trailing: Text('${i.nutrition.calories.round()} kcal'),
              )),
        ],
        const SizedBox(height: 16),
        SegmentedButton<MealType>(
          segments: MealType.values
              .map((t) => ButtonSegment(value: t, label: Text(t.label)))
              .toList(),
          selected: {_type},
          onSelectionChanged: (s) => setState(() => _type = s.first),
        ),
        const SizedBox(height: 16),
        FilledButton(
          onPressed: _saving ? null : _save,
          child: _saving
              ? const SizedBox(
                  height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Guardar comida'),
        ),
        const SizedBox(height: 24),
      ]),
    );
  }

  Widget _row(String label, double value, String unit, Color color) {
    return Padding(
      padding: const EdgeInsets.symmetric(vertical: 4),
      child: Row(children: [
        Container(
            width: 10,
            height: 10,
            decoration: BoxDecoration(color: color, shape: BoxShape.circle)),
        const SizedBox(width: 10),
        Expanded(child: Text(label)),
        Text('${value.round()} $unit',
            style: const TextStyle(fontWeight: FontWeight.w600)),
      ]),
    );
  }
}
