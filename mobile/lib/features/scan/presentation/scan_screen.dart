import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:image_picker/image_picker.dart';

import '../../../core/models/nutrition.dart';
import '../../../core/network/repositories.dart';
import 'scan_result_screen.dart';

/// Cámara IA: fotografiar o elegir de galería y analizar con visión artificial.
class ScanScreen extends ConsumerStatefulWidget {
  const ScanScreen({super.key});

  @override
  ConsumerState<ScanScreen> createState() => _ScanScreenState();
}

class _ScanScreenState extends ConsumerState<ScanScreen> {
  final _picker = ImagePicker();
  File? _image;
  bool _analyzing = false;

  Future<void> _pick(ImageSource source) async {
    final file = await _picker.pickImage(
        source: source, maxWidth: 1280, imageQuality: 85);
    if (file != null) setState(() => _image = File(file.path));
  }

  Future<void> _analyze() async {
    if (_image == null) return;
    setState(() => _analyzing = true);
    try {
      final FoodAnalysis analysis =
          await ref.read(mealRepositoryProvider).analyzePhoto(_image!.path);
      if (!mounted) return;
      if (analysis.dishName == 'NO_FOOD') {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('No se detectó comida en la imagen. Intenta de nuevo.')));
        return;
      }
      Navigator.of(context).push(MaterialPageRoute(
          builder: (_) => ScanResultScreen(analysis: analysis, image: _image!)));
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('No se pudo analizar la imagen. ¿Alcanzaste tu límite diario?')));
      }
    } finally {
      if (mounted) setState(() => _analyzing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: Colors.black,
      appBar: AppBar(
        title: const Text('Escanear comida', style: TextStyle(color: Colors.white)),
        iconTheme: const IconThemeData(color: Colors.white),
        backgroundColor: Colors.transparent,
      ),
      body: Column(children: [
        Expanded(
          child: Center(
            child: _image == null
                ? Column(mainAxisSize: MainAxisSize.min, children: [
                    Icon(Icons.restaurant_rounded,
                        size: 72, color: Colors.white.withValues(alpha: .4)),
                    const SizedBox(height: 16),
                    const Text('Coloca la comida dentro del marco',
                        style: TextStyle(color: Colors.white70)),
                  ])
                : ClipRRect(
                    borderRadius: BorderRadius.circular(24),
                    child: Image.file(_image!, fit: BoxFit.cover),
                  ),
          ),
        ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Row(mainAxisAlignment: MainAxisAlignment.spaceEvenly, children: [
              IconButton.filledTonal(
                onPressed: _analyzing ? null : () => _pick(ImageSource.gallery),
                iconSize: 28,
                icon: const Icon(Icons.photo_library_rounded),
              ),
              // Botón de captura estilo cámara
              GestureDetector(
                onTap: _analyzing
                    ? null
                    : () async {
                        if (_image == null) {
                          await _pick(ImageSource.camera);
                        } else {
                          await _analyze();
                        }
                      },
                child: Container(
                  width: 76,
                  height: 76,
                  decoration: BoxDecoration(
                    shape: BoxShape.circle,
                    color: _image == null ? Colors.white : scheme.primary,
                    border: Border.all(color: Colors.white24, width: 5),
                  ),
                  child: _analyzing
                      ? const Padding(
                          padding: EdgeInsets.all(20),
                          child: CircularProgressIndicator(strokeWidth: 3))
                      : Icon(
                          _image == null
                              ? Icons.camera_alt_rounded
                              : Icons.auto_awesome_rounded,
                          color: _image == null ? Colors.black : Colors.white,
                          size: 32),
                ),
              ),
              IconButton.filledTonal(
                onPressed:
                    _analyzing || _image == null ? null : () => setState(() => _image = null),
                iconSize: 28,
                icon: const Icon(Icons.refresh_rounded),
              ),
            ]),
          ),
        ),
      ]),
    );
  }
}
