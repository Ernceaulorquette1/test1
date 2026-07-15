import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:in_app_purchase/in_app_purchase.dart';

import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

/// Pantalla de suscripción Premium (Google Play Billing).
class PremiumScreen extends ConsumerStatefulWidget {
  const PremiumScreen({super.key});

  static const monthlyId = 'nutriscan_premium_monthly';
  static const annualId = 'nutriscan_premium_annual';

  @override
  ConsumerState<PremiumScreen> createState() => _PremiumScreenState();
}

class _PremiumScreenState extends ConsumerState<PremiumScreen> {
  String _selected = PremiumScreen.annualId;
  bool _busy = false;
  StreamSubscription<List<PurchaseDetails>>? _purchases;

  @override
  void initState() {
    super.initState();
    // Cierre del flujo de compra: Google Play confirma por purchaseStream,
    // el backend valida el token y activa Premium.
    _purchases = InAppPurchase.instance.purchaseStream.listen((updates) async {
      for (final purchase in updates) {
        if (purchase.status == PurchaseStatus.purchased ||
            purchase.status == PurchaseStatus.restored) {
          final plan = purchase.productID == PremiumScreen.monthlyId
              ? 'MONTHLY'
              : 'ANNUAL';
          try {
            await ref.read(subscriptionRepositoryProvider).verify(
                plan, purchase.verificationData.serverVerificationData);
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                  content: Text('¡Bienvenido a NutriScan Premium! 👑')));
              Navigator.of(context).maybePop();
            }
          } catch (_) {
            if (mounted) {
              ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
                  content:
                      Text('Compra recibida, pero no se pudo activar. Reintenta.')));
            }
          }
        }
        if (purchase.pendingCompletePurchase) {
          await InAppPurchase.instance.completePurchase(purchase);
        }
      }
    });
  }

  @override
  void dispose() {
    _purchases?.cancel();
    super.dispose();
  }

  static const _benefits = [
    'Análisis ilimitados con IA',
    'Planes de alimentación personalizados',
    'Chat nutricional ilimitado',
    'Reportes y estadísticas avanzadas',
    'Exportar a PDF y Excel',
    'Sin anuncios',
  ];

  Future<void> _subscribe() async {
    setState(() => _busy = true);
    try {
      final iap = InAppPurchase.instance;
      if (!await iap.isAvailable()) {
        throw Exception('Google Play Billing no disponible');
      }
      final response =
          await iap.queryProductDetails({PremiumScreen.monthlyId, PremiumScreen.annualId});
      final product =
          response.productDetails.where((p) => p.id == _selected).firstOrNull;
      if (product == null) throw Exception('Producto no encontrado en Play Console');
      await iap.buyNonConsumable(purchaseParam: PurchaseParam(productDetails: product));
      // La confirmación llega por purchaseStream; el token se verifica en el
      // backend con /subscriptions/verify (ver SubscriptionRepository.verify).
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('No se pudo iniciar la compra. Inténtalo más tarde.')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      backgroundColor: const Color(0xFF1D1440),
      appBar: AppBar(
        backgroundColor: Colors.transparent,
        iconTheme: const IconThemeData(color: Colors.white),
      ),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        const Icon(Icons.workspace_premium_rounded,
            color: Colors.amber, size: 56),
        const SizedBox(height: 8),
        const Text('NutriScan AI Premium',
            textAlign: TextAlign.center,
            style: TextStyle(
                color: Colors.white, fontSize: 26, fontWeight: FontWeight.bold)),
        const SizedBox(height: 4),
        const Text('Desbloquea todo el potencial de la aplicación',
            textAlign: TextAlign.center, style: TextStyle(color: Colors.white70)),
        const SizedBox(height: 24),
        ..._benefits.map((b) => Padding(
              padding: const EdgeInsets.symmetric(vertical: 6),
              child: Row(children: [
                const Icon(Icons.check_circle_rounded,
                    color: AppColors.green, size: 22),
                const SizedBox(width: 12),
                Expanded(child: Text(b, style: const TextStyle(color: Colors.white))),
              ]),
            )),
        const SizedBox(height: 24),
        _PlanCard(
          title: 'Mensual',
          price: r'$4.990 CLP/mes',
          selected: _selected == PremiumScreen.monthlyId,
          onTap: () => setState(() => _selected = PremiumScreen.monthlyId),
        ),
        const SizedBox(height: 12),
        _PlanCard(
          title: 'Anual',
          price: r'$39.990 CLP/año',
          badge: 'Ahorra 33%',
          selected: _selected == PremiumScreen.annualId,
          onTap: () => setState(() => _selected = PremiumScreen.annualId),
        ),
        const SizedBox(height: 24),
        FilledButton(
          onPressed: _busy ? null : _subscribe,
          style: FilledButton.styleFrom(backgroundColor: AppColors.purple),
          child: _busy
              ? const SizedBox(
                  height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
              : const Text('Prueba 7 días gratis'),
        ),
        const SizedBox(height: 12),
        const Text(
          'Suscripción gestionada por Google Play. Cancela cuando quieras.',
          textAlign: TextAlign.center,
          style: TextStyle(color: Colors.white54, fontSize: 12),
        ),
      ]),
    );
  }
}

class _PlanCard extends StatelessWidget {
  const _PlanCard({
    required this.title,
    required this.price,
    required this.selected,
    required this.onTap,
    this.badge,
  });

  final String title, price;
  final String? badge;
  final bool selected;
  final VoidCallback onTap;

  @override
  Widget build(BuildContext context) {
    return InkWell(
      onTap: onTap,
      borderRadius: BorderRadius.circular(16),
      child: Container(
        padding: const EdgeInsets.all(18),
        decoration: BoxDecoration(
          color: Colors.white.withValues(alpha: selected ? .16 : .06),
          borderRadius: BorderRadius.circular(16),
          border: Border.all(
              color: selected ? AppColors.green : Colors.white24, width: 2),
        ),
        child: Row(children: [
          Expanded(
            child: Column(crossAxisAlignment: CrossAxisAlignment.start, children: [
              Text(title,
                  style: const TextStyle(
                      color: Colors.white, fontWeight: FontWeight.bold, fontSize: 16)),
              Text(price, style: const TextStyle(color: Colors.white70)),
            ]),
          ),
          if (badge != null)
            Container(
              padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
              decoration: BoxDecoration(
                color: AppColors.green,
                borderRadius: BorderRadius.circular(12),
              ),
              child: Text(badge!,
                  style: const TextStyle(
                      color: Colors.white, fontSize: 12, fontWeight: FontWeight.bold)),
            ),
        ]),
      ),
    );
  }
}
