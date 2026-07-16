import 'package:firebase_core/firebase_core.dart';
import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import 'app.dart';

Future<void> main() async {
  WidgetsFlutterBinding.ensureInitialized();
  try {
    // firebase_options.dart se genera con `flutterfire configure`.
    await Firebase.initializeApp();
  } catch (_) {
    // Permite ejecutar la app sin Firebase configurado (solo email demo).
    debugPrint('Firebase no configurado: ejecuta `flutterfire configure`.');
  }
  runApp(const ProviderScope(child: NutriScanApp()));
}
