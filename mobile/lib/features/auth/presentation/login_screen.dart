import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/session/session_controller.dart';

class LoginScreen extends ConsumerStatefulWidget {
  const LoginScreen({super.key});

  @override
  ConsumerState<LoginScreen> createState() => _LoginScreenState();
}

class _LoginScreenState extends ConsumerState<LoginScreen> {
  final _email = TextEditingController();
  final _password = TextEditingController();
  bool _register = false;
  bool _busy = false;
  bool _obscure = true;

  Future<void> _run(Future<void> Function() action) async {
    setState(() => _busy = true);
    try {
      await action();
    } catch (e) {
      if (mounted) {
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
            content: Text('No se pudo iniciar sesión. Revisa tus datos.'),
            behavior: SnackBarBehavior.floating));
      }
    } finally {
      if (mounted) setState(() => _busy = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final session = ref.read(sessionProvider.notifier);
    return Scaffold(
      body: SafeArea(
        child: SingleChildScrollView(
          padding: const EdgeInsets.all(24),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              const SizedBox(height: 32),
              Text('¡Bienvenido!',
                  textAlign: TextAlign.center,
                  style: Theme.of(context)
                      .textTheme
                      .headlineMedium
                      ?.copyWith(fontWeight: FontWeight.bold)),
              const SizedBox(height: 8),
              Text(
                _register ? 'Crea tu cuenta para continuar' : 'Inicia sesión para continuar',
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 32),
              OutlinedButton.icon(
                onPressed: _busy ? null : () => _run(session.signInWithGoogle),
                icon: const Icon(Icons.g_mobiledata_rounded, size: 28),
                label: const Text('Continuar con Google'),
                style: OutlinedButton.styleFrom(minimumSize: const Size.fromHeight(52)),
              ),
              const SizedBox(height: 24),
              Row(children: [
                const Expanded(child: Divider()),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 12),
                  child: Text('o', style: Theme.of(context).textTheme.bodySmall),
                ),
                const Expanded(child: Divider()),
              ]),
              const SizedBox(height: 24),
              TextField(
                controller: _email,
                keyboardType: TextInputType.emailAddress,
                decoration: const InputDecoration(
                    labelText: 'Correo electrónico', hintText: 'ejemplo@correo.com'),
              ),
              const SizedBox(height: 16),
              TextField(
                controller: _password,
                obscureText: _obscure,
                decoration: InputDecoration(
                  labelText: 'Contraseña',
                  suffixIcon: IconButton(
                    icon: Icon(_obscure ? Icons.visibility_off : Icons.visibility),
                    onPressed: () => setState(() => _obscure = !_obscure),
                  ),
                ),
              ),
              if (!_register)
                Align(
                  alignment: Alignment.centerRight,
                  child: TextButton(
                    onPressed: _busy || _email.text.isEmpty
                        ? null
                        : () => _run(() async {
                              final messenger = ScaffoldMessenger.of(context);
                              await session.sendPasswordReset(_email.text.trim());
                              messenger.showSnackBar(const SnackBar(
                                  content: Text('Correo de recuperación enviado')));
                            }),
                    child: const Text('¿Olvidaste tu contraseña?'),
                  ),
                ),
              const SizedBox(height: 16),
              FilledButton(
                onPressed: _busy
                    ? null
                    : () => _run(() => _register
                        ? session.registerWithEmail(_email.text.trim(), _password.text)
                        : session.signInWithEmail(_email.text.trim(), _password.text)),
                child: _busy
                    ? const SizedBox(
                        height: 22, width: 22, child: CircularProgressIndicator(strokeWidth: 2))
                    : Text(_register ? 'Crear cuenta' : 'Iniciar sesión'),
              ),
              TextButton(
                onPressed: () => setState(() => _register = !_register),
                child: Text(_register
                    ? '¿Ya tienes cuenta? Inicia sesión'
                    : '¿No tienes cuenta? Regístrate'),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
