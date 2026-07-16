import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:mobile_scanner/mobile_scanner.dart';

import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

/// Escáner de código de barras: consulta nutricional del producto.
class BarcodeScreen extends ConsumerStatefulWidget {
  const BarcodeScreen({super.key});

  @override
  ConsumerState<BarcodeScreen> createState() => _BarcodeScreenState();
}

class _BarcodeScreenState extends ConsumerState<BarcodeScreen> {
  bool _handling = false;

  Future<void> _onDetect(BarcodeCapture capture) async {
    if (_handling) return;
    final code = capture.barcodes.firstOrNull?.rawValue;
    if (code == null || code.isEmpty) return;
    setState(() => _handling = true);
    try {
      final product = await ref.read(barcodeRepositoryProvider).lookup(code);
      if (!mounted) return;
      await showModalBottomSheet(
        context: context,
        showDragHandle: true,
        builder: (_) => _ProductSheet(product: product),
      );
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('Producto no encontrado')));
      }
    } finally {
      if (mounted) setState(() => _handling = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Escanear código de barras',
            style: TextStyle(color: Colors.white)),
        backgroundColor: Colors.transparent,
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: Stack(children: [
        MobileScanner(onDetect: _onDetect),
        Center(
          child: Container(
            width: 280,
            height: 160,
            decoration: BoxDecoration(
              border: Border.all(color: AppColors.green, width: 3),
              borderRadius: BorderRadius.circular(16),
            ),
          ),
        ),
        const Align(
          alignment: Alignment(0, .75),
          child: Text('Coloca el código de barras dentro del marco',
              style: TextStyle(color: Colors.white70)),
        ),
      ]),
    );
  }
}

class _ProductSheet extends StatelessWidget {
  const _ProductSheet({required this.product});
  final Map<String, dynamic> product;

  @override
  Widget build(BuildContext context) {
    double d(String k) => ((product[k] ?? 0) as num).toDouble();
    return Padding(
      padding: const EdgeInsets.fromLTRB(24, 0, 24, 32),
      child: Column(mainAxisSize: MainAxisSize.min, crossAxisAlignment: CrossAxisAlignment.start, children: [
        Text(product['name'] ?? 'Producto',
            style: Theme.of(context)
                .textTheme
                .titleLarge
                ?.copyWith(fontWeight: FontWeight.bold)),
        if ((product['brand'] ?? '').toString().isNotEmpty)
          Text(product['brand'], style: Theme.of(context).textTheme.bodyMedium),
        const SizedBox(height: 12),
        Text('Por 100 g:', style: Theme.of(context).textTheme.bodySmall),
        const SizedBox(height: 8),
        _row('Calorías', d('caloriesPer100g'), 'kcal'),
        _row('Proteínas', d('proteinG'), 'g'),
        _row('Carbohidratos', d('carbsG'), 'g'),
        _row('Grasas', d('fatG'), 'g'),
        _row('Azúcares', d('sugarG'), 'g'),
        _row('Sodio', d('sodiumMg'), 'mg'),
      ]),
    );
  }

  Widget _row(String label, double v, String unit) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 3),
        child: Row(children: [
          Expanded(child: Text(label)),
          Text('${v.toStringAsFixed(1)} $unit',
              style: const TextStyle(fontWeight: FontWeight.w600)),
        ]),
      );
}
