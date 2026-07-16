import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../core/network/repositories.dart';
import '../../../core/theme/app_theme.dart';

class _ChatMessage {
  final String role; // USER | ASSISTANT
  final String content;
  const _ChatMessage(this.role, this.content);
}

class _ChatViewModel extends AsyncNotifier<List<_ChatMessage>> {
  @override
  Future<List<_ChatMessage>> build() async {
    final history = await ref.read(chatRepositoryProvider).history();
    if (history.isEmpty) {
      return const [
        _ChatMessage('ASSISTANT',
            '¡Hola! Soy tu asistente nutricional. ¿En qué puedo ayudarte hoy?'),
      ];
    }
    return history.map((e) => _ChatMessage(e.role, e.content)).toList();
  }

  Future<void> send(String text) async {
    final current = state.valueOrNull ?? [];
    state = AsyncData([...current, _ChatMessage('USER', text)]);
    try {
      final reply = await ref.read(chatRepositoryProvider).send(text);
      state = AsyncData([...state.value!, _ChatMessage('ASSISTANT', reply)]);
    } catch (_) {
      state = AsyncData([
        ...state.value!,
        const _ChatMessage('ASSISTANT',
            'Ups, no pude responder ahora mismo. Inténtalo de nuevo en unos segundos.'),
      ]);
    }
  }
}

final _chatProvider =
    AsyncNotifierProvider<_ChatViewModel, List<_ChatMessage>>(_ChatViewModel.new);

/// Asistente nutricional conversacional con IA.
class ChatScreen extends ConsumerStatefulWidget {
  const ChatScreen({super.key});

  @override
  ConsumerState<ChatScreen> createState() => _ChatScreenState();
}

class _ChatScreenState extends ConsumerState<ChatScreen> {
  final _controller = TextEditingController();
  final _scroll = ScrollController();

  static const _suggestions = [
    '¿Qué puedo desayunar?',
    '¿Cómo bajar de peso?',
    '¿Qué alimentos tienen más proteínas?',
    '¿Qué debo comer hoy?',
  ];

  void _send([String? text]) {
    final msg = (text ?? _controller.text).trim();
    if (msg.isEmpty) return;
    _controller.clear();
    ref.read(_chatProvider.notifier).send(msg);
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (_scroll.hasClients) {
        _scroll.animateTo(_scroll.position.maxScrollExtent + 200,
            duration: const Duration(milliseconds: 300), curve: Curves.easeOut);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final chat = ref.watch(_chatProvider);
    final scheme = Theme.of(context).colorScheme;

    return Scaffold(
      appBar: AppBar(title: const Text('Chat Nutricional IA')),
      body: Column(children: [
        Expanded(
          child: chat.when(
            loading: () => const Center(child: CircularProgressIndicator()),
            error: (_, __) => const Center(child: Text('No se pudo cargar el chat')),
            data: (messages) => ListView.builder(
              controller: _scroll,
              padding: const EdgeInsets.all(16),
              itemCount: messages.length,
              itemBuilder: (_, i) {
                final m = messages[i];
                final isUser = m.role == 'USER';
                return Align(
                  alignment: isUser ? Alignment.centerRight : Alignment.centerLeft,
                  child: Container(
                    margin: const EdgeInsets.symmetric(vertical: 4),
                    padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
                    constraints: BoxConstraints(
                        maxWidth: MediaQuery.of(context).size.width * .78),
                    decoration: BoxDecoration(
                      color: isUser ? AppColors.purple : scheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.only(
                        topLeft: const Radius.circular(18),
                        topRight: const Radius.circular(18),
                        bottomLeft: Radius.circular(isUser ? 18 : 4),
                        bottomRight: Radius.circular(isUser ? 4 : 18),
                      ),
                    ),
                    child: Text(m.content,
                        style: TextStyle(color: isUser ? Colors.white : null)),
                  ),
                );
              },
            ),
          ),
        ),
        if ((chat.valueOrNull?.length ?? 0) <= 1)
          SizedBox(
            height: 44,
            child: ListView(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.symmetric(horizontal: 12),
              children: _suggestions
                  .map((s) => Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: ActionChip(label: Text(s), onPressed: () => _send(s)),
                      ))
                  .toList(),
            ),
          ),
        SafeArea(
          child: Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 12),
            child: Row(children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  decoration: const InputDecoration(hintText: 'Escribe tu mensaje...'),
                  onSubmitted: (_) => _send(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton.filled(
                  onPressed: _send, icon: const Icon(Icons.send_rounded)),
            ]),
          ),
        ),
      ]),
    );
  }
}
