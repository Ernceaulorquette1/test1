import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/repositories.dart';
import '../../../core/session/session_controller.dart';

/// Onboarding de perfil: datos para calcular las calorías objetivo.
class ProfileSetupScreen extends ConsumerStatefulWidget {
  const ProfileSetupScreen({super.key});

  @override
  ConsumerState<ProfileSetupScreen> createState() => _ProfileSetupScreenState();
}

class _ProfileSetupScreenState extends ConsumerState<ProfileSetupScreen> {
  final _name = TextEditingController();
  final _age = TextEditingController();
  final _weight = TextEditingController();
  final _height = TextEditingController();
  final _targetWeight = TextEditingController();
  String _sex = 'MALE';
  String _activity = 'MODERATE';
  String _goal = 'LOSE_WEIGHT';
  bool _busy = false;

  Future<void> _save() async {
    setState(() => _busy = true);
    try {
      await ref.read(profileRepositoryProvider).update({
        'name': _name.text.trim(),
        'age': int.tryParse(_age.text),
        'sex': _sex,
        'weightKg': double.tryParse(_weight.text),
        'heightCm': double.tryParse(_height.text),
        'activityLevel': _activity,
        'goal': _goal,
        'targetWeightKg': double.tryParse(_targetWeight.text),
      });
      ref.read(sessionProvider.notifier).completeProfile();
    } catch (_) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(
            const SnackBar(content: Text('No se pudo guardar el perfil')));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      appBar: AppBar(title: const Text('Cuéntanos sobre ti')),
      body: SingleChildScrollView(
        padding: const EdgeInsets.all(24),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Text('Necesitamos algunos datos para personalizar tu experiencia',
                textAlign: TextAlign.center,
                style: Theme.of(context).textTheme.bodyMedium),
            const SizedBox(height: 24),
            TextField(controller: _name, decoration: const InputDecoration(labelText: 'Nombre')),
            const SizedBox(height: 16),
            Row(children: [
              Expanded(
                child: TextField(
                    controller: _age,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Edad')),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: DropdownButtonFormField<String>(
                  value: _sex,
                  decoration: const InputDecoration(labelText: 'Sexo'),
                  items: const [
                    DropdownMenuItem(value: 'MALE', child: Text('Masculino')),
                    DropdownMenuItem(value: 'FEMALE', child: Text('Femenino')),
                    DropdownMenuItem(value: 'OTHER', child: Text('Otro')),
                  ],
                  onChanged: (v) => setState(() => _sex = v!),
                ),
              ),
            ]),
            const SizedBox(height: 16),
            Row(children: [
              Expanded(
                child: TextField(
                    controller: _weight,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Peso (kg)')),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: TextField(
                    controller: _height,
                    keyboardType: TextInputType.number,
                    decoration: const InputDecoration(labelText: 'Altura (cm)')),
              ),
            ]),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              value: _activity,
              decoration: const InputDecoration(labelText: 'Nivel de actividad'),
              items: const [
                DropdownMenuItem(value: 'SEDENTARY', child: Text('Sedentario')),
                DropdownMenuItem(value: 'LIGHT', child: Text('Ligero')),
                DropdownMenuItem(value: 'MODERATE', child: Text('Moderado')),
                DropdownMenuItem(value: 'ACTIVE', child: Text('Activo')),
                DropdownMenuItem(value: 'VERY_ACTIVE', child: Text('Muy activo')),
              ],
              onChanged: (v) => setState(() => _activity = v!),
            ),
            const SizedBox(height: 16),
            DropdownButtonFormField<String>(
              value: _goal,
              decoration: const InputDecoration(labelText: 'Objetivo'),
              items: const [
                DropdownMenuItem(value: 'LOSE_WEIGHT', child: Text('Perder peso')),
                DropdownMenuItem(value: 'MAINTAIN', child: Text('Mantener peso')),
                DropdownMenuItem(value: 'GAIN_MUSCLE', child: Text('Ganar músculo')),
              ],
              onChanged: (v) => setState(() => _goal = v!),
            ),
            const SizedBox(height: 16),
            TextField(
                controller: _targetWeight,
                keyboardType: TextInputType.number,
                decoration: const InputDecoration(labelText: 'Peso objetivo (kg)')),
            const SizedBox(height: 32),
            FilledButton(
              onPressed: _busy ? null : _save,
              child: _busy
                  ? const SizedBox(
                      height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                  : const Text('Guardar y continuar'),
            ),
          ],
        ),
      ),
    );
  }
}
